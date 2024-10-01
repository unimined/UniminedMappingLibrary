package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatReader {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader").lowercase()

    fun isFormat(fileName: String, input: BufferedSource, envType: EnvType = EnvType.JOINED): Boolean

    fun getSide(fileName: String, input: BufferedSource): Set<EnvType> = EnvType.entries.toSet()

    suspend fun read(
        content: String,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ): MemoryMappingTree = MemoryMappingTree().also {
        read(StringCharReader(content.replace("\r", "")), null, it, envType, nsMapping)
    }

    suspend fun read(
        content: String,
        into: AbstractMappingTree,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ) {
        read(StringCharReader(content.replace("\r", "")), into, into, envType, nsMapping)
    }

    suspend fun read(
        content: String,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ) = read(StringCharReader(content.replace("\r", "")), context, into, envType, nsMapping)

    suspend fun read(
        input: BufferedSource,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ): MemoryMappingTree = MemoryMappingTree().also {
        read(input, null, it, envType, nsMapping)
    }

    suspend fun read(
        input: BufferedSource,
        into: AbstractMappingTree,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ) {
        read(StringCharReader(input.readUtf8().replace("\r", "")), into, into, envType, nsMapping)
    }

    suspend fun read(
        input: BufferedSource,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ) {
        read(StringCharReader(input.readUtf8().replace("\r", "")), context, into, envType, nsMapping)
    }

    suspend fun read(
        input: CharReader<*>,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ): MemoryMappingTree = MemoryMappingTree().also {
        read(input, null, it, envType, nsMapping)
    }

    suspend fun read(
        input: CharReader<*>,
        into: AbstractMappingTree,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    ) {
        read(input, into, into, envType, nsMapping)
    }

    suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType = EnvType.JOINED,
        nsMapping: Map<String, String> = mapOf()
    )

}