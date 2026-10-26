package xyz.wagyourtail.unimined.mapping.remapper

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.remapper
import xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree

/**
 * Per-remap view handed to [AsmExtension] instances — the mapping tree plus
 * ready-made [xyz.wagyourtail.unimined.mapping.NamespaceRemapper]s in both
 * directions. Most extensions need member lookups beyond a flat rename (e.g.
 * resolving mixin `target`/`method` annotation values against the tree), so
 * both directions are provided.
 */
class ExtensionContext(
    /** the (already overlaid + propagated) mapping tree in effect */
    val tree: MappingTree,
    /** the input jar's namespace */
    val fns: Namespace,
    /** the output jar's namespace */
    val targetNs: Namespace,
    /** the active remap options — builtin stages read their flags here */
    val options: xyz.wagyourtail.unimined.mapping.remapper.asm.Options = xyz.wagyourtail.unimined.mapping.remapper.asm.Options(),
) {

    /** `"owner.name"` → (ConstantValue, descriptor) of static fields in the
     *  input jar — populated by the pipeline before class transforms run,
     *  consumed by constant-group unpick ([ConstantGroupEmitter]) */
    var fieldValues: Map<String, Pair<Any, String>> = emptyMap()

    /** resolve the member row that supplied a method's propagated name —
     *  param/lvar and other member children live on the originating row.
     *  Input is the method's fns identity. Set by the pipeline when
     *  inheritance propagation is in effect (graph remapping); null for
     *  flat remaps. */
    var methodOrigin: ((owner: InternalName, name: UnqualifiedName, desc: MethodDescriptor) -> xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping?)? =
        null

    /** source → target member/type remapper */
    val remapper = tree.remapper(fns, targetNs)

    /** target → source — for resolving names written in the target space */
    val reverse = tree.remapper(targetNs, fns)

    /** map a class internal name source → target (`base/Service` → `b`) */
    fun mapClass(internalName: String): String = remapper.map(InternalName.unchecked(internalName)).value

    /** map a method name source → target; [desc] disambiguates overloads, null
     *  for desc-less lookup */
    fun mapMethod(owner: String, name: String, desc: String? = null): String = remapper
        .mapMethod(InternalName.unchecked(owner), UnqualifiedName.unchecked(name), desc?.let(MethodDescriptor::unchecked)).value

    /** map a field name source → target; [desc] may be null for desc-less lookup */
    fun mapField(owner: String, name: String, desc: String? = null): String = remapper
        .mapField(InternalName.unchecked(owner), UnqualifiedName.unchecked(name), desc?.let(FieldDescriptor::unchecked)).value

    /** remap the class refs inside a raw descriptor string ([isField] selects
     *  field vs method descriptor parsing) */
    fun mapDesc(desc: String, isField: Boolean): String =
        if (isField) remapper.map(FieldDescriptor.unchecked(desc)).toString()
        else remapper.map(MethodDescriptor.unchecked(desc)).toString()

    /** number of member rows matching a desc-less lookup — `>1` means a
     *  name-only ref is ambiguous */
    fun memberCandidateCount(owner: String, name: String, isField: Boolean): Int {
        val cls = tree.getClass(fns, InternalName.unchecked(owner)) ?: return 0
        val n = UnqualifiedName.unchecked(name)
        return with(tree) {
            if (isField) cls.fields.count { it.names[fns] == n }
            else cls.getMethods(fns, n, null).size
        }
    }

    /** the scanned classfile's actual desc for [name] on [owner] — the
     *  bytecode-scan disambiguator for desc-less refs when >1 tree row
     *  matches (desc-less `firstOrNull` picks wrong on overloads) */
    private fun scannedDesc(owner: String, name: String, isField: Boolean): String? {
        val info = (tree as? InheritanceBackedMappingTree)?.inheritance
            ?.classes?.get(InternalName.unchecked(owner)) ?: return null
        return if (isField) {
            info.fields.firstOrNull { it.name.value == name }?.descriptor?.toString()
        } else {
            info.methods.firstOrNull { it.name.value == name }?.descriptor?.toString()
        }
    }

    /** the resolved member's *source* desc remapped to target space — used to
     *  render canonical refs when the written ref was desc-less */
    fun resolveMemberDesc(owner: String, name: String, isField: Boolean): String? {
        val cls = tree.getClass(fns, InternalName.unchecked(owner)) ?: return null
        val n = UnqualifiedName.unchecked(name)
        return with(tree) {
            // ambiguous name-only lookup — consult the bytecode scan for the
            // member's real desc before falling back to firstOrNull
            val resolved = if (memberCandidateCount(owner, name, isField) > 1) {
                scannedDesc(owner, name, isField)
            } else null
            if (isField) {
                cls.getField(fns, n, resolved?.let { FieldDescriptor.unchecked(it) })
                    ?.getDescriptor(fns)?.let { remapper.map(it).toString() }
            } else {
                cls.getMethod(fns, n, resolved?.let { MethodDescriptor.unchecked(it) })
                    ?.getDescriptor(fns)?.let { remapper.map(it).toString() }
            }
        }
    }

}

/**
 * A remap-time extension hook. Get one via [AsmExtensionFactory] — a fresh
 * instance per [AsmRemapper.remap] call, so per-jar state just lives in fields.
 *
 * Lifecycle per remap:
 *  1. [analyze] + [analyzeResource] — once per input class (raw, pre-remap
 *     node) / once per input resource — collect state across the whole jar
 *     (mixin targets, refmap entries, configs…)
 *  2. [finishAnalysis] — after every input entry was analyzed
 *  3. [postApply] — per output class, after `ClassRemapper` + `MetadataAdapter`
 *     — the node carries targetNs names; mutate freely (annotation rewrites,
 *     extra methods, …)
 *  4. [transformResource] — per resource, after the standard manifest /
 *     services / access-file fixups — replace contents (merge a stale refmap…)
 *  5. [resources] — extra jar entries to append (refmap JSON, configs…)
 */
