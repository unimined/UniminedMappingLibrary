package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext

interface AsmExtension {

    /** inspect a raw input class before it is remapped */
    fun analyze(node: ClassNode) {}

    /** all input classes analyzed — resolve collected state before transforms */
    fun finishAnalysis() {}

    /** inspect a raw input resource (mixin configs, existing refmaps…) —
     *  called during analysis for every non-class entry, before
     *  [finishAnalysis]. Sees the *input* bytes, not the fixed-up output. */
    fun analyzeResource(path: String, bytes: ByteArray) {}

    /** transform a remapped class; [cls] is its tree row (may be null for
     *  unmapped classes) */
    fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {}

    /** replace a resource's output bytes — receives the standard-fixup
     *  (manifest/services/access-file) output and returns its replacement, or
     *  `null` to leave it unchanged. First non-null result wins. */
    fun transformResource(path: String, bytes: ByteArray): ByteArray? = null

    /** extra entries for the output jar — `(path, bytes)` pairs appended as-is
     *  (not run through manifest/services fixups — write final names) */
    fun resources(): List<Pair<String, ByteArray>> = emptyList()

}

/** Factory for per-remap [AsmExtension] instances — extension state is per-jar,
 *  so a new instance is created for each [AsmRemapper.remap] call. */
fun interface AsmExtensionFactory {
    fun create(ctx: ExtensionContext): AsmExtension
}
