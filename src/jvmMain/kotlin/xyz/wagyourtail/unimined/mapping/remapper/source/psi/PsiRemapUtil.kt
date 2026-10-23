@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiClass
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction

/** internal PSI helpers shared by source-side extensions */

internal fun PsiElement.indent(): String {
    val text = containingFile.text
    var i = textRange.startOffset - 1
    while (i >= 0 && (text[i] == ' ' || text[i] == '\t')) i--
    return text.substring(i + 1, textRange.startOffset)
}

/** `a.b.C.Inner` → `a.b.C$Inner` (binary name, dotted) */
internal fun dollarName(c: PsiClass): String? {
    val parent = PsiTreeUtil.getParentOfType(c, PsiClass::class.java) ?: return c.qualifiedName
    return "${dollarName(parent) ?: return c.qualifiedName}$${'$'}${c.name ?: return c.qualifiedName}"
}

/** `a.b.C.Inner` → `a.b.C$Inner` for a Kotlin declaration */
internal fun dollarName(c: KtClassOrObject): String? =
    c.fqName?.asString()

internal fun KtNamedFunction.lightMethod(): PsiMethod? =
    org.jetbrains.kotlin.asJava.LightClassUtil.getLightClassMethod(this)
