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
import xyz.wagyourtail.unimined.mapping.tree.impl.isGenuineCellName

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
    /** declared column order — the direction name-fills copy (earlier →
     *  later); echo detection may only look *back* along it */
    private val nsOrder = tree.namespaces.toList()

    // callsite resolution memoization — the ancestor boundary walk runs
    // once per unique (owner, name, desc). Lock-guarded: map calls arrive
    // from parallel remap workers.
    private val memoLock = ReentrantLock()
    private val methodMemo = HashMap<Triple<InternalName, UnqualifiedName, MethodDescriptor?>, UnqualifiedName>()
    // resolved field refs memoize the *member*, not just the name — the
    // declaring class and the name it supplied must stay paired so
    // insn-owner and name consumers can't disagree. Explicit nulls
    // memoize misses; the chain walk is the expensive part.
    private val fieldMemo = HashMap<Triple<InternalName, UnqualifiedName, FieldDescriptor?>, Pair<MemberGroups.FieldMember, UnqualifiedName>?>()
    private val methodPrefixMemo = HashMap<Pair<InternalName, MethodNameAndDescriptor>, UnqualifiedName>()

    override fun mapClass(name: InternalName): InternalName =
        fallback.mapClass(name)

    override fun mapField(owner: InternalName, name: UnqualifiedName, desc: FieldDescriptor?): UnqualifiedName {
        if (identity) return name
        return fieldResolution(owner, name, desc)?.second ?: fallback.mapField(owner, name, desc)
    }

    /**
     * The mapped declaring class of the member a field ref binds to —
     * for emitting as the ref's owner. A subclass can declare a field
     * whose name collides with an inherited member's *target* name
     * (FG2-era FML adds `EntityPlayer.eyeHeight`; yarn names the
     * inherited `Entity.field_70129_M` `eyeHeight` too) — a
     * callsite-owned ref then binds statically to the child's field.
     * Pinning the declaring owner keeps the ref bound to the member
     * the name came from. Falls back to the callsite owner when no
     * declaring member resolves (unscanned/library classes).
     */
    fun fieldInsnOwner(owner: InternalName, name: UnqualifiedName, desc: FieldDescriptor?): InternalName {
        val resolution = fieldResolution(owner, name, desc) ?: return mapClass(owner)
        val declaring = resolution.first.cls.name
        if (declaring == owner) return mapClass(owner)
        // pin only when a callsite-owned ref would actually mis-bind:
        // some class between the callsite owner and the declaring class
        // declares a field whose *target* name collides with the resolved
        // one (the FG2 eyeHeight shape). Unconditional pinning rewrites
        // owners exact-match tooling (mixin slice/injection targets)
        // expects to find verbatim — production remappers never rewrite
        // them, so a collision-free ref must keep its callsite owner.
        if (!fieldBindingHazard(owner, declaring, resolution.second)) {
            return mapClass(owner)
        }
        return mapClass(declaring)
    }

    /**
     * True when some class on the inheritance path from [owner] up to
     * (excluding) [declaring] declares a field whose target-namespace
     * name matches the resolved member's — a ref emitted with the
     * callsite owner could then statically bind to that nearer field
     * instead of the intended declaring-class member. Name-only:
     * desc-mismatched collisions can't actually capture the ref, but
     * pinning them too is harmless next to the exact-match parity the
     * no-collision path preserves.
     */
    private fun fieldBindingHazard(
        owner: InternalName,
        declaring: InternalName,
        targetName: UnqualifiedName
    ): Boolean {
        val start = inheritance.classes[owner] ?: return false
        val visited = mutableSetOf<InternalName>()
        val queue = ArrayDeque<InheritanceTree.ClassInfo>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c.name)) continue
            if (c.name == declaring) continue
            for (f in c.fields) {
                if (mapField(c.name, f.name, f.descriptor) == targetName) return true
            }
            c.superClass?.let { queue.add(it) }
            queue.addAll(c.interfaceClasses)
        }
        return false
    }

    /** Memoized [resolveFieldMember] shared by name and insn-owner lookups. */
    private fun fieldResolution(
        owner: InternalName,
        name: UnqualifiedName,
        desc: FieldDescriptor?,
    ): Pair<MemberGroups.FieldMember, UnqualifiedName>? = memoLock.withLock {
        val key = Triple(owner, name, desc)
        if (key in fieldMemo) fieldMemo[key]
        else resolveFieldMember(owner, name, desc).also { fieldMemo[key] = it }
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
        val row = with(tree) { cls.getMethod(from, name, desc) } ?: return null
        // the declaring row's stated [to] name — echo cells (fills that
        // copied an earlier column) still encode the member's name in
        // this namespace, and precedence vs other candidates is already
        // settled by `group.names[to] ?:` at every callsite; returning
        // null here would fall to the flat *callsite-owner* lookup which
        // loses inherited renames entirely
        return row.names[to]
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
    ): UnqualifiedName? = resolveFieldMember(owner, name, desc)?.second

    /**
     * The declaring member whose row supplies the [to] name, paired with
     * that name — [mapField]/[fieldInsnOwner] share this so the emitted
     * ref always binds the member the name came from.
     */
    private fun resolveFieldMember(
        owner: InternalName,
        name: UnqualifiedName,
        desc: FieldDescriptor?
    ): Pair<MemberGroups.FieldMember, UnqualifiedName>? {
        var echoCandidate: Pair<MemberGroups.FieldMember, UnqualifiedName>? = null
        for (member in declaringFieldChain(owner, name, desc)) {
            val cls = tree.getClass(from, member.cls.name) ?: continue
            val row = with(tree) {
                cls.getField(from, name, member.info.descriptor)
            } ?: continue
            val cell = row.names[to] ?: continue
            // a genuine rename (the [to] name distinct from every earlier
            // column — fills copy strictly earlier→later) outranks any
            // echo cell, including a nearer ancestor's. A *later* column
            // carrying the same name copies *from* this cell — it doesn't
            // make this cell an echo: `{official: c, searge: field_x,
            // mcp: field_x}` keeps `field_x` genuine for `searge`.
            if (isGenuineCellName(nsOrder, row.names, to) { n -> n }) {
                return member to cell
            }
            // echo cells still encode the member's stated [to] name —
            // `{official: c, searge: field_x, mcp: field_x}` targeting
            // `mcp` resolves `field_x`, never `c`. They only lose the
            // ordering race against a genuine rename (same precedence as
            // MemberGroups' real.ifEmpty { cands } winner resolution), so
            // the nearest one survives as the fallback.
            if (echoCandidate == null) echoCandidate = member to cell
        }
        return echoCandidate
    }

}
