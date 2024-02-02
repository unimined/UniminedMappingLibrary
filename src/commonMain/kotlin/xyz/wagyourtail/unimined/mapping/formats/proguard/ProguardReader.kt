package xyz.wagyourtail.unimined.mapping.formats.proguard

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object ProguardReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (!fileName.endsWith(".txt")) return false
        // check that there's a -> in the file
        return inputType.peek().readUtf8(1024).contains("->")
    }

    override fun getSide(fileName: String, inputType: BufferedSource): Set<EnvType> {
        if (fileName.substringAfterLast('/') == "client.txt") return setOf(EnvType.CLIENT, EnvType.JOINED)
        if (fileName.substringAfterLast('/') == "server.txt") return setOf(EnvType.SERVER, EnvType.JOINED)
        return super.getSide(fileName, inputType)
    }

    fun remapProguardParamDesc(desc: String): String {
        var i = 0
        while (desc.endsWith("[]")) {
            i++
            desc.substring(0, desc.length-2)
        }
        return "[".repeat(i) + when (desc) {
            "byte" -> return "B"
            "char" -> return "C"
            "double" -> return "D"
            "float" -> return "F"
            "int" -> return "I"
            "long" -> return "J"
            "short" -> return "S"
            "boolean" -> return "Z"
            "void" -> return "V"
            else -> "L${desc.replace('.', '/')};"
        }
    }

    fun remapProguardMethodDesc(desc: String): String {
        val params = desc.substring(0, desc.length-1).split(',')
        return buildString {
            append('(')
            for (param in params) {
                append(remapProguardParamDesc(param))
            }
            append(')')
        }
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: MappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.visitHeader(srcNs.name, dstNs.name)

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
                val srcCls = InternalName.read(parts[0].trim())
                val dstCls = InternalName.read(parts[1].trim().removeSuffix(":"))
                cls = into.visitClass(mapOf(srcNs to srcCls, dstNs to dstCls))
            } else {
                val parts = line.split("->")
                val src = parts[0].trim().split(' ')
                val dst = parts[1].trim()
                val srcDesc = src[0].split(':')
                if (srcDesc.size > 1 && srcDesc[0].toIntOrNull() != null) {
                    // method
                    srcDesc[0].toInt() // fromLine
                    srcDesc[1].toInt() // toLine
                    val srcRetVal = remapProguardParamDesc(srcDesc[2])
                    val srcName = src[1].substringBeforeLast("(")
                    val srcDesc = MethodDescriptor.read(remapProguardMethodDesc("(" + src[1].substringAfterLast("(")) + srcRetVal)
                    cls?.visitMethod(mapOf(srcNs to (srcName to srcDesc), dstNs to (dst to null)))
                } else {
                    // field
                    val srcDesc = FieldDescriptor.read(src[0])
                    val srcName = src[1]
                    cls?.visitField(mapOf(srcNs to (srcName to srcDesc), dstNs to (dst to null)))
                }
            }
        }

    }

}