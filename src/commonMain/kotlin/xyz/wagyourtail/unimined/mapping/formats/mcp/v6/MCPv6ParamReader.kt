package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.ParameterMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.sink.pushed
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * First-party implementation: MCP 6.0+ `params.csv` (MCPBot exports, `mcp_stable` zips).
 */
object MCPv6ParamReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "params.csv") return false
        return input.peek().readUtf8Line()?.startsWith("param,name,side") ?: false
    }

    private data class FieldData(
        val source: String,
        val target: String,
        val comment: String?
    )

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val header = input.takeLine()
        if (!header.startsWith("param,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        val data = mutableMapOf<UnqualifiedName, UnqualifiedName>()
        val paramsByNumber = mutableMapOf<Int, MutableMap<UnqualifiedName, UnqualifiedName>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = UnqualifiedName.read(input.takeCol()!!)
            val name = UnqualifiedName.read(input.takeCol()!!)
            val side = input.takeCol()!!

            if (side == "2" || side.toInt() == envType.ordinal || envType == EnvType.JOINED) {
                data[searge] = name

                if (searge.value.matches(Regex("p_\\d+_\\d+_"))) {
                    val mid = searge.value.split('_')[1].toInt()
                    paramsByNumber.getOrPut(mid) { mutableMapOf() }[searge] = name
                }
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.emit(object : MappingSink by into {
            override fun header(namespaces: List<Namespace>) {
                into.header((namespaces + setOf(srcNs, dstNs)).distinct())
            }

            override fun push(element: MappingElement): MappingElement? {
                when (element) {
                    is ParameterMapping -> {
                        val searge = element.names[srcNs] ?: return null
                        val name = data[searge] ?: return null
                        return into.push(MutableParameterMapping(element.index, element.lvOrd, mapOf(dstNs to name)))
                    }
                    is MethodMapping -> {
                        val pushed = into.push(element) ?: return null
                        val srcName = element.emitEntries()[srcNs]?.name?.value
                        if (srcName?.matches(Regex("func_\\d+_.+")) == true) {
                            val mid = srcName.split('_')[1].toIntOrNull()
                            if (mid != null) {
                                for ((searge, name) in paramsByNumber[mid] ?: emptyMap()) {
                                    val lvOrd = searge.value.split('_')[2].toInt()
                                    into.pushed(MutableParameterMapping(null, lvOrd, mapOf(dstNs to name))) {}
                                }
                            }
                        }
                        return pushed
                    }
                    else -> return into.push(element)
                }
            }
        })

    }

}
