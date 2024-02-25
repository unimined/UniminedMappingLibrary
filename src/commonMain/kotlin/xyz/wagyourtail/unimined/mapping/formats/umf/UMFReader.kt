package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node.InnerClassNode
import xyz.wagyourtail.unimined.mapping.util.*
import xyz.wagyourtail.unimined.mapping.visitor.*

/**
 * Our format to represent everything.
 */
object UMFReader : FormatReader {

    @Suppress("MemberVisibilityCanBePrivate")
    var uncheckedReading = false

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return inputType.peek().readUtf8Line()?.lowercase()?.startsWith("umf") ?: false
    }

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

    private fun fixValue(value: Pair<TokenType, String>): String? {
        if (value.first == TokenType.STRING) return value.second
        val literal = value.second
        if (!literal.startsWith('_')) {
            return literal
        }
        val count = literal.count { it == '_' }
        if (count == literal.length) {
            if (count == 1) {
                return null
            }
            return "_".repeat(count - 1)
        }
        return literal
    }

    override suspend fun read(envType: EnvType, input: CharReader, context: MappingTree?, into: MappingVisitor, nsMapping: Map<String, String>) {
        val unchecked = uncheckedReading
        var token = input.takeNext()
        if (token.second.lowercase() != "umf") {
            throw IllegalArgumentException("Invalid UMF file, expected UMF header found ${token.second}")
        }
        token = input.takeNext()
        if (token.second != "1") {
            throw IllegalArgumentException("unsupported UMF major version ${token.second}")
        }
        token = input.takeNext()
        if (token.second != "0") {
            throw IllegalArgumentException("unsupported UMF minor version ${token.second}")
        }

        val extensions = input.takeRemainingOnLine()
        if (extensions.isNotEmpty()) {
            TODO("Extensions are not fully supported yet")
        }

        input.takeWhitespace()
        val namespaces = input.takeRemainingOnLine().map { nsMapping[it.second] ?: it.second }.toMutableList()
        into.visitHeader(*namespaces.toTypedArray())

        fun getNamespace(i: Int): Namespace {
            while (i !in namespaces.indices) {
                namespaces.add(into.nextUnnamedNs().name)
            }
            return Namespace(namespaces[i])
        }

        val visitStack = mutableListOf<BaseVisitor<*>?>(into)
        val indentStack = mutableListOf(-1)

        while (!input.exhausted()) {
            val indent = input.takeWhitespace().indentCount()
            token = input.takeNext()
            if (token.second.length != 1) {
                if (input.exhausted()) break
                throw IllegalArgumentException("Invalid entry type ${token.second}")
            }
            val entryType = EntryType.byKey[token.second.first()] ?: throw IllegalArgumentException("Invalid entry type ${token.second}")
            while (indent <= indentStack.last()) {
                visitStack.removeLast()
                indentStack.removeLast()
            }
            val last = visitStack.last()
            val next: BaseVisitor<*>? = when (entryType) {
                EntryType.COMMENT -> {
                    input.takeLine()
                    continue
                }
                EntryType.PACKAGE -> {
                    val names = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) PackageName.unchecked(name) else PackageName.read(name)
                    }
                    last as MappingVisitor?
                    last?.visitPackage(names)
                }
                EntryType.CLASS -> {
                    val names = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) InternalName.unchecked(name) else InternalName.read(name)
                    }
                    last as MappingVisitor?
                    last?.visitClass(names)
                }
                EntryType.METHOD -> {
                    val names = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        val nd = (if (unchecked) NameAndDescriptor.unchecked(name) else NameAndDescriptor.read(name)).getParts()
                        getNamespace(idx) to (nd.first.value to nd.second?.getMethodDescriptor())
                    }
                    last as ClassVisitor?
                    last?.visitMethod(names)
                }
                EntryType.PARAMETER -> {
                    val index = fixValue(input.takeNext())?.toIntOrNull()
                    val lvOrd = fixValue(input.takeNext())?.toIntOrNull()
                    if (index == null && lvOrd == null) {
                        throw IllegalArgumentException("Invalid parameter entry, no index or lvOrd")
                    }
                    val remain = input.takeRemainingOnLine()
                    val names = remain.map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to name
                    }
                    last as MethodVisitor?
                    last?.visitParameter(index, lvOrd, names)
                }
                EntryType.LOCAL_VARIABLE -> {
                    val lvOrd = fixValue(input.takeNext())!!.toInt()
                    val startOp = fixValue(input.takeNext())?.toIntOrNull()
                    val names = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to name
                    }
                    last as MethodVisitor?
                    last?.visitLocalVariable(lvOrd, startOp, names)
                }
                EntryType.EXCEPTION -> {
                    val type = fixValue(input.takeNext())!!.let {
                        when (it) {
                            "+" -> ExceptionType.ADD
                            "-" -> ExceptionType.REMOVE
                            else -> throw IllegalArgumentException("Invalid exception type $it")
                        }
                    }
                    val exception = fixValue(input.takeNext())!!.let { if (unchecked) InternalName.unchecked(it) else InternalName.read(it) }
                    val baseNs = getNamespace(input.takeNext().second.toInt())
                    val excNs = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.toSet()
                    last as MethodVisitor?
                    last?.visitException(type, exception, baseNs, excNs)
                }
                EntryType.FIELD -> {
                    val names = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        val nd = (if (unchecked) NameAndDescriptor.unchecked(name) else NameAndDescriptor.read(name)).getParts()
                        getNamespace(idx) to (nd.first.value to nd.second?.getFieldDescriptor())
                    }
                    last as ClassVisitor?
                    last?.visitField(names)
                }
                EntryType.INNER_CLASS -> {
                    val type = fixValue(input.takeNext())!!.let {
                        when (it) {
                            "i" -> InnerClassNode.InnerType.INNER
                            "a" -> InnerClassNode.InnerType.ANONYMOUS
                            "l" -> InnerClassNode.InnerType.LOCAL
                            else -> throw IllegalArgumentException("Invalid inner class type $it")
                        }
                    }
                    val names = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        val innerName = name.substringBefore(';')
                        val fqn = if (';' in name) {
                            if (unchecked) FullyQualifiedName.unchecked(name.substringAfter(';')) else FullyQualifiedName.read(name.substringAfter(';'))
                        } else {
                            null
                        }
                        getNamespace(idx) to (innerName to fqn)
                    }
                    last as ClassVisitor?
                    last?.visitInnerClass(type, names)
                }
                EntryType.JAVADOC -> {
                    val values = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to name
                    }
                    val fixed = values.mapValues {
                        val ref = it.value.toIntOrNull()
                        if (ref != null) {
                            values[getNamespace(ref)]!!
                        } else {
                            it.value.removePrefix("_").toIntOrNull()?.toString() ?: it.value
                        }
                    }
                    last as MemberVisitor<*>?
                    last?.visitComment(fixed)
                }
                EntryType.ANNOTATION -> {
                    val type = fixValue(input.takeNext())!!.let {
                        when (it) {
                            "+" -> AnnotationType.ADD
                            "-" -> AnnotationType.REMOVE
                            "m" -> AnnotationType.MODIFY
                            else -> throw IllegalArgumentException("Invalid annotation type $it")
                        }
                    }
                    val key = fixValue(input.takeNext())
                    val value = fixValue(input.takeNext()) ?: "()"
                    val annotation = if (unchecked) Annotation.unchecked("@$key$value") else Annotation.read("@$key$value")
                    val baseNs = getNamespace(input.takeNext().second.toInt())
                    val annNs = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.toSet()
                    last as MemberVisitor<*>?
                    last?.visitAnnotation(type, baseNs, annotation, annNs)
                }
                EntryType.ACCESS -> {
                    val type = fixValue(input.takeNext())!!.let {
                        when (it) {
                            "+" -> AccessType.ADD
                            "-" -> AccessType.REMOVE
                            else -> throw IllegalArgumentException("Invalid access type $it")
                        }
                    }
                    val value = AccessFlag.valueOf(fixValue(input.takeNext())!!.uppercase())
                    val accNs = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.toSet()
                    last as MemberVisitor<*>?
                    last?.visitAccess(type, value, accNs)
                }
                EntryType.CONSTANT_GROUP -> {
                    val type = ConstantGroupNode.InlineType.valueOf(input.takeNext().second.uppercase())
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as MappingVisitor?
                    last?.visitConstantGroup(type, names.next(), names.asSequence().toSet())
                }
                EntryType.CONSTANT -> {
                    val cls = input.takeNext().second.let { if (unchecked) InternalName.unchecked(it) else InternalName.read(it) }
                    val fd = input.takeNext().second.let { if (unchecked) NameAndDescriptor.unchecked(it) else NameAndDescriptor.read(it) }.getParts()
                    last as ConstantGroupVisitor?
                    last?.visitConstant(cls, fd.first, fd.second?.getFieldDescriptor())
                }
                EntryType.CONSTANT_TARGET -> {
                    val target = input.takeNext().second.let { if (unchecked) FullyQualifiedName.unchecked(it) else FullyQualifiedName.read(it) }
                    val paramIdx = fixValue(input.takeNext())?.toIntOrNull()
                    last as ConstantGroupVisitor?
                    last?.visitTarget(target, paramIdx)
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
        PACKAGE('k'),
        CLASS('c'),
        METHOD('m'),
        PARAMETER('p'),
        LOCAL_VARIABLE('v'),
        EXCEPTION('x'),
        FIELD('f'),
        INNER_CLASS('i'),
        JAVADOC('*'),
        ANNOTATION('@'),
        ACCESS('a'),
        CONSTANT_GROUP('u'),
        CONSTANT('n'),
        CONSTANT_TARGET('t'),
        EXTENSION('e'),
        COMMENT('#')
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