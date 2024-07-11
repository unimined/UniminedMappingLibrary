package xyz.wagyourtail.unimined.mapping

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import okio.buffer
import okio.sink
import okio.source
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.propogator.Propagator
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.util.mutliAssociate
import xyz.wagyourtail.unimined.mapping.visitor.delegate.copyTo
import java.io.File
import kotlin.time.measureTime

fun main(vararg args: String) = Main().main(args)

class Main: CliktCommand() {
    val LOGGER = KotlinLogging.logger {  }

    val envType by option("-e", "--env").convert { EnvType.valueOf(it) }.default(EnvType.JOINED)
    val mappingFiles by option("-i", "--input", help = "the input mappings, format should auto-detect in most cases").file(mustExist = true).multiple(required = true)
    val nsMap by option("-n", "--namespace", help = "Namespace mapping to apply to the input mappings.").transformValues(nvalues = 3) { Triple(it[0].toInt(), it[1], it[2]) }.multiple(required = false, default = emptyList())
    val propogationNs: String? by option("-pns", "--propogation-namespace", help = "Namespace mapping to apply to the propogation mappings.")
    val propogation by option("-p", "--propogation", help = "Any jars to scan the classes of for the case of propogating the mappings.").file(mustExist = true, canBeDir = false).multiple(required = false, default = emptyList())
    val classpath  by option("-c", "--classpath", help = "Classpath for the jar scanning.").file(mustExist = true, canBeDir = false).multiple(required = false, default = emptyList())
    val copyMissing by option("-m", "--copy-missing", help = "Copy missing names from ns to other ns").pair().multiple(required = false, default = emptyList())
    val output by option("-o", "--output", help = "The output").transformValues(nvalues = 2) { FormatRegistry.byName[it[0]] to File(it[1]) }.required()

    override fun run() = runBlocking {
        val totalTime = measureTime {
            val mappings = MemoryMappingTree()
            val nsMapMap = nsMap.mutliAssociate { it.first to (it.second to it.third) }
            for ((idx, input) in mappingFiles.withIndex()) {
                LOGGER.info { "Loading ${input.name}..." }
                val t = measureTime {
                    input.source().buffer().use { buf ->
                        FormatRegistry.autodetectFormat(envType, input.name, buf.peek())
                            ?.read(envType, buf, mappings, mappings, nsMapMap[idx]?.associate { it } ?: emptyMap())
                            ?: throw IllegalArgumentException("Could not autodetect format for ${input.name}")
                    }
                }
                LOGGER.info { "Loaded in ${t.inWholeMilliseconds}ms" }
            }
            val prop = propogation.map { it.toPath() }.toSet()
            val cp = classpath.map { it.toPath() }.toSet()
            if (prop.isNotEmpty() || cp.isNotEmpty()) {
                LOGGER.info { "Propogating..." }
                val t = measureTime {
                    Propagator(Namespace(propogationNs!!), mappings, prop + cp)
                        .propagate(mappings.namespaces.toSet() - Namespace(propogationNs!!))
                }
                LOGGER.info { "Propogated in ${t.inWholeMilliseconds}ms" }
            }
            val copyMissingMap = copyMissing.mutliAssociate { it }
            for ((from, to) in copyMissingMap) {
                LOGGER.info { "Copying missing names from $from to ${to.joinToString(", ")}" }
                val t = measureTime {
                    mappings.accept(mappings.copyTo(Namespace(from), to.map { Namespace(it) }.toSet(), mappings))
                }
                LOGGER.info { "Copied missing names in ${t.inWholeMilliseconds}ms" }
            }
            LOGGER.info { "Writing ${output.second.name}..." }
            val t = measureTime {
                output.second.parentFile?.mkdirs()
                output.second.sink().buffer().use { buf ->
                    mappings.accept(output.first?.write(envType, buf)!!)
                }
            }
            LOGGER.info { "Wrote in ${t.inWholeMilliseconds}ms" }
        }
        LOGGER.info { "Finished in ${totalTime.inWholeMilliseconds}ms" }
    }

}