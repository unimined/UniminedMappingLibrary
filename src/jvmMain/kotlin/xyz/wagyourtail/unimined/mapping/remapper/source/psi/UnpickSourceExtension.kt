@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.JavaRecursiveElementVisitor
import org.jetbrains.kotlin.com.intellij.psi.PsiExpressionList
import org.jetbrains.kotlin.com.intellij.psi.PsiLiteralExpression
import org.jetbrains.kotlin.com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.com.intellij.psi.PsiMethodCallExpression
import org.jetbrains.kotlin.com.intellij.psi.util.ClassUtil
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.ConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.source.ChangeSet
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceFileView

/**
 * Unpick — the source half of constant uninlining (`ConstantGroupMapping`).
 *
 * Replaces literal values in source with the group's [ExpressionElement]
 * expression (e.g. `3` → `a.b.Flags.FLAG_A | a.b.Flags.FLAG_B`), subject to
 * [TargetElement] scoping: a method target applies only to literals passed
 * as arguments to that method call (optionally just argument [paramIdx]);
 * a null target applies globally; a class target applies inside that class.
 *
 * Expressions reference fields in `La/b/Cls;name` / `this.name` form —
 * emitted to source as `a.b.Cls.name` / `name`.
 *
 * [ns] is the namespace the constant values/owners are written in (defaults
 * to the context's `fns` — unpick groups describe obf-space constants that
 * decompiled sources replaced with named refs).
 */
class UnpickSourceExtension(private val ns: Namespace? = null) : SourceExtension {

    override fun transform(file: SourceFileView, ctx: ExtensionContext, changes: ChangeSet, errors: MutableList<Pair<Int, String>>) {
        if (file !is PsiFileView) return
        val ns = ns ?: ctx.fns
        val groups = ctx.tree.constantGroups.filter { it.baseNs == ns }
        if (groups.isEmpty()) return

        file.file.accept(object : JavaRecursiveElementVisitor() {
            override fun visitLiteralExpression(lit: PsiLiteralExpression) {
                if (!changes.valid(lit.textRange.startOffset, lit.textRange.endOffset)) {
                    super.visitLiteralExpression(lit)
                    return
                }
                val text = lit.text.replace(Regex("[lLfFdD]+$"), "")
                for (group in groups) {
                    val expr = group.expressions.firstOrNull {
                        it.value.value == text || it.value.value == lit.text
                    } ?: continue
                    if (!inScope(group, lit, errors)) continue
                    changes.replace(
                        lit.textRange.startOffset, lit.textRange.endOffset,
                        sourceForm(expr.expression.toString(), group)
                    )
                    break
                }
                super.visitLiteralExpression(lit)
            }
        })

        // kotlin literals — KtConstantExpression / KtValueArgument scope
        val ktFile = file.file as? org.jetbrains.kotlin.psi.KtFile ?: return
        ktFile.accept(object : org.jetbrains.kotlin.psi.KtTreeVisitorVoid() {
            override fun visitConstantExpression(lit: org.jetbrains.kotlin.psi.KtConstantExpression) {
                if (!changes.valid(lit.textRange.startOffset, lit.textRange.endOffset)) {
                    super.visitConstantExpression(lit)
                    return
                }
                val text = lit.text.replace(Regex("[lLfFdD]+$"), "")
                for (group in groups) {
                    val expr = group.expressions.firstOrNull {
                        it.value.value == text || it.value.value == lit.text
                    } ?: continue
                    if (!ktInScope(group, lit)) continue
                    changes.replace(
                        lit.textRange.startOffset, lit.textRange.endOffset,
                        sourceForm(expr.expression.toString(), group)
                    )
                    break
                }
                super.visitConstantExpression(lit)
            }
        })
    }

    /** does [lit] sit in this group's target scope? */
    private fun inScope(
        group: ConstantGroupMapping,
        lit: PsiLiteralExpression,
        errors: MutableList<Pair<Int, String>>,
    ): Boolean {
        val targets = group.targets.toList()
        if (targets.isEmpty() || targets.any { it.target == null }) return true
        for (target in targets) {
            val t = target.target ?: continue
            val fq = t.value
            val owner = fq.substringAfter('L').substringBefore(';')
            val member = fq.substringAfter(';', "").takeIf { it.isNotEmpty() }
            if (member == null) {
                // class-level target — literal anywhere inside that class
                val cls = lit.takeIf { it.text.isNotEmpty() }?.let {
                    org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                        lit, org.jetbrains.kotlin.com.intellij.psi.PsiClass::class.java)
                }
                if (cls?.let { dollarName(it)?.replace('.', '/') } == owner) return true
            } else {
                // method target — literal must be an argument of a call to it
                val argList = lit.parent as? PsiExpressionList ?: continue
                val call = argList.parent as? PsiMethodCallExpression ?: continue
                val method = call.resolveMethod() ?: continue
                val mOwner = method.containingClass?.let { dollarName(it)?.replace('.', '/') } ?: continue
                if (mOwner != owner) continue
                val mDesc = runCatching { ClassUtil.getAsmMethodSignature(method) }.getOrNull() ?: continue
                if (member != "${method.name}$mDesc" && member != method.name) continue
                if (target.paramIdx != null) {
                    val idx = argList.expressions.indexOfFirst { it == lit }
                    if (idx != target.paramIdx) continue
                }
                return true
            }
        }
        return false
    }

    /** kt scope: method target → literal inside `KtValueArgument` of a call to
     *  that method; class target → inside that class; null → global */
    private fun ktInScope(group: ConstantGroupMapping, lit: org.jetbrains.kotlin.psi.KtConstantExpression): Boolean {
        val targets = group.targets.toList()
        if (targets.isEmpty() || targets.any { it.target == null }) return true
        for (target in targets) {
            val t = target.target ?: continue
            val owner = t.value.substringAfter('L').substringBefore(';')
            val member = t.value.substringAfter(';', "").takeIf { it.isNotEmpty() }
            if (member == null) {
                // class-level — enclosing KtClass fq name
                val cls = org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                    lit, org.jetbrains.kotlin.psi.KtClassOrObject::class.java)
                if (cls?.fqName?.asString()?.replace('.', '/') == owner) return true
            } else {
                val arg = org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                    lit, org.jetbrains.kotlin.psi.KtValueArgument::class.java) ?: continue
                val call = org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                    arg, org.jetbrains.kotlin.psi.KtCallExpression::class.java) ?: continue
                val methodName = member.substringBefore('(')
                if (call.calleeExpression?.text != methodName) continue
                if (target.paramIdx != null &&
                    call.valueArguments.indexOf(arg) != target.paramIdx) continue
                return true
            }
        }
        return false
    }

    /** expression text → source form — `Lowner;name;desc` → `owner.name`,
     *  `this.name` → `owner.name` (resolved via the group's constants) */
    private fun sourceForm(expr: String, group: ConstantGroupMapping): String {
        val owners = group.constants.associate {
            val (name) = it.field.getParts()
            name.value to it.owner.value.replace('/', '.')
        }
        return Regex("L([a-zA-Z0-9_/${'$'}]+);([a-zA-Z0-9_${'$'}]+)(;[^ \\t|&()+\\-*/<>=!?:]*)?")
            .replace(expr) { "${it.groupValues[1].replace('/', '.')}.${it.groupValues[2]}" }
            .replace(Regex("this\\.([a-zA-Z0-9_${'$'}]+)(;[^ \\t|&()+\\-*/<>=!?:]*)?")) {
                val name = it.groupValues[1]
                (owners[name]?.let { o -> "$o." } ?: "") + name
            }
    }
}
