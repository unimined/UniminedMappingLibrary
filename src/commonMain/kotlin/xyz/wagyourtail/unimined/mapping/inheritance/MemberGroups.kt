package xyz.wagyourtail.unimined.mapping.inheritance

import kotlin.concurrent.Volatile
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.newNsMap

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
        val names = newNsMap<UnqualifiedName>()
        /** the member whose row supplied each [names] entry — the
         *  *originating declaration*: param/lvar and other member children
         *  live on that row, not on the propagated name. */
        val origins = newNsMap<MethodMember>()
        /** name → candidate member, per ns — resolved alongside [names]. */
        internal val candidateMembers = newNsMap<MutableMap<UnqualifiedName, MethodMember>>()
        /** losing candidates per ns — conflict bookkeeping (deterministic:
         *  resolution picks sorted-first, count returned by [propagate]). */
        val candidates = newNsMap<MutableSet<UnqualifiedName>>()
        /** candidates stamped by a row as a genuine rename for that ns —
         *  i.e. the name differs from every other ns column on its own
         *  row. Names that merely echo a sibling column (identity /
         *  no-op stamps, e.g. unobf-completion fills and hop-fills like
         *  an official `a` copied into `intermediary`) are never added
         *  here: they lose to any real rename in the component, because
         *  a completed `a` on an unmapped implementing member must not
         *  degrade a real `m_*`/`f_*` row declared elsewhere in the same
         *  virtual-method group. */
        internal val genuineNames = newNsMap<MutableSet<UnqualifiedName>>()
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
                loser.genuineNames.forEach { (ns, s) ->
                    winner.genuineNames.getOrPut(ns) { mutableSetOf() }.addAll(s)
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
     *  [groups] yet. Locked: [resolve]/[find]/`groups` mutate the
     *  union-find, and late-merged scans (`LazyASMInheritanceTree.mergeScan`)
     *  can surface unresolved members on concurrent readers. */
    fun methodGroup(info: InheritanceTree.ClassInfo, mi: InheritanceTree.MethodInfo): MethodGroup {
        build()
        return lock.withLock {
            resolve(info, mi)
            groups.getOrPut(find(MethodMember(info, mi))) { MethodGroup() }
        }
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
     * The unique method named [name] (and [desc], when non-null)
     * declaring anywhere in [info]'s hierarchy — the resolver-side
     * analogue of Tiny Remapper's `resolveUnique`. Collects self plus
     * the nearest-per-branch declaring ancestors (a declaration ends
     * its branch's climb, like [fieldChain]), then collapses matches
     * by union component: diamond re-declarations of one override set
     * count once, while genuinely distinct components make the name
     * ambiguous. Returns the nearest match, or `null` when nothing
     * declares it / the name is ambiguous.
     */
    fun resolveUniqueMethod(
        info: InheritanceTree.ClassInfo,
        name: UnqualifiedName,
        desc: MethodDescriptor? = null
    ): MethodMember? {
        fun declared(c: InheritanceTree.ClassInfo) =
            c.methods.filter { it.name == name && (desc == null || it.descriptor == desc) }
        build()
        return lock.withLock {
            val found = mutableListOf<MethodMember>()
            declared(info).mapTo(found) { MethodMember(info, it) }
            val visited = mutableSetOf(info)
            val queue = ArrayDeque(parentsOf(info))
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                if (!visited.add(c)) continue
                val ms = declared(c)
                if (ms.isNotEmpty()) {
                    ms.mapTo(found) { MethodMember(c, it) }
                } else {
                    queue.addAll(parentsOf(c))
                }
            }
            // collapse by component — re-declared overrides of one
            // logical method (diamonds) count once; distinct
            // components are genuinely ambiguous
            val canonicals = found.mapTo(mutableSetOf()) {
                resolve(it.cls, it.info)
                find(it)
            }
            if (canonicals.size == 1) found.first() else null
        }
    }

    /**
     * The unique field named [name] (and [desc], when non-null)
     * declaring anywhere in [info]'s hierarchy — the [fieldChain]
     * boundary restricted to one member: `null` when nothing matches
     * or multiple visible declarations make the name ambiguous
     * (fields never merge — a shadowed ancestor stays a distinct
     * member, unlike methods collapsing by component).
     */
    fun resolveUniqueField(
        info: InheritanceTree.ClassInfo,
        name: UnqualifiedName,
        desc: FieldDescriptor? = null
    ): FieldMember? {
        val found = mutableListOf<FieldMember>()
        lock.withLock {
            info.fields.firstOrNull { it.name == name && (desc == null || it.descriptor == desc) }
                ?.let { found += FieldMember(info, it) }
            val visited = mutableSetOf(info)
            val queue = ArrayDeque(parentsOf(info))
            while (queue.isNotEmpty()) {
                val c = queue.removeFirst()
                if (!visited.add(c)) continue
                val m = c.fields.firstOrNull { it.name == name && (desc == null || it.descriptor == desc) }
                if (m != null) {
                    found += FieldMember(c, m)
                } else {
                    queue.addAll(parentsOf(c))
                }
            }
        }
        return found.singleOrNull()
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
        for (cls in tree.classes) {
            stampClass(cls, null)
        }
        var conflicts = 0
        lock.withLock {
            for ((_, group) in groups) {
                conflicts += resolveWinners(group)
            }
        }
        return conflicts
    }

    /**
     * [propagate] restricted to the classes named in [scope]
     * (fns-namespace internal names) — the late-union path for input
     * scans merged via `LazyASMInheritanceTree.mergeScan` *after* the
     * batch-wide pass: the input's own classes were absent from the
     * graph then, so their member rows never stamped a component and
     * overrides in unmapped subclasses kept their source names.
     * Stamps + winner re-resolution run under [lock] so a late pass
     * is safe against concurrent [methodGroup]/[fieldChain] queries.
     * Idempotent — re-stamping the same rows yields the same winners.
     */
    fun propagate(tree: MappingTree, scope: Set<InternalName>): Int {
        inheritance.applyAccessChanges(tree, scope)
        build()
        var conflicts = 0
        lock.withLock {
            val touched = mutableSetOf<MethodGroup>()
            for (fnsName in scope) {
                stampClass(tree.getClass(inheritance.fns, fnsName) ?: continue, touched)
            }
            for (group in touched) {
                conflicts += resolveWinners(group, clearCandidates = false)
            }
        }
        return conflicts
    }

    /** Stamp [cls]'s member rows — adds each row's namespace columns to
     *  its member's component and records the group in [touched] when
     *  non-null (the scoped pass re-resolves winners on touched groups
     *  only). Callers outside [lock] rely on single-threaded setup. */
    private fun stampClass(cls: ClassMapping, touched: MutableSet<MethodGroup>?) {
        val fnsName = cls.names[inheritance.fns] ?: return
        val info = inheritance.classInfo(fnsName) ?: return
        for (mm in cls.methods) {
            val entry = mm.entries[inheritance.fns] ?: continue
            val desc = entry.descriptor ?: continue
            val mi = info.methods.firstOrNull {
                it.name == entry.name && it.descriptor == desc
            } ?: continue
            resolve(info, mi)
            val group = groups.getOrPut(find(MethodMember(info, mi))) { MethodGroup() }
            touched?.add(group)
            for ((ns, e) in mm.entries) {
                group.candidates.getOrPut(ns) { mutableSetOf() }.add(e.name)
                // genuine rename: the name is distinct for this ns — it
                // doesn't echo a sibling column (fns identity stamps and
                // hop-fills like official `a` copied into `intermediary`)
                if (mm.entries.none { (k, o) -> k != ns && o.name == e.name }) {
                    group.genuineNames.getOrPut(ns) { mutableSetOf() }.add(e.name)
                }
                group.candidateMembers.getOrPut(ns) { mutableMapOf() }.let { m ->
                    if (e.name !in m) m[e.name] = MethodMember(info, mi)
                }
            }
        }
        // fields need no stamping — [fieldChain] + row lookups at query
        // time give down-propagation pull semantics with zero writes
    }

    /** Resolve one group's per-namespace winners from its stamped
     *  candidates — count of disagreements (multi-candidate ns).
     *  [clearCandidates] releases the per-candidate member provenance
     *  — only safe on the final pass (the batch-wide call); scoped
     *  passes may re-resolve after later stamps and keep it. */
    private fun resolveWinners(group: MethodGroup, clearCandidates: Boolean = true): Int {
        var conflicts = 0
        for ((ns, cands) in group.candidates) {
            if (cands.size > 1) conflicts++
            // echo stamps (no-ops, cell completions, hop-fills) only
            // apply when no real rename exists in the component —
            // a completed `a` on an unmapped impl loses to a
            // sibling's actual `m_*`/`f_*` name
            val real = group.genuineNames[ns] ?: emptySet()
            val winner = (real.ifEmpty { cands }).minBy { it.value }
            group.names[ns] = winner
            group.candidateMembers[ns]?.get(winner)?.let { group.origins[ns] = it }
        }
        if (clearCandidates) group.candidateMembers.clear()
        return conflicts
    }

}
