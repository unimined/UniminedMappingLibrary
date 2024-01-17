package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import java.util.ServiceLoader

actual class FormatRegistryImpl : FormatRegistry() {
    private val formatReaders = ServiceLoader.load(FormatReader::class.java).toList()
    private val formatWriters = ServiceLoader.load(FormatWriter::class.java).toList().associateBy { it.name }

    actual override fun validFormatReaders(
        fileName: String,
        inputType: BufferedSource
    ): List<FormatReader> {
        return formatReaders.filter { it.isFormat(fileName, inputType) }
    }

    override fun writer(type: String, output: BufferedSink): MappingVisitor {
        return formatWriters[type]!!.write(output)
    }


}