package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * reads the constructor file
 * used in MCPConfig (mc 1.12.2+)
 */
object MCPConfigAccessReader : FormatReader{

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "access.txt") return false
        // check matches
        // PUBLIC InternalName UnqualifiedName MethodDescriptor
        val line = input.peek().readUtf8Line()?.split(" ")
        if (line == null || line.size != 4) return false
        if (line[0] != "PUBLIC") return false
        try {
            InternalName.read(line[1])
            UnqualifiedName.read(line[2])
            MethodDescriptor.read(line[3])
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
                val line = input.takeLine().split(" ").iterator()
                val access = AccessFlag.valueOf(line.next().uppercase())
                val srcCls = InternalName.read(line.next())
                val srcName = UnqualifiedName.read(line.next())
                val srcDesc = MethodDescriptor.read(line.next())
                if (line.hasNext()) {
                    throw IllegalStateException("expected 4 elements on line, found more")
                }
                pushed(MutableClassMapping(mapOf(srcNs to srcCls))) {
                    pushed(MutableMethodMapping(mapOf(srcNs to MethodNameAndDescriptor(srcName, srcDesc)))) {
                        pushed(AccessElement(
                            AddRemove.ADD,
                            access,
                            AccessConditions.ALL,
                        )) {}
                    }
                }
            }
        }
into.end()
    }
}

