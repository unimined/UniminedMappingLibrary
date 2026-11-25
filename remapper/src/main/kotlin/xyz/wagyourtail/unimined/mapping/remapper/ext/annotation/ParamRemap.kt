package xyz.wagyourtail.unimined.mapping.remapper.ext.annotation
import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext

private val LOGGER = KotlinLogging.logger {}


/**
 * Declarative annotation-driven remapping. Instead of hand-writing visitor
 * logic per annotation (the Tiny-Remapper-extension approach), a table says
 * *how each annotation parameter remaps* — the engine walks the node's
 * annotations and rewrites matching values through the mapping context.
 *
 * Declaring new annotations:
 * ```kotlin
 * val table = annotationTable {
 *     annotation("Lcom/example/MyInject;") {
 *         param("method", ParamRemap.MethodRef)
 *         param("at", ParamRemap.Nested(MixinTables.POINTCUT))
 *     }
 *     shadow("Lcom/example/MyShadow;")      // rename annotated members via mixin targets
 *     accessor("Lcom/example/MyAccessor;")  // get/set/is prefix → target field
 *     invoker("Lcom/example/MyInvoker;")    // call/invoke/new prefix → target method
 *     mixinMarker("Lcom/example/Mixin;")    // declares the target classes
 * }
 * // or extend a preset:
 * val extended = MixinTables.STANDARD + table
 * ```
 */
sealed interface ParamRemap {

    /**
     * Rewrite [value] in place. [ownerHints] are the enclosing mixin class's
     * target owners (source-space internal names) — member refs without an
     * explicit owner try each until one resolves. [record] receives
     * `(originalAsWritten, canonicalTargetRef)` pairs for refmap emission —
     * `null` means recording is off.
     */
    fun apply(value: Any?, ctx: ExtensionContext, ownerHints: List<String>, record: RefmapRecorder?): Any?

    /** `Type` / class-name values — `Lpkg/Class;` types and `pkg.Class`/`pkg/Class` strings */
    object ClassRef : ParamRemap {
        override fun apply(value: Any?, ctx: ExtensionContext, ownerHints: List<String>, record: RefmapRecorder?): Any? = when (value) {
            is Type -> when (value.sort) {
                Type.OBJECT -> Type.getObjectType(ctx.mapClass(value.internalName))
                Type.ARRAY -> Type.getType("[".repeat(value.dimensions) + "L" + ctx.mapClass(value.elementType.internalName) + ";")
                else -> value
            }
            is String -> {
                val mapped = ctx.mapClass(value.replace('.', '/'))
                record?.recordClassRef(value, mapped)
                if ('/' in value) mapped else mapped.replace('/', '.')
            }
            is List<*> -> value.map { apply(it, ctx, ownerHints, record) }
            else -> value
        }
    }

    /** method-reference strings — `name`, `name desc`, `name(desc)`, `owner.name(desc)`, `Lowner;name(desc)`, `name*` wildcards */
    object MethodRef : ParamRemap {
        override fun apply(value: Any?, ctx: ExtensionContext, ownerHints: List<String>, record: RefmapRecorder?): Any? {
            if (value is List<*>) return value.map { apply(it, ctx, ownerHints, record) }
            if (value !is String) return value
            val ref = MemberRefParts.parse(value, isField = false) ?: return value
            val resolved = ref.resolveViaExisting(value, ctx, record, isField = false)
                ?: ref.resolve(ctx, ownerHints, isField = false)
                ?: return value
            record?.recordMemberRef(value, resolved)
            return ref.render(resolved)
        }
    }

    /** ambiguous member refs — `@At(target=…)` takes methods *and* fields
     *  (`Lowner;name(desc)` / `Lowner;name:desc` / bare names), plus
     *  class-only `Lowner;` refs and bare ctor descriptors
     *  (`(Largs;)Lret;`) for NEW pointcuts; tries method, then field,
     *  then class resolution */
    object MemberRef : ParamRemap {
        override fun apply(value: Any?, ctx: ExtensionContext, ownerHints: List<String>, record: RefmapRecorder?): Any? {
            if (value is List<*>) return value.map { apply(it, ctx, ownerHints, record) }
            if (value !is String) return value
            // ctor-descriptor refs — `(Largs;)Lret;` carries no owner/name;
            // every class literal inside the desc remaps
            if (value.startsWith("(")) {
                val mapped = ctx.mapDesc(value, isField = false)
                if (mapped != value) {
                    record?.recordClassRef(value, mapped)
                    return mapped
                }
                return value
            }
            MemberRefParts.parse(value, isField = false)?.let { ref ->
                (ref.resolveViaExisting(value, ctx, record, isField = false)
                    ?: ref.resolve(ctx, ownerHints, isField = false))?.let {
                    record?.recordMemberRef(value, it)
                    return ref.render(it)
                }
            }
            MemberRefParts.parse(value, isField = true)?.let { ref ->
                (ref.resolveViaExisting(value, ctx, record, isField = true)
                    ?: ref.resolve(ctx, ownerHints, isField = true))?.let {
                    record?.recordMemberRef(value, it)
                    return ref.render(it)
                }
            }
            // class-only ref (`Lowner;`) — NEW-pointcut ctor targets
            Regex("^L([^;]+);$").matchEntire(value)?.let {
                val mapped = ctx.mapClass(it.groupValues[1])
                if (mapped != it.groupValues[1]) {
                    record?.recordClassRef(value, mapped)
                    return "L$mapped;"
                }
            }
            return value
        }
    }

    /** field-reference strings — same grammar as [MethodRef], `name:desc` field forms, field lookup */
    object FieldRef : ParamRemap {
        override fun apply(value: Any?, ctx: ExtensionContext, ownerHints: List<String>, record: RefmapRecorder?): Any? {
            if (value is List<*>) return value.map { apply(it, ctx, ownerHints, record) }
            if (value !is String) return value
            val ref = MemberRefParts.parse(value, isField = true) ?: return value
            val resolved = ref.resolveViaExisting(value, ctx, record, isField = true)
                ?: ref.resolve(ctx, ownerHints, isField = true)
                ?: return value
            record?.recordMemberRef(value, resolved)
            return ref.render(resolved)
        }
    }

    /** `@At(args="class=pkg.Foo,…")` — comma-separated `key=value`; `class=` values remap as class refs */
    object InjectionArgs : ParamRemap {
        override fun apply(value: Any?, ctx: ExtensionContext, ownerHints: List<String>, record: RefmapRecorder?): Any? {
            if (value is List<*>) return value.map { apply(it, ctx, ownerHints, record) }
            if (value !is String) return value
            return value.split(',').joinToString(",") { part ->
                val kv = part.trim().split('=', limit = 2)
                if (kv.size == 2 && kv[0] == "class") {
                    val mapped = ctx.mapClass(kv[1].replace('.', '/'))
                    record?.recordClassRef(kv[1], mapped)
                    "class=$mapped"
                } else part
            }
        }
    }

    /** recurse into a nested annotation with its own rule set */
    /** rewrite nested annotation params through [table] — lazy so a table can
     *  self-reference its own nested grammar (`Nested { STANDARD }`) */
    class Nested(val table: () -> AnnotationTable) : ParamRemap {
        constructor(table: AnnotationTable) : this({ table })
        override fun apply(value: Any?, ctx: ExtensionContext, ownerHints: List<String>, record: RefmapRecorder?): Any? {
            if (value is AnnotationNode) { table().rewrite(value, ctx, ownerHints, record); return value }
            if (value is List<*>) return value.map { apply(it, ctx, ownerHints, record) }
            return value
        }
    }
}

/** refmap entry sink — receives canonical target-space refs keyed by the
 *  value as written in the source annotation. Implemented by [MixinExtension]. */
interface RefmapRecorder {
    /** current mixin class context — set by the driver before each member/class pass */
    var currentMixin: String?

    /** an existing input refmap's entry for [key] in the current mixin class —
     *  used as a resolution fallback when the tree lookup misses */
    fun existingEntry(key: String): String? = null

    /** a member ref resolved: [key] as written → [ref] canonical target ref (`Lowner;name desc` / `name:desc`) */
    fun recordMemberRef(key: String, ref: ResolvedRef)

    /** a class ref resolved: [key] as written → [mapped] target internal name */
    fun recordClassRef(key: String, mapped: String) {}

    /** a member of the mixin class itself was renamed (shadow/accessor/invoker) */
    fun recordMemberRename(srcName: String, kindIsField: Boolean, ref: ResolvedRef) {}
}

/** a resolved member mapping — canonical refmap rendering lives here */
class ResolvedRef(
    val owner: String,
    val name: String,
    val desc: String?,
    val isField: Boolean,
    /** omit the `Lowner;` prefix — unimined does this for multi-target mixins */
    val includeOwner: Boolean,
) {
    /** canonical refmap value — methods `Lowner;name desc`, fields `Lowner;name:desc` */
    override fun toString(): String {
        val body = if (isField) "$name${desc?.let { ":$it" } ?: ""}" else "$name${desc ?: ""}"
        return if (includeOwner) "L$owner;$body" else body
    }
}

/**
 * Parsed `owner + name + desc` out of the string forms member refs take.
 * [owner]/[desc] are null when the form doesn't carry them — resolution falls
 * back to the mixin target hints / desc-less lookup.
 */
class MemberRefParts(
    val owner: String?,
    val name: String,
    val desc: String?,
) {

    /** resolve against the tree — returns null when no hint produces a member */
    fun resolve(ctx: ExtensionContext, ownerHints: List<String>, isField: Boolean): ResolvedRef? {
        if (name in listOf("<init>", "<clinit>", "*")) {
            // ctor/wildcard refs: name passes through, owner + desc still remap.
            // Don't synthesize an owner for these — bare `<init>`/`<clinit>`
            // can't collapse through obfuscation, and `*` is intentionally
            // target-wide. Explicit owners still remap. ownerHints are
            // source-space — map them too, else an explicit owner keeps the
            // fns-space name (refmap consumers see unmapped C_*).
            val mappedDesc = desc?.let { ctx.mapDesc(it, isField) }
            val mappedOwner = owner?.let(ctx::mapClass)
                ?: ownerHints.singleOrNull()?.let(ctx::mapClass) ?: ""
            return ResolvedRef(mappedOwner, name, mappedDesc, isField,
                includeOwner = mappedOwner.isNotEmpty() && owner != null)
        }
        val wildcard = name.endsWith("*")
        val baseName = if (wildcard) name.dropLast(1) else name
        val lookupDesc = desc?.takeIf { it != "*" }
        val hints = if (owner != null) listOf(owner.replace('.', '/')) else ownerHints
        var warned = false
        for (hint in hints) {
            // a desc-less ref must resolve to one real member before its desc
            // can be canonically filled in. Real overloads are not a first-
            // match race: picking a row would silently narrow a multi-match
            // selector (and often pick the wrong one). Ambiguity on one owner
            // hint doesn't poison the others — multi-target mixins try each.
            val descless = if (lookupDesc == null && !wildcard) {
                ctx.lookupDesclessMember(hint, baseName, isField)
            } else null
            if (descless?.ambiguous == true) {
                if (!warned) {
                    LOGGER.warn { "ambiguous member ref '$name' on $hint (no descriptor, multiple candidates) — leaving it unqualified" }
                    warned = true
                }
                continue
            }
            val mapped = if (isField) ctx.mapField(hint, baseName, lookupDesc) else ctx.mapMethod(hint, baseName, lookupDesc)
            // `mapped == baseName` conflates "member maps to itself" (e.g.
            // Closeable.close() — identical in every namespace) with "no such
            // member". An explicit owner commits the ref either way — remap
            // owner+desc even when the name is unchanged or unresolved,
            // matching tiny-remapper (a source-ns owner left verbatim can
            // never resolve). Ownerless refs use a member row/scan candidate's
            // existence under the written name as the found-signal.
            if (mapped != baseName || owner != null || descless?.found == true ||
                ctx.memberCandidateCount(hint, baseName, isField) > 0) {
                val mappedOwner = ctx.mapClass(hint)
                // canonical desc: use the resolved member's real desc when the
                // ref was desc-less, else the remapped written desc
                val canonicalDesc = lookupDesc?.let { ctx.mapDesc(it, isField) }
                    ?: descless?.sourceDesc?.let { ctx.mapDesc(it, isField) }
                    ?: ctx.resolveMemberDesc(hint, baseName, isField)
                return ResolvedRef(
                    mappedOwner, mapped + (if (wildcard) "*" else ""), canonicalDesc, isField,
                    includeOwner = owner != null || ownerHints.size <= 1,
                )
            }
        }
        return null
    }

    /** resolution fallback: the recorder's existing refmap entry holds an
     *  fns-space canonical ref — parse it and remap *that* to target space.
     *  Some artifacts (notably LiteLoader) ship production-obfuscation refs
     *  in the top-level table while the mod binary itself is in a different
     *  source namespace (searge). Those entries are not compile-namespace
     *  bridges for this transform: accepting one would rewrite an already
     *  correct dev-space annotation to the unresolved obfuscated member
     *  (`init` → `aq`). An explicit owner must therefore exist in fns before
     *  the fallback is eligible. Ownerless entries keep the existing
     *  resolution behavior. */
    fun resolveViaExisting(original: String, ctx: ExtensionContext, record: RefmapRecorder?, isField: Boolean): ResolvedRef? {
        val ex = record?.existingEntry(original) ?: return null
        val parsed = parse(ex, isField) ?: return null
        val owner = parsed.owner?.replace('.', '/')
        if (owner != null) {
            if (ctx.tree.getClass(ctx.fns, InternalName.unchecked(owner)) == null) {
                return null
            }
            // A fallback ref is only a compile-namespace bridge if the member
            // itself exists in fns. LiteLoader ships at least one stale
            // `data.searge` value whose owner is valid but whose descriptor
            // carries an extra parameter; explicit-owner resolution would
            // otherwise commit that unresolved member and overwrite a correct
            // already-target annotation with the malformed descriptor.
            val wildcard = parsed.name.endsWith("*")
            val baseName = if (wildcard) parsed.name.dropLast(1) else parsed.name
            if (!wildcard && ctx.memberCandidateCount(owner, baseName, isField) == 0) {
                return null
            }
            // the written desc is fns-space (that's the fallback's premise),
            // but `resolveMemberDesc` renders target-space — compare
            // like-with-like or every desc-bearing legacy bridge is rejected
            // (rift's `Lux;a(Luy;)V` never matched `(Lnet/…/RepositorySource;)V`)
            val writtenDesc = parsed.desc?.takeIf { it != "*" }
            if (!wildcard && writtenDesc != null &&
                ctx.resolveMemberDesc(owner, baseName, isField) != ctx.mapDesc(writtenDesc, isField)) {
                return null
            }
        }
        return parsed.resolve(ctx, emptyList(), isField)
    }

    /** rewrite the ref in canonical mixin form. Resolution has already
     *  established the member the ref names, so preserving a lossy bare shape
     *  is strictly worse: obfuscated production namespaces collapse distinct
     *  source names onto the same single-letter members, where a bare name
     *  can over-match or fail `require`. [ResolvedRef.includeOwner] still
     *  suppresses the owner for multi-target mixins, where choosing one
     *  owner would narrow the selector incorrectly. */
    fun render(resolved: ResolvedRef): String = resolved.toString()

    companion object {
        fun parse(value: String, isField: Boolean): MemberRefParts? {
            val v = value.trim()
            if (v.isEmpty()) return null
            if (isField) {
                // Lowner;name:desc / owner.name:desc / name:desc
                Regex("^L([^;]+);(\\S+):(\\S+)$").matchEntire(v)?.let {
                    return MemberRefParts(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                }
                Regex("^([\\w/$]+)\\.([\\w$<>*]+):(\\S+)$").matchEntire(v)?.let {
                    return MemberRefParts(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                }
                Regex("^([\\w$<>*]+):(\\S+)$").matchEntire(v)?.let {
                    return MemberRefParts(null, it.groupValues[1], it.groupValues[2])
                }
            } else {
                // Lowner;name(desc) / Lowner;name desc
                Regex("^L([^;]+);([\\w$<>*]+)(\\(.*)$").matchEntire(v)?.let {
                    return MemberRefParts(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                }
                // owner.name(desc)
                Regex("^([\\w/$]+)\\.([\\w$<>*]+)(\\(.*)$").matchEntire(v)?.let {
                    return MemberRefParts(it.groupValues[1], it.groupValues[2], it.groupValues[3])
                }
                // name(desc) — owner from hint
                Regex("^([\\w$<>*]+)(\\(.*)$").matchEntire(v)?.let {
                    return MemberRefParts(null, it.groupValues[1], it.groupValues[2])
                }
                // name desc — owner from hint
                Regex("^([\\w$<>*]+) (\\S+)$").matchEntire(v)?.let {
                    return MemberRefParts(null, it.groupValues[1], it.groupValues[2])
                }
            }
            // bare name — owner + desc from hint / desc-less lookup
            return MemberRefParts(null, v, null)
        }
    }
}
