package xyz.wagyourtail.unimined.mapping.formats.ct

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader.AWComment
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader.AWData
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader.AWNewline
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.InterfacesType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * ClassTweaker is an extension of the AccessWidener format.
 */
object CTReader: FormatReader {

    @Suppress("MemberVisibilityCanBePrivate")
    var allowNonTransitive = true

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return (input.peek().readUtf8Line()?.startsWith("classTweaker") ?: false) || AWReader.isFormat(fileName, input, envType)
    }

    interface CTItem

    interface CTData : CTItem

    data class CTInjectedInterface(
        val access: String,
        val target: InternalName,
        val signature: ClassTypeSignature
    ) : CTData

    data class CTExtendedEnum(
        val access: String,
        val target: InternalName,
        val fieldName: UnqualifiedName
    ) : CTData

    data class CTMappings(
        val namespace: Namespace,
        val targets: List<CTItem>
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

            for (ctData in targets.filterIsInstance<CTData>()) {
                when (ctData) {
                    is AWData -> {
                        val (access, target) = ctData

                        AWReader.readAWData(target, access, into, ns, allowNonTransitive)
                    }
                    is CTInjectedInterface -> {
                        val (access, target, sig) = ctData

                        if (!access.startsWith("transitive-")) {
                            if (!allowNonTransitive) {
                                continue
                            }
                        }

                        into.visitClass(mapOf(ns to target))?.use {
                            visitInterface(InterfacesType.ADD, sig, ns, setOf())
                        }
                    }
                }

            }
        }
    }

    fun readData(input: CharReader<*>): CTMappings {
        val ctType = input.takeNextLiteral { it.isWhitespace() }
        input.takeWhitespace()
        val version = input.takeNextLiteral { it.isWhitespace() }
        input.takeWhitespace()
        val namespace = input.takeNextLiteral { it.isWhitespace() }!!
        val targets = mutableListOf<CTItem>()

        if (ctType == "accessWidener") {
            if (version !in setOf("v1", "v2")) {
                throw IllegalArgumentException("Unknown access widener version $version")
            }

            val awItems = AWReader.parseAWMappings(input, version!!, allowNonTransitive)
            return CTMappings(Namespace(namespace), awItems)
        } else if (ctType != "classTweaker") {
            throw IllegalArgumentException("Invalid class tweaker file")
        }

        if (version !in setOf("v1", "v2")) {
            throw IllegalArgumentException("Unknown version $version")
        }

        fun delimiter(c: Char): Boolean = c == ' ' || c == '\t'

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

            val access = input.takeNextLiteral { delimiter(it) }!!
            input.takeWhitespace()
            val target = input.takeNextLiteral { delimiter(it) }!!
            input.takeWhitespace()

            if (!access.startsWith("transitive-") && !allowNonTransitive) {
                input.takeLine()
                continue
            }

            if (access in listOf("extend-enum", "transitive-extend-enum")) {
                if (version == "v1") {
                    // throw IllegalArgumentException("extend-enum is not supported in class tweaker v1")
                    input.takeLine()
                    continue
                }

                val cls = InternalName.read(target)
                val fieldName = UnqualifiedName.read(input.takeNextLiteral { delimiter(it) }!!)
                targets.add(CTExtendedEnum(access, cls, fieldName))
            } else if (access in listOf("inject-interface", "transitive-inject-interface")) {
                val cls = InternalName.read(target)
                val sig = ClassTypeSignature.read("L${input.takeNextLiteral { delimiter(it) }!!};")
                targets.add(CTInjectedInterface(access, cls, sig))
            } else {
                when (target) {
                    "class" -> {
                        val cls = InternalName.read(input.takeNextLiteral { delimiter(it) }!!)
                        targets.add(AWData(access, FullyQualifiedName(ObjectType(cls), null)))
                    }

                    "method" -> {
                        val cls = InternalName.read(input.takeNextLiteral { delimiter(it) }!!)
                        input.takeWhitespace()
                        val method = input.takeNextLiteral { delimiter(it) }!!
                        input.takeWhitespace()
                        val desc = MethodDescriptor.read(input.takeNextLiteral { delimiter(it) }!!)
                        targets.add(
                            AWData(
                                access,
                                FullyQualifiedName(
                                    ObjectType(cls),
                                    NameAndDescriptor(UnqualifiedName.read(method), FieldOrMethodDescriptor(desc))
                                )
                            )
                        )
                    }

                    "field" -> {
                        val cls = InternalName.read(input.takeNextLiteral { delimiter(it) }!!)
                        input.takeWhitespace()
                        val field = input.takeNextLiteral { delimiter(it) }!!
                        input.takeWhitespace()
                        val desc = FieldDescriptor.read(input.takeNextLiteral { delimiter(it) }!!)
                        targets.add(
                            AWData(
                                access,
                                FullyQualifiedName(
                                    ObjectType(cls),
                                    NameAndDescriptor(UnqualifiedName.read(field), FieldOrMethodDescriptor(desc))
                                )
                            )
                        )
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown target $target")
                    }
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
        return CTMappings(Namespace(namespace), targets)
    }
}
