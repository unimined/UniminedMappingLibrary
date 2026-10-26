package xyz.wagyourtail.unimined.mapping.remapper.source

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.asm.JarFixups
import xyz.wagyourtail.unimined.mapping.remapper.asm.MappingRemapper
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Source jar remapping — `fns → targetNs`, driven by the same [MappingTree]
 * as the bytecode remapper.
 *
 * Usage pairs a **source jar with its matching bytecode jar**: the bytecode
 * jar goes on the engine's resolution classpath, so member access resolution
 * is exact — the same guarantee the annotation-table mixin remap gets from
 * the inheritance graph.
 */
object SourceRemapper {

    /** remapped sources — entry path → new contents */
    class Result(
        val sources: List<Pair<String, ByteArray>>,
        /** non-source entries (manifest/services/access-files already remapped
         *  per [SourceOptions.remapResources]) */
        val resources: List<Pair<String, ByteArray>>,
        /** entry path → zip extra-field bytes (vineflower linemaps preserved;
         *  adjusted when transforms shift lines) */
        val extras: Map<String, ByteArray> = emptyMap(),
        /** per-file diagnostics: `(path, line, message)` */
        val errors: List<Triple<String, Int, String>>,
    )

    /** remap a source jar on disk */
    fun remap(
        input: Path,
        output: Path,
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
        classpath: List<Path> = emptyList(),
        options: SourceOptions = SourceOptions(),
    ) = writeJar(output, remap(input, tree, fns, targetNs, classpath, options))

    /** paired source+bytecode remap — `srcJar` remapped against `tree` with
     *  `binJar` on the engine's resolution classpath. Source jars come paired
     *  with their bytecode jar — this is the expected call shape. */
    fun remapPaired(
        srcJar: Path, binJar: Path, output: Path,
        tree: MappingTree,
        fns: Namespace, targetNs: Namespace,
        classpath: List<Path> = emptyList(),
        options: SourceOptions = SourceOptions(),
    ) = remap(srcJar, output, tree, fns, targetNs, classpath + binJar, options)

    /** remap a source jar in memory — returns entry contents + diagnostics */
    fun remap(
        input: Path,
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
        classpath: List<Path> = emptyList(),
        options: SourceOptions = SourceOptions(),
    ): Result {
        val units = mutableListOf<SourceUnit>()
        val resources = mutableListOf<Pair<String, ByteArray>>()
        val extras = mutableMapOf<String, ByteArray>()
        ZipFile(input.toFile()).use { zin ->
            for (entry in zin.entries().asSequence().sortedBy { it.name }) {
                if (entry.isDirectory) continue
                val bytes = zin.getInputStream(entry).readBytes()
                if (entry.extra != null && entry.extra.isNotEmpty()) extras[entry.name] = entry.extra
                if (isSourceEntry(entry.name)) {
                    units += SourceUnit(entry.name, bytes.decodeToString())
                } else {
                    resources += entry.name to bytes
                }
            }
        }

        val ctx = ExtensionContext(tree, fns, targetNs)
        val engine = options.engine.create(units, classpath, ctx, options.extensions)
        val errors = mutableListOf<Triple<String, Int, String>>()
        val outSources = units.map { u ->
            val fileErrors = mutableListOf<Pair<Int, String>>()
            val remapped = try {
                engine.remap(u, fileErrors)
            } catch (e: Exception) {
                throw RuntimeException("Failed to remap ${u.path}", e)
            }
            fileErrors.forEach { (line, msg) -> errors += Triple(u.path, line, msg) }
            // transforms that shift lines invalidate vineflower linemaps —
            // adjust the extra field in place so it tracks the new text
            val deltas = engine.lineDeltas(u)
            if (deltas.isNotEmpty()) {
                extras[u.path]?.let { extra ->
                    Linemap.parse(extra)?.let { lines ->
                        extras[u.path] = Linemap.replace(extra, Linemap.shift(lines, deltas))
                    }
                }
            }
            val mappedPkg = engine.mappedPackage(u)
            val outPath = if (mappedPkg != null)
                (if (mappedPkg.isEmpty()) "" else "$mappedPkg/") + u.path.substringAfterLast('/')
            else u.path
            outPath to (remapped ?: u.text).encodeToByteArray()
        }
        engine.close()

        val outResources = if (options.remapResources) {
            val remapper = MappingRemapper(tree, fns, targetNs)
            resources.map { (name, bytes) ->
                when {
                    name.equals("META-INF/MANIFEST.MF", ignoreCase = true) ->
                        name to ByteArrayOutputStream().also {
                            Manifest(bytes.inputStream()).write(it)
                        }.toByteArray()
                    name.startsWith("META-INF/services/") ->
                        JarFixups.remapServiceName(name, remapper) to
                            JarFixups.remapServiceContents(bytes, remapper)
                    name.endsWith(".accesswidener", ignoreCase = true) ||
                        name.endsWith("accesstransformer.cfg", ignoreCase = true) ->
                        name to JarFixups.remapAccessFile(name, bytes, remapper, targetNs.name)
                    else -> name to bytes
                }
            }
        } else resources

        return Result(outSources, outResources, extras, errors)
    }

    /** is [path] a source file any engine could handle? (engine decides per
     *  extension via [SourceEngine.handles] — this only splits resources) */
    private fun isSourceEntry(path: String): Boolean =
        path.endsWith(".java") || path.endsWith(".kt") || path.endsWith(".kts")

    /** serialize to a jar — entry extras (e.g. vineflower linemaps) preserved */
    fun writeJar(output: Path, result: Result) {
        ZipOutputStream(Files.newOutputStream(output).buffered()).use { zout ->
            fun put(name: String, bytes: ByteArray) {
                zout.putNextEntry(ZipEntry(name).apply {
                    time = 0L
                    result.extras[name]?.let { extra = it }
                }); zout.write(bytes); zout.closeEntry()
            }
            for ((name, bytes) in result.resources) {
                if (name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) put(name, bytes)
            }
            for ((name, bytes) in result.sources + result.resources) {
                if (!name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) put(name, bytes)
            }
        }
    }
}
