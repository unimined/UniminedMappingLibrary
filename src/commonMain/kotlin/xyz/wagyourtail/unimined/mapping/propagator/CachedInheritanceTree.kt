package xyz.wagyourtail.unimined.mapping.propagator

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree

class CachedInheritanceTree(tree: AbstractMappingTree, fns: Namespace, data: CharReader<*>): InheritanceTree(tree, fns) {

    companion object {

        fun write(tree: InheritanceTree, append: (String) -> Unit) {
            for (cls in tree.classes.values) {
                append(cls.name.toString())
                append("\t")
                append(cls.superType.toString())
                append("\t")
                append(cls.interfaces.joinToString("\t") { it.toString() })
                append("\n")

                for (method in cls.methods) {
                    append("\t")
                    append(AccessFlag.of(ElementType.METHOD, method.access).joinToString("|") { it.toString() })
                    append("\t")
                    append(method.name)
                    append("\t")
                    append(method.descriptor.toString())
                    append("\n")
                }
            }
        }

    }

    override val classes by lazy {
        val classes = mutableMapOf<InternalName, ClassInfo>()
        var ci: ClassInfo? = null
        while (!data.exhausted()) {
            if (data.peek() == '\n') {
                data.take()
                continue
            }
            var col = data.takeNext()!!
            var indent = 0
            while (col.isEmpty()) {
                indent++
                col = data.takeNext()!!
            }
            if (indent > 1) {
                throw IllegalArgumentException("expected method, found double indent")
            }
            if (indent == 0) {
                val cls = col
                val sup = data.takeNext()!!.ifEmpty { null }
                val intf = data.takeRemainingOnLine().map { InternalName.read(it) }
                ci = ClassInfo(InternalName.read(cls), sup?.let { InternalName.read(it) }, intf)
                classes[ci!!.name] = ci!!
            } else {
                val acc = col.split("|").map { AccessFlag.valueOf(it.uppercase()) }
                val name = data.takeNext()!!
                val desc = FieldOrMethodDescriptor.read(data.takeNext()!!)

                if (desc.isMethodDescriptor()) {
                    ci!!.methods.add(
                        MethodInfo(
                            name,
                            desc.getMethodDescriptor(),
                            AccessFlag.toInt(acc.toSet())
                        )
                    )
                } else {
                    ci!!.fields.add(
                        FieldInfo(
                            name,
                            desc.getFieldDescriptor(),
                            AccessFlag.toInt(acc.toSet())
                        )
                    )
                }
            }
        }
        classes
    }

}