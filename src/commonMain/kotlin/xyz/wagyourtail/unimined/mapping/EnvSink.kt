package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableLocalVariableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutablePackageMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableWildcardMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.AnnotationElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ConstantAttributeElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.EnumExtensionElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ExceptionElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.InterfaceElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.JavadocElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SealElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SignatureElementData
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

/**
 * Emit-side env tagging — readers push row elements through
 * [env] and the *sink* decides how that env manifests, keeping the
 * `ns:env` convention out of reader code:
 *
 * ```kotlin
 * val sink = if (into is EnvAwareSink) into else EnvAwareSink.sinkAs(into, settings.envType)
 * // per row:
 * sink.env(rowEnv).push(element)
 * ```
 *
 *  - [sinkAs] with `CLIENT`/`SERVER` — the sink *materializes that
 *    side's view*: rows tagged `CLIENT`/`JOINED` emit into plain `ns`
 *    (both collapse into the same unsuffixed column); rows tagged
 *    `SERVER` are dropped (subtree skipped).
 *  - [sinkAs] with `JOINED` — the env-aware emit: sided rows land in
 *    `ns:client`/`ns:server` columns, joined rows in `ns`.
 *  - If `into` already is an [EnvAwareSink] (an env-qualified
 *    materialization target, or another layer of this wrapper), pass
 *    it through — it owns the env semantics already.
 *
 * Paired with [envView] on the read side; the `:` convention lives
 * only in [Namespace.forEnv]/[Namespace.splitEnv] and the sinks here.
 */
interface EnvAwareSink : MappingSink {

    /**
     * emit the next pushed elements tagged to [env]'s domain —
     * the returned sink's semantics depend on the target (see class
     * docs): suffix (env-aware emit), collapse, or drop.
     */
    fun env(env: EnvType): MappingSink

    companion object {

        /**
         * wrap [into] for emission relative to [envType] — see class
         * docs for the collapse/suffix semantics.
         */
        fun sinkAs(into: MappingSink, envType: EnvType): EnvAwareSink = when (envType) {
            EnvType.JOINED -> sinkAll(into)
            else -> SingleEnvSink(into, envType)
        }

        /**
         * env-aware emit — all envs, sided rows land in `ns:env`
         * columns, joined rows in `ns`.
         */
        fun sinkAll(into: MappingSink): EnvAwareSink = EnvAwareSinkImpl(into)

    }

}

/** the env-aware impl — `env(x)` emits `ns:env` columns, `env(JOINED)` unqualified */
private class EnvAwareSinkImpl(private val into: MappingSink) : EnvAwareSink, MappingSink by into {

    override fun env(env: EnvType): MappingSink =
        if (env == EnvType.JOINED) into else EnvSink(into, null, env.name.lowercase())

}

/**
 * single-env target — rows of the target env (and joined) collapse
 * into plain `ns`; rows of the *other* env are dropped entirely.
 */
private class SingleEnvSink(
    private val into: MappingSink,
    private val envType: EnvType
) : EnvAwareSink, MappingSink by into {

    override fun env(env: EnvType): MappingSink =
        if (env == EnvType.JOINED || env == envType) into else DropSink

}

/** drops a pushed element and its whole subtree — used for other-env rows */
private object DropSink : MappingSink {
    override fun push(element: MappingElement): MappingElement? = null
    override fun push(meta: MetadataElement): MetadataElement? = null
    override fun pop(element: MappingElement) {}
    override fun pop(meta: MetadataElement) {}
    override fun end() {}
}

/** row-env tag — all columns of rows pushed through the returned sink are qualified */
fun MappingSink.env(env: EnvType): MappingSink =
    if (this is EnvAwareSink) env(env) else EnvAwareSinkImpl(this).env(env)

/** file-env tag — only [qualify] namespaces are env-qualified; rest pass through */
fun MappingSink.file(env: String?, qualify: Set<Namespace>): MappingSink =
    if (env == null) this else EnvSink(this, qualify, env)

/**
 * suffixes pushed element heads' namespaces via [Namespace.forEnv] —
 * [qualify] null → all columns (row-env); non-null → only those ns
 * (file-env: e.g. `official`→`official:client`, named cols stay joined).
 */
private class EnvSink(
    private val into: MappingSink,
    /** null → qualify all columns (row-env); non-null → only these ns (file-env) */
    private val qualify: Set<Namespace>?,
    private val env: String
) : MappingSink {

    private fun label(ns: Namespace): Namespace =
        if (qualify == null || ns in qualify) ns.forEnv(env) else ns

    private fun <V> relabel(map: Map<Namespace, V>): Map<Namespace, V> =
        map.mapKeys { (ns, _) -> label(ns) }

    private fun relabelMeta(meta: MetadataElement): MetadataElement {
        if (meta !is NsBoundMetadata) return meta
        val labeled = label(meta.baseNs)
        if (labeled === meta.baseNs) return meta
        return when (meta) {
            is AnnotationElementData -> meta.copy(baseNs = labeled)
            is ConstantAttributeElementData -> meta.copy(baseNs = labeled)
            is EnumExtensionElementData -> meta.copy(baseNs = labeled)
            is ExceptionElementData -> meta.copy(baseNs = labeled)
            is InterfaceElementData -> meta.copy(baseNs = labeled)
            is JavadocElementData -> meta.copy(baseNs = labeled)
            is SealElementData -> meta.copy(baseNs = labeled)
            is SignatureElementData -> meta.copy(baseNs = labeled)
            else -> meta
        }
    }

    /** copy an element head with its ns maps relabeled; children push separately */
    private fun relabelHead(el: MappingElement): MappingElement = when (el) {
        is MutableClassMapping -> MutableClassMapping(relabel(el.names))
        is MutableFieldMapping -> MutableFieldMapping(relabel(el.entries))
        is MutableMethodMapping -> MutableMethodMapping(relabel(el.entries))
        is MutableWildcardMapping -> MutableWildcardMapping(el.type, relabel(el.descs))
        is MutableParameterMapping -> MutableParameterMapping(el.index, el.lvOrd, relabel(el.names))
        is MutableLocalVariableMapping -> MutableLocalVariableMapping(el.lvOrd, el.startOp, relabel(el.names))
        is MutablePackageMapping -> MutablePackageMapping(relabel(el.names))
        is MutableConstantGroupMapping -> MutableConstantGroupMapping(el.type, el.name, label(el.baseNs))
        else -> el
    }

    override fun header(namespaces: List<Namespace>) {
        into.header(namespaces.map(::label).distinct())
    }

    override fun push(element: MappingElement): MappingElement? {
        val pushed = into.push(relabelHead(element)) ?: return null
        // re-emit the element's own metadata through the relabeled head
        (element as? xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent)
            ?.metadata?.forEach { into.push(relabelMeta(it)) }
        return pushed
    }

    override fun push(meta: MetadataElement): MetadataElement? = into.push(relabelMeta(meta))

    override fun pop(element: MappingElement) = into.pop(element)
    override fun pop(meta: MetadataElement) = into.pop(meta)
    override fun end() = into.end()

}
