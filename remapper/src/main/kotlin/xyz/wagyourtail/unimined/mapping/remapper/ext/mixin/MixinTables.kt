package xyz.wagyourtail.unimined.mapping.remapper.ext.mixin

import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTable
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTableExtension
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.annotationTable

/**
 * Pre-populated [AnnotationTable]s — plug [ALL] into an
 * [AnnotationTableExtension] (or [MixinExtension]) to remap real mixins.
 *
 * - [STANDARD] — the sponge mixin annotation set: `@Mixin` targets, injection
 *   `method`/`at`/`slice`/`target` refs, `@Accessor`/`@Invoker` prefix-derived
 *   member renames, and shadow-marker member renames.
 * - [EXTRAS] — LlamaLad's MixinExtras annotations (`@WrapOperation`,
 *   `@ModifyExpressionValue`, `@Definition`, …) — same `method`/`at` grammar.
 * - [ALL] — both, the default for [MixinExtension].
 *
 * Extend with your own annotations via the DSL or `+`:
 * ```kotlin
 * val table = MixinTables.STANDARD + annotationTable {
 *     annotation("Lcom/example/CustomInject;") { param("method", ParamRemap.MethodRef) }
 * }
 * ```
 */
object MixinTables {

    private const val P = "Lorg/spongepowered/asm/mixin/"
    private const val E = "Lcom/llamalad7/mixinextras/"

    /** `@At`-style pointcut annotations — `target` is a member ref
     *  (`Lowner;name(desc)` / `Lowner;name:desc` / `owner.name(desc)` /
     *  `name(desc)` forms), `args` a `key=value` list (`class=` remaps). */
    val POINTCUT = annotationTable {
        annotation("${P}injection/At;") {
            param("target", ParamRemap.MethodRef)
            param("args", ParamRemap.InjectionArgs)
        }
        annotation("${P}injection/Desc;") {
            param("target", ParamRemap.MethodRef)
            param("owner", ParamRemap.ClassRef)
        }
        annotation("${P}injection/Pointcut;") {
            param("target", ParamRemap.MethodRef)
        }
    }

    /** the full sponge mixin table */
    val STANDARD = annotationTable {
        annotation("${P}Mixin;") {
            param("targets", ParamRemap.ClassRef)
            param("value", ParamRemap.ClassRef)
        }
        mixinMarker("${P}Mixin;")

        annotation("${P}Implements;") {
            param("value", ParamRemap.Nested(annotationTable {
                annotation("${P}Interface;") {
                    param("iface", ParamRemap.ClassRef)
                    param("value", ParamRemap.ClassRef)
                }
            }))
        }
        annotation("${P}Interface;") {
            param("iface", ParamRemap.ClassRef)
            param("value", ParamRemap.ClassRef)
        }
        annotation("${P}Pseudo;") {}

        // injection annotations sharing the method/at/slice grammar
        for (desc in listOf(
            "Inject", "ModifyArg", "ModifyArgs", "ModifyConstant",
            "ModifyVariable", "ModifyExpressionValue", "ModifyReceiver",
            "ModifyReturnValue", "Redirect", "Surrogate", "Group",
        )) {
            annotation("${P}injection/$desc;") {
                param("method", ParamRemap.MethodRef)
                param("at", ParamRemap.Nested(POINTCUT))
                param("slice", ParamRemap.Nested(POINTCUT))
                param("target", ParamRemap.Nested(POINTCUT))
            }
        }

        annotation("${P}gen/Accessor;") { param("value", ParamRemap.FieldRef) }
        annotation("${P}gen/Invoker;") { param("value", ParamRemap.MethodRef) }
        accessor("${P}gen/Accessor;")
        invoker("${P}gen/Invoker;")

        // member-name shadow markers
        shadow("${P}Shadow;")
        shadow("${P}Overwrite;")
        shadow("${P}Unique;")
        shadow("${P}Final;")
        shadow("${P}Mutable;")
        shadow("${P}Intrinsic;")
    }

    /** MixinExtras (`com.llamalad7.mixinextras`) — injector annotations share
     *  the `method`/`at` grammar; `@Definition` binds an `id` to a member ref. */
    val EXTRAS = annotationTable {
        annotation("${E}injector/ModifyExpressionValue;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
            param("slice", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/ModifiesExpression;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/ModifyReceiver;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
            param("slice", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/ModifyReturnValue;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
            param("slice", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/WrapWithCondition;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
            param("slice", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/v2/WrapWithCondition;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
            param("slice", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/wrapoperation/WrapOperation;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
            param("slice", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/wrapmethod/WrapMethod;") {
            param("method", ParamRemap.MethodRef)
        }
        annotation("${E}injector/ModifyArg;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/ModifyArgs;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
        }
        annotation("${E}injector/SugarWrapper;") {
            param("method", ParamRemap.MethodRef)
            param("at", ParamRemap.Nested(POINTCUT))
            param("signature", ParamRemap.MethodRef)
        }

        // expression/definition sugar — @Expression's DSL identifiers are bound
        // by @Definition(id=…); the refs live on the definition params:
        // `method`/`field` are String[] of owner-qualified member refs, `at`
        // nested @At points, `value` the owner classes
        val definitionRules: AnnotationTable.Builder.(String) -> Unit = { desc ->
            annotation(desc) {
                param("method", ParamRemap.MethodRef)
                param("field", ParamRemap.FieldRef)
                param("at", ParamRemap.Nested(POINTCUT))
                param("value", ParamRemap.ClassRef)
            }
        }
        definitionRules("${E}expression/Definition;")
        annotation("${E}expression/Definitions;") {
            param("value", ParamRemap.Nested(annotationTable {
                definitionRules("${E}expression/Definition;")
            }))
        }
    }

    /** sponge mixin + MixinExtras — the default table */
    val ALL = STANDARD + EXTRAS
}
