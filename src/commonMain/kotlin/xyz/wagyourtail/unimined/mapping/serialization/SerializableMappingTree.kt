package xyz.wagyourtail.unimined.mapping.serialization

import kotlinx.serialization.Serializable
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InlineType
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.MetadataElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.newNsMap
import xyz.wagyourtail.unimined.mapping.tree.impl.MappingTreeData

/**
 * compact kotlinx-serializable wire form of a [MappingTree].
 *
 * The namespace name table is serialized once in [namespaces]; every per-row
 * list is a dense array indexed by table position (null = no name in that
 * namespace), so no `Namespace` objects or map keys repeat across rows.
 * Wire indices are table positions — independent of process-global namespace
 * ordinals, which may differ between encoder and decoder.
 *
 * [toTree] rebuilds by pushing rows through the `MappingTreeData` sink, so
 * row merging, the class index, and descriptor remapping all re-derive
 * exactly as they would from a format reader.
 */
@Serializable
class SerializableMappingTree private constructor(
    /** namespace name table; all per-row lists index into this */
    val namespaces: List<String>,
    val packages: List<PackageEntry> = emptyList(),
    val classes: List<ClassEntry> = emptyList(),
    val constantGroups: List<ConstantGroupEntry> = emptyList()
) {

    @Serializable
    class PackageEntry(
        /** internal package names, indexed by [namespaces] position */
        val names: List<String?>,
        val metadata: List<MetadataElementData> = emptyList()
    )

    @Serializable
    class ClassEntry(
        /** internal class names, indexed by [namespaces] position */
        val names: List<String?>,
        val methods: List<MethodEntry> = emptyList(),
        val fields: List<FieldEntry> = emptyList(),
        val wildcards: List<WildcardEntry> = emptyList(),
        val metadata: List<MetadataElementData> = emptyList()
    )

    @Serializable
    class MethodEntry(
        /** combined `name;desc` strings, indexed by [namespaces] position */
        val entries: List<String?>,
        val parameters: List<ParameterEntry> = emptyList(),
        val localVariables: List<LocalVariableEntry> = emptyList(),
        val metadata: List<MetadataElementData> = emptyList()
    )

    @Serializable
    class FieldEntry(
        /** combined `name;desc` strings, indexed by [namespaces] position */
        val entries: List<String?>,
        val metadata: List<MetadataElementData> = emptyList()
    )

    @Serializable
    class WildcardEntry(
        val type: WildcardType,
        /** field or method descriptors, indexed by [namespaces] position */
        val descs: List<String?>,
        val parameters: List<ParameterEntry> = emptyList(),
        val localVariables: List<LocalVariableEntry> = emptyList(),
        val metadata: List<MetadataElementData> = emptyList()
    )

    @Serializable
    class ParameterEntry(
        val index: Int?,
        val lvOrd: Int?,
        /** unqualified names, indexed by [namespaces] position */
        val names: List<String?>,
        val metadata: List<MetadataElementData> = emptyList()
    )

    @Serializable
    class LocalVariableEntry(
        val lvOrd: Int,
        val startOp: Int?,
        /** unqualified names, indexed by [namespaces] position */
        val names: List<String?>,
        val metadata: List<MetadataElementData> = emptyList()
    )

    @Serializable
    class ConstantGroupEntry(
        val type: InlineType,
        val name: String?,
        /** index into [namespaces] of the base namespace */
        val baseNs: Int,
        val metadata: List<MetadataElementData> = emptyList()
    )

    companion object {

        /** flatten [tree] into the serializable wire form */
        fun of(tree: MappingTree): SerializableMappingTree {
            val nss = tree.namespaces.toList()
            val pos = nss.mapIndexed { i, ns -> ns to i }.toMap()
            fun meta(m: Iterable<MetadataElement>): List<MetadataElementData> =
                m.map { it as MetadataElementData }

            fun <T> flatten(map: Map<Namespace, T>, value: (T) -> String): List<String?> {
                val out = arrayOfNulls<String>(nss.size)
                for ((ns, v) in map) out[pos.getValue(ns)] = value(v)
                return out.asList()
            }

            fun params(it: Iterable<xyz.wagyourtail.unimined.mapping.element.mapping.ParameterMapping>) =
                it.map { p -> ParameterEntry(p.index, p.lvOrd, flatten(p.names) { n -> n.value }, meta(p.metadata)) }

            fun lvars(it: Iterable<xyz.wagyourtail.unimined.mapping.element.mapping.LocalVariableMapping>) =
                it.map { l -> LocalVariableEntry(l.lvOrd, l.startOp, flatten(l.names) { n -> n.value }, meta(l.metadata)) }

            return SerializableMappingTree(
                namespaces = nss.map { it.name },
                packages = tree.packages.map { p ->
                    PackageEntry(flatten(p.names) { it.value }, meta(p.metadata))
                },
                classes = tree.classes.map { c ->
                    ClassEntry(
                        names = flatten(c.names) { it.value },
                        methods = c.methods.map { m ->
                            MethodEntry(
                                entries = flatten(m.entries) { it.value },
                                parameters = params(m.parameters),
                                localVariables = lvars(m.localVariables),
                                metadata = meta(m.metadata)
                            )
                        },
                        fields = c.fields.map { f ->
                            FieldEntry(flatten(f.entries) { it.value }, meta(f.metadata))
                        },
                        wildcards = c.wildcards.map { w ->
                            WildcardEntry(
                                type = w.type,
                                descs = flatten(w.descs) { it.toString() },
                                parameters = params(w.parameters),
                                localVariables = lvars(w.localVariables),
                                metadata = meta(w.metadata)
                            )
                        },
                        metadata = meta(c.metadata)
                    )
                },
                constantGroups = tree.constantGroups.map { g ->
                    ConstantGroupEntry(g.type, g.name, pos.getValue(g.baseNs), meta(g.metadata))
                }
            )
        }
    }

    /** rebuild a mutable tree, re-deriving merge state, the class index, and descriptor remapping */
    fun toTree(): MappingTreeData {
        val nss = namespaces.map { Namespace(it) }
        val tree = MappingTreeData(nss.toMutableList())
        fun <T> inflate(list: List<String?>, read: (String) -> T): MutableMap<Namespace, T> {
            val m = newNsMap<T>()
            for (i in list.indices) list[i]?.let { m[nss[i]] = read(it) }
            return m
        }

        for (p in packages) {
            val pm = tree.package_(inflate(p.names) { PackageName.unchecked(it) })
            for (meta in p.metadata) pm.addMetadata(meta)
        }
        for (c in classes) {
            val cm = tree.clazz(inflate(c.names) { InternalName.unchecked(it) })
            for (f in c.fields) {
                val fm = cm.field(inflate(f.entries) { FieldNameAndDescriptor.unchecked(it) })
                for (meta in f.metadata) fm.addMetadata(meta)
            }
            for (m in c.methods) {
                val mm = cm.method(inflate(m.entries) { MethodNameAndDescriptor.unchecked(it) })
                for (p in m.parameters) {
                    val pm = mm.parameter(p.index, p.lvOrd, inflate(p.names) { UnqualifiedName.unchecked(it) })
                    for (meta in p.metadata) pm.addMetadata(meta)
                }
                for (l in m.localVariables) {
                    val lm = mm.localVariable(l.lvOrd, l.startOp, inflate(l.names) { UnqualifiedName.unchecked(it) })
                    for (meta in l.metadata) lm.addMetadata(meta)
                }
                for (meta in m.metadata) mm.addMetadata(meta)
            }
            for (w in c.wildcards) {
                val wm = cm.wildcard(w.type, inflate(w.descs) { FieldOrMethodDescriptor.unchecked(it) })
                for (p in w.parameters) {
                    val pm = wm.parameter(p.index, p.lvOrd, inflate(p.names) { UnqualifiedName.unchecked(it) })
                    for (meta in p.metadata) pm.addMetadata(meta)
                }
                for (l in w.localVariables) {
                    val lm = wm.localVariable(l.lvOrd, l.startOp, inflate(l.names) { UnqualifiedName.unchecked(it) })
                    for (meta in l.metadata) lm.addMetadata(meta)
                }
                for (meta in w.metadata) wm.addMetadata(meta)
            }
            for (meta in c.metadata) cm.addMetadata(meta)
        }
        for (g in constantGroups) {
            val gm = tree.constantGroup(g.type, g.name, nss[g.baseNs])
            for (meta in g.metadata) gm.addMetadata(meta)
        }
        return tree
    }

}
