package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.NamespaceRemapper
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.remapper
import xyz.wagyourtail.unimined.mapping.toNsMap

/**
 * fills missing cells on rows whose present names are all equal — the row
 * describes an un-obfuscated member, so the missing cells carry the same
 * name (loom's `MappingNsCompleter` "un-changed" rule: e.g. a member
 * present as `intermediary == named` gets its missing `official` cell).
 * Rows whose present names differ are real mappings and are left alone —
 * the missing cell can't be guessed.
 *
 * Member descs are remapped through the tree's namespace remapper rather
 * than copied — descriptors are namespace-relative even when the member
 * name agrees across namespaces.
 *
 * env columns (`ns:env`) mark side-presence — an empty cell means
 * "absent on that side", not "same name", so they must never be
 * filled: fabricating `official:server` from `official:client`
 * would declare members the other side's jar doesn't have.
 * Likewise a row whose only present cells are env-qualified
 * carries no "unchanged" evidence — a single-sided obf name can't
 * prove the member is un-obfuscated, so env-only rows don't fill.
 */
fun MutableMappingTree.fillUnchangedCells() {
    val allNs = namespaces.toList()
    if (allNs.size < 2) return
    val unqualified = allNs.filter { it.splitEnv().second == null }
    val remappers = mutableMapOf<Pair<Namespace, Namespace>, NamespaceRemapper>()
    fun descRemapper(from: Namespace, to: Namespace): NamespaceRemapper =
        remappers.getOrPut(from to to) { remapper(from, to) }

    /** name-only rows: fill missing cells iff all present names equal. */
    fun <T> completeNames(names: Map<Namespace, T>): Map<Namespace, T> {
        if (names.isEmpty() || names.size == allNs.size) return names
        if (names.keys.none { it.splitEnv().second == null }) return names
        val present = names.values.toSet()
        if (present.size != 1) return names
        val out = names.toNsMap()
        for (ns in unqualified) {
            if (ns !in out) out[ns] = present.single()
        }
        return out
    }

    /**
     * the desc donor for a fill — deterministic tree-namespace order
     * (ns-map iteration is *ordinal* order, not row order): the first
     * unqualified cell carrying a descriptor, else the first unqualified
     * cell at all. `null` when no unqualified cell exists.
     */
    fun <T> donorOf(entries: Map<Namespace, T>, desc: (T) -> FieldOrMethodDescriptor?): Pair<Namespace, T>? {
        var fallback: Pair<Namespace, T>? = null
        for (ns in allNs) {
            if (ns.splitEnv().second != null) continue
            val e = entries[ns] ?: continue
            if (desc(e) != null) return ns to e
            if (fallback == null) fallback = ns to e
        }
        return fallback
    }

    for (pkg in packages) {
        (pkg as? MutablePackageMapping)?.setNames(completeNames(pkg.names))
    }
    for (cls in classes) {
        if (cls !is MutableClassMapping) continue
        cls.setNames(completeNames(cls.names))
        for (field in cls.fields) {
            if (field !is MutableFieldMapping) continue
            val entries = field.entries
            if (entries.isNotEmpty() && entries.size != allNs.size &&
                entries.keys.any { it.splitEnv().second == null }
            ) {
                val present = entries.values.mapTo(mutableSetOf()) { it.name }
                if (present.size == 1) {
                    val name = present.single()
                    val donor = donorOf(entries) { it.descriptor }
                    val out = entries.toNsMap()
                    for (ns in unqualified) {
                        if (ns in out) continue
                        val desc = donor?.second?.descriptor
                            ?.let { descRemapper(donor.first, ns).map(it) }
                        out[ns] = FieldNameAndDescriptor.unchecked(
                            if (desc != null) "${name.value};${desc.value}" else name.value
                        )
                    }
                    field.setEntries(out)
                }
            }
        }
        for (method in cls.methods) {
            if (method !is MutableMethodMapping) continue
            val entries = method.entries
            if (entries.isNotEmpty() && entries.size != allNs.size &&
                entries.keys.any { it.splitEnv().second == null }
            ) {
                val present = entries.values.mapTo(mutableSetOf()) { it.name }
                if (present.size == 1) {
                    val name = present.single()
                    val donor = donorOf(entries) { it.descriptor }
                    val out = entries.toNsMap()
                    for (ns in unqualified) {
                        if (ns in out) continue
                        val desc = donor?.second?.descriptor
                            ?.let { descRemapper(donor.first, ns).map(it) }
                        out[ns] = MethodNameAndDescriptor.unchecked(
                            if (desc != null) "${name.value};${desc.value}" else name.value
                        )
                    }
                    method.setEntries(out)
                }
            }
            // child rows fill independently of the parent's own
            // completeness — a fully-mapped method can still carry
            // sparse param/lvar names
            for (param in method.parameters) {
                (param as? MutableParameterMapping)
                    ?.setNames(completeNames(param.names))
            }
            for (local in method.localVariables) {
                (local as? MutableLocalVariableMapping)
                    ?.setNames(completeNames(local.names))
            }
        }
        for (wildcard in cls.wildcards) {
            if (wildcard !is MutableWildcardMapping) continue
            for (param in wildcard.parameters) {
                (param as? MutableParameterMapping)
                    ?.setNames(completeNames(param.names))
            }
            for (local in wildcard.localVariables) {
                (local as? MutableLocalVariableMapping)
                    ?.setNames(completeNames(local.names))
            }
        }
    }
}
