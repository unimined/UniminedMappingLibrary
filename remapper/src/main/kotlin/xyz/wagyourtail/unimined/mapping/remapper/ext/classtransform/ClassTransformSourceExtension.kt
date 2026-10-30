package xyz.wagyourtail.unimined.mapping.remapper.ext.classtransform

import xyz.wagyourtail.unimined.mapping.remapper.ext.mixin.MixinSourceExtension

/**
 * Source-side classtransform handling — the `SourceExtension` counterpart to
 * [ClassTransformExtension], driven by [ClassTransformTables.ALL].
 *
 * - annotation *params* always remap (`@CInject(method = "foo()V")`,
 *   `@CTarget(target = "Lowner;name desc")`, nested annotations, lists)
 * - `@CShadow`/`@COverride` *member names* rename to the mapped target — only
 *   when [refmap] is false; in refmap mode the runtime binding comes from the
 *   refmap, so member names are preserved as written.
 *
 * ```kotlin
 * SourceOptions(extensions = listOf(ClassTransformSourceExtension()))           // non-refmap
 * SourceOptions(extensions = listOf(ClassTransformSourceExtension(refmap = true))) // params only
 * ```
 */
class ClassTransformSourceExtension(
    refmap: Boolean = false,
) : MixinSourceExtension(ClassTransformTables.ALL, refmap)
