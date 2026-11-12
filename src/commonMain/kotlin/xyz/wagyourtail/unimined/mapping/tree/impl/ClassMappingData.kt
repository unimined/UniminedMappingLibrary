@file:UseSerializers(
    NamespaceSerializer::class,
    InternalNameSerializer::class,
    UnqualifiedNameSerializer::class,
    FieldNameAndDescriptorSerializer::class,
    MethodNameAndDescriptorSerializer::class,
    FieldOrMethodDescriptorSerializer::class,
    FieldDescriptorSerializer::class,
    MethodDescriptorSerializer::class
)

package xyz.wagyourtail.unimined.mapping.tree.impl


import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableWildcardMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.MetadataElementData
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.mergeMetadata
import xyz.wagyourtail.unimined.mapping.forEachNsEntry
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.serialization.*
import xyz.wagyourtail.unimined.mapping.toNsMap

@Serializable
data class ClassMappingData(
    override val names: MutableMap<Namespace, InternalName> = mutableMapOf(),
    override val methods: MutableList<MethodMappingData> = mutableListOf(),
    override val fields: MutableList<FieldMappingData> = mutableListOf(),
    override val wildcards: MutableList<WildcardMappingData> = mutableListOf(),
    override val metadata: MutableList<MetadataElementData> = mutableListOf()
) : MutableClassMapping {

    @Transient
    internal var descRemapper: DescRemapper? = null
        set(value) {
            field = value
            methods.forEach { it.descRemapper = value }
            fields.forEach { it.descRemapper = value }
            wildcards.forEach { it.descRemapper = value }
        }

    override fun setNames(names: Map<Namespace, InternalName>) {
        this.names.putAll(names)
    }

    // name-keyed merge candidate index: base-namespace + name region →
    // members, maintained per member kind. Replaces the O(siblings)
    // mergeInto trial scan in the upserts below — hot on big classes
    // (500+ members → 250k+ trials per emit otherwise). Deliberately
    // never pruned: entry names only accumulate over a row's life, so a
    // stale key only adds a false-positive candidate that mergeInto
    // re-verifies — a real name match can never be missed. Detached
    // rows (spliced by pop-time re-merge) are skipped instead, and a
    // deserialized class rebuilds lazily (transient fields come back
    // empty).
    @Transient
    private val methodByName = mutableMapOf<String, MutableList<MethodMappingData>>()

    @Transient
    private val fieldByName = mutableMapOf<String, MutableList<FieldMappingData>>()

    // desc-keyed siblings of the name indices — the desc-only merge fallback
    // scans every stored member per unmatched row (O(siblings) × rows). A
    // raw desc value can only match when both rows carry it in env-comparable
    // namespaces, so (baseNs, desc) buckets are a superset filter — the real
    // descMergeable check still runs per candidate. Only valid while
    // descRemapper is null: remapped descs need not share a raw value.
    @Transient
    private val methodByDesc = mutableMapOf<String, MutableList<MethodMappingData>>()

    @Transient
    private val fieldByDesc = mutableMapOf<String, MutableList<FieldMappingData>>()

    private fun nameKey(ns: Namespace, rawEntry: String): String {
        val end = rawEntry.indexOf(';').let { if (it < 0) rawEntry.length else it }
        // index by BASE namespace: `ns` and `ns:env` are env-comparable
        // in the merge check, so a lookup by base sees every candidate —
        // the mergeInto call does the real env-domain comparability test
        return ns.baseNs.name + ":" + rawEntry.substring(0, end)
    }

    private fun <T> indexNames(
        index: MutableMap<String, MutableList<T>>,
        entries: Map<Namespace, out xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor>,
        member: T
    ) {
        entries.forEachNsEntry { ns, e ->
            index.getOrPut(nameKey(ns, e.value)) { mutableListOf() }.let {
                if (member !in it) it.add(member)
            }
        }
    }

    private fun descKey(ns: Namespace, rawEntry: String): String? {
        val sep = rawEntry.indexOf(';')
        if (sep < 0) return null
        return ns.baseNs.name + ":" + rawEntry.substring(sep + 1)
    }

    private fun <T> indexDescs(
        index: MutableMap<String, MutableList<T>>,
        entries: Map<Namespace, out xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor>,
        member: T
    ) {
        entries.forEachNsEntry { ns, e ->
            val key = descKey(ns, e.value) ?: return@forEachNsEntry
            index.getOrPut(key) { mutableListOf() }.let {
                if (member !in it) it.add(member)
            }
        }
    }

    private fun ensureIndices() {
        if (methodByName.isEmpty() && methods.isNotEmpty()) {
            for (m in methods) {
                indexNames(methodByName, m.entries, m)
                indexDescs(methodByDesc, m.entries, m)
            }
        }
        if (fieldByName.isEmpty() && fields.isNotEmpty()) {
            for (f in fields) {
                indexNames(fieldByName, f.entries, f)
                indexDescs(fieldByDesc, f.entries, f)
            }
        }
    }

    override fun method(entries: Map<Namespace, MethodNameAndDescriptor>): MutableMethodMapping {
        ensureIndices()
        val incoming = MethodMappingData(entries.toNsMap())
        incoming.descRemapper = descRemapper
        // name-keyed merges are unambiguous and always win — desc-only
        // matching is a fallback that must not outrun a name match later
        // in the list (record accessors hit this: hashCode's ()I desc
        // matches the searchDistance accessor's, but the accessor's own
        // row already carries the shared intermediary name)
        // snapshot before any merge attempts — push-time desc-mismatch
        // splices mutate entries and bump mergeVersion, so a bumped row at
        // pop means "revisit siblings" not "unchanged"
        incoming.pushedMergeVersion = incoming.mergeVersion
        // dedupe set + drain queue — desc-mismatch splices mutate
        // incoming.entries mid-loop, so collectCandidates() can run again
        // while candidates remain; a live set iterator would CME
        val seen = HashSet<MethodMappingData>()
        val queue = ArrayDeque<MethodMappingData>()
        fun collectCandidates() {
            incoming.entries.forEachNsEntry { ns, e ->
                methodByName[nameKey(ns, e.value)]?.let { bucket ->
                    for (m in bucket) if (seen.add(m)) queue.add(m)
                }
            }
        }
        collectCandidates()
        while (queue.isNotEmpty()) {
            val existing = queue.removeFirst()
            if (existing.detached) continue
            val ver = incoming.mergeVersion
            if (incoming.mergeInto(existing) { false }) {
                indexNames(methodByName, existing.entries, existing)
                indexDescs(methodByDesc, existing.entries, existing)
                return existing
            }
            if (incoming.mergeVersion != ver) collectCandidates()
        }
        // desc-only merges additionally require a unique candidate — with
        // several same-descriptor siblings the first match wins by
        // iteration order and cross-wires names (unobfuscated members
        // never seen by one source, e.g. forge's
        // Schema#registerEntities/registerBlockEntities). Tracked during
        // the count so the merge targets the proven sibling directly —
        // a name-path merge in a second full scan is impossible anyway
        // (name-matched candidates already failed mergeInto above)
        var sole: MethodMappingData? = null
        var ambiguous = false
        if (descRemapper == null) {
            // index-filtered candidates — same set the full scan would
            // produce, minus members sharing no desc value
            val descCandidates = LinkedHashSet<MethodMappingData>()
            incoming.entries.forEachNsEntry { ns, e ->
                descKey(ns, e.value)?.let { methodByDesc[it] }?.let { bucket ->
                    descCandidates.addAll(bucket)
                }
            }
            for (existing in descCandidates) {
                if (incoming.descMergeable(existing)) {
                    if (sole != null) { ambiguous = true; break }
                    sole = existing
                }
            }
        } else {
            for (existing in methods) {
                if (incoming.descMergeable(existing)) {
                    if (sole != null) { ambiguous = true; break }
                    sole = existing
                }
            }
        }
        if (!ambiguous && sole != null) {
            incoming.mergeInto(sole) { true }
            indexNames(methodByName, sole.entries, sole)
            indexDescs(methodByDesc, sole.entries, sole)
            return sole
        }
        methods.add(incoming)
        indexNames(methodByName, incoming.entries, incoming)
        indexDescs(methodByDesc, incoming.entries, incoming)
        return incoming
    }

    override fun field(entries: Map<Namespace, FieldNameAndDescriptor>): MutableFieldMapping {
        ensureIndices()
        val incoming = FieldMappingData(entries.toNsMap())
        incoming.descRemapper = descRemapper
        // see method(): name-keyed merges first, unique desc-only second
        incoming.pushedMergeVersion = incoming.mergeVersion
        // see method(): dedupe set + drain queue — collectCandidates()
        // can run mid-loop after merge splices; a live iterator would CME
        val seen = HashSet<FieldMappingData>()
        val queue = ArrayDeque<FieldMappingData>()
        fun collectCandidates() {
            incoming.entries.forEachNsEntry { ns, e ->
                fieldByName[nameKey(ns, e.value)]?.let { bucket ->
                    for (f in bucket) if (seen.add(f)) queue.add(f)
                }
            }
        }
        collectCandidates()
        while (queue.isNotEmpty()) {
            val existing = queue.removeFirst()
            if (existing.detached) continue
            val ver = incoming.mergeVersion
            if (incoming.mergeInto(existing) { false }) {
                indexNames(fieldByName, existing.entries, existing)
                indexDescs(fieldByDesc, existing.entries, existing)
                return existing
            }
            if (incoming.mergeVersion != ver) collectCandidates()
        }
        var sole: FieldMappingData? = null
        var ambiguous = false
        if (descRemapper == null) {
            val descCandidates = LinkedHashSet<FieldMappingData>()
            incoming.entries.forEachNsEntry { ns, e ->
                descKey(ns, e.value)?.let { fieldByDesc[it] }?.let { bucket ->
                    descCandidates.addAll(bucket)
                }
            }
            for (existing in descCandidates) {
                if (incoming.descMergeable(existing)) {
                    if (sole != null) { ambiguous = true; break }
                    sole = existing
                }
            }
        } else {
            for (existing in fields) {
                if (incoming.descMergeable(existing)) {
                    if (sole != null) { ambiguous = true; break }
                    sole = existing
                }
            }
        }
        if (!ambiguous && sole != null) {
            incoming.mergeInto(sole) { true }
            indexNames(fieldByName, sole.entries, sole)
            indexDescs(fieldByDesc, sole.entries, sole)
            return sole
        }
        fields.add(incoming)
        indexNames(fieldByName, incoming.entries, incoming)
        indexDescs(fieldByDesc, incoming.entries, incoming)
        return incoming
    }

    override fun wildcard(type: WildcardType, descs: Map<Namespace, FieldOrMethodDescriptor>): MutableWildcardMapping {
        val incoming = WildcardMappingData(type, descs.toNsMap())
        incoming.descRemapper = descRemapper
        for (existing in wildcards) {
            if (incoming.mergeInto(existing)) return existing
        }
        wildcards.add(incoming)
        return incoming
    }

    override fun addMetadata(meta: MetadataElement) {
        metadata.mergeMetadata(meta)
    }

    override fun toString() = "ClassMappingData($names)"

}
