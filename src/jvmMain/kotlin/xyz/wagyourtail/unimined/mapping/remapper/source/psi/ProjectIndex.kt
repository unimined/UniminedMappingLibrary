package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiJavaFile
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace

/**
 * Project class index — the fallback for references PSI can't resolve
 * because the class is missing from the engine classpath.
 *
 * Two sources of truth:
 * - the mapping tree's [fns] class names — every class the mappings know
 *   (project + deps), whether or not it's on the classpath
 * - the source jar's own parsed top-level declarations — catches classes the
 *   mappings don't describe
 */
class ProjectIndex(
    /** simple class name → candidate internal names */
    val bySimpleName: Map<String, List<String>>,
    /** every internal name — fully-qualified lookups */
    val internals: Set<String>,
) {

    companion object {
        fun build(parsed: Map<String, PsiFile>, tree: MappingTree, fns: Namespace): ProjectIndex {
            val bySimpleName = mutableMapOf<String, MutableList<String>>()
            val internals = mutableSetOf<String>()

            fun add(internal: String) {
                if (!internals.add(internal)) return
                bySimpleName.getOrPut(internal.substringAfterLast('/').substringAfterLast('$')) { mutableListOf() } += internal
            }

            for (cls in tree.classes) {
                cls.names[fns]?.let { add(it.value) }
            }
            for ((_, file) in parsed) {
                val pkg = when (file) {
                    is PsiJavaFile -> file.packageName
                    is KtFile -> file.packageFqName.asString()
                    else -> continue
                }
                val names = mutableListOf<String>()
                if (file is PsiJavaFile) file.classes.mapNotNullTo(names) { it.name }
                if (file is KtFile) file.declarations.filterIsInstance<KtClassOrObject>().mapNotNullTo(names) { it.name }
                for (name in names) {
                    add(if (pkg.isEmpty()) name else "${pkg.replace('.', '/')}/$name")
                }
            }
            return ProjectIndex(bySimpleName, internals)
        }
    }

    /** the internal name a source ref points at, or null — fq text matches
     *  [internals] directly; bare names need a unique candidate (same-package
     *  preferred when ambiguous) */
    fun resolve(text: String, filePkgInternal: String): String? = when {
        '.' in text || '/' in text -> text.replace('.', '/').takeIf { it in internals }
        else -> bySimpleName[text]?.let { cands ->
            cands.singleOrNull() ?: cands.filter {
                it.substringBeforeLast('/') == filePkgInternal
            }.singleOrNull()
        }
    }

}
