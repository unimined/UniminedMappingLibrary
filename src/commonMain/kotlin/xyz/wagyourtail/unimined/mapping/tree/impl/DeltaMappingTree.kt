package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.SyntheticBridgeElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SyntheticBridgeElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * A writable overlay over a shared, read-only [base] tree.
 *
 * The tree presents `delta ∪ base` as one [MutableMappingTree]: reads fall
 * through to [base] wherever the private [delta] store has no matching row,
 * and every write lands in [delta] — [base] is never mutated through this
 * façade. Construction is O(1) no matter how large [base] is.
 *
 * ## Why this exists
 *
 * [InheritanceBackedMappingTree] (and any propagating accessor built like it)
 * mutates its delegate while *reading* — inherited member names are written
 * into the delegate lazily at lookup time (`pullDown`/`propagateDown`). That
 * forces each consumer to own a fully materialized copy of the mapping tree:
 * `view.emit(MutableMappingTree(...))` re-emits every row per invocation
 * (~O(rows) each — for a merged production mapping view this is the dominant
 * cost of a per-jar pass).
 *
 * With a delta, the expensive merged tree is emitted **once**, shared as the
 * immutable [base], and each invocation wraps it in a fresh
 * `DeltaMappingTree`. Propagated names and any other writes accumulate in
 * that invocation's small delta; the next invocation starts empty again.
 *
 * ## Overlay semantics
 *
 * * **Rows** — class/member rows in delta *bind* to base rows by identity
 *   (a class binds a base class when they share a name in some namespace;
 *   a member binds when it shares a `name;desc` entry in some namespace).
 *   Reads of a bound pair merge per-namespace, delta winning on conflict —
 *   `entries`/`names`/`descs` report `base + delta`.
 * * **Lists** — `classes`, `methods`, `fields`, `wildcards`, `parameters`,
 *   `localVariables`, `packages`, `constantGroups` iterate base rows in base
 *   order (each wrapped as a bound-or-lazy view), then delta-only rows in
 *   write order. A base row claimed by a delta row appears once.
 * * **Writes** — `clazz`/`method`/`field`/`wildcard`/`parameter`/
 *   `localVariable`/`package_`/`constantGroup` get-or-create the delta node
 *   (using the underlying upsert rules of [MappingTreeData]) and bind it to
 *   the matching base row. New class nodes are seeded with the bound base
 *   row's *complete* name set so any later probe by any of its names — from
 *   `getClass`, `push`, or another `clazz` call — resolves to the same delta
 *   node rather than forking a second row over one base class.
 * * **Lazy materialization** — rows obtained by *iterating* the tree (rather
 *   than through a factory) are views that hold no delta node; the delta
 *   node is allocated on first mutation and seeded from the base row. Member
 *   scans such as [MappingTree.getMethod] therefore stay O(members-in-class),
 *   never materializing stub rows for untouched members.
 * * **Metadata** — at materialization the base row's metadata list is copied
 *   into the delta node; [SyntheticBridgeElementData] is deep-copied because
 *   bridge bookkeeping mutates its `names` map in place. Other metadata
 *   elements are treated as immutable and shared by reference. On an
 *   unmaterialized view, `metadata` returns a copy of the base list (deep
 *   copies again for bridge elements) so reads never leak a mutable handle
 *   into [base].
 * * **Sink** — `push`/`pop`/`header`/`end` delegate straight to the delta
 *   store, so `source.emit(this)` streams rows into the delta with ordinary
 *   merge semantics; binding resolves lazily on subsequent reads.
 *
 * ## What it allows
 *
 * Share one merged mapping tree across many independent remap invocations —
 * sequentially *or* on parallel transform workers — where each invocation
 * needs its own propagation side-effects. The base must not be written by
 * any other handle for the duration (the façade cannot defend against an
 * outside writer mutating [base] mid-read).
 *
 * ## Thread-safety
 *
 * Same profile as [MappingTreeData]: single-writer use is assumed (sink
 * state and upserts are not internally synchronized); concurrent readers
 * share the underlying store's lazy-index caveat. Give each concurrent
 * invocation its own `DeltaMappingTree` over the shared [base].
 */
class DeltaMappingTree(
    /** the shared, read-only layer — must not be mutated externally while in use */
    val base: MappingTree,
) : MutableMappingTree {

    /** private writable layer — all writes land here */
    private val delta: MappingTreeData =
        MutableMappingTree(base.namespaces) as MappingTreeData

    private val baseNs by lazy { base.namespaces.toList() }

    override val namespaces: Iterable<Namespace>
        get() = baseNs + delta.namespaces.filter { it !in baseNs }

    // ---------- overlay machinery ----------

    /**
     * merge a delta member list over a base member list: base order is
     * preserved (bound pairs via [wrapBound], or lazy views when `d == null`),
     * then unclaimed delta rows in write order via [wrapDeltaOnly]. A delta
     * row may claim at most one base row and vice versa.
     */
    private inline fun <D, B, R : MappingElement> overlay(
        base: List<B>,
        delta: List<D>,
        crossinline matches: (D, B) -> Boolean,
        crossinline wrapBound: (D?, B) -> R,
        crossinline wrapDeltaOnly: (D) -> R,
    ): List<R> {
        val claimed = BooleanArray(delta.size)
        val out = ArrayList<R>(base.size + delta.size)
        for (b in base) {
            val idx = delta.indexOfFirst { matches(it, b) }
            if (idx >= 0) {
                claimed[idx] = true
                out += wrapBound(delta[idx], b)
            } else {
                out += wrapBound(null, b)
            }
        }
        delta.forEachIndexed { i, d -> if (!claimed[i]) out += wrapDeltaOnly(d) }
        return out
    }

    /**
     * copy a metadata element for delta seeding — only
     * [SyntheticBridgeElement] needs a deep copy (bridge bookkeeping mutates
     * `names` in place); the rest are treated as immutable and shared.
     */
    private fun copyMetadata(el: MetadataElement): MetadataElement = when (el) {
        is SyntheticBridgeElement -> SyntheticBridgeElementData(el.names.toMutableMap())
        else -> el
    }

    /** the base class a delta node is bound to — first base row sharing any of its names */
    private fun classBaseOf(node: ClassMapping): ClassMapping? =
        node.names.entries.firstNotNullOfOrNull { (ns, n) -> base.getClass(ns, n) }

    /** member identity: rows that share a name+desc entry in some namespace are the same member */
    private fun <T> entriesMatch(a: Map<Namespace, T>, b: Map<Namespace, T>): Boolean =
        a.any { (ns, e) -> b[ns] == e }

    // ---------- tree surface ----------

    override val packages: Iterable<PackageMapping>
        get() = overlay(base.packages.toList(), delta.packages.toList(),
            matches = { d, b -> d.names.any { (ns, n) -> b.names[ns] == n } },
            wrapBound = { d, b -> DeltaPackage(d, b) },
            wrapDeltaOnly = { DeltaPackage(it, null) })

    override val classes: Iterable<ClassMapping>
        get() = overlay(base.classes.toList(), delta.classes.toList(),
            matches = { d, b -> d.names.any { (ns, n) -> b.names[ns] == n } },
            wrapBound = { d, b -> DeltaClass(d, b) },
            wrapDeltaOnly = { DeltaClass(it, null) })

    override val constantGroups: Iterable<ConstantGroupMapping>
        get() = overlay(base.constantGroups.toList(), delta.constantGroups.toList(),
            matches = { d, b -> d.type == b.type && d.name == b.name && d.baseNs == b.baseNs },
            wrapBound = { d, b -> DeltaConstantGroup(d, b) },
            wrapDeltaOnly = { DeltaConstantGroup(it, null) })

    override fun getClass(namespace: Namespace, name: InternalName): ClassMapping? {
        // delta claims the query if it has a node indexed under this name —
        // bound nodes are seeded with their base row's full names, so a hit
        // here is authoritative
        delta.getClass(namespace, name)?.let { return DeltaClass(it, classBaseOf(it)) }
        val b = base.getClass(namespace, name) ?: return null
        // propagation may have written to this class under another ns-name;
        // surface that delta node so its writes merge into reads
        val bound = b.names.entries.firstNotNullOfOrNull { (ns, n) -> delta.getClassExact(ns, n) }
        return DeltaClass(bound, b)
    }

    override fun addNamespaces(namespaces: Iterable<Namespace>) = delta.addNamespaces(namespaces)

    override fun package_(names: Map<Namespace, PackageName>): MutablePackageMapping {
        val b = base.packages.firstOrNull { bm -> names.any { (ns, n) -> bm.names[ns] == n } }
        return DeltaPackage(delta.package_(if (b != null) b.names + names else names), b)
    }

    override fun clazz(names: Map<Namespace, InternalName>): MutableClassMapping {
        val b = names.entries.firstNotNullOfOrNull { (ns, n) -> base.getClass(ns, n) }
        // seed the full base name set — every future probe by any of this
        // class's names must resolve to this same delta node
        return DeltaClass(delta.clazz(if (b != null) b.names + names else names), b)
    }

    override fun constantGroup(
        type: InlineType, name: String?, baseNs: Namespace
    ): MutableConstantGroupMapping = DeltaConstantGroup(
        delta.constantGroup(type, name, baseNs),
        base.constantGroups.firstOrNull { it.type == type && it.name == name && it.baseNs == baseNs })

    // sink path — the delta store owns stack/merge bookkeeping; bindings
    // resolve lazily when the pushed rows are read back out
    override fun header(namespaces: List<Namespace>) = delta.header(namespaces)
    override fun push(element: MappingElement): MappingElement? = delta.push(element)
    override fun push(meta: MetadataElement): MetadataElement? = delta.push(meta)
    override fun pop(element: MappingElement) = delta.pop(element)
    override fun pop(meta: MetadataElement) = delta.pop(meta)
    override fun end() = delta.end()

    override fun toString() = "DeltaMappingTree(base=$base)"

    // ---------- element wrappers ----------

    private inner class DeltaPackage(
        private var d: MutablePackageMapping?,
        private val b: PackageMapping?,
    ) : MutablePackageMapping {

        private fun d(): MutablePackageMapping = d ?: run {
            val node = delta.package_(b!!.names)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override val names: Map<Namespace, PackageName>
            get() = (b?.names ?: emptyMap()) + (d?.names ?: emptyMap())

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override fun setNames(names: Map<Namespace, PackageName>) = d().setNames(names)
        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

    private inner class DeltaClass(
        private var d: MutableClassMapping?,
        private val b: ClassMapping?,
    ) : MutableClassMapping {

        /** materialize the delta node (seeding names + metadata from base) on first write */
        fun d(): MutableClassMapping = d ?: run {
            val node = delta.clazz(b!!.names)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override val names: Map<Namespace, InternalName>
            get() = (b?.names ?: emptyMap()) + (d?.names ?: emptyMap())

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override val methods: Iterable<MethodMapping>
            get() = overlay(b?.methods?.toList() ?: emptyList(), d?.methods?.filterIsInstance<MutableMethodMapping>() ?: emptyList(),
                matches = { d, b -> entriesMatch(d.entries, b.entries) },
                wrapBound = { d, b -> DeltaMethod(d, b, this) },
                wrapDeltaOnly = { DeltaMethod(it, null, this) })

        override val fields: Iterable<FieldMapping>
            get() = overlay(b?.fields?.toList() ?: emptyList(), d?.fields?.filterIsInstance<MutableFieldMapping>() ?: emptyList(),
                matches = { d, b -> entriesMatch(d.entries, b.entries) },
                wrapBound = { d, b -> DeltaField(d, b, this) },
                wrapDeltaOnly = { DeltaField(it, null, this) })

        override val wildcards: Iterable<WildcardMapping>
            get() = overlay(b?.wildcards?.toList() ?: emptyList(), d?.wildcards?.filterIsInstance<MutableWildcardMapping>() ?: emptyList(),
                matches = { d, b -> d.type == b.type && entriesMatch(d.descs, b.descs) },
                wrapBound = { d, b -> DeltaWildcard(d, b, this) },
                wrapDeltaOnly = { DeltaWildcard(it, null, this) })

        override fun setNames(names: Map<Namespace, InternalName>) = d().setNames(names)

        override fun method(entries: Map<Namespace, MethodNameAndDescriptor>): MutableMethodMapping {
            val node = d().method(entries)
            return DeltaMethod(node, b?.methods?.firstOrNull { entriesMatch(node.entries, it.entries) }, this)
        }

        override fun field(entries: Map<Namespace, FieldNameAndDescriptor>): MutableFieldMapping {
            val node = d().field(entries)
            return DeltaField(node, b?.fields?.firstOrNull { entriesMatch(node.entries, it.entries) }, this)
        }

        override fun wildcard(
            type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>
        ): MutableWildcardMapping {
            val node = d().wildcard(type, descs)
            return DeltaWildcard(node,
                b?.wildcards?.firstOrNull { it.type == type && entriesMatch(node.descs, it.descs) }, this)
        }

        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

    /** owners that can lazily materialize their own backing delta node —
     *  parameter/local-variable wrappers use it to attach to the real node,
     *  not another wrapper */
    private interface DeltaInvokable {
        fun materializedOwner(): MutableInvokableMapping
    }

    private inner class DeltaMethod(
        private var d: MutableMethodMapping?,
        private val b: MethodMapping?,
        private val owner: DeltaClass,
    ) : MutableMethodMapping, DeltaInvokable {

        fun d(): MutableMethodMapping = d ?: run {
            val node = owner.d().method(b!!.entries)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override val entries: Map<Namespace, MethodNameAndDescriptor>
            get() = (b?.entries ?: emptyMap()) + (d?.entries ?: emptyMap())

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override val parameters: Iterable<ParameterMapping>
            get() = overlay(b?.parameters?.toList() ?: emptyList(), d?.parameters?.filterIsInstance<MutableParameterMapping>() ?: emptyList(),
                matches = { d, b -> d.index == b.index && d.lvOrd == b.lvOrd },
                wrapBound = { d, b -> DeltaParameter(d, b, this) },
                wrapDeltaOnly = { DeltaParameter(it, null, this) })

        override val localVariables: Iterable<LocalVariableMapping>
            get() = overlay(b?.localVariables?.toList() ?: emptyList(), d?.localVariables?.filterIsInstance<MutableLocalVariableMapping>() ?: emptyList(),
                matches = { d, b -> d.lvOrd == b.lvOrd && d.startOp == b.startOp },
                wrapBound = { d, b -> DeltaLocalVariable(d, b, this) },
                wrapDeltaOnly = { DeltaLocalVariable(it, null, this) })

        override fun setNames(names: Map<Namespace, UnqualifiedName>) = d().setNames(names)
        override fun setDescriptors(descs: Map<Namespace, xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor>) =
            d().setDescriptors(descs)
        override fun setEntries(entries: Map<Namespace, MethodNameAndDescriptor>) = d().setEntries(entries)

        override fun materializedOwner(): MutableInvokableMapping = d()

        override fun parameter(
            index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>
        ): MutableParameterMapping = DeltaParameter(
            d().parameter(index, lvOrd, names),
            b?.parameters?.firstOrNull { it.index == index && it.lvOrd == lvOrd }, this)

        override fun localVariable(
            lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>
        ): MutableLocalVariableMapping = DeltaLocalVariable(
            d().localVariable(lvOrd, startOp, names),
            b?.localVariables?.firstOrNull { it.lvOrd == lvOrd && it.startOp == startOp }, this)

        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

    private inner class DeltaField(
        private var d: MutableFieldMapping?,
        private val b: FieldMapping?,
        private val owner: DeltaClass,
    ) : MutableFieldMapping {

        private fun d(): MutableFieldMapping = d ?: run {
            val node = owner.d().field(b!!.entries)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override val entries: Map<Namespace, FieldNameAndDescriptor>
            get() = (b?.entries ?: emptyMap()) + (d?.entries ?: emptyMap())

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override fun setNames(names: Map<Namespace, UnqualifiedName>) = d().setNames(names)
        override fun setDescriptors(descs: Map<Namespace, xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor>) =
            d().setDescriptors(descs)
        override fun setEntries(entries: Map<Namespace, FieldNameAndDescriptor>) = d().setEntries(entries)
        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

    private inner class DeltaWildcard(
        private var d: MutableWildcardMapping?,
        private val b: WildcardMapping?,
        private val owner: DeltaClass,
    ) : MutableWildcardMapping, DeltaInvokable {

        fun d(): MutableWildcardMapping = d ?: run {
            val node = owner.d().wildcard(b!!.type, b.descs)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override val type: WildcardType
            get() = d?.type ?: b!!.type

        override val descs: Map<Namespace, FieldOrMethodDescriptor>
            get() = (b?.descs ?: emptyMap()) + (d?.descs ?: emptyMap())

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override val parameters: Iterable<ParameterMapping>
            get() = overlay(b?.parameters?.toList() ?: emptyList(), d?.parameters?.filterIsInstance<MutableParameterMapping>() ?: emptyList(),
                matches = { d, b -> d.index == b.index && d.lvOrd == b.lvOrd },
                wrapBound = { d, b -> DeltaParameter(d, b, this) },
                wrapDeltaOnly = { DeltaParameter(it, null, this) })

        override val localVariables: Iterable<LocalVariableMapping>
            get() = overlay(b?.localVariables?.toList() ?: emptyList(), d?.localVariables?.filterIsInstance<MutableLocalVariableMapping>() ?: emptyList(),
                matches = { d, b -> d.lvOrd == b.lvOrd && d.startOp == b.startOp },
                wrapBound = { d, b -> DeltaLocalVariable(d, b, this) },
                wrapDeltaOnly = { DeltaLocalVariable(it, null, this) })

        override fun setDescriptors(descs: Map<Namespace, FieldOrMethodDescriptor>) = d().setDescriptors(descs)

        override fun parameter(
            index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>
        ): MutableParameterMapping = DeltaParameter(
            d().parameter(index, lvOrd, names),
            b?.parameters?.firstOrNull { it.index == index && it.lvOrd == lvOrd }, this)

        override fun localVariable(
            lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>
        ): MutableLocalVariableMapping = DeltaLocalVariable(
            d().localVariable(lvOrd, startOp, names),
            b?.localVariables?.firstOrNull { it.lvOrd == lvOrd && it.startOp == startOp }, this)

        override fun materializedOwner(): MutableInvokableMapping = d()

        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

    private inner class DeltaParameter(
        private var d: MutableParameterMapping?,
        private val b: ParameterMapping?,
        private val owner: DeltaInvokable,
    ) : MutableParameterMapping {

        private fun d(): MutableParameterMapping = d ?: run {
            val node = owner.materializedOwner().parameter(b!!.index, b.lvOrd, b.names)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override var index: Int?
            get() = d?.index ?: b?.index
            set(value) { d().index = value }

        override var lvOrd: Int?
            get() = d?.lvOrd ?: b?.lvOrd
            set(value) { d().lvOrd = value }

        override val names: Map<Namespace, UnqualifiedName>
            get() = (b?.names ?: emptyMap()) + (d?.names ?: emptyMap())

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override fun setNames(names: Map<Namespace, UnqualifiedName>) = d().setNames(names)
        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

    private inner class DeltaLocalVariable(
        private var d: MutableLocalVariableMapping?,
        private val b: LocalVariableMapping?,
        private val owner: DeltaInvokable,
    ) : MutableLocalVariableMapping {

        private fun d(): MutableLocalVariableMapping = d ?: run {
            val node = owner.materializedOwner().localVariable(b!!.lvOrd, b.startOp, b.names)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override val lvOrd: Int
            get() = d?.lvOrd ?: b!!.lvOrd

        override val startOp: Int?
            get() = d?.startOp ?: b?.startOp

        override val names: Map<Namespace, UnqualifiedName>
            get() = (b?.names ?: emptyMap()) + (d?.names ?: emptyMap())

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override fun setNames(names: Map<Namespace, UnqualifiedName>) = d().setNames(names)
        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

    private inner class DeltaConstantGroup(
        private var d: MutableConstantGroupMapping?,
        private val b: ConstantGroupMapping?,
    ) : MutableConstantGroupMapping {

        private fun d(): MutableConstantGroupMapping = d ?: run {
            val node = delta.constantGroup(b!!.type, b.name, b.baseNs)
            b.metadata.forEach { node.addMetadata(copyMetadata(it)) }
            d = node
            node
        }

        override val type: InlineType
            get() = d?.type ?: b!!.type

        override val name: String?
            get() = d?.name ?: b?.name

        override val baseNs: Namespace
            get() = d?.baseNs ?: b!!.baseNs

        override val metadata: Iterable<MetadataElement>
            get() = d?.metadata ?: (b?.metadata?.map(::copyMetadata) ?: emptyList())

        override fun constant(
            fieldClass: InternalName, fieldName: UnqualifiedName,
            fieldDesc: xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor?
        ) = d().constant(fieldClass, fieldName, fieldDesc)

        override fun target(target: xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName?, paramIdx: Int?) =
            d().target(target, paramIdx)

        override fun expression(
            value: xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant,
            expression: xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
        ) = d().expression(value, expression)

        override fun addMetadata(meta: MetadataElement) = d().addMetadata(meta)
    }

}
