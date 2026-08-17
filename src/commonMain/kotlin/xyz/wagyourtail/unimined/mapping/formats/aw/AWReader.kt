package xyz.wagyourtail.unimined.mapping.formats.aw

import okio.BufferedSource
import org.jetbrains.annotations.ApiStatus
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.ct.CTReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

open class AWLikeReader {
    protected fun readAWData(
        target: FullyQualifiedName,
        access: String,
        into: MappingVisitor,
        ns: Namespace,
        allowNonTransitive: Boolean
    ) {
        val addAccess = mutableSetOf<Pair<AccessFlag, AccessConditions>>()
        val removeAccess = mutableSetOf<Pair<AccessFlag, AccessConditions>>()

        val (cls, member) = target.getParts()

        if (!access.startsWith("transitive-")) {
            if (!allowNonTransitive) {
                return
            }
        }

        when (access) {
            "accessible", "transitive-accessible" -> {
                if (member == null) {
                    addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                } else {
                    val (memberName, memberDesc) = member.getParts()
                    if (memberDesc!!.isMethodDescriptor()) {
                        addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                        addAccess.add(AccessFlag.FINAL to AccessConditions.unchecked("+${AccessFlag.PRIVATE}"))
                    } else {
                        addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                    }
                }
            }

            "mutable", "transitive-mutable" -> {
                if (member == null) {
                    throw IllegalArgumentException("mutable is only valid for fields")
                }
                val (memberName, memberDesc) = member.getParts()
                if (memberDesc!!.isMethodDescriptor()) {
                    throw IllegalArgumentException("mutable is only valid for fields")
                }
                removeAccess.add(AccessFlag.FINAL to AccessConditions.ALL)
            }

            "extendable", "transitive-extendable" -> {
                if (member == null) {
                    addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                    removeAccess.add(AccessFlag.FINAL to AccessConditions.ALL)
                } else {
                    val (memberName, memberDesc) = member.getParts()
                    if (memberDesc!!.isFieldDescriptor()) {
                        throw IllegalArgumentException("extendable is not valid for fields")
                    }
                    addAccess.add(AccessFlag.PROTECTED to AccessConditions.unchecked("-${AccessFlag.PUBLIC}"))
                    removeAccess.add(AccessFlag.FINAL to AccessConditions.ALL)
                }
            }
        }

        if (member == null) {
            into.visitClass(mapOf(ns to cls.getInternalName()))?.use {
                for ((flag, conditions) in addAccess) {
                    visitAccess(AccessType.ADD, flag, conditions, setOf(ns))?.visitEnd()
                }
                for ((flag, conditions) in removeAccess) {
                    visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))?.visitEnd()
                }
            }
        } else {
            val (memberName, memberDesc) = member.getParts()
            if (memberDesc!!.isMethodDescriptor()) {
                into.visitClass(mapOf(ns to cls.getInternalName()))?.use {
                    var removeClsFinal = false
                    visitMethod(mapOf(ns to (memberName.value to memberDesc.getMethodDescriptor())))?.use {
                        for ((flag, conditions) in addAccess) {
                            visitAccess(AccessType.ADD, flag, conditions, setOf(ns))?.visitEnd()
                        }
                        for ((flag, conditions) in removeAccess) {
                            visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))?.visitEnd()
                            if (flag == AccessFlag.FINAL) {
                                removeClsFinal = true
                            }
                        }
                    }
                    if (removeClsFinal) {
                        visitAccess(
                            AccessType.REMOVE,
                            AccessFlag.FINAL,
                            AccessConditions.ALL,
                            setOf(ns)
                        )?.visitEnd()
                    }
                }
            } else {
                into.visitClass(mapOf(ns to cls.getInternalName()))?.use {
                    visitField(mapOf(ns to (memberName.value to memberDesc.getFieldDescriptor())))?.use {
                        for ((flag, conditions) in addAccess) {
                            visitAccess(AccessType.ADD, flag, conditions, setOf(ns))?.visitEnd()
                        }
                        for ((flag, conditions) in removeAccess) {
                            visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))?.visitEnd()
                        }
                    }
                }
            }
        }
    }

    protected fun readHeader(input: CharReader<*>): Triple<String?, String?, String> {
        val type = input.takeNextLiteral { it.isWhitespace() }
        input.takeWhitespace()
        val version = input.takeNextLiteral { it.isWhitespace() }
        input.takeWhitespace()
        val namespace = input.takeNextLiteral { it.isWhitespace() }!!
        return Triple(type, version, namespace)
    }

    protected fun parseAWMappings(
        input: CharReader<*>,
        version: String,
        allowNonTransitive: Boolean
    ): List<AWReader.AWItem> {
        fun delimiter(c: Char): Boolean = (version == "v1" && c.isWhitespace()) || (version == "v2" && (c == ' ' || c == '\t'))

        val targets = mutableListOf<AWReader.AWItem>()
        handleFirstAndLastLines(input, targets)

        while (!input.exhausted()) {
            if (handleCommentsAndBlanks(input, targets)) continue

            val access = input.takeNextLiteral { delimiter(it) }!!
            input.takeWhitespace()
            val target = input.takeNextLiteral { delimiter(it) }!!
            input.takeWhitespace()

            if (!access.startsWith("transitive-") && !allowNonTransitive) {
                input.takeLine()
                continue
            }

            handleAccessWideners(target, input, targets, access, ::delimiter)

            handleFirstAndLastLines(input, targets)
        }
        return targets
    }

    protected fun <T : CTReader.CTItem> handleAccessWideners(
        target: String,
        input: CharReader<*>,
        targets: MutableList<T>,
        access: String,
        delimiter: (Char) -> Boolean
    ) {
        when (target) {
            "class" -> {
                val cls = InternalName.read(input.takeNextLiteral { delimiter(it) }!!)
                targets.add(AWReader.AWData(access, FullyQualifiedName(ObjectType(cls), null)) as T)
            }

            "method" -> {
                val cls = InternalName.read(input.takeNextLiteral { delimiter(it) }!!)
                input.takeWhitespace()
                val method = input.takeNextLiteral { delimiter(it) }!!
                input.takeWhitespace()
                val desc = MethodDescriptor.read(input.takeNextLiteral { delimiter(it) }!!)
                targets.add(
                    AWReader.AWData(
                        access,
                        FullyQualifiedName(
                            ObjectType(cls),
                            NameAndDescriptor(UnqualifiedName.read(method), FieldOrMethodDescriptor(desc))
                        )
                    ) as T
                )
            }

            "field" -> {
                val cls = InternalName.read(input.takeNextLiteral { delimiter(it) }!!)
                input.takeWhitespace()
                val field = input.takeNextLiteral { delimiter(it) }!!
                input.takeWhitespace()
                val desc = FieldDescriptor.read(input.takeNextLiteral { delimiter(it) }!!)
                targets.add(
                    AWReader.AWData(
                        access,
                        FullyQualifiedName(
                            ObjectType(cls),
                            NameAndDescriptor(UnqualifiedName.read(field), FieldOrMethodDescriptor(desc))
                        )
                    ) as T
                )
            }

            else -> {
                throw IllegalArgumentException("Unknown target $target")
            }
        }
    }

    protected fun <T : CTReader.CTItem> handleCommentsAndBlanks(
        input: CharReader<*>,
        targets: MutableList<T>
    ): Boolean {
        if (input.peek() == '\n') {
            input.take()
            targets.add(AWReader.AWNewline as T)
            return true
        }
        if (input.peek() == '#') {
            targets.add(AWReader.AWComment(input.takeLine(), true) as T)

            if (input.peek() == '\n') {
                input.take()
            }
            return true
        }
        if (input.peek()?.isWhitespace() == true) {
            throw IllegalStateException("Unexpected whitespace")
        }
        return false
    }

    protected fun <T : CTReader.CTItem> handleFirstAndLastLines(
        input: CharReader<*>,
        targets: MutableList<T>
    ) {
        val remain = input.takeLine().trimStart()
        if (remain.isNotEmpty()) {
            if (remain.first() != '#') {
                throw IllegalArgumentException("Expected newline or comment, found $remain")
            }
            targets.add(AWReader.AWComment(remain, false) as T)
        }

        if (input.peek() == '\n') {
            input.take()
        }
    }
}

object AWReader: FormatReader, AWLikeReader() {

    @Suppress("MemberVisibilityCanBePrivate")
    var allowNonTransitive = true

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        // check content begins with "accessWidener"
        return input.peek().readUtf8Line()?.startsWith("accessWidener") ?: false
    }

    sealed interface AWItem : CTReader.CTItem

    data class AWData(
        val access: String,
        val target: FullyQualifiedName
    ) : AWItem, CTReader.CTData

    data class AWComment(
        val comment: String,
        val newline: Boolean
    ) : AWItem, CTReader.CTItem

    object AWNewline : AWItem

    data class AWMappings(
        val namespace: Namespace,
        val targets: List<AWItem>
    )

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {

        val (namespace, targets) = readData(input)

        into.use {
            into.visitHeader(nsMapping[namespace.name] ?: namespace.name)
            val ns = nsMapping[namespace.name]?.let { Namespace(it) } ?: namespace

            for ((access, target) in targets.filterIsInstance<AWData>()) {
                readAWData(target, access, into, ns, allowNonTransitive)
            }
        }
    }

    fun readData(input: CharReader<*>): AWMappings {
        val (type, version, namespace) = readHeader(input)

        if (type != "accessWidener") {
            throw IllegalArgumentException("Invalid access widener file")
        }
        if (version !in setOf("v1", "v2")) {
            throw IllegalArgumentException("Unknown version $version")
        }

        return AWMappings(Namespace(namespace), parseAWMappings(input, version!!, allowNonTransitive))
    }

}
