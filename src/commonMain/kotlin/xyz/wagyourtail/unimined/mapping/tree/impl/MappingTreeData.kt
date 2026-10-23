@file:UseSerializers(
    NamespaceSerializer::class,
    InternalNameSerializer::class,
    PackageNameSerializer::class
)

package xyz.wagyourtail.unimined.mapping.tree.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.element.metadata.MutableMetadataParent
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.serialization.InternalNameSerializer
import xyz.wagyourtail.unimined.mapping.serialization.NamespaceSerializer
import xyz.wagyourtail.unimined.mapping.serialization.PackageNameSerializer
import xyz.wagyourtail.unimined.mapping.toSmallMap

@Serializable
class MappingTreeData(
    override val namespaces: MutableList<Namespace> = mutableListOf()
) : MutableMappingTree {

    override val packages: MutableList<PackageMappingData> = mutableListOf()

    override val classes: MutableList<ClassMappingData> = mutableListOf()

    override val constantGroups: MutableList<ConstantGroupMappingData> = mutableListOf()

    @Transient
    private val byNamespace = defaultedMapOf<Namespace, MutableMap<InternalName, ClassMappingData>> { mutableMapOf() }

    @Transient
    private val stack = ArrayDeque<Any>()

    override fun getClass(namespace: Namespace, name: InternalName): ClassMappingData? {
        return byNamespace[namespace][name]
    }

    override fun addNamespaces(namespaces: Iterable<Namespace>) {
        for (ns in namespaces) {
            if (ns !in this.namespaces) {
                this.namespaces.add(ns)
            }
        }
    }

    override fun header(namespaces: List<Namespace>) {
        addNamespaces(namespaces)
    }

    override fun package_(names: Map<Namespace, PackageName>): MutablePackageMapping {
        addNamespaces(names.keys)
        for (ns in this.namespaces.filter { it in names }) {
            val existing = packages.firstOrNull { it.names[ns] == names[ns] }
            if (existing != null) {
                existing.setNames(names)
                return existing
            }
        }
        val node = PackageMappingData(names.toSmallMap())
        packages.add(node)
        return node
    }

    override fun clazz(names: Map<Namespace, InternalName>): MutableClassMapping {
        addNamespaces(names.keys)
        for (ns in this.namespaces.filter { it in names }) {
            val existing = getClass(ns, names[ns]!!)
            if (existing != null) {
                for ((ens, name) in existing.names.filter { it.key in names && it.value != names[it.key] }) {
                    byNamespace[ens].remove(name)
                }
                existing.setNames(names)
                for ((ens, name) in names) {
                    byNamespace[ens][name] = existing
                }
                return existing
            }
        }
        val node = ClassMappingData(names.toSmallMap())
        node.descRemapper = { from, to, desc -> map(from, to, desc) }
        for ((ns, name) in names) {
            byNamespace[ns][name] = node
        }
        classes.add(node)
        return node
    }

    override fun constantGroup(type: InlineType, name: String?, baseNs: Namespace): MutableConstantGroupMapping {
        addNamespaces(setOf(baseNs))
        val node = ConstantGroupMappingData(type, name, baseNs)
        constantGroups.add(node)
        return node
    }

    /** attach a pushed element to the nearest open element that can hold it (walks the stack —
     * readers may leave child visitors open without ending them, so the top isn't necessarily
     * the intended parent) */
    private fun attachChild(child: MappingElement): MappingElement? {
        for (parent in stack.asReversed().filterIsInstance<MappingElement>()) {
            val retained = attachTo(parent, child)
            if (retained != null) return retained
        }
        return null
    }

    /** attach [child] directly to [parent], returning the retained (possibly merged-into) node */
    private fun attachTo(parent: MappingElement, child: MappingElement): MappingElement? = when {
        parent is MutableClassMapping && child is MethodMapping -> parent.method(child.entries)
        parent is MutableClassMapping && child is FieldMapping -> parent.field(child.entries)
        parent is MutableClassMapping && child is WildcardMapping -> parent.wildcard(child.type, child.descs)
        parent is MutableInvokableMapping && child is ParameterMapping -> parent.parameter(child.index, child.lvOrd, child.names)
        parent is MutableInvokableMapping && child is LocalVariableMapping -> parent.localVariable(child.lvOrd, child.startOp, child.names)
        else -> null
    }

    private fun attachTop(element: MappingElement): MappingElement? = when (element) {
        is PackageMapping -> package_(element.names)
        is ClassMapping -> clazz(element.names)
        is ConstantGroupMapping -> constantGroup(element.type, element.name, element.baseNs)
        is MethodMapping, is FieldMapping, is WildcardMapping,
        is ParameterMapping, is LocalVariableMapping, is MappingTree -> null
        else -> null
    }

    /** deep-merge an element's children + metadata into the retained node (for complete-element pushes) */
    private fun mergeInto(retained: MappingElement, element: MappingElement) {
        if (element is MetadataParent && retained is MutableMetadataParent) {
            for (meta in element.metadata) retained.addMetadata(meta)
        }
        for (child in element.contents) {
            val r = attachTo(retained, child) ?: continue
            mergeInto(r, child)
        }
    }

    override fun push(element: MappingElement): MappingElement? {
        // top-level element types (package/class/group) can't be children — if no open
        // element can hold this one, it goes to the root regardless of stale stack state
        // (flat-format readers don't always end previous elements)
        val retained = (if (stack.isEmpty()) attachTop(element) else attachChild(element) ?: attachTop(element))
            ?: return null
        stack.addLast(retained)
        mergeInto(retained, element)
        return retained
    }

    override fun push(meta: MetadataElement): MetadataElement? {
        val parent = stack.asReversed().firstOrNull { it is MutableMetadataParent } as? MutableMetadataParent ?: return null
        parent.addMetadata(meta)
        stack.addLast(meta)
        return meta
    }

    override fun pop(element: MappingElement) {
        // pop the element and any un-closed children above it
        val idx = stack.indexOfLast { it === element }
        if (idx < 0) return
        while (stack.size > idx) stack.removeLast()
        // element complete — re-merge it against siblings. head-merge runs at push time
        // before children exist, so desc-mismatch/naming merges that also move children
        // (params, metadata) only become possible once the element is fully populated.
        // a row that now merges fully gets dropped from its parent's list.
        when (element) {
            is MethodMappingData -> {
                val parent = stack.asReversed().firstOrNull { it is MutableClassMapping } as? MutableClassMapping
                val siblings = (parent as? ClassMappingData)?.methods?.filterIsInstance<MethodMappingData>()
                if (siblings != null) {
                    for (existing in siblings) {
                        if (existing !== element && element.mergeInto(existing)) {
                            (parent as ClassMappingData).methods.remove(element)
                            break
                        }
                    }
                }
            }
            is FieldMappingData -> {
                val parent = stack.asReversed().firstOrNull { it is MutableClassMapping } as? MutableClassMapping
                val siblings = (parent as? ClassMappingData)?.fields?.filterIsInstance<FieldMappingData>()
                if (siblings != null) {
                    for (existing in siblings) {
                        if (existing !== element && element.mergeInto(existing)) {
                            (parent as ClassMappingData).fields.remove(element)
                            break
                        }
                    }
                }
            }
            is WildcardMappingData -> {
                val parent = stack.asReversed().firstOrNull { it is MutableClassMapping } as? MutableClassMapping
                val siblings = (parent as? ClassMappingData)?.wildcards?.filterIsInstance<WildcardMappingData>()
                if (siblings != null) {
                    for (existing in siblings) {
                        if (existing !== element && element.mergeInto(existing)) {
                            (parent as ClassMappingData).wildcards.remove(element)
                            break
                        }
                    }
                }
            }
            is ParameterMappingData -> {
                val parent = stack.asReversed().firstOrNull { it is MutableInvokableMapping } as? MutableInvokableMapping
                val siblings = (parent as? MethodMappingData)?.parameters?.filterIsInstance<ParameterMappingData>()
                    ?: (parent as? WildcardMappingData)?.parameters?.filterIsInstance<ParameterMappingData>()
                if (siblings != null) {
                    for (existing in siblings) {
                        if (existing !== element && element.mergeInto(existing)) {
                            if (parent is MethodMappingData) parent.parameters.remove(element)
                            else if (parent is WildcardMappingData) parent.parameters.remove(element)
                            break
                        }
                    }
                }
            }
            is LocalVariableMappingData -> {
                val parent = stack.asReversed().firstOrNull { it is MutableInvokableMapping } as? MutableInvokableMapping
                val siblings = (parent as? MethodMappingData)?.localVariables?.filterIsInstance<LocalVariableMappingData>()
                    ?: (parent as? WildcardMappingData)?.localVariables?.filterIsInstance<LocalVariableMappingData>()
                if (siblings != null) {
                    for (existing in siblings) {
                        if (existing !== element && existing.lvOrd == element.lvOrd && existing.startOp == element.startOp) {
                            existing.setNames(element.names)
                            if (parent is MethodMappingData) parent.localVariables.remove(element)
                            else if (parent is WildcardMappingData) parent.localVariables.remove(element)
                            break
                        }
                    }
                }
            }
            else -> {}
        }
    }

    override fun pop(meta: MetadataElement) {
        val idx = stack.indexOfLast { it === meta }
        if (idx < 0) return
        while (stack.size > idx) stack.removeLast()
    }

    override fun end() {}

    override fun toString() = "MappingTreeData(${namespaces.joinToString()})"

}
