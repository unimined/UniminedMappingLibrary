package xyz.wagyourtail.unimined.mapping.formats.tinyv2

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.checked
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.translateEscapes
import xyz.wagyourtail.unimined.mapping.visitor.*

object TinyV2Reader : FormatReader {

    override fun isFormat(fileName: String, inputType: BufferedSource): Boolean {
        return inputType.peek().readUtf8Line()?.startsWith("tiny\t2\t0\t") ?: false
    }

    fun BufferedSource.nextCol(): String? {
        if (exhausted()) return null
        if (peek().readUtf8CodePoint().checkedToChar() == '\n') {
            return null
        }
        return Buffer().use {
            while (!exhausted()) {
                val b = peek().readUtf8CodePoint()
                if (b.checkedToChar() == '\n') break
                val c = readUtf8CodePoint()
                if (c.checkedToChar() == '\t') break
                it.writeUtf8CodePoint(c)
            }
            it.readUtf8()
        }
    }

    override fun read(inputType: BufferedSource, into: MappingVisitor, unnamedNamespaceNames: List<String>) {
        val v = inputType.nextCol()
        if (v != "tiny") throw IllegalArgumentException("Invalid tinyv2 file")
        if (inputType.nextCol() != "2") throw IllegalArgumentException("Invalid tinyv2 file")
        if (inputType.nextCol() != "0") throw IllegalArgumentException("Invalid tinyv2 file")

        val namespaces = mutableListOf<Namespace>()
        while (true) {
            namespaces.add(Namespace(inputType.nextCol() ?: break))
        }
        into.visitHeader(*namespaces.map { it.name }.toTypedArray())
        val stack = mutableListOf<BaseVisitor<*>?>(into)
        outer@while (!inputType.exhausted()) {
            if (inputType.peek().readUtf8CodePoint().checkedToChar() == '\n') {
                inputType.readUtf8CodePoint()
                continue
            }
            var col = inputType.nextCol() ?: continue
            var indent = 0
            while (col.isEmpty()) {
                indent++
                col = inputType.nextCol() ?: continue@outer
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
                            names.add(InternalName.read(inputType.nextCol() ?: break))
                        }
                        checked<MappingVisitor, ClassVisitor>(last) {
                            visitClass(namespaces.zip(names).toMap())
                        }
                    } else {
                        // comment
                        val comment = inputType.readUtf8Line()!!.removePrefix("\t").translateEscapes()
                        checked<MemberVisitor<*>, CommentVisitor>(last) {
                            visitComment(namespaces.associateWith { comment })
                        }
                    }
                }
                "f" -> {
                    // field
                    val desc = inputType.nextCol()!!
                    val names = mutableListOf<String>()
                    while (true) {
                        names.add(inputType.nextCol() ?: break)
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
                    val desc = inputType.nextCol()!!
                    val names = mutableListOf<String>()
                    while (true) {
                        names.add(inputType.nextCol() ?: break)
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
                    val lvOrd = inputType.nextCol()?.toIntOrNull()
                    val names = mutableListOf<String>()
                    while (true) {
                        names.add(inputType.nextCol() ?: break)
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