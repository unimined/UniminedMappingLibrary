package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.EnvAwareSink
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.env
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.sink.pushed
import xyz.wagyourtail.unimined.mapping.toNsMap
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * First-party implementation: MCP 6.0+ `fields.csv` (MCPBot exports, `mcp_stable` zips).
 */
object MCPv6FieldReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        return input.peek().readUtf8Line()?.startsWith("searge,name,side") ?: false
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
        // env-aware emit: the sink owns env semantics — joined target
        // gets `ns:env` columns, single-env target collapses tagged
        // rows into plain `ns` (and drops the other env's rows)
        val sink = if (into is EnvAwareSink) into else EnvAwareSink.sinkAs(into, envType)
                val header = input.takeLine()
        if (!header.startsWith("searge,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        // searge → (name, comment, rowEnv) — the row's `side` tag
        // flows to the sink's env routing
        val data = mutableMapOf<UnqualifiedName, Triple<UnqualifiedName, String?, EnvType>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = UnqualifiedName.read(input.takeCol()!!)
            val name = UnqualifiedName.read(input.takeCol()!!)
            val side = input.takeCol()!!
            val comment = input.takeCol()

            val rowEnv = when (side) {
                "0" -> EnvType.CLIENT
                "1" -> EnvType.SERVER
                else -> EnvType.JOINED
            }
            data[searge] = Triple(name, comment, rowEnv)

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.emit(object : MappingSink by sink {
            override fun header(namespaces: List<Namespace>) {
                sink.header((namespaces + setOf(srcNs, dstNs)).distinct())
            }

            override fun push(element: MappingElement): MappingElement? {
                if (element is FieldMapping) {
                    val entries = element.emitEntries()
                    val ns = entries[srcNs] ?: return null
                    val fdata = data[ns.name] ?: return null
                    val nameMap = entries.toNsMap()
                    nameMap[dstNs] = FieldNameAndDescriptor(fdata.first, null)
                    // the row's env tag routes through the sink — joined
                    // target → `ns:env` column, single-env → plain `ns`/drop
                    val rowSink = sink.env(fdata.third)
                    val pushed = rowSink.push(MutableFieldMapping(nameMap)) ?: return null
                    if (fdata.second != null) {
                        rowSink.pushed(JavadocElement(fdata.second!!, dstNs)) {}
                    }
                    return pushed
                }
                return sink.push(element)
            }
        })

    }

}