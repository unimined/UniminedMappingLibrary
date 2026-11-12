package xyz.wagyourtail.unimined.mapping.tree.impl

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import xyz.wagyourtail.unimined.mapping.GraphNamespaceRemapper
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.NamespaceRemapper
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.LocalVariableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.ParameterMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.WildcardMapping
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.inheritance.HasInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper

/**
 * Read-only [MappingTree] facade over shared, already-propagated remap state
 * — the lazy counterpart of [InheritanceBackedMappingTree].
 *
 * Where IBT re-emits every row of the source tree through propagating
 * factories into private storage (an O(rows) pass per consumer), this view
 * wraps the merged tree directly and answers member lookups by *resolving*
 * through the same [InheritanceTree] + [MemberGroups] component graph a
 * graph remap used — so a lookup returns the name the remap actually wrote,
 * with no row materialization at all.
 *
 * Semantics vs IBT (the model this reproduces):
 * - Propagated member rows exist only on classes that *declare* the member
 *   (`propagateDown` BFSes children and upserts only where
 *   `child.members.any { name+desc }`); a class that merely inherits shows
 *   no row. Synthesis here follows the same rule: a row materializes iff
 *   the class's scanned [InheritanceTree.ClassInfo] declares the member
 *   AND [GraphNamespaceRemapper.resolveMethodName]/[resolveFieldName]
 *   returns non-null — the pre-fallback predicate IBT's `setNames` fired on.
 * - Synthesized rows carry name-only target entries and no metadata,
 *   matching IBT's `setNames`-only writes onto upserted rows.
 * - [classes] additionally yields synthesized class rows for [extraClasses]
 *   (e.g. a remap input's own scan) that have at least one synthesized
 *   member — IBT class rows created as propagation targets.
 *
 * Known intentional divergences (documented, not bugs):
 * - `getMethod`/`getField` in a non-source namespace sees explicit rows
 *   only — member rows are keyed by source-namespace identity and lazy
 *   resolution runs source→target, so target-keyed member queries skip
 *   synthesized rows. Extension lookups all key on the source namespace.
 * - `remapper(from, to)` is graph-backed only when `from` is the source
 *   namespace — hierarchy edges are stored in source names, so other
 *   directions delegate to the flat tree remapper (mirroring what
 *   `GraphNamespaceRemapper` itself degrades to off-axis).
 * - Bulk `methods`/`fields` iteration cost is O(declared members) per class
 *   — resolutions memoize inside the per-namespace
 *   [GraphNamespaceRemapper]s, so repeated walks amortize.
 *
 * Thread-safety: read-only over shared state — safe for concurrent lookups
 * under the same contract as [MemberGroups]/[GraphNamespaceRemapper]
 * (resolution writes memoize under locks; the delegate is never mutated).
 * Single-writer mutation is *not* supported — this is not a
 * [MutableMappingTree].
 *
 * @param delegate the shared merged tree supplying explicit rows, class
 *   rows, constant groups, packages and namespaces
 * @param inheritance the (shared) inheritance graph member resolution walks
 * — e.g. a `RemapBatch`'s union classpath graph; may grow via
 *   `LazyASMInheritanceTree.mergeScan` while readers hold this view
 * @param groups the propagated member-component graph over [inheritance]
 * @param fns the source namespace — the graph's name space and the identity
 *   column member lookups key on
 * @param extraClasses source-namespace class names absent from [delegate]
 *   that should still resolve — typically a remap input's own scan
 */
class ResolvingMappingTree(
    private val delegate: MappingTree,
    override val inheritance: InheritanceTree,
    private val groups: MemberGroups,
    private val fns: Namespace,
    private val extraClasses: Set<InternalName> = emptySet(),
) : MappingTree by delegate, HasInheritanceTree {

    private val resolversLock = ReentrantLock()
    private val resolvers = HashMap<Namespace, GraphNamespaceRemapper>()

    /**
     * source→[to] member resolver over the shared graph — the
     * [GraphNamespaceRemapper] whose `map*` answers equal the names a
     * batch remap wrote (component names → declaring-row explicit → flat).
     */
    fun remapperFor(to: Namespace): GraphNamespaceRemapper = resolversLock.withLock {
        resolvers.getOrPut(to) {
            GraphNamespaceRemapper(delegate, inheritance, groups, fns, to)
        }
    }

    /**
     * `(from, to)`-bound remapper — graph-backed when [from] is the source
     * namespace, flat delegate lookups otherwise (hierarchy edges are keyed
     * in source names; a non-source `from` has no edges to walk — the same
     * place [GraphNamespaceRemapper] itself ends up). Member shadows the
     * `MappingTree.remapper` extension so `ExtensionContext` sees the
     * graph-backed behavior on this concrete type.
     */
    fun remapper(from: Namespace, to: Namespace): NamespaceRemapper {
        checkNamespace(from)
        checkNamespace(to)
        return if (from == fns) remapperFor(to) else delegate.remapper(from, to)
    }

    override fun getClass(namespace: Namespace, name: InternalName): ClassMapping? {
        delegate.getClass(namespace, name)?.let { return ResolvingClass(it) }
        if (namespace != fns || name !in extraClasses) return null
        val info = inheritance.classInfo(name) ?: return null
        return ResolvingClass(null, info)
    }

    override val classes: Iterable<ClassMapping>
        get() = delegate.classes.map { ResolvingClass(it) } +
            extraClasses.asSequence()
                .filter { delegate.getClass(fns, it) == null }
                .mapNotNull { n -> inheritance.classInfo(n)?.let { i -> ResolvingClass(null, i) } }
                .filter { it.methods.any() || it.fields.any() }
                .toList()

    private fun infoFor(base: ClassMapping?): InheritanceTree.ClassInfo? =
        base?.names?.get(fns)?.let { inheritance.classInfo(it) }

    /**
     * A class row — either a wrapper over an explicit [delegate] row, or a
     * synthesized row for a scan-only class ([extraClasses], e.g. an input
     * jar's own classes). Member iterables append *synthesized* rows for
     * declared members a row doesn't already cover — the lazy form of the
     * propagated rows IBT materializes.
     */
    private inner class ResolvingClass(
        private val base: ClassMapping?,
        private val info: InheritanceTree.ClassInfo? = null,
    ) : ClassMapping {

        /** scan entry for this class — explicit [info] for synthesized
         *  (scan-only) classes, resolved from [base]'s source name for
         *  delegate rows; `null` when neither source has it (mapping rows
         *  for classes the jars don't carry — nothing to synthesize). */
        private val resolvedInfo: InheritanceTree.ClassInfo? by lazy {
            info ?: infoFor(base)
        }

        override val names: Map<Namespace, InternalName>
            get() = base?.names
                ?: resolvedInfo?.name?.let { mapOf(fns to it) }
                ?: emptyMap()

        override val metadata: Iterable<MetadataElement>
            get() = base?.metadata ?: emptyList()

        override val wildcards: Iterable<WildcardMapping>
            get() = base?.wildcards ?: emptyList()

        /** the source-namespace owner name member resolution keys on —
         *  the scan's canonical name, else the row's own source entry */
        private fun ownerName(): InternalName? =
            resolvedInfo?.name ?: base?.names?.get(fns)

        override val methods: Iterable<MethodMapping>
            get() {
                val owner = ownerName()
                val own = base?.methods?.map { ResolvingMethod(it, owner) } ?: emptyList()
                val info = resolvedInfo ?: return own
                val covered = own.mapNotNullTo(HashSet()) { m ->
                    m.entries[fns]?.let { it.name to it.descriptor }
                }
                return own + info.methods.asSequence()
                    .filter { mi -> (mi.name to mi.descriptor) !in covered }
                    .mapNotNull { mi -> methodEntries(info.name, mi)?.let(::SynthesizedMethod) }
                    .asIterable()
            }

        override val fields: Iterable<FieldMapping>
            get() {
                val owner = ownerName()
                val own = base?.fields?.map { ResolvingField(it, owner) } ?: emptyList()
                val info = resolvedInfo ?: return own
                val covered = own.mapNotNullTo(HashSet()) { f ->
                    f.entries[fns]?.let { it.name to it.descriptor }
                }
                return own + info.fields.asSequence()
                    .filter { fi -> (fi.name to fi.descriptor) !in covered }
                    .mapNotNull { fi -> fieldEntries(info.name, fi)?.let(::SynthesizedField) }
                    .asIterable()
            }

        /** the propagated-member entries IBT would have materialized for
         *  this declared member — `null` when no namespace resolves a name
         *  (the "no propagated row" case). fns carries the member's own
         *  name+desc; other columns are name-only, like `setNames` writes. */
        private fun methodEntries(
            owner: InternalName,
            mi: InheritanceTree.MethodInfo,
        ): Map<Namespace, MethodNameAndDescriptor>? {
            var entries: MutableMap<Namespace, MethodNameAndDescriptor>? = null
            for (ns in namespaces) {
                if (ns == fns) continue
                val resolved = remapperFor(ns).resolveMethodName(owner, mi.name, mi.descriptor)
                    ?: continue
                (entries ?: HashMap<Namespace, MethodNameAndDescriptor>(4).also { entries = it })[ns] =
                    MethodNameAndDescriptor.unchecked(resolved.value)
            }
            return entries?.apply {
                put(fns, MethodNameAndDescriptor.unchecked("${mi.name.value};${mi.descriptor.value}"))
            }
        }

        private fun fieldEntries(
            owner: InternalName,
            fi: InheritanceTree.FieldInfo,
        ): Map<Namespace, FieldNameAndDescriptor>? {
            var entries: MutableMap<Namespace, FieldNameAndDescriptor>? = null
            for (ns in namespaces) {
                if (ns == fns) continue
                val resolved = remapperFor(ns).resolveFieldName(owner, fi.name, fi.descriptor)
                    ?: continue
                (entries ?: HashMap<Namespace, FieldNameAndDescriptor>(4).also { entries = it })[ns] =
                    FieldNameAndDescriptor.unchecked(resolved.value)
            }
            return entries?.apply {
                put(fns, FieldNameAndDescriptor.unchecked("${fi.name.value};${fi.descriptor.value}"))
            }
        }

    }

    /** an explicit row with propagated names filling its empty cells —
     *  the read-side equivalent of IBT's `applyPropagated` under
     *  KEEP_EXPLICIT (explicit entries win; propagated names only fill
     *  cells the row lacks). Everything except [entries] delegates. */
    private inner class ResolvingMethod(
        base: MethodMapping,
        private val owner: InternalName?,
    ) : MethodMapping by base {

        private val baseEntries = base.entries

        override val entries: Map<Namespace, MethodNameAndDescriptor> by lazy {
            val id = baseEntries[fns]?.takeIf { it.descriptor != null }
            buildMap {
                putAll(baseEntries)
                if (id != null && owner != null) {
                    for (ns in namespaces) {
                        if (ns == fns || ns in baseEntries) continue
                        remapperFor(ns).resolveMethodName(owner, id.name, id.descriptor!!)
                            ?.let { put(ns, MethodNameAndDescriptor.unchecked(it.value)) }
                    }
                }
            }
        }

        // entries-derived accessors — `by base` would read the base's
        // (unfilled) entries, so they re-derive from the merged map
        override val names: Map<Namespace, UnqualifiedName>
            get() = entries.mapValues { it.value.name }
        override val descs: Map<Namespace, MethodDescriptor>
            get() = entries.mapNotNull { (ns, e) -> e.descriptor?.let { ns to it } }.toMap()
        override fun hasDescriptor() = entries.values.any { it.descriptor != null }

    }

    private inner class ResolvingField(
        base: FieldMapping,
        private val owner: InternalName?,
    ) : FieldMapping by base {

        private val baseEntries = base.entries

        override val entries: Map<Namespace, FieldNameAndDescriptor> by lazy {
            val id = baseEntries[fns]?.takeIf { it.descriptor != null }
            buildMap {
                putAll(baseEntries)
                if (id != null && owner != null) {
                    for (ns in namespaces) {
                        if (ns == fns || ns in baseEntries) continue
                        remapperFor(ns).resolveFieldName(owner, id.name, id.descriptor!!)
                            ?.let { put(ns, FieldNameAndDescriptor.unchecked(it.value)) }
                    }
                }
            }
        }

        override val names: Map<Namespace, UnqualifiedName>
            get() = entries.mapValues { it.value.name }
        override val descs: Map<Namespace, FieldDescriptor>
            get() = entries.mapNotNull { (ns, e) -> e.descriptor?.let { ns to it } }.toMap()
        override fun hasDescriptor() = entries.values.any { it.descriptor != null }

    }

    /** name-only member entries + empty children — the exact shape IBT's
     *  `setNames`-propagated rows have (no metadata, params or lvars). */
    private class SynthesizedMethod(
        override val entries: Map<Namespace, MethodNameAndDescriptor>
    ) : MethodMapping {
        override val parameters: Iterable<ParameterMapping> get() = emptyList()
        override val localVariables: Iterable<LocalVariableMapping> get() = emptyList()
    }

    private class SynthesizedField(
        override val entries: Map<Namespace, FieldNameAndDescriptor>
    ) : FieldMapping

}
