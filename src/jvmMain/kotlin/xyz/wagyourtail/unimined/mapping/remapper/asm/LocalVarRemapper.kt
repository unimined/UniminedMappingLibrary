package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.LocalVariableNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.InvokableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping

/**
 * Parameter and local-variable name remapping on the post-remap [MethodNode].
 *
 * ASM gives no help here — `Remapper` has no LV hook and
 * `MethodRemapper.visitLocalVariable` passes names through unchanged, so names
 * are rewritten on the node model directly (tiny-remapper's `processLocals`):
 *
 * - `MethodParameters` and LVT arg entries are merged into one `args[]` model —
 *   [ParameterMapping] names (by `index`, or `lvOrd` converted to arg index)
 *   override both
 * - non-arg LVT entries map via [LocalVariableMapping] `(lvOrd, startOp)` — the
 *   count of real-opcode insns before `lv.start`
 * - `renameInvalidLocals` replaces non-identifier names (obfuscated jars) with
 *   a name derived from the var's type, deduped per method
 * - `inferNameFromSameLvIndex` reuses a sibling LV entry's name at the same
 *   slot+desc before generating
 * - named args missing LVT coverage get a synthesized spanning entry so
 *   `MethodParameters` and LVT agree
 */
object LocalVarRemapper {

    fun apply(
        method: MethodNode,
        mapping: MethodMapping?,
        targetNs: Namespace,
        options: Options,
        wildcards: List<InvokableMapping> = emptyList(),
    ) {
        val isStatic = method.access and Opcodes.ACC_STATIC != 0
        val argTypes = Type.getArgumentTypes(method.desc)
        val argLvSize = lvIndex(argTypes.size, isStatic, argTypes)
        val args = arrayOfNulls<String>(argTypes.size)
        val nameCounts = HashMap<String, Int>()
        // exact mapping outranks desc-grouped wildcards
        val invokables = wildcards + listOfNotNull(mapping)

        // seed arg names from MethodParameters
        method.parameters?.let { params ->
            if (params.size == argTypes.size) {
                for ((i, p) in params.withIndex()) args[i] = p.name
            }
        }

        // overlay mapped param names — wildcards first so an exact row wins
        if (!options.skipLocalVariableMapping) {
            for (inv in invokables) {
                for (p in inv.parameters) {
                    val i = p.index ?: p.lvOrd?.let { asmIndex(it, isStatic, argTypes) } ?: continue
                    if (i in args.indices) p.names[targetNs]?.let { args[i] = it.value }
                }
            }
        }

        // arg-name LV entries feed args[] when they're better than what we have;
        // var entries get remapped in place
        method.localVariables?.forEach { lv ->
            when {
                !isStatic && lv.index == 0 -> lv.name = "this"
                lv.index < argLvSize -> {
                    val i = asmIndex(lv.index, isStatic, argTypes)
                    if (i >= 0 && (args[i] == null || !isValid(args[i]!!, options) && isValid(lv.name, options))) {
                        args[i] = lv.name
                    }
                }
                else -> if (!options.skipLocalVariableMapping) {
                    val lvMapping = listOfNotNull(mapping).plus(wildcards).firstNotNullOfOrNull { inv ->
                        inv.localVariables.firstOrNull {
                            it.lvOrd == lv.index && (it.startOp == null || it.startOp == startOpIndex(method, lv))
                        }
                    }
                    lvMapping?.names?.get(targetNs)?.let { lv.name = it.value }
                }
            }
        }

        // block generated names from colliding with valid existing names
        if (options.renameInvalidLocals) {
            for (a in args) if (a != null && isValid(a, options)) nameCounts[a] = 1
            method.localVariables?.forEach {
                if (isValid(it.name, options)) nameCounts[it.name] = 1
            }
            for (i in args.indices) {
                if (args[i] == null || !isValid(args[i]!!, options)) {
                    args[i] = nameFromType(argTypes[i], nameCounts)
                }
            }
        }

        // write back
        method.parameters?.let { params ->
            if (params.size == argTypes.size) {
                for ((i, p) in params.withIndex()) args[i]?.let { p.name = it }
            }
        }
        val argsWritten = BooleanArray(argTypes.size)
        method.localVariables?.forEach { lv ->
            when {
                !isStatic && lv.index == 0 -> Unit
                lv.index < argLvSize -> {
                    val i = asmIndex(lv.index, isStatic, argTypes)
                    if (i >= 0) {
                        args[i]?.let { lv.name = it }
                        argsWritten[i] = true
                    }
                }
                else -> if (options.renameInvalidLocals && !isValid(lv.name, options)) {
                    lv.name = inferFromSibling(method, lv, options) ?: nameFromType(Type.getType(lv.desc), nameCounts)
                }
            }
        }

        // synthesize LV entries for named args with no coverage — creates the
        // table when absent (abstract/native methods have no code to attach to)
        if (method.localVariables == null && args.any { it != null } && method.access and Opcodes.ACC_ABSTRACT == 0) {
            method.localVariables = mutableListOf()
        }
        if (method.localVariables != null && method.instructions != null) {
            var lvSlot = if (isStatic) 0 else 1
            for ((i, t) in argTypes.withIndex()) {
                val slot = lvSlot
                lvSlot += t.size
                if (!argsWritten[i] && args[i] != null) {
                    val (start, end) = spanLabels(method) ?: break
                    method.localVariables.add(LocalVariableNode(args[i], t.descriptor, null, start, end, slot))
                }
            }
        }
    }

    /** count of real-opcode insns before `lv.start` — matches `LocalVariableMapping.startOp` */
    private fun startOpIndex(method: MethodNode, lv: LocalVariableNode): Int {
        var count = 0
        var insn = lv.start.previous
        while (insn != null) {
            if (insn.opcode >= 0) count++
            insn = insn.previous
        }
        return count
    }

    private fun inferFromSibling(method: MethodNode, lv: LocalVariableNode, options: Options): String? {
        if (!options.inferNameFromSameLvIndex) return null
        return method.localVariables?.firstOrNull { other ->
            other !== lv && other.index == lv.index && other.desc == lv.desc && isValid(other.name, options)
        }?.name
    }

    private fun spanLabels(method: MethodNode): Pair<LabelNode, LabelNode>? {
        var start: LabelNode? = null
        var end: LabelNode? = null
        var insn = method.instructions.first
        while (insn != null) {
            if (insn is LabelNode) {
                if (start == null) start = insn
                end = insn
            }
            insn = insn.next
        }
        return if (start != null) start to end!! else null
    }

    /** lvIndex for the arg at [asmIndex] — lv slots count `this` + wide types. */
    private fun lvIndex(asmIndex: Int, isStatic: Boolean, argTypes: Array<Type>): Int {
        var index = if (isStatic) 0 else 1
        for (i in 0 until asmIndex) index += argTypes[i].size
        return index
    }

    /** arg index for an lv slot — inverse of [lvIndex]; -1 if not an arg slot. */
    private fun asmIndex(lvIndex: Int, isStatic: Boolean, argTypes: Array<Type>): Int {
        var index = if (isStatic) 0 else 1
        for ((i, t) in argTypes.withIndex()) {
            if (lvIndex == index) return i
            index += t.size
        }
        return -1
    }

    private fun isValid(name: String, options: Options): Boolean {
        val pattern = options.invalidLvNamePattern
        if (pattern != null) return !pattern.matches(name)
        return name.isNotEmpty() && name.first().isJavaIdentifierStart() && name.all { it.isJavaIdentifierPart() }
    }

    /** `Ljava/lang/String;` → `string`, `[[I` → `ints`/`arrays`, deduped via [nameCounts]. */
    private fun nameFromType(type: Type, nameCounts: MutableMap<String, Int>): String {
        var t = type
        var dims = 0
        while (t.sort == Type.ARRAY) {
            dims++
            t = t.elementType
        }
        val base = when (t.sort) {
            Type.OBJECT -> t.internalName.substringAfterLast('/').substringAfterLast('$')
            else -> t.descriptor.lowercase()
        }.replaceFirstChar { it.lowercaseChar() }
        val root = if (dims > 0) "${base}s" else base
        var name = root
        var i = 1
        while (nameCounts.putIfAbsent(name, 1) != null) name = "$root${++i}"
        return name
    }

}
