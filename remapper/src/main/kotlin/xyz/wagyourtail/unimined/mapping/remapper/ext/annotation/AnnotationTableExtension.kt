package xyz.wagyourtail.unimined.mapping.remapper.ext.annotation
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension

/** how annotation string values get their target-space replacements */
enum class AnnotationApplyMode {
    /** semantic resolve: parse each ref and look it up in the mapping tree —
     *  shipped refmap entries are only a fallback for refs the tree misses */
    RESOLVE,

    /** verbatim: plain key→value substitution from the *shipped* refmap —
     *  mirrors JarModAgent's `RefmapAnnotationPreprocessor`. Every string in
     *  a marker-annotated class's annotations that equals a refmap key is
     *  replaced by its (source→target remapped) value; strings without an
     *  entry pass through untouched. Member names are left alone. */
    REFMAP,
}

/** drives an [AnnotationTable] over remapped nodes — generic, mixin-agnostic.
 *  [recorder] (optional) receives canonical refs for refmap emission and,
 *  in [AnnotationApplyMode.REFMAP] mode, supplies the substitution table. */
class AnnotationTableExtension(
    private val ctx: ExtensionContext,
    private val table: AnnotationTable,
    private val recorder: RefmapRecorder? = null,
    private val mode: AnnotationApplyMode = AnnotationApplyMode.RESOLVE,
) : AsmExtension {

    /** resolve a member through the recorder's existing-refmap entry —
     *  [name] is the source-space candidate; the entry holds an fns-space
     *  canonical ref which remaps to target space */
    private fun existingMember(name: String, isField: Boolean): ResolvedRef? =
        recorder?.existingEntry(name)?.let {
            MemberRefParts.parse(it, isField)?.resolve(ctx, emptyList(), isField)
        }

    /** map a shipped refmap value (fns-space canonical ref or class name)
     *  to target space; null when it isn't a resolvable ref */
    private fun mapRefValue(value: String): String? {
        MemberRefParts.parse(value, isField = false)?.resolve(ctx, emptyList(), isField = false)
            ?.let { return it.toString() }
        MemberRefParts.parse(value, isField = true)?.resolve(ctx, emptyList(), isField = true)
            ?.let { return it.toString() }
        // class-name values (`pkg/Foo`, `Lpkg/Foo;`) — map when they resolve
        val stripped = value.removePrefix("L").removeSuffix(";")
        return ctx.mapClass(stripped).takeIf { it != stripped }
    }

    /** verbatim refmap substitution — walk [ann]'s values; strings matching
     *  a shipped-refmap key get the remapped entry value, lists and nested
     *  annotations recurse (mirrors JMA's RefmapAnnotationPreprocessor) */
    private fun verbatim(v: Any?): Any? = when (v) {
        is String -> recorder?.existingEntry(v)?.let { mapRefValue(it) } ?: v
        is AnnotationNode -> { rewriteVerbatim(v); v }
        is List<*> -> v.map { verbatim(it) }
        else -> v
    }

    private fun rewriteVerbatim(ann: AnnotationNode) {
        if (!table.isRemapEnabled(ann)) return
        val values = ann.values ?: return
        var i = 0
        while (i + 1 < values.size) {
            values[i + 1] = verbatim(values[i + 1])
            i += 2
        }
    }

    override fun postApply(input: ClassNode, output: ClassNode, cls: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping?) {
        recorder?.currentMixin = output.name
        if (!table.mixinRemapEnabled(output)) {
            recorder?.currentMixin = null
            return
        }
        if (mode == AnnotationApplyMode.REFMAP) {
            for (ann in table.annotations(output)) rewriteVerbatim(ann)
            for (m in output.methods) for (ann in table.annotations(m)) rewriteVerbatim(ann)
            for (f in output.fields) for (ann in table.annotations(f)) rewriteVerbatim(ann)
            recorder?.currentMixin = null
            return
        }
        // mixin targets in *source* space — output's annotation is already
        // remapped, so map the names back through the reverse remapper for
        // member lookups (tree keys are source-side)
        val targets by lazy {
            table.mixinTargets(output, ctx).map {
                ctx.reverse.map(xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName.unchecked(it)).value
            }
        }

        // member renames first — reads explicit `value` params while they're
        // still source-side (the rewrite below mutates them in place)
        for (m in output.methods) {
            for (ann in table.annotations(m)) {
                if (!table.isRemapEnabled(ann)) continue
                if (table.isAccessor(ann) || table.isInvoker(ann)) {
                    val isField = table.isAccessor(ann)
                    val explicit = table.explicitValue(ann)
                    val derived = explicit?.let { null to it }
                        ?: table.deriveTarget(m.name, isField, table.declaredPrefixes(ann))
                        ?: continue
                    var done = false
                    for (t in targets) {
                        val mapped = if (isField) ctx.mapField(t, derived.second, null)
                                     else ctx.mapMethod(t, derived.second, if (explicit != null) null else m.desc)
                        if (mapped != derived.second) {
                            recorder?.recordMemberRename(
                                derived.second, isField,
                                ResolvedRef(ctx.mapClass(t), mapped, ctx.resolveMemberDesc(t, derived.second, isField), isField, targets.size <= 1)
                            )
                            if (explicit == null) {
                                m.name = derived.first + mapped.replaceFirstChar { it.uppercaseChar() }
                            }
                            done = true
                            break
                        }
                    }
                    if (!done) existingMember(derived.second, isField)?.let {
                        recorder?.recordMemberRename(derived.second, isField, it)
                        if (explicit == null) {
                            m.name = derived.first + it.name.replaceFirstChar { c -> c.uppercaseChar() }
                        }
                    }
                } else if (table.isShadow(ann)) {
                    rename@ for ((prefix, cand) in table.shadowCandidates(m.name, ann)) {
                        for (t in targets) {
                            val mapped = ctx.mapMethod(t, cand, m.desc)
                            if (mapped != cand) {
                                recorder?.recordMemberRename(
                                    m.name, false,
                                    ResolvedRef(ctx.mapClass(t), mapped, ctx.mapDesc(m.desc, false), false, targets.size <= 1)
                                )
                                m.name = prefix + mapped
                                break@rename
                            }
                        }
                        // resolution fallback: a stale input refmap may know this member
                        existingMember(cand, isField = false)?.let {
                            recorder?.recordMemberRename(m.name, false, it)
                            m.name = prefix + it.name
                            break@rename
                        }
                    }
                }
            }
        }
        for (f in output.fields) {
            for (ann in table.annotations(f)) {
                if (table.isShadow(ann) && table.isRemapEnabled(ann)) {
                    rename@ for ((prefix, cand) in table.shadowCandidates(f.name, ann)) {
                        for (t in targets) {
                            val mapped = ctx.mapField(t, cand, f.desc)
                            if (mapped != cand) {
                                recorder?.recordMemberRename(
                                    f.name, true,
                                    ResolvedRef(ctx.mapClass(t), mapped, ctx.mapDesc(f.desc, true), true, targets.size <= 1)
                                )
                                f.name = prefix + mapped
                                break@rename
                            }
                        }
                        existingMember(cand, isField = true)?.let {
                            recorder?.recordMemberRename(f.name, true, it)
                            f.name = prefix + it.name
                            break@rename
                        }
                    }
                }
            }
        }

        // annotation param rewrites — class-level first (targets feed recording)
        for (ann in table.annotations(output)) table.rewrite(ann, ctx, listOf(output.name), recorder)
        for (m in output.methods) {
            for (ann in table.annotations(m)) table.rewrite(ann, ctx, targets, recorder)
        }
        for (f in output.fields) {
            for (ann in table.annotations(f)) table.rewrite(ann, ctx, targets, recorder)
        }
        recorder?.currentMixin = null
    }
}
