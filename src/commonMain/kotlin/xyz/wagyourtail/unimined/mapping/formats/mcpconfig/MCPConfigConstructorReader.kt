package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * reads the constructor file
 * used in MCPConfig (mc 1.12.2+)
 */
object MCPConfigConstructorReader : FormatReader{

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "constructors.txt") return false
        val line = inputType.peek().readUtf8Line()?.split(' ')
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
            val line = input.takeLine().split(' ')
            val id = line[0].toInt()
            val srcCls = InternalName.read(line[1])
            val srcMethod = MethodDescriptor.read(line[2])
            if (line.size > 3) {
                throw IllegalStateException("expected 3 elements on line, found more")
            }
            into.visitClass(mapOf(srcNs to srcCls))?.visitMethod(mapOf(srcNs to ("<init>" to srcMethod)))?.let {
                var lvtIdx = 1
                val parts = srcMethod.getParts().second
                for (idx in parts.indices) {
                    it.visitParameter(idx, lvtIdx, mapOf(srcNs to "p_i${id}_${idx + 1}"))
                    lvtIdx += parts[idx].value.getWidth()
                }
            }
        }
    }


}