package xyz.wagyourtail.unimined.mapping.remapper.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.remapper.asm.EnumExtender.add
import xyz.wagyourtail.unimined.mapping.remapper.asm.EnumExtender.renumber

/**
 * Applies [EnumExtensionElement]s — adds/removes enum constant fields and
 * rewrites the javac `<clinit>` pattern:
 *
 * ```
 * NEW self / DUP / LDC "NAME" / ICONST ord / <extra ctor args> / INVOKESPECIAL <init> / PUTSTATIC NAME
 * ...
 * ICONST n / ANEWARRAY self / (DUP ICONST i GETSTATIC X AASTORE)* / PUTSTATIC $VALUES
 * ```
 *
 * `values()`/`valueOf()` read through `$VALUES`, so fixing the array fixes both.
 * Ordinal arguments of subsequent constants are renumbered on removal. If the
 * `<clinit>` shape isn't recognized the element is skipped with a warning.
 */
object EnumExtender {

    private val LOGGER = KotlinLogging.logger {}

    fun apply(node: ClassNode, meta: List<MetadataElement>, tree: MappingTree, targetNs: Namespace) {
        val elements = meta.filterIsInstance<EnumExtensionElement>()
        if (elements.isEmpty()) return
        if (node.access and Opcodes.ACC_ENUM == 0) {
            LOGGER.warn { "EnumExtensionElement on non-enum class ${node.name}, skipping" }
            return
        }

        val valuesField = findValuesField(node)
        val clinit = node.methods.firstOrNull { it.name == "<clinit>" && it.desc == "()V" }
        if (valuesField == null || clinit == null) {
            LOGGER.warn { "EnumExtensionElement on ${node.name} but no \$VALUES field/<clinit> found, skipping" }
            return
        }
        val pattern = parseClinit(node, clinit, valuesField) ?: run {
            LOGGER.warn { "EnumExtensionElement on ${node.name}: unrecognized <clinit> shape, skipping" }
            return
        }

        for (el in elements) {
            val name = MetadataAdapter.resolve(el, tree, targetNs) { it.name }.value
            when (el.type) {
                AddRemove.ADD -> add(node, clinit, pattern, name)
                AddRemove.REMOVE -> remove(node, clinit, pattern, name)
            }
        }
    }

    /** javac's `$VALUES`, or any static-final synthetic `[Lself;` field other compilers emit. */
    private fun findValuesField(node: ClassNode): FieldNode? =
        node.fields.firstOrNull { it.name == "\$VALUES" && it.desc == "[L${node.name};" }
            ?: node.fields.firstOrNull {
                it.desc == "[L${node.name};" &&
                    it.access and (Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC) ==
                        (Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_SYNTHETIC)
            }

    private class Pattern(
        /** `PUTSTATIC self.X` insn closing each constant's init block, keyed by field name */
        val initBlocks: MutableMap<String, FieldInsnNode>,
        /** `GETSTATIC self.X` insns feeding the array stores, keyed by field name */
        val arrayStores: MutableMap<String, FieldInsnNode>,
        /** `ANEWARRAY self` insn */
        val anewarray: TypeInsnNode,
        /** int-push insn directly before [anewarray] (the array size) — replaced
         *  by [renumber]/[add], so must stay current or `InsnList.set` on a stale
         *  node corrupts the whole list */
        var sizePush: AbstractInsnNode,
        /** `PUTSTATIC self.$VALUES` */
        val valuesStore: FieldInsnNode,
    )

    private fun parseClinit(node: ClassNode, clinit: MethodNode, valuesField: FieldNode): Pattern? {
        val initBlocks = mutableMapOf<String, FieldInsnNode>()
        val arrayStores = mutableMapOf<String, FieldInsnNode>()
        var anewarray: TypeInsnNode? = null
        var sizePush: AbstractInsnNode? = null
        var valuesStore: FieldInsnNode? = null

        var insn = clinit.instructions.first
        var prev: AbstractInsnNode? = null
        while (insn != null) {
            when {
                insn is TypeInsnNode && insn.opcode == Opcodes.ANEWARRAY && insn.desc == node.name -> {
                    anewarray = insn
                    sizePush = prev
                }
                insn is FieldInsnNode && insn.opcode == Opcodes.PUTSTATIC && insn.owner == node.name -> {
                    if (insn.name == valuesField.name) {
                        valuesStore = insn
                    } else if (anewarray == null) {
                        initBlocks[insn.name] = insn
                    }
                }
                insn is FieldInsnNode && insn.opcode == Opcodes.GETSTATIC && insn.owner == node.name -> {
                    if (anewarray != null && insn.name != valuesField.name) {
                        arrayStores[insn.name] = insn
                    }
                }
            }
            prev = insn
            insn = insn.next
        }

        if (initBlocks.isEmpty() || anewarray == null || sizePush == null || valuesStore == null) return null
        if (arrayStores.size != initBlocks.size) return null
        return Pattern(initBlocks, arrayStores, anewarray, sizePush, valuesStore)
    }

    // --- shared insns ---

    private fun intPush(n: Int): AbstractInsnNode = when (n) {
        -1 -> InsnNode(Opcodes.ICONST_M1)
        in 0..5 -> InsnNode(Opcodes.ICONST_0 + n)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, n)
        in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, n)
        else -> LdcInsnNode(n)
    }

    private fun intValue(insn: AbstractInsnNode): Int? = when {
        insn is InsnNode && insn.opcode in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> insn.opcode - Opcodes.ICONST_0
        insn is IntInsnNode && (insn.opcode == Opcodes.BIPUSH || insn.opcode == Opcodes.SIPUSH) -> insn.operand
        insn is LdcInsnNode && insn.cst is Int -> insn.cst as Int
        else -> null
    }

    /** back up to the `NEW self` that starts this constant's init block */
    private fun blockStart(putstatic: FieldInsnNode, self: String): AbstractInsnNode? {
        var insn: AbstractInsnNode? = putstatic
        while (insn != null) {
            if (insn is TypeInsnNode && insn.opcode == Opcodes.NEW && insn.desc == self) return insn
            insn = insn.previous
        }
        return null
    }

    /** first string `LDC` in the block = the enum-name ctor arg */
    private fun nameLdc(start: AbstractInsnNode, end: AbstractInsnNode): LdcInsnNode? {
        var insn: AbstractInsnNode? = start
        while (insn != null && insn != end) {
            if (insn is LdcInsnNode && insn.cst is String) return insn
            insn = insn.next
        }
        return null
    }

    /** first int push after [from] = the ordinal ctor arg */
    private fun ordinalPush(from: AbstractInsnNode, end: AbstractInsnNode): AbstractInsnNode? {
        var insn: AbstractInsnNode? = from
        while (insn != null && insn != end) {
            if (intValue(insn) != null) return insn
            insn = insn.next
        }
        return null
    }

    private fun cloneBlock(start: AbstractInsnNode, end: AbstractInsnNode): InsnList {
        val list = InsnList()
        val labels = mutableMapOf<LabelNode, LabelNode>()
        var insn: AbstractInsnNode? = start
        while (insn != null && insn != end.next) {
            list.add(insn.clone(labels))
            insn = insn.next
        }
        return list
    }

    private fun add(node: ClassNode, clinit: MethodNode, pattern: Pattern, name: String) {
        if (pattern.initBlocks.containsKey(name)) {
            LOGGER.warn { "enum constant $name already exists on ${node.name}, skipping ADD" }
            return
        }
        val index = pattern.initBlocks.size
        val selfDesc = "L${node.name};"

        node.fields.add(FieldNode(
            Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL or Opcodes.ACC_ENUM,
            name, selfDesc, null, null,
        ))

        // clone the last constant's init block, patch name/ordinal/field refs
        val lastPut = pattern.initBlocks.values.last()
        val start = blockStart(lastPut, node.name) ?: return warnBadShape(node.name, "init block start")
        val clone = cloneBlock(start, lastPut)

        val clonedNameLdc = nameLdc(clone.first, clone.last) ?: return warnBadShape(node.name, "name ldc")
        clonedNameLdc.cst = name
        val clonedOrdinal = ordinalPush(clonedNameLdc.next, clone.last) ?: return warnBadShape(node.name, "ordinal push")
        clone.set(clonedOrdinal, intPush(index))
        val clonedPut = clone.filterIsInstance<FieldInsnNode>().single { it.opcode == Opcodes.PUTSTATIC }
        clonedPut.name = name

        clinit.instructions.insert(lastPut, clone)
        pattern.initBlocks[name] = clonedPut

        // extend the $VALUES array: bump size, append a store for the new constant
        val newSize = intValue(pattern.sizePush)?.plus(1) ?: return warnBadShape(node.name, "array size")
        clinit.instructions.set(pattern.sizePush, intPush(newSize).also { pattern.sizePush = it })

        val getstatic = FieldInsnNode(Opcodes.GETSTATIC, node.name, name, selfDesc)
        val store = InsnList()
        store.add(InsnNode(Opcodes.DUP))
        store.add(intPush(index))
        store.add(getstatic)
        store.add(InsnNode(Opcodes.AASTORE))
        clinit.instructions.insertBefore(pattern.valuesStore, store)
        pattern.arrayStores[name] = getstatic
    }

    private fun remove(node: ClassNode, clinit: MethodNode, pattern: Pattern, name: String) {
        val putstatic = pattern.initBlocks.remove(name) ?: run {
            LOGGER.warn { "enum constant $name not found on ${node.name}, skipping REMOVE" }
            return
        }
        node.fields.removeIf { it.name == name && it.access and Opcodes.ACC_ENUM != 0 }

        // drop the constant's init block — capture the end first: removing the
        // putstatic unlinks it and makes .next unstable mid-loop
        val start = blockStart(putstatic, node.name) ?: return warnBadShape(node.name, "init block start")
        val stop = putstatic.next
        var insn: AbstractInsnNode? = start
        while (insn != null && insn != stop) {
            val next = insn.next
            clinit.instructions.remove(insn)
            insn = next
        }

        // drop the array store entry: DUP / int / GETSTATIC / AASTORE
        pattern.arrayStores.remove(name)?.let { getstatic ->
            val astore = getstatic.next
            val idxPush = getstatic.previous
            val dup = idxPush?.previous
            if (astore?.opcode == Opcodes.AASTORE) clinit.instructions.remove(astore)
            clinit.instructions.remove(getstatic)
            if (intValue(idxPush) != null) clinit.instructions.remove(idxPush)
            if (dup?.opcode == Opcodes.DUP) clinit.instructions.remove(dup)
        }

        // renumber: ordinals + array indices of later constants, and the array size
        renumber(clinit, pattern, node.name)
    }

    private fun renumber(clinit: MethodNode, pattern: Pattern, self: String) {
        clinit.instructions.set(pattern.sizePush, intPush(pattern.initBlocks.size).also { pattern.sizePush = it })
        pattern.initBlocks.values.forEachIndexed { index, putstatic ->
            val start = blockStart(putstatic, self) ?: return
            val ldc = nameLdc(start, putstatic) ?: return
            ordinalPush(ldc.next, putstatic)?.let { push ->
                if (intValue(push) != index) clinit.instructions.set(push, intPush(index))
            }
        }
        pattern.arrayStores.values.forEachIndexed { index, getstatic ->
            val push = getstatic.previous
            if (intValue(push) != null && intValue(push) != index) clinit.instructions.set(push, intPush(index))
        }
    }

    private fun warnBadShape(owner: String, what: String) =
        LOGGER.warn { "EnumExtensionElement on $owner: couldn't locate $what, skipping" }

}
