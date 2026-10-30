package xyz.wagyourtail.unimined.mapping.formats.mcp.v3

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
 * this reads the classes.csv from mcp 3.0-5.6
 * First-party implementation: MCP 3.0–5.6 `classes.csv` schema — no standalone upstream parser; corpus is schema-derived.
 */
object MCPv3ClassesReader: FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "classes.csv") return false
        return input.peek().readUtf8Line()?.startsWith("\"name\",\"notch\"") ?: false
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val srcNs = Namespace(nsMapping["notch"] ?: "notch")
        val dstNs = Namespace(nsMapping["searge"] ?: "searge")

        val header = input.takeLine()
        if (header != "\"name\",\"notch\",\"supername\",\"package\",\"side\"") {
            throw IllegalArgumentException("invalid header: $header")
        }

        with (into) {
            header(listOf(srcNs, dstNs))

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val dstName = input.takeCol()!!
                val srcName = input.takeCol()!!
                input.takeCol() // supername
                val pkg = input.takeCol()!!
                val side = input.takeCol()!!

                if (side == "2" || side.toInt() == envType.ordinal) {
                    val dstCls =
                        InternalName.read(if (pkg.isNotEmpty()) "$pkg/$dstName" else dstName)
                    val srcCls =
                        InternalName.read(if (dstName == srcName && pkg.isNotEmpty()) "${pkg}/${srcName}" else srcName)
                    pushed(MutableClassMapping(mapOf(srcNs to srcCls, dstNs to dstCls))) {}
                }
            }
        }
into.end()
    }

}