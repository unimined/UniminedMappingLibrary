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
     * The pre-fallback member answer — the component name or the declaring
     * member's explicit row, `null` when neither resolves. This is exactly
     * the predicate write-time propagation
     * ([xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree])
     * materializes a member cell on, so lazy tree views can test "would a
     * propagated row exist here" without producing one. Identity remaps and
     * JVM-special names (`<init>`/`<clinit>`) never resolve — `null`.
     */
    fun resolveMethodName(owner: InternalName, name: UnqualifiedName, desc: MethodDescriptor): UnqualifiedName? =
        if (identity || name.value.startsWith("<")) null else resolveMethod(owner, name, desc)

    /** field counterpart of [resolveMethodName] — nearest declaring-chain
     *  row name or `null` when no row in the chain names [to]. */
    fun resolveFieldName(owner: InternalName, name: UnqualifiedName, desc: FieldDescriptor): UnqualifiedName? =
        if (identity) null else resolveField(owner, name, desc)

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
        val member = if (desc != null) {
            declaringMethod(owner, name, desc) ?: return null
        } else {
            uniqueNameMatch(owner, name) ?: return null
        }
        val group = groups.methodGroup(member.cls, member.info)
        return group.names[to] ?: explicitMethodName(member.cls.name, name, member.info.descriptor)
    }

    /**
     * The nearest declaring class holding members named [name] (any desc) —
     * used for desc-less refs like mixin `method=` strings, where the flat
     * fallback would prefer an impl-class row (filled with a name echoed
     * from another column) over the declaring row's real mapping. Resolves
     * only when the boundary class declares the name once among its
     * non-synthetic candidates (synthetics resolve only when nothing
     * declared shares the name) — desc-less refs on real overloads stay
     * ambiguous and fall back.
     */
    private fun uniqueNameMatch(
        owner: InternalName,
        name: UnqualifiedName
    ): MemberGroups.MethodMember? {
        val start = inheritance.classes[owner] ?: return null
        val visited = mutableSetOf<InternalName>()
        val queue = ArrayDeque<InheritanceTree.ClassInfo>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c.name)) continue
            val matches = c.methods.filter { it.name == name }
            // declared members rank ahead of synthetic/bridge — the AP's
            // element model never sees synthetics, so a name-only ref
            // canonically resolves to the declaration; all-synthetic
            // matches (e.g. lambda$…) still resolve when nothing else exists
            val preferred = matches.filter { !it.isSynthetic && !it.isBridge }
            if (matches.isNotEmpty()) return (preferred.ifEmpty { matches }).singleOrNull()
                ?.let { MemberGroups.MethodMember(c, it) }
            c.superClass?.let { queue.add(it) }
            queue.addAll(c.interfaceClasses)
        }
        return null
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
        desc: FieldDescriptor?
    ): List<MemberGroups.FieldMember> {
        val start = inheritance.classes[owner] ?: return emptyList()
        val chain = mutableListOf<MemberGroups.FieldMember>()
        val visited = mutableSetOf<InternalName>()
        val queue = ArrayDeque<InheritanceTree.ClassInfo>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c.name)) continue
            // desc-less refs match on name alone — the boundary class's
            // declaration then supplies the real descriptor for the row read.
            // Declared members rank ahead of synthetic (no bridge bit on
            // fields — 0x40 is VOLATILE there); synthetic stays in the chain
            // as a lower-priority candidate
            val ms = c.fields.filter { it.name == name && (desc == null || it.descriptor == desc) }
            if (ms.isNotEmpty()) ms.sortedBy { it.isSynthetic }.mapTo(chain) { MemberGroups.FieldMember(c, it) }
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
        for (member in declaringFieldChain(owner, name, desc)) {
            val cls = tree.getClass(from, member.cls.name) ?: continue
            val row = with(tree) {
                cls.getField(from, name, member.info.descriptor)
            } ?: continue
            // identity rows (name == source name — no-ops and completed
            // cells) don't outrank a real rename on a farther ancestor —
            // same precedence as MemberGroups method candidates. The test
            // is an echo of ANY sibling column, not just the source name:
            // impl rows filled with e.g. the official `a` must lose to the
            // declaring member's real `field_*` name.
            row.names[to]?.takeIf { t -> row.names.none { (k, v) -> k != to && v == t } }
                ?.let { return it }
        }
        return null
    }

}
