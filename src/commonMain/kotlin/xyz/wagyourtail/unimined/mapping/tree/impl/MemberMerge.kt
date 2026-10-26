package xyz.wagyourtail.unimined.mapping.tree.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.newNsMap

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

/**
 * member-merge helpers operate on the raw `name;desc` strings stored in row
 * `entries` — comparing the name region without substringing and never
 * materializing the derived `names`/`descs` maps, which previously cost a
 * LinkedHashMap (plus per-entry substrings) per merge check.
 */

private fun nameEnd(v: String): Int = v.indexOf(';').let { if (it < 0) v.length else it }

private fun descStart(v: String): Int = v.indexOf(';') + 1

/** name-part equality on raw `name;desc` strings — zero-alloc */
private fun namePartEq(a: String, b: String): Boolean {
    val ae = nameEnd(a)
    val be = nameEnd(b)
    return ae == be && a.regionMatches(0, b, 0, ae)
}

/** desc-part equality on raw `name;desc` strings — zero-alloc */
private fun descPartEq(a: String, b: String): Boolean {
    val as_ = descStart(a)
    val bs = descStart(b)
    return a.length - as_ == b.length - bs && a.regionMatches(as_, b, bs, a.length - as_)
}

internal fun Map<Namespace, out NameAndDescriptor>.namesMatch(
    other: Map<Namespace, out NameAndDescriptor>
): NameMatch {
    var namesMatched = false
    var differentNames = false
    for ((ns, entry) in this) {
        val otherEntry = other[ns] ?: continue
        if (namePartEq(entry.value, otherEntry.value)) {
            namesMatched = true
        } else {
            differentNames = true
        }
    }
    return if (!namesMatched) NameMatch.NONE else if (differentNames) NameMatch.PARTIAL else NameMatch.FULL
}

/**
 * true when [this] and [other] share a namespace with *different* names — the two
 * rows cannot be the same member. rows with disjoint namespace keys have no
 * conflict, so they may still merge when descriptors verify (eg `m _ named;(I)Lv; yarn`
 * vs `m official;()V _`).
 */
internal fun Map<Namespace, out NameAndDescriptor>.hasNameConflict(
    other: Map<Namespace, out NameAndDescriptor>
): Boolean {
    for ((ns, entry) in this) {
        val otherEntry = other[ns] ?: continue
        if (!namePartEq(entry.value, otherEntry.value)) return true
    }
    return false
}

internal fun Map<Namespace, out NameAndDescriptor>.anyDesc(): Boolean =
    values.any { it.hasDescriptor }

/**
 * pick the namespace to verify descriptors in: the first of [this]'s desc-bearing
 * namespaces also bearing a desc in [other], else [other]'s first desc-bearing ns.
 * callers guarantee [other] has at least one desc.
 */
internal fun Map<Namespace, out NameAndDescriptor>.pickDescNs(
    other: Map<Namespace, out NameAndDescriptor>
): Namespace {
    for ((ns, entry) in this) {
        if (entry.hasDescriptor && other[ns]?.hasDescriptor == true) return ns
    }
    for ((ns, entry) in other) {
        if (entry.hasDescriptor) return ns
    }
    throw IllegalArgumentException("no descriptor namespace available")
}

/** the descriptor of [this] as seen in [ns], remapping the first stored desc if absent */
internal fun Map<Namespace, out NameAndDescriptor>.descIn(
    ns: Namespace,
    remapper: DescRemapper?
): FieldOrMethodDescriptor? {
    this[ns]?.let { if (it.hasDescriptor) return it.descriptor }
    val (from, entry) = entries.firstOrNull { it.value.hasDescriptor } ?: return null
    return remapper?.invoke(from, ns, entry.descriptor!!)
}

/** descriptor-region equality between this row's [ns] entry and [other]'s remapped desc */
internal fun Map<Namespace, out NameAndDescriptor>.descMatches(
    other: Map<Namespace, out NameAndDescriptor>,
    ns: Namespace,
    remapper: DescRemapper?
): Boolean {
    val incoming = other[ns]?.takeIf { it.hasDescriptor } ?: return false
    val stored = this[ns]
    if (stored != null && stored.hasDescriptor) return descPartEq(stored.value, incoming.value)
    return descIn(ns, remapper) == incoming.descriptor
}

/** [descIn] for maps that store raw descriptors (wildcard rows) */
internal fun Map<Namespace, out FieldOrMethodDescriptor>.descInDesc(
    ns: Namespace,
    remapper: DescRemapper?
): FieldOrMethodDescriptor? {
    if (isEmpty()) return null
    if (ns in this) return this[ns]
    val (from, desc) = entries.first()
    return remapper?.invoke(from, ns, desc)
}

/**
 * entries view for emit/write: stored name+desc pairs with the desc remapped into
 * every namespace (old impl kept names/descs separate and interpolated via
 * `root.map` at accept time).
 */
internal fun MethodMapping.emitEntries(): Map<Namespace, xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor> {
    val remapper = (this as? MethodMappingData)?.descRemapper
    val out = newNsMap<xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor>()
    for ((ns, e) in entries) {
        val desc = e.descriptor as? xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
            ?: entries.descIn(ns, remapper) as? xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
        out[ns] = e.name.withMethodDesc(desc)
    }
    return out
}

internal fun FieldMapping.emitEntries(): Map<Namespace, xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor> {
    val remapper = (this as? FieldMappingData)?.descRemapper
    val out = newNsMap<xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor>()
    for ((ns, e) in entries) {
        val desc = e.descriptor as? xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
            ?: entries.descIn(ns, remapper) as? xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
        out[ns] = e.name.withFieldDesc(desc)
    }
    return out
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
