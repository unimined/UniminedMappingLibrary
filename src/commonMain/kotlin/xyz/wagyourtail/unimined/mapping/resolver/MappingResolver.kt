package xyz.wagyourtail.unimined.mapping.resolver

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.collection.finalizable.finalizableMapOf
import xyz.wagyourtail.commonskt.collection.finalizable.finalizableSetOf
import xyz.wagyourtail.commonskt.properties.FinalizeOnRead
import xyz.wagyourtail.commonskt.properties.LazyMutable
import xyz.wagyourtail.commonskt.utils.associateWithNonNull
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatProvider
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipFS
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.sink.nsFiltered
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree
import xyz.wagyourtail.unimined.mapping.tree.impl.fillMissingNames
import xyz.wagyourtail.unimined.mapping.tree.impl.renest
import xyz.wagyourtail.unimined.mapping.util.Scoped
import xyz.wagyourtail.unimined.mapping.util.isZip
import kotlin.js.JsName
import kotlin.jvm.JvmOverloads
import kotlin.time.measureTime

@Scoped
abstract class MappingResolver<T : MappingResolver<T>>(val name: String) : FormatReaderSettings {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    override var unchecked: Boolean by FinalizeOnRead(false)
    override var leinient: Boolean by FinalizeOnRead(false)

    var finalized = false
        private set

    override var envType by FinalizeOnRead(EnvType.JOINED)

    private val _entries = finalizableMapOf<String, MappingEntry>()
    val entries: Map<String, MappingEntry> get() = _entries

    protected lateinit var namespaces: Map<Namespace, Boolean>
        private set

    protected lateinit var resolved: MappingTree
        private set

    open val unmappedNs = setOf(Namespace("official"))

    open suspend fun combinedNames(): String {
        finalize()
        return entries.entries.sortedBy { it.key }.joinToString("-") { it.value.id }
    }

    open suspend fun applyInheritanceTree(tree: MutableMappingTree, apply: suspend (InheritanceTree) -> MutableMappingTree): MutableMappingTree = tree

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
    }

    fun addDependency(key: String, dependency: MappingEntry) {
        if (_entries.containsKey(key)) {
            LOGGER.warn { "Overwriting dependency $key" }
        }
        _entries[key] = dependency
    }

    abstract fun createForPostProcess(key: String, process: MutableMappingTree.() -> Unit): T

    @JvmOverloads
    fun postProcessDependency(key: String,
                              intern: @Scoped T.() -> Unit,
                              process: MutableMappingTree.() -> Unit = { },
                              postProcess: MappingEntry.() -> Unit) {
        val resolver = createForPostProcess(key, process)
        resolver.intern()

        addDependency(key, MappingEntry(object : ContentProvider {
            var mappings: Buffer? = null

            override suspend fun resolve() {
                val tree = resolver.resolve()
                mappings = Buffer().apply {
                    tree.emit(UMFWriter.write(this))
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

    open suspend fun fromCache(key: String): MappingTree? = null

    open suspend fun writeCache(key: String, tree: MappingTree) {}

    open suspend fun afterLoad(tree: MutableMappingTree) {}

    open suspend fun resolve(): MappingTree {
        if (::resolved.isInitialized) return resolved
        return resolveLock.withLock {
            if (::resolved.isInitialized) return@withLock resolved
            finalize()
            LOGGER.info { "Resolving mappings $name..." }

            val values = _entries.values
            val resolvedEntries = mutableSetOf<MappingEntry>()

            val sorted = mutableListOf<MappingEntry>()
            val sortedNs = unmappedNs.toMutableSet()

            measureTime {
                for (entry in values) {
                    resolvedEntries.addAll(entry.expand().filter { !it.skip })
                }

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
            }.also {
                LOGGER.info { "Resolved ${sorted.size} entries in $it" }
            }

            val cacheKey = "$envType-${combinedNames()}"
            var resolved = fromCache(cacheKey)

            if (resolved == null) {
                resolved = MutableMappingTree()
                resolved.header(unmappedNs.toList())

                measureTime {
                    for (entry in sorted) {
                        LOGGER.info { "Reading: $entry" }
                        val visitor =
                            entry.insertInto.fold(resolved.nsFiltered((entry.provides.map { it.first } + entry.requires).toSet())) { acc, it ->
                                it(acc)
                            }
                        try {
                            val targetTree = if (entry.preProcess.isNotEmpty()) {
                                MutableMappingTree()
                            } else {
                                null
                            }
                            entry.provider.read(
                                entry.content.content(),
                                resolved,
                                targetTree ?: visitor,
                                entry.mapNs.map { it.key.name to it.value.name }.toMap(),
                                entry as FormatReaderSettings,
                            )
                            if (targetTree != null) {
                                entry.preProcess(targetTree)
                                targetTree.emit(visitor)
                            }
                        } catch (e: Throwable) {
                            throw IllegalStateException("Error reading $entry", e)
                        }
                    }
                } .also {
                    LOGGER.info { "Read ${sorted.size} entries in $it" }
                }

                afterLoad(resolved)

                LOGGER.info { "Propagating..." }

                measureTime {
                    resolved = applyInheritanceTree(resolved) { pt ->
                        // wrap: fold access changes, emit rows through the
                        // propagating factories — incremental, no batch pass
                        InheritanceBackedMappingTree(resolved as MutableMappingTree, pt)
                    }
                }.also {
                    LOGGER.info { "Propagated in $it" }
                }

                LOGGER.info { "Renesting inner classes..." }

                val renest = defaultedMapOf<Namespace, MutableSet<Namespace>> { mutableSetOf() }
                for (entry in sorted) {
                    renest[entry.requires].addAll(entry.renest)
                }
                for ((key, value) in renest) {
                    if (value.isNotEmpty()) {
                        LOGGER.info { "Renesting $key -> ${value.joinToString(", ")}" }
                        (resolved as MutableMappingTree).renest(key, value)
                    }
                }

                LOGGER.info { "Filling in missing names..." }

                measureTime {
                    // fill in missing names from dependent namespaces
                    val filled = unmappedNs.toMutableSet()
                    val toFill = mutableListOf<Pair<Namespace, Set<Namespace>>>()
                    for (entry in sorted) {
                        val targets = entry.provides.map { it.first }.toSet() - filled
                        if (targets.isNotEmpty()) {
                            filled.addAll(targets)
                            toFill.add(entry.requires to targets)
                        }
                    }
                    (resolved as MutableMappingTree).fillMissingNames(*toFill.toTypedArray())
                }.also {
                    LOGGER.info { "Filled in missing names in $it" }
                }

                LOGGER.info { "Writing to cache" }

                writeCache(cacheKey, checkNotNull(resolved))
            } else {
                LOGGER.info { "Loaded from cache" }
            }

            LOGGER.info { "Resolving complete" }

            this.namespaces = sorted.flatMap { it.provides }.associate { it.first to it.second }

            this.resolved = checkNotNull(resolved)
            this.resolved
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
                    val files = zip.getFiles().associateWithNonNull { FormatRegistry.autodetectFormat(it.replace('\\', '/'), zip.getContents(it)) }
                    for ((file, format) in files.entries) {
                        if (!format.getSide(file, zip.getContents(file)).contains(envType)) continue
                        val fileName = file.replace("\\", "/").substringAfterLast("/")
                        val provider = ContentProvider.of(fileName, zip.getContents(file))
                        val entry = MappingEntry(provider, "$id/$fileName")
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

    open inner class MappingConfig(val content: ContentProvider) : FormatReaderSettings {

        override var unchecked: Boolean by FinalizeOnRead(LazyMutable { this@MappingResolver.unchecked })
        override var leinient: Boolean by FinalizeOnRead(LazyMutable { this@MappingResolver.leinient })

        var requires: Namespace by FinalizeOnRead(Namespace("official"))
        val provides = finalizableSetOf<Pair<Namespace, Boolean>>()
        val mapNs = finalizableMapOf<Namespace, Namespace>()
        @JsName("_renest")
        val renest = finalizableSetOf<Namespace>()

        var skip by FinalizeOnRead(false)

        val insertInto = finalizableSetOf<(MappingSink) -> MappingSink>()
        val preProcess = finalizableSetOf<(MappingTree, FormatProvider, ContentProvider) -> Unit>()

        var provider by FinalizeOnRead(LazyMutable {
            val format = FormatRegistry.autodetectFormat(content.fileName(), content.content())
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

        fun renest() {
            renest.addAll(provides.map { it.first })
        }

        fun renest(vararg ns: String) {
            renest.addAll(ns.map { Namespace(it) })
        }

        fun preProcess(pre: (MappingTree, FormatProvider, ContentProvider) -> Unit) {
            preProcess.add(pre)
        }

        internal fun preProcess(mappings: MappingTree) {
            for (pre in preProcess) {
                pre(mappings, provider, content)
            }
        }

        fun combineWith(other: MappingConfig) {
            requires = other.requires
            leinient = other.leinient
            unchecked = other.unchecked
            provides.addAll(other.provides)
            mapNs.putAll(other.mapNs)
            skip = other.skip
            insertInto.addAll(other.insertInto)
            preProcess.addAll(other.preProcess)
            renest.addAll(other.renest)
        }

        open suspend fun finalize() {
            requires.name
            provides.finalize()
            insertInto.finalize()
            mapNs.finalize()
            renest.finalize()
            preProcess.finalize()
        }

    }

}