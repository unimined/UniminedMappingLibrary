package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import kotlin.jvm.JvmStatic
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

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

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
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
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                readNonBlocking(settings, input, context, into, nsMapping)
    }

    fun readNonBlocking(
        settings: FormatReaderSettings = this,
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>
    ) {
                var token = input.takeNext()!!
        if (token.lowercase() != "umf") {
            throw IllegalArgumentException("Invalid UMF file, expected UMF header found ${token}")
        }
        token = input.takeNext()!!
        val majorVersion = token.toInt()
        if (majorVersion > 1) {
            throw IllegalArgumentException("unsupported UMF major version ${token}")
        }
        token = input.takeNext()!!
        val minorVersion = token.toInt()
        if (minorVersion > 2) {
            throw IllegalArgumentException("unsupported UMF minor version ${token}")
        }
        if (input.peek() == '\n') {
            input.take()
        } else {
            throw IllegalArgumentException("Invalid UMF header")
        }

        input.takeWhitespace()
        val namespaces = input.takeRemainingOnLine().map { nsMapping[it] ?: it }.toMutableList()

        with (into) {
            header(namespaces.map { Namespace(it) })

            fun getNamespace(i: Int): Namespace {
                return Namespace(namespaces[i])
            }

            val visitStack = mutableListOf<Element?>(null)
            val indentStack = mutableListOf(-1)
            readWithStack(
                input, context, into, nsMapping, visitStack, indentStack, ::getNamespace, settings,
                legacyEnumKey = majorVersion == 1 && minorVersion < 2
            )
        }
        into.end()
    }

    internal fun readWithStack(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        visitStack: MutableList<Element?>,
        indentStack: MutableList<Int>,
        getNamespace: (Int) -> Namespace,
        settings: FormatReaderSettings = this,
        legacyEnumKey: Boolean = false
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
            val entryKey = token.first().lowercaseChar()
            while (indent <= indentStack.last()) {
                visitStack.removeLast()?.let { into.pop(it) }
                indentStack.removeLast()
            }
            val entryType = if (legacyEnumKey && entryKey == 'u') {
                if (visitStack.last() is ClassMapping) EntryType.ENUM else EntryType.CONSTANT_GROUP
            } else {
                EntryType.byKey.getValue(entryKey)
            }
            val next: Element? = when (entryType) {
                EntryType.COMMENT -> {
                    input.takeLine()
                    continue
                }
                EntryType.PACKAGE -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) PackageName.unchecked(name) else PackageName.read(name)
                    }
                    into.push(MutablePackageMapping(names))
                }
                EntryType.CLASS -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) InternalName.unchecked(name) else InternalName.read(name)
                    }
                    into.push(MutableClassMapping(names))
                }
                EntryType.METHOD -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        val nd = (if (unchecked) MethodNameAndDescriptor.unchecked(name) else MethodNameAndDescriptor.read(name))
                        getNamespace(idx) to nd
                    }
                    into.push(MutableMethodMapping(names))
                }
                EntryType.PARAMETER -> {
                    val index = input.takeNextUMF()?.toIntOrNull()
                    val lvOrd = input.takeNextUMF()?.toIntOrNull()
                    if (index == null && lvOrd == null) {
                        throw IllegalArgumentException("Invalid parameter entry, no index or lvOrd on line $line")
                    }
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to UnqualifiedName.read(name)
                    }
                    into.push(MutableParameterMapping(index, lvOrd, names))
                }
                EntryType.LOCAL_VARIABLE -> {
                    val lvOrd = input.takeNextUMF()!!.toInt()
                    val startOp = input.takeNextUMF()?.toIntOrNull()
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to UnqualifiedName.read(name)
                    }
                    into.push(MutableLocalVariableMapping(lvOrd, startOp, names))
                }
                EntryType.EXCEPTION -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AddRemove.ADD
                            "-" -> AddRemove.REMOVE
                            else -> throw IllegalArgumentException("Invalid exception type $it")
                        }
                    }
                    val exception = input.takeNextUMF()!!.let { if (unchecked) InternalName.unchecked(it) else InternalName.read(it) }
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    into.push(ExceptionElement(type, exception, names.next()))
                }
                EntryType.FIELD -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        val nd = (if (unchecked) FieldNameAndDescriptor.unchecked(name) else FieldNameAndDescriptor.read(name))
                        getNamespace(idx) to nd
                    }
                    into.push(MutableFieldMapping(names))
                }
                EntryType.WILDCARD -> {
                                        val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "f" -> WildcardType.FIELD
                            "m" -> WildcardType.METHOD
                            else -> throw IllegalArgumentException("Invalid wildcard type $it")
                        }
                    }
                    val descs = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) FieldOrMethodDescriptor.unchecked(name) else FieldOrMethodDescriptor.read(name)
                    }
                    into.push(MutableWildcardMapping(type, descs))
                }
                EntryType.INNER_CLASS -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "i" -> InnerType.INNER
                            "a" -> InnerType.ANONYMOUS
                            "l" -> InnerType.LOCAL
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
                    into.push(InnerClassElement(type, names))
                }
                EntryType.SEAL -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AddRemoveClear.ADD
                            "-" -> AddRemoveClear.REMOVE
                            "c" -> AddRemoveClear.CLEAR
                            else -> throw IllegalArgumentException("Invalid seal type $it")
                        }
                    }
                    val name = if (type != AddRemoveClear.CLEAR) {
                        input.takeNextUMF()?.let {
                            if (unchecked) InternalName.unchecked(it) else InternalName.read(it)
                        }
                    } else null
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    into.push(SealElement(type, name, names.next()))
                }
                EntryType.INTERFACE -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AddRemove.ADD
                            "-" -> AddRemove.REMOVE
                            else -> throw IllegalArgumentException("Invalid interface type $it")
                        }
                    }
                    val name = input.takeNextUMF()!!.let {
                        if (unchecked) ClassTypeSignature.unchecked(it) else ClassTypeSignature.read(it)
                    }
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    into.push(InterfaceElement(type, name, names.next()))
                }
                EntryType.ENUM -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AddRemove.ADD
                            "-" -> AddRemove.REMOVE
                            else -> throw IllegalArgumentException("Invalid enum type $it")
                        }
                    }
                    val name = input.takeNextUMF()!!.let {
                        if (unchecked) UnqualifiedName.unchecked(it) else UnqualifiedName.read(it)
                    }
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    into.push(EnumExtensionElement(type, name, names.next()))
                }
                EntryType.JAVADOC -> {
                    val comment = input.takeNextUMF()!!
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }
                    into.push(JavadocElement(comment, names.first()))
                }
                EntryType.ANNOTATION -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AddRemoveModify.ADD
                            "-" -> AddRemoveModify.REMOVE
                            "m" -> AddRemoveModify.MODIFY
                            else -> throw IllegalArgumentException("Invalid annotation type $it")
                        }
                    }
                    val key = input.takeNextUMF()
                    val value = input.takeNextUMF() ?: "()"
                    val annotation = if (unchecked) Annotation.unchecked("@$key$value") else Annotation.read("@$key$value")
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    into.push(AnnotationElement(type, names.next(), annotation))
                }
                EntryType.ACCESS -> {
                    val type = input.takeNextUMF()!!.let {
                        when (it) {
                            "+" -> AddRemove.ADD
                            "-" -> AddRemove.REMOVE
                            else -> throw IllegalArgumentException("Invalid access type $it")
                        }
                    }
                    val value = AccessFlag.valueOf(input.takeNextUMF()!!.uppercase())
                    val conditions = input.takeNextUMF()!!.let { if (unchecked) AccessConditions.unchecked(it) else AccessConditions.read(it) }
                    val accNs = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.toSet()
                    into.push(AccessElement(type, value, conditions))
                }
                EntryType.CONSTANT_GROUP -> {
                    val type = InlineType.valueOf(input.takeNextUMF()!!.uppercase())
                    val name = input.takeNextUMF()
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    into.push(MutableConstantGroupMapping(type, name, names.next()))
                }
                EntryType.CONSTANT -> {
                    val cls = input.takeNextUMF()!!.let { if (unchecked) InternalName.unchecked(it) else InternalName.read(it) }
                    val fd = input.takeNextUMF()!!.let { if (unchecked) NameAndDescriptor.unchecked(it) else NameAndDescriptor.read(it) }.getParts()
                    into.push(ConstantElement(cls, fd.first.withFieldDesc(fd.second?.getFieldDescriptor())))
                }
                EntryType.CONSTANT_TARGET -> {
                    val target = input.takeNextUMF()!!.let { if (it.isEmpty()) null else if (unchecked) FullyQualifiedName.unchecked(it) else FullyQualifiedName.read(it) }
                    val paramIdx = input.takeNextUMF()?.toIntOrNull()
                    into.push(TargetElement(target, paramIdx))
                }
                EntryType.CONSTANT_EXPRESSION -> {
                    val constant = input.takeNextUMF()!!.let { if (unchecked) Constant.unchecked(it) else Constant.read(it) }
                    val expression = input.takeNextUMF()!!.let { if (unchecked) Expression.unchecked(it) else Expression.read(it) }
                    into.push(ExpressionElement(constant, expression))
                }
                EntryType.SYNTHETIC_BRIDGE -> {
                    val names = input.takeRemainingFixedOnLine().withIndex().filterNotNullValues().associate { (idx, name) ->
                        getNamespace(idx) to if (unchecked) UnqualifiedName.unchecked(name) else UnqualifiedName.read(name)
                    }
                    into.push(SyntheticBridgeElement(names))
                }
                EntryType.SIGNATURE -> {
                    val sig = input.takeNextUMF()!!
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    when (visitStack.last()) {
                        is ClassMapping -> into.push(SignatureElement(if (unchecked) ClassSignature.unchecked(sig) else ClassSignature.read(sig), names.next()))
                        is MethodMapping -> into.push(SignatureElement(if (unchecked) MethodSignature.unchecked(sig) else MethodSignature.read(sig), names.next()))
                        is FieldMapping -> into.push(SignatureElement(if (unchecked) FieldSignature.unchecked(sig) else FieldSignature.read(sig), names.next()))
                        null -> null
                        else -> throw IllegalArgumentException("Invalid signature parent element")
                    }
                }
            }
            visitStack.add(next)
            indentStack.add(indent)
        }
        while (visitStack.size > initialSize) {
            visitStack.removeLast()?.let { into.pop(it) }
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
        SEAL('s'),
        ENUM('z'),
        SYNTHETIC_BRIDGE('b')
        ;

        companion object {
            val byKey = entries.associateBy { it.key }

            init {
                // assert all keys are unique
                if (byKey.size != entries.size) {
                    throw IllegalStateException("Duplicate keys found in ${this::class.simpleName}")
                }
            }
        }
    }

}