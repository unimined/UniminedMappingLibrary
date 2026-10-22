package xyz.wagyourtail.unimined.mapping.formats.mcp.v1

import okio.BufferedSource
import okio.use
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
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

/**
 * this reads the MCP 1-2.12 field csv files
 */
object MCPv1FieldReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        // check that 4th line starts with "class"
        input.peek().use {
            it.readUtf8Line()
            it.readUtf8Line()
            return it.readUtf8Line()?.startsWith("class") ?: false
        }
    }

    override suspend fun read(
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
        val l3 = input.takeLine()
        input.take()

        if (!l3.startsWith("Class")) {
            throw IllegalArgumentException("invalid header for older method csv")
        }

        val data = mutableMapOf<UnqualifiedName, Pair<UnqualifiedName, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            input.takeCol() // clientClsName
            input.takeCol() // empty
            val clientSrg = UnqualifiedName.read(input.takeCol()!!)

            input.takeCol() // serverClsName
            input.takeCol() // empty
            val serverSrg = UnqualifiedName.read(input.takeCol()!!)

            val fieldName = input.takeCol()
            val comment = input.takeCol()

            if (input.peek() != '\n') {
                input.takeRemainingCol()
            }

            if (fieldName.isNullOrEmpty()) {
                continue
            }

            if (envType == EnvType.CLIENT) {
                if (clientSrg.value == "*") continue
                data[clientSrg] = UnqualifiedName.read(fieldName) to comment
            }
            if (envType == EnvType.SERVER) {
                if (serverSrg.value == "*") continue
                data[serverSrg] = UnqualifiedName.read(fieldName) to comment
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.emit(object : MappingSink by into {
            override fun header(namespaces: List<Namespace>) {
                into.header((namespaces + setOf(srcNs, dstNs)).distinct())
            }

            override fun push(element: MappingElement): MappingElement? {
                if (element is FieldMapping) {
                    val entries = element.emitEntries()
                    val ns = entries[srcNs] ?: return null
                    val fData = data[ns.name] ?: return null
                    val nameMap = entries.toMutableMap()
                    nameMap[dstNs] = FieldNameAndDescriptor(fData.first, null)
                    val pushed = into.push(MutableFieldMapping(nameMap)) ?: return null
                    if (fData.second != null) {
                        into.pushed(JavadocElement(fData.second!!, dstNs)) {}
                    }
                    return pushed
                }
                return into.push(element)
            }
        })

    }
}