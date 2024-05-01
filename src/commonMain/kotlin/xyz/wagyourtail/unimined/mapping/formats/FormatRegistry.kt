package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.csrg.CsrgReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.MCPExceptionReader
import xyz.wagyourtail.unimined.mapping.formats.tsrg.TsrgV1Reader
import xyz.wagyourtail.unimined.mapping.formats.tsrg.TsrgV2Reader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3ClassesReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6ParamReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigAccessReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigConstructorReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigExceptionsReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigStaticMethodsReader
import xyz.wagyourtail.unimined.mapping.formats.nests.NestReader
import xyz.wagyourtail.unimined.mapping.formats.parchment.ParchmentReader
import xyz.wagyourtail.unimined.mapping.formats.proguard.ProguardReader
import xyz.wagyourtail.unimined.mapping.formats.rgs.RetroguardReader
import xyz.wagyourtail.unimined.mapping.formats.srg.PackageSrgReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgWriter
import xyz.wagyourtail.unimined.mapping.formats.tiny.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.tiny.TinyV2Writer
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.unpick.UnpickReader
import xyz.wagyourtail.unimined.mapping.formats.unsupported.UnsupportedWriter

object FormatRegistry {

    val builtinFormats = listOf(
//        FormatProvider(ZipReader, null),
        FormatProvider(UMFReader, UMFWriter),
        FormatProvider(TinyV2Reader, TinyV2Writer),
        FormatProvider(NestReader),
        FormatProvider(UnpickReader),
        FormatProvider(SrgReader, SrgWriter),
        FormatProvider(RetroguardReader),
        FormatProvider(MCPv1MethodReader, mustBeAfter = listOf(RetroguardReader)),
        FormatProvider(MCPv1FieldReader, mustBeAfter = listOf(RetroguardReader)),
        FormatProvider(MCPv3ClassesReader),
        FormatProvider(MCPv3MethodReader, mustBeAfter = listOf(MCPv3ClassesReader)),
        FormatProvider(MCPv3FieldReader, mustBeAfter = listOf(MCPv3ClassesReader)),
        FormatProvider(MCPExceptionReader, mustBeAfter = listOf(RetroguardReader, MCPv3ClassesReader, SrgReader, TsrgV1Reader, TsrgV2Reader)),
        FormatProvider(MCPv6MethodReader, mustBeAfter = listOf(SrgReader, TsrgV1Reader, TsrgV2Reader)),
        FormatProvider(MCPv6FieldReader, mustBeAfter = listOf(SrgReader, TsrgV1Reader, TsrgV2Reader)),
        FormatProvider(MCPv6ParamReader, mustBeAfter = listOf(SrgReader, TsrgV1Reader, TsrgV2Reader, MCPExceptionReader, MCPConfigConstructorReader, MCPConfigStaticMethodsReader)),
        FormatProvider(MCPConfigConstructorReader, mustBeAfter = listOf(TsrgV1Reader)),
        FormatProvider(MCPConfigExceptionsReader, mustBeAfter = listOf(TsrgV1Reader)),
        FormatProvider(MCPConfigAccessReader, mustBeAfter = listOf(TsrgV1Reader)),
        FormatProvider(MCPConfigStaticMethodsReader, mustBeAfter = listOf(TsrgV1Reader)),
        FormatProvider(TsrgV1Reader),
        FormatProvider(TsrgV2Reader),
        FormatProvider(ProguardReader),
        FormatProvider(ParchmentReader),
        FormatProvider(PackageSrgReader),
        FormatProvider(CsrgReader, mustBeAfter = listOf(PackageSrgReader)),
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
        return formats.firstOrNull {
            try { it.isFormat(envType, fileName, inputType.peek()) }
            catch (e: Exception) { false }
        }
    }

}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class FormatSupplier() {

    val providers: List<FormatProvider>

}

class FormatProvider(val reader: FormatReader, val writer: FormatWriter = UnsupportedWriter, val mustBeAfter: List<String> = listOf()): FormatReader by reader, FormatWriter by writer {

    companion object {

        operator fun invoke(reader: FormatReader, writer: FormatWriter = UnsupportedWriter, mustBeAfter: List<FormatReader> = listOf()): FormatProvider {
            return FormatProvider(reader, writer, mustBeAfter.map { it.name })
        }

    }
    override val name: String
        get() = reader.name

}