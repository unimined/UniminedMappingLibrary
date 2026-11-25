package xyz.wagyourtail.unimined.mapping.remapper

import xyz.wagyourtail.unimined.mapping.GraphNamespaceRemapper
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.HasInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.NamespaceRemapper
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.ResolvingMappingTree

/**
 * Per-remap view handed to [AsmExtension] instances — the mapping tree plus
 * ready-made [xyz.wagyourtail.unimined.mapping.NamespaceRemapper]s in both
 * directions. Most extensions need member lookups beyond a flat rename (e.g.
 * resolving mixin `target`/`method` annotation values against the tree), so
 * both directions are provided.
 */
class ExtensionContext(
    /** the (already overlaid + propagated) mapping tree in effect */
    val tree: MappingTree,
    /** the input jar's namespace */
    val fns: Namespace,
    /** the output jar's namespace */
    val targetNs: Namespace,
    /** the active remap options — builtin stages read their flags here */
    val options: xyz.wagyourtail.unimined.mapping.remapper.asm.Options = xyz.wagyourtail.unimined.mapping.remapper.asm.Options(),
) {

    /** `"owner.name"` → (ConstantValue, descriptor) of static fields in the
     *  input jar — populated by the pipeline before class transforms run,
     *  consumed by constant-group unpick ([ConstantGroupEmitter]) */
    var fieldValues: Map<String, Pair<Any, String>> = emptyMap()

    /** resolve the member row that supplied a method's propagated name —
     *  param/lvar and other member children live on the originating row.
     *  Input is the method's fns identity. Set by the pipeline when
     *  inheritance propagation is in effect (graph remapping); null for
     *  flat remaps. */
    var methodOrigin: ((owner: InternalName, name: UnqualifiedName, desc: MethodDescriptor) -> xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping?)? =
        null

    /**
     * source → target member/type remapper. Graph-backed when the tree is
     * [InheritanceBackedMappingTree] — member refs on subclasses resolve to
     * the declaring ancestor's row (`Loq;q:D` → Entity's `field_70163_u`),
     * which flat lookups miss: write-time propagation only reaches
     * descendants the scan already materialized.
     */
    val remapper: NamespaceRemapper = bind(fns, targetNs)

    /** target → source — for resolving names written in the target space */
    val reverse: NamespaceRemapper = bind(targetNs, fns)

    private fun bind(from: Namespace, to: Namespace): NamespaceRemapper =
        when (tree) {
            // ResolvingMappingTree carries its own graph-backed resolvers —
            // member names resolve through the shared MemberGroups the
            // producing remap used, so lookups match what was written
            is ResolvingMappingTree -> tree.remapper(from, to)
            is InheritanceBackedMappingTree ->
                GraphNamespaceRemapper(tree, tree.inheritance, MemberGroups(tree.inheritance), from, to)
            else -> tree.remapper(from, to)
        }

    /** map a class internal name source → target (`base/Service` → `b`) */
    fun mapClass(internalName: String): String = remapper.map(InternalName.unchecked(internalName)).value

    /** map a method name source → target; [desc] disambiguates overloads, null
     *  for desc-less lookup */
    fun mapMethod(owner: String, name: String, desc: String? = null): String = remapper
        .mapMethod(InternalName.unchecked(owner), UnqualifiedName.unchecked(name), desc?.let(MethodDescriptor::unchecked)).value

    /** map a field name source → target; [desc] may be null for desc-less lookup */
    fun mapField(owner: String, name: String, desc: String? = null): String = remapper
        .mapField(InternalName.unchecked(owner), UnqualifiedName.unchecked(name), desc?.let(FieldDescriptor::unchecked)).value

    /** remap the class refs inside a raw descriptor string ([isField] selects
     *  field vs method descriptor parsing) */
    fun mapDesc(desc: String, isField: Boolean): String =
        if (isField) remapper.map(FieldDescriptor.unchecked(desc)).toString()
        else remapper.map(MethodDescriptor.unchecked(desc)).toString()

    /** source-space desc for a desc read off an already-remapped (targetNs)
     *  node — member lookups against the tree key on source-side descs */
    fun reverseDesc(desc: String, isField: Boolean): String =
        if (isField) reverse.map(FieldDescriptor.unchecked(desc)).toString()
        else reverse.map(MethodDescriptor.unchecked(desc)).toString()

    /** number of member rows matching a desc-less lookup — `>1` means a
     *  name-only ref is ambiguous */
    fun memberCandidateCount(owner: String, name: String, isField: Boolean): Int {
        val cls = tree.getClass(fns, InternalName.unchecked(owner)) ?: return 0
        val n = UnqualifiedName.unchecked(name)
        return with(tree) {
            if (isField) cls.fields.count { it.names[fns] == n }
            else cls.getMethods(fns, n, null).size
        }
    }

    /** outcome of a desc-less member lookup — [sourceDesc] is the unique
     *  candidate's descriptor in *source* space when one could be determined */
    internal data class DesclessMemberLookup(
        val found: Boolean,
        val ambiguous: Boolean,
        val sourceDesc: String?,
    )

    /** desc-less member lookup with the same boundary rule as the graph
     *  remapper: the nearest declaring class in the hierarchy supplies the
     *  candidates; declared members outrank synthetic/bridge companions.
     *  Real overloads are [DesclessMemberLookup.ambiguous] rather than an
     *  order-dependent first match. The bytecode scan is authoritative when
     *  it sees a candidate; mapping rows are the fallback for unscanned
     *  classes (and carry stale-row noise, hence the same ambiguity rule). */
    internal fun lookupDesclessMember(owner: String, name: String, isField: Boolean): DesclessMemberLookup {
        scannedDesclessMember(owner, name, isField)?.let { return it }
        val cls = tree.getClass(fns, InternalName.unchecked(owner)) ?: return DesclessMemberLookup(false, false, null)
        val n = UnqualifiedName.unchecked(name)
        if (isField) {
            val matches = cls.fields.filter { it.names[fns] == n }
            if (matches.isEmpty()) return DesclessMemberLookup(false, false, null)
            if (matches.size > 1) return DesclessMemberLookup(true, true, null)
            return DesclessMemberLookup(true, false,
                with(tree) { matches.single().getDescriptor(fns) }?.toString())
        }
        val matches = with(tree) { cls.getMethods(fns, n, null) }
        if (matches.isEmpty()) return DesclessMemberLookup(false, false, null)
        if (matches.size > 1) return DesclessMemberLookup(true, true, null)
        return DesclessMemberLookup(true, false,
            with(tree) { matches.single().getDescriptor(fns) }?.toString())
    }

    /** scan-backed half of [lookupDesclessMember] — null means the scan
     *  either does not cover [owner] or has no candidate at the declaring
     *  boundary, so the mapping-tree fallback remains eligible */
    private fun scannedDesclessMember(owner: String, name: String, isField: Boolean): DesclessMemberLookup? {
        val inheritance = (tree as? HasInheritanceTree)?.inheritance ?: return null
        val start = inheritance.classInfo(InternalName.unchecked(owner)) ?: return null
        val visited = mutableSetOf<InternalName>()
        val queue = ArrayDeque<xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree.ClassInfo>()
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!visited.add(c.name)) continue
            if (isField) {
                val matches = c.fields.filter { it.name.value == name }
                // no bridge bit on fields — 0x40 is VOLATILE there
                val preferred = matches.filter { !it.isSynthetic }
                val candidates = preferred.ifEmpty { matches }
                if (candidates.isNotEmpty()) {
                    return if (candidates.size == 1) {
                        DesclessMemberLookup(true, false, candidates.single().descriptor.toString())
                    } else {
                        DesclessMemberLookup(true, true, null)
                    }
                }
            } else {
                val matches = c.methods.filter { it.name.value == name }
                // declared members rank ahead of synthetic/bridge — the AP's
                // element model never sees synthetics, so a name-only ref
                // canonically resolves to the declaration; all-synthetic
                // matches (lambdas) still resolve when nothing else exists
                val preferred = matches.filter { !it.isSynthetic && !it.isBridge }
                val candidates = preferred.ifEmpty { matches }
                if (candidates.isNotEmpty()) {
                    return if (candidates.size == 1) {
                        DesclessMemberLookup(true, false, candidates.single().descriptor.toString())
                    } else {
                        DesclessMemberLookup(true, true, null)
                    }
                }
            }
            c.superClass?.let { queue.add(it) }
            queue.addAll(c.interfaceClasses)
        }
        return null
    }

    /** the resolved member's *source* desc remapped to target space — used to
     *  render canonical refs when the written ref was desc-less. Desc-less
     *  overloads deliberately have no answer: picking the first row would
     *  encode an arbitrary member into the output ref. */
    fun resolveMemberDesc(owner: String, name: String, isField: Boolean): String? {
        val lookup = lookupDesclessMember(owner, name, isField)
        if (!lookup.found || lookup.ambiguous) return null
        return lookup.sourceDesc?.let { mapDesc(it, isField) }
    }

}

/**
 * A remap-time extension hook. Get one via [AsmExtensionFactory] — a fresh
 * instance per [AsmRemapper.remap] call, so per-jar state just lives in fields.
 *
 * Lifecycle per remap:
 *  1. [analyze] + [analyzeResource] — once per input class (raw, pre-remap
 *     node) / once per input resource — collect state across the whole jar
 *     (mixin targets, refmap entries, configs…)
 *  2. [finishAnalysis] — after every input entry was analyzed
 *  3. [postApply] — per output class, after `ClassRemapper` + `MetadataAdapter`
 *     — the node carries targetNs names; mutate freely (annotation rewrites,
 *     extra methods, …)
 *  4. [transformResource] — per resource, after the standard manifest /
 *     services / access-file fixups — replace contents (merge a stale refmap…)
 *  5. [resources] — extra jar entries to append (refmap JSON, configs…)
 */
