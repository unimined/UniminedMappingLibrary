package xyz.wagyourtail.unimined.mapping.remapper.ext.annotation

import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext

/**
 * The table: annotation descriptor → param name → remap kind.
 *
 * Declarative annotation-driven remapping — instead of hand-writing visitor
 * logic per annotation, the table says *how each annotation parameter
 * remaps* and the engine rewrites matching values through the mapping
 * context. Shared by the bytecode side ([AnnotationTableExtension]) and the
 * source side (`MixinSourceExtension`) — same grammar, same semantics.
 *
 * ```kotlin
 * val table = annotationTable {
 *     annotation("Lorg/spongepowered/asm/mixin/injection/Inject;") {
 *         param("method", ParamRemap.MethodRef)
 *         param("at", ParamRemap.Nested(atTable))
 *     }
 *     shadow("Lorg/spongepowered/asm/mixin/Shadow;")
 * }
 * ```
 */
class AnnotationTable(
    val rules: Map<String, Map<String, ParamRemap>>,
    /** annotations marking "this member shadows a target member" — the
     *  member's own name remaps via the owning class's mixin targets */
    val shadowDescriptors: Set<String> = emptySet(),
    /** annotations marking accessor members — name derives via `get`/`set`/`is` */
    val accessorDescriptors: Set<String> = emptySet(),
    /** annotations marking invoker members — name derives via `call`/`invoke`/… */
    val invokerDescriptors: Set<String> = emptySet(),
    /** annotations marking "this class is a mixin" — `targets`/`value` params
     *  provide the owner hints for owner-less member refs */
    val mixinMarkers: Set<String> = setOf("Lorg/spongepowered/asm/mixin/Mixin;"),
) {

    /** `remap = false` disables processing for that annotation (mirrors
     *  tiny-remapper's per-annotation escape hatch) */
    fun isRemapEnabled(ann: AnnotationNode): Boolean {
        val values = ann.values ?: return true
        var i = 0
        while (i + 1 < values.size) {
            if (values[i] == "remap" && values[i + 1] == false) return false
            i += 2
        }
        return true
    }

    fun isShadow(ann: AnnotationNode) = ann.desc in shadowDescriptors
    fun isAccessor(ann: AnnotationNode) = ann.desc in accessorDescriptors
    fun isInvoker(ann: AnnotationNode) = ann.desc in invokerDescriptors
    fun isMixinMarker(ann: AnnotationNode) = ann.desc in mixinMarkers

    /** rewrite [ann]'s matching param values in place — string/Type refs
     *  through the declared [rules]; `remap=false` on the annotation skips */
    fun rewrite(ann: AnnotationNode, ctx: ExtensionContext, ownerHints: List<String>, recorder: RefmapRecorder? = null) {
        if (!isRemapEnabled(ann)) return
        val rules = rules[ann.desc] ?: return
        val values = ann.values ?: return
        var i = 0
        while (i + 1 < values.size) {
            val remap = rules[values[i]]
            if (remap != null) values[i + 1] = remap.apply(values[i + 1], ctx, ownerHints, recorder)
            i += 2
        }
    }

    /** the `prefix` param declared on an annotation (e.g. `@Shadow(prefix=…)`),
     *  as a list — empty when undeclared */
    fun declaredPrefixes(ann: AnnotationNode): List<String> {
        val values = ann.values ?: return emptyList()
        val out = mutableListOf<String>()
        var i = 0
        while (i + 1 < values.size) {
            if (values[i] == "prefix") {
                when (val v = values[i + 1]) {
                    is String -> out += v
                    is List<*> -> v.filterIsInstance<String>().forEach { out += it }
                }
            }
            i += 2
        }
        return out
    }

    /** the `aliases` param declared on an annotation — alternate target names */
    fun shadowAliases(ann: AnnotationNode): List<String> {
        val values = ann.values ?: return emptyList()
        var i = 0
        while (i + 1 < values.size) {
            if (values[i] == "aliases") {
                return when (val v = values[i + 1]) {
                    is String -> listOf(v)
                    is List<*> -> v.filterIsInstance<String>()
                    else -> emptyList()
                }
            }
            i += 2
        }
        return emptyList()
    }

    /** candidate `(prefix, targetName)` pairs for a shadow member — declared
     *  prefixes stripped (re-applied on rename), the bare name, then aliases */
    fun shadowCandidates(memberName: String, ann: AnnotationNode): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (p in declaredPrefixes(ann)) {
            if (memberName.startsWith(p) && memberName.length > p.length) {
                out += p to memberName.substring(p.length)
            }
        }
        out += "" to memberName
        shadowAliases(ann).forEach { out += "" to it }
        return out
    }

    /** the explicit `value` param on an accessor/invoker annotation — the
     *  target member name written out, or null to derive from the member name */
    fun explicitValue(ann: AnnotationNode): String? {
        val values = ann.values ?: return null
        var i = 0
        while (i + 1 < values.size) {
            if (values[i] == "value") return values[i + 1] as? String
            i += 2
        }
        return null
    }

    /** derive the target member name from an accessor/invoker member name —
     *  `getFoo`/`setFoo`/`isFoo` → `foo`, `callFoo`/`invokeFoo`/`newFoo` → `foo`.
     *  Returns `(prefix, derivedName)`. */
    fun deriveTarget(memberName: String, isField: Boolean, extraPrefixes: List<String> = emptyList()): Pair<String, String>? {
        val prefixes = extraPrefixes + if (isField) listOf("get", "set", "is") else listOf("call", "invoke", "new", "create")
        for (p in prefixes) {
            if (memberName.startsWith(p) && memberName.length > p.length &&
                (memberName[p.length].isUpperCase() || memberName[p.length] == '$' || p.endsWith('$'))) {
                val rest = memberName.substring(p.length).trimStart('$')
                if (rest.isNotEmpty()) return p to rest.replaceFirstChar { it.lowercaseChar() }
            }
        }
        return null
    }

    fun annotations(node: ClassNode) = (node.visibleAnnotations.orEmpty() + node.invisibleAnnotations.orEmpty())
    fun annotations(node: MethodNode) = (node.visibleAnnotations.orEmpty() + node.invisibleAnnotations.orEmpty())
    fun annotations(node: FieldNode) = (node.visibleAnnotations.orEmpty() + node.invisibleAnnotations.orEmpty())

    /** mixin targets declared on the class — marker `targets`/`value` params,
     *  strings and `Type`s, normalized to internal names */
    fun mixinTargets(node: ClassNode, ctx: ExtensionContext): List<String> =
        annotations(node).filter { it.desc in mixinMarkers }.flatMap { ann ->
            val vals = ann.values ?: emptyList<Any>()
            val out = mutableListOf<String>()
            var i = 0
            while (i + 1 < vals.size) {
                val v = vals[i + 1]
                if (vals[i] == "targets" || vals[i] == "value") {
                    when (v) {
                        is String -> out += v.replace('.', '/')
                        is Type -> out += v.internalName
                        is List<*> -> v.forEach {
                            when (it) {
                                is String -> out += it.replace('.', '/')
                                is Type -> out += it.internalName
                            }
                        }
                    }
                }
                i += 2
            }
            out
        }

    /** `remap = false` on any mixin marker disables remapping for the whole class */
    fun mixinRemapEnabled(node: ClassNode): Boolean =
        annotations(node).filter { it.desc in mixinMarkers }.all { isRemapEnabled(it) }

    // ---- composition / DSL ----

    fun toBuilder(): Builder = Builder().include(this)

    operator fun plus(other: AnnotationTable): AnnotationTable = toBuilder().include(other).build()

    class Builder {
        private val rules = mutableMapOf<String, MutableMap<String, ParamRemap>>()
        private val shadows = mutableSetOf<String>()
        private val accessors = mutableSetOf<String>()
        private val invokers = mutableSetOf<String>()
        private val markers = mutableSetOf<String>()

        /** declare an annotation descriptor's param rules —
         *  `annotation("L..;") { param("method", ParamRemap.MethodRef) }` */
        fun annotation(descriptor: String, block: RuleBuilder.() -> Unit): Builder {
            rules.getOrPut(descriptor) { mutableMapOf() }.let { RuleBuilder(it).block() }
            return this
        }

        /** merge another table — its rules/descriptor sets fold into this one */
        fun include(table: AnnotationTable): Builder {
            table.rules.forEach { (desc, params) -> rules.getOrPut(desc) { mutableMapOf() }.putAll(params) }
            shadows += table.shadowDescriptors
            accessors += table.accessorDescriptors
            invokers += table.invokerDescriptors
            markers += table.mixinMarkers
            return this
        }

        fun shadow(descriptor: String): Builder { shadows += descriptor; return this }
        fun accessor(descriptor: String): Builder { accessors += descriptor; return this }
        fun invoker(descriptor: String): Builder { invokers += descriptor; return this }
        fun mixinMarker(descriptor: String): Builder { markers += descriptor; return this }

        fun build(): AnnotationTable = AnnotationTable(
            rules, shadows, accessors, invokers,
            markers.ifEmpty { setOf("Lorg/spongepowered/asm/mixin/Mixin;") },
        )
    }

    class RuleBuilder(private val rule: MutableMap<String, ParamRemap>) {
        fun param(name: String, remap: ParamRemap): RuleBuilder { rule[name] = remap; return this }
    }
}

/** DSL entry — `annotationTable { annotation("L..;") { param("method", ParamRemap.MethodRef) } }` */
fun annotationTable(block: AnnotationTable.Builder.() -> Unit): AnnotationTable =
    AnnotationTable.Builder().apply(block).build()
