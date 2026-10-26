package xyz.wagyourtail.unimined.mapping.remapper.source

import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import java.nio.file.Path

/** one source unit — [path] is the jar entry name, [text] the source */
class SourceUnit(val path: String, val text: String)

/**
 * A source-remap backend. The core pipeline ([SourceRemapper]) is
 * engine-agnostic — swap [SourceOptions.engine] to change parse/resolve
 * machinery (and the dependency footprint that comes with it).
 *
 * - [psi.PsiSourceEngine] — kotlin-compiler-embeddable PSI, Java+Kotlin,
 *   exact symbol resolution. Heavy dep, shipped `implementation`-scoped.
 * - A javac (`jdk.compiler`) engine can implement this interface for a
 *   zero-dep, Java-only build.
 */
interface SourceEngine {

    /** `true` when [path] is a source file this engine handles */
    fun handles(path: String): Boolean

    /**
     * Parse and remap [unit]; returns the rewritten text, or `null` to emit
     * the input unchanged. Per-file diagnostics go to [errors] as
     * `(line, message)`.
     */
    fun remap(unit: SourceUnit, errors: MutableList<Pair<Int, String>>): String?

    /** per-line net deltas the remap introduced for [unit] —
     *  `(line, insertedCount)`; empty when the engine's transforms are
     *  line-neutral (the contract all built-in extensions follow) */
    fun lineDeltas(unit: SourceUnit): List<Pair<Int, Int>> = emptyList()

    /** the file's mapped package (internal `/` form) — `null` when the
     *  primary class didn't move, `""` when it maps to the default package.
     *  Drives output-path relocation + `package` decl rewrites */
    fun mappedPackage(unit: SourceUnit): String? = null

    /** release engine resources (temp dirs, compiler env) */
    fun close() {}
}

/** builds an engine over a fixed source set + resolution classpath */
fun interface SourceEngineFactory {

    /**
     * [sources] — the full source set (engines may need whole-set analysis,
     * e.g. kotlin `BindingContext`); [classpath] — jars for symbol resolution
     * (the matching bytecode jar(s) + libraries); [ctx] — the mapping context.
     */
    fun create(sources: List<SourceUnit>, classpath: List<Path>, ctx: ExtensionContext, extensions: List<SourceExtension>): SourceEngine
}
