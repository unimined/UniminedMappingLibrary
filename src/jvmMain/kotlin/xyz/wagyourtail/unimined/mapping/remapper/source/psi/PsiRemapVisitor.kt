package xyz.wagyourtail.unimined.mapping.remapper.source.psi

import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.psi.*
import org.jetbrains.kotlin.com.intellij.psi.util.ClassUtil
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.propertyNameByGetMethodName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext

/**
 * The remap visitor — walks a parsed file collecting `(TextRange → newText)`
 * edits applied back-to-front so positions stay valid. Formatting is
 * preserved: only the renamed identifier tokens (or changed literals) are
 * replaced.
 *
 * Resolution is PSI-native: `PsiJavaCodeReferenceElement.resolve()` and
 * kotlin `BindingContext.REFERENCE_TARGET` hand us the resolved member; the
 * member's *declaring class + name + desc* then looks up the mapping tree —
 * same semantics as the bytecode remapper's annotation table (member renames
 * resolve through [ExtensionContext]).
 */
class PsiRemapVisitor(
    private val file: PsiFile,
    private val ctx: ExtensionContext,
    private val bindingContext: BindingContext,
    private val extensions: List<xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension> = emptyList(),
) {
    private val changeSet = xyz.wagyourtail.unimined.mapping.remapper.source.ChangeSet()
    private val errors = mutableListOf<Pair<Int, String>>()
    private val ambiguousImports = mutableMapOf<String, MutableSet<String>>()

    /** the file's mapped package (dotted) — first mapped top-level class wins;
     *  `""` when the primary class maps to the default package */
    var mappedPackage: String? = null
        private set
    private val ambiguousImportStatics = mutableMapOf<String, MutableSet<String>>()
    private val aliased = mutableSetOf<String>()

    private fun error(at: PsiElement, message: String) {
        errors += StringUtil.offsetToLineNumber(file.text, at.textOffset) to message
    }

    private fun replace(e: PsiElement, with: String) = replace(e.textRange, with)
    private fun replace(range: TextRange, with: String) = replace(range) { with }
    private fun replace(e: PsiElement, with: () -> String) = replace(e.textRange, with)
    private fun replace(range: TextRange, with: () -> String) {
        changeSet.replace(range.startOffset, range.endOffset, with())
    }

    /** replace the identifier leaf under [parent] (PsiIdentifier or kt IDENTIFIER) */
    private fun replaceIdentifier(parent: PsiElement, with: String) {
        var child = parent.firstChild
        while (child != null) {
            if (child is PsiIdentifier || (child is ASTNode && child.elementType == KtTokens.IDENTIFIER)) {
                replace(child, with)
                return
            }
            child = child.nextSibling
        }
    }

    /** an edit may not overlap an already-queued one */
    private fun valid(e: PsiElement): Boolean = valid(e.textRange)
    private fun valid(range: TextRange): Boolean =
        changeSet.valid(range.startOffset, range.endOffset)

    /** KDoc `[a.b.Cls]`/`[a.b.Cls#m]` — `linkTextRange` is the ref inside
     *  the brackets; remap via the DocRef grammar */
    private fun remapKdocLink(link: PsiElement) {
        val text = link.text
        val ref = runCatching {
            xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.DocRef.unchecked(text)
        }.getOrNull() ?: return
        val owner = ref.owner ?: return
        val mappedOwner = ctx.mapClass(owner.value)
        val member = ref.member
        val mappedMember = member?.let { m ->
            val n = if (m.params != null) ctx.mapMethod(mappedOwner, m.name.value, null)
            else ctx.mapField(mappedOwner, m.name.value, null)
                .takeIf { it != m.name.value } ?: ctx.mapMethod(mappedOwner, m.name.value, null)
            m.copy(name = xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName.unchecked(n))
        }
        val out = xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.DocRef(
            xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName.unchecked(mappedOwner),
            mappedMember,
        ).toString()
        if (out != text) replace(link.textRange, "[$out]")
    }

    /** kotlin callable declaration (property / ctor val|var param) →
     *  mapped name: the backing field's mapping first, else the getter
     *  mapping converted back to property form */
    private fun mapKtCallable(id: PsiElement, decl: KtCallableDeclaration) {
        val field = org.jetbrains.kotlin.asJava.LightClassUtil.getLightClassBackingField(decl)
        val mapped = field?.let(::mapFieldName)
            ?: runCatching {
                org.jetbrains.kotlin.asJava.LightClassUtil.getLightClassPropertyMethods(decl as KtParameter).getter
            }.getOrNull()?.let(::mapMethodName)
                ?.let { propertyNameByGetMethodName(Name.identifier(it))?.identifier }
        if (mapped != null) replaceIdentifier(id, mapped)
    }

    /** apply collected edits back-to-front */
    private fun result(): String = changeSet.apply(file.text)

    /** per-line net deltas from the collected edits — `(line, inserted)` */
    fun lineDeltas(): List<Pair<Int, Int>> = changeSet.lineDeltas(file.text)

    // ---------------- member lookups ----------------

    private fun internalName(psiClass: PsiClass): String? {
        val q = dollarQualifiedName(psiClass) ?: return null
        return q.replace('.', '/')
    }

    /** `a.b.C.Inner` → `a.b.C$Inner` */
    private fun dollarQualifiedName(c: PsiClass): String? {
        val parent = PsiTreeUtil.getParentOfType(c, PsiClass::class.java) ?: return c.qualifiedName
        val parentName = dollarQualifiedName(parent) ?: return c.qualifiedName
        return "$parentName$${'$'}${c.name ?: return c.qualifiedName}"
    }

    /** map a method: exact desc first, then desc-less, then walk supers —
     *  mirrors the bytecode remapper's `mapMethod` + propagation reach */
    private fun mapMethodName(method: PsiMethod): String? {
        val desc = runCatching { ClassUtil.getAsmMethodSignature(method) }.getOrNull()
        val chain = sequenceOf(method) + method.findAllSuperMethods().asSequence()
        for (m in chain) {
            val owner = m.containingClass?.let(::internalName) ?: continue
            val d = runCatching { ClassUtil.getAsmMethodSignature(m) }.getOrNull()
            val mapped = ctx.mapMethod(owner, m.name, d ?: desc)
            if (mapped != m.name || (d == null && ctx.mapMethod(owner, m.name, null) != m.name)) {
                return if (m.name == method.name) mapped else ctx.mapMethod(owner, method.name, desc)
            }
        }
        // declaring-class direct lookup (no supers found)
        val owner = method.containingClass?.let(::internalName) ?: return null
        val mapped = ctx.mapMethod(owner, method.name, desc)
        return mapped.takeIf { it != method.name }
    }

    private fun mapFieldName(field: PsiField): String? {
        val owner = field.containingClass?.let(::internalName) ?: return null
        val desc = runCatching { ClassUtil.getBinaryPresentation(field.type) }.getOrNull()
        return ctx.mapField(owner, field.name, desc).takeIf { it != field.name }
    }

    // ---------------- element mapping ----------------

    private fun mapMemberExpr(expr: PsiElement, method: PsiMethod) {
        if (method.isConstructor) {
            if (expr is KtSimpleNameExpression) method.containingClass?.let { mapClassExpr(expr, it) }
            return
        }
        val mapped = mapMethodName(method) ?: return
        if (method.hasModifierProperty(PsiModifier.STATIC)) {
            method.containingClass?.qualifiedName?.let {
                ambiguousImportStatics.getOrPut(it) { mutableSetOf() } += mapped
            }
        }
        val getterProp = propertyNameByGetMethodName(Name.identifier(mapped))
        if (getterProp != null && !method.hasParameters()
            && method.returnType != null && method.returnType != PsiTypes.voidType()
            && !method.hasModifierProperty(PsiModifier.STATIC)
            && expr.containingFile is KtFile
            && expr.parent.parent.let { it !is KtDotQualifiedExpression || it.firstChild !is KtSuperExpression }
        ) {
            // java getter called from kotlin — mapped name isn't getter-shaped,
            // expose it as the synthetic property
            if (!expr.text.contains('.')) replace(expr.parent, getterProp.identifier)
            else replaceIdentifier(expr, getterProp.identifier)
            return
        }
        replaceIdentifier(expr, mapped)
    }

    private fun mapMemberExpr(expr: PsiElement, field: PsiField) {
        val mapped = mapFieldName(field) ?: return
        if (field.hasModifierProperty(PsiModifier.STATIC)) {
            field.containingClass?.qualifiedName?.let {
                ambiguousImportStatics.getOrPut(it) { mutableSetOf() } += mapped
            }
        }
        if (!expr.isQualifiedRef() && field.containingClass != null) {
            // implicit member ref — mapped name may be shadowed by a local; prefix `this.`
            var parent = expr.parent
            while (parent != null && parent !is PsiCodeBlock) parent = parent.parent
            if (parent != null) {
                val shadowed = PsiTreeUtil.collectElementsOfType(parent, PsiVariable::class.java)
                    .any { it.name == mapped }
                if (shadowed) replaceIdentifier(expr, "this.$mapped").also { return }
            }
        }
        replaceIdentifier(expr, mapped)
    }

    private fun PsiElement.isQualifiedRef(): Boolean =
        (this as? PsiJavaCodeReferenceElement)?.isQualified == true || text.contains('.')

    private fun mapClassExpr(expr: PsiElement, cls: PsiClass) {
        val src = internalName(cls) ?: return
        val mappedInternal = ctx.mapClass(src)
        if (mappedInternal == src) return
        val mapped = mappedInternal.replace('/', '.')
        val dollarName = dollarQualifiedName(cls) ?: return
        val fqName = cls.qualifiedName ?: return

        // record for wildcard-import collapsing
        cls.qualifiedName?.substringBeforeLast('.')?.let { ambiguousImports.getOrPut(it) { mutableSetOf() } += mapped }

        // fully-qualified occurrence — rewrite the whole text
        if (expr.text == fqName || expr.text == dollarName) { replace(expr, mapped); return }

        // `qualifier.Simple` split form — map qualifier too
        val qualifierExpr = when (val parent = expr.parent) {
            is KtUserType -> if (parent.qualifier != expr) parent.qualifier else null
            is KtQualifiedExpression -> if (parent.receiverExpression != expr) parent.receiverExpression else null
            else -> null
        }
        val simpleName = fqName.substringAfterLast('.')
        val qualifierName = fqName.substringBeforeLast('.')
        if (expr.text == simpleName && qualifierExpr?.text == qualifierName) {
            if (valid(qualifierExpr)) replace(qualifierExpr, mapped.substringBeforeLast('.'))
            replaceIdentifier(expr, mapped.substringAfterLast('.'))
            return
        }
        if (qualifierExpr == null && expr.text in aliased) return
        replaceIdentifier(expr, mapped.substringAfterLast('.'))
    }

    private fun mapResolved(expr: PsiElement, resolved: PsiElement?) {
        when (resolved) {
            is PsiMethod -> mapMemberExpr(expr, resolved)
            is PsiField -> mapMemberExpr(expr, resolved)
            is PsiClass -> mapClassExpr(expr, resolved)
            is PsiPackage -> {
                // `a.b` → `a/b/package-info` — the classfile exists, so the
                // tree carries its mapping; derive the package mapping from it
                val pkgInternal = resolved.qualifiedName.replace('.', '/')
                val mapped = ctx.mapClass("$pkgInternal/package-info")
                if (mapped != "$pkgInternal/package-info") {
                    replaceIdentifier(expr, mapped.substringBeforeLast('/').replace('/', '.'))
                }
            }
            is KtNamedFunction -> resolved.getRepresentativeLightMethod()?.let { mapMemberExpr(expr, it) }
            is KtClassOrObject -> resolved.fqName?.let { fq ->
                // class literal name → internal → map
                val internal = fq.asString().replace('.', '/')
                val mapped = ctx.mapClass(internal)
                if (mapped != internal) {
                    if (expr.text == fq.asString()) replace(expr, mapped.replace('/', '.'))
                    else replaceIdentifier(expr, mapped.substringAfterLast('/'))
                }
            }
        }
    }

    // ---------------- super-method walk ----------------

    private fun PsiMethod.findAllSuperMethods(): List<PsiMethod> {
        val out = LinkedHashSet<PsiMethod>()
        fun walk(m: PsiMethod, guard: MutableSet<PsiMethod>) {
            if (!guard.add(m)) return
            for (s in m.findSuperMethods()) { out += s; walk(s, guard) }
        }
        out += this
        walk(this, mutableSetOf())
        out -= this
        return out.toList()
    }

    // ---------------- main ----------------

    fun remap(errors: MutableList<Pair<Int, String>>): String {
        if (file is KtFile) {
            file.importDirectives.mapNotNull { it.aliasName }.forEach { aliased += it }
        }


        // pass 1: java elements
        file.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(c: PsiClass) {
                // class declaration rename
                c.nameIdentifier?.let { id ->
                    if (valid(id)) {
                        val src = internalName(c)
                        if (src != null) {
                            val mapped = ctx.mapClass(src)
                            if (mapped != src) {
                                if (c.containingClass == null && mappedPackage == null) {
                                    mappedPackage = mapped.substringBeforeLast('/', "").replace('/', '.')
                                }
                                replace(id, mapped.substringAfterLast('/').substringAfterLast('$'))
                            }
                        }
                    }
                }
                super.visitClass(c)
            }

            override fun visitField(field: PsiField) {
                field.nameIdentifier?.let { id ->
                    if (valid(id)) mapFieldName(field)?.let { replace(id, it) }
                }
                super.visitField(field)
            }

            override fun visitRecordComponent(component: PsiRecordComponent) {
                // record `R(int f_1)` — the component is one source site but
                // maps via the *field* row (bytecode field + accessor share it)
                component.nameIdentifier?.let { id ->
                    if (valid(id)) {
                        (component.containingClass?.let { internalName(it) })?.let { owner ->
                            val mapped = ctx.mapField(owner, component.name, null)
                            if (mapped != component.name) replace(id, mapped)
                        }
                    }
                }
                super.visitRecordComponent(component)
            }

            override fun visitMethod(method: PsiMethod) {
                method.nameIdentifier?.let { id ->
                    if (valid(id)) mapMethodName(method)?.let { replace(id, it) }
                }
                super.visitMethod(method)
            }

            override fun visitReferenceElement(reference: PsiJavaCodeReferenceElement) {
                if (valid(reference)) {
                    val resolved = reference.multiResolve(false)
                        .mapNotNull { it.element }
                        .firstOrNull { it !is PsiMethod || !it.hasModifierProperty(PsiModifier.STRICTFP) }
                        ?: reference.resolve()
                    mapResolved(reference, resolved)
                }
                super.visitReferenceElement(reference)
            }

            override fun visitPackageStatement(stmt: PsiPackageStatement) {
                val ref = stmt.packageReference
                if (ref != null && valid(ref)) {
                    // lazy — the mapped package is only known once a top-level
                    // class in this file resolves; evaluated at apply time
                    changeSet.replaceLazy(ref.textRange.startOffset, ref.textRange.endOffset) {
                        mappedPackage?.takeIf { it.isNotEmpty() } ?: ref.text
                    }
                }
                super.visitPackageStatement(stmt)
            }

            override fun visitImportStatement(import: PsiImportStatement) {
                if (valid(import)) {
                    val text = import.text.removePrefix("import").replace(Regex("\\s+"), "").removeSuffix(";")
                    if (text.endsWith(".*")) {
                        val pkg = text.dropLast(2)
                        if (ambiguousImports.containsKey(pkg)) {
                            replace(import.textRange, "")
                        } else {
                            ambiguousImports[pkg] = mutableSetOf()
                            // lazy — expands to every mapped name collected
                            // during the rest of the pass
                            changeSet.replaceLazy(import.textRange.startOffset, import.textRange.endOffset) {
                                ambiguousImports.getValue(pkg).joinToString("") { "import $it;\n" }
                            }
                        }
                    }
                }
                super.visitImportStatement(import)
            }

            override fun visitImportStaticReferenceElement(importStatic: PsiImportStaticReferenceElement) {
                if (valid(importStatic)) {
                    val text = importStatic.text.replace(Regex("\\s+"), "").removeSuffix(";")
                    if (!text.endsWith(".*")) {
                        val fq = text.removePrefix("importstatic").substringBeforeLast('.')
                        if (ambiguousImportStatics.containsKey(fq)) {
                            replace(importStatic.parent.textRange, "")
                        } else {
                            ambiguousImportStatics[fq] = mutableSetOf()
                            changeSet.replaceLazy(importStatic.parent.textRange.startOffset, importStatic.parent.textRange.endOffset) {
                                ambiguousImportStatics.getValue(fq).joinToString("") {
                                    "import static ${fq.substringBeforeLast('.')}.$it;\n"
                                }
                            }
                        }
                    }
                }
                super.visitImportStaticReferenceElement(importStatic)
            }
        })

        // pass 2: kotlin elements
        if (file is KtFile) {
            // KDoc links aren't binding-context refs — remap via DocRef grammar
            org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
                .findChildrenOfType(file, org.jetbrains.kotlin.kdoc.psi.impl.KDocLink::class.java)
                .forEach { link -> if (valid(link)) remapKdocLink(link) }

            file.accept(object : KtTreeVisitor<Void?>() {
                override fun visitNamedFunction(fn: KtNamedFunction, data: Void?): Void? {
                    fn.nameIdentifier?.let { id ->
                        if (valid(id)) fn.getRepresentativeLightMethod()?.let { m -> mapMemberExpr(id, m) }
                    }
                    return super.visitNamedFunction(fn, data)
                }

                override fun visitDotQualifiedExpression(expr: KtDotQualifiedExpression, data: Void?): Void? {
                    expr.selectorExpression?.accept(this)
                    expr.receiverExpression.accept(this)
                    return null
                }

                override fun visitPackageDirective(directive: KtPackageDirective, data: Void?): Void? {
                    // package name sits at the end of the directive text
                    val fq = directive.fqName.asString()
                    val idx = directive.text.lastIndexOf(fq)
                    if (idx >= 0 && valid(directive)) {
                        val start = directive.textRange.startOffset + idx
                        changeSet.replaceLazy(start, start + fq.length) {
                            mappedPackage?.takeIf { it.isNotEmpty() } ?: fq
                        }
                    }
                    return super.visitPackageDirective(directive, data)
                }

                override fun visitClassOrObject(cls: KtClassOrObject, data: Void?): Void? {
                    cls.nameIdentifier?.let { id ->
                        if (valid(id)) {
                            val src = cls.fqName?.asString()?.replace('.', '/')
                            if (src != null) {
                                val mapped = ctx.mapClass(src)
                                if (mapped != src) {
                                    if (mappedPackage == null && PsiTreeUtil.getParentOfType(cls, KtClassOrObject::class.java) == null) {
                                        mappedPackage = mapped.substringBeforeLast('/', "").replace('/', '.')
                                    }
                                    replaceIdentifier(id, mapped.substringAfterLast('/').substringAfterLast('$'))
                                }
                            }
                        }
                    }
                    return super.visitClassOrObject(cls, data)
                }

                override fun visitProperty(property: KtProperty, data: Void?): Void? {
                    property.nameIdentifier?.let { id -> if (valid(id)) mapKtCallable(id, property) }
                    return super.visitProperty(property, data)
                }

                override fun visitParameter(parameter: KtParameter, data: Void?): Void? {
                    if (parameter.hasValOrVar()) {
                        parameter.nameIdentifier?.let { id -> if (valid(id)) mapKtCallable(id, parameter) }
                    }
                    return super.visitParameter(parameter, data)
                }

                override fun visitImportDirective(directive: KtImportDirective, data: Void?): Void? {
                    if (directive.isAllUnder && valid(directive)) {
                        val pkg = directive.importedFqName?.asString() ?: return super.visitImportDirective(directive, data)
                        if (ambiguousImports.containsKey(pkg)) {
                            replace(directive.textRange, "")
                        } else {
                            ambiguousImports[pkg] = mutableSetOf()
                            changeSet.replaceLazy(directive.textRange.startOffset, directive.textRange.endOffset) {
                                ambiguousImports.getValue(pkg).joinToString("") { "import $it\n" }
                            }
                        }
                    }
                    return super.visitImportDirective(directive, data)
                }

                override fun visitUserType(type: KtUserType, data: Void?): Void? {
                    type.referenceExpression?.accept(this)
                    type.qualifier?.accept(this)
                    type.typeArgumentList?.accept(this)
                    return null
                }

                override fun visitReferenceExpression(expr: KtReferenceExpression, data: Void?): Void? {
                    if (valid(expr)) {
                        when (val target = bindingContext[BindingContext.REFERENCE_TARGET, expr]) {
                            is SyntheticJavaPropertyDescriptor -> {
                                // java member seen as kotlin property — map
                                // through the accessor's method mapping
                                val getterPsi = target.getMethod.findPsi() as? PsiMethod
                                if (getterPsi != null) {
                                    val mapped = mapMethodName(getterPsi)
                                    if (mapped != null) {
                                        val prop = propertyNameByGetMethodName(Name.identifier(mapped))
                                        replaceIdentifier(expr, prop?.identifier ?: "$mapped()")
                                    }
                                }
                            }
                            else -> {
                                val psi = (target as? CallableMemberDescriptor)
                                    ?.takeIf { it.kind != CallableMemberDescriptor.Kind.SYNTHESIZED }
                                    ?.findPsi()
                                if (psi != null) mapResolved(expr, psi)
                                else (target as? FunctionDescriptor)?.overriddenDescriptors
                                    ?.firstNotNullOfOrNull { it.findPsi() }
                                    ?.let { mapResolved(expr, it) }
                            }
                        }
                    }
                    return super.visitReferenceExpression(expr, data)
                }
            }, null)
        }

        for (stage in extensions) {
            runCatching { stage.transform(PsiFileView(file, bindingContext), ctx, changeSet, this.errors) }
                .onFailure { errors += file.textRange.startOffset to "${stage.javaClass.simpleName}: ${it.message}" }
        }
        if (mappedPackage == null) {
            // package-info.java — no class decls to anchor on; the
            // package-info.class entry itself carries the mapping
            (file as? PsiJavaFile)?.packageName?.takeIf { it.isNotEmpty() }?.let { pkg ->
                val mapped = ctx.mapClass("${pkg.replace('.', '/')}/package-info")
                if (mapped.substringBeforeLast('/') != pkg.replace('.', '/')) {
                    mappedPackage = mapped.substringBeforeLast('/').replace('/', '.')
                }
            }
        }
        errors += this.errors
        return result()
    }
}

private fun KtNamedFunction.getRepresentativeLightMethod(): PsiMethod? =
    org.jetbrains.kotlin.asJava.LightClassUtil.getLightClassMethod(this)
