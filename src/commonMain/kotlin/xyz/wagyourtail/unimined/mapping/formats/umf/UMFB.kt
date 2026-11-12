package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.Buffer
import okio.BufferedSink
import okio.utf8Size
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.Javadoc
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * `UMFB` — binary sibling of [UMFWriter]/[UMFReader] for machine-to-machine
 * payloads (serialized bundles, derived caches). Same element universe and
 * push/pop semantics, but cells are length-prefixed utf8 and element kinds
 * are single-byte opcodes, so reading skips tokenization, escaping, and all
 * JVMS re-validation (every JVMS value is wrapped via its `unchecked`
 * factory — a zero-parse string wrap).
 *
 * This format is ONLY for payloads written by this library and read back by
 * it; there is no cross-version contract beyond the magic + version byte.
 * For anything user-facing or foreign-authored, use UMF text.
 *
 * Layout:
 * ```
 * 'U' 'M' 'F' 'B' u8(version=1)
 * u8 nsCount, nsCount x utf8 name           (nsCount <= 255)
 * events until OP_END:
 *   u8 opcode; per-element payload; OP_POP closes the open element
 * ```
 * names rows encode as `u8 count` then `count x (u8 nsIdx, utf8 value)` —
 * for member kinds the value is the packed `name;desc` cell, identical to
 * the non-minimized UMF cell.
 */
object UMFB {

    const val MAGIC = 0x554D4642 // "UMFB"
    const val VERSION = 1

    private const val OP_END = 0
    private const val OP_POP = 1
    private const val OP_PACKAGE = 2
    private const val OP_CLASS = 3
    private const val OP_METHOD = 4
    private const val OP_FIELD = 5
    private const val OP_WILDCARD = 6
    private const val OP_PARAMETER = 7
    private const val OP_LOCAL_VARIABLE = 8
    private const val OP_CONSTANT_GROUP = 9
    private const val OP_ACCESS = 10
    private const val OP_JAVADOC = 11
    private const val OP_SIGNATURE = 12
    private const val OP_ANNOTATION = 13
    private const val OP_SEAL = 14
    private const val OP_INTERFACE = 15
    private const val OP_INNER_CLASS = 16
    private const val OP_ENUM_EXT = 17
    private const val OP_EXCEPTION = 18
    private const val OP_SYNTHETIC_BRIDGE = 19
    private const val OP_CONSTANT = 20
    private const val OP_CONSTANT_TARGET = 21
    private const val OP_CONSTANT_EXPRESSION = 22
    private const val OP_CONSTANT_ATTRIBUTE = 23

    // signature subtypes — the umf text form resolves these from the parent
    // element; binary carries them explicitly
    private const val SIG_CLASS = 0
    private const val SIG_METHOD = 1
    private const val SIG_FIELD = 2

    private fun BufferedSink.writeUtf8Cell(s: String) {
        writeInt(s.utf8Size().toInt())
        writeUtf8(s)
    }

    private fun BufferedSink.writeOptUtf8(s: String?) {
        writeByte(if (s == null) 0 else 1)
        if (s != null) writeUtf8Cell(s)
    }

    /** sink — emits the binary event stream for `tree.emit(write(...))` */
    fun write(into: BufferedSink): MappingSink {
        into.writeInt(MAGIC)
        into.writeByte(VERSION)
        return UMFBWriterSink(into)
    }

    class UMFBWriterSink(private val into: BufferedSink) : MappingSink {

        private lateinit var nsIndex: Map<Namespace, Int>

        override fun header(namespaces: List<Namespace>) {
            require(namespaces.size <= 255) { "umfb supports at most 255 namespaces" }
            nsIndex = namespaces.withIndex().associate { it.value to it.index }
            into.writeByte(namespaces.size)
            for (ns in namespaces) into.writeUtf8Cell(ns.name)
        }

        private fun BufferedSink.writeNames(names: Map<Namespace, String>) {
            writeByte(names.size)
            for ((ns, value) in names) {
                writeByte(nsIndex.getValue(ns))
                writeUtf8Cell(value)
            }
        }

        override fun push(element: MappingElement): MappingElement? = when (element) {
            is PackageMapping -> {
                into.writeByte(OP_PACKAGE)
                into.writeNames(element.names.mapValues { it.value.value })
                element
            }
            is ClassMapping -> {
                into.writeByte(OP_CLASS)
                into.writeNames(element.names.mapValues { it.value.value })
                element
            }
            is MethodMapping -> {
                into.writeByte(OP_METHOD)
                into.writeNames(element.emitEntries().mapValues { it.value.value })
                element
            }
            is FieldMapping -> {
                into.writeByte(OP_FIELD)
                into.writeNames(element.emitEntries().mapValues { it.value.value })
                element
            }
            is WildcardMapping -> {
                into.writeByte(OP_WILDCARD)
                into.writeByte(if (element.type == WildcardType.FIELD) 0 else 1)
                into.writeNames(element.descs.mapValues { it.value.toString() })
                element
            }
            is ParameterMapping -> {
                into.writeByte(OP_PARAMETER)
                into.writeInt(element.index ?: -1)
                into.writeInt(element.lvOrd ?: -1)
                into.writeNames(element.names.mapValues { it.value.value })
                element
            }
            is LocalVariableMapping -> {
                into.writeByte(OP_LOCAL_VARIABLE)
                into.writeInt(element.lvOrd)
                into.writeInt(element.startOp ?: -1)
                into.writeNames(element.names.mapValues { it.value.value })
                element
            }
            is ConstantGroupMapping -> {
                into.writeByte(OP_CONSTANT_GROUP)
                into.writeByte(element.type.ordinal)
                into.writeOptUtf8(element.name)
                into.writeByte(nsIndex.getValue(element.baseNs))
                element
            }
            else -> null
        }

        override fun push(meta: MetadataElement): MetadataElement? = when (meta) {
            is AccessElement -> {
                into.writeByte(OP_ACCESS)
                into.writeByte(if (meta.type == AddRemove.ADD) 1 else 0)
                into.writeByte(meta.value.ordinal)
                into.writeUtf8Cell(meta.condition.toString())
                meta
            }
            is JavadocElement -> {
                into.writeByte(OP_JAVADOC)
                into.writeUtf8Cell(meta.value.toString())
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            is SignatureElement -> {
                into.writeByte(OP_SIGNATURE)
                into.writeByte(
                    when (meta.value) {
                        is ClassSignature -> SIG_CLASS
                        is MethodSignature -> SIG_METHOD
                        is FieldSignature -> SIG_FIELD
                        else -> throw IllegalArgumentException("unknown signature type ${meta.value}")
                    }
                )
                into.writeUtf8Cell(meta.value.toString())
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            is AnnotationElement -> {
                into.writeByte(OP_ANNOTATION)
                into.writeByte(meta.type.ordinal)
                into.writeUtf8Cell(meta.annotation.toString())
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            is SealElement -> {
                into.writeByte(OP_SEAL)
                into.writeByte(meta.type.ordinal)
                into.writeOptUtf8(meta.name?.value)
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            is InterfaceElement -> {
                into.writeByte(OP_INTERFACE)
                into.writeByte(if (meta.type == AddRemove.ADD) 1 else 0)
                into.writeUtf8Cell(meta.name.value)
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            is InnerClassElement -> {
                into.writeByte(OP_INNER_CLASS)
                into.writeByte(meta.type.ordinal)
                into.writeNames(meta.names.mapValues { it.value.let { (n, fqn) -> if (fqn != null) "$n;${fqn.value}" else n } })
                meta
            }
            is EnumExtensionElement -> {
                into.writeByte(OP_ENUM_EXT)
                into.writeByte(if (meta.type == AddRemove.ADD) 1 else 0)
                into.writeUtf8Cell(meta.name.value)
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            is ExceptionElement -> {
                into.writeByte(OP_EXCEPTION)
                into.writeByte(if (meta.type == AddRemove.ADD) 1 else 0)
                into.writeUtf8Cell(meta.exception.value)
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            is SyntheticBridgeElement -> {
                into.writeByte(OP_SYNTHETIC_BRIDGE)
                into.writeNames(meta.names.mapValues { it.value.value })
                meta
            }
            is ConstantElement -> {
                into.writeByte(OP_CONSTANT)
                into.writeUtf8Cell(meta.owner.value)
                into.writeUtf8Cell(meta.field.value)
                meta
            }
            is TargetElement -> {
                into.writeByte(OP_CONSTANT_TARGET)
                into.writeOptUtf8(meta.target?.value)
                into.writeInt(meta.paramIdx ?: -1)
                meta
            }
            is ExpressionElement -> {
                into.writeByte(OP_CONSTANT_EXPRESSION)
                into.writeUtf8Cell(meta.value.value)
                into.writeUtf8Cell(meta.expression.toString())
                meta
            }
            is ConstantAttributeElement -> {
                into.writeByte(OP_CONSTANT_ATTRIBUTE)
                into.writeUtf8Cell(meta.key)
                into.writeOptUtf8(meta.value)
                into.writeByte(nsIndex.getValue(meta.baseNs))
                meta
            }
            else -> null
        }

        override fun pop(element: MappingElement) {
            into.writeByte(OP_POP)
        }

        override fun pop(meta: MetadataElement) {
            into.writeByte(OP_POP)
        }

        override fun end() {
            into.writeByte(OP_END)
        }
    }

    /**
     * Replay a `UMFB` payload into [into] — mirrors the push/pop sequence a
     * UMF read would produce. All JVMS values are wrapped via `unchecked`
     * factories: payloads are machine-written, never foreign input.
     *
     * Reads over a raw [ByteArray] cursor rather than a BufferedSource —
     * per-op sink dispatch was a measurable share of read time on large
     * payloads.
     */
    fun read(payload: ByteArray, into: MappingSink) {
        var pos = 0
        fun byte() = payload[pos++].toInt() and 0xFF
        fun int(): Int {
            val v = (byte() shl 24) or (byte() shl 16) or (byte() shl 8) or byte()
            return v
        }
        fun utf8(): String {
            val len = int()
            val s = payload.decodeToString(pos, pos + len)
            pos += len
            return s
        }
        fun optUtf8(): String? = if (byte() != 0) utf8() else null

        val magic = int()
        require(magic == MAGIC) { "not a umfb payload (magic $magic)" }
        val version = byte()
        require(version == VERSION) { "unsupported umfb version $version" }
        val nsCount = byte()
        val namespaces = Array(nsCount) { Namespace(utf8()) }
        into.header(namespaces.toList())

        // stack of elements retained by the sink — pops dispatch on it the
        // same way UMFReader's visitStack does
        val stack = ArrayDeque<Element?>()
        fun readNames(): Pair<Int, Array<Pair<Namespace, String>>> {
            val count = byte()
            return count to Array(count) { namespaces[byte()] to utf8() }
        }
        fun <V> Pair<Int, Array<Pair<Namespace, String>>>.toNames(f: (String) -> V): MutableMap<Namespace, V> {
            val map = LinkedHashMap<Namespace, V>(first)
            for ((ns, v) in second) map[ns] = f(v)
            return map
        }

        while (true) {
            when (byte()) {
                OP_END -> break
                OP_POP -> into.pop(stack.removeLast()!!)
                OP_PACKAGE -> stack.addLast(
                    into.push(MutablePackageMapping(readNames().toNames { PackageName.unchecked(it) }))
                )
                OP_CLASS -> stack.addLast(
                    into.push(MutableClassMapping(readNames().toNames { InternalName.unchecked(it) }))
                )
                OP_METHOD -> stack.addLast(
                    into.push(MutableMethodMapping(readNames().toNames { MethodNameAndDescriptor.unchecked(it) }))
                )
                OP_FIELD -> stack.addLast(
                    into.push(MutableFieldMapping(readNames().toNames { FieldNameAndDescriptor.unchecked(it) }))
                )
                OP_WILDCARD -> stack.addLast(
                    into.push(
                        MutableWildcardMapping(
                            if (byte() == 0) WildcardType.FIELD else WildcardType.METHOD,
                            readNames().toNames { FieldOrMethodDescriptor.unchecked(it) }
                        )
                    )
                )
                OP_PARAMETER -> {
                    val index = int().let { if (it == -1) null else it }
                    val lvOrd = int().let { if (it == -1) null else it }
                    stack.addLast(
                        into.push(MutableParameterMapping(index, lvOrd, readNames().toNames { UnqualifiedName.unchecked(it) }))
                    )
                }
                OP_LOCAL_VARIABLE -> {
                    val lvOrd = int()
                    val startOp = int().let { if (it == -1) null else it }
                    stack.addLast(
                        into.push(MutableLocalVariableMapping(lvOrd, startOp, readNames().toNames { UnqualifiedName.unchecked(it) }))
                    )
                }
                OP_CONSTANT_GROUP -> stack.addLast(
                    into.push(
                        MutableConstantGroupMapping(
                            InlineType.entries[byte()],
                            optUtf8(),
                            namespaces[byte()]
                        )
                    )
                )
                OP_ACCESS -> stack.addLast(
                    into.push(
                        AccessElement(
                            if (byte() != 0) AddRemove.ADD else AddRemove.REMOVE,
                            AccessFlag.entries[byte()],
                            AccessConditions.unchecked(utf8())
                        )
                    )
                )
                OP_JAVADOC -> stack.addLast(
                    into.push(JavadocElement(Javadoc.unchecked(utf8()), namespaces[byte()]))
                )
                OP_SIGNATURE -> {
                    val sig = when (byte()) {
                        SIG_CLASS -> ClassSignature.unchecked(utf8())
                        SIG_METHOD -> MethodSignature.unchecked(utf8())
                        else -> FieldSignature.unchecked(utf8())
                    }
                    stack.addLast(into.push(SignatureElement(sig, namespaces[byte()])))
                }
                OP_ANNOTATION -> {
                    val type = AddRemoveModify.entries[byte()]
                    val annotation = Annotation.unchecked(utf8())
                    stack.addLast(
                        into.push(AnnotationElement(type, namespaces[byte()], annotation))
                    )
                }
                OP_SEAL -> stack.addLast(
                    into.push(
                        SealElement(
                            AddRemoveClear.entries[byte()],
                            optUtf8()?.let { InternalName.unchecked(it) },
                            namespaces[byte()]
                        )
                    )
                )
                OP_INTERFACE -> stack.addLast(
                    into.push(
                        InterfaceElement(
                            if (byte() != 0) AddRemove.ADD else AddRemove.REMOVE,
                            ClassTypeSignature.unchecked(utf8()),
                            namespaces[byte()]
                        )
                    )
                )
                OP_INNER_CLASS -> stack.addLast(
                    into.push(
                        InnerClassElement(
                            InnerType.entries[byte()],
                            readNames().toNames { name ->
                                val fqn = if (';' in name) FullyQualifiedName.unchecked(name.substringAfter(';')) else null
                                name.substringBefore(';') to fqn
                            }
                        )
                    )
                )
                OP_ENUM_EXT -> stack.addLast(
                    into.push(
                        EnumExtensionElement(
                            if (byte() != 0) AddRemove.ADD else AddRemove.REMOVE,
                            UnqualifiedName.unchecked(utf8()),
                            namespaces[byte()]
                        )
                    )
                )
                OP_EXCEPTION -> stack.addLast(
                    into.push(
                        ExceptionElement(
                            if (byte() != 0) AddRemove.ADD else AddRemove.REMOVE,
                            InternalName.unchecked(utf8()),
                            namespaces[byte()]
                        )
                    )
                )
                OP_SYNTHETIC_BRIDGE -> stack.addLast(
                    into.push(SyntheticBridgeElement(readNames().toNames { UnqualifiedName.unchecked(it) }))
                )
                OP_CONSTANT -> stack.addLast(
                    into.push(
                        ConstantElement(
                            InternalName.unchecked(utf8()),
                            FieldNameAndDescriptor.unchecked(utf8())
                        )
                    )
                )
                OP_CONSTANT_TARGET -> stack.addLast(
                    into.push(
                        TargetElement(
                            optUtf8()?.let { FullyQualifiedName.unchecked(it) },
                            int().let { if (it == -1) null else it }
                        )
                    )
                )
                OP_CONSTANT_EXPRESSION -> stack.addLast(
                    into.push(
                        ExpressionElement(
                            Constant.unchecked(utf8()),
                            Expression.unchecked(utf8())
                        )
                    )
                )
                OP_CONSTANT_ATTRIBUTE -> stack.addLast(
                    into.push(
                        ConstantAttributeElement(
                            utf8(),
                            optUtf8(),
                            namespaces[byte()]
                        )
                    )
                )
                else -> throw IllegalArgumentException("invalid umfb opcode")
            }
        }
        into.end()
    }
}
