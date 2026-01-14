package xyz.wagyourtail.unimined.mapping.formats.srg

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object SrgWriter : FormatWriter {

    override fun write(append: (String) -> Unit, envType: EnvType): RootMappingVisitor {

        return EmptyRootMappingVisitor().delegator(object : NullDelegator() {
            lateinit var namespaces: List<Namespace>
            var currentClsNames: Map<Namespace, String>? = null

            override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("Srg requires 2 namespaces")
                }
                this.namespaces = namespaces.toList()
                default.visitHeader(delegate, *namespaces)
            }

            override fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
                val from = names[namespaces[0]]?.value?.substringBeforeLast('/')?.ifEmpty { "." } ?: return null
                val to = names[namespaces[1]]?.value?.substringBeforeLast('/')?.ifEmpty { "." } ?: return null
                append("PK: $from $to\n")
                return null
            }

            override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
                val from = names[namespaces[0]] ?: return null
                val to = names[namespaces[1]] ?: return null
                currentClsNames = names.mapValues { it.value.value }
                append("CL: ${from.value} ${to.value}\n")
                return default.visitClass(delegate, names)
            }

            override fun visitField(
                delegate: ClassMappingVisitor,
                names: Map<Namespace, FieldNameAndDescriptor>
            ): FieldMappingVisitor? {
                val from = names[namespaces[0]]?.name ?: return null
                val to = names[namespaces[1]]?.name ?: return null
                append("FD: ${currentClsNames!!.getValue(namespaces[0])}/$from ${currentClsNames!!.getValue(namespaces[1])}/$to\n")
                return null
            }

            override fun visitMethod(
                delegate: ClassMappingVisitor,
                names: Map<Namespace, MethodNameAndDescriptor>
            ): MethodMappingVisitor? {
                val from = names[namespaces[0]] ?: return null
                if (!from.hasDescriptor) return null
                val to = names[namespaces[1]] ?: return null
                if (!to.hasDescriptor) return null
                append("MD: ${currentClsNames!!.getValue(namespaces[0])}/${from.name} ${from.descriptor} ${currentClsNames!!.getValue(namespaces[1])}/${to.name} ${to.descriptor}\n")
                return null
            }

        })
    }

}