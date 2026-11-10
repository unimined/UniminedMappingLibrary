package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.EnvAwareSink
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.env
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
        // env-aware emit: the sink owns env semantics — joined target
        // gets `ns:env` columns, single-env target collapses tagged
        // rows into plain `ns` (and drops the other env's rows)
        val sink = if (into is EnvAwareSink) into else EnvAwareSink.sinkAs(into, envType)
                val header = input.takeLine()
        if (!header.startsWith("param,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        // searge → (name, rowEnv) — the row's `side` tag flows to the
        // sink's env routing
        val data = mutableMapOf<UnqualifiedName, Pair<UnqualifiedName, EnvType>>()
        val paramsByNumber = mutableMapOf<Int, MutableMap<UnqualifiedName, Pair<UnqualifiedName, EnvType>>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = UnqualifiedName.read(input.takeCol()!!)
            val name = UnqualifiedName.read(input.takeCol()!!)
            val side = input.takeCol()!!

            val rowEnv = when (side) {
                "0" -> EnvType.CLIENT
                "1" -> EnvType.SERVER
                else -> EnvType.JOINED
            }
            data[searge] = name to rowEnv

            if (searge.value.matches(Regex("p_\\d+_\\d+_"))) {
                val mid = searge.value.split('_')[1].toInt()
                paramsByNumber.getOrPut(mid) { mutableMapOf() }[searge] = name to rowEnv
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.emit(object : MappingSink by sink {
            override fun header(namespaces: List<Namespace>) {
                sink.header((namespaces + setOf(srcNs, dstNs)).distinct())
            }

            override fun push(element: MappingElement): MappingElement? {
                when (element) {
                    is ParameterMapping -> {
                        // sided rows carry their src name under `srcNs:env`
                        val searge = element.names.entries.firstOrNull { (ns, _) ->
                            ns.splitEnv().first == srcNs
                        }?.value ?: return null
                        val (name, env) = data[searge] ?: return null
                        return sink.env(env).push(MutableParameterMapping(element.index, element.lvOrd, mapOf(dstNs to name)))
                    }
                    is MethodMapping -> {
                        val pushed = sink.push(element) ?: return null
                        val srcName = element.emitEntries().entries.firstOrNull { (ns, _) ->
                            ns.splitEnv().first == srcNs
                        }?.value?.name?.value
                        if (srcName?.matches(Regex("func_\\d+_.+")) == true) {
                            val mid = srcName.split('_')[1].toIntOrNull()
                            if (mid != null) {
                                for ((searge, p) in paramsByNumber[mid] ?: emptyMap()) {
                                    val (name, env) = p
                                    val lvOrd = searge.value.split('_')[2].toInt()
                                    sink.env(env).pushed(MutableParameterMapping(null, lvOrd, mapOf(dstNs to name))) {}
                                }
                            }
                        }
                        return pushed
                    }
                    else -> return sink.push(element)
                }
            }
        })

    }

}
