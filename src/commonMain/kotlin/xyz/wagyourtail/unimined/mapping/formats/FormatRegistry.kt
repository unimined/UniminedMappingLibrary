package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.mcp.MCPExceptionReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3ClassesReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6ParamReader
import xyz.wagyourtail.unimined.mapping.formats.nests.NestReader
import xyz.wagyourtail.unimined.mapping.formats.rgs.RetroguardReader
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
        FormatProvider(ZipReader, null),
        FormatProvider(UMFReader, UMFWriter),
        FormatProvider(TinyV2Reader, TinyV2Writer),
        FormatProvider(NestReader, null),
        FormatProvider(UnpickReader, null),
        FormatProvider(SrgReader, SrgWriter),
        FormatProvider(RetroguardReader, null),
        FormatProvider(MCPv1MethodReader, null, listOf(RetroguardReader)),
        FormatProvider(MCPv1FieldReader, null, listOf(RetroguardReader)),
        FormatProvider(MCPv3ClassesReader, null),
        FormatProvider(MCPv3MethodReader, null, listOf(MCPv3ClassesReader)),
        FormatProvider(MCPv3FieldReader, null, listOf(MCPv3ClassesReader)),
        FormatProvider(MCPExceptionReader, null, listOf(RetroguardReader.name, MCPv3ClassesReader.name, SrgReader.name, "tsrg", "tsrg2")),
        FormatProvider(MCPv6MethodReader, null, listOf(SrgReader.name, "tsrg", "tsrg2")),
        FormatProvider(MCPv6FieldReader, null, listOf(SrgReader.name, "tsrg", "tsrg2")),
        FormatProvider(MCPv6ParamReader, null, listOf(SrgReader.name, "tsrg", "tsrg2", MCPExceptionReader.name)),
    )

    private val _formats = mutableListOf<FormatProvider>()
    val formats: List<FormatProvider>
        get() = _formats

    val byName: Map<String, FormatProvider> by lazy {
        formats.associateBy { it.name }
    }

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
        return formats.firstOrNull { it.reader.isFormat(envType, fileName, inputType.peek()) }
    }

}

expect class FormatSupplier() {

    val providers: List<FormatProvider>

}

class FormatProvider(val reader: FormatReader, val writer: FormatWriter?, val mustBeAfter: List<String> = listOf()) {

    constructor(reader: FormatReader, writer: FormatWriter?, mustBeAfter: List<FormatReader>): this(reader, writer, mustBeAfter.map { it.name })

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