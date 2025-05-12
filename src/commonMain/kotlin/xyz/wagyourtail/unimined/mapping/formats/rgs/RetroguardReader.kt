package xyz.wagyourtail.unimined.mapping.formats.rgs

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * This reader is for MCP's bastardization of the RetroGuard obfuscation config.
 * used until minecraft b1.5
 *
 * Because this one is special, and specifically for a minecraft format,
 * I'm going to hardcode the package mapping that's used from a random config file.
 */
object RetroguardReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    private const val PKG = "net/minecraft/src/"
    val keys = setOf(".op", ".at", ".cl", ".me", ".fi")

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
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

            // hardcoded package mapping
            visitPackage(mapOf(srcNs to PackageName.read(""), dstNs to PackageName.read(PKG)))

            while (!input.exhausted()) {
                input.takeWhitespace()
                val key = input.takeNextLiteral(' ') ?: continue

                when (key) {
                    ".class_map" -> {
                        val srcName = InternalName.read(input.takeNextLiteral(' ')!!)
                        val dst = input.takeNextLiteral(' ')!!
                        val dstName = InternalName.read(if (dst.contains('/')) dst else "$PKG$dst")
                        visitClass(mapOf(srcNs to srcName, dstNs to dstName))?.visitEnd()
                    }

                    ".field_map" -> {
                        val srcName = input.takeNextLiteral(' ')!!
                        val dstFd = input.takeNextLiteral(' ')!!
                        val srcCls = InternalName.read(srcName.substringBeforeLast('/'))
                        val srcFd = srcName.substringAfterLast('/')
                        into.visitClass(mapOf(srcNs to srcCls))?.use {
                            visitField(
                                mapOf(srcNs to (srcFd to null), dstNs to (dstFd to null))
                            )?.visitEnd()
                        }
                    }

                    ".method_map" -> {
                        val srcName = input.takeNextLiteral(' ')!!
                        val srcDesc = MethodDescriptor.read(input.takeNextLiteral(' ')!!)
                        val dstMd = input.takeNextLiteral(' ')!!
                        val srcCls = InternalName.read(srcName.substringBeforeLast('/'))
                        val srcMd = srcName.substringAfterLast('/')

                        into.visitClass(mapOf(srcNs to srcCls))?.use {
                            visitMethod(
                                mapOf(srcNs to (srcMd to srcDesc), dstNs to (dstMd to null))
                            )?.visitEnd()
                        }
                    }

                    else -> {} // ignore all others
                }
            }
        }

    }


}