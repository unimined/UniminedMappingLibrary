package xyz.wagyourtail.unimined.mapping.names

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import java.nio.file.Path

/**
 * ProGuard-style short names: `a`, `b`, …, `z`, `aa`, `ab`, …
 */
object ShortNames {
    operator fun get(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.append('a' + i % 26)
            i = i / 26 - 1
        } while (i >= 0)
        return sb.reverse().toString()
    }
}

/**
 * Generates ProGuard-style obfuscation mappings into a [MutableMappingTree] from a
 * jar scan — classes get flat short names (`a`, `b`, …), members share a name
 * whenever descriptors differ (aggressive overloading, like ProGuard), and
 * members related through inheritance keep one name so overrides stay linked.
 *
 * The output tree is a normal obf-namespace layer — feed it to
 * `AsmRemapper.remap` to produce the obfuscated jar.
 */
class ProGuardMapper(
    private val tree: MutableMappingTree,
    private val fns: Namespace,
    private val obf: Namespace,
) {

    /** Scan [jars] (classes must be in [fns] namespace) and generate [obf] rows. */
    fun apply(jars: Set<Path>) {
        val graph = ASMInheritanceTree(fns, jars)

        graph.classes.values.sortedBy { it.name.value }.forEachIndexed { i, info ->
            tree.clazz(mapOf(fns to info.name, obf to InternalName.unchecked(ShortNames[i])))
        }

        // (root owner, member name, desc) -> shared obf name — overrides resolve
        // to the topmost declaring ancestor so the whole chain gets one name
        val inherited = mutableMapOf<Triple<InternalName, String, String>, String>()
        for (info in graph.classes.values.sortedBy { it.allParents.size }) {
            val cls = tree.clazz(mapOf(fns to info.name))
            val used = mutableMapOf<String, MutableSet<String>>() // desc -> taken names in this class
            fun nextFor(desc: String): String {
                // first name not already taken by a *same-desc* member — names get
                // reused across differing descriptors (ProGuard overloading)
                var i = 0
                var n: String
                do { n = ShortNames[i++] } while (n in (used[desc] ?: emptySet()))
                used.getOrPut(desc) { mutableSetOf() }.add(n)
                return n
            }

            for (mi in info.methods) {
                if (mi.name.value.startsWith("<")) continue
                val root = info.allParents.lastOrNull { p ->
                    p.methods.any { it.name == mi.name && it.descriptor == mi.descriptor }
                } ?: info
                val key = Triple(root.name, mi.name.value, mi.descriptor.value)
                val name = inherited.getOrPut(key) { nextFor(mi.descriptor.value) }
                used.getOrPut(mi.descriptor.value) { mutableSetOf() }.add(name)
                cls.method(mapOf(
                    fns to MethodNameAndDescriptor.unchecked("${mi.name.value};${mi.descriptor.value}"),
                    obf to MethodNameAndDescriptor.unchecked("$name;${mi.descriptor.value}"),
                ))
            }
            for (fi in info.fields) {
                val key = Triple(info.name, fi.name.value, fi.descriptor.value.value)
                val name = inherited.getOrPut(key) { nextFor(fi.descriptor.value.value) }
                used.getOrPut(fi.descriptor.value.value) { mutableSetOf() }.add(name)
                cls.field(mapOf(
                    fns to FieldNameAndDescriptor.unchecked("${fi.name.value};${fi.descriptor.value}"),
                    obf to FieldNameAndDescriptor.unchecked("$name;${fi.descriptor.value.value}"),
                ))
            }
        }
    }

}
