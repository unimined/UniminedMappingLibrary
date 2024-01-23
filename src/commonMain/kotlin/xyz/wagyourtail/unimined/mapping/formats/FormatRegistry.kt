package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.nests.NestReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgWriter
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.tinyv2.TinyV2Writer
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.unpick.UnpickReader
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipReader
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object FormatRegistry {

    val builtinFormats = listOf(
        FormatProvider(ZipReader, null, listOf()),
        FormatProvider(UMFReader, UMFWriter, listOf()),
        FormatProvider(TinyV2Reader, TinyV2Writer, listOf()),
        FormatProvider(NestReader, null, listOf()),
        FormatProvider(UnpickReader, null, listOf()),
        FormatProvider(SrgReader, SrgWriter, listOf())
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

    fun autodetectFormat(envType: EnvType, fileName: String, inputType: BufferedSource): FormatProvider? {
        return formats.firstOrNull { it.reader.isFormat(envType, fileName, inputType) }
    }

}

expect class FormatSupplier() {

    val providers: List<FormatProvider>

}

class FormatProvider(val reader: FormatReader, val writer: FormatWriter?, val mustBeAfter: List<String>) {

    val name: String
        get() = reader.name

    fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return reader.isFormat(envType, fileName, inputType)
    }

    fun getSide(fileName: String, inputType: BufferedSource): Set<EnvType> {
        return reader.getSide(fileName, inputType)
    }

    suspend fun read(envType: EnvType, inputType: BufferedSource, context: MappingTree?, into: MappingVisitor, nsMap: Map<String, String>) {
        reader.read(envType, inputType, context, into, nsMap)
    }

    fun write(envType: EnvType, output: BufferedSink): MappingVisitor {
        return writer!!.write(envType, output)
    }

}