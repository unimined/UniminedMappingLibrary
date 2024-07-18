package xyz.wagyourtail.unimined.mapping.formats.srg

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * MCP's SRG format.
 */
object SrgReader : FormatReader {

    val keys = setOf("PK:", "CL:", "FD:", "MD:")

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        val ext = fileName.endsWith(".srg")
        val firstLine = input.readUtf8Line() ?: return ext
        return ext && keys.any { firstLine.startsWith(it) }
    }

    override fun getSide(fileName: String, input: BufferedSource): Set<EnvType> {
        if (fileName == "client.srg") return setOf(EnvType.CLIENT, EnvType.JOINED)
        if (fileName == "server.srg") return setOf(EnvType.SERVER, EnvType.JOINED)
        return super.getSide(fileName, input)
    }

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.use {
            visitHeader(srcNs.name, dstNs.name)

            while (!input.exhausted()) {
                input.takeWhitespace()
                val key = input.takeNextLiteral(sep = ' ') ?: continue
                when (key) {
                    "PK:" -> {
                        val src = input.takeNextLiteral(sep = ' ')!!
                        val dst = input.takeNextLiteral(sep = ' ')!!
                        val srcFix = PackageName.read(if (src == ".") "" else "${src}/")
                        val dstFix = PackageName.read(if (dst == ".") "" else "${dst}/")
                        visitPackage(mapOf(srcNs to srcFix, dstNs to dstFix))?.visitEnd()
                    }

                    "CL:" -> {
                        val src = input.takeNextLiteral(sep = ' ')!!
                        val dst = input.takeNextLiteral(sep = ' ')!!
                        visitClass(mapOf(srcNs to InternalName.read(src), dstNs to InternalName.read(dst)))?.visitEnd()
                    }

                    "FD:" -> {
                        val src = input.takeNextLiteral(sep = ' ')!!
                        val dst = input.takeNextLiteral(sep = ' ')!!
                        val srcClass = src.substringBeforeLast('/')
                        val dstClass = dst.substringBeforeLast('/')
                        val srcField = src.substringAfterLast('/')
                        val dstField = dst.substringAfterLast('/')
                        into.visitClass(
                            mapOf(
                                srcNs to InternalName.read(srcClass),
                                dstNs to InternalName.read(dstClass)
                            )
                        )?.use {
                            visitField(
                                mapOf(srcNs to (srcField to null), dstNs to (dstField to null))
                            )
                        }
                    }

                    "MD:" -> {
                        val src = input.takeNextLiteral(sep = ' ')!!
                        val srcDesc = MethodDescriptor.read(input.takeNextLiteral(sep = ' ')!!)
                        val dst = input.takeNextLiteral(sep = ' ')!!
                        val dstDesc = MethodDescriptor.read(input.takeNextLiteral(sep = ' ')!!)
                        val srcClass = src.substringBeforeLast('/')
                        val dstClass = dst.substringBeforeLast('/')
                        val srcMethod = src.substringAfterLast('/')
                        val dstMethod = dst.substringAfterLast('/')
                        into.visitClass(
                            mapOf(
                                srcNs to InternalName.read(srcClass),
                                dstNs to InternalName.read(dstClass)
                            )
                        )?.use {
                            visitMethod(
                                mapOf(srcNs to (srcMethod to srcDesc), dstNs to (dstMethod to dstDesc))
                            )
                        }
                    }

                    else -> throw IllegalArgumentException("Unknown key $key")
                }

            }
        }

    }

}