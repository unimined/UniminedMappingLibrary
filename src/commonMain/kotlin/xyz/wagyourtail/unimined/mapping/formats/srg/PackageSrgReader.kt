package xyz.wagyourtail.unimined.mapping.formats.srg

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object PackageSrgReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "package.srg") return false
        return inputType.peek().readUtf8Line()?.let { line -> SrgReader.keys.none { line.startsWith(it) } } ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.use {
            visitHeader(srcNs.name, dstNs.name)

            while (!input.exhausted()) {
                input.takeWhitespace()
                val src = input.takeNextLiteral(sep = ' ') ?: continue
                val dst = input.takeNextLiteral(sep = ' ') ?: continue
                visitPackage(
                    mapOf(
                        srcNs to PackageName.read(if (src == "./") "" else src),
                        dstNs to PackageName.read(if (dst == "./") "" else dst)
                    )
                )?.visitEnd()
            }
        }

    }

}