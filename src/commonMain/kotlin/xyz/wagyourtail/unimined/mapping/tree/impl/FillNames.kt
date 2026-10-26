package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.toNsMap

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
