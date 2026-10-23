package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit

interface FormatReader : FormatReaderSettings {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean

    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader").lowercase()

    fun isFormat(fileName: String, input: BufferedSource): Boolean

    fun getSide(fileName: String, input: BufferedSource): Set<EnvType> = EnvType.entries.toSet()

    suspend fun read(
        content: String,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ): MutableMappingTree = MutableMappingTree().also {
        read(StringCharReader(content.replace("\r", "")), null, it, nsMapping, settings)
    }

    suspend fun read(
        content: String,
        into: MutableMappingTree,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ) {
        read(StringCharReader(content.replace("\r", "")), into, into, nsMapping, settings)
    }

    suspend fun read(
        content: String,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ) = read(StringCharReader(content.replace("\r", "")), context, into, nsMapping, settings)

    suspend fun read(
        input: BufferedSource,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ): MutableMappingTree = MutableMappingTree().also {
        read(input, null, it, nsMapping, settings)
    }

    suspend fun read(
        input: BufferedSource,
        into: MutableMappingTree,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ) {
        read(StringCharReader(input.readUtf8().replace("\r", "")), into, into, nsMapping, settings)
    }

    suspend fun read(
        input: BufferedSource,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ) {
        read(StringCharReader(input.readUtf8().replace("\r", "")), context, into, nsMapping, settings)
    }

    suspend fun read(
        input: CharReader<*>,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ): MutableMappingTree = MutableMappingTree().also {
        read(input, null, it, nsMapping, settings)
    }

    suspend fun read(
        input: CharReader<*>,
        into: MutableMappingTree,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    ) {
        read(input, into, into, nsMapping, settings)
    }

    /**
     * Stage through a temp tree: the reader writes its own namespaces into
     * [readImpl]'s staging tree, then `emit` pushes every element into [into]
     * with [nsMapping] applied at emit time. Sinks that transform on push —
     * e.g. [InheritanceBackedMappingTree] propagating names — see the same
     * element flow a direct write would have produced.
     */
    suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
        /** runs on the staged tree between read and emit — e.g. fold access
         *  changes into an inheritance graph before emit pushes propagate:
         *  `read(..., into = backed) { graph.applyAccessChanges(it) }` */
        prepare: (MutableMappingTree) -> Unit = {},
    ) {
        val temp = MutableMappingTree()
        readImpl(input, context, temp, emptyMap(), settings)
        prepare(temp)
        temp.emit(into, nsRenamer = { nsMapping[it.name]?.let { n -> Namespace(n) } ?: it })
    }

    /**
     * Implementation entry — readers write elements to [into] as they parse.
     * nsMapping is handled by the emit stage of [read]; implementers should
     * write the file's own namespaces verbatim.
     */
    suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    )

}