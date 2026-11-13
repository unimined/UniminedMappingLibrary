package xyz.wagyourtail.unimined.mapping.remapper.ext.annotation
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
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
 *  in [AnnotationApplyMode.REFMAP] mode, supplies the substitution table.
 *
 *  Member renames (`@Shadow`/`@Accessor`/`@Invoker` decls) are resolved in
 *  [analyze] — on the raw source-space node — and stored in [memberRenames].
 *  [postApply] then writes the new name onto the declaring class *and*
 *  rewrites every reference to the renamed member in every class it sees:
 *  the core remapper's insn pass already ran (mapping lookups miss on
 *  mixin-class members), so without this the decl and its refs diverge —
 *  e.g. a shadow field renamed `f_08006312` → `TICKS_RANDOMLY` while the
 *  handler's `getstatic` still says `f_08006312`. Owner keys are
 *  output-side names so non-mixin callers (accessor consumers) resolve too. */
class AnnotationTableExtension(
    private val ctx: ExtensionContext,
    private val table: AnnotationTable,
    private val recorder: RefmapRecorder? = null,
    private val mode: AnnotationApplyMode = AnnotationApplyMode.RESOLVE,
) : AsmExtension {

    /** a resolved member rename — [oldName] is the source-space name on the
     *  owning class, [targetDesc] the descriptor as it appears on the
     *  *output* node (types remapped) — that is what output insns carry.
     *  [newName] is null for refmap-only entries (explicit-value accessors
     *  keep their member name — only the refmap records the mapping) */
    private data class MemberRename(
        val oldName: String,
        val targetDesc: String,
        val newName: String?,
        val isField: Boolean,
    )

    /** output-side owner internal name → renames for members it declares */
    private val memberRenames = mutableMapOf<String, MutableList<MemberRename>>()

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

    /** resolve member renames on the raw (pre-transform) node — runs for
     *  every input class before any class is remapped, so the table is
     *  complete before insns of *any* output class are rewritten:
     *  same-class refs, nestmates and cross-class accessor callers all see
     *  the full set.
     *
     *  The input isn't guaranteed fully source-space: a step running an
     *  identity outer remap over an already-remapped jar (unimined's
     *  modArtifacts — the upstream fns→tns step already rewrote descs and
     *  insn owners, but not mixin member names) hands us target-space
     *  descs and `@Mixin` values over fns-space member names. Everything
     *  is normalized back to fns before tree lookups: class targets via
     *  [ExtensionContext.reverse], member descs via
     *  [ExtensionContext.reverseDesc] (both no-ops on fully source-space
     *  input). */
    override fun analyze(node: ClassNode) {
        if (mode == AnnotationApplyMode.REFMAP) return
        if (!table.mixinRemapEnabled(node)) return
        val targets = table.mixinTargets(node, ctx).map {
            ctx.reverse.map(xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName.unchecked(it)).value
        }
        if (targets.isEmpty()) return
        val list = mutableListOf<MemberRename>()
        // accessor/invoker member names are interface contracts: an
        // iface-bound impl must keep the declared name — the iface can
        // live outside the remapped set, and neither its decl nor every
        // callsite can be rewritten. Unbound members still rename to
        // `verb`+target so they self-resolve where no refmap is emitted
        // (jarmod-era runtimes). A non-Object superclass may carry the
        // iface, so treat it as bound too; an interface node's own decls
        // are bound by its implementors.
        val ifaceBound = node.access and Opcodes.ACC_INTERFACE != 0 ||
                node.interfaces.isNotEmpty() ||
                (node.superName != null && node.superName != "java/lang/Object")
        recorder?.currentMixin = ctx.mapClass(node.name)
        for (m in node.methods) {
            val anns = table.annotations(m)
            val suppressed = table.dontRemapSuppressed(anns)
            for (ann in anns) {
                if (ann in suppressed || !table.isRemapEnabled(ann)) continue
                if (table.isAccessor(ann) || table.isInvoker(ann)) {
                    val isField = table.isAccessor(ann)
                    val explicit = table.explicitValue(ann)
                    val derived = explicit?.let { null to it }
                        ?: table.deriveTarget(m.name, isField, table.declaredPrefixes(ann))
                        ?: continue
                    var done = false
                    for (t in targets) {
                        val mapped = if (isField) ctx.mapField(t, derived.second, null)
                                     else ctx.mapMethod(t, derived.second, ctx.reverseDesc(m.desc, false))
                        if (mapped != derived.second) {
                            recorder?.recordMemberRename(
                                derived.second, isField,
                                ResolvedRef(ctx.mapClass(t), mapped, ctx.resolveMemberDesc(t, derived.second, isField), isField, targets.size <= 1)
                            )
                            list += MemberRename(
                                m.name, ctx.mapDesc(m.desc, false),
                                if (explicit == null && !ifaceBound)
                                    derived.first + mapped.replaceFirstChar { it.uppercaseChar() }
                                else null,
                                false
                            )
                            done = true
                            break
                        }
                    }
                    if (!done) existingMember(derived.second, isField)?.let {
                        recorder?.recordMemberRename(derived.second, isField, it)
                        list += MemberRename(
                            m.name, ctx.mapDesc(m.desc, false),
                            if (explicit == null && !ifaceBound)
                                derived.first + it.name.replaceFirstChar { c -> c.uppercaseChar() }
                            else null,
                            false
                        )
                    }
                } else if (table.isShadow(ann)) {
                    rename@ for ((prefix, cand) in table.shadowCandidates(m.name, ann)) {
                        for (t in targets) {
                            val mapped = ctx.mapMethod(t, cand, ctx.reverseDesc(m.desc, false))
                            if (mapped != cand) {
                                recorder?.recordMemberRename(
                                    m.name, false,
                                    ResolvedRef(ctx.mapClass(t), mapped, ctx.mapDesc(m.desc, false), false, targets.size <= 1)
                                )
                                list += MemberRename(
                                    m.name, ctx.mapDesc(m.desc, false), prefix + mapped, false
                                )
                                break@rename
                            }
                        }
                        // resolution fallback: a stale input refmap may know this member
                        existingMember(cand, isField = false)?.let {
                            recorder?.recordMemberRename(m.name, false, it)
                            list += MemberRename(
                                m.name, ctx.mapDesc(m.desc, false), prefix + it.name, false
                            )
                            break@rename
                        }
                    }
                }
            }
        }
        for (f in node.fields) {
            val anns = table.annotations(f)
            val suppressed = table.dontRemapSuppressed(anns)
            for (ann in anns) {
                if (ann !in suppressed && table.isShadow(ann) && table.isRemapEnabled(ann)) {
                    rename@ for ((prefix, cand) in table.shadowCandidates(f.name, ann)) {
                        for (t in targets) {
                            val mapped = ctx.mapField(t, cand, ctx.reverseDesc(f.desc, true))
                            if (mapped != cand) {
                                recorder?.recordMemberRename(
                                    f.name, true,
                                    ResolvedRef(ctx.mapClass(t), mapped, ctx.mapDesc(f.desc, true), true, targets.size <= 1)
                                )
                                list += MemberRename(
                                    f.name, ctx.mapDesc(f.desc, true), prefix + mapped, true
                                )
                                break@rename
                            }
                        }
                        existingMember(cand, isField = true)?.let {
                            recorder?.recordMemberRename(f.name, true, it)
                            list += MemberRename(
                                f.name, ctx.mapDesc(f.desc, true), prefix + it.name, true
                            )
                            break@rename
                        }
                    }
                }
            }
        }
        recorder?.currentMixin = null
        if (list.isNotEmpty()) {
            memberRenames.getOrPut(ctx.mapClass(node.name)) { mutableListOf() } += list
        }
    }

    /** apply the analyze-time renames onto [output]'s member decls — member
     *  names of mixin classes aren't tree-mapped, so output names still
     *  equal their source names here */
    private fun applyDeclRenames(output: ClassNode) {
        val rs = memberRenames[output.name] ?: return
        for (r in rs) {
            val newName = r.newName ?: continue
            if (r.isField) {
                output.fields.firstOrNull { it.name == r.oldName && it.desc == r.targetDesc }
                    ?.name = newName
            } else {
                output.methods.firstOrNull { it.name == r.oldName && it.desc == r.targetDesc }
                    ?.name = newName
            }
        }
    }

    private fun fieldHandleTag(tag: Int) = tag in Opcodes.H_GETFIELD..Opcodes.H_PUTSTATIC

    /** rewrite references to renamed members — every class may carry them:
     *  the declaring mixin (handler bodies), nestmates, and non-mixin
     *  callers of renamed accessor methods */
    private fun rewriteRefs(output: ClassNode) {
        if (memberRenames.isEmpty()) return
        fun Handle.remapped(): Handle =
            memberRenames[owner]?.firstOrNull {
                it.newName != null && it.oldName == name && it.targetDesc == desc &&
                        (if (fieldHandleTag(tag)) it.isField else !it.isField)
            }?.let { Handle(tag, owner, it.newName!!, desc, isInterface) } ?: this
        for (m in output.methods) {
            for (insn in m.instructions) {
                when (insn) {
                    is FieldInsnNode -> memberRenames[insn.owner]
                        ?.firstOrNull { it.isField && it.newName != null && it.oldName == insn.name && it.targetDesc == insn.desc }
                        ?.let { insn.name = it.newName!! }
                    is MethodInsnNode -> memberRenames[insn.owner]
                        ?.firstOrNull { !it.isField && it.newName != null && it.oldName == insn.name && it.targetDesc == insn.desc }
                        ?.let { insn.name = it.newName!! }
                    is InvokeDynamicInsnNode -> {
                        insn.bsmArgs = insn.bsmArgs.map { if (it is Handle) it.remapped() else it }
                            .toTypedArray()
                    }
                    is LdcInsnNode -> (insn.cst as? Handle)?.let { insn.cst = it.remapped() }
                }
            }
        }
    }

    override fun postApply(input: ClassNode, output: ClassNode, cls: xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping?) {
        // ref rewrite is class-agnostic — runs even when this class isn't a
        // mixin (accessor consumers) or has remap=false on its marker
        rewriteRefs(output)
        if (!table.mixinRemapEnabled(output)) return
        recorder?.currentMixin = output.name
        if (mode == AnnotationApplyMode.REFMAP) {
            val clsAnns = table.annotations(output)
            val clsSup = table.dontRemapSuppressed(clsAnns)
            for (ann in clsAnns) if (ann !in clsSup) rewriteVerbatim(ann)
            for (m in output.methods) {
                val anns = table.annotations(m)
                val sup = table.dontRemapSuppressed(anns)
                for (ann in anns) if (ann !in sup) rewriteVerbatim(ann)
            }
            for (f in output.fields) {
                val anns = table.annotations(f)
                val sup = table.dontRemapSuppressed(anns)
                for (ann in anns) if (ann !in sup) rewriteVerbatim(ann)
            }
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
        applyDeclRenames(output)

        // annotation param rewrites — class-level first (targets feed recording)
        val clsAnns = table.annotations(output)
        val clsSup = table.dontRemapSuppressed(clsAnns)
        for (ann in clsAnns) if (ann !in clsSup) table.rewrite(ann, ctx, listOf(output.name), recorder)
        for (m in output.methods) {
            val anns = table.annotations(m)
            val sup = table.dontRemapSuppressed(anns)
            for (ann in anns) if (ann !in sup) table.rewrite(ann, ctx, targets, recorder)
        }
        for (f in output.fields) {
            val anns = table.annotations(f)
            val sup = table.dontRemapSuppressed(anns)
            for (ann in anns) if (ann !in sup) table.rewrite(ann, ctx, targets, recorder)
        }
        recorder?.currentMixin = null
    }
}
