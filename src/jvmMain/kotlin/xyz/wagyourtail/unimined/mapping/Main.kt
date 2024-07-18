package xyz.wagyourtail.unimined.mapping

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import okio.source
import xyz.wagyourtail.unimined.mapping.Main.Companion.LOGGER
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.propogator.Propagator
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.util.mutliAssociate
import xyz.wagyourtail.unimined.mapping.visitor.delegate.copyTo
import kotlin.time.measureTime

fun main(vararg args: String) {
    val totalTime = measureTime {
        Main().apply {
            subcommands(ExportMappings())
        }.main(args)
    }
    LOGGER.info { "Finished in ${totalTime.inWholeMilliseconds}ms" }
}

class Main: CliktCommand(printHelpOnEmptyArgs = true) {
    companion object {
        val LOGGER = KotlinLogging.logger { }
    }

    val inputs by argument("input", "the input mappings, format should auto-detect in most cases").file(mustExist = true).multiple(required = true)
    val envType by option("-e", "--env").enum<EnvType>().default(EnvType.JOINED)
    val nsMap by option("-n", "--namespace", help = "Namespace mapping to apply to the input mappings.").transformValues(nvalues = 3) { Triple(it[0].toInt(), it[1], it[2]) }.multiple(required = false, default = emptyList())
    val propagationNs: String? by option("-pns", "--propagation-namespace", help = "Namespace mapping to apply to the propagation mappings.")
    val propagation by option("-p", "--propagation", help = "Any jars to scan the classes of for the case of propagating the mappings.").file(mustExist = true, canBeDir = false).multiple(required = false, default = emptyList())
    val classpath  by option("-c", "--classpath", help = "Classpath for the jar scanning.").file(mustExist = true, canBeDir = false).multiple(required = false, default = emptyList())
    val copyMissing by option("-m", "--copy-missing", help = "Copy missing names from ns to other ns").pair().multiple(required = false, default = emptyList())

    val self by findOrSetObject { this }
    val mappings = MemoryMappingTree()

    override fun run() = runBlocking {
        val nsMapMap = nsMap.mutliAssociate { it.first to (it.second to it.third) }
        for ((idx, input) in inputs.withIndex()) {
            LOGGER.info { "Loading ${input.name}..." }
            val t = measureTime {
                input.source().buffer().use { buf ->
                    FormatRegistry.autodetectFormat(envType, input.name, buf.peek())
                        ?.read(buf, mappings, mappings, envType, nsMapMap[idx]?.associate { it } ?: emptyMap())
                        ?: throw IllegalArgumentException("Could not autodetect format for ${input.name}")
                }
            }
            LOGGER.info { "Loaded in ${t.inWholeMilliseconds}ms" }
        }
        val prop = propagation.map { it.toPath() }.toSet()
        val cp = classpath.map { it.toPath() }.toSet()
        if (prop.isNotEmpty() || cp.isNotEmpty()) {
            LOGGER.info { "Propagating..." }
            val t = measureTime {
                Propagator(Namespace(propagationNs!!), mappings, prop + cp)
                    .propagate(mappings.namespaces.toSet() - Namespace(propagationNs!!))
            }
            LOGGER.info { "Propagated in ${t.inWholeMilliseconds}ms" }
        }
        val copyMissingMap = copyMissing.mutliAssociate { it }
        for ((from, to) in copyMissingMap) {
            LOGGER.info { "Copying missing names from $from to ${to.joinToString(", ")}" }
            val t = measureTime {
                mappings.accept(mappings.copyTo(Namespace(from), to.map { Namespace(it) }.toSet(), mappings))
            }
            LOGGER.info { "Copied missing names in ${t.inWholeMilliseconds}ms" }
        }
    }

}

class ExportMappings : CliktCommand(name = "export") {

    val format by argument("format", help = "The format to export as").convert { FormatRegistry.byName.getValue(it) }
    val output by argument("output", help = "The output file").file()

    val main by requireObject<Main>()

    override fun run() {
        LOGGER.info { "Writing ${output.name}..." }
        val t = measureTime {
            output.parentFile?.mkdirs()
            output.sink().buffer().use { buf ->
                main.mappings.accept(format.write(buf, main.envType))
            }
        }
        LOGGER.info { "Wrote in ${t.inWholeMilliseconds}ms" }
    }

}