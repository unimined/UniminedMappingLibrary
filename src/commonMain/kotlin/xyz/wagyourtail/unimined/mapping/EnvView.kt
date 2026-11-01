package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableInvokableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.InvokableMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.MutableMetadataParent
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.element.metadata.NsBoundMetadata
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.AnnotationElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ConstantAttributeElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.EnumExtensionElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ExceptionElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.InterfaceElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.JavadocElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SealElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SignatureElementData
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.ConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.LocalVariableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.PackageMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.ParameterMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.WildcardMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor

/**
 * Env-view collapse — the env-selector convention lives here and on
 * [Namespace.forEnv]/[Namespace.splitEnv]; nothing else in the library
 * knows the `ns:env` suffix. `envView(E)` produces a plain
 * [MutableMappingTree] in which each env-qualified `ns:env` column is
 * folded into its base `ns` — **sided wins** when both are present —
 * giving a complete per-side mapping domain (`envView(client)` =
 * `official ∪ official:client` as `official`). Other envs' rows are
 * dropped from the view. Remap/lookup then proceed through the normal
 * two-namespace APIs — env is an emit-time and view-time concept, not
 * a lookup parameter.
 */

/**
 * Env-collapse on a `Map<Namespace, V>` — entries under `ns:env` are
 * relabeled to `ns` and take precedence over plain `ns` entries;
 * entries under a *different* env's `ns:other` are dropped.
 */
private fun <V> Map<Namespace, V>.envCollapse(env: String): Map<Namespace, V> {
    if (isEmpty()) return this
    val out = LinkedHashMap<Namespace, V>(size)
    for ((ns, v) in this) {
        val (base, e) = ns.splitEnv()
        when {
            e == null -> if (base !in out) out[base] = v
            e == env -> out[base] = v
        }
    }
    return out
}

/** collapse an ns-binding carried by metadata — sided `ns:env` → `ns`, else verbatim */
private fun Namespace.envBase(env: String): Namespace {
    val (base, e) = splitEnv()
    return if (e == env) base else this
}

/**
 * The env-collapsed view of this tree as a plain [MutableMappingTree].
 * [env] = `null` yields a copy restricted to the joined/unqualified
 * domain (joined env-view is exactly `ns` — never the union of sides).
 * The view's header lists the *base* namespaces; every element's
 * `names`/`entries`/`descs`/`comments` maps and ns-bound metadata are
 * collapsed via [envCollapse]. Remapping a side's jar runs against
 * `envView(side)` with plain `ns` routes — `official → calamus` on
 * `envView(client)` resolves `official:client` ∪ `official`.
 */
fun MappingTree.envView(env: String?): MutableMappingTree {
    val view = MutableMappingTree(namespaces.map { it.splitEnv().first }.distinct())
    for (pkg in packages) {
        view.package_(pkg.names.envCollapseSafe(env)).also { emitMeta(it, pkg, env) }
    }
    for (group in constantGroups) {
        val baseNs = if (env == null) group.baseNs else group.baseNs.envBase(env)
        view.constantGroup(group.type, group.name, baseNs).also { emitMeta(it, group, env) }
    }
    for (cls in classes) {
        val c = view.clazz(cls.names.envCollapseSafe(env))
        emitMeta(c, cls, env)
        emitClassChildren(this, c, cls, env)
    }
    return view
}

/** null-env keeps only unqualified columns; non-null collapses `ns:env` into `ns` */
private fun <V> Map<Namespace, V>.envCollapseSafe(env: String?): Map<Namespace, V> =
    if (env == null) filterKeys { it.splitEnv().second == null } else envCollapse(env)

private fun emitMeta(target: MutableMetadataParent, src: MetadataParent, env: String?) {
    for (m in src.metadata) {
        target.addMetadata(if (env == null) m else collapseMeta(m, env))
    }
}

/**
 * metadata carries ns-bound values (`baseNs` on signatures/annotations/etc)
 * — collapse a sided `ns:env` binding back to `ns` so the view's
 * relabeled domain matches the metadata's declared namespace.
 */
private fun collapseMeta(meta: MetadataElement, env: String): MetadataElement {
    if (meta !is NsBoundMetadata) return meta
    val (base, e) = meta.baseNs.splitEnv()
    if (e != env) return meta
    return when (meta) {
        is AnnotationElementData -> meta.copy(baseNs = base)
        is ConstantAttributeElementData -> meta.copy(baseNs = base)
        is EnumExtensionElementData -> meta.copy(baseNs = base)
        is ExceptionElementData -> meta.copy(baseNs = base)
        is InterfaceElementData -> meta.copy(baseNs = base)
        is JavadocElementData -> meta.copy(baseNs = base)
        is SealElementData -> meta.copy(baseNs = base)
        is SignatureElementData -> meta.copy(baseNs = base)
        else -> meta
    }
}

private fun MappingTree.envEntriesCollapse(
    entries: Map<Namespace, MethodNameAndDescriptor>,
    env: String?
): Map<Namespace, MethodNameAndDescriptor> {
    val collapsed = entries.envCollapseSafe(env)
    if (env == null) return collapsed
    // back-fill a projected desc on collapsed env cells: siblings that
    // differ only by the dropped env's desc would otherwise collapse to
    // one member on the name-only key (b1.7.3 'g'/'d' row merge)
    return collapsed.mapValues { (ns, cell) ->
        if (cell.descriptor != null) return@mapValues cell
        val (base, e) = ns.splitEnv()
        if (e != null) return@mapValues cell
        val donor = entries.entries.firstOrNull { (dns, c) ->
            val (b, de) = dns.splitEnv()
            b == base && de != null && de != env && c.descriptor != null
        } ?: return@mapValues cell
        val toNs = base.forEnv(env) ?: return@mapValues cell
        MethodNameAndDescriptor(cell.name, map(donor.key, toNs, donor.value.descriptor!!))
    }
}

private fun MappingTree.envEntriesCollapseF(
    entries: Map<Namespace, FieldNameAndDescriptor>,
    env: String?
): Map<Namespace, FieldNameAndDescriptor> {
    val collapsed = entries.envCollapseSafe(env)
    if (env == null) return collapsed
    return collapsed.mapValues { (ns, cell) ->
        if (cell.descriptor != null) return@mapValues cell
        val (base, e) = ns.splitEnv()
        if (e != null) return@mapValues cell
        val donor = entries.entries.firstOrNull { (dns, c) ->
            val (b, de) = dns.splitEnv()
            b == base && de != null && de != env && c.descriptor != null
        } ?: return@mapValues cell
        val toNs = base.forEnv(env) ?: return@mapValues cell
        FieldNameAndDescriptor(cell.name, map(donor.key, toNs, donor.value.descriptor!!))
    }
}

private fun emitClassChildren(
    tree: MappingTree,
    target: MutableClassMapping,
    src: ClassMapping,
    env: String?
) {
    for (f in src.fields) {
        val fm = target.field(tree.envEntriesCollapseF(f.entries, env))
        emitMeta(fm, f, env)
    }
    for (m in src.methods) {
        val mm = target.method(tree.envEntriesCollapse(m.entries, env))
        emitMeta(mm, m, env)
        emitInvokable(mm, m, env)
    }
    for (w in src.wildcards) {
        val wm = target.wildcard(w.type, w.descs.envCollapseSafe(env))
        emitMeta(wm, w, env)
        emitInvokable(wm, w, env)
    }
}

private fun emitInvokable(
    target: MutableInvokableMapping,
    src: InvokableMapping,
    env: String?
) {
    for (p in src.parameters) {
        val pm = target.parameter(p.index, p.lvOrd, p.names.envCollapseSafe(env))
        emitMeta(pm, p, env)
    }
    for (l in src.localVariables) {
        val lm = target.localVariable(l.lvOrd, l.startOp, l.names.envCollapseSafe(env))
        emitMeta(lm, l, env)
    }
}

// the emit helpers attach collapsed copies of every child element —
// class fields/methods/wildcards, invokable params/locals, metadata —
// so the view is a complete plain tree, not a lazy overlay
