package xyz.wagyourtail.unimined.mapping.inheritance

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.contains
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.tree.impl.ResolvingMappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.isGenuineCellName
import java.nio.file.Path

/**
 * Materialize the remap-time member-group propagation into this tree —
 * writes the same target-namespace member names `uml:remap` would write,
 * as real rows on a [MutableMappingTree].
 *
 * Mechanism: a [LazyASMInheritanceTree] over [productScans] (eager) +
 * [classpathIndex] (lazy, hierarchy-only) feeds a [MemberGroups]
 * component graph stamped by this tree's rows ([MemberGroups.propagate]
 * — the same pass `AsmRemapper.remapGraph` runs). The tree is then
 * wrapped in a [ResolvingMappingTree] — the lazy view whose member
 * lookups answer exactly what a graph remap resolves — and *emitted back
 * into itself*, so propagated names materialize as cells:
 *
 * - rows already present gain cells for missing [targets] columns —
 *   *genuine* explicit cells win ([PropagationStrategy.KEEP_EXPLICIT]
 *   semantics; re-running is a no-op), but verbatim cells whose name
 *   merely echoes a sibling column (the composer `fillMissingNames`
 *   fns→target identity write, hop-fills) yield to the resolved
 *   component name, which stamps over them;
 * - members the scan declares but the tree lacks get synthesized
 *   name-only rows — the propagated-row shape write-time propagation
 *   ([xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree])
 *   materializes;
 * - classes absent from the tree but in [extraClasses] surface as rows
 *   carrying their source-namespace name plus resolved member cells —
 *   the "verbatim" rows a remap needs so references to them don't break;
 * - javac bridge methods unify with the specialized methods they
 *   forward to ([unifyBridges], [MemberGroups.unifyBridges]) — both
 *   members then carry the propagated target name, matching the
 *   ploceus `MappingFiller` bridge/specialized unification in named
 *   namespaces. Detection is flag-based (`ACC_BRIDGE` on the scanned
 *   method) plus the ploceus structural checks (overrides a supertype
 *   signature; the unique same-name sibling's descriptor narrows the
 *   bridge's). Deviation from ploceus: it additionally recovered
 *   *stripped* bridge flags by inspecting method bodies for the
 *   forwarding shape — [ASMInheritanceTree.ScanData] carries no
 *   instructions, so an obfuscator that removed `ACC_BRIDGE` leaves the
 *   pair un-unified.
 *
 * Rules matching the fill family ([fillMissingNames][xyz.wagyourtail.unimined.mapping.tree.impl.fillMissingNames]):
 *
 * - env-qualified columns (`ns:env`) are side-presence markers — [targets]
 *   carrying an env qualifier are skipped, never filled;
 * - [classpathIndex] classes contribute hierarchy edges only — they can
 *   *seed* names into product subclasses but never gain rows;
 * - [fns] is the identity column — rows are keyed by it, never filled.
 *
 * The re-emit drops metadata pushes: the fill only writes name cells, and
 * re-attaching a row's own metadata would duplicate types the attach
 * merge doesn't dedup ([AccessElement][xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement]s,
 * annotations) — this is what keeps a re-run byte-identical. Output is
 * deterministic: rows emit in sorted order. Emission runs with
 * `sort = true` deliberately — the emit materializes each level before
 * pushing, which is what makes writing back into the tree being read safe.
 *
 * **Parameter names.** When a member row *gains* a propagated name in a
 * [targets] column (not genuinely held before the fill — absent or
 * carrying only a verbatim echo — and landing on a non-identity name
 * after), its parameter rows copy that column's param names
 * from the component's name-donor row ([MemberGroups.MethodGroup.origins])
 * — the ploceus `MappingFiller` behavior where a mapped `run(arg0)`
 * seeds `arg0` onto every override in the group. Matching is by
 * declared LVT slot (lvOrd, falling back to parameter index); a cell
 * the override's param row already carries is never overwritten, and
 * param rows are synthesized per declared descriptor slot only where
 * the donor names them. Columns that gained a verbatim/unchanged name
 * (propagated name == the member's own [fns] name) don't trigger the
 * copy — an identity rename has no param names worth propagating.
 *
 * @param fns the source namespace the scan's class/member names are in —
 *  the graph's name space and the column member identity keys on
 * @param targets the columns to fill — must be declared namespaces of
 *  this tree; env-qualified or undeclared targets are skipped
 * @param productScans scans of the jars whose classes may gain rows —
 *  merged in order (later scans shadow duplicate class entries)
 * @param classpathIndex [LazyASMInheritanceTree.index] output —
 *  `class -> jar` for hierarchy-only supertypes; may be empty when no
 *  library classpath applies
 * @param extraClasses source-namespace class names that may surface as
 *  new class rows — defaults to every class in [productScans]; a
 *  class only lands when at least one of its declared members resolves
 *  a [targets] name
 * @param unifyBridges union javac bridge methods with their specialized
 *  targets before propagation — default `true`. Set `false` for strict
 *  signature-keyed propagation (a bridge then only carries names its
 *  own component resolves, and the specialized method gets none).
 *  There is no [PropagationStrategy] parameter: this fill path has a
 *  fixed, implicit strategy — component winners resolve
 *  deterministically (sorted-first among genuine renames) and explicit
 *  cells are never overwritten, the `KEEP_EXPLICIT`-equivalent of a
 *  fill-missing pass rather than a write-path remap
 * @return the number of member-name conflicts propagation resolved
 *  deterministically (see [MemberGroups.propagate])
 */
fun MutableMappingTree.bytecodeFill(
    fns: Namespace,
    targets: Set<Namespace>,
    productScans: List<ASMInheritanceTree.ScanData>,
    classpathIndex: Map<InternalName, Path>,
    extraClasses: Set<InternalName> = productScans.flatMapTo(mutableSetOf()) { it.classes.keys },
    unifyBridges: Boolean = true,
): Int {
    val declared = namespaces.toSet()
    val fillTargets = targets.filterTo(mutableSetOf()) {
        it.splitEnv().second == null && it != fns && it in declared
    }
    if (productScans.isEmpty() || fillTargets.isEmpty() || fns !in declared) return 0
    val mergedScan = productScans.fold(
        ASMInheritanceTree.ScanData(emptyMap()),
        ASMInheritanceTree.ScanData::plus
    )
    val graph = LazyASMInheritanceTree(fns, mergedScan, classpathIndex)
    try {
        val groups = MemberGroups(graph)
        if (unifyBridges) groups.unifyBridges()
        // stamps each row's namespace columns onto its member's component —
        // resolves the per-namespace winners the fill then materializes
        val conflicts = groups.propagate(this)
        // which target columns each member row already carries — the
        // "gained" set the param fill below keys on
        val preFill = snapshotTargetCells(fns, fillTargets)
        // metadata pushes are dropped — the fill writes name cells only;
        // re-attaching a row's own metadata would duplicate types the
        // attach merge doesn't dedup (access elements, annotations)
        val nameCellsOnly = object : MappingSink by this {
            override fun push(meta: MetadataElement): MetadataElement? = null
        }
        ResolvingMappingTree(this, graph, groups, fns, extraClasses)
            .emit(nameCellsOnly, nsFilter = fillTargets + fns, sort = true)
        fillPropagatedParams(groups, graph, fns, fillTargets, preFill)
        return conflicts
    } finally {
        graph.close()
    }
}

/** member-row identity for the pre-fill snapshot — (fns name, desc). */
private typealias MethodSig = Pair<UnqualifiedName, MethodDescriptor>

/**
 * Which [targets] columns each member row *genuinely* carries — keyed by
 * class fns-name then member signature. Snapshotted before the emit so
 * [fillPropagatedParams] can tell propagated cells from pre-existing
 * explicit ones. A column counts as held only when its name echoes no
 * *earlier* column — verbatim fills (the composer's fns→target identity
 * write, hop-fills) aren't held: a resolved name stamping over them is
 * a gain that must trigger param copying, matching the genuine-cell
 * gate [xyz.wagyourtail.unimined.mapping.tree.impl.ResolvingMappingTree]
 * applies when overlaying resolved names.
 */
private fun MutableMappingTree.snapshotTargetCells(
    fns: Namespace,
    targets: Set<Namespace>
): Map<InternalName, Map<MethodSig, Set<Namespace>>> {
    val nsOrder = namespaces.toList()
    val pre = HashMap<InternalName, MutableMap<MethodSig, MutableSet<Namespace>>>()
    for (cls in classes) {
        val cn = cls.names[fns] ?: continue
        for (m in cls.methods) {
            val e = m.entries[fns] ?: continue
            val desc = e.descriptor ?: continue
            val held = m.entries.mapNotNullTo(mutableSetOf()) { (ns, _) ->
                ns.takeIf {
                    it in targets &&
                            isGenuineCellName(nsOrder, m.entries, ns) { c -> c.name }
                }
            }
            pre.getOrPut(cn) { HashMap() }[e.name to desc] = held
        }
    }
    return pre
}

/**
 * Copy param-name cells onto member rows that *gained* a propagated
 * [targets] name — see [bytecodeFill]'s parameter-names section. A
 * column counts as gained when the row had no cell before the emit and
 * the landed name differs from the member's own [fns] name (a verbatim
 * identity fill has nothing to propagate). The donor is the component's
 * name-origin row ([MemberGroups.MethodGroup.origins]) — params copy
 * per declared descriptor slot, matched by lvOrd then parameter index,
 * and only into cells the override's param row doesn't already carry.
 */
private fun MutableMappingTree.fillPropagatedParams(
    groups: MemberGroups,
    graph: InheritanceTree,
    fns: Namespace,
    targets: Set<Namespace>,
    preFill: Map<InternalName, Map<MethodSig, Set<Namespace>>>
) {
    for (cls in classes.toList()) {
        val clsName = cls.names[fns] ?: continue
        val info = graph.classInfo(clsName) ?: continue
        for (m in cls.methods.toList()) {
            val e = m.entries[fns] ?: continue
            val desc = e.descriptor ?: continue
            val mi = info.methods.firstOrNull {
                it.name == e.name && it.descriptor == desc
            } ?: continue
            val held = preFill[clsName]?.get(e.name to desc) ?: emptySet()
            val gained = targets.filter { t ->
                val entry = m.entries[t]
                entry != null && t !in held && entry.name != e.name
            }
            if (gained.isEmpty()) continue
            val group = groups.methodGroup(info, mi)
            val method = clazz(cls.names).method(m.entries)
            for (t in gained) {
                val donor = group.origins[t] ?: continue
                if (donor.cls === info && donor.info == mi) continue
                val donorParams = with(this) {
                    getClass(fns, donor.cls.name)
                        ?.getMethod(fns, donor.info.name, donor.info.descriptor)
                }?.parameters?.filter { it.names[t] != null } ?: continue
                var lvt = if (AccessFlag.STATIC in mi.access) 0 else 1
                for ((i, p) in mi.descriptor.getParts().second.withIndex()) {
                    val donorP = donorParams.firstOrNull {
                        it.lvOrd == lvt || (it.lvOrd == null && it.index == i)
                    }
                    if (donorP != null) {
                        val target = method.parameter(i, lvt, emptyMap())
                        if (target.names[t] == null) {
                            target.setNames(mapOf(t to donorP.names.getValue(t)))
                        }
                    }
                    lvt += p.value.getWidth()
                }
            }
        }
    }
}
