package xyz.wagyourtail.unimined.mapping.tree.impl

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.forEachNsEntry
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

/**
 * env-domain comparability — same base ns and non-exclusive envs:
 * `ns` is comparable to `ns`, `ns:client` and `ns:server` (joined
 * covers both domains); `ns:client` is comparable to `ns:client` and
 * `ns` but NOT `ns:server` (disjoint env domains). Identity short-circuits
 * the common same-column compare; the split itself is memoized on
 * [Namespace] so this is allocation-free.
 */
private fun Namespace.envComparable(other: Namespace): Boolean {
    if (this === other) return true
    return baseNs === other.baseNs && (envPart == null || other.envPart == null || envPart == other.envPart)
}

internal fun Map<Namespace, out NameAndDescriptor>.namesMatch(
    other: Map<Namespace, out NameAndDescriptor>
): NameMatch {
    var namesMatched = false
    var differentNames = false
    forEachNsEntry { ns, entry ->
        other.forEachNsEntry inner@{ ons, oentry ->
            if (!ns.envComparable(ons)) return@inner
            if (namePartEq(entry.value, oentry.value)) {
                namesMatched = true
            } else {
                differentNames = true
            }
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
    forEachNsEntry { ns, entry ->
        other.forEachNsEntry inner@{ ons, oentry ->
            if (!ns.envComparable(ons)) return@inner
            if (!namePartEq(entry.value, oentry.value)) return true
        }
    }
    return false
}

internal fun Map<Namespace, out NameAndDescriptor>.anyDesc(): Boolean {
    forEachNsEntry { _, e -> if (e.hasDescriptor) return true }
    return false
}

/**
 * pick the namespace to verify descriptors in: the first of [this]'s desc-bearing
 * namespaces also bearing a desc in [other], else [other]'s first desc-bearing ns.
 * env-aware — compares env-comparable keys, returns [other]'s key so descMatches
 * can index it directly. callers guarantee [other] has at least one desc.
 */
internal fun Map<Namespace, out NameAndDescriptor>.pickDescNs(
    other: Map<Namespace, out NameAndDescriptor>
): Namespace {
    forEachNsEntry outer@{ ns, entry ->
        if (!entry.hasDescriptor) return@outer
        other.forEachNsEntry { ons, oentry ->
            if (oentry.hasDescriptor && ns.envComparable(ons)) return ons
        }
    }
    other.forEachNsEntry { ns, entry ->
        if (entry.hasDescriptor) return ns
    }
    throw IllegalArgumentException("no descriptor namespace available")
}

/** the descriptor of [this] as seen in [ns], remapping the first stored desc if absent */
internal fun Map<Namespace, out NameAndDescriptor>.descIn(
    ns: Namespace,
    remapper: DescRemapper?
): FieldOrMethodDescriptor? {
    // exact key, then an env-comparable key (`ns` ↔ `ns:env`), then remap
    this[ns]?.let { if (it.hasDescriptor) return it.descriptor }
    forEachNsEntry { k, e ->
        if (k !== ns && k.envComparable(ns) && e.hasDescriptor) return e.descriptor
    }
    forEachNsEntry { k, e ->
        if (e.hasDescriptor) return remapper?.invoke(k, ns, e.descriptor!!)
    }
    return null
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
    this[ns]?.let { return it }
    // env-comparable key (`ns` ↔ `ns:env`) before remapping
    forEachNsEntry { k, d ->
        if (k.envComparable(ns)) return d
    }
    // remap the first stored pair — non-empty is guaranteed above
    forEachNsEntry { k, d -> return remapper?.invoke(k, ns, d) }
    throw IllegalStateException("unreachable — non-empty map")
}

/**
 * entries view for emit/write: stored name+desc pairs with the desc remapped into
 * every namespace (old impl kept names/descs separate and interpolated via
 * `root.map` at accept time).
 *
 * [nsFilter]/[nsRenamer] fuse the emit-side `filterKeys`/`mapKeys` passes
 * into this one materialization — the desc is still resolved under the
 * stored ns and only the output key is renamed.
 */
internal fun MethodMapping.emitEntries(
    nsFilter: Collection<Namespace>? = null,
    nsRenamer: (Namespace) -> Namespace = { it }
): MutableMap<Namespace, xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor> {
    val remapper = (this as? MethodMappingData)?.descRemapper
    val out = newNsMap<xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor>()
    entries.forEachNsEntry { ns, e ->
        if (nsFilter != null && ns !in nsFilter) return@forEachNsEntry
        val desc = e.descriptor as? xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
            ?: entries.descIn(ns, remapper) as? xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
        out[nsRenamer(ns)] = e.name.withMethodDesc(desc)
    }
    return out
}

internal fun FieldMapping.emitEntries(
    nsFilter: Collection<Namespace>? = null,
    nsRenamer: (Namespace) -> Namespace = { it }
): MutableMap<Namespace, xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor> {
    val remapper = (this as? FieldMappingData)?.descRemapper
    val out = newNsMap<xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor>()
    entries.forEachNsEntry { ns, e ->
        if (nsFilter != null && ns !in nsFilter) return@forEachNsEntry
        val desc = e.descriptor as? xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
            ?: entries.descIn(ns, remapper) as? xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
        out[nsRenamer(ns)] = e.name.withFieldDesc(desc)
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
