package xyz.wagyourtail.unimined.mapping.formats.unpick

import okio.BufferedSource
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.element.AddRemoveModify
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * FabricMC's unpick format to represent constant groups.
 */
object UnpickReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        val first = input.peek().readUtf8Line() ?: return false
        return fileName.endsWith(".unpick") && (first.startsWith("v2") || first == "unpick v3" || first == "unpick v4")
    }

    data class UnpickConstant(val type: String, val intlName: InternalName, val fieldName: UnqualifiedName)
    data class UnpickTarget(val intlName: InternalName, val targetName: UnqualifiedName, val targetDesc: MethodDescriptor, val params: MutableList<UnpickParam>)
    data class UnpickParam(val index: Int, val group: String)

    override suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val version = input.takeLine()
        when (version) {
            "v2" -> readV2(input, into, nsMapping)
            "unpick v3" -> readV3(input, into, nsMapping, 3)
            "unpick v4" -> readV3(input, into, nsMapping, 4)
            else -> throw IllegalArgumentException("Invalid unpick file, unknown version $version")
        }
    }

    fun readV2(
        input: CharReader<*>,
        into: MappingSink,
        nsMapping: Map<String, String>,
    ) {
        val constants = defaultedMapOf<String, MutableList<UnpickConstant>> { mutableListOf() }
        val targets = mutableListOf<UnpickTarget>()
        var currentTarget: UnpickTarget? = null
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val indent = input.takeWhitespace().length
            val line = input.takeRemainingOnLine()
            if (line.isEmpty()) continue
            if (indent == 0) {
                when (line[0]) {
                    "constant" -> {
                        val key = line[1]
                        val intlName = InternalName.read(line[2])
                        val fieldName = UnqualifiedName.read(line[3])
                        constants.getValue(key).add(UnpickConstant("constant", intlName, fieldName))
                        currentTarget = null
                    }

                    "flag" -> {
                        val key = line[1]
                        val intlName = InternalName.read(line[2])
                        val fieldName = UnqualifiedName.read(line[3])
                        constants.getValue(key).add(UnpickConstant("flag", intlName, fieldName))
                        currentTarget = null
                    }

                    "target_method" -> {
                        val intlName = InternalName.read(line[1])
                        val targetName = UnqualifiedName.read(line[2])
                        val targetDesc = MethodDescriptor.read(line[3])
                        targets.add(UnpickTarget(intlName, targetName, targetDesc, mutableListOf()))
                        currentTarget = targets.last()
                    }
                }
            } else {
                if (currentTarget == null) {
                    throw IllegalArgumentException("Invalid unpick file, found double indent")
                }
                when (line[0]) {
                    "param" -> {
                        val index = line[1].toInt()
                        val group = line[2]
                        currentTarget.params.add(UnpickParam(index, group))
                    }
                    "return" -> {
                        val group = line[1]
                        currentTarget.params.add(UnpickParam(-1, group))
                    }
                }
            }
        }
        val ns = Namespace(nsMapping["source"] ?: "source")

        with (into) {
            header(listOf(ns))

            val targetsByGroup = defaultedMapOf<String, MutableList<UnpickTarget>> { mutableListOf() }
            for (target in targets) {
                for (param in target.params) {
                    targetsByGroup.getValue(param.group).add(target)
                }
            }

            for ((group, consts) in constants) {
                val targs = targetsByGroup[group]
                val type = if (consts.any { it.type == "flag" }) {
                    InlineType.BITFIELD
                } else {
                    InlineType.PLAIN
                }

                pushed(MutableConstantGroupMapping(type, group, ns)) {
                    for (const in consts) {
                        pushed(ConstantElement(const.intlName, const.fieldName.withFieldDesc(null))) {}
                    }
                    for (target in targs) {
                        for (param in target.params) {
                            if (param.group != group) continue
                            pushed(TargetElement(
                                FullyQualifiedName(
                                    ObjectType(target.intlName),
                                    NameAndDescriptor(target.targetName, FieldOrMethodDescriptor(target.targetDesc))
                                ), param.index
                            )) {}
                        }
                    }
                }
            }
        }
        into.end()
    }

    /** unpick v3 data types that are not valid group types per the spec. */
    private val v3GroupTypes = setOf("int", "long", "float", "double", "String", "Class")

    private class V3Group(val dataType: String, val name: String?) {
        var flags = false
        /** `@` attributes in file order (`@scope ...`, `@strict`, `@format ...`, minus `@flags`) */
        val attrs = mutableListOf<String>()
        val consts = mutableListOf<String>()
        /** contiguous `#` comment lines preceding the group definition */
        val docs = mutableListOf<String>()
    }

    private fun binaryToInternal(name: String) = InternalName.read(name.replace('.', '/'))

    private val v3FieldRef = Regex(
        "^[a-zA-Z_\$][a-zA-Z0-9_\$]*(?:\\.[a-zA-Z_\$][a-zA-Z0-9_\$]*)*\\.([a-zA-Z_\$][a-zA-Z0-9_\$]*|\\*)(?::instance)?(?::[a-zA-Z_\$][a-zA-Z0-9_\$]*)?$"
    )
    private val v3Types = mapOf(
        "byte" to "B", "short" to "S", "int" to "I", "long" to "J",
        "float" to "F", "double" to "D", "char" to "C",
        "String" to "Ljava/lang/String;", "Class" to "Ljava/lang/Class;"
    )

    /**
     * unpick v3/v4 (https://github.com/FabricMC/unpick/issues/26).
     *
     * `group <type> [name]` blocks carry indented `@attribute` lines and constant
     * expressions; `target_method` blocks carry indented `param`/`return` items;
     * `target_field` and (v4) `target_annotation` are single lines.
     *
     * mapping into the element model:
     * - contiguous `#` comment lines before a group become group javadoc
     * - the group's data type and `@scope`/`@strict`/`@format` attributes become
     *   `Lunimined/unpick/...;` annotations on the group
     * - `@flags` maps to a bitfield group
     * - plain `Cls.FIELD[:type]` references (incl. `Cls.*` wildcards) become
     *   constant elements; `:instance` markers and any other expressions are
     *   preserved verbatim as expression elements
     * - `target_field`/`target_annotation` emit class-level targets
     *   (paramIdx null); annotation targets are distinguishable by having no
     *   member descriptor on the target
     */
    fun readV3(
        input: CharReader<*>,
        into: MappingSink,
        nsMapping: Map<String, String>,
        version: Int = 3,
    ) {
        val groups = linkedMapOf<String, V3Group>()
        val targets = mutableListOf<UnpickTarget>()
        var currentGroup: V3Group? = null
        var currentTarget: UnpickTarget? = null
        val pendingDocs = mutableListOf<String>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val indented = input.peek() == ' ' || input.peek() == '\t'
            input.takeWhitespace()
            val raw = input.takeLine()
            if (raw.startsWith("#") || raw.isBlank() && raw.contains('#')) {
                // comment-only line: candidate doc comment for the next item
                val text = raw.substring(raw.indexOf('#') + 1).trim()
                if (text.isNotEmpty()) pendingDocs.add(text)
                continue
            }
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) continue

            if (!indented) {
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "group" -> {
                        val type = parts[1]
                        if (type !in v3GroupTypes) {
                            throw IllegalArgumentException("Invalid unpick v3 group data type: $type")
                        }
                        val name = parts.getOrNull(2)
                        currentGroup = groups.getOrPut(name ?: "") { V3Group(type, name) }
                        currentGroup!!.docs.addAll(pendingDocs)
                        pendingDocs.clear()
                        currentTarget = null
                    }
                    "target_method" -> {
                        pendingDocs.clear()
                        currentTarget = UnpickTarget(
                            binaryToInternal(parts[1]),
                            UnqualifiedName.read(parts[2]),
                            MethodDescriptor.read(parts[3]),
                            mutableListOf()
                        ).also { targets.add(it) }
                        currentGroup = null
                    }
                    "target_field" -> {
                        pendingDocs.clear()
                        targets.add(UnpickTarget(
                            binaryToInternal(parts[1]),
                            UnqualifiedName.read(parts[2]),
                            MethodDescriptor.unchecked("()${parts[3]}"),
                            mutableListOf(UnpickParam(FIELD_TARGET, parts[4]))
                        ))
                        currentGroup = null
                        currentTarget = null
                    }
                    "target_annotation" -> {
                        if (version < 4) {
                            throw IllegalArgumentException("target_annotation is not supported in unpick format version $version")
                        }
                        pendingDocs.clear()
                        targets.add(UnpickTarget(
                            binaryToInternal(parts[1]),
                            UnqualifiedName.unchecked(""),
                            MethodDescriptor.unchecked("()V"),
                            mutableListOf(UnpickParam(ANNOTATION_TARGET, parts[2]))
                        ))
                        currentGroup = null
                        currentTarget = null
                    }
                    else -> throw IllegalArgumentException("Invalid unpick v3 line: $line")
                }
            } else if (currentGroup != null) {
                if (line.startsWith("@")) {
                    if (line == "@flags") currentGroup!!.flags = true else currentGroup!!.attrs.add(line)
                } else {
                    currentGroup!!.consts.add(line)
                }
            } else if (currentTarget != null) {
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "param" -> currentTarget!!.params.add(UnpickParam(parts[1].toInt(), parts[2]))
                    "return" -> currentTarget!!.params.add(UnpickParam(-1, parts[1]))
                    else -> throw IllegalArgumentException("Invalid unpick v3 target item: $line")
                }
            } else {
                throw IllegalArgumentException("Invalid unpick v3, indent with no group/target: $line")
            }
        }

        val ns = Namespace(nsMapping["source"] ?: "source")

        with (into) {
            header(listOf(ns))

            val targetsByGroup = defaultedMapOf<String, MutableList<UnpickTarget>> { mutableListOf() }
            for (target in targets) {
                for (param in target.params) {
                    targetsByGroup.getValue(param.group).add(target)
                }
            }

            for ((key, group) in groups) {
                pushed(MutableConstantGroupMapping(if (group.flags) InlineType.BITFIELD else InlineType.PLAIN, group.name, ns)) {
                    if (group.docs.isNotEmpty()) {
                        pushed(JavadocElement(group.docs.joinToString("\n"), ns)) {}
                    }
                    pushed(AnnotationElement(AddRemoveModify.ADD, ns, Annotation.unchecked("@Lunimined/unpick/DataType;(value=\"${group.dataType}\")"))) {}
                    for (attr in group.attrs) {
                        pushed(AnnotationElement(AddRemoveModify.ADD, ns, Annotation.unchecked(encodeV3Attr(attr)))) {}
                    }
                    for (const in group.consts) {
                        emitV3Const(this, const)
                    }
                    for (target in targetsByGroup[key] ?: emptyList()) {
                        for (param in target.params) {
                            if (param.group != key) continue
                            when (param.index) {
                                ANNOTATION_TARGET -> pushed(TargetElement(
                                    FullyQualifiedName(ObjectType(target.intlName), null), null
                                )) {}
                                FIELD_TARGET -> pushed(TargetElement(
                                    FullyQualifiedName(
                                        ObjectType(target.intlName),
                                        NameAndDescriptor(target.targetName, FieldDescriptor.unchecked(target.targetDesc.value.removePrefix("()")))
                                    ), null
                                )) {}
                                else -> pushed(TargetElement(
                                    FullyQualifiedName(
                                        ObjectType(target.intlName),
                                        NameAndDescriptor(target.targetName, FieldOrMethodDescriptor(target.targetDesc))
                                    ), param.index
                                )) {}
                            }
                        }
                    }
                }
            }
        }
        into.end()
    }

    private const val FIELD_TARGET = -2
    private const val ANNOTATION_TARGET = -3

    /** encode an unpick `@` attribute as a `Lunimined/unpick/...;` annotation literal */
    private fun encodeV3Attr(attr: String): String {
        val parts = attr.removePrefix("@").split(Regex("\\s+"))
        return when (parts[0]) {
            "strict" -> "@Lunimined/unpick/Strict;()"
            "format" -> "@Lunimined/unpick/Format;(value=\"${parts[1]}\")"
            "scope" -> when (parts[1]) {
                "package", "class" -> "@Lunimined/unpick/Scope;(type=\"${parts[1]}\",target=\"${parts[2]}\")"
                "method" -> "@Lunimined/unpick/Scope;(type=\"method\",target=\"${parts[2]}\",name=\"${parts[3]}\",desc=\"${parts[4]}\")"
                else -> "@Lunimined/unpick/Scope;(value=\"${parts.drop(1).joinToString(" ")}\")"
            }
            else -> "@Lunimined/unpick/Attribute;(value=\"${attr.removePrefix("@")}\")"
        }
    }

    /**
     * emit one v3 group const. plain `Cls.FIELD[:type]` references (including
     * `Cls.*` wildcards) become constant elements; `:instance` markers and any
     * other expressions are preserved verbatim as expression elements.
     */
    private fun emitV3Const(into: MappingSink, expr: String) {
        with (into) {
            if (expr.endsWith(":instance") || !v3FieldRef.matches(expr)) {
                pushed(ExpressionElement(Constant.unchecked(expr), Expression.unchecked(expr))) {}
                return@with
            }
            val m = v3FieldRef.matchEntire(expr) ?: return@with
            val (cls, field) = expr.substringBeforeLast('.') to expr.substringAfterLast('.').substringBefore(':')
            pushed(ConstantElement(
                InternalName.read(cls.replace('.', '/')),
                UnqualifiedName.read(field).withFieldDesc(null)
            )) {}
        }
    }
}