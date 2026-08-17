package xyz.wagyourtail.unimined.mapping.formats.ct

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.aw.AWLikeReader
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader.AWData
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.InterfacesType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * ClassTweaker is an extension of the AccessWidener format.
 */
object CTReader: FormatReader, AWLikeReader() {

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

                        readAWData(target, access, into, ns, allowNonTransitive)
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
        val (ctType, version, namespace) = readHeader(input)
        val targets = mutableListOf<CTItem>()

        if (ctType == "accessWidener") {
            if (version !in setOf("v1", "v2")) {
                throw IllegalArgumentException("Unknown access widener version $version")
            }

            val awItems = parseAWMappings(input, version!!, allowNonTransitive)
            return CTMappings(Namespace(namespace), awItems)
        } else if (ctType != "classTweaker") {
            throw IllegalArgumentException("Invalid class tweaker file")
        }

        if (version !in setOf("v1", "v2")) {
            throw IllegalArgumentException("Unknown version $version")
        }

        fun delimiter(c: Char): Boolean = c == ' ' || c == '\t'

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
                handleAccessWideners(target, input, targets, access, ::delimiter)
            }

            handleFirstAndLastLines(input, targets)
        }
        return CTMappings(Namespace(namespace), targets)
    }
}
