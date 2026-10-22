package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
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

/**
 * reads the constructor file
 * used in MCPConfig (mc 1.12.2+)
 */
object MCPConfigExceptionsReader : FormatReader{

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "constructors.txt") return false
        val line = input.peek().readUtf8Line()?.split(" ") ?: return false
        // check matches InternalName/unqualifiedName methodDescriptor {InternalName}
        try {
            InternalName.read(line[0])
            MethodDescriptor.read(line[1])
            return true
        } catch (e: Exception) {
            return false
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
                val srcNs = Namespace(nsMapping["searge"] ?: "searge")

        with (into) {
            header(listOf(srcNs))
            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val line = input.takeLine().split(" ").iterator()
                val srcName = line.next()
                val srcCls = InternalName.read(srcName.substringBeforeLast('/'))
                val srcMethod = UnqualifiedName.read(srcName.substringAfterLast('/'))
                val srcDesc = MethodDescriptor.read(line.next())
                val exceptions = line.asSequence().map { InternalName.read(it) }.toList()
                pushed(MutableClassMapping(mapOf(srcNs to srcCls))) {
                    pushed(MutableMethodMapping(mapOf(srcNs to MethodNameAndDescriptor(srcMethod, srcDesc)))) {
                        for (exception in exceptions) {
                            pushed(ExceptionElement(AddRemove.ADD, exception, srcNs)) {}
                        }
                    }
                }
            }
        }
into.end()
    }


}