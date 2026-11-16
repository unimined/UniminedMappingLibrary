package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.toNsMap

/**
 * Whether [entries]' cell for [ns] counts as a *genuine* rename rather
 * than a verbatim copy — the shared test member-name resolution applies
 * (`GraphNamespaceRemapper`'s declaring-chain row reads, MemberGroups'
 * candidate stamping, `ResolvingMappingTree`'s cell preservation, and
 * the bytecode fill's held-cell snapshot).
 *
 * Name-completion passes copy strictly earlier→later along the declared
 * namespace chain: [fillMissingNames] writes a `requires` column's value
 * into empty later `provides` cells, and hop-fills do the same across an
 * intermediate column. A cell can therefore only echo a column *earlier*
 * than [ns] in [order]; an equal name in a *later* column is a copy
 * **of** this cell, not its source. Comparing against every sibling
 * column misclassifies a genuine cell whenever a later column copied it
 * — `{official: c, searge: field_x, mcp: field_x}` has `mcp` echoing
 * `searge`, not the reverse — which is what left subclass-scoped member
 * references on the source name while declarations renamed.
 *
 * Columns absent from [order] cannot be fill sources for [ns] — they are
 * never witnesses.
 *
 * @return `true` when [ns]'s name differs from every earlier-declared
 *  column on the row; `false` when the cell is absent or echoes one
 */
inline fun <E> isGenuineCellName(
    order: List<Namespace>,
    entries: Map<Namespace, E>,
    ns: Namespace,
    cellName: (E) -> UnqualifiedName?,
): Boolean {
    val target = entries[ns]?.let(cellName) ?: return false
    val idx = order.indexOf(ns)
    for ((k, e) in entries) {
        if (k == ns || cellName(e) != target) continue
        val ki = order.indexOf(k)
        if (ki != -1 && (idx == -1 || ki < idx)) return false
    }
    return true
}

/**
 * fills missing names from dependent namespaces.
 * replaces NameCopyDelegate/fillMissingNames — direct mutation, no visitor round-trip.
 */
fun MutableMappingTree.fillMissingNames(vararg toFill: Pair<Namespace, Set<Namespace>>) {
    fun <T> fill(names: Map<Namespace, T>): Map<Namespace, T> {
        if (toFill.isEmpty()) return names
        val out = names.toNsMap()
        for ((from, targets) in toFill) {
            val src = out[from] ?: continue
            for (t in targets) {
                // env columns (`ns:env`) mark side-presence — an
                // empty cell means "absent on that side", not
                // "same name", so they must never be filled
                if (t.splitEnv().second != null) continue
                if (t !in out) out[t] = src
            }
        }
        return out
    }

    for (pkg in packages) {
        (pkg as? MutablePackageMapping)?.setNames(fill(pkg.names))
    }
    for (cls in classes) {
        if (cls !is MutableClassMapping) continue
        cls.setNames(fill(cls.names))
        for (field in cls.fields) {
            (field as? MutableFieldMapping)?.setNames(fill(field.names))
        }
        for (method in cls.methods) {
            if (method is MutableMethodMapping) {
                method.setNames(fill(method.names))
                for (param in method.parameters) {
                    (param as? MutableParameterMapping)?.setNames(fill(param.names))
                }
                for (local in method.localVariables) {
                    (local as? MutableLocalVariableMapping)?.setNames(fill(local.names))
                }
            }
        }
        for (wildcard in cls.wildcards) {
            if (wildcard is MutableWildcardMapping) {
                for (param in wildcard.parameters) {
                    (param as? MutableParameterMapping)?.setNames(fill(param.names))
                }
                for (local in wildcard.localVariables) {
                    (local as? MutableLocalVariableMapping)?.setNames(fill(local.names))
                }
            }
        }
    }
}
