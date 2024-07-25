package xyz.wagyourtail.unimined.mapping.resolver

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatProvider
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipFS
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.util.*
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.copyTo
import xyz.wagyourtail.unimined.mapping.visitor.delegate.nsFiltered
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import kotlin.jvm.JvmOverloads

@Scoped
abstract class MappingResolver<T : MappingResolver<T>>(val name: String) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    var finalized = false
        private set

    open var envType by FinalizeOnRead(EnvType.JOINED)

    private val _entries = finalizableMapOf<String, MappingEntry>()
    val entries: Map<String, MappingEntry> get() = _entries

    val afterLoad = finalizableListOf<MemoryMappingTree.() -> Unit>()

    lateinit var namespaces: Map<Namespace, Boolean>
        private set

    lateinit var resolved: MemoryMappingTree
        private set

    open val unmappedNs = setOf(Namespace("official"))

    open suspend fun combinedNames(): String {
        finalize()
        return entries.entries.sortedBy { it.key }.joinToString("-") { it.value.id }
    }

    open fun propogator(tree: MemoryMappingTree): MemoryMappingTree = tree

    fun checkedNsOrNull(name: String): Namespace? {
        val ns = Namespace(name)
        if (namespaces.keys.contains(ns) || ns in unmappedNs) {
            return ns
        }
        return null
    }

    fun checkedNs(name: String): Namespace {
        return checkedNsOrNull(name) ?: throw IllegalArgumentException("Unknown namespace $name")
    }

    fun isOfficial(name: String): Boolean {
        return checkedNs(name) in unmappedNs
    }

    fun isIntermediary(name: String): Boolean {
        val ns = checkedNs(name)
        if (ns in unmappedNs) return false
        return !namespaces.getValue(ns)
    }

    fun isNamed(name: String): Boolean {
        return namespaces.getValue(checkedNs(name))
    }

    open suspend fun finalize() {
        finalized = true
        _entries.finalize()
        _entries.values.forEach { it.finalize() }
        afterLoad.finalize()
    }

    fun addDependency(key: String, dependency: MappingEntry) {
        if (_entries.containsKey(key)) {
            LOGGER.warn { "Overwriting dependency $key" }
        }
        _entries[key] = dependency
    }

    abstract fun createForPostProcess(key: String): T

    @JvmOverloads
    fun postProcessDependency(key: String, intern: T.() -> Unit, postProcess: MappingEntry.() -> Unit = {}) {
        val resolver = createForPostProcess(key)
        resolver.intern()

        addDependency(key, MappingEntry(object : ContentProvider {
            var mappings: Buffer? = null

            override suspend fun resolve() {
                val tree = resolver.resolve()
                mappings = Buffer().apply {
                    tree.accept(UMFWriter.write(this))
                }
            }

            override fun fileName(): String {
                return "$key.umf"
            }

            override fun content(): BufferedSource {
                return mappings!!.peek()
            }

        }, key).also(postProcess))
    }

    private val resolveLock = Mutex()

    open suspend fun fromCache(key: String): MemoryMappingTree? = null

    open suspend fun writeCache(key: String, tree: MemoryMappingTree) {}

    open suspend fun afterLoad(tree: MemoryMappingTree) {
        afterLoad.forEach { it(tree) }
    }

    open suspend fun resolve(): MemoryMappingTree {
        if (::resolved.isInitialized) return resolved
        return resolveLock.withLock {
            finalize()
            val values = _entries.values
            val resolvedEntries = mutableSetOf<MappingEntry>()

            for (entry in values) {
                resolvedEntries.addAll(entry.expand())
            }

            val sorted = mutableListOf<MappingEntry>()
            val sortedNs = unmappedNs.toMutableSet()

            while (resolvedEntries.isNotEmpty()) {
                val toRemove = mutableSetOf<MappingEntry>()
                for (entry in resolvedEntries) {
                    if (entry.requires.let { sortedNs.contains(it) }) {
                        toRemove.add(entry)
                    }
                }
                if (toRemove.isEmpty()) {
                    //TODO: better logging, determine case
                    LOGGER.error { "UnmappedNs: $unmappedNs" }
                    for (entry in sorted) {
                        LOGGER.error { "Resolved: ${entry.id}" }
                        LOGGER.error { "    requires: ${entry.requires}" }
                        LOGGER.error { "    provides: ${entry.provides.map { it.first }}" }
                    }
                    for (entry in resolvedEntries) {
                        LOGGER.error { "Unresolved: ${entry.id}" }
                        LOGGER.error { "    requires: ${entry.requires}" }
                        LOGGER.error { "    provides: ${entry.provides.map { it.first }}" }
                    }
                    throw IllegalStateException("Circular dependency detected, or missing required ns, remaining: ${resolvedEntries.map { it.id }}")
                }

                resolvedEntries.removeAll(toRemove)
                sorted.addAll(toRemove.sortedBy { FormatRegistry.formats.indexOf(it.provider) })
                sortedNs.addAll(toRemove.flatMap { it.provides.map { it.first } })
            }

            val cacheKey = "$envType-${combinedNames()}"
            var resolved = fromCache(cacheKey)

            if (resolved == null) {
                resolved = MemoryMappingTree()
                resolved.visitHeader(*unmappedNs.map { it.name }.toTypedArray())
                for (entry in sorted) {
                    val visitor =
                        entry.insertInto.fold(resolved.nsFiltered((entry.provides.map { it.first } + entry.requires).toSet()) as MappingVisitor) { acc, it ->
                            it(acc)
                        }
                    try {
                        entry.provider.read(
                            entry.content.content(),
                            resolved,
                            visitor,
                            envType,
                            entry.mapNs.map { it.key.name to it.value.name }.toMap()
                        )
                    } catch (e: Throwable) {
                        throw IllegalStateException("Error reading ${entry.id}", e)
                    }
                }

                resolved = propogator(resolved)

                val filled = mutableSetOf<Namespace>()
                for (entry in sorted) {
                    val toFill = entry.provides.map { it.first }.toSet() - filled
                    if (toFill.isNotEmpty()) {
                        resolved.accept(resolved.copyTo(entry.requires, toFill, resolved))
                        filled.addAll(toFill)
                    }
                }

                afterLoad(resolved)

                writeCache(cacheKey, resolved)
            }

            this.namespaces = sorted.flatMap { it.provides }.associate { it.first to it.second }
            this.resolved = resolved
            resolved
        }
    }

    open inner class MappingEntry(content: ContentProvider, open val id: String) : MappingConfig(content) {
        private val subEntries = finalizableSetOf<MappingConfig.(ContentProvider, FormatProvider) -> Unit>()

        override suspend fun finalize() {
            super.finalize()
            subEntries.finalize()
            content.resolve()
        }

        fun subEntry(sub: MappingConfig.(ContentProvider, FormatProvider) -> Unit) {
            subEntries.add(sub)
        }

        suspend fun expand(): Set<MappingEntry> {
            finalize()
            val contents = content.content()
            return if (contents.isZip()) {
                val inner = mutableSetOf<MappingEntry>()
                ZipFS(contents).use { zip ->
                    val files = zip.getFiles().associateWithNonNull { FormatRegistry.autodetectFormat(envType, it.replace('\\', '/'), zip.getContents(it)) }
                    for ((file, format) in files.entries) {
                        if (!format.getSide(file, zip.getContents(file)).contains(envType)) continue
                        val fileName = file.replace("\\", "/").substringAfterLast("/")
                        val provider = ContentProvider.of(fileName, zip.getContents(file))
                        val entry = MappingEntry(provider, "$id/${fileName.substringBeforeLast(".")}")
                        entry.combineWith(this)
                        entry.provider = format
                        this.subEntries.forEach { entry.it(provider, format) }
                        inner.add(entry)
                        entry.finalize()
                    }
                }
                inner
            } else {
                return setOf(this)
            }
        }

        override fun toString(): String {
            return id
        }
    }

    open inner class MappingConfig(val content: ContentProvider) {
        var requires: Namespace by FinalizeOnRead(Namespace("official"))
        val provides = finalizableSetOf<Pair<Namespace, Boolean>>()
        val mapNs = finalizableMapOf<Namespace, Namespace>()

        var skip by FinalizeOnRead(false)

        val insertInto = finalizableSetOf<(MappingVisitor) -> MappingVisitor>()

        var provider by FinalizeOnRead(LazyMutable {
            val format = FormatRegistry.autodetectFormat(envType, content.fileName(), content.content())
            format ?: throw IllegalArgumentException("Unknown format for ${content.fileName()}")
        })

        fun requires(ns: String) {
            requires = Namespace(ns)
        }

        fun provides(ns: String, named: Boolean) {
            provides.add(Namespace(ns) to named)
        }

        fun provides(vararg ns: Pair<String, Boolean>) {
            provides.addAll(ns.map { Namespace(it.first) to it.second })
        }

        fun mapNamespace(from: String, to: String) {
            mapNs[Namespace(from)] = Namespace(to)
        }

        fun mapNamespace(vararg ns: Pair<String, String>) {
            mapNs.putAll(ns.map { Namespace(it.first) to Namespace(it.second) })
        }

        fun combineWith(other: MappingConfig) {
            requires = other.requires
            provides.addAll(other.provides)
            mapNs.putAll(other.mapNs)
            skip = other.skip
            insertInto.addAll(other.insertInto)
        }

        open suspend fun finalize() {
            requires.name
            provides.finalize()
            insertInto.finalize()
        }

    }

}