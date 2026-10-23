package xyz.wagyourtail.unimined.mapping.remapper.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.PropagationStrategy
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import xyz.wagyourtail.unimined.mapping.remapper as nsBoundRemapper

/** Options for [AsmRemapper.remap]. */
data class Options(
    /** Strip `StackMapTable` attributes entirely — the escape hatch for broken input frames. */
    val removeFrames: Boolean = false,
    /** Rewrite the `SourceFile` attribute to the mapped class name. */
    val rebuildSourceFilenames: Boolean = true,
    /** Package-private access check/fix — renaming can move classes across
     *  packages and break package-private references. */
    val packageAccess: PackageAccess = PackageAccess.WARN,
    /** Drop `META-INF` signature files (`.SF`/`.RSA`/`.DSA`/`.EC`) and manifest signature sections — renaming invalidates jar signing. */
    val stripSignatures: Boolean = true,
    /** Skip parameter/local-variable-name remapping entirely. */
    val skipLocalVariableMapping: Boolean = false,
    /** Rename locals whose names aren't valid Java identifiers (obfuscated jars). */
    val renameInvalidLocals: Boolean = true,
    /** Overrides the default Java-identifier test for [renameInvalidLocals] — must fully match the name. */
    val invalidLvNamePattern: Regex? = null,
    /** Invalid-named locals reuse a sibling LV entry's name at the same index+desc. */
    val inferNameFromSameLvIndex: Boolean = false,
    /** Remap-time extension hooks (mixin refmaps, extra transforms…). Each gets a
     *  fresh [AsmExtension] instance per remap — see [ExtensionContext]. */
    val extensions: List<AsmExtensionFactory> = emptyList(),
)

/**
 * Jar remap pipeline — remaps `fns → targetNs` and applies `BytecodeTransform`
 * metadata.
 *
 * Per class: `ClassReader → ClassNode → ClassRemapper(outNode) → MetadataAdapter`.
 * The metadata pass runs **after** name remapping so all lookups and injected
 * values resolve in `targetNs`. Multi-release entries under
 * `META-INF/versions/N/` remap through the same path and land under their
 * version dir.
 *
 * Two entry points: [remap] returns a [Result] for in-memory pipelines; [writeJar]
 * (or the `remap` overload taking an output path) serializes the same result.
 */
object AsmRemapper {

    private val LOGGER = KotlinLogging.logger {}

    /** Output of [remap] — usable directly as a transformation-pipeline stage. */
    class Result(
        /** output entry path → remapped + transformed [ClassNode] (targetNs names) */
        val classes: List<Pair<String, ClassNode>>,
        /** output entry path → bytes; manifest + services already remapped,
         *  signature files already dropped per [Options.stripSignatures] */
        val resources: List<Pair<String, ByteArray>>,
    )

    /** Remap a jar on disk and write the result to [output]. */
    fun remap(
        input: Path,
        output: Path,
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
        options: Options = Options(),
    ) = writeJar(output, remap(input, tree, fns, targetNs, options))

    /**
     * Overlay convenience for multi-layer remaps — e.g. base mappings (MC) under
     * a mod's own mapping/AT layer. Later trees win where rows overlap; metadata
     * (access transforms etc.) from every layer accumulates.
     *
     * [libraries] — jars to scan for the inheritance pass (classpath deps +
     * inputs); [input] is included automatically. Propagated rows are written
     * into the merged tree before remapping, so overrides in classes the
     * mappings don't know about still rename correctly.
     */
    suspend fun remap(
        input: Path,
        output: Path,
        trees: List<MappingTree>,
        fns: Namespace,
        targetNs: Namespace,
        libraries: Set<Path> = emptySet(),
        options: Options = Options(),
        strategy: PropagationStrategy = PropagationStrategy.KEEP_EXPLICIT,
    ) = remap(input, output, overlay(trees, fns, libraries.plusElement(input), strategy), fns, targetNs, options)

    /**
     * Merge [trees] into one mutable tree — later entries override earlier ones.
     *
     * With [fns]+[libraries] set, writes flow through an
     * [InheritanceBackedMappingTree] over an [ASMInheritanceTree] scan of the
     * jars, so member names propagate to descendants incrementally as each
     * layer lands — no batch propagation pass.
     */
    suspend fun overlay(
        trees: List<MappingTree>,
        fns: Namespace? = null,
        libraries: Set<Path> = emptySet(),
        strategy: PropagationStrategy = PropagationStrategy.KEEP_EXPLICIT,
    ): MutableMappingTree {
        val merged = MutableMappingTree(trees.flatMap { it.namespaces }.toSet())
        for (t in trees) t.emit(merged)
        if (fns == null || libraries.isEmpty()) return merged
        return overlay(merged, ASMInheritanceTree(fns, libraries), strategy)
    }

    /**
     * Wrap [merged] in an [InheritanceBackedMappingTree] over a pre-scanned
     * [graph] — the session-shaped entry: scan the library jars once
     * (`ASMInheritanceTree`), then overlay/remap each jar against the shared
     * graph. Construction emits the merged rows through propagating factories
     * after folding declared access changes into the scan.
     */
    fun overlay(
        merged: MutableMappingTree,
        graph: xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree,
        strategy: PropagationStrategy = PropagationStrategy.KEEP_EXPLICIT,
    ): MutableMappingTree = InheritanceBackedMappingTree(merged, graph, strategy)

    /** Remap a jar's classes to [ClassNode]s — no jar output written. */
    fun remap(
        input: Path,
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
        options: Options = Options(),
    ): Result {
        val remapper = MappingRemapper(tree.nsBoundRemapper(fns, targetNs))
        val readerFlags = if (options.removeFrames) ClassReader.SKIP_FRAMES else 0
        val ctx = ExtensionContext(tree, fns, targetNs)
        val exts = options.extensions.map { it.create(ctx) }

        // pass 1: read all entries (classes held in memory — the package-access
        // fixup pass needs them all before write anyway) + extension analysis
        val classes = mutableListOf<Pair<String, ClassNode>>() // entry path → node
        val resources = mutableListOf<Pair<String, ByteArray>>()

        ZipFile(input.toFile()).use { zin ->
            for (entry in zin.entries().asSequence().sortedBy { it.name }) {
                if (entry.isDirectory) continue
                val bytes = zin.getInputStream(entry).readBytes()
                when {
                    entry.name.endsWith(".class") -> {
                        val node = ClassNode()
                        ClassReader(bytes).accept(node, readerFlags)
                        classes += entry.name to node
                        exts.forEach { it.analyze(node) }
                    }
                    options.stripSignatures && JarFixups.isSignatureFile(entry.name) -> Unit // drop
                    else -> {
                        resources += entry.name to bytes
                        exts.forEach { it.analyzeResource(entry.name, bytes) }
                    }
                }
            }
        }
        exts.forEach { it.finishAnalysis() }

        // pass 2: remap names, then apply metadata in targetNs
        val outClasses = ArrayList<Pair<String, ClassNode>>(classes.size)
        for ((entryName, node) in classes) {
            val cls = tree.getClass(fns, InternalName.unchecked(node.name))
            val outNode = ClassNode()
            node.accept(ClassRemapper(outNode, remapper))
            if (options.rebuildSourceFilenames && outNode.sourceFile != null) {
                outNode.sourceFile = outNode.name.substringAfterLast('/') + ".java"
            }
            MetadataAdapter.apply(outNode, cls, tree, targetNs, options)
            exts.forEach { it.postApply(node, outNode, cls) }
            val prefix = if (entryName.startsWith("META-INF/versions/")) {
                entryName.substring(0, entryName.indexOf('/', "META-INF/versions/".length) + 1)
            } else ""
            outClasses += "$prefix${outNode.name}.class" to outNode
        }

        // pass 3: package-access check/fix across the whole output set
        if (options.packageAccess != PackageAccess.NONE) {
            JarFixups.checkPackageAccess(outClasses.map { it.second }, remapper, options, LOGGER)
        }

        // resource fixups: manifest attrs + service-provider files, then
        // extension transforms (refmap merges…) over the fixup output
        val outResources = resources.map { (name, bytes) ->
            val fixed = when {
                name.equals("META-INF/MANIFEST.MF", ignoreCase = true) ->
                    name to JarFixups.remapManifest(Manifest(bytes.inputStream()), remapper).toBytes()
                name.startsWith("META-INF/services/") ->
                    JarFixups.remapServiceName(name, remapper) to JarFixups.remapServiceContents(bytes, remapper)
                name.endsWith(".accesswidener", ignoreCase = true) ||
                    name.endsWith("accesstransformer.cfg", ignoreCase = true) ->
                    name to JarFixups.remapAccessFile(name, bytes, remapper, targetNs.name)
                else -> name to bytes
            }
            exts.firstNotNullOfOrNull { it.transformResource(fixed.first, fixed.second) }
                ?.let { fixed.first to it } ?: fixed
        }

        // extension-emitted entries (refmaps etc.) come last — written verbatim
        return Result(outClasses, outResources + exts.flatMap { it.resources() })
    }

    /** Serialize a [Result] to a jar at [output]. */
    fun writeJar(output: Path, result: Result) {
        ZipOutputStream(Files.newOutputStream(output).buffered()).use { zout ->
            fun put(name: String, write: (java.io.OutputStream) -> Unit) {
                zout.putNextEntry(ZipEntry(name).apply { time = 0L })
                write(zout)
                zout.closeEntry()
            }
            for ((name, bytes) in result.resources) {
                // manifest first so streaming readers see it early
                if (name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) put(name) { it.write(bytes) }
            }
            for ((name, node) in result.classes) {
                put(name) { stream ->
                    val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
                    node.accept(writer)
                    stream.write(writer.toByteArray())
                }
            }
            for ((name, bytes) in result.resources) {
                if (!name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) put(name) { it.write(bytes) }
            }
        }
    }

    private fun Manifest.toBytes(): ByteArray =
        ByteArrayOutputStream().also { write(it) }.toByteArray()

}

/** [Options.packageAccess] behavior for references to package-private classes
 *  that land in a different package after remapping. */
enum class PackageAccess {
    NONE,  /** don't check */
    WARN,  /** log the broken access */
    FIX,   /** upgrade the target class to public */
}
