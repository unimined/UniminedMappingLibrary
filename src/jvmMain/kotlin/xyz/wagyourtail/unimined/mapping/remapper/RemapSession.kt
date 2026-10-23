package xyz.wagyourtail.unimined.mapping.remapper

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmRemapper
import xyz.wagyourtail.unimined.mapping.remapper.asm.Options
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
    private val libraries: Set<Path>,
    private val asmOptions: Options,
    private val srcOptions: SourceOptions,
) {

    companion object {
        /** scan `libraries` (classpath deps + the jars about to be remapped)
         *  once — returns a session whose overlay/inheritance state is
         *  precomputed for `remapJar`/`remapSources` */
        suspend fun scan(
            trees: List<MappingTree>,
            fns: Namespace, targetNs: Namespace,
            libraries: Set<Path> = emptySet(),
            asmOptions: Options = Options(),
            srcOptions: SourceOptions = SourceOptions(),
        ): RemapSession = RemapSession(trees, fns, targetNs, libraries, asmOptions, srcOptions)
    }

    /** bytecode remap one jar through the session's merged tree + libraries */
    suspend fun remapJar(input: Path, output: Path) =
        AsmRemapper.remap(input, output, trees, fns, targetNs, libraries, asmOptions)

    /** source remap a paired jar — `srcJar` + `binJar` (resolution classpath) */
    fun remapSources(srcJar: Path, binJar: Path, output: Path) =
        SourceRemapper.remapPaired(srcJar, binJar, output, trees.last(), fns, targetNs, options = srcOptions)
}
