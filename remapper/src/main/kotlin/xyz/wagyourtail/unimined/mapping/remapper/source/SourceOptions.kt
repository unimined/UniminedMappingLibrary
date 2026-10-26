package xyz.wagyourtail.unimined.mapping.remapper.source

/** options for [SourceRemapper.remap]. */
data class SourceOptions(
    /** path-1 (paired source+bytecode) invariant: inserted text mustn't shift
     *  lines — newlines in insertions get eaten (`import`/`doc` inserts land
     *  on one line). Turn off for path-2 when `Linemap` adjustment applies. */
    val linePreserving: Boolean = true,
    /** the parse/resolve backend — defaults to the PSI engine
     *  (kotlin-compiler-embeddable on the classpath) */
    val engine: SourceEngineFactory = SourceEngineFactory { sources, classpath, ctx, extensions ->
        xyz.wagyourtail.unimined.mapping.remapper.source.psi.PsiSourceEngine(sources, classpath, ctx, extensions, linePreserving = linePreserving)
    },
    /** metadata-insertion stages run per source file — path-2 transforms
     *  (interface injection, javadoc, …) applied over parsed sources */
    val extensions: List<SourceExtension> = emptyList(),
    /** run manifest/services/access-file fixups on non-source resources too */
    val remapResources: Boolean = true,
)
