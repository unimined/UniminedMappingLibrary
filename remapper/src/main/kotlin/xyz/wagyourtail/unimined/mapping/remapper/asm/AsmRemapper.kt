package xyz.wagyourtail.unimined.mapping.remapper.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.GraphNamespaceRemapper
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.NamespaceRemapper
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceConflictException
import xyz.wagyourtail.unimined.mapping.inheritance.MemberGroups
import xyz.wagyourtail.unimined.mapping.inheritance.PropagationStrategy
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest
import java.util.stream.Collectors
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
    /** Apply `ConstantGroupMapping` unpick at bytecode level — literal loads
     *  matching a group constant are replaced with instructions evaluating the
     *  group's expression. See [ConstantGroupEmitter]. */
    val unpickConstants: Boolean = true,
    /** Remap-time extension hooks — pipeline order. Defaults to
     *  [BuiltinAsmExtensions.defaults] (source-file rebuild, metadata
     *  application, constant-group unpick); disable a builtin by removing
     *  it, e.g. `extensions - BuiltinAsmExtensions.CONSTANT_GROUPS`, and
     *  add hooks (mixin refmaps, extra transforms…) by appending. Each
     *  factory gets a fresh [AsmExtension] per remap — see
     *  [ExtensionContext]. */
    val extensions: List<AsmExtensionFactory> = BuiltinAsmExtensions.defaults(),
    /** Optional progress callback — invoked from worker threads as
     *  `(phase, done, total)` with phase in `parse`/`remap`/`write`; called
     *  once per work unit, so throttle inside the callback. Must be
     *  thread-safe. */
    val onProgress: ((phase: String, done: Int, total: Int) -> Unit)? = null,
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
    ) {
        if (allPerClass(options)) {
            remapStreaming(input, output, MappingRemapper(tree.nsBoundRemapper(fns, targetNs)), tree, fns, targetNs, options)
        } else {
            writeJar(output, remap(input, tree, fns, targetNs, options), options.onProgress)
        }
    }

    /** every extension is a pure per-class transform — streaming eligible */
    private fun allPerClass(options: Options): Boolean =
        options.extensions.all { it.perClass }

    /** install the origin-row resolver on [ctx] when [nsRemapper] is
     *  graph-backed — propagated names come from an ancestor's row, which is
     *  where the param/lvar children live */
    private fun attachMethodOrigin(ctx: ExtensionContext, nsRemapper: NamespaceRemapper?, tree: MappingTree, fns: Namespace) {
        val g = nsRemapper as? GraphNamespaceRemapper ?: return
        ctx.methodOrigin = { owner, name, desc ->
            g.methodOrigin(owner, name, desc)?.let { m ->
                tree.getClass(fns, m.cls.name)?.let { c ->
                    with(tree) { c.getMethod(fns, m.info.name, m.info.descriptor) }
                }
            }
        }
    }

    /**
     * Overlay convenience for multi-layer remaps — e.g. base mappings (MC) under
     * a mod's own mapping/AT layer. Later trees win where rows overlap; metadata
     * (access transforms etc.) from every layer accumulates.
     *
     * [libraries] — jars to scan for the inheritance pass (classpath deps +
     * inputs); [input] is included automatically. Member names propagate
     * through a [MemberGroups] component graph and resolve at lookup time —
     * no row materialization — so overrides in classes the mappings don't
     * know about still rename correctly.
     *
     * [strategy] — [PropagationStrategy.BRIDGE] still takes the materializing
     * [InheritanceBackedMappingTree] path (bridges live as row metadata);
     * [PropagationStrategy.STRICT] throws on member-name conflicts.
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
    ) {
        if (strategy == PropagationStrategy.BRIDGE) {
            return remap(input, output, overlay(trees, fns, libraries.plusElement(input), strategy), fns, targetNs, options)
        }
        val merged = mergeTrees(trees)
        return remapGraph(input, output, merged, ASMInheritanceTree(fns, libraries.plusElement(input)), fns, targetNs, options, strategy)
    }

    /**
     * [remap] with a prebuilt inheritance [graph] — for callers that
     * share a classpath scan across remaps (`ASMInheritanceTree.scan`
     * + [ASMInheritanceTree.ScanData]); [input]'s own classes must
     * already be folded into [graph]. Propagation mutates the graph's
     * member data, so each call needs a graph built for it — share the
     * underlying `ScanData`, not the tree.
     */
    suspend fun remap(
        input: Path,
        output: Path,
        trees: List<MappingTree>,
        fns: Namespace,
        targetNs: Namespace,
        graph: xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree,
        options: Options = Options(),
        strategy: PropagationStrategy = PropagationStrategy.KEEP_EXPLICIT,
    ) {
        if (strategy == PropagationStrategy.BRIDGE) {
            return remap(input, output, overlay(mergeTrees(trees), graph, strategy), fns, targetNs, options)
        }
        return remapGraph(input, output, mergeTrees(trees), graph, fns, targetNs, options, strategy)
    }

    private suspend fun remapGraph(
        input: Path,
        output: Path,
        merged: MutableMappingTree,
        inheritance: xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree,
        fns: Namespace,
        targetNs: Namespace,
        options: Options,
        strategy: PropagationStrategy,
    ) {
        val groups = MemberGroups(inheritance)
        val conflicts = groups.propagate(merged)
        if (strategy == PropagationStrategy.STRICT && conflicts > 0) {
            throw InheritanceConflictException("$conflicts member-name conflicts during propagation")
        }
        if (conflicts > 0) LOGGER.warn { "$conflicts member-name conflicts resolved deterministically" }
        val graphRemapper = GraphNamespaceRemapper(merged, inheritance, groups, fns, targetNs)
        if (allPerClass(options)) {
            remapStreaming(input, output, MappingRemapper(graphRemapper), merged, fns, targetNs, options)
        } else {
            writeJar(output, remap(input, graphRemapper, merged, fns, targetNs, options), options.onProgress)
        }
    }

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
        val merged = mergeTrees(trees)
        if (fns == null || libraries.isEmpty()) return merged
        return overlay(merged, ASMInheritanceTree(fns, libraries), strategy)
    }

    /** Merge [trees] into one mutable tree — later entries override earlier. */
    private fun mergeTrees(trees: List<MappingTree>): MutableMappingTree {
        val merged = MutableMappingTree(trees.flatMap { it.namespaces }.toSet())
        for (t in trees) t.emit(merged)
        return merged
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
    ): Result = remap(input, tree.nsBoundRemapper(fns, targetNs), tree, fns, targetNs, options)

    /**
     * Remap with a pre-resolved [NamespaceRemapper] — the graph-propagation
     * path. [tree] still supplies class rows and metadata lookups
     * ([MetadataAdapter], [ConstantGroupEmitter], [ExtensionContext]).
     */
    fun remap(
        input: Path,
        remapper: NamespaceRemapper,
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
        options: Options = Options(),
    ): Result {
        val asmRemapper = MappingRemapper(remapper)
        val readerFlags = if (options.removeFrames) ClassReader.SKIP_FRAMES else 0
        val ctx = ExtensionContext(tree, fns, targetNs, options)
        attachMethodOrigin(ctx, remapper, tree, fns)
        // (factory, instance) pairs — perClass factories run postApply inside
        // the parallel worker; the rest run serially after collection
        val exts = options.extensions.map { f -> f to f.create(ctx) }
        val perClassExts = exts.filter { it.first.perClass }.map { it.second }
        val serialExts = exts.filterNot { it.first.perClass }.map { it.second }

        // pass 1: read all entries (classes held in memory — the package-access
        // fixup pass needs them all before write anyway) + extension analysis
        val resources = mutableListOf<Pair<String, ByteArray>>()
        val rawClasses = mutableListOf<Pair<String, ByteArray>>()
        ZipFile(input.toFile()).use { zin ->
            for (entry in zin.entries().asSequence().sortedBy { it.name }) {
                if (entry.isDirectory) continue
                when {
                    entry.name.endsWith(".class") ->
                        rawClasses += entry.name to zin.getInputStream(entry).readBytes()
                    options.stripSignatures && JarFixups.isSignatureFile(entry.name) -> Unit // drop
                    else -> {
                        val bytes = zin.getInputStream(entry).readBytes()
                        resources += entry.name to bytes
                        exts.forEach { it.second.analyzeResource(entry.name, bytes) }
                    }
                }
            }
        }
        // ClassNode parses are independent — parallel; ordered stream keeps
        // output deterministic. analyze()/fieldValues run after, serially,
        // since extensions may hold shared per-remap state.
        val parseCount = java.util.concurrent.atomic.AtomicInteger()
        val classes = rawClasses.parallelStream().map { e ->
            val node = ClassNode()
            ClassReader(e.second).accept(node, readerFlags)
            options.onProgress?.invoke("parse", parseCount.incrementAndGet(), rawClasses.size)
            e.first to node
        }.collect(Collectors.toList())
        // "owner.name" → (ConstantValue, field desc) — for constant-group
        // expression evaluation in ConstantGroupEmitter
        val fieldValues = mutableMapOf<String, Pair<Any, String>>()
        for ((_, node) in classes) {
            exts.forEach { it.second.analyze(node) }
            for (f in node.fields) {
                if (f.value != null && f.access and Opcodes.ACC_STATIC != 0) {
                    fieldValues["${node.name}.${f.name}"] = f.value to f.desc
                }
            }
        }
        exts.forEach { it.second.finishAnalysis() }
        ctx.fieldValues = fieldValues

        // pass 2: remap names, then per-class stages (source files, metadata,
        // constant groups, other perClass extensions) — independent per class,
        // so run parallel; non-perClass postApply runs serially afterwards
        val remapCount = java.util.concurrent.atomic.AtomicInteger()
        val outClasses = classes.parallelStream().map { e ->
            val (entryName, node) = e
            val cls = tree.getClass(fns, InternalName.unchecked(node.name))
            val outNode = ClassNode()
            node.accept(MappingClassRemapper(outNode, asmRemapper))
            perClassExts.forEach { it.postApply(node, outNode, cls) }
            options.onProgress?.invoke("remap", remapCount.incrementAndGet(), classes.size)
            val prefix = if (entryName.startsWith("META-INF/versions/")) {
                entryName.substring(0, entryName.indexOf('/', "META-INF/versions/".length) + 1)
            } else ""
            Triple("$prefix${outNode.name}.class", outNode, node to cls)
        }.collect(Collectors.toList()).also { out ->
            for ((_, outNode, input) in out) {
                serialExts.forEach { it.postApply(input.first, outNode, input.second) }
                JarFixups.fixParamAnnotations(outNode)
            }
            JarFixups.fixInnerClasses(out.map { it.first to it.second })
        }.map { it.first to it.second }

        // pass 3: package-access check/fix across the whole output set
        if (options.packageAccess != PackageAccess.NONE) {
            JarFixups.checkPackageAccess(outClasses.map { it.second }, asmRemapper, options, LOGGER)
        }

        // resource fixups: manifest attrs + service-provider files, then
        // extension transforms (refmap merges…) over the fixup output
        val outResources = resources.map { (name, bytes) ->
            val fixed = when {
                name.equals("META-INF/MANIFEST.MF", ignoreCase = true) ->
                    name to JarFixups.remapManifest(Manifest(bytes.inputStream()), asmRemapper).toBytes()
                name.startsWith("META-INF/services/") ->
                    JarFixups.remapServiceName(name, asmRemapper) to JarFixups.remapServiceContents(bytes, asmRemapper)
                name.endsWith(".accesswidener", ignoreCase = true) ||
                    name.endsWith("accesstransformer.cfg", ignoreCase = true) ->
                    name to JarFixups.remapAccessFile(name, bytes, tree, fns, targetNs)
                else -> name to bytes
            }
            exts.firstNotNullOfOrNull { it.second.transformResource(fixed.first, fixed.second) }
                ?.let { fixed.first to it } ?: fixed
        }

        // extension-emitted entries (refmaps etc.) come last — written verbatim
        return Result(outClasses, outResources + exts.flatMap { it.second.resources() })
    }

    /**
     * Streaming jar remap — per-class workers go parse → remap → metadata →
     * access-summary → serialize in one pass, retaining only bytes +
     * [JarFixups.AccessSummary] (no `ClassNode` residency). The
     * package-access check collates summaries afterward and
     * [PackageAccess.FIX] patches flags in serialized bytes via
     * [JarFixups.patchClassAccess].
     *
     * Requires every extension factory to be `perClass` — `analyze`/
     * `finishAnalysis` never run here (they need whole-`ClassNode` access
     * across the jar), and extension instances are per-worker-thread.
     */
    fun remapStreaming(
        input: Path,
        output: Path,
        remapper: Remapper,
        tree: MappingTree,
        fns: Namespace,
        targetNs: Namespace,
        options: Options = Options(),
    ) {
        require(allPerClass(options)) { "streaming path requires all extensions to be perClass" }
        val readerFlags = if (options.removeFrames) ClassReader.SKIP_FRAMES else 0
        val ctx = ExtensionContext(tree, fns, targetNs, options)
        attachMethodOrigin(ctx, (remapper as? MappingRemapper)?.remapper, tree, fns)
        val factories = options.extensions
        // per-worker-thread instances; only postApply/transformResource/
        // resources run here (analyze*/finishAnalysis are node-path only)
        val extInstances = java.util.concurrent.ConcurrentLinkedQueue<AsmExtension>()
        val threadExts = ThreadLocal.withInitial {
            factories.map { it.create(ctx) }.also(extInstances::addAll)
        }

        val resources = mutableListOf<Pair<String, ByteArray>>()
        val rawClasses = mutableListOf<Pair<String, ByteArray>>()
        ZipFile(input.toFile()).use { zin ->
            for (entry in zin.entries().asSequence().sortedBy { it.name }) {
                if (entry.isDirectory) continue
                when {
                    entry.name.endsWith(".class") ->
                        rawClasses += entry.name to zin.getInputStream(entry).readBytes()
                    options.stripSignatures && JarFixups.isSignatureFile(entry.name) -> Unit
                    else -> resources += entry.name to zin.getInputStream(entry).readBytes()
                }
            }
        }

        // constant-group expressions resolve static-final values across the
        // whole input — collect in a cheap pre-pass only when unpick runs
        val fieldValues = if (options.unpickConstants && tree.constantGroups.iterator().hasNext()) {
            rawClasses.parallelStream().flatMap { e ->
                val node = ClassNode()
                ClassReader(e.second).accept(node, readerFlags)
                node.fields.filter { it.value != null && it.access and Opcodes.ACC_STATIC != 0 }
                    .map { "${node.name}.${it.name}" to (it.value to it.desc) }.stream()
            }.collect(Collectors.toMap({ it.first }, { it.second }, { a, _ -> a }))
        } else emptyMap()
        ctx.fieldValues = fieldValues

        // single pass per class: parse → remap → metadata → summary → bytes
        val remapCount = java.util.concurrent.atomic.AtomicInteger()
        val processed = rawClasses.parallelStream().map { e ->
            val (entryName, bytes) = e
            val node = ClassNode()
            ClassReader(bytes).accept(node, readerFlags)
            val outNode = ClassNode()
            node.accept(MappingClassRemapper(outNode, remapper))
            val cls = tree.getClass(fns, InternalName.unchecked(node.name))
            threadExts.get().forEach { it.postApply(node, outNode, cls) }
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            outNode.accept(writer)
            val summary = if (options.packageAccess != PackageAccess.NONE) {
                JarFixups.summarize(outNode)
            } else null
            val prefix = if (entryName.startsWith("META-INF/versions/")) {
                entryName.substring(0, entryName.indexOf('/', "META-INF/versions/".length) + 1)
            } else ""
            options.onProgress?.invoke("remap", remapCount.incrementAndGet(), rawClasses.size)
            Triple("$prefix${outNode.name}.class", writer.toByteArray(), summary)
        }.collect(Collectors.toList())

        if (options.packageAccess == PackageAccess.FIX) {
            val violations = JarFixups.checkPackageAccess(processed.mapNotNull { it.third }, options, LOGGER)
            if (!violations.isEmpty) {
                val byName = processed.associateBy { it.third!!.name }
                for (target in violations.brokenClasses.keys) {
                    val p = byName.getValue(target)
                    JarFixups.patchClassAccess(
                        p.second, Opcodes.ACC_PUBLIC,
                        violations.memberPatchesByClass[target] ?: emptySet()
                    )
                }
                for ((owner, memberKeys) in violations.memberPatchesByClass) {
                    if (violations.brokenClasses.containsKey(owner)) continue // already patched
                    JarFixups.patchClassAccess(byName.getValue(owner).second, 0, memberKeys)
                }
            }
        } else if (options.packageAccess == PackageAccess.WARN) {
            JarFixups.checkPackageAccess(processed.mapNotNull { it.third }, options, LOGGER)
        }

        val outResources = resources.map { (name, bytes) ->
            val fixed = when {
                name.equals("META-INF/MANIFEST.MF", ignoreCase = true) ->
                    name to JarFixups.remapManifest(Manifest(bytes.inputStream()), remapper).toBytes()
                name.startsWith("META-INF/services/") ->
                    JarFixups.remapServiceName(name, remapper) to JarFixups.remapServiceContents(bytes, remapper)
                name.endsWith(".accesswidener", ignoreCase = true) ||
                    name.endsWith("accesstransformer.cfg", ignoreCase = true) ->
                    name to JarFixups.remapAccessFile(name, bytes, tree, fns, targetNs)
                else -> name to bytes
            }
            threadExts.get().firstNotNullOfOrNull { it.transformResource(fixed.first, fixed.second) }
                ?.let { fixed.first to it } ?: fixed
        }

        ZipOutputStream(Files.newOutputStream(output).buffered()).use { zout ->
            // synthesize missing `dir/` parents — classloaders need them
            // for package resource enumeration (log4j `packages=` scan,
            // FML discovery, ...)
            val emittedDirs = HashSet<String>()
            fun putEntry(name: String, write: (java.io.OutputStream) -> Unit) {
                var i = name.indexOf('/')
                while (i != -1) {
                    val dir = name.substring(0, i + 1)
                    if (emittedDirs.add(dir)) {
                        zout.putNextEntry(ZipEntry(dir).apply { time = 0L })
                        zout.closeEntry()
                    }
                    i = name.indexOf('/', i + 1)
                }
                // explicit `dir/` input entry — the parent loop already
                // emitted it; writing it again would be a duplicate
                if (name.endsWith('/')) return
                zout.putNextEntry(ZipEntry(name))
                write(zout)
                zout.closeEntry()
            }
            val total = processed.size + outResources.size
            var writeCount = 0
            for ((name, bytes) in processed) {
                putEntry(name) { it.write(bytes) }
                options.onProgress?.invoke("write", ++writeCount, total)
            }
            for ((name, bytes) in outResources + extInstances.flatMap { it.resources() }) {
                putEntry(name) { it.write(bytes) }
                options.onProgress?.invoke("write", ++writeCount, total)
            }
        }
    }

    /** Serialize a [Result] to a jar at [output]. */
    fun writeJar(
        output: Path,
        result: Result,
        onProgress: ((phase: String, done: Int, total: Int) -> Unit)? = null
    ) {
        // ClassWriter(COMPUTE_MAXS) is per-class independent work — serialize
        // in parallel, then stream the zip in deterministic order
        val writeCount = java.util.concurrent.atomic.AtomicInteger()
        val total = result.classes.size + result.resources.size
        val classBytes = result.classes.parallelStream().map { e ->
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            e.second.accept(writer)
            onProgress?.invoke("write", writeCount.incrementAndGet(), total)
            e.first to writer.toByteArray()
        }.collect(Collectors.toList())
        ZipOutputStream(Files.newOutputStream(output).buffered()).use { zout ->
            // synthesize `dir/` parents — needed for package resource
            // enumeration under classloaders
            val emittedDirs = HashSet<String>()
            fun put(name: String, write: (java.io.OutputStream) -> Unit) {
                var i = name.indexOf('/')
                while (i != -1) {
                    val dir = name.substring(0, i + 1)
                    if (emittedDirs.add(dir)) {
                        zout.putNextEntry(ZipEntry(dir).apply { time = 0L })
                        zout.closeEntry()
                    }
                    i = name.indexOf('/', i + 1)
                }
                if (name.endsWith('/')) return
                zout.putNextEntry(ZipEntry(name).apply { time = 0L })
                write(zout)
                zout.closeEntry()
            }
            for ((name, bytes) in result.resources) {
                // manifest first so streaming readers see it early
                if (name.equals("META-INF/MANIFEST.MF", ignoreCase = true)) put(name) { it.write(bytes) }
            }
            for ((name, bytes) in classBytes) {
                put(name) { it.write(bytes) }
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
