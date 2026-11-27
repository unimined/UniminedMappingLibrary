package xyz.wagyourtail.unimined.mapping.remapper.nil

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper

/**
 * `keepAllMappings` emission path — every class/member row of the
 * namespace pair, regardless of usage. Shares [NilMappingsWriter]'s
 * emission shape with the tracked scan; the widen block still comes
 * from the usage scan (widening is inherently usage-driven — NilGradle
 * behaves the same).
 *
 * Identity member rows (from-sig == to-sig) are skipped as in the
 * tracked path; class rows emit even when memberless since the runtime
 * lookup walk needs them for inner classes and the cost is small.
 */
object NilFullTreeEmitter {

    fun emit(tree: MappingTree, from: Namespace, to: Namespace): NilMappings {
        val remapper = tree.remapper(from, to)
        val result = NilMappings()
        for (cls in tree.classes) {
            val fromName = cls.names[from]?.value ?: continue
            val toName = cls.names[to]?.value ?: fromName
            val entry = NilClassEntry(fromName, toName)
            for (method in cls.methods) {
                val fn = method.entries[from] ?: continue
                if (fn.name.value.startsWith("<")) continue
                val fDesc = fn.descriptor?.value ?: continue
                val targetName = method.names[to]?.value ?: fn.name.value
                val tDesc = method.entries[to]?.descriptor?.value
                    ?: remapper.map(MethodDescriptor.read(fDesc)).value
                val fromSig = fn.name.value + fDesc
                val toSig = targetName + tDesc
                if (fromSig != toSig) entry.methods[fromSig] = toSig
            }
            for (field in cls.fields) {
                val fn = field.entries[from] ?: continue
                val fDesc = fn.descriptor?.toString() ?: continue
                val targetName = field.names[to]?.value ?: fn.name.value
                val tDesc = field.entries[to]?.descriptor?.toString()
                    ?: remapper.map(FieldDescriptor.read(fDesc)).toString()
                val fromSig = "${fn.name.value}:$fDesc"
                val toSig = "$targetName:$tDesc"
                if (fromSig != toSig) entry.fields[fromSig] = toSig
            }
            result.classes[fromName] = entry
        }
        closeAncestors(result, tree, from, to)
        return result
    }

    /** inner entries are unreachable without their outer chain — lorenz
     *  resolves `$` level by level */
    private fun closeAncestors(result: NilMappings, tree: MappingTree, from: Namespace, to: Namespace) {
        val queue = ArrayDeque(result.classes.keys.toList())
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            val slash = name.lastIndexOf('/')
            val dollar = name.lastIndexOf('$')
            if (dollar <= slash) continue
            val parent = name.substring(0, dollar)
            if (parent !in result.classes) {
                val mapped = tree.getClass(from, InternalName.unchecked(parent))
                    ?.names?.get(to)?.value ?: parent
                result.classes[parent] = NilClassEntry(parent, mapped)
                queue.add(parent)
            }
        }
    }

}
