@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.JavaRecursiveElementVisitor
import org.jetbrains.kotlin.com.intellij.psi.PsiClass
import org.jetbrains.kotlin.com.intellij.psi.PsiField
import org.jetbrains.kotlin.com.intellij.psi.PsiKeyword
import org.jetbrains.kotlin.com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.com.intellij.psi.PsiModifierList
import org.jetbrains.kotlin.com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.com.intellij.psi.util.ClassUtil
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.element.metadata.MetadataParent
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.source.ChangeSet
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceFileView

/**
 * Source-level access widener — applies [AccessElement]s to modifier lists
 * in source text (`private` → `public`, add/remove `final`, …). The source
 * half of the AW story: bytecode AW runs through [MetadataAdapter], this
 * keeps decompiled sources in sync.
 *
 * [ns] is the namespace the source text is written in (defaults to targetNs).
 */
class AccessSourceExtension(private val ns: Namespace? = null) : SourceExtension {

    private val keywords = mapOf(
        AccessFlag.PUBLIC to "public",
        AccessFlag.PRIVATE to "private",
        AccessFlag.PROTECTED to "protected",
        AccessFlag.FINAL to "final",
        AccessFlag.ABSTRACT to "abstract",
        AccessFlag.STATIC to "static",
        AccessFlag.SYNCHRONIZED to "synchronized",
        AccessFlag.NATIVE to "native",
        AccessFlag.TRANSIENT to "transient",
        AccessFlag.VOLATILE to "volatile",
        AccessFlag.STRICT to "strictfp",
    )

    /** PSI keywords → the current flag set */
    private fun flagsOf(list: PsiModifierList?): MutableSet<AccessFlag> {
        val flags = keywords.filterValues { list?.hasExplicitModifier(it) == true }.keys.toMutableSet()
        if (AccessFlag.PRIVATE !in flags && AccessFlag.PROTECTED !in flags && AccessFlag.PUBLIC !in flags) {
            flags += AccessFlag.DEFAULT
        }
        return flags
    }

    /** apply [elements] to [list]'s keywords in the file text */
    private fun applyTo(list: PsiModifierList?, elements: List<AccessElement>, anchor: PsiModifierListOwner, changes: ChangeSet) {
        if (elements.isEmpty()) return
        val flags = flagsOf(list)
        elements.forEach { it.apply(flags) }
        val current = flagsOf(list)

        // removals — delete keyword tokens + trailing whitespace for flags that dropped
        for ((flag, kw) in keywords) {
            if (flag in current && flag !in flags) {
                list?.children?.filterIsInstance<PsiKeyword>()
                    ?.firstOrNull { it.text == kw }
                    ?.let { kw ->
                        var end = kw.textRange.endOffset
                        val text = anchor.containingFile.text
                        while (end < text.length && text[end].isWhitespace() && text[end] != '\n') end++
                        changes.replace(kw.textRange.startOffset, end, "")
                    }
            }
        }
        // additions — insert missing keywords at the start of the modifier list
        val adds = keywords.filter { it.key in flags && it.key !in current }.map { it.value }
        if (adds.isNotEmpty()) {
            val pos = list?.textRange?.startOffset ?: anchor.textRange.startOffset
            changes.insert(pos, adds.joinToString(" ", postfix = " "))
        }
    }

    private fun elementsOf(mapping: MetadataParent?, type: ElementType): List<AccessElement> =
        mapping?.metadata?.filterIsInstance<AccessElement>()?.filter { it.value.elements.contains(type) }.orEmpty()

    override fun transform(file: SourceFileView, ctx: ExtensionContext, changes: ChangeSet, errors: MutableList<Pair<Int, String>>) {
        if (file !is PsiFileView) return
        val ns = ns ?: ctx.targetNs
        fun internalOf(fq: String) = fq.replace('.', '/')
        fun clsMapping(c: PsiClass): ClassMapping? =
            ctx.tree.getClass(ns, InternalName.unchecked(internalOf(dollarName(c) ?: return null)))

        file.file.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(c: PsiClass) {
                applyTo(c.modifierList, elementsOf(clsMapping(c), ElementType.CLASS), c, changes)
                super.visitClass(c)
            }
            override fun visitMethod(m: PsiMethod) {
                val cls = m.containingClass?.let(::clsMapping) ?: return super.visitMethod(m)
                val desc = runCatching { ClassUtil.getAsmMethodSignature(m) }.getOrNull()
                val row = with(ctx.tree) {
                    cls.getMethod(ns, UnqualifiedName.unchecked(m.name),
                        desc?.let { MethodDescriptor.unchecked(it) })
                }
                applyTo(m.modifierList, elementsOf(row, ElementType.METHOD), m, changes)
                super.visitMethod(m)
            }
            override fun visitField(f: PsiField) {
                val cls = f.containingClass?.let(::clsMapping) ?: return super.visitField(f)
                val row = with(ctx.tree) {
                    cls.getField(ns, UnqualifiedName.unchecked(f.name), null)
                }
                        applyTo(f.modifierList, elementsOf(row, ElementType.FIELD), f, changes)
                super.visitField(f)
            }
        })

        val ktFile = file.file as? org.jetbrains.kotlin.psi.KtFile ?: return
        ktFile.accept(object : org.jetbrains.kotlin.psi.KtTreeVisitorVoid() {
            private fun ktFlags(list: org.jetbrains.kotlin.psi.KtModifierList?): MutableSet<AccessFlag> {
                val flags = mutableSetOf<AccessFlag>()
                val mods = list?.children?.map { it.text } ?: emptyList()
                if ("public" in mods) flags += AccessFlag.PUBLIC
                if ("private" in mods) flags += AccessFlag.PRIVATE
                if ("protected" in mods) flags += AccessFlag.PROTECTED
                // kotlin `open` = non-final; no keyword = final for classes
                if ("final" !in mods && "open" !in mods) flags += AccessFlag.FINAL
                if ("abstract" in mods) flags += AccessFlag.ABSTRACT
                if ("static" in mods || "const" in mods) flags += AccessFlag.STATIC
                if ("internal" in mods) flags += AccessFlag.PUBLIC // jvm-visible
                if (AccessFlag.PRIVATE !in flags && AccessFlag.PROTECTED !in flags) flags += AccessFlag.PUBLIC
                return flags
            }

            private fun ktApply(list: org.jetbrains.kotlin.psi.KtModifierList?,
                                elements: List<AccessElement>,
                                anchor: PsiModifierListOwner, changes: ChangeSet) {
                if (elements.isEmpty()) return
                val current = ktFlags(list)
                val flags = current.toMutableSet()
                elements.forEach { it.apply(flags) }
                val kwMap = mapOf(
                    AccessFlag.PUBLIC to "public", AccessFlag.PRIVATE to "private",
                    AccessFlag.PROTECTED to "protected", AccessFlag.ABSTRACT to "abstract",
                    AccessFlag.STATIC to "static",
                )
                for ((flag, kw) in kwMap) {
                    if (flag in current && flag !in flags) {
                        list?.children?.firstOrNull { it.text == kw }?.let { kwNode ->
                            var end = kwNode.textRange.endOffset
                            val text = anchor.containingFile.text
                            while (end < text.length && text[end].isWhitespace() && text[end] != '\n') end++
                            changes.replace(kwNode.textRange.startOffset, end, "")
                        }
                    }
                }
                // FINAL add → drop `open`; FINAL remove → add `open` (kotlin inverse)
                if (AccessFlag.FINAL in flags && AccessFlag.FINAL !in current) {
                    list?.children?.firstOrNull { it.text == "open" }?.let { open ->
                        var end = open.textRange.endOffset
                        val text = anchor.containingFile.text
                        while (end < text.length && text[end].isWhitespace() && text[end] != '\n') end++
                        changes.replace(open.textRange.startOffset, end, "")
                    }
                }
                val adds = kwMap.filter { it.key in flags && it.key !in current }.map { it.value }.toMutableList()
                if (AccessFlag.FINAL !in flags && AccessFlag.FINAL in current) adds += "open"
                // kotlin's default visibility is public — don't re-add `public`
                adds.remove("public")
                if (adds.isNotEmpty()) {
                    val pos = list?.textRange?.startOffset ?: anchor.textRange.startOffset
                    changes.insert(pos, adds.joinToString(" ", postfix = " "))
                }
            }

            override fun visitClassOrObject(cls: org.jetbrains.kotlin.psi.KtClassOrObject) {
                ktApply(cls.modifierList, elementsOf(
                    cls.fqName?.asString()?.let { ctx.tree.getClass(ns, InternalName.unchecked(it.replace('.', '/'))) },
                    ElementType.CLASS
                ), cls as PsiModifierListOwner, changes)
                super.visitClassOrObject(cls)
            }
        })
    }
}
