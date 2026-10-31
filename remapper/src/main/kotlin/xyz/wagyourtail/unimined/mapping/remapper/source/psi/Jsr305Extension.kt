package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.JavaRecursiveElementVisitor
import org.jetbrains.kotlin.com.intellij.psi.PsiAnnotation
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.source.ChangeSet
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceFileView

/**
 * Rewrites JSR-305-era nullness annotations (type-use + declaration) to JetBrains
 * equivalents (`org.jetbrains.annotations.Nullable`/`NotNull`) —
 * e.g. checkerframework, jspecify and spotbugs annotations carried
 * by mapping-side or userdev-provided sources. Same-text-length
 * concerns don't apply: [ChangeSet] line deltas keep linemaps valid.
 *
 * [table] overrides/extends the default qualified-name mapping.
 */
class Jsr305Extension(
    private val table: Map<String, String> = DEFAULT_TABLE
) : SourceExtension {

    companion object {
        val DEFAULT_TABLE: Map<String, String> = mapOf(
            "org.checkerframework.checker.nullness.qual.Nullable" to "org.jetbrains.annotations.Nullable",
            "org.checkerframework.checker.nullness.qual.NonNull" to "org.jetbrains.annotations.NotNull",
            "org.checkerframework.checker.nullness.qual.NullableDecl" to "org.jetbrains.annotations.Nullable",
            "org.jspecify.annotations.Nullable" to "org.jetbrains.annotations.Nullable",
            "org.jspecify.annotations.NonNull" to "org.jetbrains.annotations.NotNull",
            "javax.annotation.Nullable" to "org.jetbrains.annotations.Nullable",
            "javax.annotation.Nonnull" to "org.jetbrains.annotations.NotNull",
            "javax.annotation.CheckForNull" to "org.jetbrains.annotations.Nullable",
            "edu.umd.cs.findbugs.annotations.Nullable" to "org.jetbrains.annotations.Nullable",
            "edu.umd.cs.findbugs.annotations.NonNull" to "org.jetbrains.annotations.NotNull",
        )
    }

    override fun transform(
        file: SourceFileView, ctx: ExtensionContext,
        changes: ChangeSet, errors: MutableList<Pair<Int, String>>
    ) {
        val psiFile = (file as? PsiFileView)?.file ?: return
        psiFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitAnnotation(annotation: PsiAnnotation) {
                super.visitAnnotation(annotation)
                val qName = annotation.qualifiedName ?: return
                val replacement = table[qName] ?: return
                val ref = annotation.nameReferenceElement ?: return
                // fully-qualified replacement — a same-line import fix
                // isn't required since the ref itself is replaced
                changes.replace(ref.textRange.startOffset, ref.textRange.endOffset, replacement)
            }
        })
    }

}
