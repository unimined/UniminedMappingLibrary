package xyz.wagyourtail.unimined.mapping.tree.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor

private val LOGGER = KotlinLogging.logger { }

/**
 * desc remapper injected by the owning tree so member merge can compare descriptors
 * across namespaces that have no common key. default: no remap available.
 */
internal typealias DescRemapper = (fromNs: Namespace, toNs: Namespace, desc: FieldOrMethodDescriptor) -> FieldOrMethodDescriptor?

internal enum class NameMatch {
    NONE,
    FULL,
    PARTIAL
}

internal fun namesMatch(a: Map<Namespace, *>, b: Map<Namespace, *>): NameMatch {
    var namesMatched = false
    var differentNames = false
    for ((ns, nameVal) in a) {
        val other = b[ns] ?: continue
        if (other == nameVal) {
            namesMatched = true
        } else {
            differentNames = true
        }
    }
    return if (!namesMatched) NameMatch.NONE else if (differentNames) NameMatch.PARTIAL else NameMatch.FULL
}

/**
 * true when [a] and [b] share a namespace with *different* names — the two rows
 * cannot be the same member. rows with disjoint namespace keys have no conflict,
 * so they may still merge when descriptors verify (eg `m _ named;(I)Lv; yarn`
 * vs `m official;()V _`).
 */
internal fun hasNameConflict(a: Map<Namespace, *>, b: Map<Namespace, *>): Boolean {
    for ((ns, nameVal) in a) {
        val other = b[ns] ?: continue
        if (other != nameVal) return true
    }
    return false
}

/** compute the descriptor of [descs] as seen in [namespace], remapping if necessary */
internal fun descIn(
    descs: Map<Namespace, out FieldOrMethodDescriptor>,
    namespace: Namespace,
    remapper: DescRemapper?
): FieldOrMethodDescriptor? {
    if (descs.isEmpty()) return null
    if (namespace in descs) return descs[namespace]
    val (from, desc) = descs.entries.first()
    return remapper?.invoke(from, namespace, desc)
}

/**
 * entries view for emit/write: stored name+desc pairs with the desc remapped into
 * every namespace (old impl kept names/descs separate and interpolated via
 * `root.map` at accept time).
 */
internal fun MethodMapping.emitEntries(): Map<Namespace, xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor> {
    val remapper = (this as? MethodMappingData)?.descRemapper
    return entries.mapValues { (ns, e) ->
        e.name.withMethodDesc(descIn(descs, ns, remapper) as? xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor)
    }
}

internal fun FieldMapping.emitEntries(): Map<Namespace, xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor> {
    val remapper = (this as? FieldMappingData)?.descRemapper
    return entries.mapValues { (ns, e) ->
        e.name.withFieldDesc(descIn(descs, ns, remapper) as? xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor)
    }
}

internal fun logPartialMerge(incoming: Any, existing: Any) {
    LOGGER.info {
        """
            Joining different names, second will take priority
            $incoming
            $existing
        """.trimIndent()
    }
}
