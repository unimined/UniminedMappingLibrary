package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatReader {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader").lowercase()

    fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean

    fun getSide(fileName: String, inputType: BufferedSource): Set<EnvType> = EnvType.entries.toSet()

    suspend fun read(inputType: BufferedSource, nsMapping: Map<String, String> = mapOf()): MappingTree = MappingTree().also { read(EnvType.JOINED, inputType, it, it, nsMapping) }

    suspend fun read(envType: EnvType, inputType: BufferedSource, nsMapping: Map<String, String> = mapOf()): MappingTree = MappingTree().also { read(envType, inputType, it, it, nsMapping) }

    suspend fun read(envType: EnvType, inputType: BufferedSource, into: MappingTree, nsMapping: Map<String, String> = mapOf()) = read(envType, inputType, into, into, nsMapping)

    suspend fun read(envType: EnvType, inputType: BufferedSource, context: MappingTree?, into: MappingVisitor, nsMapping: Map<String, String> = mapOf()) {
        read(envType, CharReader(inputType.readUtf8()), context, into, nsMapping)
    }

    suspend fun read(envType: EnvType, input: CharReader, context: MappingTree?, into: MappingVisitor, nsMapping: Map<String, String> = mapOf()) {}

}

inline fun <reified T, U> checked(value: Any?, action: T.() -> U?): U? {
    if (value == null) return null
    if (value !is T) {
        throw IllegalArgumentException("Expected ${T::class.simpleName}, found ${value.let { it::class.simpleName}}")
    }
    return action(value)
}