package xyz.wagyourtail.unimined.mapping

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * [NamespaceRemapper] backed by an [InheritanceTree] + [MemberGroups] —
 * propagated member names resolve at lookup time instead of being
 * materialized as mapping rows.
 *
 * Resolution order per member: component name ([MemberGroups.propagate]
 * stamps explicit row names onto the whole component, so group names
 * already reflect the rows) → explicit row on the callsite owner (members
 * absent from the scanned graph, e.g. unscanned library classes) → source
 * name. Class names always delegate to the tree.
 *
 * Field names use declaring-chain pull — the nearest declaring ancestor's
 * row wins, which doubles as the explicit-over-propagated precedence.
 */
class GraphNamespaceRemapper(
    private val tree: MappingTree,
    private val inheritance: InheritanceTree,
    private val groups: MemberGroups,
    override val from: Namespace,
    override val to: Namespace,
) : NamespaceRemapper {

    private val identity = from == to
    private val fallback = tree.remapper(from, to)

    // callsite resolution memoization — the ancestor boundary walk runs
    // once per unique (owner, name, desc). Lock-guarded: map calls arrive
    // from parallel remap workers.
    private val memoLock = ReentrantLock()
    private val methodMemo = HashMap<Triple<InternalName, UnqualifiedName, MethodDescriptor?>, UnqualifiedName>()
    private val fieldMemo = HashMap<Triple<InternalName, UnqualifiedName, FieldDescriptor?>, UnqualifiedName>()
    private val methodPrefixMemo = HashMap<Pair<InternalName, MethodNameAndDescriptor>, UnqualifiedName>()

    override fun mapClass(name: InternalName): InternalName =
        fallback.mapClass(name)

    override fun mapField(owner: InternalName, name: UnqualifiedName, desc: FieldDescriptor?): UnqualifiedName {
        if (identity) return name
        return memoLock.withLock {
            fieldMemo.getOrPut(Triple(owner, name, desc)) {
                resolveField(owner, name, desc) ?: fallback.mapField(owner, name, desc)
            }
        }
    }

    override fun mapMethod(owner: InternalName, name: UnqualifiedName, desc: MethodDescriptor?): UnqualifiedName {
        if (identity || name.value.startsWith("<")) return name
        return memoLock.withLock {
            methodMemo.getOrPut(Triple(owner, name, desc)) {
                resolveMethod(owner, name, desc) ?: fallback.mapMethod(owner, name, desc)
            }
        }
    }

    override fun mapMethodPrefixDesc(owner: InternalName, member: MethodNameAndDescriptor): UnqualifiedName {
        if (identity || member.name.value.startsWith("<")) return member.name
        return memoLock.withLock {
            methodPrefixMemo.getOrPut(owner to member) {
                resolveMethodPrefix(owner, member) ?: fallback.mapMethodPrefixDesc(owner, member)
            }
        }
    }

    /**
     * The member whose row supplied this component's [to]-namespace name —
     * the origin declaration. Param/lvar and other member children live on
     * the originating member's row, not on propagated names, so metadata
     * lookups for a remapped method should resolve through this. Null when
     * no row supplied the name (unmapped member).
     */
    fun methodOrigin(owner: InternalName, name: UnqualifiedName, desc: MethodDescriptor): MemberGroups.MethodMember? {
        if (identity || name.value.startsWith("<")) return null
        val member = declaringMethod(owner, name, desc) ?: return null
        return groups.methodGroup(member.cls, member.info).origins[to]
    }

    override fun <T : Type> map(value: T): T = fallback.map(value)

    /**
     * Collects every matching declared member reachable from [owner] —
     * resolves only on a unique match across the whole hierarchy.
     */
    private fun resolveMethodPrefix(
        owner: InternalName,
        member: MethodNameAndDescriptor,
    ): UnqualifiedName? {
        val start = inheritance.classes[owner] ?: return null
        val name = member.name
        val descPrefix = member.descriptor?.value
        val visited = mutableSetOf<InternalName>()
        val queue = ArrayDeque<InheritanceTree.ClassInfo>()
        val matches = LinkedHashSet<MemberGroups.MethodMember>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c.name)) continue
            for (mi in c.methods) {
                if (mi.name == name && (descPrefix == null || mi.descriptor.value.startsWith(descPrefix))) {
                    matches += MemberGroups.MethodMember(c, mi)
                }
            }
            c.superClass?.let { queue.add(it) }
            queue.addAll(c.interfaceClasses)
        }
        val resolved = matches.singleOrNull() ?: return null
        return groups.methodGroup(resolved.cls, resolved.info).names[to]
            ?: explicitMethodName(resolved.cls.name, name, resolved.info.descriptor)
    }

    /**
     * Find the member a callsite resolves to: [owner]'s own declaration,
     * else the first declaring class on each ancestor path (BFS boundary —
     * the JVM resolves inherited members through the hierarchy).
     */
    private fun declaringMethod(
        owner: InternalName,
        name: UnqualifiedName,
        desc: MethodDescriptor
    ): MemberGroups.MethodMember? {
        val start = inheritance.classes[owner] ?: return null
        val visited = mutableSetOf<InternalName>()
        val queue = ArrayDeque<InheritanceTree.ClassInfo>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c.name)) continue
            val m = c.methods.firstOrNull { it.name == name && it.descriptor == desc }
            if (m != null) return MemberGroups.MethodMember(c, m)
            c.superClass?.let { queue.add(it) }
            queue.addAll(c.interfaceClasses)
        }
        return null
    }

    private fun resolveMethod(
        owner: InternalName,
        name: UnqualifiedName,
        desc: MethodDescriptor?
    ): UnqualifiedName? {
        if (desc == null) return null
        val member = declaringMethod(owner, name, desc) ?: return null
        val group = groups.methodGroup(member.cls, member.info)
        return group.names[to] ?: explicitMethodName(member.cls.name, name, desc)
    }

    private fun explicitMethodName(
        clsName: InternalName,
        name: UnqualifiedName,
        desc: MethodDescriptor
    ): UnqualifiedName? {
        val cls = tree.getClass(from, clsName) ?: return null
        return with(tree) { cls.getMethod(from, name, desc) }?.names?.get(to)
    }

    /**
     * Chain of declaring members for a field callsite — own declaration
     * first, then declaring ancestors nearest-first (matching
     * [MemberGroups.fieldChain] semantics).
     */
    private fun declaringFieldChain(
        owner: InternalName,
        name: UnqualifiedName,
        desc: FieldDescriptor
    ): List<MemberGroups.FieldMember> {
        val start = inheritance.classes[owner] ?: return emptyList()
        val chain = mutableListOf<MemberGroups.FieldMember>()
        val visited = mutableSetOf<InternalName>()
        val queue = ArrayDeque<InheritanceTree.ClassInfo>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c.name)) continue
            val m = c.fields.firstOrNull { it.name == name && it.descriptor == desc }
            if (m != null) chain += MemberGroups.FieldMember(c, m)
            else {
                c.superClass?.let { queue.add(it) }
                queue.addAll(c.interfaceClasses)
            }
        }
        return chain
    }

    private fun resolveField(
        owner: InternalName,
        name: UnqualifiedName,
        desc: FieldDescriptor?
    ): UnqualifiedName? {
        if (desc == null) return null
        for (member in declaringFieldChain(owner, name, desc)) {
            val cls = tree.getClass(from, member.cls.name) ?: continue
            val row = with(tree) { cls.getField(from, name, desc) } ?: continue
            row.names[to]?.let { return it }
        }
        return null
    }

}
