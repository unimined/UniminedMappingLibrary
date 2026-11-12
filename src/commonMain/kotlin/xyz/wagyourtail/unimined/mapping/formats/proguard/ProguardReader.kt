package xyz.wagyourtail.unimined.mapping.formats.proguard

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * First-party implementation: ProGuard ReTrace mapping reader (https://github.com/Guardsquare/proguard); Mojang `client_mappings.txt` is the canonical producer. Spec: https://www.guardsquare.com/manual/tools/retrace
 */
object ProguardReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (!fileName.endsWith(".txt")) return false
        // check that there's a -> in the first 1KiB (or the whole file
        // when smaller — readUtf8(n) throws on short inputs)
        val peek = input.peek()
        val sample = if (peek.request(1024)) peek.readUtf8(1024)
        else peek.readUtf8()
        return sample.contains("->")
    }

    override fun getSide(fileName: String, input: BufferedSource): Set<EnvType> {
        if (fileName.substringAfterLast('/') == "client.txt") return setOf(EnvType.CLIENT, EnvType.JOINED)
        if (fileName.substringAfterLast('/') == "server.txt") return setOf(EnvType.SERVER, EnvType.JOINED)
        return super.getSide(fileName, input)
    }

    private fun remapProguardParamDesc(fancyDesc: String): String {
        var i = 0
        var desc = fancyDesc
        while (desc.endsWith("[]")) {
            i++
            desc = desc.substring(0, desc.length-2)
        }
        return "[".repeat(i) + when (desc) {
            "byte" -> "B"
            "char" -> "C"
            "double" -> "D"
            "float" -> "F"
            "int" -> "I"
            "long" -> "J"
            "short" -> "S"
            "boolean" -> "Z"
            "void" -> "V"
            else -> "L${desc.replace(".", "/")};"
        }
    }

    private fun remapProguardMethodDesc(desc: String): String {
        if (desc == "()") return desc
        if (desc[0] != '(' || desc[desc.length-1] != ')') throw IllegalArgumentException("Invalid method descriptor")
        val params = desc.substring(1, desc.length-1).split(",")
        return buildString {
            append('(')
            for (param in params) {
                append(remapProguardParamDesc(param))
            }
            append(')')
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
        // machine-generated input (Mojang client/server mappings) rides
        // unchecked factories when flagged — per-row JVMS revalidation
        // otherwise dominates the parse
        val unchk = unchecked || settings.unchecked
        val internalName: (String) -> InternalName =
            if (unchk) InternalName::unchecked else InternalName::read
        val unqualifiedName: (String) -> UnqualifiedName =
            if (unchk) UnqualifiedName::unchecked else UnqualifiedName::read
        val methodDesc: (String) -> MethodDescriptor =
            if (unchk) MethodDescriptor::unchecked else MethodDescriptor::read
        val fieldDesc: (String) -> FieldDescriptor =
            if (unchk) FieldDescriptor::unchecked else FieldDescriptor::read

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
                val indent = input.takeWhitespace()
                val line = input.takeLine()
                if (line.startsWith("#")) continue
                if (indent.isEmpty()) {
                    val parts = line.split("->")
                    val srcCls = internalName(parts[0].trim().replace(".", "/"))
                    val dstCls = internalName(parts[1].trim().removeSuffix(":").replace(".", "/"))
                    cls?.let { pop(it) }
                    cls = push(MutableClassMapping(mapOf(srcNs to srcCls, dstNs to dstCls)))
                } else {
                    val parts = line.split("->")
                    val src = parts[0].trim().split(" ")
                    val dst = unqualifiedName(parts[1].trim())
                    val srcDesc = src[0].split(":")
                    if (src[1].endsWith(")")) {
                        // method
                        val srcRetVal = remapProguardParamDesc(srcDesc.last())
                        val srcName = unqualifiedName(src[1].substringBeforeLast("("))
                        val mDesc =
                            methodDesc(remapProguardMethodDesc("(" + src[1].substringAfterLast("(")) + srcRetVal)
                        pushed(MutableMethodMapping(mapOf(srcNs to srcName.withMethodDesc(mDesc), dstNs to dst.withMethodDesc(null)))) {}
                    } else {
                        // field
                        val fDesc = fieldDesc(remapProguardParamDesc(srcDesc.last()))
                        val srcName = unqualifiedName(src[1])
                        pushed(MutableFieldMapping(mapOf(srcNs to srcName.withFieldDesc(fDesc), dstNs to dst.withFieldDesc(null)))) {}
                    }
                }
            }

            cls?.let { pop(it) }
        }
        into.end()

    }

}