package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.ParameterMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.WildcardMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.FormatWriterSettings
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

object MCPv6ParamWriter : FormatWriter {

    fun String.maybeEscapeCol(): String {
        return if (this.contains(Regex("[\\n,]"))) this.escape(doubleQuote = true) else this
    }

    override fun write(append: (String) -> Unit, settings: FormatWriterSettings): MappingSink {
        val envType = settings.envType
        lateinit var ns: List<Namespace>

        return object : MappingSink {
            override fun header(namespaces: List<Namespace>) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("MCPv6 requires 2 namespaces")
                }
                ns = namespaces
                append("${namespaces[0]},${namespaces[1]},side,desc")
            }

            override fun push(element: MappingElement): MappingElement? {
                when (element) {
                    is ParameterMapping -> {
                        var src = element.names[ns[0]]
                        var dst = element.names[ns[1]]
                        if (src == null && dst == null) return null
                        if (src == null) src = dst!!
                        if (dst == null) dst = src
                        val side = envType.ordinal
                        append("\n${src.value.maybeEscapeCol()},${dst.value.maybeEscapeCol()},$side")
                    }
                    is MethodMapping, is WildcardMapping, is ClassMapping -> {}
                    else -> return null
                }
                return element
            }

            override fun push(meta: MetadataElement): MetadataElement? = null

            override fun pop(element: MappingElement) {}
        }

    }

}