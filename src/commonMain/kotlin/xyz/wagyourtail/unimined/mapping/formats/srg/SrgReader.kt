package xyz.wagyourtail.unimined.mapping.formats.srg

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutablePackageMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * MCP's SRG format.
 * First-party implementation: SrgUtils `IMappingFile.Format.SRG` (https://github.com/MinecraftForge/SrgUtils/blob/3c679e5/src/main/java/net/minecraftforge/srgutils/IMappingFile.java).
 */
object SrgReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    val keys = setOf("PK:", "CL:", "FD:", "MD:")

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        val ext = fileName.endsWith(".srg")
        val firstLine = input.readUtf8Line() ?: return ext
        return ext && keys.any { firstLine.startsWith(it) }
    }

    override fun getSide(fileName: String, input: BufferedSource): Set<EnvType> {
        if (fileName.substringAfterLast('/') == "client.srg") return setOf(EnvType.CLIENT, EnvType.JOINED)
        if (fileName.substringAfterLast('/') == "server.srg") return setOf(EnvType.SERVER, EnvType.JOINED)
        return super.getSide(fileName, input)
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
                val key = input.takeNextLiteral(' ') ?: continue
                if (key.startsWith("#")) {
                    input.takeLine()
                    continue
                }
                when (key) {
                    "PK:" -> {
                        val src = input.takeNextLiteral(' ')!!
                        val dst = input.takeNextLiteral(' ')!!
                        val srcFix = PackageName.read(if (src == ".") "" else "${src}/")
                        val dstFix = PackageName.read(if (dst == ".") "" else "${dst}/")
                        pushed(MutablePackageMapping(mapOf(srcNs to srcFix, dstNs to dstFix))) {}
                    }

                    "CL:" -> {
                        val src = input.takeNextLiteral(' ')!!
                        val dst = input.takeNextLiteral(' ')!!
                        pushed(MutableClassMapping(mapOf(srcNs to InternalName.read(src), dstNs to InternalName.read(dst)))) {}
                    }

                    "FD:" -> {
                        val src = input.takeNextLiteral(' ')!!
                        val dst = input.takeNextLiteral(' ')!!
                        val srcClass = src.substringBeforeLast('/')
                        val dstClass = dst.substringBeforeLast('/')
                        val srcField = UnqualifiedName.read(src.substringAfterLast('/'))
                        val dstField = UnqualifiedName.read(dst.substringAfterLast('/'))
                        pushed(
                            MutableClassMapping(
                                mapOf(
                                    srcNs to InternalName.read(srcClass),
                                    dstNs to InternalName.read(dstClass)
                                )
                            )
                        ) {
                            pushed(
                                MutableFieldMapping(mapOf(srcNs to srcField.withFieldDesc(null), dstNs to dstField.withFieldDesc(null)))
                            ) {}
                        }
                    }

                    "MD:" -> {
                        val src = input.takeNextLiteral(' ')!!
                        val srcDesc = MethodDescriptor.read(input.takeNextLiteral(' ')!!)
                        val dst = input.takeNextLiteral(' ')!!
                        val dstDesc = MethodDescriptor.read(input.takeNextLiteral(' ')!!)
                        val srcClass = src.substringBeforeLast('/')
                        val dstClass = dst.substringBeforeLast('/')
                        val srcMethod = UnqualifiedName.read(src.substringAfterLast('/'))
                        val dstMethod = UnqualifiedName.read(dst.substringAfterLast('/'))
                        pushed(
                            MutableClassMapping(
                                mapOf(
                                    srcNs to InternalName.read(srcClass),
                                    dstNs to InternalName.read(dstClass)
                                )
                            )
                        ) {
                            pushed(
                                MutableMethodMapping(mapOf(srcNs to srcMethod.withMethodDesc(srcDesc), dstNs to dstMethod.withMethodDesc(dstDesc)))
                            ) {}
                        }
                    }

                    else -> throw IllegalArgumentException("Unknown key $key")
                }

            }
        }
into.end()

    }

}