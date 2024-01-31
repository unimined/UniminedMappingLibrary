package xyz.wagyourtail.unimined.mapping.formats.tsrg

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object TsrgV1Reader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (!fileName.endsWith(".tsrg")) return false
        return !(inputType.peek().readUtf8Line()?.startsWith("tsrg") ?: true)
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: MappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.visitHeader(srcNs.name, dstNs.name)

        var cls: ClassVisitor? = null

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val whitespace = input.takeWhitespace()

            if (whitespace.isEmpty()) {
                val srcName = input.takeNextLiteral(' ')
                val dstName = input.takeNextLiteral(' ')

                if (srcName == null || dstName == null) {
                    throw IllegalArgumentException("invalid line: $srcName $dstName")
                }

                cls = into.visitClass(mapOf(srcNs to InternalName.read(srcName), dstNs to InternalName.read(dstName)))
            } else {
                val srcName = input.takeNextLiteral(' ')!!
                val dst = input.takeNextLiteral(' ')!!
                if (dst.startsWith('(')) {
                    val dstName = input.takeNextLiteral(' ')!!
                    cls?.visitMethod(mapOf(srcNs to (srcName to MethodDescriptor.read(dst)), dstNs to (dstName to null)))
                } else {
                    cls?.visitField(mapOf(srcNs to (srcName to null), dstNs to (dst to null)))
                }
            }
        }

    }

}