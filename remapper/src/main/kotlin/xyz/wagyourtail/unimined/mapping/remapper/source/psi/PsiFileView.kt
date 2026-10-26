@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceFileView

/** a [SourceFileView] carrying the parsed PSI file — PSI extensions check
 *  for this. [bindingContext] resolves Kotlin annotations/references when
 *  the file is a [org.jetbrains.kotlin.psi.KtFile]. */
class PsiFileView(
    val file: PsiFile,
    val bindingContext: org.jetbrains.kotlin.resolve.BindingContext? = null,
) : SourceFileView {
    override val text get() = file.text
}
