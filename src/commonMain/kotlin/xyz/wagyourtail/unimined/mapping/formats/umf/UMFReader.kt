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
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.visitor.*
import kotlin.jvm.JvmStatic

/**
 * Our format to represent everything.
 */
object UMFReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    @JvmStatic
    @Deprecated("set within the settings argument instead")
    var uncheckedReading
        get() = unchecked
        set(value) {
            unchecked = value
        }

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return input.peek().readUtf8Line()?.lowercase()?.startsWith("umf") == true
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

    fun CharReader<*>.takeRemainingFixedOnLine() = buildList {
        while (peek() != null && peek() != '\n') {
            add(takeNextUMF())
        }
    }

    fun CharReader<*>.takeNextUMF(): String? {
        val isString = peek() == '"'
        if (isString) {
            val str = takeString()
            val next = peek()
            if (next != null && next != '\n' && next.isWhitespace()) {
                take()
            }
            return str
        }
        val literal = takeNextLiteral() ?: return null
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
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        readNonBlocking(settings, envType, input, context, into, nsMapping)
    }

    fun readNonBlocking(
        settings: FormatReaderSettings = this,
        envType: EnvType,
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        var token = input.takeNext()!!
        if (token.lowercase() != "umf") {
            throw IllegalArgumentException("Invalid UMF file, expected UMF header found ${token}")
        }
        token = input.takeNext()!!
        if (token != "1") {
            throw IllegalArgumentException("unsupported UMF major version ${token}")
        }
        token = input.takeNext()!!
        if (token != "0") {
            throw IllegalArgumentException("unsupported UMF minor version ${token}")
        }
        if (input.peek() == '\n') {
            input.take()
        } else {
            throw IllegalArgumentException("Invalid UMF header")
        }

        input.takeWhitespace()
        val namespaces = input.takeRemainingOnLine().map { nsMapping[it] ?: it }.toMutableList()

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
            readWithStack(envType, input, context, into, nsMapping, visitStack, indentStack, ::getNamespace, settings)
        }
    }

    internal fun readWithStack(
        envType: EnvType,
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>,
        visitStack: MutableList<BaseVisitor<*>?>,
        indentStack: MutableList<Int>,
        getNamespace: (Int) -> Namespace,
        settings: FormatReaderSettings = this
    ) {
        val unchecked = uncheckedReading || settings.unchecked
        val initialSize = visitStack.size
        var line = 2
        lateinit var token: String
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            line++
            val indent = input.takeWhitespace().indentCount()
            token = input.takeNext()!!
            if (token.length != 1) {
                if (input.exhausted()) break
                throw IllegalArgumentException("Invalid entry type $token")
            }
            val entryType = EntryType.byKey[token.first().lowercaseChar()] ?: throw IllegalArgumentException("Invalid entry type ${token}")
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
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) PackageName.unchecked(name) else PackageName.read(name)
                    }
                    last as MappingVisitor?
                    last?.visitPackage(names)
                }
                EntryType.CLASS -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) InternalName.unchecked(name) else InternalName.read(name)
                    }
                    last as MappingVisitor?
                    last?.visitClass(names)
                }
                EntryType.METHOD -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        val nd = (if (unchecked) NameAndDescriptor.unchecked(name) else NameAndDescriptor.read(name)).getParts()
                        getNamespace(idx) to (nd.first.value to nd.second?.getMethodDescriptor())
                    }
                    last as ClassVisitor?
                    last?.visitMethod(names)
                }
                EntryType.PARAMETER -> {
                    val index = input.takeNextUMF()?.toIntOrNull()
                    val lvOrd = input.takeNextUMF()?.toIntOrNull()
                    if (index == null && lvOrd == null) {
                        throw IllegalArgumentException("Invalid parameter entry, no index or lvOrd on line $line")
                    }
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to name
                    }
                    last as MethodVisitor?
                    last?.visitParameter(index, lvOrd, names)
                }
                EntryType.LOCAL_VARIABLE -> {
                    val lvOrd = input.takeNextUMF()!!.toInt()
                    val startOp = input.takeNextUMF()?.toIntOrNull()
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to name
                    }
                    last as MethodVisitor?
                    last?.visitLocalVariable(lvOrd, startOp, names)
                }
                EntryType.EXCEPTION -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> ExceptionType.ADD
                            "-" -> ExceptionType.REMOVE
                            else -> throw IllegalArgumentException("Invalid exception type $it")
                        }
                    }
                    val exception = input.takeNextUMF()!!.let { if (unchecked) InternalName.unchecked(it) else InternalName.read(it) }
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    last as MethodVisitor?
                    last?.visitException(type, exception, names.next(), names.asSequence().toSet())
                }
                EntryType.FIELD -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        val nd = (if (unchecked) NameAndDescriptor.unchecked(name) else NameAndDescriptor.read(name)).getParts()
                        getNamespace(idx) to (nd.first.value to nd.second?.getFieldDescriptor())
                    }
                    last as ClassVisitor?
                    last?.visitField(names)
                }
                EntryType.WILDCARD -> {
                    last as ClassVisitor?
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "f" -> WildcardNode.WildcardType.FIELD
                            "m" -> WildcardNode.WildcardType.METHOD
                            else -> throw IllegalArgumentException("Invalid wildcard type $it")
                        }
                    }
                    val descs = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) FieldOrMethodDescriptor.unchecked(name) else FieldOrMethodDescriptor.read(name)
                    }
                    last?.visitWildcard(type, descs)
                }
                EntryType.INNER_CLASS -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "i" -> InnerClassNode.InnerType.INNER
                            "a" -> InnerClassNode.InnerType.ANONYMOUS
                            "l" -> InnerClassNode.InnerType.LOCAL
                            else -> throw IllegalArgumentException("Invalid inner class type $it")
                        }
                    }
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
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
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> SealedType.ADD
                            "-" -> SealedType.REMOVE
                            "c" -> SealedType.CLEAR
                            else -> throw IllegalArgumentException("Invalid seal type $it")
                        }
                    }
                    val name = if (type != SealedType.CLEAR) {
                        input.takeNextUMF()?.let {
                            if (unchecked) InternalName.unchecked(it) else InternalName.read(it)
                        }
                    } else null
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    last as ClassVisitor?
                    last?.visitSeal(type, name, names.next(), names.asSequence().toSet())
                }
                EntryType.INTERFACE -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> InterfacesType.ADD
                            "-" -> InterfacesType.REMOVE
                            else -> throw IllegalArgumentException("Invalid interface type $it")
                        }
                    }
                    val name = input.takeNextUMF()!!.let {
                        if (unchecked) InternalName.unchecked(it) else InternalName.read(it)
                    }
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    last as ClassVisitor?
                    last?.visitInterface(type, name, names.next(), names.asSequence().toSet())
                }
                EntryType.JAVADOC -> {
                    val comment = input.takeNextUMF()!!
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }
                    last as MemberVisitor<*>?
                    last?.visitJavadoc(comment, names.toSet())
                }
                EntryType.ANNOTATION -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AnnotationType.ADD
                            "-" -> AnnotationType.REMOVE
                            "m" -> AnnotationType.MODIFY
                            else -> throw IllegalArgumentException("Invalid annotation type $it")
                        }
                    }
                    val key = input.takeNextUMF()
                    val value = input.takeNextUMF() ?: "()"
                    val annotation = if (unchecked) Annotation.unchecked("@$key$value") else Annotation.read("@$key$value")
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    last as MemberVisitor<*>?
                    last?.visitAnnotation(type, names.next(), annotation, names.asSequence().toSet())
                }
                EntryType.ACCESS -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AccessType.ADD
                            "-" -> AccessType.REMOVE
                            else -> throw IllegalArgumentException("Invalid access type $it")
                        }
                    }
                    val value = AccessFlag.valueOf(input.takeNextUMF()!!.uppercase())
                    val conditions = input.takeNextUMF()!!.let { if (unchecked) AccessConditions.unchecked(it) else AccessConditions.read(it) }
                    val accNs = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.toSet()
                    last as MemberVisitor<*>?
                    last?.visitAccess(type, value, conditions, accNs)
                }
                EntryType.CONSTANT_GROUP -> {
                    val type = ConstantGroupNode.InlineType.valueOf(input.takeNextUMF()!!.uppercase())
                    val name = input.takeNextUMF()
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    last as MappingVisitor?
                    last?.visitConstantGroup(type, name, names.next(), names.asSequence().toSet())
                }
                EntryType.CONSTANT -> {
                    val cls = input.takeNextUMF()!!.let { if (unchecked) InternalName.unchecked(it) else InternalName.read(it) }
                    val fd = input.takeNextUMF()!!.let { if (unchecked) NameAndDescriptor.unchecked(it) else NameAndDescriptor.read(it) }.getParts()
                    last as ConstantGroupVisitor?
                    last?.visitConstant(cls, fd.first, fd.second?.getFieldDescriptor())
                }
                EntryType.CONSTANT_TARGET -> {
                    val target = input.takeNextUMF()!!.let { if (it.isEmpty()) null else if (unchecked) FullyQualifiedName.unchecked(it) else FullyQualifiedName.read(it) }
                    val paramIdx = input.takeNextUMF()?.toIntOrNull()
                    last as ConstantGroupVisitor?
                    last?.visitTarget(target, paramIdx)
                }
                EntryType.CONSTANT_EXPRESSION -> {
                    val constant = input.takeNextUMF()!!.let { if (unchecked) Constant.unchecked(it) else Constant.read(it) }
                    val expression = input.takeNextUMF()!!.let { if (unchecked) Expression.unchecked(it) else Expression.read(it) }
                    last as ConstantGroupVisitor?
                    last?.visitExpression(constant, expression)
                }
                EntryType.SIGNATURE -> {
                    val sig = input.takeNextUMF()!!
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    last as SignatureParentVisitor<*>?
                    last?.visitSignature(sig, names.next(), names.asSequence().toSet())
                }
            }
            visitStack.add(next)
            indentStack.add(indent)
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
        CONSTANT_EXPRESSION('e'),
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