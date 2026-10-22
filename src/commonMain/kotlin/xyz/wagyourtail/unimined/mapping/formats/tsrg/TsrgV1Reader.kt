package xyz.wagyourtail.unimined.mapping.formats.tsrg

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
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

object TsrgV1Reader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (!fileName.endsWith(".tsrg")) return false
        return !(input.peek().readUtf8Line()?.startsWith("tsrg") ?: true)
    }

    override suspend fun read(
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

            var cls: MappingElement? = null

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
                    cls?.let { pop(it) }
                    cls = push(MutableClassMapping(mapOf(srcNs to InternalName.read(srcName), dstNs to InternalName.read(dstName))))
                } else {
                    val srcName = UnqualifiedName.read(input.takeNextLiteral(' ')!!)
                    val dst = input.takeNextLiteral(' ')!!
                    if (dst.startsWith('(')) {
                        val dstName = UnqualifiedName.read(input.takeNextLiteral(' ')!!)
                        pushed(MutableMethodMapping(
                            mapOf(
                                srcNs to srcName.withMethodDesc(MethodDescriptor.read(dst)),
                                dstNs to dstName.withMethodDesc(null)
                            )
                        )) {}
                    } else {
                        pushed(MutableFieldMapping(mapOf(srcNs to srcName.withFieldDesc(null), dstNs to UnqualifiedName.read(dst).withFieldDesc(null)))) {}
                    }
                }
            }

            cls?.let { pop(it) }
        }
        into.end()

    }

}