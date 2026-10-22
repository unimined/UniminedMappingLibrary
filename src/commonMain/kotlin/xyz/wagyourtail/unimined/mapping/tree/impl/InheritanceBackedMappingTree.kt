package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceConflictException
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.PropagationStrategy
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.element.mapping.walkMappings
import xyz.wagyourtail.unimined.mapping.newSmallSet

/**
 * member identity for propagation lookups — (name, desc) in the inheritance tree's base namespace
 */
data class MemberKey(val name: UnqualifiedName, val desc: FieldOrMethodDescriptor?)

/**
 * tree that propagates member name assignments through the class hierarchy immediately
 * on assignment/creation, instead of requiring a batch [InheritanceTree.propagate] pass.
 *
 * usage: wrap a [MutableMappingTree]; build the [InheritanceTree] first (e.g. via Propagator
 * or CachedInheritanceTree), then writes through [clazz]/[MutableClassMapping.method]/etc.
 * flow down to descendants automatically. names written through this wrapper are treated as
 * explicitly-set and are not overwritten by later inherited values.
 */
class InheritanceBackedMappingTree(
    private val delegate: MutableMappingTree,
    val inheritance: InheritanceTree,
    /**
     * how propagated-vs-existing name disagreements resolve — see [PropagationStrategy].
     */
    val strategy: PropagationStrategy = PropagationStrategy.KEEP_EXPLICIT
) : MutableMappingTree by delegate {

    /** explicit-assignment provenance: class fns-name -> member -> namespaces with explicit names.
     *  lookup-heavy at member scale — stays LinkedHashMap regardless of [MappingCollections.useSmallCollections] */
    internal val explicit = LinkedHashMap<InternalName, MutableMap<MemberKey, MutableSet<Namespace>>>()

    private fun markExplicit(cls: InternalName, key: MemberKey, nss: Collection<Namespace>) {
        explicit.getOrPut(cls) { LinkedHashMap() }.getOrPut(key) { newSmallSet() }.addAll(nss)
    }

    private fun isExplicit(cls: InternalName, key: MemberKey, ns: Namespace) =
        explicit[cls]?.get(key)?.contains(ns) == true

    /**
     * run the batch propagation pass on the wrapped tree (for targets not yet covered
     * by incremental writes — e.g. bulk-loaded mappings).
     */
    suspend fun propagate(targets: Set<Namespace>) = inheritance.propagate(delegate, targets)

    /**
     * emit only the classes/members present in the inheritance graph ([InheritanceTree.inMappings]).
     */
    fun filtered(output: xyz.wagyourtail.unimined.mapping.sink.MappingSink) =
        inheritance.filtered(this, output)

    override fun clazz(names: Map<Namespace, InternalName>): MutableClassMapping {
        val base = delegate.clazz(names)
        val fnsName = names[inheritance.fns] ?: return base
        val info = inheritance.classes[fnsName] ?: return base
        return PropagatingClass(base, info)
    }

    /**
     * push an inherited name down to matching members in descendant classes (BFS, visited-set
     * for diamonds). skips namespaces where the descendant's member has an explicit name.
     */
    private fun propagateDown(
        info: InheritanceTree.ClassInfo,
        key: MemberKey,
        ns: Namespace,
        name: UnqualifiedName,
        isMethod: Boolean
    ) {
        val visited = mutableSetOf(info.name)
        val queue = ArrayDeque<InheritanceTree.ClassInfo>()
        queue.addAll(info.children)
        while (queue.isNotEmpty()) {
            val child = queue.removeFirst()
            if (!visited.add(child.name)) continue
            val matches = if (isMethod) {
                child.methods.any { it.name == key.name && it.descriptor == key.desc }
            } else {
                child.fields.any { it.name == key.name && it.descriptor == key.desc }
            }
            if (matches) {
                val clsNode = delegate.clazz(mapOf(inheritance.fns to child.name))
                if (isMethod) {
                    val member = clsNode.method(mapOf(inheritance.fns to MethodNameAndDescriptor.unchecked("${key.name};${key.desc ?: ""}")))
                    applyPropagated(member, { member.entries[ns]?.name }, { setName -> member.setNames(mapOf(ns to setName)) }, child.name, key, ns, name)
                } else {
                    val member = clsNode.field(mapOf(inheritance.fns to FieldNameAndDescriptor.unchecked("${key.name};${key.desc ?: ""}")))
                    applyPropagated(member, { member.entries[ns]?.name }, { setName -> member.setNames(mapOf(ns to setName)) }, child.name, key, ns, name)
                }
            }
            queue.addAll(child.children)
        }
    }

    /**
     * apply a propagated name to a member under [strategy] — explicit names win
     * unless [LAST_WINS][PropagationStrategy.LAST_WINS]; disagreements throw
     * under [STRICT][PropagationStrategy.STRICT], keep-first under
     * [FIRST_INHERITED_WINS][PropagationStrategy.FIRST_INHERITED_WINS], and
     * attach a [SyntheticBridgeElement] for the losing name under
     * [BRIDGE][PropagationStrategy.BRIDGE].
     */
    private fun applyPropagated(
        member: MutableMetadataParent,
        memberName: () -> UnqualifiedName?,
        setName: (UnqualifiedName) -> Unit,
        cls: InternalName,
        key: MemberKey,
        ns: Namespace,
        name: UnqualifiedName
    ) {
        val existing = memberName()
        val explicitHere = isExplicit(cls, key, ns)
        when {
            explicitHere -> when (strategy) {
                PropagationStrategy.LAST_WINS -> { setName(name); removeStaleBridge(member, ns, name) }
                PropagationStrategy.BRIDGE -> addBridge(member, name, ns)
                else -> {}
            }
            existing == null -> { setName(name); removeStaleBridge(member, ns, name) }
            existing == name -> {}
            else -> when (strategy) {
                PropagationStrategy.STRICT -> throw InheritanceConflictException(
                    "${cls.value}#${key.name.value} in ns ${ns.name}: propagated '$name' disagrees with existing '$existing'"
                )
                PropagationStrategy.FIRST_INHERITED_WINS -> {}
                PropagationStrategy.BRIDGE -> addBridge(member, name, ns)
                else -> { setName(name); removeStaleBridge(member, ns, name) } // KEEP_EXPLICIT, LAST_WINS
            }
        }
    }

    /**
     * attach a [SyntheticBridgeElement] for a propagated name that lost —
     * deduped by `(name, baseNs)` so repeated propagation of the same
     * losing name doesn't stack identical bridges.
     */
    private fun addBridge(member: MutableMetadataParent, name: UnqualifiedName, ns: Namespace) {
        val existing = member.metadata.filterIsInstance<SyntheticBridgeElement>().firstOrNull()
        if (existing is SyntheticBridgeElementData) {
            if (ns !in existing.names) existing.names[ns] = name
        } else if (existing == null) {
            member.addMetadata(SyntheticBridgeElementData(mutableMapOf(ns to name)))
        }
    }

    /**
     * drop a bridge the member now satisfies — a written name (explicit or
     * propagated) equal to an existing bridge alias in [ns] means the member
     * exposes that name itself, so no delegate is needed for it.
     */
    private fun removeStaleBridge(member: MutableMetadataParent, ns: Namespace, name: UnqualifiedName) {
        for (bridge in member.metadata.filterIsInstance<SyntheticBridgeElement>()) {
            if (bridge is SyntheticBridgeElementData && bridge.names[ns] == name) {
                bridge.names.remove(ns)
            }
        }
        (member.metadata as? MutableList<*>)?.removeAll {
            it is SyntheticBridgeElement && it.names.isEmpty()
        }
    }

    /**
     * post-write hygiene pass — drop every [SyntheticBridgeElement] that is
     * already satisfied (the member exposes the bridge's name in its
     * [SyntheticBridgeElement.baseNs] itself) and collapse duplicates.
     * handles bulk-loaded trees and name retractions the incremental path misses.
     */
    fun cleanupBridges() {
        delegate.walkMappings().forEach { el ->
            if (el !is MutableMetadataParent) return@forEach
            val names: Map<Namespace, UnqualifiedName> = when (el) {
                is MethodMapping -> el.entries.mapValues { it.value.name }
                is FieldMapping -> el.entries.mapValues { it.value.name }
                is ParameterMapping -> el.names
                is LocalVariableMapping -> el.names
                else -> return@forEach
            }
            val seen = mutableSetOf<UnqualifiedName>()
            for (bridge in el.metadata.filterIsInstance<SyntheticBridgeElement>()) {
                if (bridge is SyntheticBridgeElementData) {
                    bridge.names.entries.removeAll { (ns, alias) ->
                        names[ns] == alias || !seen.add(alias)
                    }
                }
            }
            (el.metadata as? MutableList<*>)?.removeAll {
                it is SyntheticBridgeElement && it.names.isEmpty()
            }
        }
    }

    /**
     * pull names a freshly-attached member lacks down from its ancestors —
     * covers the late-registration case: a parent was named before the
     * descendant member existed, so [propagateDown] never fired for it.
     * pulled names flow further down via [propagateDown]; explicit names on
     * the new member always win.
     */
    private fun pullDown(
        info: InheritanceTree.ClassInfo,
        key: MemberKey,
        explicitNss: Set<Namespace>,
        isMethod: Boolean,
        memberNames: (Namespace) -> UnqualifiedName?,
        setName: (Namespace, UnqualifiedName) -> Unit,
        bridgeTarget: MutableMetadataParent
    ) {
        for (parent in info.allParents) {
            val pCls = delegate.getClass(inheritance.fns, parent.name) ?: continue
            val pEntries: Map<Namespace, out NameAndDescriptor> = (if (isMethod) {
                pCls.methods.firstOrNull {
                    it.entries[inheritance.fns]?.let { e -> e.name == key.name && e.descriptor == key.desc } == true
                }?.entries
            } else {
                pCls.fields.firstOrNull {
                    it.entries[inheritance.fns]?.let { e -> e.name == key.name && e.descriptor == key.desc } == true
                }?.entries
            }) ?: continue
            for ((ns, e) in pEntries) {
                if (ns in explicitNss) {
                    // child declared this ns itself — an override, not a fill;
                    // under BRIDGE the losing ancestor name still becomes a delegate
                    val existing = memberNames(ns)
                    if (existing != e.name && strategy == PropagationStrategy.BRIDGE) {
                        addBridge(bridgeTarget, e.name, ns)
                    }
                    continue
                }
                val existing = memberNames(ns)
                when {
                    existing == null -> {
                        setName(ns, e.name)
                        removeStaleBridge(bridgeTarget, ns, e.name)
                        propagateDown(info, key, ns, e.name, isMethod)
                    }
                    existing == e.name -> {}
                    strategy == PropagationStrategy.STRICT -> throw InheritanceConflictException(
                        "${info.name.value}#${key.name.value} in ns ${ns.name}: ancestor '${e.name}' disagrees with existing '$existing'"
                    )
                    strategy == PropagationStrategy.BRIDGE -> addBridge(bridgeTarget, e.name, ns)
                    strategy == PropagationStrategy.LAST_WINS -> {
                        setName(ns, e.name)
                        removeStaleBridge(bridgeTarget, ns, e.name)
                        propagateDown(info, key, ns, e.name, isMethod)
                    }
                    else -> {} // KEEP_EXPLICIT / FIRST_INHERITED_WINS — nearest ancestor wins
                }
            }
        }
    }

    inner class PropagatingClass(
        private val delegate: MutableClassMapping,
        val info: InheritanceTree.ClassInfo
    ) : MutableClassMapping by delegate {

        override fun method(entries: Map<Namespace, MethodNameAndDescriptor>): MutableMethodMapping {
            val base = delegate.method(entries)
            val fnsEntry = entries[inheritance.fns] ?: return base
            val key = MemberKey(fnsEntry.name, fnsEntry.descriptor)
            markExplicit(info.name, key, entries.keys)
            pullDown(info, key, entries.keys.toSet(), isMethod = true,
                memberNames = { ns -> base.entries[ns]?.name },
                setName = { ns, n -> base.setNames(mapOf(ns to n)) },
                bridgeTarget = base)
            for ((ns, e) in entries) {
                removeStaleBridge(base, ns, e.name)
                propagateDown(info, key, ns, e.name, isMethod = true)
            }
            return PropagatingMethod(base, key)
        }

        override fun field(entries: Map<Namespace, FieldNameAndDescriptor>): MutableFieldMapping {
            val base = delegate.field(entries)
            val fnsEntry = entries[inheritance.fns] ?: return base
            val key = MemberKey(fnsEntry.name, fnsEntry.descriptor)
            markExplicit(info.name, key, entries.keys)
            pullDown(info, key, entries.keys.toSet(), isMethod = false,
                memberNames = { ns -> base.entries[ns]?.name },
                setName = { ns, n -> base.setNames(mapOf(ns to n)) },
                bridgeTarget = base)
            for ((ns, e) in entries) {
                removeStaleBridge(base, ns, e.name)
                propagateDown(info, key, ns, e.name, isMethod = false)
            }
            return PropagatingField(base, key)
        }

        inner class PropagatingMethod(
            private val delegate: MutableMethodMapping,
            private val key: MemberKey
        ) : MutableMethodMapping by delegate {
            override fun setNames(names: Map<Namespace, UnqualifiedName>) {
                delegate.setNames(names)
                markExplicit(info.name, key, names.keys)
                for ((ns, name) in names) {
                    removeStaleBridge(delegate, ns, name)
                    propagateDown(info, key, ns, name, isMethod = true)
                }
            }
            override fun setEntries(entries: Map<Namespace, MethodNameAndDescriptor>) {
                delegate.setEntries(entries)
                markExplicit(info.name, key, entries.keys)
                for ((ns, e) in entries) {
                    removeStaleBridge(delegate, ns, e.name)
                    propagateDown(info, key, ns, e.name, isMethod = true)
                }
            }
        }

        inner class PropagatingField(
            private val delegate: MutableFieldMapping,
            private val key: MemberKey
        ) : MutableFieldMapping by delegate {
            override fun setNames(names: Map<Namespace, UnqualifiedName>) {
                delegate.setNames(names)
                markExplicit(info.name, key, names.keys)
                for ((ns, name) in names) {
                    removeStaleBridge(delegate, ns, name)
                    propagateDown(info, key, ns, name, isMethod = false)
                }
            }
            override fun setEntries(entries: Map<Namespace, FieldNameAndDescriptor>) {
                delegate.setEntries(entries)
                markExplicit(info.name, key, entries.keys)
                for ((ns, e) in entries) {
                    removeStaleBridge(delegate, ns, e.name)
                    propagateDown(info, key, ns, e.name, isMethod = false)
                }
            }
        }
    }
}
