@file:Suppress("DEPRECATION", "DEPRECATION_ERROR")

package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.psi.JavaRecursiveElementVisitor
import org.jetbrains.kotlin.com.intellij.psi.PsiClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.InterfaceElement
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.source.ChangeSet
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceFileView

/** inject `implements`/`extends` entries for [InterfaceElement] `ADD` rows —
 *  the source-level half of interface injection (loom's JST `inject` stage).
 *  [ns] is the namespace the source text is written in (defaults to targetNs). */
class InterfaceInjectionExtension(private val ns: Namespace? = null) : SourceExtension {

    private fun injectedIfaces(cls: ClassMapping, ns: Namespace): List<String> =
        cls.metadata.filterIsInstance<InterfaceElement>()
            .filter { it.baseNs == ns && it.type == AddRemove.ADD }
            .map { it.name.toString().removePrefix("L").removeSuffix(";").replace('/', '.') }

    private fun internalOf(fq: String) = fq.replace('.', '/')

    override fun transform(file: SourceFileView, ctx: ExtensionContext, changes: ChangeSet, errors: MutableList<Pair<Int, String>>) {
        if (file !is PsiFileView) return
        val ns = ns ?: ctx.targetNs
        file.file.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(c: PsiClass) {
                val ifaces = ctx.tree.getClass(ns, InternalName.unchecked(internalOf(dollarName(c) ?: return)))
                    ?.let { injectedIfaces(it, ns) }.orEmpty()
                if (ifaces.isNotEmpty()) inject(c, ifaces.joinToString(", "), changes)
                super.visitClass(c)
            }
        })
        file.file.accept(object : org.jetbrains.kotlin.psi.KtTreeVisitorVoid() {
            override fun visitClassOrObject(c: KtClassOrObject) {
                val fq = c.fqName?.asString() ?: return super.visitClassOrObject(c)
                val ifaces = ctx.tree.getClass(ns, InternalName.unchecked(internalOf(fq)))
                    ?.let { injectedIfaces(it, ns) }.orEmpty()
                if (ifaces.isNotEmpty()) {
                    val superList = c.getSuperTypeList()
                    if (superList != null) {
                        changes.insert(superList.textRange.endOffset, ", ${ifaces.joinToString(", ")}")
                    } else c.getBody()?.lBrace?.let {
                        changes.insert(it.startOffset, " : ${ifaces.joinToString(", ")}")
                    }
                }
                super.visitClassOrObject(c)
            }
        })
    }

    private fun inject(c: PsiClass, ifaceList: String, changes: ChangeSet) {
        if (c.isInterface) {
            val extends = c.extendsList
            val last = extends?.referenceElements?.lastOrNull()
            if (last != null) { changes.insert(last.textRange.endOffset, ", $ifaceList"); return }
            c.lBrace?.let { changes.insert(it.startOffset, " extends $ifaceList") }
            return
        }
        val impl = c.implementsList
        val last = impl?.referenceElements?.lastOrNull()
        if (last != null) { changes.insert(last.textRange.endOffset, ", $ifaceList"); return }
        c.lBrace?.let { changes.insert(it.startOffset, " implements $ifaceList") }
    }
}
