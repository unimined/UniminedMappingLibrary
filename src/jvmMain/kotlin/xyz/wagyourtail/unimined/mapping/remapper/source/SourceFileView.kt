package xyz.wagyourtail.unimined.mapping.remapper.source

/**
 * A parsed source file, abstracted so [SourceExtension]s can (in principle)
 * run over different engines — PSI-backed extensions check for
 * `source.psi.PsiFileView`.
 */
interface SourceFileView {
    val text: String
}
