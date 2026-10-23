package xyz.wagyourtail.unimined.mapping.remapper.ext.annotation
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension

/** drives an [AnnotationTable] over remapped nodes — generic, mixin-agnostic.
 *  [recorder] (optional) receives canonical refs for refmap emission. */
class AnnotationTableExtension(
    private val ctx: ExtensionContext,
    private val table: AnnotationTable,
    private val recorder: RefmapRecorder? = null,
) : AsmExtension {

    /** resolve a member through the recorder's existing-refmap entry —
     *  [name] is the source-space candidate; the entry holds an fns-space
     *  canonical ref which remaps to target space */
    private fun existingMember(name: String, isField: Boolean): ResolvedRef? =
        recorder?.existingEntry(name)?.let {
            MemberRefParts.parse(it, isField)?.resolve(ctx, emptyList(), isField)
        }

    override fun postApply(input: ClassNode, output: ClassNode, cls: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping?) {
        recorder?.currentMixin = output.name
        if (!table.mixinRemapEnabled(output)) {
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
