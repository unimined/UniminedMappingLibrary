package xyz.wagyourtail.unimined.mapping.formats.tiny.v2

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*

/**
 * FabricMC's tinyv2 format.
 */
object TinyV2Reader : FormatReader {

    /**
     * translate escapes function that only does the ones actually allowed by tinyv2
     */
    private fun String.translateEscapes(leinient: Boolean = false): String {
        if (this.isEmpty() || !this.contains("\\")) {
            return this
        }
        return buildString {
            var i = 0
            while (i < this@translateEscapes.length) {
                val c = this@translateEscapes[i++]
                if (c == '\\') {
                    if (i >= this@translateEscapes.length) throw IllegalArgumentException("Invalid escape, hit end of string")
                    when (val n = this@translateEscapes[i++]) {
                        '\\' -> append('\\')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        '0' -> append('\u0000')
                        else -> {
                            throw IllegalArgumentException(
                                "Invalid escape: ${n.toString().escape(true)} in \"${this}\" at $i"
                            )
                        }
                    }
                } else {
                    append(c)
                }
            }
        }
    }

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return input.peek().readUtf8Line()?.startsWith("tiny\t2\t0\t") ?: false
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        val v = input.takeNextLiteral()
        if (v != "tiny") throw IllegalArgumentException("Invalid tinyv2 file")
        if (input.takeNextLiteral() != "2") throw IllegalArgumentException("Invalid tinyv2 file")
        if (input.takeNextLiteral() != "0") throw IllegalArgumentException("Invalid tinyv2 file")
        val namespaces = mutableListOf<Namespace>()
        while (true) {
            namespaces.add(input.takeNextLiteral()?.let { Namespace(nsMapping[it] ?: it) } ?: break)
        }
        val metadataKeys = mutableMapOf<String, String?>()
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            if (input.peek() != '\t') {
                break
            }
            input.take()
            val key = input.takeNextLiteral()!!
            val value = input.takeNextLiteral()
            metadataKeys[key] = value
        }
        val escaped = metadataKeys.contains("escaped-names")

        into.use {

            visitHeader(*namespaces.map { it.name }.toTypedArray())
            val stack = mutableListOf<BaseVisitor<*>?>(into)
            outer@ while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                var col = input.takeNextLiteral() ?: continue
                var indent = 0
                while (col.isEmpty()) {
                    indent++
                    col = input.takeNextLiteral() ?: continue@outer
                }
                if (indent > stack.size - 1) {
                    throw IllegalArgumentException("Invalid tinyv2 file, found double indent")
                }
                while (indent < stack.size - 1) {
                    stack.removeLast()?.visitEnd()
                }
                val type = col
                val last = stack.last()
                val next: BaseVisitor<*>? = when (type) {
                    "c" -> {
                        if (indent == 0) {
                            // class
                            val names = mutableListOf<String>()
                            while (true) {
                                names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                    ?: break)
                            }
                            val nameIter = names.iterator()
                            val nsIter = namespaces.iterator()
                            val nameMap = mutableMapOf<Namespace, InternalName>()
                            nameMap[nsIter.next()] = InternalName.read(nameIter.next())
                            while (nameIter.hasNext()) {
                                val ns = nsIter.next()
                                val name = nameIter.next()
                                if (name.isNotEmpty()) {
                                    nameMap[ns] = InternalName.read(name)
                                }
                            }
                            last as MappingVisitor?
                            last?.visitClass(nameMap)
                        } else {
                            // comment
                            val comment = input.takeLine().removePrefix("\t").translateEscapes()
                            last as JavadocParentNode?
                            last?.visitJavadoc(comment, namespaces.first(), namespaces.drop(1).toSet())
                        }
                    }

                    "f" -> {
                        // field
                        val desc = input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }!!
                        val names = mutableListOf<String>()
                        while (true) {
                            names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                ?: break)
                        }
                        val nameIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, Pair<String, FieldDescriptor?>>()
                        nameMap[nsIter.next()] = nameIter.next() to FieldDescriptor.read(desc)
                        while (nameIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = nameIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = name to null
                            }
                        }
                        last as ClassVisitor?
                        last?.visitField(nameMap)
                    }

                    "m" -> {
                        // method
                        val desc = input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }!!
                        val names = mutableListOf<String>()
                        while (true) {
                            names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                ?: break)
                        }
                        val nameIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, Pair<String, MethodDescriptor?>>()
                        nameMap[nsIter.next()] = nameIter.next() to MethodDescriptor.read(desc)
                        while (nameIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = nameIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = name to null
                            }
                        }
                        last as ClassVisitor?
                        last?.visitMethod(nameMap)
                    }

                    "p" -> {
                        // parameter
                        val lvOrd = input.takeNextLiteral()?.toIntOrNull()
                        val names = mutableListOf<String>()
                        while (true) {
                            names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                ?: break)
                        }
                        val nameIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, String>()
                        while (nameIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = nameIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = name
                            }
                        }
                        last as MethodVisitor?
                        last?.visitParameter(null, lvOrd, nameMap)
                    }

                    else -> {
                        throw IllegalArgumentException("Invalid tinyv2 file, unknown type $type")
                    }
                }
                stack.add(next)
            }

            while (stack.size > 1) {
                stack.removeLast()?.visitEnd()
            }
        }
    }

}