package xyz.wagyourtail.unimined.mapping.formats.ct

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InterfaceElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.aw.applyAccessWidener
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/** Reads ClassTweaker v1/v2 directives into mapping access and metadata elements.
 * First-party implementation: https://github.com/FabricMC/fabric-tooling/blob/b01b36a/class-tweaker/src/main/java/net/fabricmc/classtweaker/reader/ClassTweakerReaderImpl.java
 */
object CTReader : FormatReader {
    /** Whether to ignore non-transitive directives while reading. */
    var allowNonTransitive: Boolean = true

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false

    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean =
        input.peek().readUtf8Line()?.startsWith("classTweaker") == true

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings,
    ) {
        val mappings = readData(input)
        val namespace = nsMapping[mappings.namespace.name]?.let { Namespace(it) } ?: mappings.namespace
        into.header(listOf(namespace))

        for (directive in mappings.targets) {
            when (directive) {
                is AccessDirective -> {
                    if (include(directive.access))
                        into.applyAccessWidener(directive.target, directive.access, namespace, allowNonTransitive)
                }
                is InterfaceDirective -> {
                    if (!include(directive.access)) continue
                    into.pushed(MutableClassMapping(mapOf(namespace to directive.owner))) {
                        into.push(InterfaceElement(AddRemove.ADD, directive.signature, namespace))
                    }
                }
                is EnumDirective -> {
                    if (!include(directive.access)) continue
                    into.pushed(MutableClassMapping(mapOf(namespace to directive.owner))) {
                        into.push(EnumExtensionElement(AddRemove.ADD, directive.name, namespace))
                    }
                }
                else -> {}
            }
        }
        into.end()
    }

    private fun include(access: String) = allowNonTransitive || access.startsWith("transitive-")

    sealed interface CTItem

    sealed interface CTDirective : CTItem {
        val access: String
    }

    data class AccessDirective(override val access: String, val target: FullyQualifiedName) : CTDirective
    data class InterfaceDirective(
        override val access: String,
        val owner: InternalName,
        val signature: ClassTypeSignature,
    ) : CTDirective
    data class EnumDirective(
        override val access: String,
        val owner: InternalName,
        val name: UnqualifiedName,
    ) : CTDirective

    data class CTComment(
        val comment: String
    ) : CTItem

    object CTNewline : CTItem

    data class CTMappings(
        val version: String,
        val namespace: Namespace,
        val targets: List<CTItem>
    )

    fun readData(input: CharReader<*>): CTMappings {
        val header = input.takeLine().trim().split(Regex("\\s+"))
        if (input.peek() == '\n') input.take()
        require(header.size == 3 && header[0] == "classTweaker") { "Invalid ClassTweaker header" }
        val version = header[1]
        require(version == "v1" || version == "v2") { "Unsupported ClassTweaker version $version" }
        val directives = mutableListOf<CTItem>()

        while (!input.exhausted()) {
            val line = input.takeLine().trim()
            if (input.peek() == '\n') input.take()
            if (line.isEmpty()) {
                directives += CTNewline
                continue
            }
            if (line.startsWith("#")) {
                directives += CTComment(line)
                continue
            }
            val fields = line.split(Regex("\\s+"))
            val access = fields[0]

            when (access.removePrefix("transitive-")) {
                "accessible", "extendable", "mutable" ->
                    directives += AccessDirective(access, parseAccessTarget(fields))
                "inject-interface" -> {
                    require(fields.size == 3) { "Invalid inject-interface directive: $line" }
                    directives += InterfaceDirective(
                        access, InternalName.read(fields[1]), ClassTypeSignature.read("L${fields[2]};")
                    )
                }
                "extend-enum" -> {
                    require(version == "v2") { "extend-enum requires ClassTweaker v2" }
                    require(fields.size == 3) { "Invalid extend-enum directive: $line" }
                    directives += EnumDirective(access, InternalName.read(fields[1]), UnqualifiedName.read(fields[2]))
                }
                else -> throw IllegalArgumentException("Unknown ClassTweaker operation $access")
            }
        }
        return CTMappings(version, Namespace(header[2]), directives)
    }

    private fun parseAccessTarget(fields: List<String>): FullyQualifiedName {
        require(fields.size >= 3) { "Incomplete ClassTweaker access directive" }
        val owner = InternalName.read(fields[2])
        return when (fields[1]) {
            "class" -> {
                require(fields.size == 3) { "Invalid class access directive" }
                FullyQualifiedName(ObjectType(owner), null)
            }
            "field" -> {
                require(fields.size == 5) { "Invalid field access directive" }
                val descriptor = FieldDescriptor.read(fields[4])
                val name = UnqualifiedName.read(fields[3])
                FullyQualifiedName(ObjectType(owner), NameAndDescriptor(name, FieldOrMethodDescriptor(descriptor)))
            }
            "method" -> {
                require(fields.size == 5) { "Invalid method access directive" }
                val descriptor = MethodDescriptor.read(fields[4])
                val name = UnqualifiedName.read(fields[3])
                FullyQualifiedName(ObjectType(owner), NameAndDescriptor(name, FieldOrMethodDescriptor(descriptor)))
            }
            else -> throw IllegalArgumentException("Unknown ClassTweaker target ${fields[1]}")
        }
    }
}
