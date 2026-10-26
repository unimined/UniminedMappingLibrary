package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext

/**
 * Built-in remap stages as [AsmExtensionFactory]s — the default value of
 * [Options.extensions]; disable a stage by removing it from that list.
 * All are `perClass` (stateless, thread-safe `postApply`), so they run on
 * the streaming path.
 */
object BuiltinAsmExtensions {

    /**
     * Rebuilds `SourceFile` to the *mapped outer-class* `.java` name —
     * strips `$inner` suffixes and tolerates weird nested names like
     * `a$b/c`. Honors [Options.rebuildSourceFilenames].
     */
    val SOURCE_FILES = object : AsmExtensionFactory {
        override val perClass get() = true
        override fun create(ctx: ExtensionContext) = object : AsmExtension {
            override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {
                if (!ctx.options.rebuildSourceFilenames || output.sourceFile == null) return
                output.sourceFile = sourceFileName(output.name)
            }
        }
    }

    /** Source-file name derivation: mapped outer-class name + `.java`. */
    fun sourceFileName(mappedName: String): String {
        var end = mappedName.indexOf('$')
        if (end <= 0) end = mappedName.length // require at least 1 char of outer name
        val start = mappedName.lastIndexOf('/', end - 1) + 1 // avoid searching after $ for names like a$b/c
        if (end <= start) end = mappedName.length
        return mappedName.substring(start, end) + ".java"
    }

    /**
     * Class-row metadata in targetNs — access fixes (AW/AT), signatures,
     * annotations, interfaces, seals, inner-class attrs, enum constants,
     * bridges, exceptions, and parameter/local names ([LocalVarRemapper]).
     */
    val METADATA = object : AsmExtensionFactory {
        override val perClass get() = true
        override fun create(ctx: ExtensionContext) = object : AsmExtension {
            override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {
                MetadataAdapter.apply(output, cls, ctx.tree, ctx.targetNs, ctx.options, input, ctx.methodOrigin)
            }
        }
    }

    /**
     * Constant-group unpick — literal loads matching a `ConstantGroupMapping`
     * are replaced by the group's expression. Honors [Options.unpickConstants];
     * field values come from [ExtensionContext.fieldValues] (populated by the
     * pipeline when groups exist).
     */
    val CONSTANT_GROUPS = object : AsmExtensionFactory {
        override val perClass get() = true
        override fun create(ctx: ExtensionContext) = object : AsmExtension {
            override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {
                ConstantGroupEmitter.apply(output, ctx.tree, ctx.fns, ctx.targetNs, ctx.options, ctx.fieldValues)
            }
        }
    }

    /** The default stage set — [Options.extensions] defaults to this. */
    fun defaults(): List<AsmExtensionFactory> = listOf(SOURCE_FILES, METADATA, CONSTANT_GROUPS)

}
