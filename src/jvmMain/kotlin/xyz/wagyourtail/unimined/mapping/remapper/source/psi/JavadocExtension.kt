@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.*
import org.jetbrains.kotlin.com.intellij.psi.util.ClassUtil
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.source.ChangeSet
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceFileView

/** insert javadoc comments for [JavadocElement] rows on classes, methods and
 *  fields — yarn-style docs into decompiled sources. Written inline
 *  inline — no newline — so line numbers never shift and linemaps stay valid.
 *  [ns] defaults to targetNs (path-2 sources are already target-space). */
class JavadocExtension(private val ns: Namespace? = null) : SourceExtension {

    private fun insertDoc(e: PsiElement, docs: List<String>, changes: ChangeSet) {
        // inline form — inserting a newline would shift line numbers and
        // force linemap adjustment downstream
        for (doc in docs) {
            changes.insert(e.textRange.startOffset, "/** $doc */ ")
        }
    }

    /** all docs, ref-remapped into the view namespace — docs authored in a
     *  different baseNs still insert, their links rewritten baseNs → ns */
    private fun docsOf(mapping: MetadataParent, ns: Namespace, ctx: ExtensionContext): List<String> =
        mapping.metadata.filterIsInstance<JavadocElement>().map { doc ->
            doc.value.remap { ref -> remapRef(ref, doc.baseNs, ns, ctx) }.toString()
        }

    private fun remapRef(
        ref: xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.DocRef,
        from: Namespace, to: Namespace, ctx: ExtensionContext,
    ): xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.DocRef {
        // ctx only covers fns → targetNs — docs bound to a third namespace
        // can't ref-remap through it, so they pass through verbatim
        if (from != ctx.fns || to != ctx.targetNs) return ref
        val owner = ref.owner?.let {
            InternalName.unchecked(ctx.mapClass(it.value))
        } ?: return ref
        val member = ref.member ?: return ref.copy(owner = owner)
        val mapped = if (member.params != null) {
            ctx.mapMethod(owner.value, member.name.value, null)
        } else {
            ctx.mapField(owner.value, member.name.value, null)
                .takeIf { it != member.name.value }
                ?: ctx.mapMethod(owner.value, member.name.value, null)
        }
        return ref.copy(owner = owner, member = member.copy(
            name = xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked(mapped)))
    }

    override fun transform(file: SourceFileView, ctx: ExtensionContext, changes: ChangeSet, errors: MutableList<Pair<Int, String>>) {
        if (file !is PsiFileView) return
        val ns = ns ?: ctx.targetNs
        fun internalOf(fq: String) = fq.replace('.', '/')
        fun clsMapping(c: PsiClass): ClassMapping? =
            ctx.tree.getClass(ns, InternalName.unchecked(internalOf(dollarName(c) ?: return null)))

        file.file.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(c: PsiClass) {
                clsMapping(c)?.let { insertDoc(c, docsOf(it, ns, ctx), changes) }
                super.visitClass(c)
            }
            override fun visitMethod(m: PsiMethod) {
                val cls = m.containingClass?.let(::clsMapping) ?: return
                val desc = runCatching { ClassUtil.getAsmMethodSignature(m) }.getOrNull()
                with(ctx.tree) {
                    cls.getMethod(ns, UnqualifiedName.unchecked(m.name),
                        desc?.let { MethodDescriptor.unchecked(it) })
                }?.let { insertDoc(m, docsOf(it, ns, ctx), changes) }
                super.visitMethod(m)
            }
            override fun visitField(f: PsiField) {
                val cls = f.containingClass?.let(::clsMapping) ?: return
                with(ctx.tree) {
                    cls.getField(ns, UnqualifiedName.unchecked(f.name), null)
                }?.let { insertDoc(f, docsOf(it, ns, ctx), changes) }
                super.visitField(f)
            }
        })
    }
}
