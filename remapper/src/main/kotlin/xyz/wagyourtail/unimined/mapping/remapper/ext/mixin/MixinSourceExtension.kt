package xyz.wagyourtail.unimined.mapping.remapper.ext.mixin

import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.com.intellij.psi.*
import org.jetbrains.kotlin.com.intellij.psi.util.ClassUtil
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTable
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap
import xyz.wagyourtail.unimined.mapping.remapper.source.ChangeSet
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceExtension
import xyz.wagyourtail.unimined.mapping.remapper.source.SourceFileView
import xyz.wagyourtail.unimined.mapping.remapper.source.psi.PsiFileView

/**
 * Source-side mixin handling — the `SourceExtension` counterpart to
 * [MixinExtension]. Same [AnnotationTable] grammar, same semantics:
 *
 * - annotation *params* always remap (`method="m_1()V"`, `targets={…}`,
 *   `@At(target=…)`, nested annotations, lists)
 * - `@Shadow`/`@Accessor`/`@Invoker` *member names* (decls **and** references)
 *   are renamed to the target member's mapped name — **only when [refmap] is
 *   false**. In refmap mode the runtime binding comes from the emitted
 *   refmap, so mixin member names are preserved as written (this is the
 *   non-refmap-only behavior — mirror of the bytecode side).
 *
 * Kotlin sources get the same annotation-param handling via
 * `KtAnnotationEntry`/`KtAnnotationUseSiteTarget`; member renames cover
 * `KtNamedFunction`/`KtProperty` declarations.
 *
 * ```kotlin
 * SourceOptions(extensions = listOf(MixinSourceExtension()))                 // non-refmap
 * SourceOptions(extensions = listOf(MixinSourceExtension(refmap = true)))    // params only
 * ```
 */
open class MixinSourceExtension(
    private val table: AnnotationTable = MixinTables.ALL,
    private val refmap: Boolean = false,
) : SourceExtension {

    private fun desc(ann: PsiAnnotation): String? =
        ann.qualifiedName?.let { "L${it.replace('.', '/')};" }

    private fun literalStrings(value: org.jetbrains.kotlin.com.intellij.psi.PsiAnnotationMemberValue?): List<PsiLiteralExpression> =
        when (value) {
            is PsiArrayInitializerMemberValue -> value.initializers.filterIsInstance<PsiLiteralExpression>()
            is PsiLiteralExpression -> listOf(value)
            else -> emptyList()
        }

    /** `@Mixin` target hints — `value`/`targets` class literals + strings,
     *  normalized to internal names */
    private fun targetHints(ann: PsiAnnotation): List<String> {
        // hint params are the marker's ClassRef-declared params — `value`/
        // `targets` on @Mixin, `value`/`name` on @CTransformer
        val hintParams = table.rulesFor(desc(ann) ?: return emptyList())
            .filterValues { it is ParamRemap.ClassRef }.keys
        val out = mutableListOf<String>()
        for (attr in ann.parameterList.attributes) {
            if ((attr.name ?: "value") !in hintParams) continue
            val values = when (val v = attr.value) {
                is PsiArrayInitializerMemberValue -> v.initializers.toList()
                else -> listOfNotNull(v)
            }
            for (v in values) {
                when (v) {
                    is PsiClassObjectAccessExpression ->
                        (v.operand.innermostComponentReferenceElement?.resolve() as? PsiClass)
                            ?.qualifiedName?.replace('.', '/')?.let { out += it }
                    is PsiLiteralExpression -> (v.value as? String)?.let { out += it.replace('.', '/') }
                }
            }
        }
        return out
    }

    /** hints for a member — its containing class's `@Mixin` targets, or the
     *  class itself when not a mixin (annotation tables apply broadly) */
    private fun memberHints(member: org.jetbrains.kotlin.com.intellij.psi.PsiMember): List<String> {
        val cls = member.containingClass ?: return emptyList()
        val mixin = cls.annotations.firstOrNull { desc(it) in table.mixinMarkers }
        val hints = mixin?.let(::targetHints).orEmpty()
        return hints.ifEmpty { cls.qualifiedName?.let { listOf(it.replace('.', '/')) }.orEmpty() }
    }

    /** `remap=false` on this annotation, or on any `@Mixin` marker of the
     *  enclosing class, disables processing */
    private fun remapEnabled(ann: PsiAnnotation): Boolean =
        ann.findAttributeValue("remap") !is PsiLiteralExpression ||
        (ann.findAttributeValue("remap") as? PsiLiteralExpression)?.value != false

    private fun classRemapEnabled(cls: PsiClass): Boolean =
        cls.annotations.filter { desc(it) in table.mixinMarkers }.all { remapEnabled(it) }

    // ---- param rewriting — mirrors AnnotationTable.rewrite over PSI ----

    private fun rewriteAnnotation(ann: PsiAnnotation, ctx: ExtensionContext, hints: List<String>, changes: ChangeSet) {
        if (!remapEnabled(ann)) return
        val rules = table.rulesFor(desc(ann) ?: return)
        if (rules.isEmpty()) return
        for (attr in ann.parameterList.attributes) {
            val remap = rules[attr.name ?: "value"] ?: continue
            val values = when (val v = attr.value) {
                is PsiArrayInitializerMemberValue -> v.initializers.toList()
                else -> listOfNotNull(v)
            }
            for (v in values) {
                if (remap is ParamRemap.Nested && v is PsiAnnotation) {
                    rewriteAnnotation(v, ctx, hints, changes)
                    continue
                }
                val s = (v as? PsiLiteralExpression)?.value as? String ?: continue
                val mapped = remap.apply(s, ctx, hints, null) as? String ?: continue
                if (mapped != s && changes.valid(v.textRange.startOffset, v.textRange.endOffset)) {
                    changes.replace(v.textRange.startOffset, v.textRange.endOffset,
                        "\"${StringUtil.escapeStringCharacters(mapped)}\"")
                }
            }
        }
    }

    // ---- member renames (non-refmap mode) ----

    private fun declaredPrefixes(ann: PsiAnnotation): List<String> =
        ((ann.findAttributeValue("prefix") as? PsiLiteralExpression)?.value as? String)
            ?.let { listOf(it) } ?: emptyList()

    private fun shadowAliases(ann: PsiAnnotation): List<String> =
        literalStrings(ann.findAttributeValue("aliases")).mapNotNull { it.value as? String }

    /** the target-space name a mixin member should get — `null` to keep it */
    private fun renameFor(member: PsiMethod, ctx: ExtensionContext): String? {
        val hints = memberHints(member)
        val desc = runCatching { ClassUtil.getAsmMethodSignature(member) }.getOrNull()
        for (ann in member.annotations) {
            val d = desc(ann) ?: continue
            if (!remapEnabled(ann)) continue
            if (d in table.shadowDescriptors) {
                for ((prefix, cand) in shadowCandidates(member.name, ann)) {
                    for (t in hints) {
                        val mapped = ctx.mapMethod(t, cand, desc)
                        if (mapped != cand) return prefix + mapped
                    }
                }
            } else if (d in table.invokerDescriptors) {
                val explicit = (ann.findAttributeValue("value") as? PsiLiteralExpression)?.value as? String
                if (explicit != null) {
                    for (t in hints) {
                        val mapped = ctx.mapMethod(t, explicit, null)
                        if (mapped != explicit) return mapped
                    }
                } else {
                    val derived = table.deriveTarget(member.name, isField = false) ?: continue
                    for (t in hints) {
                        val mapped = ctx.mapMethod(t, derived.second, desc)
                        if (mapped != derived.second)
                            return derived.first + mapped.replaceFirstChar { it.uppercaseChar() }
                    }
                }
            } else if (d in table.accessorDescriptors) {
                val explicit = (ann.findAttributeValue("value") as? PsiLiteralExpression)?.value as? String
                if (explicit != null) {
                    for (t in hints) {
                        val mapped = ctx.mapField(t, explicit, null)
                        if (mapped != explicit) return mapped
                    }
                } else {
                    val derived = table.deriveTarget(member.name, isField = true) ?: continue
                    for (t in hints) {
                        val mapped = ctx.mapField(t, derived.second, null)
                        if (mapped != derived.second)
                            return derived.first + mapped.replaceFirstChar { it.uppercaseChar() }
                    }
                }
            }
        }
        return null
    }

    private fun renameFor(member: PsiField, ctx: ExtensionContext): String? {
        val hints = memberHints(member)
        for (ann in member.annotations) {
            val d = desc(ann) ?: continue
            if (!remapEnabled(ann)) continue
            if (d in table.shadowDescriptors) {
                for ((prefix, cand) in shadowCandidates(member.name, ann)) {
                    for (t in hints) {
                        val mapped = ctx.mapField(t, cand, null)
                        if (mapped != cand) return prefix + mapped
                    }
                }
            }
        }
        return null
    }

    private fun shadowCandidates(name: String, ann: PsiAnnotation?): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        for (p in (ann?.let(::declaredPrefixes) ?: emptyList()) + "shadow$") {
            if (name.startsWith(p) && name.length > p.length) out += p to name.substring(p.length)
        }
        out += "" to name
        ann?.let { shadowAliases(it).forEach { a -> out += "" to a } }
        return out
    }

    private fun renameIdentifier(e: org.jetbrains.kotlin.com.intellij.psi.PsiElement?, name: String, changes: ChangeSet) {
        e ?: return
        if (changes.valid(e.textRange.startOffset, e.textRange.endOffset))
            changes.replace(e.textRange.startOffset, e.textRange.endOffset, name)
    }

    override fun transform(file: SourceFileView, ctx: ExtensionContext, changes: ChangeSet, errors: MutableList<Pair<Int, String>>) {
        if (file !is PsiFileView) return
        file.file.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(cls: PsiClass) {
                if (classRemapEnabled(cls)) {
                    for (ann in cls.annotations) rewriteAnnotation(ann, ctx, memberHintsCls(cls), changes)
                }
                super.visitClass(cls)
            }

            override fun visitMethod(m: PsiMethod) {
                if (m.containingClass?.let { classRemapEnabled(it) } != false) {
                    val hints = memberHints(m)
                    for (ann in m.modifierList?.annotations ?: emptyArray()) rewriteAnnotation(ann, ctx, hints, changes)
                    if (!refmap) renameFor(m, ctx)?.let { renameIdentifier(m.nameIdentifier, it, changes) }
                }
                super.visitMethod(m)
            }

            override fun visitField(f: PsiField) {
                if (f.containingClass?.let { classRemapEnabled(it) } != false) {
                    val hints = memberHints(f)
                    for (ann in f.modifierList?.annotations ?: emptyArray()) rewriteAnnotation(ann, ctx, hints, changes)
                    if (!refmap) renameFor(f, ctx)?.let { renameIdentifier(f.nameIdentifier, it, changes) }
                }
                super.visitField(f)
            }

            // references — a call/access into a mixin member gets the same
            // derived name, so refs stay consistent with renamed decls
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (!refmap) {
                    val resolved = call.resolveMethod()
                    if (resolved != null && resolved.containingClass?.let { classRemapEnabled(it) } != false) {
                        renameFor(resolved, ctx)
                            ?.let { renameIdentifier(call.methodExpression.referenceNameElement, it, changes) }
                    }
                }
                super.visitMethodCallExpression(call)
            }

            override fun visitReferenceExpression(expr: PsiReferenceExpression) {
                if (!refmap) {
                    (expr.resolve() as? PsiField)
                        ?.takeIf { it.containingClass?.let { c -> classRemapEnabled(c) } != false }
                        ?.let { renameFor(it, ctx) }
                        ?.let { renameIdentifier(expr.referenceNameElement, it, changes) }
                }
                super.visitReferenceExpression(expr)
            }
        })

        // kotlin pass — KtAnnotationEntry params + Kt decl renames
        val ktFile = file.file as? org.jetbrains.kotlin.psi.KtFile ?: return
        val bc = file.bindingContext
        ktFile.accept(object : org.jetbrains.kotlin.psi.KtTreeVisitorVoid() {
            override fun visitClassOrObject(cls: org.jetbrains.kotlin.psi.KtClassOrObject) {
                val hints = ktTargetHints(cls, bc).ifEmpty {
                    cls.fqName?.asString()?.let { listOf(it.replace('.', '/')) }.orEmpty()
                }
                for (entry in cls.annotationEntries) rewriteKtAnnotation(entry, ctx, hints, changes, bc)
                super.visitClassOrObject(cls)
            }
            override fun visitNamedFunction(fn: org.jetbrains.kotlin.psi.KtNamedFunction) {
                val hints = (fn.parent?.parent as? org.jetbrains.kotlin.psi.KtClassOrObject)
                    ?.let { ktTargetHints(it, bc) }.orEmpty()
                for (entry in fn.annotationEntries) rewriteKtAnnotation(entry, ctx, hints, changes, bc)
                if (!refmap) ktRenameFor(fn, false, ctx, hints, bc)?.let {
                    renameIdentifier(fn.nameIdentifier, it, changes)
                }
                super.visitNamedFunction(fn)
            }
            override fun visitProperty(prop: org.jetbrains.kotlin.psi.KtProperty) {
                val hints = (prop.parent?.parent as? org.jetbrains.kotlin.psi.KtClassOrObject)
                    ?.let { ktTargetHints(it, bc) }.orEmpty()
                for (entry in prop.annotationEntries) rewriteKtAnnotation(entry, ctx, hints, changes, bc)
                if (!refmap) ktRenameFor(prop, true, ctx, hints, bc)?.let {
                    renameIdentifier(prop.nameIdentifier, it, changes)
                }
                super.visitProperty(prop)
            }

            override fun visitSimpleNameExpression(expr: org.jetbrains.kotlin.psi.KtSimpleNameExpression) {
                if (!refmap && bc != null && changes.valid(expr.textRange.startOffset, expr.textRange.endOffset)) {
                    val decl = bc[org.jetbrains.kotlin.resolve.BindingContext.REFERENCE_TARGET, expr]
                        ?.findPsi()
                    val (callable, isField) = when (decl) {
                        is org.jetbrains.kotlin.psi.KtNamedFunction -> decl to false
                        is org.jetbrains.kotlin.psi.KtProperty -> decl to true
                        is org.jetbrains.kotlin.psi.KtParameter -> decl to true
                        else -> null to false
                    }
                    if (callable != null) {
                        val declCls = callable.parent?.parent as? org.jetbrains.kotlin.psi.KtClassOrObject
                        val hints = declCls?.let { ktTargetHints(it, bc) }.orEmpty()
                        ktRenameFor(callable, isField, ctx, hints, bc)?.let {
                            renameIdentifier(expr, it, changes)
                        }
                    }
                }
                super.visitSimpleNameExpression(expr)
            }
        })
    }

    private fun memberHintsCls(cls: PsiClass): List<String> {
        val mixin = cls.annotations.firstOrNull { desc(it) in table.mixinMarkers }
        val hints = mixin?.let(::targetHints).orEmpty()
        return hints.ifEmpty { cls.qualifiedName?.let { listOf(it.replace('.', '/')) }.orEmpty() }
    }

    // ---- kotlin (KtAnnotationEntry / Kt declarations) ----

    private fun ktDesc(entry: org.jetbrains.kotlin.psi.KtAnnotationEntry, bc: org.jetbrains.kotlin.resolve.BindingContext?): String? {
        val fq = bc?.get(org.jetbrains.kotlin.resolve.BindingContext.ANNOTATION, entry)?.fqName?.asString()
            ?: entry.typeReference?.text
            ?: entry.calleeExpression?.text
        return fq?.let { "L${it.replace('.', '/')};" }
    }

    private fun ktLiteralStrings(expr: org.jetbrains.kotlin.psi.KtExpression?): List<org.jetbrains.kotlin.psi.KtStringTemplateExpression> =
        when (expr) {
            is org.jetbrains.kotlin.psi.KtStringTemplateExpression -> listOf(expr)
            is org.jetbrains.kotlin.psi.KtCollectionLiteralExpression ->
                expr.getInnerExpressions().filterIsInstance<org.jetbrains.kotlin.psi.KtStringTemplateExpression>()
            else -> emptyList()
        }

    private fun rewriteKtAnnotation(
        entry: org.jetbrains.kotlin.psi.KtAnnotationEntry,
        ctx: ExtensionContext,
        hints: List<String>,
        changes: ChangeSet,
        bc: org.jetbrains.kotlin.resolve.BindingContext?,
    ) {
        val rules = table.rulesFor(ktDesc(entry, bc) ?: return)
        if (rules.isEmpty()) return
        for (arg in entry.valueArguments) {
            val remap = rules[arg.getArgumentName()?.asName?.asString() ?: "value"] ?: continue
            val expr = arg.getArgumentExpression()
            if (remap is ParamRemap.Nested) {
                // nested @At-style values — array elements or a direct entry
                expr?.let {
                    org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
                        .findChildrenOfType(it, org.jetbrains.kotlin.psi.KtAnnotationEntry::class.java)
                        .forEach { e -> rewriteKtAnnotation(e, ctx, hints, changes, bc) }
                }
            }
            for (lit in ktLiteralStrings(expr)) {
                val s = lit.entries.filterIsInstance<org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry>()
                    .joinToString("") { it.text }
                if (s.isEmpty()) continue
                val mapped = remap.apply(s, ctx, hints, null) as? String ?: continue
                if (mapped != s && changes.valid(lit.textRange.startOffset, lit.textRange.endOffset)) {
                    changes.replace(lit.textRange.startOffset, lit.textRange.endOffset,
                        "\"${StringUtil.escapeStringCharacters(mapped)}\"")
                }
            }
        }
    }

    private fun ktTargetHints(cls: org.jetbrains.kotlin.psi.KtClassOrObject, bc: org.jetbrains.kotlin.resolve.BindingContext?): List<String> {
        val mixin = cls.annotationEntries.firstOrNull { ktDesc(it, bc) in table.mixinMarkers } ?: return emptyList()
        val out = mutableListOf<String>()
        for (arg in mixin.valueArguments) {
            if (arg.getArgumentName()?.asName?.asString()?.let { it != "value" && it != "targets" } == true) continue
            for (lit in ktLiteralStrings(arg.getArgumentExpression())) {
                lit.entries.filterIsInstance<org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry>()
                    .joinToString("") { it.text }.takeIf { it.isNotEmpty() }
                    ?.let { out += it.replace('.', '/') }
            }
        }
        return out
    }

    private fun ktRenameFor(decl: org.jetbrains.kotlin.psi.KtCallableDeclaration, isField: Boolean, ctx: ExtensionContext, hints: List<String>, bc: org.jetbrains.kotlin.resolve.BindingContext?): String? {
        val name = decl.name ?: return null
        for (entry in decl.annotationEntries) {
            val d = ktDesc(entry, bc) ?: continue
            if (d in table.shadowDescriptors) {
                for ((prefix, cand) in shadowCandidates(name, null)) {
                    for (t in hints) {
                        val mapped = if (isField) ctx.mapField(t, cand, null) else ctx.mapMethod(t, cand, null)
                        if (mapped != cand) return prefix + mapped
                    }
                }
            } else if (d in table.accessorDescriptors || d in table.invokerDescriptors) {
                val explicit = entry.valueArguments.firstOrNull {
                    it.getArgumentName()?.asName?.asString() == "value" || it.getArgumentName() == null
                }?.getArgumentExpression()
                val explicitName = (explicit as? org.jetbrains.kotlin.psi.KtStringTemplateExpression)
                    ?.entries?.filterIsInstance<org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry>()
                    ?.joinToString("") { it.text }
                if (explicitName != null && explicitName.isNotEmpty()) {
                    for (t in hints) {
                        val mapped = if (isField) ctx.mapField(t, explicitName, null) else ctx.mapMethod(t, explicitName, null)
                        if (mapped != explicitName) return mapped
                    }
                } else {
                    val derived = table.deriveTarget(name, isField = isField || d in table.accessorDescriptors) ?: continue
                    for (t in hints) {
                        val mapped = if (isField || d in table.accessorDescriptors)
                            ctx.mapField(t, derived.second, null) else ctx.mapMethod(t, derived.second, null)
                        if (mapped != derived.second)
                            return derived.first + mapped.replaceFirstChar { it.uppercaseChar() }
                    }
                }
            }
        }
        return null
    }
}
