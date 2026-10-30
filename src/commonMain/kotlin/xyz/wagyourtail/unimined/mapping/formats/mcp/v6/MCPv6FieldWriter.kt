package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.FormatWriterSettings
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * First-party implementation: MCP 6.0+ `fields.csv` (MCPBot exports, `mcp_stable` zips).
 */
object MCPv6FieldWriter : FormatWriter {

    var writeComments = true

    /** writer tunable — emit comment columns */
    val WRITE_COMMENTS = FormatReaderSettings.SettingsKey<Boolean>("writeComments")

    fun String.maybeEscapeCol(): String {
        return if (this.contains(Regex("[\\n,]"))) this.escape(doubleQuote = true) else this
    }

    override fun write(append: (String) -> Unit, settings: FormatWriterSettings): MappingSink {
        val envType = settings.envType
        val writeComments = settings[WRITE_COMMENTS] ?: this.writeComments
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
                    is FieldMapping -> {
                        val names = element.emitEntries()
                        var src = names[ns[0]]
                        var dst = names[ns[1]]
                        if (src == null && dst == null) return null
                        if (src == null) src = dst!!
                        if (dst == null) dst = src
                        val side = envType.ordinal
                        append("\n${src.name.value.maybeEscapeCol()},${dst.name.value.maybeEscapeCol()},$side,")
                    }
                    is ClassMapping -> {}
                    else -> return null
                }
                return element
            }

            override fun push(meta: MetadataElement): MetadataElement? {
                if (meta is JavadocElement) {
                    if (!writeComments) return null
                    append(meta.value.toString().maybeEscapeCol())
                }
                return null
            }

            override fun pop(element: MappingElement) {}
        }

    }

}