package xyz.wagyourtail.unimined.mapping.remapper.ext.mixin

import kotlinx.serialization.json.*
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTable
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTableExtension
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.RefmapRecorder
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ResolvedRef

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
) : AsmExtension {

    private val recorder = Recorder()
    private val delegate = AnnotationTableExtension(ctx, table, if (emitRefmap) recorder else null)

    /** config path → declared refmap path */
    private val configRefmaps = mutableMapOf<String, String>()
    /** config path → mixin internal names it declares (fns space) */
    private val configMixins = mutableMapOf<String, Set<String>>()
    /** input-resource path → parsed existing refmap `mappings` object */
    private val existingRefmaps = mutableMapOf<String, JsonObject>()
    /** mixin source name → refmap path (resolved at finishAnalysis) */
    private val mixinRefmapPath = mutableMapOf<String, String>()
    /** refmap paths present as input resources */
    private val inputResources = mutableSetOf<String>()

    /** refmap path → mixin *output* name → (key → canonical value) */
    private val entries = mutableMapOf<String, MutableMap<String, MutableMap<String, String>>>()

    override fun analyzeResource(path: String, bytes: ByteArray) {
        inputResources += path
        if (!emitRefmap) return
        if (path.endsWith(".mixins.json")) {
            val json = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull() ?: return
            val pkg = json["package"]?.jsonPrimitive?.contentOrNull?.replace('.', '/') ?: ""
            val refmap = json["refmap"]?.jsonPrimitive?.contentOrNull
                ?: path.removeSuffix(".mixins.json") + "-refmap.json"
            configRefmaps[path] = refmap
            configMixins[path] = setOf("mixins", "client", "server")
                .flatMap { k -> json[k]?.jsonArray.orEmpty() }
                .mapNotNull { it.jsonPrimitive.contentOrNull }
                .map { (if (pkg.isEmpty()) it else "$pkg/$it").replace('.', '/') }
                .toSet()
        } else if (path.endsWith("-refmap.json") || path.endsWith("refmap.json")) {
            val json = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull() ?: return
            json["mappings"]?.jsonObject?.let { existingRefmaps[path] = it }
        }
    }

    override fun finishAnalysis() {
        for ((cfg, mixins) in configMixins) {
            val path = configRefmaps.getValue(cfg)
            for (m in mixins) mixinRefmapPath[m] = path
        }
    }

    /** which refmap a mixin class's entries land in */
    private fun refmapPathFor(mixinSource: String): String =
        mixinRefmapPath[mixinSource]
            ?: configRefmaps.entries.firstOrNull { it.key !in noRefmap }?.value
            ?: "refmap.json"

    override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {
        recorder.currentInput = input.name
        delegate.postApply(input, output, cls)
        recorder.currentInput = null
    }

    override fun transformResource(path: String, bytes: ByteArray): ByteArray? {
        if (!emitRefmap || path in noRefmap) return null
        // patch configs that declare no refmap so the emitted file actually loads
        if (path in configRefmaps && path.endsWith(".mixins.json")) {
            val json = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull() ?: return null
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
            runCatching { Json.parseToJsonElement(it.decodeToString()).jsonObject }.getOrNull()
        } ?: existingRefmaps[path]
        val oldMappings = (old?.get("mappings") as? JsonObject) ?: existingRefmaps[path]
        val merged = sortedMapOf<String, MutableMap<String, kotlinx.serialization.json.JsonElement>>()
        oldMappings?.forEach { (mixin, clsEntries) ->
            val target = ctx.mapClass(mixin)
            (clsEntries as? JsonObject)?.forEach { (k, v) ->
                merged.getOrPut(target) { mutableMapOf() }[k] = v
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
        // way (`ctx.mapClass`); the env-specific *values* stay verbatim
        val data = (old?.get("data") as? JsonObject)?.let { oldData ->
            buildJsonObject {
                oldData.forEach { (env, envMap) ->
                    putJsonObject(env) {
                        (envMap as? JsonObject)?.forEach { (mixin, clsEntries) ->
                            put(ctx.mapClass(mixin), clsEntries)
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
