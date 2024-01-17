package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

abstract class FormatRegistry {
    companion object {
        val instance: FormatRegistryImpl = FormatRegistryImpl()
    }

    abstract fun validFormatReaders(fileName: String, inputType: BufferedSource): List<FormatReader>

    fun read(fileName: String, inputType: BufferedSource) = MappingTree().also { read(fileName, inputType, it) }

    fun read(fileName: String, inputType: BufferedSource, into: MappingTree) {
        val validFormatReaders = validFormatReaders(fileName, inputType)
        if (validFormatReaders.isEmpty()) {
            throw IllegalArgumentException("No valid format readers for $fileName")
        }
        if (validFormatReaders.size > 1) {
            throw IllegalArgumentException("Multiple valid format readers for $fileName")
        }
        validFormatReaders[0].read(inputType, into)
    }

    abstract fun writer(type: String, output: BufferedSink): MappingVisitor

}

expect class FormatRegistryImpl(): FormatRegistry {

    override fun validFormatReaders(fileName: String, inputType: BufferedSource): List<FormatReader>

}