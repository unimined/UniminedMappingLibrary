package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.FIELD
import xyz.wagyourtail.unimined.mapping.jvms.four.METHOD

/**
 * Resolve conditional [AccessElement]s — predicates like `+PRIVATE` /
 * `-FINAL` ([AccessConditions] `CONTAINS`/`NOT_CONTAINS` parts) — against
 * the member's *scanned* bytecode flags, and stamp the outcome back as
 * concrete unconditional elements.
 *
 * Mapping rows store access *changes*; the base flag sets the conditions
 * test live in bytecode, not in the tree — so resolution needs a flag
 * source, here [inheritance] (any [InheritanceTree]: eager scan,
 * [CachedInheritanceTree][xyz.wagyourtail.unimined.mapping.inheritance.CachedInheritanceTree],
 * lazy ASM graph). For each member row keyed by its [fns]-namespace
 * name+descriptor, the scanned `access` int decodes via
 * [AccessFlag.of] and every conditional element is checked once against
 * that original set — matching
 * [InheritanceTree.applyAccessChanges]' original-vs-evolving
 * split (conditions see pre-change flags):
 *
 * - condition **holds** → the element is replaced in place by
 *   `AccessElement(type, value, AccessConditions.ALL)`;
 * - condition **fails** → the element is dropped — it is a semantic
 *   no-op, and formats that cannot express conditions must not emit
 *   it as an unconditional change;
 * - the member has **no scan counterpart** (or a desc-less row matches
 *   multiple members — which single flag set would it test?) → its
 *   elements are left conditional.
 *
 * This is the shared primitive for pivot-style conversion/fill paths
 * (`ATReader.read` → `resolveAccessConditions` → `AWWriter.write`) that
 * feed writers unable to express conditional access — AW ignores
 * non-`ALL` conditions entirely.
 *
 * Out of scope, left untouched:
 *
 * - **class rows** — [InheritanceTree.ClassInfo] scans no class-level
 *   flags, so there is nothing to test against;
 * - **wildcard rows** — a wildcard's conditions are per-target-member
 *   predicates; resolving them would mean stamping concrete elements on
 *   every matched member row (expansion, not resolution — see
 *   `AccessFormatConvert` for that semantics).
 *
 * Note the ordering hazard this shares with `applyAccessChanges`: if a
 * caller already folded the same elements into the scan's flags, the
 * conditions re-evaluate against *mutated* flags and may flip. Resolve
 * before folding, or accept flags exactly as the source supplies them.
 *
 * @param fns the namespace member identity keys on — class and member
 *  rows resolve through this column's names
 * @param inheritance the scanned flag source
 * @return the number of conditional elements resolved (replaced or
 *  dropped); elements left conditional are not counted
 */
fun MutableMappingTree.resolveAccessConditions(
    fns: Namespace,
    inheritance: InheritanceTree,
): Int {
    var resolved = 0
    for (cls in classes) {
        val info = inheritance.classInfo(cls.names[fns] ?: continue) ?: continue
        for (method in cls.methods) {
            val entry = method.entries[fns] ?: continue
            val matches = info.methods.filter {
                it.name == entry.name &&
                        (entry.descriptor == null || it.descriptor == entry.descriptor)
            }
            if (matches.size != 1) continue
            resolved += method.resolveConditions(
                AccessFlag.of(METHOD, matches.single().access)
            )
        }
        for (field in cls.fields) {
            val entry = field.entries[fns] ?: continue
            val matches = info.fields.filter {
                it.name == entry.name &&
                        (entry.descriptor == null || it.descriptor == entry.descriptor)
            }
            if (matches.size != 1) continue
            resolved += field.resolveConditions(
                AccessFlag.of(FIELD, matches.single().access)
            )
        }
    }
    return resolved
}

/** Evaluate every conditional [AccessElement] on this member against
 *  [orig] — replace holds with `ALL`-condition twins, drop fails. */
private fun MetadataParent.resolveConditions(orig: Set<AccessFlag>): Int {
    // MetadataParent exposes Iterable; the data classes back it with a
    // MutableList — non-mutable parents (deltas, views) skip resolution
    val els = metadata as? MutableList<MetadataElement> ?: return 0
    var resolved = 0
    val it = els.listIterator()
    while (it.hasNext()) {
        val el = it.next()
        if (el !is AccessElement || el.condition == AccessConditions.ALL) continue
        resolved++
        if (el.condition.check(orig)) {
            it.set(AccessElement(el.type, el.value, AccessConditions.ALL))
        } else {
            it.remove()
        }
    }
    return resolved
}
