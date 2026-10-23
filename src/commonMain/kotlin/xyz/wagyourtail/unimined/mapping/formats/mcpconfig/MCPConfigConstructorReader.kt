package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * reads the constructor file
 * used in MCPConfig (mc 1.12.2+)
 */
object MCPConfigConstructorReader : FormatReader{

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "constructors.txt") return false
        val line = input.peek().readUtf8Line()?.split(" ")
        if (line?.size != 3) return false
        // check matches \d+ internalName methodDescriptor
        if (line[0].toIntOrNull() == null) return false
        try {
            InternalName.read(line[1])
            MethodDescriptor.read(line[2])
            return true
        } catch (e: Exception) {
            return false
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
                val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        with (into) {
            header(listOf(srcNs))
            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val line = input.takeLine().split(" ")
                val id = line[0].toInt()
                val srcCls = InternalName.read(line[1])
                val srcMethod = MethodDescriptor.read(line[2])
                if (line.size > 3) {
                    throw IllegalStateException("expected 3 elements on line, found more")
                }
                pushed(MutableClassMapping(mapOf(srcNs to srcCls))) {
                    pushed(MutableMethodMapping(mapOf(srcNs to MethodNameAndDescriptor(UnqualifiedName.init, srcMethod)))) {
                        var lvtIdx = 1
                        val parts = srcMethod.getParts().second
                        for (idx in parts.indices) {
                            pushed(MutableParameterMapping(idx, lvtIdx, mapOf(srcNs to UnqualifiedName.read("p_i${id}_${idx + 1}_")))) {}
                            lvtIdx += parts[idx].value.getWidth()
                        }
                    }
                }
            }
        }
into.end()
    }


}