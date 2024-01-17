package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatReader {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader")

    fun isFormat(fileName: String, inputType: BufferedSource): Boolean

    fun read(inputType: BufferedSource, unnamedNamespaceNames: List<String> = listOf()): MappingTree = MappingTree().also { read(inputType, it, unnamedNamespaceNames) }

    fun read(inputType: BufferedSource, into: MappingVisitor, unnamedNamespaceNames: List<String> = listOf())

}

inline fun <reified T, U> checked(value: Any?, action: T.() -> U?): U? {
    if (value == null) return null
    if (value !is T) {
        throw IllegalArgumentException("Expected ${T::class.simpleName}, found ${value.let { it::class.simpleName}}")
    }
    return action(value)
}