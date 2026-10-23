package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.sink.pushed
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

object MCPConfigStaticMethodsReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "static_methods.txt") return false
        // single field_
        val line = input.peek().readUtf8Line() ?: return false
        return line.matches(Regex("^func_\\d+_\\w*$"))
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

        val statics = mutableSetOf<String>()
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val line = input.takeLine()
            if (line.startsWith("func_")) {
                statics.add(line)
            }
        }

        context?.emit(object : MappingSink by into {
            override fun push(element: MappingElement): MappingElement? {
                if (element is MethodMapping) {
                    val entries = element.emitEntries()
                    val name = entries[srcNs] ?: return null
                    if (!name.name.value.matches(Regex("^func_\\d+_\\w*$"))) return null
                    val mid = name.name.value.split("_")[1]
                    val desc = entries.mapNotNull { it.value.descriptor }.firstOrNull() ?: return null
                    val pushed = into.push(element) ?: return null
                    val params = desc.getParts().second
                    var lvtIdx = if (statics.contains(name.name.value)) 0 else 1
                    for (idx in params.indices) {
                        into.pushed(MutableParameterMapping(idx, lvtIdx, mapOf(srcNs to UnqualifiedName.read("p_${mid}_${lvtIdx}")))) {}
                        lvtIdx += params[idx].value.getWidth()
                    }
                    return pushed
                }
                return into.push(element)
            }
        })

    }

}