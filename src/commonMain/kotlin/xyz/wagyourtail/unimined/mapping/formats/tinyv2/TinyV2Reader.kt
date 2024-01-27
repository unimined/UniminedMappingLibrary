package xyz.wagyourtail.unimined.mapping.formats.tinyv2

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.checked
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.translateEscapes
import xyz.wagyourtail.unimined.mapping.visitor.*

/**
 * FabricMC's tinyv2 format.
 */
object TinyV2Reader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return inputType.peek().readUtf8Line()?.startsWith("tiny\t2\t0\t") ?: false
    }

    override suspend fun read(envType: EnvType, input: CharReader, context: MappingTree?, into: MappingVisitor, nsMapping: Map<String, String>) {
        val v = input.takeNextLiteral()
        if (v != "tiny") throw IllegalArgumentException("Invalid tinyv2 file")
        if (input.takeNextLiteral() != "2") throw IllegalArgumentException("Invalid tinyv2 file")
        if (input.takeNextLiteral() != "0") throw IllegalArgumentException("Invalid tinyv2 file")
        val namespaces = mutableListOf<Namespace>()
        while (true) {
            namespaces.add(input.takeNextLiteral()?.let { Namespace(nsMapping[it] ?: it) } ?: break)
        }
        into.visitHeader(*namespaces.map { it.name }.toTypedArray())
        val stack = mutableListOf<BaseVisitor<*>?>(into)
        outer@while (!input.exhausted()) {
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
                stack.removeLast()
            }
            val type = col
            val last = stack.last()
            val next: BaseVisitor<*>? = when (type) {
                "c" -> {
                    if (indent == 0) {
                        // class
                        val names = mutableListOf<InternalName>()
                        while (true) {
                            names.add(InternalName.read(input.takeNextLiteral() ?: break))
                        }
                        checked<MappingVisitor, ClassVisitor>(last) {
                            visitClass(namespaces.zip(names).toMap())
                        }
                    } else {
                        // comment
                        val comment = input.takeLine().removePrefix("\t").translateEscapes()
                        checked<MemberVisitor<*>, CommentVisitor>(last) {
                            visitComment(namespaces.associateWith { comment })
                        }
                    }
                }
                "f" -> {
                    // field
                    val desc = input.takeNextLiteral()!!
                    val names = mutableListOf<String>()
                    while (true) {
                        names.add(input.takeNextLiteral() ?: break)
                    }
                    val nameIter = names.iterator()
                    val nsIter = namespaces.iterator()
                    val nameMap = mutableMapOf<Namespace, Pair<String, FieldDescriptor?>>()
                    nameMap.put(nsIter.next(), nameIter.next() to FieldDescriptor.read(desc))
                    while (nameIter.hasNext()) {
                        nameMap[nsIter.next()] = nameIter.next() to null
                    }
                    checked<ClassVisitor, FieldVisitor>(last) {
                        visitField(nameMap)
                    }
                }
                "m" -> {
                    // method
                    val desc = input.takeNextLiteral()!!
                    val names = mutableListOf<String>()
                    while (true) {
                        names.add(input.takeNextLiteral() ?: break)
                    }
                    val nameIter = names.iterator()
                    val nsIter = namespaces.iterator()
                    val nameMap = mutableMapOf<Namespace, Pair<String, MethodDescriptor?>>()
                    nameMap.put(nsIter.next(), nameIter.next() to MethodDescriptor.read(desc))
                    while (nameIter.hasNext()) {
                        nameMap[nsIter.next()] = nameIter.next() to null
                    }
                    checked<ClassVisitor, MethodVisitor>(last) {
                        visitMethod(nameMap)
                    }
                }
                "p" -> {
                    // parameter
                    val lvOrd = input.takeNextLiteral()?.toIntOrNull()
                    val names = mutableListOf<String>()
                    while (true) {
                        names.add(input.takeNextLiteral() ?: break)
                    }
                    val nameIter = names.iterator()
                    val nsIter = namespaces.iterator()
                    val nameMap = mutableMapOf<Namespace, String>()
                    nameMap[nsIter.next()] = nameIter.next()
                    while (nameIter.hasNext()) {
                        nameMap[nsIter.next()] = nameIter.next()
                    }
                    checked<MethodVisitor, ParameterVisitor>(last) {
                        visitParameter(null, lvOrd, nameMap)
                    }
                }
                else -> {
                    throw IllegalArgumentException("Invalid tinyv2 file, unknown type $type")
                }
            }
            stack.add(next)
        }
    }

}