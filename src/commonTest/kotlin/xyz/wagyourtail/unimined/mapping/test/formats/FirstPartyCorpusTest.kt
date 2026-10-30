package xyz.wagyourtail.unimined.mapping.test.formats

import com.goncalossilva.resources.Resource
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader
import xyz.wagyourtail.unimined.mapping.formats.at.LegacyATReader
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader
import xyz.wagyourtail.unimined.mapping.formats.csrg.CsrgReader
import xyz.wagyourtail.unimined.mapping.formats.ct.CTReader
import xyz.wagyourtail.unimined.mapping.formats.feather.ExceptionReader
import xyz.wagyourtail.unimined.mapping.formats.feather.NestReader
import xyz.wagyourtail.unimined.mapping.formats.feather.SignatureReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.MCPExceptionReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v1.MCPv1MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3ClassesReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v3.MCPv3MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6FieldReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6MethodReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6PackageReader
import xyz.wagyourtail.unimined.mapping.formats.mcp.v6.MCPv6ParamReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigAccessReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigConstructorReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigExceptionsReader
import xyz.wagyourtail.unimined.mapping.formats.mcpconfig.MCPConfigStaticMethodsReader
import xyz.wagyourtail.unimined.mapping.formats.parchment.ParchmentReader
import xyz.wagyourtail.unimined.mapping.formats.proguard.ProguardReader
import xyz.wagyourtail.unimined.mapping.formats.rgs.RetroguardReader
import xyz.wagyourtail.unimined.mapping.formats.srg.PackageSrgReader
import xyz.wagyourtail.unimined.mapping.formats.srg.SrgReader
import xyz.wagyourtail.unimined.mapping.formats.tiny.v1.TinyV1Reader
import xyz.wagyourtail.unimined.mapping.formats.tiny.v2.TinyV2Reader
import xyz.wagyourtail.unimined.mapping.formats.tsrg.TsrgV1Reader
import xyz.wagyourtail.unimined.mapping.formats.tsrg.TsrgV2Reader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.formats.unpick.UnpickReader
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipReader
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** corpus loader + canonical UMF dump — shared with the jvmTest golden regen */
object FirstPartyCorpus {
    fun text(path: String) = Resource("firstparty/$path").readText()
    fun bytes(path: String) = Resource("firstparty/$path").readBytes()

    suspend fun dump(tree: MutableMappingTree): String =
        Buffer().use { tree.emit(UMFWriter.write(it, minimize = true)); it.readUtf8().trimEnd() }

    suspend fun readDump(reader: FormatReader, path: String, context: MutableMappingTree? = null): String {
        val into = context ?: MutableMappingTree()
        reader.read(Buffer().writeUtf8(text(path)), context, into)
        return dump(into)
    }

    fun golden(name: String) = Resource("firstparty/expected/$name").readText().trimEnd()

    /** golden-name → dump for every corpus case */
    suspend fun all(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        suspend fun put(name: String, reader: FormatReader, path: String, ctx: MutableMappingTree? = null) {
            out[name] = readDump(reader, path, ctx)
        }
        val srgCtx = MutableMappingTree().also { SrgReader.read(Buffer().writeUtf8(text("mcp/context.srg")), it) }
        val tsrgCtx = MutableMappingTree().also { TsrgV1Reader.read(Buffer().writeUtf8(text("mcpconfig/joined.tsrg")), it) }
        val pkgCtx = MutableMappingTree().also { PackageSrgReader.read(Buffer().writeUtf8(text("srg/package.srg")), it) }

        put("srg_mio", SrgReader, "srg/mio_srg.srg")
        put("srg_joined", SrgReader, "srg/joined_excerpt.srg")
        put("packagesrg", PackageSrgReader, "srg/package.srg")
        put("csrg_mio", CsrgReader, "csrg/mio_csrg.csrg", pkgCtx)
        put("tsrg_mio", TsrgV1Reader, "tsrg/mio_tsrg.tsrg")
        put("tsrgv2_mio", TsrgV2Reader, "tsrg/mio_tsrgv2.tsrg")
        put("tsrgv2_joined_1_17_1", TsrgV2Reader, "mcpconfig/joined_1_17_1.tsrg")
        put("tsrg_joined_1_12_2", TsrgV1Reader, "mcpconfig/joined.tsrg")
        put("tiny_v1_mio", TinyV1Reader, "tiny/mio_tiny.tiny")
        put("tiny_v2_inhtree", TinyV2Reader, "tiny/tmp_inhtree_v2.tiny")
        put("tiny_v2_mio", TinyV2Reader, "tiny/mio_tinyv2.tiny")
        put("aw_transitive", AWReader, "aw/transitive.accesswidener")
        put("aw_writer_v1", AWReader, "aw/writer_v1.accesswidener")
        put("at_test", ATReader, "at/accesstransformer.cfg")
        put("at_forge", ATReader, "at/forge_at.cfg")
        put("at_legacy", LegacyATReader, "at/legacy_at.cfg")
        put("ct_transitive", CTReader, "ct/transitive.classtweaker")
        put("ct_interface", CTReader, "ct/interface.classtweaker")
        put("ct_enum", CTReader, "ct/enum_extension.classtweaker")
        put("unpick_v2", UnpickReader, "unpick/example_v2.unpick")
        put("unpick_v3", UnpickReader, "unpick/example_v3.unpick")
        put("feather_nest", NestReader, "feather/example.nest")
        put("feather_excs", ExceptionReader, "feather/example.excs")
        put("feather_sigs", SignatureReader, "feather/example.sigs")
        put("rgs_client", RetroguardReader, "rgs/client.rgs")
        put("proguard_mio", ProguardReader, "proguard/mio_proguard.txt")
        put("proguard_mojang", ProguardReader, "proguard/client_mappings_1_20_1_excerpt.txt")
        put("parchment", ParchmentReader, "parchment/parchment.json")
        put("mcp_v1_methods", MCPv1MethodReader, "mcp/methods_v1.csv", srgCtx)
        put("mcp_v1_fields", MCPv1FieldReader, "mcp/fields_v1.csv", srgCtx)
        put("mcp_v3_classes", MCPv3ClassesReader, "mcp/classes_v3.csv", srgCtx)
        put("mcp_v3_methods", MCPv3MethodReader, "mcp/methods_v3.csv", srgCtx)
        put("mcp_v3_fields", MCPv3FieldReader, "mcp/fields_v3.csv", srgCtx)
        put("mcp_v6_methods", MCPv6MethodReader, "mcp/methods.csv", tsrgCtx)
        put("mcp_v6_fields", MCPv6FieldReader, "mcp/fields.csv", tsrgCtx)
        put("mcp_v6_params", MCPv6ParamReader, "mcp/params.csv", tsrgCtx)
        put("mcp_exc", MCPExceptionReader, "mcp/joined.exc", srgCtx)
        put("mcpconfig_ctors", MCPConfigConstructorReader, "mcpconfig/constructors.txt", tsrgCtx)
        put("mcpconfig_excs", MCPConfigExceptionsReader, "mcpconfig/exceptions.txt", tsrgCtx)
        put("mcpconfig_access", MCPConfigAccessReader, "mcpconfig/access.txt", tsrgCtx)
        put("mcpconfig_statics", MCPConfigStaticMethodsReader, "mcpconfig/static_methods.txt", tsrgCtx)

        val zipTree = MutableMappingTree()
        ZipReader.read(Buffer().write(bytes("zip/mcp_conf_style.zip")), null, zipTree)
        out["zip_mcp_conf"] = dump(zipTree)
        return out
    }
}

class FirstPartyCorpusTest {

    @Test
    fun corpusGoldens() = runTest {
        val all = FirstPartyCorpus.all()
        for ((name, dump) in all) {
            assertEquals(FirstPartyCorpus.golden("$name.umf"), dump, "golden mismatch for $name")
        }
    }

    @Test
    fun autodetect() = runTest {
        fun check(path: String, expected: FormatReader) {
            val fmt = FormatRegistry.autodetectFormat(path, Buffer().write(FirstPartyCorpus.bytes(path)))
            assertTrue(fmt?.reader === expected, "$path detected as ${fmt?.name}, expected ${expected.name}")
        }
        check("tiny/mio_tinyv2.tiny", TinyV2Reader)
        check("tiny/mio_tiny.tiny", TinyV1Reader)
        check("srg/mio_srg.srg", SrgReader)
        check("srg/package.srg", PackageSrgReader)
        check("csrg/mio_csrg.csrg", CsrgReader)
        check("tsrg/mio_tsrg.tsrg", TsrgV1Reader)
        check("tsrg/mio_tsrgv2.tsrg", TsrgV2Reader)
        check("aw/transitive.accesswidener", AWReader)
        check("ct/transitive.classtweaker", CTReader)
        check("rgs/client.rgs", RetroguardReader)
        check("proguard/client_mappings_1_20_1_excerpt.txt", ProguardReader)
        check("parchment/parchment.json", ParchmentReader)
        check("mcp/methods.csv", MCPv6MethodReader)
        check("mcp/joined.exc", MCPExceptionReader)
        check("mcpconfig/constructors.txt", MCPConfigConstructorReader)
        check("feather/example.nest", NestReader)
        check("feather/example.excs", ExceptionReader)
        check("feather/example.sigs", SignatureReader)
        check("unpick/example_v3.unpick", UnpickReader)
    }
}
