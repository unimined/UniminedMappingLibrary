package xyz.wagyourtail.unimined.mapping.formats.aw

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * First-party implementation: https://github.com/FabricMC/access-widener/blob/30e2d00/src/main/java/net/fabricmc/accesswidener/AccessWidenerReader.java
 */
object AWReader: FormatReader {

    @Deprecated("set within the settings argument instead")
    var allowNonTransitive = true

    /** [FormatReaderSettings] key for the allowNonTransitive tunable —
     *  `FormatReaderSettings(extras = mapOf(ALLOW_NON_TRANSITIVE to false))` */
    val ALLOW_NON_TRANSITIVE = FormatReaderSettings.SettingsKey<Boolean>("aw.allowNonTransitive")

    /** settings-facing read for the tunable — `settings.allowNonTransitive` */
    private val FormatReaderSettings.allowNonTransitive get() = this[ALLOW_NON_TRANSITIVE]

    private fun awSettings(settings: FormatReaderSettings): Boolean =
        settings.allowNonTransitive ?: allowNonTransitive

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        // check content begins with "accessWidener"
        return input.peek().readUtf8Line()?.startsWith("accessWidener") ?: false
    }

    sealed interface AWItem

    data class AWData(
        val access: String,
        val target: FullyQualifiedName
    ) : AWItem, Comparable<AWData> {
        init {
            target.getParts()
        }

        override fun compareTo(other: AWData): Int {
            val i = access.compareTo(other.access)
            if (i != 0) return i
            return target.compareTo(other.target)
        }
    }

    data class AWComment(
        val comment: String,
        val newline: Boolean
    ) : AWItem

    object AWNewline : AWItem

    data class AWMappings(
        val namespace: Namespace,
        val targets: List<AWItem>
    )

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
        
        val (namespace, targets) = readData(input, awSettings(settings))

        with (into) {
            header(listOf(nsMapping[namespace.name]?.let { Namespace(it) } ?: namespace))
            val ns = nsMapping[namespace.name]?.let { Namespace(it) } ?: namespace

            for ((access, target) in targets.filterIsInstance<AWData>()) {
                into.applyAccessWidener(target, access, ns, awSettings(settings))
            }
        }
        into.end()
    }

    fun readData(input: CharReader<*>, allowNonTransitive: Boolean = this.allowNonTransitive): AWMappings {
        val aw = input.takeNextLiteral { it.isWhitespace() }
        input.takeWhitespace()
        val version = input.takeNextLiteral { it.isWhitespace() }
        input.takeWhitespace()
        val namespace = input.takeNextLiteral { it.isWhitespace() }!!
        val targets = mutableListOf<AWItem>()

        if (aw != "accessWidener") {
            throw IllegalArgumentException("Invalid access widener file")
        }
        if (version !in setOf("v1", "v2")) {
            throw IllegalArgumentException("Unknown version $version")
        }

        val remain = input.takeLine().trimStart()
        if (remain.isNotEmpty()) {
            if (remain.first() != '#') {
                throw IllegalArgumentException("Expected newline or comment, found $remain")
            }
            targets.add(AWComment(remain, false))
        }

        if (input.peek() == '\n') {
            input.take()
        }

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                targets.add(AWNewline)
                continue
            }
            if (input.peek() == '#') {
                targets.add(AWComment(input.takeLine(), true))

                if (input.peek() == '\n') {
                    input.take()
                }
                continue
            }
            if (input.peek()?.isWhitespace() == true) {
                throw IllegalStateException("Unexpected whitespace")
            }

            val access = input.takeNextLiteral { it.isWhitespace() }!!
            input.takeWhitespace()
            val target = input.takeNextLiteral { it.isWhitespace() }!!
            input.takeWhitespace()

            if (!access.startsWith("transitive-") && !allowNonTransitive) {
                input.takeLine()
                continue
            }

            when (target) {
                "class" -> {
                    val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    targets.add(AWData(access, FullyQualifiedName(ObjectType(cls), null)))
                }

                "method" -> {
                    val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    input.takeWhitespace()
                    val method = input.takeNextLiteral { it.isWhitespace() }!!
                    input.takeWhitespace()
                    val desc = MethodDescriptor.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    targets.add(
                        AWData(
                            access,
                            FullyQualifiedName(
                                ObjectType(cls),
                                NameAndDescriptor(UnqualifiedName.read(method), desc)
                            )
                        )
                    )
                }

                "field" -> {
                    val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    input.takeWhitespace()
                    val field = input.takeNextLiteral { it.isWhitespace() }!!
                    input.takeWhitespace()
                    val desc = FieldDescriptor.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    targets.add(
                        AWData(
                            access,
                            FullyQualifiedName(
                                ObjectType(cls),
                                NameAndDescriptor(UnqualifiedName.read(field), desc)
                            )
                        )
                    )
                }

                else -> {
                    throw IllegalArgumentException("Unknown target $target")
                }
            }

            val lineComment = input.takeLine().trimStart()
            if (lineComment.isNotEmpty()) {
                if (lineComment.first() != '#') {
                    throw IllegalArgumentException("Expected newline or comment, found $lineComment")
                }
                targets.add(AWComment(lineComment, false))
            }

            if (input.peek() == '\n') {
                input.take()
            }
        }
        return AWMappings(Namespace(namespace), targets)
    }
}

internal fun MappingSink.applyAccessWidener(
    target: FullyQualifiedName,
    access: String,
    namespace: Namespace,
    allowNonTransitive: Boolean,
) {
    if (!access.startsWith("transitive-") && !allowNonTransitive) return

    val addAccess = mutableSetOf<Pair<AccessFlag, AccessConditions>>()
    val removeAccess = mutableSetOf<Pair<AccessFlag, AccessConditions>>()
    val (cls, member) = target.getParts()

    when (access) {
        "accessible", "transitive-accessible" -> {
            addAccess += AccessFlag.PUBLIC to AccessConditions.ALL
            if (member is MethodNameAndDescriptor) {
                addAccess += AccessFlag.FINAL to AccessConditions.unchecked("+${AccessFlag.PRIVATE}")
            }
        }
        "mutable", "transitive-mutable" -> {
            if (member !is FieldNameAndDescriptor) {
                throw IllegalArgumentException("mutable is only valid for fields")
            }
            removeAccess += AccessFlag.FINAL to AccessConditions.ALL
        }
        "extendable", "transitive-extendable" -> {
            if (member == null) {
                addAccess += AccessFlag.PUBLIC to AccessConditions.ALL
                removeAccess += AccessFlag.FINAL to AccessConditions.ALL
            } else {
                if (member !is MethodNameAndDescriptor) {
                    throw IllegalArgumentException("extendable is not valid for fields")
                }
                addAccess += AccessFlag.PROTECTED to AccessConditions.unchecked("-${AccessFlag.PUBLIC}")
                removeAccess += AccessFlag.FINAL to AccessConditions.ALL
            }
        }
        else -> throw IllegalArgumentException("Unknown access widener operation $access")
    }

    if (member == null) {
        pushed(MutableClassMapping(mapOf(namespace to cls.getInternalName()))) {
            addAccess.forEach { (flag, conditions) -> pushed(AccessElement(AddRemove.ADD, flag, conditions)) {} }
            removeAccess.forEach { (flag, conditions) -> pushed(AccessElement(AddRemove.REMOVE, flag, conditions)) {} }
        }
    } else when (member) {
        is MethodNameAndDescriptor -> pushed(MutableClassMapping(mapOf(namespace to cls.getInternalName()))) {
            var removeClassFinal = false
            pushed(MutableMethodMapping(mapOf(namespace to member))) {
                addAccess.forEach { (flag, conditions) -> pushed(AccessElement(AddRemove.ADD, flag, conditions)) {} }
                removeAccess.forEach { (flag, conditions) ->
                    pushed(AccessElement(AddRemove.REMOVE, flag, conditions)) {}
                    if (flag == AccessFlag.FINAL) removeClassFinal = true
                }
            }
            if (removeClassFinal) {
                pushed(AccessElement(AddRemove.REMOVE, AccessFlag.FINAL, AccessConditions.ALL)) {}
            }
        }
        is FieldNameAndDescriptor -> pushed(MutableClassMapping(mapOf(namespace to cls.getInternalName()))) {
            pushed(MutableFieldMapping(mapOf(namespace to member))) {
                addAccess.forEach { (flag, conditions) -> pushed(AccessElement(AddRemove.ADD, flag, conditions)) {} }
                removeAccess.forEach { (flag, conditions) -> pushed(AccessElement(AddRemove.REMOVE, flag, conditions)) {} }
            }
        }
    }
}