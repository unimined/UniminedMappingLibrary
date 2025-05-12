package xyz.wagyourtail.unimined.mapping.formats.srg

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object PackageSrgReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "package.srg") return false
        return input.peek().readUtf8Line()?.let { line -> SrgReader.keys.none { line.startsWith(it) } } ?: false
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {

        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.use {
            visitHeader(srcNs.name, dstNs.name)

            while (!input.exhausted()) {
                input.takeWhitespace()
                val src = input.takeNextLiteral(' ') ?: continue
                val dst = input.takeNextLiteral(' ') ?: continue
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