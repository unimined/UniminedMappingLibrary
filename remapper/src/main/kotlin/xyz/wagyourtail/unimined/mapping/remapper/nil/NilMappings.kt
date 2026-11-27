package xyz.wagyourtail.unimined.mapping.remapper.nil

/**
 * Accumulated content of one NIL mapping-set id — the value of a
 * top-level key in `META-INF/nil/mappings.json`.
 *
 * Namespaces are mixed by design (verified against NilAgent):
 * [classes] are keyed by their *from* (mod-compile) namespace names,
 * while the widen entries are in the *target* (runtime) namespace —
 * NIL matches widen entries against live game class loads.
 */
class NilMappings {

    /** class entries keyed by from-side full binary name */
    val classes = sortedMapOf<String, NilClassEntry>()

    /** target-ns internal names of classes needing runtime widening */
    val widenClasses = sortedSetOf<String>()

    /** target-ns owner internal name → target-ns `name(desc)ret` sigs */
    val widenMethods = sortedMapOf<String, MutableSet<String>>()

    /** target-ns owner internal name → target-ns `name:desc` sigs */
    val widenFields = sortedMapOf<String, MutableSet<String>>()

    fun isEmpty() = classes.isEmpty() && widenClasses.isEmpty() &&
            widenMethods.isEmpty() && widenFields.isEmpty()

}

/**
 * One class entry — [from]/[to] are full binary names in their
 * respective namespaces; nesting into `inner-classes` happens at
 * write time. Member sig keys render in the from-side namespace,
 * values in the to-side namespace.
 */
class NilClassEntry(val from: String, val to: String) {

    /** `name(desc)ret` from-side → to-side */
    val methods = sortedMapOf<String, String>()

    /** `name:desc` from-side → to-side */
    val fields = sortedMapOf<String, String>()

}
