package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.serialization.Umf
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.*

/**
 * low-RAM tree for memory-constrained environments (web visualizer).
 *
 * top-level elements pushed through the sink are serialized to fragments immediately;
 * only the name index stays resident. subtrees are deserialized on demand.
 */
class LazyMappingTree(
    private val umf: Umf = Umf()
) : MappingTree, MappingSink {

    private val _namespaces = mutableListOf<Namespace>()

    private val _packages = mutableListOf<PackageMappingData>()
    private val _constantGroups = mutableListOf<ConstantGroupMappingData>()

    /** lookup-heavy at member scale — stays LinkedHashMap regardless of [MappingCollections.useSmallCollections] */
    private val classIndex = LinkedHashMap<Namespace, MutableMap<InternalName, Int>>()
    private val classFragments = mutableListOf<String>()

    override val namespaces get() = _namespaces.toList()
    override val packages get() = _packages.toList()
    override val constantGroups get() = _constantGroups.toList()

    override val classes: Iterable<ClassMapping>
        get() = classFragments.map { umf.decodeFromString(ClassMappingData.serializer(), it) }

    override fun getClass(namespace: Namespace, name: InternalName): ClassMapping? {
        val idx = classIndex[namespace]?.get(name) ?: return null
        return umf.decodeFromString(ClassMappingData.serializer(), classFragments[idx])
    }

    override fun header(namespaces: List<Namespace>) {
        for (ns in namespaces) if (ns !in _namespaces) _namespaces.add(ns)
        umf.namespaces = _namespaces.toList()
    }

    /** depth of open elements inside the currently capturing fragment; 0 = not capturing */
    private var capture: Pair<MutableMappingTree, Int>? = null

    override fun push(element: MappingElement): MappingElement? {
        val cap = capture
        if (cap != null) {
            if (cap.first.push(element) == null) return null
            capture = cap.copy(second = cap.second + 1)
            return element
        }
        return when (element) {
            is PackageMapping -> {
                _packages.add(element.asData())
                element
            }
            is ConstantGroupMapping -> {
                _constantGroups.add(element.asData())
                element
            }
            is ClassMapping -> {
                val frag = MutableMappingTree(namespaces)
                capture = frag.push(element)?.let { frag to 1 }
                if (capture == null) null else element
            }
            is MethodMapping, is FieldMapping, is WildcardMapping,
            is ParameterMapping, is LocalVariableMapping, is MappingTree -> null
            else -> null
        }
    }

    override fun push(meta: MetadataElement): MetadataElement? {
        return if (capture?.first?.push(meta) != null) meta else null
    }

    override fun pop(element: MappingElement) {
        val cap = capture ?: return
        if (cap.second > 1) {
            cap.first.pop(element)
            capture = cap.copy(second = cap.second - 1)
            return
        }
        // class subtree closed — serialize it
        val cls = cap.first.classes.firstOrNull() as? ClassMappingData
        capture = null
        if (cls != null) {
            val idx = classFragments.size
            classFragments.add(umf.encodeToString(ClassMappingData.serializer(), cls))
            for ((ns, name) in cls.names) {
                classIndex.getOrPut(ns) { LinkedHashMap() }[name] = idx
            }
        }
    }

    override fun end() {}

    override fun toString() = "LazyMappingTree(${_namespaces.joinToString()}, ${classFragments.size} classes)"

}
