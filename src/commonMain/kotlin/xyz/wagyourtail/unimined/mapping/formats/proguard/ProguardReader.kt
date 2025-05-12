package xyz.wagyourtail.unimined.mapping.formats.proguard

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object ProguardReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (!fileName.endsWith(".txt")) return false
        // check that there's a -> in the file
        return input.peek().readUtf8(1024).contains("->")
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

            var cls: ClassVisitor? = null

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
                    val srcCls = InternalName.read(parts[0].trim().replace(".", "/"))
                    val dstCls = InternalName.read(parts[1].trim().removeSuffix(":").replace(".", "/"))
                    cls?.visitEnd()
                    cls = visitClass(mapOf(srcNs to srcCls, dstNs to dstCls))
                } else {
                    val parts = line.split("->")
                    val src = parts[0].trim().split(" ")
                    val dst = parts[1].trim()
                    val srcDesc = src[0].split(":")
                    if (src[1].endsWith(")")) {
                        // method
                        val srcRetVal = remapProguardParamDesc(srcDesc.last())
                        val srcName = src[1].substringBeforeLast("(")
                        val mDesc =
                            MethodDescriptor.read(remapProguardMethodDesc("(" + src[1].substringAfterLast("(")) + srcRetVal)
                        cls?.visitMethod(mapOf(srcNs to (srcName to mDesc), dstNs to (dst to null)))?.visitEnd()
                    } else {
                        // field
                        val fDesc = FieldDescriptor.read(remapProguardParamDesc(srcDesc.last()))
                        val srcName = src[1]
                        cls?.visitField(mapOf(srcNs to (srcName to fDesc), dstNs to (dst to null)))?.visitEnd()
                    }
                }
            }

            cls?.visitEnd()
        }

    }

}