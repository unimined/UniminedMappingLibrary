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
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.RootMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object TsrgV1Reader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (!fileName.endsWith(".tsrg")) return false
        return !(input.peek().readUtf8Line()?.startsWith("tsrg") ?: true)
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: RootMappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.use {
            visitHeader(srcNs.name, dstNs.name)

            var cls: ClassMappingVisitor? = null

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
                    cls?.visitEnd()
                    cls = visitClass(mapOf(srcNs to InternalName.read(srcName), dstNs to InternalName.read(dstName)))
                } else {
                    val srcName = UnqualifiedName.read(input.takeNextLiteral(' ')!!)
                    val dst = input.takeNextLiteral(' ')!!
                    if (dst.startsWith('(')) {
                        val dstName = UnqualifiedName.read(input.takeNextLiteral(' ')!!)
                        cls?.visitMethod(
                            mapOf(
                                srcNs to srcName.withMethodDesc(MethodDescriptor.read(dst)),
                                dstNs to dstName.withMethodDesc(null)
                            )
                        )?.visitEnd()
                    } else {
                        cls?.visitField(mapOf(srcNs to srcName.withFieldDesc(null), dstNs to UnqualifiedName.read(dst).withFieldDesc(null)))?.visitEnd()
                    }
                }
            }

            cls?.visitEnd()
        }

    }

}