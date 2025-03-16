package xyz.wagyourtail.unimined.mapping.propagator

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader.takeNextFixed
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader.takeRemainingFixedOnLine
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter.maybeEscape
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree

class CachedInheritanceTree(tree: AbstractMappingTree, data: CharReader<*>): InheritanceTree(tree) {

    companion object {

        fun write(tree: InheritanceTree, append: (String) -> Unit) {
            append("umf_it\t1\t0\n")
            append(tree.fns.name)
            append("\n")
            for (cls in tree.classes.values) {
                append(cls.name.toString().maybeEscape())
                append("\t")
                append(cls.superType.toString().maybeEscape())
                append("\t")
                append(cls.interfaces.joinToString("\t") { it.toString().maybeEscape() })
                append("\n")

                for (field in cls.fields) {
                    append("\t")
                    val access = AccessFlag.of(ElementType.METHOD, field.access).joinToString("|") { it.toString() }
                    append(access.maybeEscape())
                    append("\t")
                    append(field.name.maybeEscape())
                    append("\t")
                    append(field.descriptor.toString().maybeEscape())
                    append("\n")
                }

                for (method in cls.methods) {
                    append("\t")
                    val access = AccessFlag.of(ElementType.METHOD, method.access).joinToString("|") { it.toString() }
                    append(access.maybeEscape())
                    append("\t")
                    append(method.name.maybeEscape())
                    append("\t")
                    append(method.descriptor.toString().maybeEscape())
                    append("\n")
                }
            }
        }

    }

    override val fns: Namespace by lazy {
        val sig = data.takeNext()
        if (sig != "umf_it") {
            throw IllegalArgumentException("expected umf_it, found ${sig}")
        }
        val major = data.takeNext()
        if (major != "1") {
            throw IllegalArgumentException("expected version 1, found ${major}")
        }
        val minor = data.takeNext()
        if (minor != "0") {
            throw IllegalArgumentException("expected version 0, found ${minor}")
        }
        data.takeNonNewlineWhitespace()
        data.expect('\n')

        val ns = data.takeLine()
        data.takeNonNewlineWhitespace()
        data.expect('\n')
        Namespace(ns)
    }

    override val classes by lazy {
        val classes = mutableMapOf<InternalName, ClassInfo>()
        var ci: ClassInfo? = null

        fns

        while (!data.exhausted()) {
            val indent = data.takeNonNewlineWhitespace().length

            if (data.peek() == '\n') {
                data.take()
                continue
            }

            if (indent > 1) {
                throw IllegalArgumentException("expected method, found double indent")
            }
            if (indent == 0) {
                val cls = data.takeNextFixed()!!
                val sup = data.takeNextFixed()?.ifEmpty { null }
                val intf = data.takeRemainingFixedOnLine().map { InternalName.read(it!!) }
                ci = ClassInfo(InternalName.read(cls), sup?.let { InternalName.read(it) }, intf)
                classes[ci!!.name] = ci!!
            } else {
                val acc = data.takeNextFixed()!!.split("|").map { AccessFlag.valueOf(it.uppercase()) }
                val name = data.takeNextFixed()!!
                val desc = FieldOrMethodDescriptor.read(data.takeNextFixed()!!)

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