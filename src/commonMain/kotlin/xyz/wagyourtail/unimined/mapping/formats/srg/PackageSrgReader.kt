package xyz.wagyourtail.unimined.mapping.formats.srg

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MutablePackageMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

object PackageSrgReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "package.srg") return false
        return input.peek().readUtf8Line()?.let { line -> SrgReader.keys.none { line.startsWith(it) } } ?: false
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
        
        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        with (into) {
            header(listOf(srcNs, dstNs))

            while (!input.exhausted()) {
                input.takeWhitespace()
                val src = input.takeNextLiteral(' ') ?: continue
                val dst = input.takeNextLiteral(' ') ?: continue
                pushed(MutablePackageMapping(
                    mapOf(
                        srcNs to PackageName.read(if (src == "./") "" else src),
                        dstNs to PackageName.read(if (dst == "./") "" else dst)
                    )
                )) {}
            }
        }
into.end()

    }

}