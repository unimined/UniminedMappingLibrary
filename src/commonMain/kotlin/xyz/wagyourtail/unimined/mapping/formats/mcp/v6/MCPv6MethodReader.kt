package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed
import xyz.wagyourtail.unimined.mapping.sink.emit

object MCPv6MethodReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "methods.csv") return false
        return input.peek().readUtf8Line()?.startsWith("searge,name,side") ?: false
    }

    private data class FieldData(
        val source: String,
        val target: String,
        val comment: String?
    )

    override suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val header = input.takeLine()
        if (!header.startsWith("searge,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        val data = mutableMapOf<UnqualifiedName, Pair<UnqualifiedName, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = UnqualifiedName.read(input.takeCol()!!)
            val name = UnqualifiedName.read(input.takeCol()!!)
            val side = input.takeCol()!!
            val comment = input.takeCol()

            if (side == "2" || side.toInt() == envType.ordinal || envType == EnvType.JOINED) {
                data[searge] = name to comment
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.emit(object : MappingSink by into {
            override fun header(namespaces: List<Namespace>) {
                into.header((namespaces + setOf(srcNs, dstNs)).distinct())
            }

            override fun push(element: MappingElement): MappingElement? {
                if (element is MethodMapping) {
                    val entries = element.emitEntries()
                    val ns = entries[srcNs] ?: return null
                    val mData = data[ns.name] ?: return null
                    val nameMap = entries.toMutableMap()
                    nameMap[dstNs] = MethodNameAndDescriptor(mData.first, null)
                    val pushed = into.push(MutableMethodMapping(nameMap)) ?: return null
                    if (mData.second != null) {
                        into.pushed(JavadocElement(mData.second!!, dstNs)) {}
                    }
                    return pushed
                }
                return into.push(element)
            }
        })

    }

}