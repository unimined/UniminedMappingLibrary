package xyz.wagyourtail.unimined.mapping.formats

import okio.Buffer
import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatReader {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader").lowercase()

    fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean

    fun getSide(fileName: String, inputType: BufferedSource): Set<EnvType> = EnvType.entries.toSet()

    suspend fun read(fileStr: String, nsMapping: Map<String, String> = mapOf()): MemoryMappingTree = Buffer().use {
        it.writeUtf8(fileStr)
        read(it, nsMapping)
    }

    suspend fun read(envType: EnvType, fileStr: String, nsMapping: Map<String, String> = mapOf()): MemoryMappingTree = Buffer().use {
        it.writeUtf8(fileStr)
        read(envType, it, nsMapping)
    }

    suspend fun read(envType: EnvType, fileStr: String, into: AbstractMappingTree, nsMapping: Map<String, String> = mapOf()) = Buffer().use {
        it.writeUtf8(fileStr)
        read(envType, it, into, nsMapping)
    }

    suspend fun read(envType: EnvType, fileStr: String, context: AbstractMappingTree?, into: MappingVisitor, nsMapping: Map<String, String> = mapOf()) = CharReader(fileStr).use {
        read(envType, it, context, into, nsMapping)
    }

    suspend fun read(inputType: BufferedSource, nsMapping: Map<String, String> = mapOf()): MemoryMappingTree = MemoryMappingTree().also { read(EnvType.JOINED, inputType, it, it, nsMapping) }

    suspend fun read(envType: EnvType, inputType: BufferedSource, nsMapping: Map<String, String> = mapOf()): MemoryMappingTree = MemoryMappingTree().also { read(envType, inputType, it, it, nsMapping) }

    suspend fun read(envType: EnvType, inputType: BufferedSource, into: AbstractMappingTree, nsMapping: Map<String, String> = mapOf()) = read(envType, inputType, into, into, nsMapping)

    suspend fun read(envType: EnvType, inputType: BufferedSource, context: AbstractMappingTree?, into: MappingVisitor, nsMapping: Map<String, String> = mapOf()) {
        read(envType, CharReader(inputType.readUtf8()), context, into, nsMapping)
    }

    suspend fun read(envType: EnvType, input: CharReader, context: AbstractMappingTree?, into: MappingVisitor, nsMapping: Map<String, String> = mapOf())

}