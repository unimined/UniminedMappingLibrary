package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.filterNotNullValues
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.Javadoc
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import kotlin.jvm.JvmStatic

/**
 * Our format to represent everything.
 * First-party implementation: this library — spec `software-engineering/unimined-mapping-format.md`.
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

    /**
     * Read the rest of the line as namespace-positioned names — `_`
     * placeholders keep their slot but are skipped. Direct equivalent of
     * `takeRemainingFixedOnLine().withIndex().filterNotNullValues()
     * .associate { (idx, name) -> getNamespace(idx) to make(name) }`
     * without the intermediate list/wrapper/filter allocations — this is
     * the per-row hot path.
     */
    private inline fun <V : Any> CharReader<*>.takeNames(
        noinline getNamespace: (Int) -> Namespace,
        make: (String) -> V
    ): MutableMap<Namespace, V> {
        val out = LinkedHashMap<Namespace, V>(4)
        var idx = 0
        while (peek() != null && peek() != '\n') {
            val tok = takeNextUMF()
            if (tok != null) out[getNamespace(idx)] = make(tok)
            idx++
        }
        return out
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

    override suspend fun readImpl(
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
        if (minorVersion > 3) {
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

            // pre-build — a fresh Namespace alloc per name per row was a
            // measurable share of parse cost on large files
            val nsArr = Array(namespaces.size) { Namespace(namespaces[it]) }
            fun getNamespace(i: Int): Namespace = nsArr[i]

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
                    val names = input.takeNames(getNamespace) {
                        if (unchecked) PackageName.unchecked(it) else PackageName.read(it)
                    }
                    into.push(MutablePackageMapping(names))
                }
                EntryType.CLASS -> {
                    val names = input.takeNames(getNamespace) {
                        if (unchecked) InternalName.unchecked(it) else InternalName.read(it)
                    }
                    into.push(MutableClassMapping(names))
                }
                EntryType.METHOD -> {
                    val names = input.takeNames(getNamespace) {
                        if (unchecked) MethodNameAndDescriptor.unchecked(it) else MethodNameAndDescriptor.read(it)
                    }
                    into.push(MutableMethodMapping(names))
                }
                EntryType.PARAMETER -> {
                    val index = input.takeNextUMF()?.toIntOrNull()
                    val lvOrd = input.takeNextUMF()?.toIntOrNull()
                    if (index == null && lvOrd == null) {
                        throw IllegalArgumentException("Invalid parameter entry, no index or lvOrd on line $line")
                    }
                    val names = input.takeNames(getNamespace) { UnqualifiedName.read(it) }
                    into.push(MutableParameterMapping(index, lvOrd, names))
                }
                EntryType.LOCAL_VARIABLE -> {
                    val lvOrd = input.takeNextUMF()!!.toInt()
                    val startOp = input.takeNextUMF()?.toIntOrNull()
                    val names = input.takeNames(getNamespace) { UnqualifiedName.read(it) }
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
                    val names = input.takeNames(getNamespace) {
                        if (unchecked) FieldNameAndDescriptor.unchecked(it) else FieldNameAndDescriptor.read(it)
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
                    val descs = input.takeNames(getNamespace) {
                        if (unchecked) FieldOrMethodDescriptor.unchecked(it) else FieldOrMethodDescriptor.read(it)
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
                    val names = input.takeNames(getNamespace) { name ->
                        val innerName = name.substringBefore(';')
                        val fqn = if (';' in name) {
                            if (unchecked) FullyQualifiedName.unchecked(name.substringAfter(';')) else FullyQualifiedName.read(name.substringAfter(';'))
                        } else {
                            null
                        }
                        innerName to fqn
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
                    val comment = input.takeNextUMF()!!.let {
                        if (unchecked) Javadoc.unchecked(it) else Javadoc.read(it)
                    }
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
                    val names = input.takeNames(getNamespace) {
                        if (unchecked) UnqualifiedName.unchecked(it) else UnqualifiedName.read(it)
                    }
                    into.push(SyntheticBridgeElement(names))
                }
                EntryType.CONSTANT_ATTRIBUTE -> {
                    val key = input.takeNextUMF()!!
                    val value = input.takeNextUMF()
                    val names = input.takeRemainingFixedOnLine().filterNotNull().map { Namespace(it) }.iterator()
                    into.push(ConstantAttributeElement(key, value, names.next()))
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
        SYNTHETIC_BRIDGE('b'),
        CONSTANT_ATTRIBUTE('q')
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