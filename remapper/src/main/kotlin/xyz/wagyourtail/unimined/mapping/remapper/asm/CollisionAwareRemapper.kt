package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode

/**
 * [Remapper] decorator that refuses to rename members *into* an occupied
 * (name, desc) — i.e. when two declared members in the same class map to
 * the same target signature.
 *
 * Real-world case: FG-era forge jars ship dual-named methods — the obf
 * override (`a(I)L…;`) plus an srg/mcp trampoline (`get(I)L…;` →
 * `return a(i)`). Remapping `a → get` produces two `get(I)` methods —
 * invalid bytecode (`ClassFormatError: Duplicate method name`). Keeping
 * the source name on the loser preserves a loadable, semantically
 * equivalent class: callers renamed `a → get` land on the surviving
 * trampoline, which forwards to the (still) `a`-named impl.
 *
 * Winner rule per colliding (targetName, targetDesc) group: a member
 * whose *source* name already equals the target keeps the target —
 * that is the pre-existing alias/impl, and the alternative (renaming it
 * away or dropping the other member) is always worse. Otherwise the
 * first declaration in class-file order wins. Losers keep their source
 * name — dead-weight compat aliases, but valid.
 *
 * The skip set only applies to members **declared in** [owner]: callers
 * referencing the same (name, desc) on other classes still rename — they
 * resolve onto the surviving target member.
 */
internal class CollisionAwareRemapper(
    private val delegate: Remapper,
    private val ownerName: String,
    private val skipMethods: Set<String>,
    private val skipFields: Set<String>,
) : Remapper() {

    companion object {
        private data class Member(val name: String, val desc: String)

        /** Wrap [delegate] so member renames in [node] never produce
         *  duplicate (name, desc) pairs — see class doc. */
        fun wrap(node: ClassNode, delegate: Remapper): Remapper {
            val skipMethods = mutableSetOf<String>()
            val byTarget = node.methods
                // ctors are never renamed — a `<init>`-named target is
                // impossible, so they can't occupy a rename target
                .filter { it.name[0] != '<' }
                .groupBy { m ->
                    Member(
                        delegate.mapMethodName(node.name, m.name, m.desc),
                        delegate.mapDesc(m.desc),
                    )
                }
            for ((target, group) in byTarget) {
                if (group.size < 2) continue
                // source-name == target wins the slot (the pre-existing
                // member); else first declaration
                val winner = group.firstOrNull { it.name == target.name }
                    ?: group.first()
                group.asSequence().filter { it !== winner }
                    .mapTo(skipMethods) { it.name + it.desc }
            }
            val skipFields = mutableSetOf<String>()
            val fieldsByTarget = node.fields.groupBy { f ->
                Member(
                    delegate.mapFieldName(node.name, f.name, f.desc),
                    delegate.mapDesc(f.desc),
                )
            }
            for ((target, group) in fieldsByTarget) {
                if (group.size < 2) continue
                val winner = group.firstOrNull { it.name == target.name }
                    ?: group.first()
                group.asSequence().filter { it !== winner }
                    .mapTo(skipFields) { it.name + it.desc }
            }
            return if (skipMethods.isEmpty() && skipFields.isEmpty()) delegate
            else CollisionAwareRemapper(
                delegate, node.name, skipMethods, skipFields)
        }

        /**
         * Post-remap pass dropping *pure self-invokers*: methods whose
         * body is loads + `return this.<same name, same desc>(args)`.
         *
         * Dual-name forge members produce these when a trampoline's
         * impl is declared on a *parent* and renames onto the
         * trampoline's own signature — e.g. FML's
         * `getId(I) { return func_148757_b(o); }` where the inherited
         * `func_148757_b` maps to `getId`. The remapped body is
         * infinite recursion by construction; removing the method lets
         * callers resolve to the surviving impl. Same-class collisions
         * don't need this — the skip set keeps the impl's source name,
         * so the trampoline still forwards to a real method.
         *
         * The check is deliberately strict — only variable loads, one
         * invoke, one return — so genuinely recursive methods (which
         * compute args or branch before self-calling) never match.
         */
        fun dropSelfInvokers(node: ClassNode) {
            node.methods.removeIf { m ->
                val insns = m.instructions.asIterable()
                    .filter { it.opcode >= 0 }
                if (insns.size < 3) return@removeIf false
                val last = insns.last()
                // xRETURN (172–177)
                if (last.opcode !in 172..177) return@removeIf false
                val invoke = insns.elementAt(insns.size - 2)
                        as? org.objectweb.asm.tree.MethodInsnNode
                        ?: return@removeIf false
                if (invoke.owner != node.name || invoke.name != m.name ||
                    invoke.desc != m.desc) return@removeIf false
                insns.dropLast(2).all {
                    it is org.objectweb.asm.tree.VarInsnNode
                }
            }
        }
    }

    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        // Remapper.mapValue can route field Handles through here — mirror
        // MappingRemapper's desc-shape guard
        if (!descriptor.startsWith("(")) return mapFieldName(owner, name, descriptor)
        return if (owner == this.ownerName && (name + descriptor) in skipMethods) name
        else delegate.mapMethodName(owner, name, descriptor)
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String =
        if (owner == ownerName && (name + descriptor) in skipFields) name
        else delegate.mapFieldName(owner, name, descriptor)

    /** forwards the declaring-owner rewrite — see
     *  [MappingRemapper.mapFieldInsnOwner]. */
    fun mapFieldInsnOwner(owner: String, name: String, descriptor: String): String =
        if (delegate is MappingRemapper) delegate.mapFieldInsnOwner(owner, name, descriptor)
        else map(owner)

    // field Handle constants bind by (owner, name, desc) like insns —
    // same declaring-owner rewrite; the name still goes through our
    // skip-aware hook so collision losers keep their source name
    override fun mapValue(value: Any?): Any? {
        if (value is Handle && value.tag in Opcodes.H_GETFIELD..Opcodes.H_PUTSTATIC) {
            return Handle(
                value.tag,
                mapFieldInsnOwner(value.owner, value.name, value.desc),
                mapFieldName(value.owner, value.name, value.desc),
                mapDesc(value.desc),
                value.isInterface,
            )
        }
        return super.mapValue(value)
    }

    // every other name hook must forward to [delegate] — the base Remapper
    // implementations are identity, so anything not forwarded silently
    // drops renames for the wrapped class (superclass, interfaces,
    // descriptors, inner/outer class names, record components, …)

    override fun map(internalName: String): String = delegate.map(internalName)

    override fun mapRecordComponentName(owner: String, name: String, descriptor: String): String =
        delegate.mapRecordComponentName(owner, name, descriptor)

    override fun mapAnnotationAttributeName(descriptor: String, name: String): String =
        delegate.mapAnnotationAttributeName(descriptor, name)

    override fun mapInvokeDynamicMethodName(name: String, descriptor: String): String =
        delegate.mapInvokeDynamicMethodName(name, descriptor)

    override fun mapInnerClassName(name: String, ownerName: String, innerName: String): String =
        delegate.mapInnerClassName(name, ownerName, innerName)

    override fun mapModuleName(name: String): String = delegate.mapModuleName(name)

    override fun mapPackageName(name: String): String = delegate.mapPackageName(name)
}
