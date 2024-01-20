package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Writer
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipReader
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object FormatRegistry {

    val builtinFormats = listOf(
        FormatProvider(UMFReader, UMFWriter, listOf()),
        FormatProvider(TinyV2Reader, TinyV2Writer, listOf()),
        FormatProvider(ZipReader, null, listOf())
    )

    private val _formats = mutableListOf<FormatProvider>()
    val formats: List<FormatProvider>
        get() = _formats

    init {
        val formatBuffer = mutableListOf<FormatProvider>()
        formatBuffer.addAll(builtinFormats)
        formatBuffer.addAll(FormatSupplier().providers)

        // sort formats by mustBeAfter
        for (format in formatBuffer) {
            registerFormat(format)
        }
    }

    fun registerFormat(format: FormatProvider) {
        val mustBeAfter = format.mustBeAfter.toMutableSet()
        if (mustBeAfter.isEmpty()) {
            _formats.add(0, format)
            return
        }
        for (i in formats.indices) {
            val name = formats[i].name
            if (mustBeAfter.contains(name)) {
                mustBeAfter.remove(name)
                if (mustBeAfter.isEmpty()) {
                    _formats.add(i + 1, format)
                    return
                }
            }
        }
        _formats.add(format)
    }

    fun autodetectFormat(fileName: String, inputType: BufferedSource): FormatProvider? {
        return formats.firstOrNull { it.reader.isFormat(fileName, inputType) }
    }

    suspend fun read(fileName: String, inputType: BufferedSource, unnamedNamespaceNames: List<String> = listOf()) = MappingTree().also { read(fileName, inputType, it, it, unnamedNamespaceNames) }

    suspend fun read(fileName: String, inputType: BufferedSource, context: MappingTree?, into: MappingVisitor, unnamedNamespaceNames: List<String> = listOf()) {
        autodetectFormat(fileName, inputType)?.read(inputType, context, into, unnamedNamespaceNames)  ?: throw IllegalArgumentException("No valid format readers for $fileName")
    }

    fun writer(type: String, output: BufferedSink): MappingVisitor {
        return formats.filter { it.name == type }.map { it.writer }.firstOrNull()?.write(output) ?: throw IllegalArgumentException("No valid format writers for $type")
    }

}

expect class FormatSupplier() {

    val providers: List<FormatProvider>

}

class FormatProvider(val reader: FormatReader, val writer: FormatWriter?, val mustBeAfter: List<String>) {

    val name: String
        get() = reader.name

    fun isFormat(fileName: String, inputType: BufferedSource): Boolean {
        return reader.isFormat(fileName, inputType)
    }

    suspend fun read(inputType: BufferedSource, context: MappingTree?, into: MappingVisitor, unnamedNamespaceNames: List<String>) {
        reader.read(inputType, context, into, unnamedNamespaceNames)
    }

    fun write(output: BufferedSink): MappingVisitor {
        return writer!!.write(output)
    }

}