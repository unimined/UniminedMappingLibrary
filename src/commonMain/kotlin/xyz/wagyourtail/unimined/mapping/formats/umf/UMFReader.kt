package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.TokenType
import xyz.wagyourtail.unimined.mapping.util.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.visitor.*

/**
 * Our format to represent everything.
 */
object UMFReader : FormatReader {

    @Suppress("MemberVisibilityCanBePrivate")
    var uncheckedReading = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return input.peek().readUtf8Line()?.lowercase()?.startsWith("umf") ?: false
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

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        readNonBlocking(envType, input, context, into, nsMapping)
    }

    fun readNonBlocking(envType: EnvType, input: CharReader, context: AbstractMappingTree?, into: MappingVisitor, nsMapping: Map<String, String>) {
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
        if (input.peek() == '\n') {
            input.take()
        } else {
            throw IllegalArgumentException("Invalid UMF header")
        }

        input.takeWhitespace()
        val namespaces = input.takeRemainingOnLine().map { nsMapping[it.second] ?: it.second }.toMutableList()

        into.use {
            visitHeader(*namespaces.toTypedArray())

            fun getNamespace(i: Int): Namespace {
                while (i !in namespaces.indices) {
                    namespaces.add(into.nextUnnamedNs().name)
                }
                return Namespace(namespaces[i])
            }

            val visitStack = mutableListOf<BaseVisitor<*>?>(into)
            val indentStack = mutableListOf(-1)
            readWithStack(envType, input, context, into, nsMapping, visitStack, indentStack, ::getNamespace)
        }
    }

    internal fun readWithStack(envType: EnvType, input: CharReader, context: AbstractMappingTree?, into: MappingVisitor, nsMapping: Map<String, String>, visitStack: MutableList<BaseVisitor<*>?>, indentStack: MutableList<Int>, getNamespace: (Int) -> Namespace) {
        val unchecked = uncheckedReading
        val initialSize = visitStack.size
        var line = 2
        var token: Pair<TokenType, String>
        while (!input.exhausted()) {
            line++
            val indent = input.takeWhitespace().indentCount()
            token = input.takeNext()
            if (token.second.length != 1) {
                if (input.exhausted()) break
                throw IllegalArgumentException("Invalid entry type ${token.second}")
            }
            val entryType = EntryType.byKey[token.second.first().lowercaseChar()] ?: throw IllegalArgumentException("Invalid entry type ${token.second}")
            while (indent <= indentStack.last()) {
                visitStack.removeLast()?.visitEnd()
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
                        throw IllegalArgumentException("Invalid parameter entry, no index or lvOrd on line $line")
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
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as MethodVisitor?
                    last?.visitException(type, exception, names.next(), names.asSequence().toSet())
                }
                EntryType.FIELD -> {
                    val names = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        val nd = (if (unchecked) NameAndDescriptor.unchecked(name) else NameAndDescriptor.read(name)).getParts()
                        getNamespace(idx) to (nd.first.value to nd.second?.getFieldDescriptor())
                    }
                    last as ClassVisitor?
                    last?.visitField(names)
                }
                EntryType.WILDCARD -> {
                    last as ClassVisitor?
                    val type = fixValue(input.takeNext())!!.let {
                        when (it) {
                            "f" -> WildcardNode.WildcardType.FIELD
                            "m" -> WildcardNode.WildcardType.METHOD
                            else -> throw IllegalArgumentException("Invalid wildcard type $it")
                        }
                    }
                    val descs = input.takeRemainingOnLine().map { fixValue(it) }.withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) FieldOrMethodDescriptor.unchecked(name) else FieldOrMethodDescriptor.read(name)
                    }
                    last?.visitWildcard(type, descs)
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
                EntryType.SEAL -> {
                    val type = fixValue(input.takeNext())!!.let {
                        when (it) {
                            "+" -> SealedType.ADD
                            "-" -> SealedType.REMOVE
                            "c" -> SealedType.CLEAR
                            else -> throw IllegalArgumentException("Invalid seal type $it")
                        }
                    }
                    val name = if (type != SealedType.CLEAR) {
                        fixValue(input.takeNext())?.let {
                            if (unchecked) InternalName.unchecked(it) else InternalName.read(it)
                        }
                    } else null
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as ClassVisitor?
                    last?.visitSeal(type, name, names.next(), names.asSequence().toSet())
                }
                EntryType.INTERFACE -> {
                    val type = fixValue(input.takeNext())!!.let {
                        when (it) {
                            "+" -> InterfacesType.ADD
                            "-" -> InterfacesType.REMOVE
                            else -> throw IllegalArgumentException("Invalid interface type $it")
                        }
                    }
                    val name = fixValue(input.takeNext())!!.let {
                        if (unchecked) InternalName.unchecked(it) else InternalName.read(it)
                    }
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as ClassVisitor?
                    last?.visitInterface(type, name, names.next(), names.asSequence().toSet())
                }
                EntryType.JAVADOC -> {
                    val comment = fixValue(input.takeNext())!!
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as MemberVisitor<*>?
                    last?.visitJavadoc(comment, names.next(), names.asSequence().toSet())
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
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as MemberVisitor<*>?
                    last?.visitAnnotation(type, names.next(), annotation, names.asSequence().toSet())
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
                    val conditions = if (unchecked) AccessConditions.unchecked(input.takeNext().second) else AccessConditions.read(input.takeNext().second)
                    val accNs = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.toSet()
                    last as MemberVisitor<*>?
                    last?.visitAccess(type, value, conditions, accNs)
                }
                EntryType.CONSTANT_GROUP -> {
                    val type = ConstantGroupNode.InlineType.valueOf(input.takeNext().second.uppercase())
                    val name = fixValue(input.takeNext())
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as MappingVisitor?
                    last?.visitConstantGroup(type, name, names.next(), names.asSequence().toSet())
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
                EntryType.SIGNATURE -> {
                    val sig = fixValue(input.takeNext())!!
                    val names = input.takeRemainingOnLine().mapNotNull { fixValue(it) }.map { Namespace(it) }.iterator()
                    last as SignatureParentVisitor<*>?
                    last?.visitSignature(sig, names.next(), names.asSequence().toSet())
                }
            }
            if (next != null) {
                visitStack.add(next)
                indentStack.add(indent)
            } else {
                visitStack.add(null)
            }
        }
        while (visitStack.size > initialSize) {
            visitStack.removeLast()?.visitEnd()
            indentStack.removeLast()
        }
    }

    enum class EntryType(val key: Char) {
        PACKAGE('k'),
        CLASS('c'),
        METHOD('m'),
        SIGNATURE('g'),
        PARAMETER('p'),
        LOCAL_VARIABLE('v'),
        EXCEPTION('x'),
        FIELD('f'),
        WILDCARD('w'),
        INNER_CLASS('i'),
        JAVADOC('*'),
        ANNOTATION('@'),
        ACCESS('a'),
        CONSTANT_GROUP('u'),
        CONSTANT('n'),
        CONSTANT_TARGET('t'),
        COMMENT('#'),
        INTERFACE('j'),
        SEAL('s')
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