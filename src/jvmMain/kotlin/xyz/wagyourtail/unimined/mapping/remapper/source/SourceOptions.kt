package xyz.wagyourtail.unimined.mapping.remapper.source

/** options for [SourceRemapper.remap]. */
data class SourceOptions(
    /** the parse/resolve backend — defaults to the PSI engine
     *  (kotlin-compiler-embeddable on the classpath) */
    val engine: SourceEngineFactory = SourceEngineFactory { sources, classpath, ctx, extensions ->
        xyz.wagyourtail.unimined.mapping.remapper.source.psi.PsiSourceEngine(sources, classpath, ctx, extensions)
    },
    /** metadata-insertion stages run per source file — path-2 transforms
     *  (interface injection, javadoc, …) applied over parsed sources */
    val extensions: List<SourceExtension> = emptyList(),
    /** run manifest/services/access-file fixups on non-source resources too */
    val remapResources: Boolean = true,
)
