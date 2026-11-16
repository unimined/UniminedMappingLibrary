package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * strips collision-marker prefixes from layered parameter names — loom's
 * `ParchmentPrefixStripingMappingVisitor` semantics: a name like `pFoo`
 * (Parchment marks its parameters with a `p` prefix) becomes `foo`.
 * The prefix character must be followed by an uppercase letter to
 * qualify, and the following letter is lowercased after the strip.
 *
 * @param prefix the collision-marker character to strip (`p` for
 *  Parchment); names shorter than two characters are never stripped
 * @param namespaces namespaces to strip in, or `null` for all — layer
 *  params land in the namespace the layer contributes (Parchment params
 *  arrive under `mojmap`), and propagate to filled cells, so the default
 *  strips everywhere the pattern appears
 */
fun MutableMappingTree.stripParamPrefix(prefix: Char = 'p', namespaces: Set<Namespace>? = null) {
    fun strip(names: Map<Namespace, UnqualifiedName>): Map<Namespace, UnqualifiedName> {
        var changed = false
        val out = names.toMutableMap()
        for ((ns, name) in names) {
            if (namespaces != null && ns !in namespaces) continue
            val stripped = stripName(name.value, prefix) ?: continue
            out[ns] = UnqualifiedName.unchecked(stripped)
            changed = true
        }
        return if (changed) out else names
    }
    for (cls in classes) {
        for (method in cls.methods) {
            for (param in method.parameters) {
                (param as? MutableParameterMapping)?.setNames(strip(param.names))
            }
        }
        for (wildcard in cls.wildcards) {
            for (param in wildcard.parameters) {
                (param as? MutableParameterMapping)?.setNames(strip(param.names))
            }
        }
    }
}

/**
 * Loom `stripMethodArg`: `<prefix><Uppercase>rest` → `<lowercase>rest`;
 * `null` when the name doesn't carry the marker.
 */
private fun stripName(arg: String, prefix: Char): String? {
    if (arg.length > 1 && arg[0] == prefix && arg[1].isUpperCase()) {
        val rest = arg.substring(1)
        return rest.substring(0, 1).lowercase() + rest.substring(1)
    }
    return null
}
