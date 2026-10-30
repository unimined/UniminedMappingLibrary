package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * First-party implementation: MCP 6.0+ `packages.csv` (MCPBot exports, `mcp_stable` zips).
 */
object MCPv6PackageReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "packages.csv") return false
        return input.peek().readUtf8Line()?.startsWith("class,package") ?: false
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        val header = input.takeLine()
        if (!header.startsWith("class,package")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        with (into) {
            header(listOf(srcNs, dstNs))

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val cls = input.takeCol()
                val pkg = input.takeCol()

                pushed(MutableClassMapping(mapOf(
                    srcNs to InternalName.read("net/minecraft/src/$cls"),
                    dstNs to InternalName.read("$pkg/$cls")
                ))) {}            }
        }
        into.end()
    }
}
