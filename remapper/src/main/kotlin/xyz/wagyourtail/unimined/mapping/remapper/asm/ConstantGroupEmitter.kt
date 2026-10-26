package xyz.wagyourtail.unimined.mapping.remapper.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.AnalyzerException
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.mapping.ConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AnnotationElement
import xyz.wagyourtail.unimined.mapping.element.metadata.ConstantAttributeElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.AdditiveExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.BitAndExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.BitOrExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.BitShiftExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.BitXorExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.CastExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.ConstantExpressionEvaluator
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.DataType
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.FieldExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.MultiplicativeExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.PrimaryExpression
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.UnaryExpression
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName

/**
 * Phase B — the **bytecode** half of constant uninlining (`ConstantGroupMapping`).
 * Mirrors [xyz.wagyourtail.unimined.mapping.remapper.source.psi.UnpickSourceExtension]:
 * a literal-load instruction (`ICONST_*`, `BIPUSH`/`SIPUSH`, `LCONST_*`,
 * `FCONST_*`, `DCONST_*`, `LDC`) whose value matches a group's
 * `ExpressionElement.value` is replaced with instructions evaluating the
 * group's `expression` — field refs become `GETSTATIC` (mapped
 * `baseNs → targetNs` through the tree), joined by the expression's
 * bit/arithmetic operators.
 *
 * Scoping follows [TargetElement]: no targets or a null target applies
 * globally; a class target applies to literals inside that class; a method
 * target applies to literals that feed an argument of a matching `INVOKE*`
 * (optionally just argument [TargetElement.paramIdx]) — arg producers are
 * resolved with asm-analysis `SourceInterpreter` frames, so only literals that
 * are *the* producing instruction of an argument slot are rewritten.
 *
 * Field targets are declared but unsupported at bytecode level — logged and
 * skipped. Expressions the emitter can't lower (string concat, non-static or
 * unresolvable field refs, unsupported casts) are logged and left as-is.
 *
 * Emitted sequences are contiguous and stack-neutral — they never cross a
 * frame boundary, so existing `StackMapTable` frames stay valid.
 */
object ConstantGroupEmitter {

    private val LOGGER = KotlinLogging.logger {}

    /** one group constant: the literal value it produces + how to emit it */
    private class ExprEntry(
        /** evaluated constant value, or null when unresolvable at bytecode level */
        val value: Any?,
        /** stack sort of the value */
        val sort: Int,
        val expr: Expression,
    )

    /** pre-resolved group: expressions + targets mapped into targetNs */
    private class GroupCtx(
        val group: ConstantGroupMapping,
        /** constant entries — evaluated value → expression, first match wins */
        val exprs: MutableList<ExprEntry>,
        /** `this.name` field-name → owner */
        val fieldOwnerByName: Map<String, InternalName>,
        /** field-name → descriptor for desc-less expression refs */
        val fieldDescByName: Map<String, String>,
        val classTargets: Set<String>,
        val methodTargets: List<MappedTarget>,
        /** target `putfield`/`putstatic` writes */
        val fieldTargets: List<MappedTarget>,
        /** target methods whose `return` value literals are in the group */
        val returnTargets: List<MappedTarget>,
        val global: Boolean,
        val targetNs: Namespace,
        /** group came from unpick (`Lunimined/unpick/` annotations) */
        val unpick: Boolean,
        /** literal sorts the group may apply to (`null` = unrestricted); per
         *  unpick dataType + `@strict` widening rules */
        val allowedSorts: Set<Int>?,
    )

    private class MappedTarget(
        val owner: String,
        val name: String,
        val desc: String?,
        val paramIdx: Int?,
    )

    /** emitted instruction sequence + the sort of value it leaves on the stack */
    private class Emit(val insns: InsnList, val sort: Int)

    /** [fieldValues]: `"owner.name"` → (ConstantValue, descriptor) of static
     *  fields in the input jar — used to evaluate unpick group constants */
    fun apply(
        node: ClassNode,
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
        options: Options = Options(),
        fieldValues: Map<String, Pair<Any, String>> = emptyMap(),
    ) {
        if (!options.unpickConstants) return
        val ctxs = tree.constantGroups
            .filter { it.baseNs == fns }
            .mapNotNull { groupCtx(it, tree, targetNs, fieldValues) }
        if (ctxs.isEmpty()) return
        for (method in node.methods) {
            currentMethod = method
            applyMethod(node, method, ctxs, tree)
        }
        currentMethod = null
    }

    /** method currently being transformed — for `:instance` field emission */
    private var currentMethod: MethodNode? = null

    /**
     * unpick directives on a group → `key` → raw value (marker attrs → null).
     * Real elements win; pre-2.1 trees carrying the attrs as synthetic
     * `Lunimined/unpick/...;` annotations still contribute.
     */
    private fun unpickAttrs(group: ConstantGroupMapping): Map<String, String?> {
        val attrs = mutableMapOf<String, String?>()
        // first wins; `value` may legitimately be null so getOrPut/putIfAbsent
        // (which treat null as absent) can't be used
        fun setIfAbsent(key: String, value: String?) {
            if (key !in attrs) attrs[key] = value
        }
        for (meta in group.metadata) {
            when (meta) {
                is ConstantAttributeElement -> setIfAbsent(meta.key, meta.value)
                is AnnotationElement -> {
                    val (obj, elements, _) = runCatching { meta.annotation.getParts() }.getOrNull() ?: continue
                    val name = obj.value.removeSurrounding("L", ";")
                        .takeIf { it.startsWith("unimined/unpick/") }
                        ?.removePrefix("unimined/unpick/") ?: continue
                    fun elem(k: String): String? = runCatching {
                        elements?.getParts()?.firstOrNull { it.getParts().first.toString() == k }
                            ?.getParts()?.second?.takeIf { it.isConstant() }
                            ?.getConstant()?.getValue()?.toString()
                    }.getOrNull()
                    when (name) {
                        "DataType" -> setIfAbsent("data-type", elem("value"))
                        "Strict" -> setIfAbsent("strict", null)
                        "Format" -> setIfAbsent("format", elem("value"))
                        "TargetAnnotation" -> setIfAbsent("target-annotation", elem("value"))
                        "Scope" -> setIfAbsent("scope",
                            listOfNotNull(elem("type"), elem("target"), elem("name"), elem("desc"))
                                .joinToString(" ").ifEmpty { elem("value") })
                        else -> setIfAbsent(name.lowercase(), elem("value"))
                    }
                }
                else -> {}
            }
        }
        return attrs
    }

    private fun groupCtx(
        group: ConstantGroupMapping,
        tree: MappingTree,
        targetNs: Namespace,
        fieldValues: Map<String, Pair<Any, String>>,
    ): GroupCtx? {
        val attrs = unpickAttrs(group)
        // unpick provenance: declared directives (v3+) or declared constants (v2
        // unpick files only carry `n`/`t` rows — the field-value lookup is what
        // makes them work at all)
        val unpick = attrs.isNotEmpty() || group.constants.iterator().hasNext()
        val dataType = attrs["data-type"]
        val strict = "strict" in attrs
        val allowedSorts = dataType?.let { dt ->
            val base = when (dt) {
                "byte", "short", "char", "int" -> Type.INT
                "long" -> Type.LONG
                "float" -> Type.FLOAT
                "double" -> Type.DOUBLE
                else -> Type.OBJECT
            }
            if (strict) setOf(base) else when (dt) {
                "long" -> setOf(Type.INT, Type.LONG)
                "float" -> setOf(Type.INT, Type.LONG, Type.FLOAT)
                "double" -> setOf(Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE)
                else -> setOf(base)
            }
        }

        val ownerByName = mutableMapOf<String, InternalName>()
        val descByName = mutableMapOf<String, String>()
        for (c in group.constants) {
            val (name, desc) = c.field.getParts()
            ownerByName.putIfAbsent(name.value, c.owner)
            desc?.let { descByName.putIfAbsent(name.value, it.toString()) }
        }

        val exprs = mutableListOf<ExprEntry>()
        val tmp = GroupCtx(group, exprs, ownerByName, descByName, emptySet(), emptyList(), emptyList(), emptyList(), true, targetNs, unpick, allowedSorts)
        val fold = { e: Expression ->
            ConstantExpressionEvaluator.eval(e,
                resolveOwner = { tmp.fieldOwnerByName[it.value] },
                resolveField = { o, n -> fieldValues["${o.value}.${n.value}"] })
        }
        for (e in group.expressions) {
            if (e.value.isString() || e.value.isBoolean() || e.value.isNumber()) {
                val v = e.value.getValue()
                val folded = fold(e.expression)
                if (folded != null && !ConstantExpressionEvaluator.valuesEqual(v, folded.value)) {
                    LOGGER.warn { "unpick: '${e.expression}' evaluates to ${folded.value}, not declared '$v' — skipping entry in group '${group.name}'" }
                    continue
                }
                exprs += ExprEntry(v, if (v is String) Type.OBJECT else sortOf(v), e.expression)
            } else {
                // unpick-style const — the expression evaluates to the constant
                fold(e.expression)?.let { (v, s) ->
                    exprs += ExprEntry(v, asmSort(s), e.expression)
                }
            }
        }
        if (unpick) {
            for (c in group.constants) {
                val (name, desc) = c.field.getParts()
                val owner = c.owner.value
                if (name.value == "*") {
                    // wildcard: expand to every static constant field of the class
                    fieldValues.filterKeys { it.startsWith("$owner.") }
                        .forEach { (k, _) ->
                            val fname = k.substringAfter('.')
                            val expr = Expression.unchecked("L$owner;$fname;")
                            fold(expr)?.let { (v, s) ->
                                exprs += ExprEntry(v, asmSort(s), expr)
                            }
                        }
                } else {
                    val expr = Expression.unchecked("L$owner;${name.value};" + (desc?.toString() ?: ""))
                    fold(expr)?.let { (v, s) ->
                        if (exprs.none { it.value == v }) exprs += ExprEntry(v, asmSort(s), expr)
                    }
                }
            }
        }
        if (exprs.isEmpty()) return null

        var global = false
        val classTargets = mutableSetOf<String>()
        val methodTargets = mutableListOf<MappedTarget>()
        val fieldTargets = mutableListOf<MappedTarget>()
        val returnTargets = mutableListOf<MappedTarget>()
        val targets = group.targets.toList()
        if (targets.isEmpty()) global = true
        for (t in targets) {
            val fqn = t.target ?: run { global = true; continue }
            val (owner, nad) = tree.map(group.baseNs, targetNs, fqn).getParts()
            if (nad == null) {
                classTargets += owner.getInternalName().value
            } else {
                val (rawName, desc) = nad.getParts()
                // unpick-format `name(desc)` — '(' is legal in unqualified
                // names so it stays glued to the name through parsing
                var name = rawName.value
                var descStr = desc?.toString()
                if ('(' in name) {
                    descStr = name.substring(name.indexOf('('))
                    name = name.substringBefore('(')
                }
                when {
                    descStr == null && desc is FieldDescriptor -> fieldTargets += MappedTarget(
                        owner.getInternalName().value, name, desc.toString(), t.paramIdx)
                    descStr != null && !descStr.startsWith("(") -> fieldTargets += MappedTarget(
                        owner.getInternalName().value, name, descStr, t.paramIdx)
                    t.paramIdx == -1 -> returnTargets += MappedTarget(
                        owner.getInternalName().value, name, descStr, null)
                    else -> methodTargets += MappedTarget(
                        owner.getInternalName().value, name, descStr, t.paramIdx)
                }
            }
        }
        return GroupCtx(group, exprs, ownerByName, descByName, classTargets, methodTargets, fieldTargets, returnTargets, global, targetNs, unpick, allowedSorts)
    }

    private fun applyMethod(node: ClassNode, method: MethodNode, ctxs: List<GroupCtx>, tree: MappingTree) {
        val insns = method.instructions.toArray()
        val replacements = LinkedHashMap<AbstractInsnNode, InsnList>()

        // global + class-scoped groups
        for (insn in insns) {
            val (v, sort) = constLoad(insn) ?: continue
            for (g in ctxs) {
                if (!(g.global || node.name in g.classTargets)) continue
                val emitted = emitFor(g, v, sort, tree) ?: continue
                replacements[insn] = emitted
                break
            }
        }

        // targeted groups — literal must produce a value consumed at a matching
        // point (method arg, field write, method return)
        if (ctxs.any { it.methodTargets.isNotEmpty() || it.fieldTargets.isNotEmpty() || it.returnTargets.isNotEmpty() }) {
            var frames: Array<org.objectweb.asm.tree.analysis.Frame<SourceValue>>? = null
            var analyzed = false
            fun analyzeOnce() {
                if (!analyzed) {
                    frames = analyze(node, method)
                    analyzed = true
                }
            }
            fun replaceProducer(slotOf: (org.objectweb.asm.tree.analysis.Frame<SourceValue>) -> Int, idx: Int, g: GroupCtx) {
                analyzeOnce()
                val fr = frames?.getOrNull(idx) ?: return
                for (prod in fr.getStack(slotOf(fr)).insns) {
                    if (replacements.containsKey(prod)) continue
                    val (v, sort) = constLoad(prod) ?: continue
                    val emitted = emitFor(g, v, sort, tree) ?: continue
                    replacements[prod] = emitted
                    break
                }
            }
            // literals feeding `return` inside a targeted method
            for (g in ctxs) {
                for (t in g.returnTargets) {
                    if (node.name != t.owner || method.name != t.name) continue
                    if (t.desc != null && method.desc != t.desc) continue
                    for (insn in insns) {
                        if (insn.opcode in Opcodes.IRETURN..Opcodes.ARETURN) {
                            replaceProducer({ it.stackSize - 1 }, method.instructions.indexOf(insn), g)
                        }
                    }
                }
            }
            for (insn in insns) {
                val idx = method.instructions.indexOf(insn)
                when (insn) {
                    is MethodInsnNode -> {
                        for (g in ctxs) {
                            for (t in g.methodTargets) {
                                if (insn.owner != t.owner || insn.name != t.name) continue
                                if (t.desc != null && insn.desc != t.desc) continue
                                analyzeOnce()
                                val frame = frames?.getOrNull(idx) ?: break
                                val argTypes = Type.getArgumentTypes(insn.desc)
                                val argSlots = argTypes.sumOf { it.size }
                                var off = 0
                                for (p in argTypes.indices) {
                                    val at = argTypes[p]
                                    if (t.paramIdx == null || t.paramIdx == p) {
                                        replaceProducer({ it.stackSize - argSlots + off + at.size - 1 }, idx, g)
                                    }
                                    off += at.size
                                }
                            }
                        }
                    }
                    is FieldInsnNode -> {
                        if (insn.opcode != Opcodes.PUTSTATIC && insn.opcode != Opcodes.PUTFIELD) continue
                        for (g in ctxs) {
                            for (t in g.fieldTargets) {
                                if (insn.owner != t.owner || insn.name != t.name) continue
                                if (t.desc != null && insn.desc != t.desc) continue
                                replaceProducer({ it.stackSize - 1 }, idx, g)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        for ((insn, list) in replacements) {
            method.instructions.insertBefore(insn, list)
            method.instructions.remove(insn)
        }
    }

    private fun analyze(node: ClassNode, method: MethodNode): Array<org.objectweb.asm.tree.analysis.Frame<SourceValue>>? =
        try {
            Analyzer(SourceInterpreter()).analyze(node.name, method)
        } catch (e: AnalyzerException) {
            LOGGER.warn { "unpick: analysis failed in ${node.name}.${method.name}${method.desc}: ${e.message} — method targets skipped" }
            null
        }

    // --- literal loads ---

    /** the constant produced by [insn] + its stack sort, or null when it isn't a literal load */
    private fun constLoad(insn: AbstractInsnNode): Pair<Any, Int>? = when (insn.opcode) {
        Opcodes.ICONST_M1 -> -1 to Type.INT
        in Opcodes.ICONST_0..Opcodes.ICONST_5 -> (insn.opcode - Opcodes.ICONST_0) to Type.INT
        Opcodes.LCONST_0, Opcodes.LCONST_1 -> (insn.opcode - Opcodes.LCONST_0).toLong() to Type.LONG
        Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 -> (insn.opcode - Opcodes.FCONST_0).toFloat() to Type.FLOAT
        Opcodes.DCONST_0, Opcodes.DCONST_1 -> (insn.opcode - Opcodes.DCONST_0).toDouble() to Type.DOUBLE
        Opcodes.BIPUSH, Opcodes.SIPUSH -> (insn as IntInsnNode).operand to Type.INT
        Opcodes.LDC -> when (val c = (insn as LdcInsnNode).cst) {
            is Int -> c to Type.INT
            is Long -> c to Type.LONG
            is Float -> c to Type.FLOAT
            is Double -> c to Type.DOUBLE
            is String -> c to Type.OBJECT
            else -> null
        }
        else -> null
    }

    private fun matches(e: ExprEntry, v: Any, sort: Int, g: GroupCtx): Boolean {
        if (e.value == null) return false
        if (g.allowedSorts != null && sort !in g.allowedSorts) return false
        val cv = e.value
        return when (cv) {
            is String -> sort == Type.OBJECT && v == cv
            is Boolean -> sort == Type.INT && v is Int && v == if (cv) 1 else 0
            is Number -> v is Number && numEq(cv, v)
            else -> false
        }
    }

    private fun numEq(a: Number, b: Number): Boolean =
        if (a.isIntegral() && b.isIntegral()) a.toLong() == b.toLong()
        else a.toDouble() == b.toDouble()

    /** literal sort of a JVM constant value */
    private fun sortOf(v: Any?): Int = when (v) {
        is Int -> Type.INT; is Long -> Type.LONG
        is Float -> Type.FLOAT; is Double -> Type.DOUBLE
        is Boolean -> Type.BOOLEAN; is String -> Type.OBJECT
        else -> Type.OBJECT
    }

    /**
     * pick and emit a replacement for a matching literal: exact value match
     * first, else (bitfield groups) the minimal set of constants ORed together
     * plus a residual literal — per unpick `@flags` semantics.
     */
    private fun emitFor(g: GroupCtx, v: Any, sort: Int, tree: MappingTree): InsnList? {
        g.exprs.firstOrNull { matches(it, v, sort, g) }?.let {
            return emitExpression(it.expr, sort, g, tree)
        }
        if (g.group.type != InlineType.BITFIELD || v !is Number || !v.isIntegral()) return null
        // greedy minimal OR-cover of the literal's bits + residual
        var remaining = v.toLong()
        val chosen = mutableListOf<ExprEntry>()
        while (remaining != 0L) {
            val best = g.exprs.filter { it.value is Number && it.value.isIntegral() }
                .maxByOrNull { (it.value as Number).toLong().and(remaining).countOneBits() }
                ?: break
            val cover = (best.value as Number).toLong() and remaining
            if (cover == 0L) break
            chosen += best
            remaining = remaining and cover.inv()
        }
        if (chosen.isEmpty()) return null
        val insns = InsnList()
        var acc = emitExpression(chosen.first().expr, sort, g, tree) ?: return null
        insns.add(acc)
        for (e in chosen.drop(1)) {
            insns.add(emitExpression(e.expr, sort, g, tree) ?: return null)
            insns.add(InsnNode(if (sort == Type.LONG) Opcodes.LOR else Opcodes.IOR))
        }
        if (remaining != 0L) {
            insns.add(if (sort == Type.LONG) LdcInsnNode(remaining) else {
                val i = remaining.toInt()
                if (i in -1..5) InsnNode(Opcodes.ICONST_0 + i) else LdcInsnNode(i)
            })
            insns.add(InsnNode(if (sort == Type.LONG) Opcodes.LOR else Opcodes.IOR))
        }
        return insns
    }

    private fun Number.isIntegral() = this is Int || this is Long

    private fun asmSort(s: ConstantExpressionEvaluator.Sort): Int = when (s) {
        ConstantExpressionEvaluator.Sort.INT -> Type.INT
        ConstantExpressionEvaluator.Sort.LONG -> Type.LONG
        ConstantExpressionEvaluator.Sort.FLOAT -> Type.FLOAT
        ConstantExpressionEvaluator.Sort.DOUBLE -> Type.DOUBLE
        ConstantExpressionEvaluator.Sort.OBJECT -> Type.OBJECT
    }

    // --- expression → instructions ---

    private fun emitExpression(expr: Expression, want: Int, g: GroupCtx, tree: MappingTree): InsnList? {
        val e = eval(expr, g, tree) ?: return null
        if (stackSort(e.sort) == stackSort(want)) return e.insns
        val ops = convertOpcodes(stackSort(e.sort), stackSort(want)) ?: return null
        for (op in ops) e.insns.add(InsnNode(op))
        return e.insns
    }

    private fun eval(e: Expression, g: GroupCtx, tree: MappingTree): Emit? = evalBitOr(e.value, g, tree)

    private fun evalBitOr(e: BitOrExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val parts = e.getParts()
        val ops = parts.first + parts.second
        var acc = evalBitXor(ops.first(), g, tree) ?: return null
        for (o in ops.drop(1)) {
            val r = evalBitXor(o, g, tree) ?: return null
            acc = binary(acc, r, Type.INT to Opcodes.IOR, Type.LONG to Opcodes.LOR) ?: return null
        }
        return acc
    }

    private fun evalBitXor(e: BitXorExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val parts = e.getParts()
        val ops = parts.first + parts.second
        var acc = evalBitAnd(ops.first(), g, tree) ?: return null
        for (o in ops.drop(1)) {
            val r = evalBitAnd(o, g, tree) ?: return null
            acc = binary(acc, r, Type.INT to Opcodes.IXOR, Type.LONG to Opcodes.LXOR) ?: return null
        }
        return acc
    }

    private fun evalBitAnd(e: BitAndExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val parts = e.getParts()
        val ops = parts.first + parts.second
        var acc = evalBitShift(ops.first(), g, tree) ?: return null
        for (o in ops.drop(1)) {
            val r = evalBitShift(o, g, tree) ?: return null
            acc = binary(acc, r, Type.INT to Opcodes.IAND, Type.LONG to Opcodes.LAND) ?: return null
        }
        return acc
    }

    private fun evalBitShift(e: BitShiftExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val parts = e.getParts()
        val operands = parts.first.map { it.first } + parts.second
        val ops = parts.first.map { it.second }
        var acc = evalAdditive(operands.first(), g, tree) ?: return null
        for ((i, op) in ops.withIndex()) {
            if (acc.sort != Type.INT && acc.sort != Type.LONG) {
                LOGGER.warn { "unpick: shift on sort ${acc.sort} unsupported" }
                return null
            }
            val r = evalAdditive(operands[i + 1], g, tree) ?: return null
            val insns = InsnList()
            insns.add(acc.insns)
            insns.add(r.insns)
            if (r.sort == Type.LONG) insns.add(InsnNode(Opcodes.L2I))
            insns.add(InsnNode(when (op) {
                "<<" -> if (acc.sort == Type.LONG) Opcodes.LSHL else Opcodes.ISHL
                ">>" -> if (acc.sort == Type.LONG) Opcodes.LSHR else Opcodes.ISHR
                ">>>" -> if (acc.sort == Type.LONG) Opcodes.LUSHR else Opcodes.IUSHR
                else -> return null
            }))
            acc = Emit(insns, acc.sort)
        }
        return acc
    }

    private fun evalAdditive(e: AdditiveExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val parts = e.getParts()
        val operands = parts.first.map { it.first } + parts.second
        val ops = parts.first.map { it.second }
        var acc = evalMultiplicative(operands.first(), g, tree) ?: return null
        for ((i, op) in ops.withIndex()) {
            val r = evalMultiplicative(operands[i + 1], g, tree) ?: return null
            acc = when (op) {
                "+" -> binary(acc, r,
                    Type.INT to Opcodes.IADD, Type.LONG to Opcodes.LADD,
                    Type.FLOAT to Opcodes.FADD, Type.DOUBLE to Opcodes.DADD)
                "-" -> binary(acc, r,
                    Type.INT to Opcodes.ISUB, Type.LONG to Opcodes.LSUB,
                    Type.FLOAT to Opcodes.FSUB, Type.DOUBLE to Opcodes.DSUB)
                else -> return null
            } ?: return null
        }
        return acc
    }

    private fun evalMultiplicative(e: MultiplicativeExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val parts = e.getParts()
        val operands = parts.first.map { it.first } + parts.second
        val ops = parts.first.map { it.second }
        var acc = evalUnary(operands.first(), g, tree) ?: return null
        for ((i, op) in ops.withIndex()) {
            val r = evalUnary(operands[i + 1], g, tree) ?: return null
            acc = when (op) {
                "*" -> binary(acc, r,
                    Type.INT to Opcodes.IMUL, Type.LONG to Opcodes.LMUL,
                    Type.FLOAT to Opcodes.FMUL, Type.DOUBLE to Opcodes.DMUL)
                "/" -> binary(acc, r,
                    Type.INT to Opcodes.IDIV, Type.LONG to Opcodes.LDIV,
                    Type.FLOAT to Opcodes.FDIV, Type.DOUBLE to Opcodes.DDIV)
                "%" -> binary(acc, r,
                    Type.INT to Opcodes.IREM, Type.LONG to Opcodes.LREM,
                    Type.FLOAT to Opcodes.FREM, Type.DOUBLE to Opcodes.DREM)
                else -> return null
            } ?: return null
        }
        return acc
    }

    private fun evalUnary(u: UnaryExpression, g: GroupCtx, tree: MappingTree): Emit? {
        var v = u.value
        var op = ' '
        if (v.firstOrNull() in setOf('-', '~', '!')) {
            op = v.first()
            v = v.substring(1)
        }
        val inner = (if (v.startsWith("<")) {
            evalCast(CastExpression.unchecked(v), g, tree)
        } else {
            evalPrimary(PrimaryExpression.unchecked(v), g, tree)
        }) ?: return null
        return when (op) {
            ' ' -> inner
            '-' -> {
                val neg = when (inner.sort) {
                    Type.INT -> Opcodes.INEG
                    Type.LONG -> Opcodes.LNEG
                    Type.FLOAT -> Opcodes.FNEG
                    Type.DOUBLE -> Opcodes.DNEG
                    else -> return null
                }
                inner.insns.add(InsnNode(neg)); inner
            }
            '~' -> {
                val (push, xor) = when (inner.sort) {
                    Type.INT -> InsnNode(Opcodes.ICONST_M1) to Opcodes.IXOR
                    Type.LONG -> LdcInsnNode(-1L) to Opcodes.LXOR
                    else -> return null
                }
                inner.insns.add(push); inner.insns.add(InsnNode(xor)); inner
            }
            '!' -> {
                if (inner.sort != Type.INT && inner.sort != Type.BOOLEAN) return null
                inner.insns.add(InsnNode(Opcodes.ICONST_1)); inner.insns.add(InsnNode(Opcodes.IXOR)); inner
            }
            else -> null
        }
    }

    private fun evalCast(c: CastExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val dt = DataType.unchecked(c.value.substringBefore(">").drop(1)).value
        val inner = evalUnary(UnaryExpression.unchecked(c.value.substringAfter(">").trimStart()), g, tree) ?: return null
        if (dt == "String") {
            inner.insns.add(TypeInsnNode(Opcodes.CHECKCAST, "java/lang/String"))
            return Emit(inner.insns, Type.OBJECT)
        }
        val to = when (dt) {
            "byte" -> Type.BYTE
            "short" -> Type.SHORT
            "char" -> Type.CHAR
            "int" -> Type.INT
            "long" -> Type.LONG
            "float" -> Type.FLOAT
            "double" -> Type.DOUBLE
            "boolean" -> Type.BOOLEAN
            else -> return null
        }
        val ops = convertOpcodes(stackSort(inner.sort), stackSort(to)) ?: return null
        for (op in ops) inner.insns.add(InsnNode(op))
        return Emit(inner.insns, to)
    }

    private fun evalPrimary(p: PrimaryExpression, g: GroupCtx, tree: MappingTree): Emit? = when {
        p.isParen() -> eval(Expression.unchecked(p.value.substring(1, p.value.length - 1)), g, tree)
        p.isField() -> evalField(p.getField()!!, g, tree)
        else -> emitConstant(p.getConstant()!!)
    }

    private fun evalField(f: FieldExpression, g: GroupCtx, tree: MappingTree): Emit? {
        val (obj, isThis, nameDesc) = f.getParts()
        val (name, desc) = nameDesc
        val owner = obj?.getInternalName() ?: g.fieldOwnerByName[name.value]
        if (owner == null) {
            LOGGER.warn { "unpick: can't resolve owner of '${if (isThis) "this." else ""}${name.value}' in group '${g.group.name}'" }
            return null
        }
        if (isThis && f.value.endsWith(":instance")) {
            // `:instance` — GETFIELD off `this`; only valid in instance methods
            val m = currentMethod
            if (m == null || m.access and Opcodes.ACC_STATIC != 0) {
                LOGGER.warn { "unpick: instance field ref '${name.value}' in static context" }
                return null
            }
        }
        val targetNs = g.targetNs
        val descStr = desc?.toString() ?: g.fieldDescByName[name.value] ?: tree
            .getClass(g.group.baseNs, owner)?.fields
            ?.firstOrNull { it.entries[g.group.baseNs]?.name == name }
            ?.entries?.get(targetNs)?.descriptor?.toString()
        val fqn = FullyQualifiedName.unchecked(
            "L${owner.value};${name.value}" + (descStr?.let { ";$it" } ?: ""))
        val (mo, mnad) = tree.map(g.group.baseNs, targetNs, fqn).getParts()
        val (mName, mDesc) = mnad?.getParts() ?: return null
        val d = mDesc?.toString() ?: descStr
        if (d == null) {
            LOGGER.warn { "unpick: no descriptor for field ref '${fqn}'" }
            return null
        }
        val instance = f.value.endsWith(":instance")
        return Emit(InsnList().apply {
            if (instance) {
                add(VarInsnNode(Opcodes.ALOAD, 0))
                add(FieldInsnNode(Opcodes.GETFIELD, mo.getInternalName().value, mName.value, d))
            } else {
                add(FieldInsnNode(Opcodes.GETSTATIC, mo.getInternalName().value, mName.value, d))
            }
        }, Type.getType(d).sort)
    }

    private fun emitConstant(c: Constant): Emit? {
        val v = c.getValue() ?: return null
        val list = InsnList()
        val sort = when (v) {
            is Boolean -> { list.add(InsnNode(if (v) Opcodes.ICONST_1 else Opcodes.ICONST_0)); Type.BOOLEAN }
            is Number -> {
                list.add(LdcInsnNode(v))
                when (v) {
                    is Int -> Type.INT; is Long -> Type.LONG
                    is Float -> Type.FLOAT; is Double -> Type.DOUBLE
                    else -> return null
                }
            }
            is String -> { list.add(LdcInsnNode(v)); Type.OBJECT }
            else -> return null
        }
        return Emit(list, sort)
    }

    // --- helpers ---

    private fun binary(a: Emit, b: Emit, vararg ops: Pair<Int, Int>): Emit? {
        val sort = promoteSort(a.sort, b.sort) ?: return null
        val op = ops.firstOrNull { it.first == sort }?.second ?: return null
        val insns = InsnList()
        insns.add(a.insns)
        convertOpcodes(stackSort(a.sort), stackSort(sort))?.forEach { insns.add(InsnNode(it)) } ?: return null
        insns.add(b.insns)
        convertOpcodes(stackSort(b.sort), stackSort(sort))?.forEach { insns.add(InsnNode(it)) } ?: return null
        insns.add(InsnNode(op))
        return Emit(insns, sort)
    }

    private fun promoteSort(a: Int, b: Int): Int? = when {
        a == b -> a
        a == Type.DOUBLE || b == Type.DOUBLE -> Type.DOUBLE
        a == Type.FLOAT || b == Type.FLOAT -> Type.FLOAT
        a == Type.LONG || b == Type.LONG -> Type.LONG
        a == Type.INT || b == Type.INT -> Type.INT
        else -> null
    }

    /** the JVM stack sort — narrow primitives ride as int */
    private fun stackSort(sort: Int) = when (sort) {
        Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT -> Type.INT
        else -> sort
    }

    private fun convertOpcodes(from: Int, to: Int): List<Int>? = when {
        from == to -> emptyList()
        to == Type.LONG -> when (from) {
            Type.INT -> listOf(Opcodes.I2L); Type.FLOAT -> listOf(Opcodes.F2L); Type.DOUBLE -> listOf(Opcodes.D2L)
            else -> null
        }
        to == Type.INT -> when (from) {
            Type.LONG -> listOf(Opcodes.L2I); Type.FLOAT -> listOf(Opcodes.F2I); Type.DOUBLE -> listOf(Opcodes.D2I)
            else -> null
        }
        to == Type.BYTE -> (convertOpcodes(from, Type.INT) ?: return null) + Opcodes.I2B
        to == Type.CHAR -> (convertOpcodes(from, Type.INT) ?: return null) + Opcodes.I2C
        to == Type.SHORT -> (convertOpcodes(from, Type.INT) ?: return null) + Opcodes.I2S
        to == Type.FLOAT -> when (from) {
            Type.INT -> listOf(Opcodes.I2F); Type.LONG -> listOf(Opcodes.L2F); Type.DOUBLE -> listOf(Opcodes.D2F)
            else -> null
        }
        to == Type.DOUBLE -> when (from) {
            Type.INT -> listOf(Opcodes.I2D); Type.LONG -> listOf(Opcodes.L2D); Type.FLOAT -> listOf(Opcodes.F2D)
            else -> null
        }
        else -> null
    }

}
