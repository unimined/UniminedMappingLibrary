package xyz.wagyourtail.unimined.mapping.resolver

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatProvider
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipFS
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.util.*
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.copyTo
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.nsFiltered
import xyz.wagyourtail.unimined.util.FinalizeOnRead
import kotlin.jvm.JvmOverloads

open class MappingResolver(val name: String, val propogator: (MemoryMappingTree.() -> Unit)?) {
    companion object {
        private val LOGGER = KotlinLogging.logger {}
    }

    var envType by FinalizeOnRead(EnvType.JOINED)
    private val entries = finalizableMapOf<String, MappingEntry>()

    private lateinit var namespaces: Set<Pair<Namespace, Boolean>>
    private lateinit var resolved: MemoryMappingTree

    val unmappedNs = Namespace("official")

    suspend fun finalize() {
        entries.finalize()
        entries.values.forEach { it.finalize() }
    }


    fun addDependency(key: String, dependency: MappingEntry) {
        if (entries.containsKey(key)) {
            LOGGER.warn { "Overwriting dependency $key" }
        }
        entries[key] = dependency
    }

    open fun createForPostProcess(key: String) = MappingResolver("$name::$key", propogator)

    @JvmOverloads
    fun postProcessDependency(key: String, intern: MappingResolver.() -> Unit, postProcess: MappingEntry.() -> Unit = {}) {
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

        }).also(postProcess))
    }

    suspend fun resolve(): MemoryMappingTree {
        if (::resolved.isInitialized) return resolved
        finalize()
        val values = entries.values
        val resolved = MemoryMappingTree()
        resolved.visitHeader(unmappedNs.name)
        val resolvedEntries = mutableSetOf<MappingEntry>()

        for (entry in values) {
            resolvedEntries.addAll(entry.expand())
        }

        val sorted = mutableListOf<MappingEntry>()
        val sortedNs = mutableSetOf(unmappedNs)

        while (resolvedEntries.isNotEmpty()) {
            val toRemove = mutableSetOf<MappingEntry>()
            for (entry in resolvedEntries) {
                if (entry.requires.let { sortedNs.contains(it) }) {
                    toRemove.add(entry)
                }
            }
            if (toRemove.isEmpty()) {
                //TODO: better logging, determine case
                throw IllegalStateException("Circular dependency detected, or missing required ns")
            }

            resolvedEntries.removeAll(toRemove)
            sorted.addAll(toRemove.sortedBy { FormatRegistry.formats.indexOf(it.provider) })
            sortedNs.addAll(toRemove.flatMap { it.provides.map { it.first } })
        }

        for (entry in sorted) {
            val visitor = entry.insertInto.fold(resolved.nsFiltered((entry.provides.map { it.first } + entry.requires).toSet()) as MappingVisitor) { acc, it -> it(acc) }
            entry.provider.read(envType, entry.content.content(), resolved, visitor, entry.mapNs.map { it.key.name to it.value.name }.toMap())
        }

        for (entry in sorted) {
            for (afterLoad in entry.afterLoad) {
                afterLoad(resolved)
            }
        }

        if (propogator != null) {
            propogator.invoke(resolved)
            val filled = mutableSetOf<Namespace>()
            for (entry in sorted) {
                val toFill = entry.provides.map { it.first }.toSet() - filled
                if (toFill.isNotEmpty()) {
                    resolved.accept(resolved.copyTo(entry.requires, toFill, resolved))
                    filled.addAll(toFill)
                }
                for (afterPropogate in entry.afterPropogate) {
                    afterPropogate(resolved)
                }
            }
        } else {
            // at least copy the class names
            val filled = mutableSetOf<Namespace>()
            for (entry in sorted) {
                val toFill = entry.provides.map { it.first }.toSet() - filled
                if (toFill.isNotEmpty()) {
                    resolved.accept(resolved.copyTo(entry.requires, toFill, resolved).delegator(object : NullDelegator() {
                        override fun visitClass(
                            delegate: MappingVisitor,
                            names: Map<Namespace, InternalName>
                        ): ClassVisitor? {
                            return default.visitClass(delegate, names)
                        }
                    }))
                    filled.addAll(toFill)
                }
            }
        }

        this.namespaces = sorted.flatMap { it.provides }.toSet()
        this.resolved = resolved
        return resolved
    }

    inner class MappingEntry(content: ContentProvider) : MappingConfig(content) {
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
                        val provider = ContentProvider.of(file, zip.getContents(file))
                        val entry = MappingEntry(provider)
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

    }

    open inner class MappingConfig(val content: ContentProvider) {
        var requires: Namespace by FinalizeOnRead(unmappedNs)
        val provides = finalizableSetOf<Pair<Namespace, Boolean>>()
        val mapNs = finalizableMapOf<Namespace, Namespace>()

        var skip by FinalizeOnRead(false)

        val insertInto = finalizableSetOf<(MappingVisitor) -> MappingVisitor>()
        val afterLoad = finalizableSetOf<(MemoryMappingTree) -> Unit>()
        val afterPropogate = finalizableSetOf<(MemoryMappingTree) -> Unit>()

        var provider by FinalizeOnRead(LazyMutable {
            val format = FormatRegistry.autodetectFormat(envType, content.fileName(), content.content())
            format ?: throw IllegalArgumentException("Unknown format for ${content.fileName()}")
        })

        fun requires(ns: String) {
            requires = Namespace(ns)
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
            afterLoad.addAll(other.afterLoad)
            afterPropogate.addAll(other.afterPropogate)
        }

        open suspend fun finalize() {
            requires.name
            provides.finalize()
            insertInto.finalize()
            afterLoad.finalize()
            afterPropogate.finalize()
        }

    }

}