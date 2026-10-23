package xyz.wagyourtail.unimined.mapping.remapper.asm

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.Type as JvmType

/**
 * Applies [xyz.wagyourtail.unimined.mapping.element.metadata.BytecodeTransform]
 * metadata to a **post-remap** [ClassNode] — all names and injected values are
 * resolved into `targetNs`.
 *
 * [NsBoundMetadata] values are relative to their [NsBoundMetadata.baseNs]; they
 * are mapped to `targetNs` through [MappingTree.map] before application.
 *
 * Unpick elements (`ConstantElement`/`TargetElement`/`ExpressionElement`) are
 * intentionally skipped — constant rewriting is phase B. `SyntheticBridgeElement`
 * is handled by [BridgeEmitter]; parameter/local names by [LocalVarRemapper].
 */
object MetadataAdapter {

    private val LOGGER = KotlinLogging.logger {}

    fun apply(
        node: ClassNode,
        cls: ClassMapping?,
        tree: MappingTree,
        targetNs: Namespace,
        options: Options = Options(),
    ) {
        if (cls == null) return
        val meta = cls.metadata.toList()

        applyAccess(node, ElementType.CLASS, meta)
        applySignature(node, meta, tree, targetNs)
        applyAnnotations(node, meta, tree, targetNs)
        applyInterfaces(node, meta, tree, targetNs)
        applySeal(node, meta, tree, targetNs)
        applyInnerClasses(node, meta, targetNs)
        EnumExtender.apply(node, meta, tree, targetNs)

        for (field in node.fields) {
            // fields can be split across rows — aggregate all matching rows
            val fms = with(tree) {
                cls.fields.filter {
                    it.entries[targetNs]?.let { e -> e.name == UnqualifiedName.unchecked(field.name) && e.descriptor == FieldDescriptor.unchecked(field.desc) } == true
                }
            }
            for (fm in fms) applyMember(field, fm, tree, targetNs)
        }

        for (method in node.methods.toTypedArray()) { // snapshot — BridgeEmitter appends
            val desc = MethodDescriptor.unchecked(method.desc)
            // methods can be split across rows — apply metadata from all of them
            val mms = with(tree) { cls.getMethods(targetNs, UnqualifiedName.unchecked(method.name), desc) }
            for (mm in mms) {
                applyMember(method, mm, tree, targetNs)
                applyExceptions(method, mm, tree, targetNs)
                BridgeEmitter.emit(node, method, mm, targetNs)
            }
            val mm = mms.firstOrNull()
            // wildcards group params/lvars by descriptor — consulted for LV names too
            val wildcards = with(tree) { cls.getWildcards(WildcardType.METHOD, targetNs, desc) }
            LocalVarRemapper.apply(method, mm, targetNs, options, wildcards)
        }
    }

    private fun applyAccess(node: ClassNode, type: ElementType, meta: List<MetadataElement>) {
        node.access = applyAccess(node.access, type, meta)
    }

    private fun applyMember(member: Any, mapping: Any?, tree: MappingTree, targetNs: Namespace) {
        if (mapping == null) return
        val meta = when (mapping) {
            is FieldMapping -> mapping.metadata.toList()
            is MethodMapping -> mapping.metadata.toList()
            else -> return
        }
        val type = if (member is FieldNode) ElementType.FIELD else ElementType.METHOD
        when (member) {
            is FieldNode -> {
                member.access = applyAccess(member.access, type, meta)
                meta.filterIsInstance<SignatureElement>().lastOrNull()?.let {
                    member.signature = mapSignature(it, tree, targetNs)
                }
                applyAnnotations(member, meta, tree, targetNs)
            }
            is MethodNode -> {
                member.access = applyAccess(member.access, type, meta)
                meta.filterIsInstance<SignatureElement>().lastOrNull()?.let {
                    member.signature = mapSignature(it, tree, targetNs)
                }
                applyAnnotations(member, meta, tree, targetNs)
            }
        }
    }

    private fun applyAccess(access: Int, type: ElementType, meta: List<MetadataElement>): Int {
        val elements = meta.filterIsInstance<AccessElement>()
        if (elements.isEmpty()) return access
        val original = AccessFlag.of(type, access)
        val flags = original.toMutableSet()
        for (el in elements) el.apply(flags)
        return AccessFlag.toInt(flags)
    }

    private fun applySignature(node: ClassNode, meta: List<MetadataElement>, tree: MappingTree, targetNs: Namespace) {
        meta.filterIsInstance<SignatureElement>().lastOrNull()?.let {
            node.signature = mapSignature(it, tree, targetNs)
        }
    }

    private fun mapSignature(el: SignatureElement, tree: MappingTree, targetNs: Namespace): String =
        resolve(el, tree, targetNs) { it.value }.toString()

    private fun applyInterfaces(node: ClassNode, meta: List<MetadataElement>, tree: MappingTree, targetNs: Namespace) {
        for (el in meta.filterIsInstance<InterfaceElement>()) {
            val name = internalNameOf(resolve(el, tree, targetNs) { it.name })
            when (el.type) {
                AddRemove.ADD -> if (name !in node.interfaces) node.interfaces.add(name)
                AddRemove.REMOVE -> node.interfaces.remove(name)
            }
        }
    }

    private fun applySeal(node: ClassNode, meta: List<MetadataElement>, tree: MappingTree, targetNs: Namespace) {
        for (el in meta.filterIsInstance<SealElement>()) {
            val permitted = node.permittedSubclasses ?: mutableListOf()
            when (el.type) {
                AddRemoveClear.CLEAR -> node.permittedSubclasses = mutableListOf()
                else -> {
                    val name = el.name?.let { resolve(el, tree, targetNs) { _ -> it } }
                    if (name == null) {
                        LOGGER.warn { "SealElement ${el.type} without a name on ${node.name}, skipping" }
                        continue
                    }
                    when (el.type) {
                        AddRemoveClear.ADD -> if (name.value !in permitted) {
                            node.permittedSubclasses = (permitted + name.value).toMutableList()
                        }
                        AddRemoveClear.REMOVE -> node.permittedSubclasses =
                            permitted.filter { it != name.value }.toMutableList()
                        else -> {}
                    }
                }
            }
        }
    }

    private fun applyInnerClasses(node: ClassNode, meta: List<MetadataElement>, targetNs: Namespace) {
        for (el in meta.filterIsInstance<InnerClassElement>()) {
            val (innerName, outer) = el.names[targetNs] ?: continue
            val outerName = outer?.getParts()?.first?.getInternalName()?.value
            val icn = node.innerClasses.firstOrNull { it.name == node.name }
            val access = applyAccess(icn?.access ?: 0, ElementType.INNER_CLASS, el.metadata.toList())
            node.innerClasses.remove(icn)
            node.innerClasses.add(
                InnerClassNode(node.name, outerName, if (el.type == InnerType.ANONYMOUS) null else innerName, access)
            )
        }
    }

    private fun applyExceptions(method: MethodNode, mm: MethodMapping, tree: MappingTree, targetNs: Namespace) {
        for (el in mm.exceptions) {
            val name = resolve(el, tree, targetNs) { it.exception }.value
            when (el.type) {
                AddRemove.ADD -> if (name !in method.exceptions) method.exceptions.add(name)
                AddRemove.REMOVE -> method.exceptions.remove(name)
            }
        }
    }

    private fun applyAnnotations(host: Any, meta: List<MetadataElement>, tree: MappingTree, targetNs: Namespace) {
        val visible = when (host) {
            is ClassNode -> host.visibleAnnotations ?: mutableListOf<AnnotationNode>().also { host.visibleAnnotations = it }
            is FieldNode -> host.visibleAnnotations ?: mutableListOf<AnnotationNode>().also { host.visibleAnnotations = it }
            is MethodNode -> host.visibleAnnotations ?: mutableListOf<AnnotationNode>().also { host.visibleAnnotations = it }
            else -> return
        }
        val invisible = when (host) {
            is ClassNode -> host.invisibleAnnotations ?: mutableListOf<AnnotationNode>().also { host.invisibleAnnotations = it }
            is FieldNode -> host.invisibleAnnotations ?: mutableListOf<AnnotationNode>().also { host.invisibleAnnotations = it }
            is MethodNode -> host.invisibleAnnotations ?: mutableListOf<AnnotationNode>().also { host.invisibleAnnotations = it }
            else -> return
        }
        for (el in meta.filterIsInstance<AnnotationElement>()) {
            val ann = toAnnotationNode(resolve(el, tree, targetNs) { it.annotation })
            val list = if (ann.second) invisible else visible
            when (el.type) {
                AddRemoveModify.ADD -> if (list.none { it.desc == ann.first.desc }) list.add(ann.first)
                AddRemoveModify.REMOVE -> list.removeIf { it.desc == ann.first.desc }
                AddRemoveModify.MODIFY -> {
                    val existing = list.firstOrNull { it.desc == ann.first.desc }
                    if (existing == null) {
                        list.add(ann.first)
                    } else {
                        val merged = (existing.values ?: mutableListOf()).toMutableList()
                        val adds = ann.first.values ?: emptyList()
                        var i = 0
                        while (i < adds.size) {
                            val name = adds[i] as String
                            val value = adds[i + 1]
                            val idx = merged.indexOfFirst { it == name }
                            if (idx >= 0 && idx + 1 < merged.size) merged[idx + 1] = value
                            else {
                                merged.add(name)
                                merged.add(value)
                            }
                            i += 2
                        }
                        existing.values = merged
                    }
                }
            }
        }
    }

    /** Extracts the erased internal name from a [ClassTypeSignature] (`Lpkg/Outer$Inner<T>;` → `pkg/Outer$Inner`). */
    internal fun internalNameOf(sig: ClassTypeSignature): String {
        val (pkg, simple, suffixes) = sig.getParts()
        val sb = StringBuilder()
        if (pkg != null) sb.append(pkg.value) // package spec already ends in '/'
        sb.append(simple.getParts().first.value)
        for (suffix in suffixes) sb.append('$').append(suffix.getParts().getParts().first.value)
        return sb.toString()
    }

    /**
     * Converts a UMF [Annotation] to an ASM [AnnotationNode] paired with its
     * visibility flag (`true` = runtime-invisible).
     */
    internal fun toAnnotationNode(ann: Annotation): Pair<AnnotationNode, Boolean> {
        val (obj, elements, invisible) = ann.getParts()
        val node = AnnotationNode("L${obj.getInternalName().value};")
        elements?.getParts()?.forEach { el ->
            val (name, value) = el.getParts()
            node.visit(name.value.unescape(), toAnnotationValue(value))
        }
        node.visitEnd()
        return node to (invisible != null)
    }

    private fun toAnnotationValue(value: xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.AnnotationElementValue): Any = when {
        value.isAnnotation() -> toAnnotationNode(value.getAnnotation()).first
        value.isArrayConstant() -> value.getArrayConstant().getParts()?.getParts()?.map { toAnnotationValue(it) } ?: emptyList<Any>()
        value.isEnumConstant() -> value.getEnumConstant().getParts().let { (obj, name) ->
            arrayOf("L${obj.getInternalName().value};", name.unescape())
        }
        value.isClassConstant() -> Type.getType("L${value.getClassConstant().value.getInternalName().value};")
        else -> value.getConstant().getValue()
            ?: throw IllegalArgumentException("unsupported annotation value: $value")
    }

    /**
     * Resolves an [NsBoundMetadata] value into `targetNs`: maps the element's
     * `baseNs`-relative value through [MappingTree.map]. Elements already in
     * `targetNs` (or whose `baseNs` isn't a tree namespace) pass through
     * unchanged.
     */
    internal fun <T : MetadataElement, R : JvmType> resolve(
        el: T,
        tree: MappingTree,
        targetNs: Namespace,
        value: (T) -> R,
    ): R {
        val v = value(el)
        val base = (el as? NsBoundMetadata)?.baseNs ?: return v
        if (base == targetNs || base !in tree.namespaces) return v
        return tree.map(base, targetNs, v)
    }

}
