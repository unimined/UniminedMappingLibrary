package xyz.wagyourtail.unimined.mapping.formats.mcp.v1

import okio.BufferedSource
import okio.use
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.sink.pushed
import xyz.wagyourtail.unimined.mapping.toNsMap
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * this reads the MCP 1-2.12 method csv files
 * First-party implementation: MCP pre-1.3 `methods.csv` schema — no standalone upstream parser; corpus is schema-derived.
 */
object MCPv1MethodReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "methods.csv") return false
        // check that 4th line starts with "class"
        input.peek().use {
            it.readUtf8Line()
            it.readUtf8Line()
            it.readUtf8Line()
            return it.readUtf8Line()?.startsWith("class") ?: false
        }
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                input.takeLine()
        input.take()
        input.takeLine()
        input.take()
        input.takeLine()
        input.take()
        val l4 = input.takeLine()
        input.take()

        if (!l4.startsWith("class")) {
            throw IllegalArgumentException("invalid header for older method csv")
        }

        val data = mutableMapOf<UnqualifiedName, Pair<UnqualifiedName, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            input.takeCol() // clientClsName
            val clientSrg = UnqualifiedName.read(input.takeCol()!!)

            input.takeCol() // serverClsName
            val serverSrg = UnqualifiedName.read(input.takeCol()!!)

            val methodName = input.takeCol()
            val comment = input.takeCol()

            if (input.peek() != '\n') {
                input.takeRemainingCol()
            }

            if (methodName.isNullOrEmpty()) {
                continue
            }

            if (envType == EnvType.CLIENT) {
                if (clientSrg.value == "*") continue
                data[clientSrg] = UnqualifiedName.read(methodName) to comment
            }
            if (envType == EnvType.SERVER) {
                if (serverSrg.value == "*") continue
                data[serverSrg] = UnqualifiedName.read(methodName) to comment
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
                    val nameMap = entries.toNsMap()
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
