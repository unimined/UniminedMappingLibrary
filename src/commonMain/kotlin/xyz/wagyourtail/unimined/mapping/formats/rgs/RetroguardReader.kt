package xyz.wagyourtail.unimined.mapping.formats.rgs

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
 * This reader is for MCP's bastardization of the RetroGuard obfuscation config.
 * used until minecraft b1.5
 *
 * Because this one is special, and specifically for a minecraft format,
 * I'm going to hardcode the package mapping that's used from a random config file.
 * First-party implementation: https://github.com/ModCoderPack/Retroguard/blob/be337bcfce238db2c6fc8a6a4737eb9f6e73582a/src/main/java/com/rl/obf/RgsEntryType.java
 */
object RetroguardReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    private const val PKG = "net/minecraft/src/"
    val keys = setOf(".op", ".at", ".cl", ".me", ".fi")

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        val ext = fileName.endsWith(".rgs")
        val firstLine = input.readUtf8Line() ?: return ext
        return ext && keys.any { firstLine.startsWith(it) }
    }

    override fun getSide(fileName: String, input: BufferedSource): Set<EnvType> {
        return if (fileName.endsWith("-server.rgs")) {
            setOf(EnvType.SERVER, EnvType.JOINED)
        } else {
            setOf(EnvType.CLIENT, EnvType.JOINED)
        }
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

            // hardcoded package mapping
            pushed(MutablePackageMapping(mapOf(srcNs to PackageName.read(""), dstNs to PackageName.read(PKG)))) {}

            while (!input.exhausted()) {
                input.takeWhitespace()
                val key = input.takeNextLiteral(' ') ?: continue

                when (key) {
                    ".class_map" -> {
                        val srcName = InternalName.read(input.takeNextLiteral(' ')!!)
                        val dst = input.takeNextLiteral(' ')!!
                        val dstName = InternalName.read(if (dst.contains('/')) dst else "$PKG$dst")
                        pushed(MutableClassMapping(mapOf(srcNs to srcName, dstNs to dstName))) {}
                    }

                    ".field_map" -> {
                        val srcName = input.takeNextLiteral(' ')!!
                        val dstFd = UnqualifiedName.read(input.takeNextLiteral(' ')!!)
                        val srcCls = InternalName.read(srcName.substringBeforeLast('/'))
                        val srcFd = UnqualifiedName.read(srcName.substringAfterLast('/'))
                        pushed(MutableClassMapping(mapOf(srcNs to srcCls))) {
                            pushed(MutableFieldMapping(
                                mapOf(srcNs to srcFd.withFieldDesc(null), dstNs to dstFd.withFieldDesc(null))
                            )) {}
                        }
                    }

                    ".method_map" -> {
                        val srcName = input.takeNextLiteral(' ')!!
                        val srcDesc = MethodDescriptor.read(input.takeNextLiteral(' ')!!)
                        val dstMd = UnqualifiedName.read(input.takeNextLiteral(' ')!!)
                        val srcCls = InternalName.read(srcName.substringBeforeLast('/'))
                        val srcMd = UnqualifiedName.read(srcName.substringAfterLast('/'))

                        pushed(MutableClassMapping(mapOf(srcNs to srcCls))) {
                            pushed(MutableMethodMapping(
                                mapOf(srcNs to srcMd.withMethodDesc(srcDesc), dstNs to dstMd.withMethodDesc(null))
                            )) {}
                        }
                    }

                    else -> {} // ignore all others
                }
            }
        }
        into.end()

    }


}