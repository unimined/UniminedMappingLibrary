package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

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

    suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String> = mapOf(),
        settings: FormatReaderSettings = this,
    )

}