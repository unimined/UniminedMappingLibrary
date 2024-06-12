package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPv6ParamWriter : FormatWriter {

    fun String.maybeEscapeCol(): String {
        return if (this.contains(Regex("[\\n,]"))) this.escape(doubleQuote = true) else this
    }

    override fun write(envType: EnvType, append: (String) -> Unit): MappingVisitor {
        lateinit var ns: List<Namespace>

        return EmptyMappingVisitor().delegator(object : NullDelegator() {
            override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("MCPv6 requires 2 namespaces")
                }
                ns = namespaces.map { Namespace(it) }
                append("${namespaces[0]},${namespaces[1]},side,desc")
            }

            override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                return default.visitClass(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                return default.visitMethod(delegate, names)
            }

            override fun visitParameter(
                delegate: InvokableVisitor<*>,
                index: Int?,
                lvOrd: Int?,
                names: Map<Namespace, String>
            ): ParameterVisitor? {
                var src = names[ns[0]]
                var dst = names[ns[1]]
                if (src == null && dst == null) return null
                if (src == null) src = dst!!
                if (dst == null) dst = src
                val side = envType.ordinal
                append("\n${src.maybeEscapeCol()},${dst.maybeEscapeCol()},$side")
                return null
            }
        })
    }

}