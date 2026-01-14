package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPv6ParamWriter : FormatWriter {

    fun String.maybeEscapeCol(): String {
        return if (this.contains(Regex("[\\n,]"))) this.escape(doubleQuote = true) else this
    }

    override fun write(append: (String) -> Unit, envType: EnvType): RootMappingVisitor {
        lateinit var ns: List<Namespace>

        return EmptyRootMappingVisitor().delegator(object : NullDelegator() {
            override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("MCPv6 requires 2 namespaces")
                }
                ns = namespaces.toList()
                append("${namespaces[0]},${namespaces[1]},side,desc")
            }

            override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
                return default.visitClass(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassMappingVisitor,
                names: Map<Namespace, MethodNameAndDescriptor>
            ): MethodMappingVisitor? {
                return default.visitMethod(delegate, names)
            }

            override fun visitParameter(
                delegate: InvokableMappingVisitor,
                index: Int?,
                lvOrd: Int?,
                names: Map<Namespace, UnqualifiedName>
            ): ParameterMappingVisitor? {
                var src = names[ns[0]]
                var dst = names[ns[1]]
                if (src == null && dst == null) return null
                if (src == null) src = dst!!
                if (dst == null) dst = src
                val side = envType.ordinal
                append("\n${src.value.maybeEscapeCol()},${dst.value.maybeEscapeCol()},$side")
                return null
            }
        })
    }

}