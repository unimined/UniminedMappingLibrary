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
        // machine-generated input rides unchecked factories when flagged
        val unchk = unchecked || settings.unchecked
        val internalName: (String) -> InternalName =
            if (unchk) InternalName::unchecked else InternalName::read
        val unqualifiedName: (String) -> UnqualifiedName =
            if (unchk) UnqualifiedName::unchecked else UnqualifiedName::read
        val packageName: (String) -> PackageName =
            if (unchk) PackageName::unchecked else PackageName::read
        val methodDesc: (String) -> MethodDescriptor =
            if (unchk) MethodDescriptor::unchecked else MethodDescriptor::read
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
                        val srcFix = packageName(if (src == ".") "" else "${src}/")
                        val dstFix = packageName(if (dst == ".") "" else "${dst}/")
                        pushed(MutablePackageMapping(mapOf(srcNs to srcFix, dstNs to dstFix))) {}
                    }

                    "CL:" -> {
                        val src = input.takeNextLiteral(' ')!!
                        val dst = input.takeNextLiteral(' ')!!
                        pushed(MutableClassMapping(mapOf(srcNs to internalName(src), dstNs to internalName(dst)))) {}
                    }

                    "FD:" -> {
                        val src = input.takeNextLiteral(' ')!!
                        val dst = input.takeNextLiteral(' ')!!
                        val srcClass = src.substringBeforeLast('/')
                        val dstClass = dst.substringBeforeLast('/')
                        val srcField = unqualifiedName(src.substringAfterLast('/'))
                        val dstField = unqualifiedName(dst.substringAfterLast('/'))
                        pushed(
                            MutableClassMapping(
                                mapOf(
                                    srcNs to internalName(srcClass),
                                    dstNs to internalName(dstClass)
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
                        val srcDesc = methodDesc(input.takeNextLiteral(' ')!!)
                        val dst = input.takeNextLiteral(' ')!!
                        val dstDesc = methodDesc(input.takeNextLiteral(' ')!!)
                        val srcClass = src.substringBeforeLast('/')
                        val dstClass = dst.substringBeforeLast('/')
                        val srcMethod = unqualifiedName(src.substringAfterLast('/'))
                        val dstMethod = unqualifiedName(dst.substringAfterLast('/'))
                        pushed(
                            MutableClassMapping(
                                mapOf(
                                    srcNs to internalName(srcClass),
                                    dstNs to internalName(dstClass)
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