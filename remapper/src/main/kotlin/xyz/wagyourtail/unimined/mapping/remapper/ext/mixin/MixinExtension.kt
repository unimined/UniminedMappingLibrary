package xyz.wagyourtail.unimined.mapping.remapper.ext.mixin

import kotlinx.serialization.json.*
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationApplyMode
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTable
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTableExtension
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.MemberRefParts
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.RefmapRecorder
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ResolvedRef

/** lenient reader for shipped mixin configs — real-world jars carry
 *  trailing commas (OSL) that strict JSON rejects; parse failures would
 *  silently unregister the config and orphan its refmap entries */
private val LENIENT = Json {
    ignoreUnknownKeys = true
    allowTrailingComma = true
    isLenient = true
}

/**
 * Mixin remapping — in-place annotation rewrites (via [AnnotationTable]) plus
 * refmap JSON emission.
 *
 * What it does per remap:
 *  1. **analyzeResource** — reads `*.mixins.json` configs (which classes are
 *     mixins, where the refmap lives) and any existing `*-refmap.json` (its
 *     `mappings` entries are used as a resolution fallback — like unimined's
 *     `existingMappings`) and merged into the output.
 *  2. **postApply** — the annotation-table rewrite (hard-target remap) runs;
 *     every resolved member/class ref and every shadow/accessor/invoker member
 *     rename is recorded as a refmap entry.
 *  3. **transformResource / resources** — the refmap is merged into the
 *     existing file (if the jar shipped one) or emitted fresh at the path the
 *     mixin config declares (config-derived `<name>-refmap.json` when absent;
 *     the config is patched to declare it). Refmap values are canonical
 *     target-space refs (`Lowner;name desc` / `Lowner;name:desc`), mixin
 *     classes keyed by their *output* name.
 *
 * Usage:
 * ```kotlin
 * Options(extensions = listOf(AsmExtensionFactory { ctx ->
 *     MixinExtension(ctx)                     // sponge + MixinExtras
 *     // MixinExtension(ctx, MixinTables.STANDARD + myTable)
 * }))
 * ```
 */
class MixinExtension(
    private val ctx: ExtensionContext,
    private val table: AnnotationTable = MixinTables.ALL,
    private val emitRefmap: Boolean = true,
    /** mixin config paths whose refmap emission is suppressed */
    private val noRefmap: Set<String> = emptySet(),
    /** RESOLVE = semantic rewrite (default); REFMAP = verbatim substitution
     *  from shipped refmaps (mirrors JMA's RefmapAnnotationPreprocessor) */
    private val mode: AnnotationApplyMode = AnnotationApplyMode.RESOLVE,
) : AsmExtension {

    companion object {
        /**
         * Mixin config file names — `*.mixins.json` (the sponge/fabric
         * convention) and `mixins.*.json` (liteloader-style, e.g.
         * `mixins.liteloader.client.json`). Matched against the file name
         * only, not directory components.
         */
        @JvmStatic
        fun isMixinConfigPath(path: String): Boolean {
            val name = path.substringAfterLast('/')
            return name.endsWith(".mixins.json") ||
                (name.startsWith("mixins.") && name.endsWith(".json"))
        }
    }

    private val recorder = Recorder()
    private val delegate = AnnotationTableExtension(ctx, table, if (emitRefmap) recorder else null, mode)

    /** config path → declared refmap path */
    private val configRefmaps = mutableMapOf<String, String>()
    /** config path → mixin internal names it declares (fns space) */
    private val configMixins = mutableMapOf<String, Set<String>>()
    /** input-resource path → parsed existing refmap `mappings` object */
    private val existingRefmaps = mutableMapOf<String, JsonObject>()
    /** mixin source name → refmap path (resolved at finishAnalysis) */
    private val mixinRefmapPath = mutableMapOf<String, String>()
    /** mixin source name → `@Mixin` target internal names (fns space) —
     *  owner hints for resolving ownerless shipped-refmap values */
    private val mixinTargets = mutableMapOf<String, List<String>>()
    /** refmap paths present as input resources */
    private val inputResources = mutableSetOf<String>()

    /** refmap path → mixin *output* name → (key → canonical value) */
    private val entries = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()

    override fun analyzeResource(path: String, bytes: ByteArray) {
        inputResources += path
        if (!emitRefmap) return
        // refmap files are checked before configs — `mixins.*.json` config
        // names also match `mixins.<name>.refmap.json` (liteloader-style), so
        // the config test alone would misfile shipped refmaps as configs
        if (path.endsWith("-refmap.json") || path.endsWith("refmap.json")) {
            val json = runCatching { LENIENT.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull() ?: return
            json["mappings"]?.jsonObject?.let { existingRefmaps[path] = it }
        } else if (isMixinConfigPath(path)) {
            val json = runCatching { LENIENT.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull() ?: return
            val pkg = json["package"]?.jsonPrimitive?.contentOrNull?.replace('.', '/') ?: ""
            val refmap = json["refmap"]?.jsonPrimitive?.contentOrNull
                ?: path.removeSuffix(".mixins.json").removeSuffix(".json") + "-refmap.json"
            configRefmaps[path] = refmap
            configMixins[path] = setOf("mixins", "client", "server")
                .flatMap { k -> json[k]?.jsonArray.orEmpty() }
                .mapNotNull { it.jsonPrimitive.contentOrNull }
                .map { (if (pkg.isEmpty()) it else "$pkg/$it").replace('.', '/') }
                .toSet()
        }
    }

    /** member renames resolve during [analyze] (pre-remap) — the input
     *  name keys `existingEntry` lookups while the delegate runs */
    override fun analyze(node: ClassNode) {
        recorder.currentInput = node.name
        delegate.analyze(node)
        recorder.currentInput = null
    }

    /** config→mixin refmap routing — populated lazily because [analyze]
     *  records entries before [finishAnalysis] runs */
    private var refmapPathsEnsured = false
    private fun ensureRefmapPaths() {
        if (refmapPathsEnsured) return
        refmapPathsEnsured = true
        for ((cfg, mixins) in configMixins) {
            val path = configRefmaps.getValue(cfg)
            for (m in mixins) mixinRefmapPath[m] = path
        }
    }

    override fun finishAnalysis() = ensureRefmapPaths()

    /** which refmap a mixin class's entries land in */
    private fun refmapPathFor(mixinSource: String): String {
        ensureRefmapPaths()
        return mixinRefmapPath[mixinSource]
            ?: configRefmaps.entries.firstOrNull { it.key !in noRefmap }?.value
            ?: "refmap.json"
    }

    override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {
        recorder.currentInput = input.name
        delegate.postApply(input, output, cls)
        recorder.currentInput = null
        captureTargets(input)
    }

    /** record the mixin's `@Mixin` targets (fns names) as resolution hints
     *  for ownerless refmap values like `d:Ljava/util/Map;` */
    private fun captureTargets(node: ClassNode) {
        val mixin = (node.visibleAnnotations.orEmpty() + node.invisibleAnnotations.orEmpty())
            .firstOrNull { it.desc == "Lorg/spongepowered/asm/mixin/Mixin;" }
            ?: return
        val values = mixin.values ?: return
        val targets = mutableListOf<String>()
        var i = 0
        while (i < values.size - 1) {
            when (values[i] as? String) {
                "value" -> (values[i + 1] as? List<*>)?.forEach { t ->
                    (t as? org.objectweb.asm.Type)?.let { targets += fnsHint(it.internalName) }
                }
                "targets" -> (values[i + 1] as? List<*>)?.forEach { t ->
                    (t as? String)?.let { targets += fnsHint(it.replace('.', '/')) }
                }
            }
            i += 2
        }
        if (targets.isNotEmpty()) mixinTargets[node.name] = targets
    }

    /** normalize a captured target name to fns space — annotation literals are
     *  normally source-space, but a step running an identity outer remap over
     *  an already-target-named jar (e.g. unimined's modArtifacts) hands us
     *  target-space names, while member resolution queries fns rows */
    private fun fnsHint(internalName: String): String =
        if (ctx.tree.getClass(ctx.fns, InternalName.unchecked(internalName)) != null) internalName
        else ctx.reverse.map(InternalName.unchecked(internalName)).value

    override fun transformResource(path: String, bytes: ByteArray): ByteArray? {
        if (!emitRefmap || path in noRefmap) return null
        // patch configs that declare no refmap so the emitted file actually loads
        if (path in configRefmaps) {
            val json = runCatching { LENIENT.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull() ?: return null
            if (json.containsKey("refmap")) return null
            val refmap = configRefmaps.getValue(path)
            if (entries[refmap].isNullOrEmpty()) return null
            return buildJsonObject {
                json.forEach { (k, v) -> put(k, v) }
                put("refmap", JsonPrimitive(refmap))
            }.toString().encodeToByteArray()
        }
        if (entries.containsKey(path) || existingRefmaps.containsKey(path)) {
            return mergeRefmap(path, bytes)
        }
        return null
    }

    override fun resources(): List<Pair<String, ByteArray>> {
        if (!emitRefmap) return emptyList()
        return entries.filter { it.key !in noRefmap && it.key !in inputResources }
            .map { (path, _) -> path to mergeRefmap(path, null) }
    }

    /** merge recorded entries over an existing refmap's `mappings` object —
     *  existing mixin keys translate to output names via the class mapping */
    private fun mergeRefmap(path: String, existing: ByteArray?): ByteArray {
        val old = existing?.let {
            runCatching { LENIENT.parseToJsonElement(it.decodeToString()).jsonObject }.getOrNull()
        } ?: existingRefmaps[path]
        val oldMappings = (old?.get("mappings") as? JsonObject) ?: existingRefmaps[path]
        val merged = sortedMapOf<String, MutableMap<String, kotlinx.serialization.json.JsonElement>>()
        oldMappings?.forEach { (mixin, clsEntries) ->
            val target = ctx.mapClass(mixin)
            val hints = mixinTargets[mixin] ?: emptyList()
            (clsEntries as? JsonObject)?.forEach { (k, v) ->
                merged.getOrPut(target) { mutableMapOf() }[k] =
                    JsonPrimitive(remapRefmapValue(v, hints))
            }
        }
        entries[path]?.forEach { (mixin, clsEntries) ->
            clsEntries.forEach { (k, v) ->
                merged.getOrPut(mixin) { mutableMapOf() }[k] = JsonPrimitive(v)
            }
        }
        val mappings = buildJsonObject {
            merged.forEach { (m, e) ->
                putJsonObject(m) { e.forEach { (k, v) -> put(k, v) } }
            }
        }
        // `data` alternate-environment sections — mixin keys re-key the same
        // way (`ctx.mapClass`); env-specific values get the same source→target
        // remap attempt as `mappings` (tables in other namespaces — e.g. a
        // `searge` block under an official→mcp remap — can't resolve and stay
        // verbatim)
        val data = (old?.get("data") as? JsonObject)?.let { oldData ->
            buildJsonObject {
                oldData.forEach { (env, envMap) ->
                    putJsonObject(env) {
                        (envMap as? JsonObject)?.forEach { (mixin, clsEntries) ->
                            putJsonObject(ctx.mapClass(mixin)) {
                                (clsEntries as? JsonObject)?.forEach { (k, v) ->
                                    put(k, JsonPrimitive(remapRefmapValue(v, mixinTargets[mixin] ?: emptyList())))
                                }
                            }
                        }
                    }
                }
            }
        }
        return buildJsonObject {
            old?.forEach { (k, v) -> if (k != "mappings" && k != "data") put(k, v) }
            put("mappings", mappings)
            if (data != null) put("data", data)
        }.toString().encodeToByteArray()
    }

    /** existing refmap lookup — keys are mixin names in *input* space */
    private fun existingEntry(inputMixin: String, key: String): String? =
        existingRefmaps.values.firstNotNullOfOrNull {
            (it[inputMixin] as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull
        }

    /**
     * Remap a shipped refmap value into target space — `Lowner;name desc`
     * member refs resolve through the tree, ownerless refs (`d`, `field_x:y`)
     * resolve against the mixin's `@Mixin` targets via [ownerHints], bare
     * class names via [ExtensionContext.mapClass]; anything unresolvable
     * (cross-namespace env tables, wildcards, already-target refs) passes
     * through verbatim.
     */
    private fun remapRefmapValue(value: JsonElement, ownerHints: List<String>): String {
        val v = (value as? JsonPrimitive)?.contentOrNull?.trim() ?: return value.toString()
        if (v.isEmpty()) return v
        if ('(' in v || ':' in v || v.startsWith("L")) {
            val isField = '(' !in v
            MemberRefParts.parse(v, isField)
                ?.resolve(ctx, ownerHints, isField)
                ?.let { return it.toString() }
        }
        return ctx.mapClass(v)
    }

    private inner class Recorder : RefmapRecorder {
        override var currentMixin: String? = null   // output (target-space) name
        var currentInput: String? = null            // input (source-space) name

        override fun existingEntry(key: String): String? =
            currentInput?.let { existingEntry(it, key) }

        override fun recordMemberRef(key: String, ref: ResolvedRef) = put(key, ref.toString())
        override fun recordClassRef(key: String, mapped: String) = put(key, mapped)
        override fun recordMemberRename(srcName: String, kindIsField: Boolean, ref: ResolvedRef) =
            put(srcName, ref.toString())

        private fun put(key: String, value: String) {
            val mixin = currentMixin ?: return
            val path = refmapPathFor(currentInput ?: mixin)
            if (path in noRefmap) return
            entries.getOrPut(path) { mutableMapOf() }
                .getOrPut(mixin) { sortedMapOf() }[key] = value
        }
    }
}
