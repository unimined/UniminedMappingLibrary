package xyz.wagyourtail.unimined.mapping.inheritance

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * Precomputed member-name equivalence over an [InheritanceTree] — the
 * propagated-name graph computed once instead of re-walked per mapping row.
 *
 * **Virtual methods** (inheritable: non-private, non-static) form union-find
 * components: a member is unioned with its *first-ring virtual declaring
 * ancestors* — ancestors reached via BFS through classes that don't declare
 * the member (propagation climbs through member-less intermediates; a
 * non-virtual match doesn't join the component but doesn't block the climb
 * either). Transitivity connects siblings through a declaring parent.
 *
 * **Non-virtual methods** are singletons — names never propagate through
 * non-inheritable members.
 *
 * **Fields** resolve pull-style through [fieldChain] — nearest declaring
 * ancestor first — which preserves the richer down-propagation semantics
 * (a parent row names matching descendant fields) with no writes.
 *
 * All namespaces share one component — a row stamps `group.names` per
 * namespace column, so any `(from, to)` pair resolves from a single graph.
 *
 * Components resolve eagerly ([build]) — lazily unioning at query time
 * would re-root components and orphan names stamped under old canonicals.
 * Query methods are safe to call from parallel remap workers once
 * [propagate] (or [build] alone) has run; field chains memoize under a
 * lock since they derive from arbitrary callsites.
 */
class MemberGroups(val inheritance: InheritanceTree) {

    /** A declaring member: (owning class, scanned member). [InheritanceTree.ClassInfo]
     *  has identity equality; MethodInfo/FieldInfo compare name+desc. */
    data class MethodMember(val cls: InheritanceTree.ClassInfo, val info: InheritanceTree.MethodInfo)
    data class FieldMember(val cls: InheritanceTree.ClassInfo, val info: InheritanceTree.FieldInfo)

    /** Per-namespace names for one component — all member rows that landed
     *  in this group contribute columns; candidates are resolved by
     *  [propagate]. */
    class MethodGroup {
        val names = mutableMapOf<Namespace, UnqualifiedName>()
        /** the member whose row supplied each [names] entry — the
         *  *originating declaration*: param/lvar and other member children
         *  live on that row, not on the propagated name. */
        val origins = mutableMapOf<Namespace, MethodMember>()
        /** name → candidate member, per ns — resolved alongside [names]. */
        internal val candidateMembers = mutableMapOf<Namespace, MutableMap<UnqualifiedName, MethodMember>>()
        /** losing candidates per ns — conflict bookkeeping (deterministic:
         *  resolution picks sorted-first, count returned by [propagate]). */
        val candidates = mutableMapOf<Namespace, MutableSet<UnqualifiedName>>()
    }

    // union-find over virtual method members
    private val uf = HashMap<MethodMember, MethodMember>()
    private val resolved = HashSet<MethodMember>()
    private val groups = HashMap<MethodMember, MethodGroup>()
    private val fieldChains = HashMap<FieldMember, List<FieldMember>>()
    private val lock = ReentrantLock()

    @Volatile
    private var built = false

    private fun find(m: MethodMember): MethodMember {
        var r = m
        var p = uf[r]
        while (p != null) {
            r = p
            p = uf[r]
        }
        return r
    }

    private fun union(a: MethodMember, b: MethodMember) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) {
            uf[rb] = ra
            // late unions (lazy trees materializing supertypes mid-query):
            // relocate the losing component's stamps to the new canonical —
            // otherwise names/candidates stamped under `rb` are orphaned
            groups[rb]?.let { loser ->
                val winner = groups.getOrPut(ra) { MethodGroup() }
                // put-if-absent: earlier stamps win (MutableMap.putIfAbsent
                // is JVM-only; this is commonMain)
                loser.names.forEach { (ns, n) -> if (ns !in winner.names) winner.names[ns] = n }
                loser.origins.forEach { (ns, m) -> if (ns !in winner.origins) winner.origins[ns] = m }
                loser.candidates.forEach { (ns, s) ->
                    winner.candidates.getOrPut(ns) { mutableSetOf() }.addAll(s)
                }
                loser.candidateMembers.forEach { (ns, m) ->
                    val wm = winner.candidateMembers.getOrPut(ns) { mutableMapOf() }
                    m.forEach { (n, mm) -> if (n !in wm) wm[n] = mm }
                }
                groups.remove(rb)
            }
        }
    }

    private fun inheritableVirtual(m: InheritanceTree.MethodInfo) =
        !m.name.value.startsWith("<") && AccessFlag.isInheritable(m.access)

    private fun parentsOf(c: InheritanceTree.ClassInfo): List<InheritanceTree.ClassInfo> =
        buildList {
            c.superClass?.let { add(it) }
            addAll(c.interfaceClasses)
        }

    /**
     * First-ring *virtual* declaring ancestors of `(info, name, desc)`:
     * BFS upward; a matching virtual member joins the ring and ends that
     * path (its own group reaches higher ancestors transitively). Other
     * classes — including ones with a non-virtual match — are climbed
     * through.
     */
    private fun firstRingVirtual(
        info: InheritanceTree.ClassInfo,
        name: UnqualifiedName,
        desc: MethodDescriptor
    ): List<MethodMember> {
        val ring = mutableListOf<MethodMember>()
        val visited = mutableSetOf(info)
        val queue = ArrayDeque(parentsOf(info))
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c)) continue
            val m = c.methods.firstOrNull { it.name == name && it.descriptor == desc }
            if (m != null && inheritableVirtual(m)) {
                ring += MethodMember(c, m)
            } else {
                queue.addAll(parentsOf(c))
            }
        }
        return ring
    }

    /**
     * Resolve every method member's component and populate [groups] for all
     * canonicals, so queries are pure reads. Idempotent; must run before
     * [propagate] stamps names (a late union would re-root a component and
     * orphan its stamped names under the old canonical).
     */
    fun build() {
        if (built) return
        lock.withLock {
            if (built) return
            // resolve to a fixpoint: lazy trees materialize supertypes
            // while resolve walks rings, growing `classes` mid-iteration —
            // every materialized class's members must be resolved before
            // groups are populated (eager trees converge in one pass)
            var seen = -1
            while (seen != inheritance.classes.size) {
                seen = inheritance.classes.size
                for (c in inheritance.classes.values.toList()) {
                    for (mi in c.methods) {
                        resolve(c, mi)
                    }
                }
            }
            for (c in inheritance.classes.values) {
                for (mi in c.methods) {
                    groups.getOrPut(find(MethodMember(c, mi))) { MethodGroup() }
                }
            }
            built = true
        }
    }

    private fun resolve(info: InheritanceTree.ClassInfo, mi: InheritanceTree.MethodInfo) {
        val self = MethodMember(info, mi)
        if (!resolved.add(self)) return // visited — also breaks hierarchy cycles
        if (!inheritableVirtual(mi)) return
        for (m in firstRingVirtual(info, mi.name, mi.descriptor)) {
            resolve(m.cls, m.info)
            union(self, m)
        }
    }

    /** The [MethodGroup] [mi] belongs to — triggers [build] on first use.
     *  Resolves on demand: a lazy tree may materialize [mi]'s class only
     *  when queried (post-build), so its member isn't guaranteed to be in
     *  [groups] yet. */
    fun methodGroup(info: InheritanceTree.ClassInfo, mi: InheritanceTree.MethodInfo): MethodGroup {
        build()
        resolve(info, mi)
        return groups.getOrPut(find(MethodMember(info, mi))) { MethodGroup() }
    }

    /**
     * `(info, fi)` plus its declaring ancestors, nearest first — the
     * pull-side chain for UML's down-propagation field semantics. Any
     * declaring ancestor ends its own path (closer declarations win), so
     * the chain is the BFS boundary of declaring classes.
     */
    fun fieldChain(info: InheritanceTree.ClassInfo, fi: InheritanceTree.FieldInfo): List<FieldMember> =
        lock.withLock {
            fieldChains.getOrPut(FieldMember(info, fi)) {
                val chain = mutableListOf(FieldMember(info, fi))
                val visited = mutableSetOf(info)
                val queue = ArrayDeque(parentsOf(info))
                while (queue.isNotEmpty()) {
                    val c = queue.removeFirst()
                    if (!visited.add(c)) continue
                    val m = c.fields.firstOrNull { it.name == fi.name && it.descriptor == fi.descriptor }
                    if (m != null) {
                        chain += FieldMember(c, m)
                    } else {
                        queue.addAll(parentsOf(c))
                    }
                }
                chain
            }
        }

    /**
     * Stamp every member row of [tree] into the groups — each row's
     * namespace columns land on its member's component. Conflicting
     * candidates resolve deterministically (sorted-first wins); the count
     * of disagreements is returned for reporting.
     */
    fun propagate(tree: MappingTree): Int {
        inheritance.applyAccessChanges(tree)
        build()
        var conflicts = 0
        for (cls in tree.classes) {
            val fnsName = cls.names[inheritance.fns] ?: continue
            val info = inheritance.classes[fnsName] ?: continue
            for (mm in cls.methods) {
                val entry = mm.entries[inheritance.fns] ?: continue
                val desc = entry.descriptor ?: continue
                val mi = info.methods.firstOrNull {
                    it.name == entry.name && it.descriptor == desc
                } ?: continue
                resolve(info, mi)
                val group = groups.getOrPut(find(MethodMember(info, mi))) { MethodGroup() }
                for ((ns, e) in mm.entries) {
                    group.candidates.getOrPut(ns) { mutableSetOf() }.add(e.name)
                    group.candidateMembers.getOrPut(ns) { mutableMapOf() }.let { m ->
                        if (e.name !in m) m[e.name] = MethodMember(info, mi)
                    }
                }
            }
            // fields need no stamping — [fieldChain] + row lookups at query
            // time give down-propagation pull semantics with zero writes
        }
        lock.withLock {
            for ((_, group) in groups) {
                for ((ns, cands) in group.candidates) {
                    if (cands.size > 1) conflicts++
                    val winner = cands.minBy { it.value }
                    group.names[ns] = winner
                    group.candidateMembers[ns]?.get(winner)?.let { group.origins[ns] = it }
                }
                group.candidateMembers.clear()
            }
        }
        return conflicts
    }

}
