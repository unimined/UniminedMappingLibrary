package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ExceptionType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * reads the constructor file
 * used in MCPConfig (mc 1.12.2+)
 */
object MCPConfigExceptionsReader : FormatReader{

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "constructors.txt") return false
        val line = inputType.peek().readUtf8Line()?.split(' ') ?: return false
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
        envType: EnvType,
        input: CharReader,
        context: MappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        into.visitHeader(srcNs.name)
        while (!input.exhausted()) {
            val line = input.takeLine().split(' ').iterator()
            val srcName = line.next()
            val srcCls = InternalName.read(srcName.substringBeforeLast('/'))
            val srcMethod = srcName.substringAfterLast('/')
            val srcDesc = MethodDescriptor.read(line.next())
            val exceptions = line.asSequence().map { InternalName.read(it) }.toList()
            into.visitClass(mapOf(srcNs to srcCls))?.visitMethod(mapOf(srcNs to (srcMethod to srcDesc)))?.let {
                for (exception in exceptions) {
                    it.visitException(ExceptionType.ADD, exception, srcNs, setOf(srcNs))
                }
            }
        }
    }


}