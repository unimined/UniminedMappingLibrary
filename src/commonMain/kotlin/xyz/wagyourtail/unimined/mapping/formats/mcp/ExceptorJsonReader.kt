package xyz.wagyourtail.unimined.mapping.formats.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InnerType
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.INNER_CLASS
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * First-party implementation: FG2-era `exceptor.json` — the inner-class
 * structure table shipped in `mcp:<mcVersion>:srg@zip` for every MC version
 * from 1.7.2 through 1.12.2 (absent in 1.6.4; the mcp_config era replaced it
 * with `config/exceptions.txt`). No standalone upstream parser; the schema is
 * defined by what the MCP exceptor tool consumed.
 *
 * The file carries no name mappings — it is a pure metadata enricher layering
 * authoritative [InnerClassElement]s (type, inner name, outer class, exact
 * access bits) onto classes the name-providing readers created, replacing the
 * `Outer$Inner` name heuristics `renest`/`fixNest` would otherwise infer:
 * anonymous classes get a null outer (matching javac, which the digit-based
 * guesswork cannot represent), and access comes from the data instead of
 * blanket `ACC_STATIC` forcing.
 *
 * Layout: top-level keys are class internal names (joined/srg namespace —
 * identical to mcp class names in this era); each value holds `innerClasses`
 * entries shaped either `{access, inner_class, inner_name, outer_class}`
 * (named members) or `{inner_class, access?}` (anonymous). From 1.8.8 onward
 * some anonymous entries also record `inner_name`/`outer_class` — the type is
 * therefore inferred from the leaf shape, never from field presence — and
 * `outer_class` is authoritative wherever present (javac sets it for
 * initializer-scope anonymous classes, omits it for method-local ones).
 * `access` is a hex string of the exact `InnerClasses` attribute bits,
 * emitted as `ADD` operations over the (typically absent) base. `start`
 * (source position) and `enclosingMethod` (the separate EnclosingMethod
 * attribute, which ASM already remaps when present in the original bytecode)
 * are not modeling-relevant here and are skipped.
 */
object ExceptorJsonReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "exceptor.json") return false
        return input.peek().readUtf8Line()?.startsWith("{") ?: false
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: xyz.wagyourtail.unimined.mapping.MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val json = Json.parseToJsonElement(input.takeRemaining()).jsonObject

        with(into) {
            header(listOf(srcNs))

            for ((ownerKey, entry) in json) {
                val inners = entry.jsonObject["innerClasses"]?.jsonArrayInnerEntries() ?: continue
                for (inner in inners) {
                    val innerCls = InternalName.read(inner.innerClass)
                    val leaf = innerCls.value.substringAfterLast('$')

                    // type is a property of the leaf shape, not of which
                    // fields happen to be present — post-1.8.8 tables record
                    // inner_name/outer_class for anonymous entries too
                    val type = when {
                        leaf.toIntOrNull() != null -> InnerType.ANONYMOUS
                        leaf.first().isDigit() -> InnerType.LOCAL
                        else -> InnerType.INNER
                    }
                    val innerName = inner.innerName ?: when (type) {
                        InnerType.ANONYMOUS -> leaf
                        InnerType.LOCAL -> leaf.dropWhile { it.isDigit() }
                        InnerType.INNER -> leaf
                    }
                    // outer is authoritative when present — javac sets it for
                    // initializer/anonymous-member shapes and omits it for
                    // method-local ones; only fall back to the listing class
                    // for named members, which always carry it in practice
                    val outer = inner.outerClass
                        ?: if (type == InnerType.INNER) ownerKey else null

                    val fqn = outer?.let {
                        FullyQualifiedName(ObjectType(InternalName.read(it)), null)
                    }

                    pushed(MutableClassMapping(mapOf(srcNs to innerCls))) {
                        pushed(InnerClassElement(type, mapOf(srcNs to (innerName to fqn)))) {
                            if (inner.access != null) {
                                val bits = inner.access.toInt(16)
                                for (flag in AccessFlag.of(INNER_CLASS, bits)) {
                                    pushed(AccessElement(AddRemove.ADD, flag, AccessConditions.ALL)) {}
                                }
                            }
                        }
                    }
                }
            }
        }
        into.end()
    }

    private class InnerEntry(
        val innerClass: String,
        val innerName: String?,
        val outerClass: String?,
        val access: String?
    )

    private fun kotlinx.serialization.json.JsonElement.jsonArrayInnerEntries(): List<InnerEntry> {
        return jsonArray.mapNotNull { el ->
            val obj = el.jsonObject
            val cls = obj["inner_class"]?.jsonPrimitive?.content ?: return@mapNotNull null
            InnerEntry(
                cls,
                obj["inner_name"]?.jsonPrimitive?.content,
                obj["outer_class"]?.jsonPrimitive?.content,
                obj["access"]?.jsonPrimitive?.content
            )
        }
    }
}
