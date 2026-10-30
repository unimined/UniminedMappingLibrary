package xyz.wagyourtail.unimined.mapping.remapper.ext.classtransform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonObject
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.asm.AsmExtension
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationApplyMode
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTable
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.AnnotationTableExtension
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.RefmapRecorder
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ResolvedRef

/**
 * ClassTransform remapping — in-place annotation rewrites (via
 * [AnnotationTable]) plus JarModAgent refmap emission.
 *
 * Simpler than `MixinExtension`: there are no transformer-config files to
 * discover — JMA refmaps are plain resources named by the mod's
 * `JarModAgent-Refmaps` manifest entry (or `jma.refmaps` sysprop), each a
 * `{"mappings": {<transformer>: {<key>: <replacement>}}}` object the runtime
 * substitutes verbatim onto annotation strings.
 *
 * What it does per remap:
 *  1. **analyzeResource** — an existing file at [refmapPath] is read: its
 *     `mappings` entries act as a resolution fallback and are merged into the
 *     output.
 *  2. **postApply** — the annotation-table rewrite runs; resolved refs are
 *     recorded keyed by their as-written string, per transformer class.
 *  3. **transformResource / resources** — the refmap is merged over an
 *     existing file or emitted fresh at [refmapPath]. Values are canonical
 *     target-space refs (`Lowner;name desc` / `Lowner;name:desc`), classes
 *     keyed by their *output* name.
 *
 * ```kotlin
 * Options(extensions = listOf(AsmExtensionFactory { ctx ->
 *     ClassTransformExtension(ctx, refmapPath = "jarmodagent.refmap.json")
 * }))
 * ```
 */
class ClassTransformExtension(
    private val ctx: ExtensionContext,
    private val table: AnnotationTable = ClassTransformTables.ALL,
    private val refmapPath: String = "jarmodagent.refmap.json",
    private val emitRefmap: Boolean = true,
    /** RESOLVE = semantic rewrite (default); REFMAP = verbatim substitution
     *  from the shipped refmap (mirrors JMA's RefmapAnnotationPreprocessor) */
    private val mode: AnnotationApplyMode = AnnotationApplyMode.RESOLVE,
) : AsmExtension {

    private val recorder = Recorder()
    private val delegate = AnnotationTableExtension(ctx, table, if (emitRefmap) recorder else null, mode)

    /** parsed `mappings` object of an input refmap at [refmapPath] */
    private var existingRefmap: JsonObject? = null
    private var inputHadRefmap = false

    /** transformer *output* name → (key → canonical value) */
    private val entries = mutableMapOf<String, MutableMap<String, String>>()

    override fun analyzeResource(path: String, bytes: ByteArray) {
        if (!emitRefmap || path != refmapPath) return
        inputHadRefmap = true
        val json = runCatching { Json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull() ?: return
        existingRefmap = json["mappings"]?.jsonObject
    }

    override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {
        recorder.currentInput = input.name
        delegate.postApply(input, output, cls)
        recorder.currentInput = null
    }

    override fun transformResource(path: String, bytes: ByteArray): ByteArray? =
        if (emitRefmap && path == refmapPath) mergeRefmap(bytes) else null

    override fun resources(): List<Pair<String, ByteArray>> =
        if (emitRefmap && !inputHadRefmap && entries.isNotEmpty()) {
            listOf(refmapPath to mergeRefmap(null))
        } else emptyList()

    /** merge recorded entries over an existing refmap's `mappings` object —
     *  existing transformer keys translate to output names via the class mapping */
    private fun mergeRefmap(existing: ByteArray?): ByteArray {
        val old = existing?.let {
            runCatching { Json.parseToJsonElement(it.decodeToString()).jsonObject }.getOrNull()
        }
        val oldMappings = (old?.get("mappings") as? JsonObject) ?: existingRefmap
        val merged = sortedMapOf<String, MutableMap<String, kotlinx.serialization.json.JsonElement>>()
        oldMappings?.forEach { (transformer, clsEntries) ->
            val target = ctx.mapClass(transformer)
            (clsEntries as? JsonObject)?.forEach { (k, v) ->
                merged.getOrPut(target) { mutableMapOf() }[k] = v
            }
        }
        entries.forEach { (transformer, clsEntries) ->
            clsEntries.forEach { (k, v) ->
                merged.getOrPut(transformer) { mutableMapOf() }[k] = JsonPrimitive(v)
            }
        }
        return buildJsonObject {
            old?.forEach { (k, v) -> if (k != "mappings") put(k, v) }
            putJsonObject("mappings") {
                merged.forEach { (m, e) -> putJsonObject(m) { e.forEach { (k, v) -> put(k, v) } } }
            }
        }.toString().encodeToByteArray()
    }

    private inner class Recorder : RefmapRecorder {
        override var currentMixin: String? = null   // output (target-space) name
        var currentInput: String? = null            // input (source-space) name

        override fun existingEntry(key: String): String? =
            currentInput?.let { input ->
                (existingRefmap?.get(input) as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull
            }

        override fun recordMemberRef(key: String, ref: ResolvedRef) = put(key, ref.toString())
        override fun recordClassRef(key: String, mapped: String) = put(key, mapped)
        override fun recordMemberRename(srcName: String, kindIsField: Boolean, ref: ResolvedRef) =
            put(srcName, ref.toString())

        private fun put(key: String, value: String) {
            val transformer = currentMixin ?: return
            entries.getOrPut(transformer) { sortedMapOf() }[key] = value
        }
    }
}
