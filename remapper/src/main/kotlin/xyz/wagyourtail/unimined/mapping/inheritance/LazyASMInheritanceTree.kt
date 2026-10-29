package xyz.wagyourtail.unimined.mapping.inheritance

import no.synth.kmpzip.zip.ZipFile
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

/**
 * Supertypes-only lazy [InheritanceTree]: library classes materialize
 * on demand as member-group resolution walks *up* hierarchies, so jars
 * whose classes never connect to the input's supertypes are never
 * parsed — only a name→jar index is built upfront (zip listings, no
 * class parsing).
 *
 * Semantics vs [ASMInheritanceTree]: [classes] enumerates only
 * *materialized* classes (the eagerly-seeded input plus whatever
 * resolution pulled in). That is safe for `MemberGroups`/`propagate` —
 * they iterate the mapping tree (input classes) and walk supertypes —
 * but NOT for consumers that iterate [classes] or query
 * [ClassInfo.children] expecting full classpath coverage (e.g.
 * `InheritanceBackedMappingTree`); those need the eager tree.
 */
class LazyASMInheritanceTree(
    override val fns: Namespace,
    input: ASMInheritanceTree.ScanData,
    private val index: Map<InternalName, Path>,
) : InheritanceTree() {

    private val materialized = ConcurrentHashMap<InternalName, ClassInfo>()
    private val misses = ConcurrentHashMap.newKeySet<InternalName>()
    private val zipCache = ConcurrentHashMap<Path, ZipFile>()

    init {
        input.classes.forEach { (name, raw) -> materialized[name] = toClassInfo(name, raw) }
    }

    private fun toClassInfo(name: InternalName, raw: ASMInheritanceTree.RawClassInfo) =
        ClassInfo(name, raw.superType, raw.interfaces).apply {
            for (m in raw.methods) methods.add(MethodInfo(m.name, m.descriptor, m.access))
            for (f in raw.fields) fields.add(FieldInfo(f.name, f.descriptor, f.access))
        }

    private fun materialize(name: InternalName): ClassInfo? {
        materialized[name]?.let { return it }
        if (name in misses) return null
        val jar = index[name]
        if (jar == null) {
            misses += name
            return null
        }
        val zf = zipCache.computeIfAbsent(jar) { ZipFile(Files.readAllBytes(it)) }
        val entry = zf.entries.firstOrNull { it.name == "$name.class" }
        if (entry == null) {
            misses += name
            return null
        }
        val bytes = zf.getInputStream(entry).use { it.readBytes() }
        val info = toClassInfo(name, ASMInheritanceTree.scanClass("$name.class", bytes).second)
        return materialized.putIfAbsent(name, info) ?: info
    }

    override val classes: Map<InternalName, ClassInfo> = object : Map<InternalName, ClassInfo> {
        override val size get() = materialized.size
        override fun isEmpty() = materialized.isEmpty()
        override fun containsKey(key: InternalName) = materialize(key) != null
        override fun containsValue(value: ClassInfo) = materialized.containsValue(value)
        override fun get(key: InternalName) = materialize(key)
        override val keys get() = materialized.keys.toSet()
        override val values get() = materialized.values.toList()
        override val entries get() = materialized.entries.toSet()
    }

    companion object {

        /** Build a class-name → jar index for [jars] — zip listings
         *  only, no class parsing. Duplicate names resolve to the
         *  first jar on the classpath. */
        fun index(jars: Set<Path>): Map<InternalName, Path> =
            jars.parallelStream().flatMap { jar ->
                ZipFile(Files.readAllBytes(jar)).use { zf ->
                    zf.entries.asSequence()
                        .filter {
                            !it.isDirectory &&
                                it.name.endsWith(".class") &&
                                !it.name.startsWith("META-INF/versions")
                        }
                        .map { InternalName.read(it.name.removeSuffix(".class")) to jar }
                        .toList().stream()
                }
            }.collect(Collectors.toMap({ it.first }, { it.second }, { a, _ -> a }))
    }

}
