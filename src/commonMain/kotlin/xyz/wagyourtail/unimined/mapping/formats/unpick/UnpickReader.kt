package xyz.wagyourtail.unimined.mapping.formats.unpick

import okio.BufferedSource
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.ParseException
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemoveModify
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.*
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

    override suspend fun readImpl(
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
        /** group name → annotation binary class names (v4 `target_annotation`) */
        val annotationTargets = mutableMapOf<String, MutableList<String>>()
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
                // comment-only line: doc comment for the next item; `#:` is the
                // spec's javadoc convention (colon stripped), plain `#` kept too
                var text = raw.substring(raw.indexOf('#') + 1)
                if (text.startsWith(":")) text = text.substring(1)
                if (text.trim().isNotEmpty()) pendingDocs.add(text.trim())
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
                        annotationTargets.getOrPut(parts[2]) { mutableListOf() }.add(parts[1])
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
                        val ann = AnnotationElement(AddRemoveModify.ADD, ns, Annotation.unchecked(encodeV3Attr(attr)))
                        pushed(ann) {}
                        // class scopes are also real class-level targets — same
                        // "literals inside that class" semantics
                        if (attr.startsWith("@scope class ")) {
                            pushed(TargetElement(
                                FullyQualifiedName(
                                    ObjectType(binaryToInternal(attr.removePrefix("@scope class ").substringBefore('#').trim())),
                                    null
                                ), null
                            )) {}
                        }
                    }
                    for (annCls in annotationTargets[key] ?: emptyList()) {
                        pushed(AnnotationElement(AddRemoveModify.ADD, ns,
                            Annotation.unchecked("@Lunimined/unpick/TargetAnnotation;(value=\"L${annCls.replace('.', '/')};\")"))) {}
                    }
                    for (const in group.consts) {
                        emitV3Const(this, const)
                    }
                    for (target in targetsByGroup[key] ?: emptyList()) {
                        for (param in target.params) {
                            if (param.group != key) continue
                            when (param.index) {
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

    /** datatype keyword → field descriptor, for `:`type suffixes */
    private val v3DataTypes = setOf("byte", "short", "int", "long", "float", "double", "char", "String", "Class")

    /** parse result for a single v3 const expression */
    private class ParsedConst(
        /** canonical internal-grammar form of the expression */
        val canonical: String,
        /** non-null when the whole expression is a single `Cls.FIELD[:instance][:type]` ref */
        val fieldRef: FieldRef?,
    ) {
        class FieldRef(val owner: InternalName, val name: UnqualifiedName, val desc: FieldDescriptor?)
    }

    /**
     * emit one v3 group const. Everything is normalized into the canonical
     * internal expression grammar: `com.foo.Cls.FIELD` → `Lcom/foo/Cls;FIELD`,
     * `(type)` casts → `<type>`, `:instance`/`:`type markers preserved,
     * `'c'` char literals → int values. Whole-line static field refs (incl.
     * `Cls.*` wildcards) also get a `ConstantElement` (with the `:`type
     * descriptor when given) so `this.` resolution and field declaration work.
     */
    private fun emitV3Const(into: MappingSink, expr: String) {
        val const = parseV3Const(expr)
        with (into) {
            if (const.fieldRef != null && !expr.endsWith(":instance")) {
                pushed(ConstantElement(
                    const.fieldRef.owner,
                    const.fieldRef.name.withFieldDesc(const.fieldRef.desc)
                )) {}
            }
            pushed(ExpressionElement(Constant.unchecked(const.canonical), Expression.unchecked(const.canonical))) {}
        }
    }

    /**
     * parse one v3/v4 const expression, normalizing it into canonical internal
     * form. Tokenizes respecting string/char literals, rewrites:
     * - `'c'` char literals → their int value
     * - `( dtype )` casts → `<dtype>`
     * - `com.foo.Cls.FIELD[:instance][:type]` → `Lcom/foo/Cls;FIELD[:desc][:instance]`
     *   (incl. `Cls.*` wildcards → `Lcls;*`)
     * - everything else (numbers incl. 0x/0b/0 octal, ops, parens, `this.`)
     *   passes through — the grammar is the same precedence structure
     */
    private fun parseV3Const(expr: String): ParsedConst {
        val reader = StringCharReader(expr)
        val out = StringBuilder()
        var fieldRef: ParsedConst.FieldRef? = null
        while (!reader.exhausted()) {
            when (reader.peek()) {
                '"' -> out.append(reader.takeLiteral('"')) // verbatim incl. escapes
                '\'' -> { // char literal → int value
                    val lit = reader.takeLiteral('\'')
                    if (lit.length > 2 && lit.last() == '\'') {
                        out.append(charValue(lit.substring(1, lit.length - 1)).toString())
                    } else {
                        out.append(lit)
                    }
                }
                '(' -> { // `( dtype )` cast → `<dtype>`; other parens pass through
                    reader.mark()
                    reader.take()
                    reader.takeWhitespace()
                    val word = reader.takeWhile { it.isV3IdentPart() }
                    reader.takeWhitespace()
                    val cast = word in v3DataTypes && reader.peek() == ')' && run {
                        reader.take()
                        val next = reader.peek()
                        next != null && (next.isV3IdentStart() || next in "(~-'0123456789")
                    }
                    if (cast) out.append('<').append(word).append('>')
                    else {
                        reader.reset()
                        out.append(reader.take()!!)
                    }
                }
                else -> {
                    if (!reader.peek()!!.isV3IdentStart()) {
                        out.append(reader.take()!!)
                        continue
                    }
                    val onlyToken = out.isEmpty()
                    val ref = parseV3FieldToken(reader, out)
                    if (onlyToken && ref != null && reader.exhausted()) fieldRef = ref
                }
            }
        }
        return ParsedConst(out.toString(), fieldRef)
    }

    /**
     * parse one dotted-identifier token into [out] — `a.b.Cls.FIELD[:instance][:type]`
     * → `La/b/Cls;FIELD[:desc][:instance]`, `a.b.Cls.*` → `La/b/Cls;*`. Bare
     * (undotted) tokens pass through verbatim, `:`suffixes included.
     * Returns the ref form when the token itself was a `Cls.FIELD` ref —
     * the caller decides whether it was also the whole expression.
     */
    private fun parseV3FieldToken(reader: CharReader<*>, out: StringBuilder): ParsedConst.FieldRef? {
        val chain = reader.takeWhile { it.isV3IdentPart() || it == '.' }
        val suffixes = mutableListOf<String>()
        fun takeSuffixes() {
            while (reader.peek() == ':') {
                reader.take()
                suffixes += reader.takeWhile { it.isV3IdentPart() }
            }
        }
        takeSuffixes()
        // `Cls.*` wildcard — `*` immediately after a dotted chain
        val wildcard = chain.endsWith('.') && reader.peek() == '*'
        if (wildcard) {
            reader.take()
            takeSuffixes()
        }
        if ('.' !in chain && !wildcard) {
            out.append(chain)
            for (s in suffixes) out.append(':').append(s)
            return null
        }
        val cls: String
        val field: String
        if (wildcard) { cls = chain.dropLast(1); field = "*" }
        else { cls = chain.substringBeforeLast('.'); field = chain.substringAfterLast('.') }
        val type = suffixes.firstOrNull { it in v3DataTypes }
        out.append("L${cls.replace('.', '/')};$field")
        if (type != null) out.append(';').append(v3Types[type]!!)
        if ("instance" in suffixes) out.append(":instance")
        // the spec's plain ref shape — non-empty `FIELD`/`*`, then at most
        // `:instance` followed by a single `:`type
        if (field.isEmpty() || suffixes.any { it.isEmpty() } ||
            !(suffixes.size <= 1 || suffixes.size == 2 && suffixes[0] == "instance")) return null
        return ParsedConst.FieldRef(
            InternalName.read(cls.replace('.', '/')),
            UnqualifiedName.read(field),
            type?.let { FieldDescriptor.unchecked(v3Types[it]!!) }
        )
    }

    /** take a [quote]-delimited literal verbatim, incl. quotes; lenient — unterminated input runs to EOS */
    private fun CharReader<*>.takeLiteral(quote: Char): String {
        mark()
        try {
            return takeString(noTranslateEscapes = true, quote = quote.toString())
        } catch (_: ParseException) {
            reset()
        }
        return buildString {
            append(take()!!) // opening quote
            while (true) {
                val c = take() ?: break
                append(c)
                if (c == '\\') take()?.let { append(it) } else if (c == quote) break
            }
        }
    }

    /** java-identifier checks without the JVM-only Char methods (commonMain) */
    private fun Char.isV3IdentStart() = isLetter() || this == '_' || this == '$'
    private fun Char.isV3IdentPart() = isLetterOrDigit() || this == '_' || this == '$'

    /** JLS char literal body → int value (escapes incl. \uXXXX and octal) */
    private fun charValue(body: String): Int {
        if (!body.startsWith("\\")) return body.first().code
        return when (val c = body[1]) {
            'n' -> '\n'.code; 't' -> '\t'.code; 'r' -> '\r'.code
            'b' -> '\b'.code; 'f' -> '\u000C'.code
            '\'' -> '\''.code; '"' -> '"'.code; '\\' -> '\\'.code
            'u' -> body.substring(2).toInt(16)
            else -> body.substring(1).toInt(8) // octal escape
        }
    }
}
