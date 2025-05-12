package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * reads the constructor file
 * used in MCPConfig (mc 1.12.2+)
 */
object MCPConfigAccessReader : FormatReader{

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
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
                val access = AccessFlag.valueOf(line.next().uppercase())
                val srcCls = InternalName.read(line.next())
                val srcName = line.next()
                val srcDesc = MethodDescriptor.read(line.next())
                if (line.hasNext()) {
                    throw IllegalStateException("expected 4 elements on line, found more")
                }
                visitClass(mapOf(srcNs to srcCls))?.use {
                    visitMethod(mapOf(srcNs to (srcName to srcDesc)))?.use {
                        visitAccess(
                            AccessType.ADD,
                            access,
                            AccessConditions.ALL,
                            setOf(srcNs)
                        )?.visitEnd()
                    }
                }
            }
        }
    }
}

