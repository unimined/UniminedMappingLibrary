package xyz.wagyourtail.unimined.mapping.remapper

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
import xyz.wagyourtail.unimined.mapping.remapper.asm.RemapBatch
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceOptions
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceRemapper
import java.nio.file.Path

/**
 * Scan-once, remap-per-jar session — the common remapper driver shape: build
 * the inheritance graph + merged tree once, then overlay+remap any number of
 * input jars against it (a mod loader's per-jar remap).
 *
 * ```
 * val session = RemapSession.scan(trees, fns, targetNs, libraries)
 * session.remapJar(base, out)      // obf → named bytecode
 * session.remapJar(mod, out2)      // same merged tree, second jar
 * ```
 */
class RemapSession private constructor(
    private val trees: List<MappingTree>,
    private val fns: Namespace,
    private val targetNs: Namespace,
    private val batch: RemapBatch,
    private val asmOptions: Options,
    private val srcOptions: SourceOptions,
) : AutoCloseable {

    companion object {
        /** scan `libraries` (classpath deps + the jars about to be remapped)
         *  once — returns a session whose merged tree + inheritance state is
         *  precomputed for `remapJar`/`remapSources` (the per-jar work
         *  shrinks to jar IO + parse + rewrite + write). */
        suspend fun scan(
            trees: List<MappingTree>,
            fns: Namespace, targetNs: Namespace,
            libraries: Set<Path> = emptySet(),
            asmOptions: Options = Options(),
            srcOptions: SourceOptions = SourceOptions(),
        ): RemapSession = RemapSession(
            trees, fns, targetNs,
            AsmRemapper.prepareBatch(trees, fns, targetNs, libraries),
            asmOptions, srcOptions
        )
    }

    /** bytecode remap one jar through the session's shared merge+groups —
     *  the input's own classes fold into the graph on the way in */
    suspend fun remapJar(input: Path, output: Path) =
        batch.remap(
            input, output,
            ASMInheritanceTree.scan(setOf(input)), asmOptions
        )

    /** source remap a paired jar — `srcJar` + `binJar` (resolution classpath) */
    fun remapSources(srcJar: Path, binJar: Path, output: Path) =
        SourceRemapper.remapPaired(srcJar, binJar, output, trees.last(), fns, targetNs, options = srcOptions)

    override fun close() = batch.close()
}
