package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Writer
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

actual class FormatRegistryImpl : FormatRegistry() {
    private val readers = mutableListOf<FormatReader>()
    private val writers = mutableMapOf<String, FormatWriter>()

    init {
        registerFormat(UMFReader, UMFWriter)
        registerFormat(TinyV2Reader, TinyV2Writer)
    }

    fun registerFormat(reader: FormatReader, writer: FormatWriter?) {
        readers.add(reader)
        if (writer != null) {
            writers[writer.name] = writer
        }
    }

    actual override fun validFormatReaders(
        fileName: String,
        inputType: BufferedSource
    ): List<FormatReader> {
        return readers.filter { it.isFormat(fileName, inputType) }
    }

    override fun writer(type: String, output: BufferedSink): MappingVisitor {
        return writers[type]!!.write(output)
    }

}