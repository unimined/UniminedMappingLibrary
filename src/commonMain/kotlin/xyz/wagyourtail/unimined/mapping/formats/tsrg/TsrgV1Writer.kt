package xyz.wagyourtail.unimined.mapping.formats.tsrg

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object TsrgV1Writer : FormatWriter {
    override fun write(envType: EnvType, append: (String) -> Unit): MappingVisitor {

        return EmptyMappingVisitor().delegator(object : NullDelegator() {
            lateinit var namespaces: List<Namespace>

            override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("Srg requires 2 namespaces")
                }
                this.namespaces = namespaces.map { Namespace(it) }
                default.visitHeader(delegate, *namespaces)
            }

            override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                val from = names[namespaces[0]] ?: return null
                val to = names[namespaces[1]] ?: return null
                append("${from.value} ${to.value}\n")
                return default.visitClass(delegate, names)
            }

            override fun visitField(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, FieldDescriptor?>>
            ): FieldVisitor? {
                val from = names[namespaces[0]] ?: return null
                val to = names[namespaces[1]] ?: return null
                append("\t${from.first} ${to.first}\n")
                return null
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                val from = names[namespaces[0]] ?: return null
                if (from.second == null) return null
                val to = names[namespaces[1]] ?: return null
                append("\t${from.first} ${from.second} ${to.first}\n")
                return null
            }

        })
    }

}