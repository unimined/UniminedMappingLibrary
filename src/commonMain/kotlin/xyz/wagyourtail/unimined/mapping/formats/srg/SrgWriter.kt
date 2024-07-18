package xyz.wagyourtail.unimined.mapping.formats.srg

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object SrgWriter : FormatWriter {
    override fun write(append: (String) -> Unit, envType: EnvType): MappingVisitor {

        return EmptyMappingVisitor().delegator(object : NullDelegator() {
            lateinit var namespaces: List<Namespace>
            var currentClsNames: Map<Namespace, String>? = null

            override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("Srg requires 2 namespaces")
                }
                this.namespaces = namespaces.map { Namespace(it) }
                default.visitHeader(delegate, *namespaces)
            }

            override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
                val from = names[namespaces[0]]?.value?.substringBeforeLast('/')?.ifEmpty { "." } ?: return null
                val to = names[namespaces[1]]?.value?.substringBeforeLast('/')?.ifEmpty { "." } ?: return null
                append("PK: $from $to\n")
                return null
            }

            override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                val from = names[namespaces[0]] ?: return null
                val to = names[namespaces[1]] ?: return null
                currentClsNames = names.mapValues { it.value.value }
                append("CL: ${from.value} ${to.value}\n")
                return default.visitClass(delegate, names)
            }

            override fun visitField(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, FieldDescriptor?>>
            ): FieldVisitor? {
                val from = names[namespaces[0]]?.first ?: return null
                val to = names[namespaces[1]]?.first ?: return null
                append("FD: ${currentClsNames!!.getValue(namespaces[0])}/$from ${currentClsNames!!.getValue(namespaces[1])}/$to\n")
                return null
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                val from = names[namespaces[0]] ?: return null
                if (from.second == null) return null
                val to = names[namespaces[1]] ?: return null
                if (to.second == null) return null
                append("MD: ${currentClsNames!!.getValue(namespaces[0])}/${from.first} ${from.second} ${currentClsNames!!.getValue(namespaces[1])}/${to.first} ${to.second}\n")
                return null
            }

        })
    }

}