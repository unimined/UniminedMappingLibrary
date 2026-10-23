package xyz.wagyourtail.unimined.mapping.remapper.source

import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext

/**
 * Source-extension seam — the source pipeline's counterpart to
 * `AsmExtension`. Each extension receives every parsed source file and
 * mutates its [ChangeSet]: identifier renames handled by the core remap
 * pass, or metadata-driven insertions (implements entries, javadoc, mixin
 * member names) for elements marked
 * `metadata.filterIsInstance<SourceTransform>()`.
 *
 * Register via `SourceOptions(extensions = listOf(…))`.
 */
fun interface SourceExtension {

    /** mutate [changes] for [file] — [ctx] gives tree + namespace context */
    fun transform(file: SourceFileView, ctx: ExtensionContext, changes: ChangeSet, errors: MutableList<Pair<Int, String>>)
}
