package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node.InnerClassNode
import xyz.wagyourtail.unimined.mapping.util.*
import xyz.wagyourtail.unimined.mapping.visitor.*

object UMFReader : FormatReader<BufferedSource> {

    fun String.indentCount(): Int {
        var count = 0
        for (c in this) {
            when (c) {
                ' ' -> count++
                '\t' -> count += 4
                '\n' -> count = 0
                else -> throw IllegalArgumentException("Invalid indent character $c")
            }
        }
        return count
    }

    inline fun <reified T, U> checked(value: Any?, action: T.() -> U?): U? {
        if (value == null) return null
        if (value !is T) {
            throw IllegalArgumentException("Expected ${T::class.simpleName}, found ${value?.let { it::class.simpleName}}")
        }
        return action(value)
    }

    fun fixValue(value: String): String? {
        val count = value.count { it == '_' }
        if (count == value.length) {
            if (count == 1) {
                return null
            }
            return "_".repeat(count - 1)
        }
        return value
    }

    override fun read(inputType: BufferedSource, into: MappingTree) {
        var token = inputType.takeNext()
        if (token.second.lowercase() != "umf") {
            throw IllegalArgumentException("Invalid UMF file, expected UMF header found ${token.second}")
        }
        token = inputType.takeNext()
        if (token.second != "1") {
            throw IllegalArgumentException("unsupported UMF major version ${token.second}")
        }
        token = inputType.takeNext()
        if (token.second != "0") {
            throw IllegalArgumentException("unsupported UMF minor version ${token.second}")
        }

        val extensions = inputType.takeRemainingOnLine()
        // TODO: check extensions

        inputType.takeWhitespace()
        val namespaces = inputType.takeRemainingOnLine().map { Namespace(it.second) }.toMutableList()
        into.mergeNs(namespaces)

        fun getNamespace(i: Int): Namespace {
            while (i !in namespaces.indices) {
                namespaces.add(into.nextUnnamedNs())
            }
            return namespaces[i]
        }

        val visitStack = mutableListOf<BaseVisitor<*>?>(into)
        val indentStack = mutableListOf(-1)

        while (!inputType.exhausted()) {
            val indent = inputType.takeWhitespace().indentCount()
            val line = inputType.takeLineAsBuffer()
            token = line.takeNext()
            if (token.second.length != 1) {
                throw IllegalArgumentException("Invalid entry type ${token.second}")
            }
            val entryType = EntryType.byKey[token.second.first()] ?: throw IllegalArgumentException("Invalid entry type ${token.second}")
            while (indent <= indentStack.last()) {
                visitStack.removeLast()
                indentStack.removeLast()
            }
            val last = visitStack.last()
            val next: BaseVisitor<*>? = when (entryType) {
                EntryType.CLASS -> {
                    checked<MappingVisitor, ClassVisitor>(last) {
                        val names = line.takeRemainingOnLine().map { fixValue(it.second) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                            getNamespace(idx) to InternalName.read(name)
                        }
                        visitClass(names)
                    }
                }
                EntryType.METHOD -> {
                    checked<ClassVisitor, MethodVisitor>(last) {
                        val names = line.takeRemainingOnLine().map { fixValue(it.second) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                            val nd = NameAndDescriptor.read(name).getParts()
                            getNamespace(idx) to (nd.first.value to nd.second?.getMethodDescriptor())
                        }
                        visitMethod(names)
                    }
                }
                EntryType.PARAMETER -> {
                    checked<MethodVisitor, ParameterVisitor>(last) {
                        val index = fixValue(line.takeNext().second)?.toIntOrNull()
                        val lvOrd = fixValue(line.takeNext().second)?.toIntOrNull()
                        if (index == null && lvOrd == null) {
                            throw IllegalArgumentException("Invalid parameter entry, no index or lvOrd")
                        }
                        val remain = line.takeRemainingOnLine()
                        val names = remain.map { fixValue(it.second) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                            getNamespace(idx) to name
                        }
                        visitParameter(index, lvOrd, names)
                    }
                }
                EntryType.LOCAL_VARIABLE -> {
                    checked<MethodVisitor, LocalVariableVisitor>(last) {
                        val lvOrd = fixValue(line.takeNext().second)!!.toInt()
                        val startOp = fixValue(line.takeNext().second)?.toIntOrNull()
                        val names = line.takeRemainingOnLine().map { fixValue(it.second) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                            getNamespace(idx) to name
                        }
                        visitLocalVariable(lvOrd, startOp, names)
                    }
                }
                EntryType.FIELD -> {
                    checked<ClassVisitor, FieldVisitor>(last) {
                        val names = line.takeRemainingOnLine().map { fixValue(it.second) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                            val nd = NameAndDescriptor.read(name).getParts()
                            getNamespace(idx) to (nd.first.value to nd.second?.getFieldDescriptor())
                        }
                        visitField(names)
                    }
                }
                EntryType.INNER_CLASS -> {
                    checked<ClassVisitor, InnerClassVisitor>(last) {
                        val type = fixValue(line.takeNext().second)!!.let {
                            when (it) {
                                "i" -> InnerClassNode.InnerType.INNER
                                "a" -> InnerClassNode.InnerType.ANONYMOUS
                                "l" -> InnerClassNode.InnerType.LOCAL
                                else -> throw IllegalArgumentException("Invalid inner class type $it")
                            }
                        }
                        val names = line.takeRemainingOnLine().map { fixValue(it.second) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                            val innerName = name.substringBefore(';')
                            val fqn = if (';' in name) {
                                FullyQualifiedName.read(name.substringAfter(';'))
                            } else {
                                null
                            }
                            getNamespace(idx) to (innerName to fqn)
                        }
                        visitInnerClass(type, names)
                    }
                }
                EntryType.JAVADOC -> {
                    checked<MemberVisitor<*>, CommentVisitor>(last) {
                        val values = line.takeRemainingOnLine().map { fixValue(it.second) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                            getNamespace(idx) to name
                        }
                        visitComment(values)
                    }
                }
                EntryType.ANNOTATION -> {
                    checked<MemberVisitor<*>, AnnotationVisitor>(last) {
                        val type = fixValue(line.takeNext().second)!!.let {
                            when (it) {
                                "+" -> AnnotationType.ADD
                                "-" -> AnnotationType.REMOVE
                                "m" -> AnnotationType.MODIFY
                                else -> throw IllegalArgumentException("Invalid annotation type $it")
                            }
                        }
                        val key = fixValue(line.takeNext().second)
                        val value = fixValue(line.takeNext().second) ?: "()"
                        val annotation = Annotation.read("@$key$value")
                        val baseNs = getNamespace(line.takeNext().second.toInt())
                        val namespaces = line.takeRemainingOnLine().mapNotNull { fixValue(it.second) }.map { Namespace(it) }.toSet()
                        visitAnnotation(type, baseNs, annotation, namespaces)
                    }
                }
                EntryType.ACCESS -> {
                    checked<MemberVisitor<*>, AccessVisitor>(last) {
                        val type = fixValue(line.takeNext().second)!!.let {
                            when (it) {
                                "+" -> AccessType.ADD
                                "-" -> AccessType.REMOVE
                                else -> throw IllegalArgumentException("Invalid access type $it")
                            }
                        }
                        val value = AccessFlag.valueOf(fixValue(line.takeNext().second)!!.uppercase())
                        val namespaces = line.takeRemainingOnLine().mapNotNull { fixValue(it.second) }.map { Namespace(it) }.toSet()
                        visitAccess(type, value, namespaces)
                    }
                }
                EntryType.CONSTANT_GROUP -> {
                    checked<MappingVisitor, ConstantGroupVisitor>(last) {
                        val type = ConstantGroupNode.InlineType.valueOf(line.takeNext().second.uppercase())
                        val names = line.takeRemainingOnLine().mapNotNull { fixValue(it.second) }.map { Namespace(it) }.iterator()
                        visitConstantGroup(type, names.next(), names.asSequence().toSet())
                    }
                }
                EntryType.CONSTANT -> {
                    checked<ConstantGroupVisitor, ConstantVisitor>(last) {
                        val cls = InternalName.read(line.takeNext().second)
                        val fd = NameAndDescriptor.read(line.takeNext().second).getParts()
                        visitConstant(cls, fd.first, fd.second?.getFieldDescriptor())
                    }
                }
                EntryType.CONSTANT_TARGET -> {
                    checked<ConstantGroupVisitor, TargetVisitor>(last) {
                        val target = FullyQualifiedName.read(line.takeNext().second)
                        val paramIdx = fixValue(line.takeNext().second)?.toIntOrNull()
                        visitTarget(target, paramIdx)
                    }
                }
                EntryType.EXTENSION -> TODO()
            }
            if (next != null) {
                visitStack.add(next)
                indentStack.add(indent)
            } else {
                visitStack.add(null)
            }
        }
    }

    enum class EntryType(val key: Char) {
        CLASS('c'),
        METHOD('m'),
        PARAMETER('p'),
        LOCAL_VARIABLE('v'),
        FIELD('f'),
        INNER_CLASS('i'),
        JAVADOC('*'),
        ANNOTATION('@'),
        ACCESS('a'),
        CONSTANT_GROUP('u'),
        CONSTANT('n'),
        CONSTANT_TARGET('t'),
        EXTENSION('e'),
        ;

        companion object {
            val byKey = entries.associateBy { it.key }

            init {
                // assert all keys are unique
                if (byKey.size != entries.size) {
                    throw IllegalStateException("Duplicate keys found in EntryType")
                }
            }
        }
    }

}