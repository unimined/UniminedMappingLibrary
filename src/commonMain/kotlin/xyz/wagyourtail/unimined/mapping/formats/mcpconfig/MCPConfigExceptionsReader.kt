package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * reads the constructor file
 * used in MCPConfig (mc 1.12.2+)
 */
object MCPConfigExceptionsReader : FormatReader{

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
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
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val srcNs = Namespace(nsMapping["searge"] ?: "searge")

        into.use {
            visitHeader(srcNs.name)
            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val line = input.takeLine().split(" ").iterator()
                val srcName = line.next()
                val srcCls = InternalName.read(srcName.substringBeforeLast('/'))
                val srcMethod = srcName.substringAfterLast('/')
                val srcDesc = MethodDescriptor.read(line.next())
                val exceptions = line.asSequence().map { InternalName.read(it) }.toList()
                visitClass(mapOf(srcNs to srcCls))?.use {
                    visitMethod(mapOf(srcNs to (srcMethod to srcDesc)))?.use {
                        for (exception in exceptions) {
                            visitException(ExceptionType.ADD, exception, srcNs, setOf(srcNs))?.visitEnd()
                        }
                    }
                }
            }
        }
    }


}