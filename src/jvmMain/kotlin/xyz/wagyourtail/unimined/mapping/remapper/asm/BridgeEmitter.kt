package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.SyntheticBridgeElement

/**
 * Emits `ACC_BRIDGE | ACC_SYNTHETIC` delegate methods for [SyntheticBridgeElement]
 * entries on a remapped method — one bridge per alias name in `targetNs`.
 *
 * Bridges share the impl's descriptor (aliases are alternate *names* for the
 * same erased signature — that's what makes them bridges) and forward all args
 * to the impl. Used when propagated ancestor names lose a conflict in a ns —
 * e.g. loom's unifying-interface case — so the impl stays reachable under the
 * name callers in that ns expect.
 */
object BridgeEmitter {

    fun emit(node: ClassNode, impl: MethodNode, mapping: MethodMapping, targetNs: Namespace) {
        val aliases = mapping.metadata.filterIsInstance<SyntheticBridgeElement>()
            .flatMap { it.names.entries }
            .filter { (ns, _) -> ns == targetNs }
            .map { (_, name) -> name.value }

        for (alias in aliases) {
            // dup-name guard — skip if a method already occupies the alias slot
            if (node.methods.any { it.name == alias && it.desc == impl.desc }) continue

            val bridge = MethodNode(
                Opcodes.ASM9,
                (impl.access and Opcodes.ACC_BRIDGE.inv()) or Opcodes.ACC_BRIDGE or Opcodes.ACC_SYNTHETIC,
                alias,
                impl.desc,
                null,
                impl.exceptions?.toTypedArray(),
            )

            val isStatic = impl.access and Opcodes.ACC_STATIC != 0
            val itf = node.access and Opcodes.ACC_INTERFACE != 0
            var slot = 0
            if (!isStatic) {
                bridge.visitVarInsn(Opcodes.ALOAD, 0)
                slot = 1
            }
            for (arg in Type.getArgumentTypes(impl.desc)) {
                bridge.visitVarInsn(arg.getOpcode(Opcodes.ILOAD), slot)
                slot += arg.size
            }
            val opcode = when {
                isStatic -> Opcodes.INVOKESTATIC
                impl.access and Opcodes.ACC_PRIVATE != 0 -> Opcodes.INVOKESPECIAL
                itf -> Opcodes.INVOKEINTERFACE
                else -> Opcodes.INVOKEVIRTUAL
            }
            bridge.visitMethodInsn(opcode, node.name, impl.name, impl.desc, itf)
            val ret = Type.getReturnType(impl.desc)
            bridge.visitInsn(ret.getOpcode(Opcodes.IRETURN))
            bridge.visitMaxs(maxOf(slot, ret.size), slot)
            bridge.visitEnd()

            node.methods.add(bridge)
        }
    }

}
