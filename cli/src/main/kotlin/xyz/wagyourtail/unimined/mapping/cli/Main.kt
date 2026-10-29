package xyz.wagyourtail.unimined.mapping.cli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.pair
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.path
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import okio.source
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.LocalVariableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.PackageMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.ParameterMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.asm.PackageAccess
import xyz.wagyourtail.unimined.mapping.remapper.source.Linemap
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceRemapper
import xyz.wagyourtail.unimined.mapping.remapper.source.psi.AccessSourceExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.psi.InterfaceInjectionExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.psi.JavadocExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.psi.ConstantGroupSourceExtension
import xyz.wagyourtail.unimined.mapping.resolver.ContentProvider
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.time.measureTime

private val LOGGER = KotlinLogging.logger {}

fun main(args: Array<String>) = runBlocking {
    Main().apply {
        subcommands(ExportMappings(this@apply), RemapJar(this@apply), RemapSources(this@apply), TransformSources(this@apply), LinemapBin(this@apply))
    }.main(args)
}

/**
 * `uml` — unimined-mapping-library CLI.
 *
 * Parent command carries the mapping inputs and load options; subcommands
 * consume the resolved [MappingTree] (except `linemap`, which is pure jar
 * surgery and needs no mappings).
 */
class Main : SuspendingCliktCommand(name = "uml") {

    override val printHelpOnEmptyArgs = true

    val inputs by option("-i", "--input", help = "input mappings — format autodetects; zips expand per-file")
        .path(mustExist = true, canBeDir = false).multiple()
    val envType by option("-e", "--env", help = "environment side filter for entries").enum<EnvType>()
        .default(EnvType.JOINED)
    val requires by option("--requires", help = "namespace the input mappings build on").default("official")
    val provides by option("--provides", help = "named namespaces the inputs provide (order matters for fill-missing)")
        .multiple()
    val intermediary by option("--intermediary", help = "namespaces the inputs provide that are NOT named (hashed/intermediary)")
        .multiple()
    val mapNs by option("-n", "--map-ns", help = "reader-side namespace rename, applied to every input")
        .pair().multiple()
    val propagation by option("-p", "--propagation", help = "jars scanned for inheritance-based member propagation")
        .path(mustExist = true, canBeDir = false).multiple()
    val propagationNs by option("--pns", help = "namespace the propagation jars are written in")
    val classpath by option("-c", "--classpath", help = "extra jars scanned for inheritance + put on the source engine classpath")
        .path(mustExist = true, canBeDir = false).multiple()
    val unmappedNs by option("--unmapped-ns", help = "namespaces treated as unmapped (base) — default: official")
        .multiple()
    val unchecked by option("--unchecked", help = "skip format sanity checks while reading").flag()
    val leinient by option("--leinient", help = "lenient parsing for malformed entries").flag()

    val resolver by lazy {
        CliMappingResolver(
            Namespace(propagationNs ?: requires),
            (propagation + classpath).toMutableSet(),
            unmappedNs.map { Namespace(it) }.toSet().ifEmpty { setOf(Namespace("official")) },
        ).also {
            it.envType = envType
            it.unchecked = unchecked
            it.leinient = leinient
        }
    }

    private var registered = false

    override suspend fun run() {}

    /** build the resolved tree — registers [inputs] as entries on first call */
    suspend fun resolvedTree(): MappingTree {
        if (inputs.isEmpty()) throw CliktError("at least one -i/--input mappings file is required")
        if (provides.isEmpty() && intermediary.isEmpty()) {
            throw CliktError("at least one --provides or --intermediary namespace is required")
        }
        if (!registered) {
            registered = true
            for (input in inputs) {
                LOGGER.info { "Loading ${input.fileName}..." }
                val entry = resolver.MappingEntry(
                    ContentProvider.of(input.fileName.toString()) { input.toFile().source().buffer() },
                    input.fileName.toString(),
                )
                entry.requires(requires)
                for (ns in provides) entry.provides(ns, true)
                for (ns in intermediary) entry.provides(ns, false)
                for ((from, to) in mapNs) entry.mapNamespace(from, to)
                resolver.addDependency(input.fileName.toString(), entry)
            }
        }
        return resolver.resolve()
    }

    fun ns(name: String): Namespace = Namespace(name)

}

class ExportMappings(private val main: Main) : SuspendingCliktCommand(name = "export") {

    val format by argument("format", help = "format to export as").convert {
        FormatRegistry.byName[it] ?: throw CliktError("unknown format $it — known: ${FormatRegistry.byName.keys.joinToString()}")
    }
    val output by argument("output", help = "output file").path(mustExist = false)
    val ns by option("--ns", help = "emit only this namespace — required by single-ns formats (aw, at)")

    override suspend fun run() {
        val tree = main.resolvedTree()
        val filterNs = ns?.let { main.ns(it) }
        LOGGER.info { "Writing ${output.fileName}..." }
        val t = measureTime {
            output.parent?.toFile()?.mkdirs()
            output.toFile().sink().buffer().use { buf ->
                tree.emit(
                    format.write(buf, format.withEnvType(main.envType)),
                    nsFilter = filterNs?.let { listOf(it) } ?: tree.namespaces.toList(),
                    // single-ns writers throw on elements missing the name —
                    // drop them before the sink sees them
                    transform = { e ->
                        if (filterNs != null && !hasNs(e, filterNs)) null else e
                    },
                )
            }
        }
        LOGGER.info { "Wrote in ${t.inWholeMilliseconds}ms" }
    }

    private fun hasNs(e: xyz.wagyourtail.unimined.mapping.element.MappingElement, ns: Namespace): Boolean = when (e) {
        is ClassMapping -> ns in e.names
        is MethodMapping -> ns in e.names
        is FieldMapping -> ns in e.names
        is ParameterMapping -> ns in e.names
        is LocalVariableMapping -> ns in e.names
        is PackageMapping -> ns in e.names
        else -> true
    }

}

class RemapJar(private val main: Main) : SuspendingCliktCommand(name = "remap") {

    val input by argument("input", help = "input jar").path(mustExist = true, canBeDir = false)
    val output by argument("output", help = "output jar").path(mustExist = false)
    val fns by option("--from", help = "source namespace").required()
    val targetNs by option("--to", help = "target namespace").required()
    val library by option("-l", "--library", help = "extra jars scanned for the inheritance pass (input is always scanned)")
        .path(mustExist = true, canBeDir = false).multiple()
    val packageAccess by option("--package-access", help = "package-private access check").enum<PackageAccess>()
        .default(PackageAccess.WARN)
    val removeFrames by option("--remove-frames", help = "strip StackMapTable (broken input frames)").flag()
    val noStripSignatures by option("--no-strip-signatures", help = "keep META-INF signature files").flag()
    val skipLv by option("--skip-lv", help = "skip local-variable name remapping").flag()
    val noRebuildSourcefilenames by option("--no-rebuild-sourcefilenames", help = "keep SourceFile attributes").flag()

    override suspend fun run() {
        // input jar participates in the inheritance scan — propagated member
        // names must cover classes the mappings don't know about
        main.resolver.scanJars.add(input)
        main.resolver.scanJars.addAll(library)
        val tree = main.resolvedTree()
        LOGGER.info { "Remapping ${input.fileName} $fns -> $targetNs..." }
        val t = measureTime {
            AsmRemapper.remap(
                input, output, tree, main.ns(fns), main.ns(targetNs),
                Options(
                    removeFrames = removeFrames,
                    rebuildSourceFilenames = !noRebuildSourcefilenames,
                    packageAccess = packageAccess,
                    stripSignatures = !noStripSignatures,
                    skipLocalVariableMapping = skipLv,
                ),
            )
        }
        LOGGER.info { "Remapped in ${t.inWholeMilliseconds}ms" }
    }

}

/** shared options for the two source subcommands */
abstract class SourceCommand(private val main: Main, name: String) : SuspendingCliktCommand(name = name) {

    val input by argument("input", help = "input sources jar").path(mustExist = true, canBeDir = false)
    val output by argument("output", help = "output sources jar").path(mustExist = false)
    val fns by option("--from", help = "namespace the sources/metadata are written in").required()
    val targetNs by option("--to", help = "target namespace").required()
    val bin by option("--bin", help = "paired/named bytecode jar — put on the engine classpath for resolution")
        .path(mustExist = true, canBeDir = false)
    val extraClasspath by option("--classpath", help = "extra engine-classpath jars")
        .path(mustExist = true, canBeDir = false).multiple()

    abstract val sourceOptions: SourceOptions

    override suspend fun run() {
        val tree = main.resolvedTree()
        val cp = main.classpath + extraClasspath + listOfNotNull(bin)
        LOGGER.info { "Remapping sources ${input.fileName} $fns -> $targetNs..." }
        val t = measureTime {
            SourceRemapper.remap(input, output, tree, main.ns(fns), main.ns(targetNs), cp, sourceOptions)
        }
        LOGGER.info { "Done in ${t.inWholeMilliseconds}ms" }
    }

}

/** path-1: authored source jar remapped alongside its bytecode jar — line-preserving */
class RemapSources(main: Main) : SourceCommand(main, "remap-sources") {

    override val sourceOptions get() = SourceOptions()

}

/** path-2: sources decompiled from already-remapped bytecode — metadata
 *  transforms + linemap adjustment (line shifts expected) */
class TransformSources(main: Main) : SourceCommand(main, "transform-sources") {

    val noJavadoc by option("--no-javadoc", help = "skip javadoc insertion").flag()
    val noUnpick by option("--no-unpick", help = "skip constant unpicking").flag()
    val noIfaceInject by option("--no-iface-inject", help = "skip injected-interface declarations").flag()
    val noAccess by option("--no-access", help = "skip access-flag transforms").flag()

    override val sourceOptions
        get() = SourceOptions(
            linePreserving = false,
            extensions = buildList {
                if (!noIfaceInject) add(InterfaceInjectionExtension())
                if (!noJavadoc) add(JavadocExtension())
                if (!noUnpick) add(ConstantGroupSourceExtension())
                if (!noAccess) add(AccessSourceExtension())
            },
        )

}

/** path-2 last step — apply a sources jar's `0x4646` linemap extras back onto
 *  the named bin jar's LineNumberTable. No mappings required. */
class LinemapBin(private val main: Main) : SuspendingCliktCommand(name = "linemap") {

    val bin by argument("bin", help = "named binary jar").path(mustExist = true, canBeDir = false)
    val sources by argument("sources", help = "sources jar carrying linemap extras").path(mustExist = true, canBeDir = false)
    val output by argument("output", help = "output bin jar").path(mustExist = false)

    override suspend fun run() {
        LOGGER.info { "Applying linemaps ${sources.fileName} -> ${bin.fileName}..." }
        val t = measureTime {
            Linemap.applyJar(bin, sources, output)
        }
        LOGGER.info { "Done in ${t.inWholeMilliseconds}ms" }
    }

}
