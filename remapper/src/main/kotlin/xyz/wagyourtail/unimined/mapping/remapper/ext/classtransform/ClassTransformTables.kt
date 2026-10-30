package xyz.wagyourtail.unimined.mapping.remapper.ext.classtransform

import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTable
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.annotationTable

/**
 * Pre-populated [AnnotationTable]s for
 * [ClassTransform](https://github.com/Lenni0451/ClassTransform) annotations —
 * plug [STANDARD] into an `AnnotationTableExtension` or
 * [ClassTransformExtension] to remap jar-mod transformers alongside the jar.
 *
 * The rules mirror classtransform's own `@AnnotationRemap` meta-annotations:
 * `SHORT_MEMBER`/`MEMBER` params remap through the member-ref grammar,
 * `CLASS` params through class remapping, `ANNOTATION` params recurse.
 * `CTarget.target` is `DYNAMIC` upstream (its semantics depend on the
 * injection point kind); the common case is a member ref, which
 * [ParamRemap.MemberRef] covers — unresolvable strings pass through verbatim,
 * where JMA refmap replacement can still catch them.
 *
 * Extend with your own annotations via the DSL or `+`, same as `MixinTables`.
 */
object ClassTransformTables {

    private const val A = "Lnet/lenni0451/classtransform/annotations/"
    private const val AI = "Lnet/lenni0451/classtransform/annotations/injection/"

    /** nested injection-point grammar — `@CTarget`'s `target` is the member
     *  ref behind the injection point; `@CSlice`'s `from`/`to` bound the
     *  injection range with nested `@CTarget`s. These can't be top-level
     *  annotations, so like `POINTCUT` they live in their own table. */
    val POINTCUT: AnnotationTable = annotationTable {
        annotation("${A}CTarget;") {
            param("target", ParamRemap.MemberRef)
        }
        annotation("${A}CSlice;") {
            param("from", ParamRemap.Nested { POINTCUT })
            param("to", ParamRemap.Nested { POINTCUT })
        }
    }

    /** the classtransform annotation set */
    val STANDARD: AnnotationTable = annotationTable {
        // injection annotations share the method/target/slice grammar —
        // the package rule covers CInject/CRedirect/CModifyExpressionValue/
        // CWrapCondition/CModifyConstant/… including future additions;
        // per-annotation entries below override params that differ
        annotationPackage("${AI}") {
            param("method", ParamRemap.MethodRef)
            // `value` names the enclosing target method wherever it appears
            // (CWrapCatch/CASM/COverride) — additive, no conflicts
            param("value", ParamRemap.MethodRef)
            param("target", ParamRemap.Nested(POINTCUT))
            param("slice", ParamRemap.Nested(POINTCUT))
        }
        // override: CWrapCatch.target is a MEMBER ref (the member to wrap),
        // not a nested @CTarget
        annotation("${AI}CWrapCatch;") {
            param("target", ParamRemap.MemberRef)
        }

        // transformer class marker — `value`/`name` declare the target
        // classes and feed owner hints for owner-less member refs
        annotation("${A}CTransformer;") {
            param("value", ParamRemap.ClassRef)
            param("name", ParamRemap.ClassRef)
        }
        mixinMarker("${A}CTransformer;")

        // member-name-as-target markers — when `value` is empty the annotated
        // member's own name is the target name, so it renames like a shadow
        annotation("${A}CShadow;") { param("value", ParamRemap.MemberRef) }
        shadow("${A}CShadow;")
        shadow("${AI}COverride;")

        // a member declaration string — `@CStub(value = "Lowner;name desc")`
        annotation("${A}CStub;") { param("value", ParamRemap.MemberRef) }
    }

    /** the full classtransform table — the default */
    val ALL = STANDARD
}
