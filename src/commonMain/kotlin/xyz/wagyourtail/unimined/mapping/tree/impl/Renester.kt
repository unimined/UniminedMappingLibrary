package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.InnerType
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName

private fun MutableMappingTree.fixNest(target: ClassMapping, srcNs: Namespace, targetNs: Namespace): InternalName? {
    val srcName = target.names[srcNs] ?: return target.names[targetNs]
    if ('$' !in srcName.value) return target.names[targetNs]
    val parent = InternalName.unchecked(srcName.value.substringBeforeLast('$'))
    // identity probe — must not flat-alias `az` into a `q$az` row
    val parentNode = (this as? MappingTreeData)?.getClassExact(srcNs, parent)
        ?: getClass(srcNs, parent) ?: return target.names[targetNs]
    val parentDstName = fixNest(parentNode, srcNs, targetNs) ?: return target.names[targetNs]
    val dstName = target.names[targetNs] ?: srcName
    // leaf may still carry a feather `__` inner marker when the source
    // was read without separator normalization — the inner simple name
    // sits after the last marker (`Profiler__Result` -> `Result`, not
    // the whole leaf, which would produce `Profiler$Profiler__Result`)
    val clsName = dstName.value.substringAfterLast('/')
        .substringAfterLast('$').substringAfterLast("__")
    val newName = InternalName.unchecked(parentDstName.value + '$' + clsName)

    val cls = clazz(mapOf(srcNs to srcName, targetNs to newName))

    val cname = newName.value.substringAfterLast('$')
    val (type, innerName) = if (cname.toIntOrNull() != null) {
        InnerType.ANONYMOUS to cname
    } else if (cname.first().isDigit()) {
        InnerType.LOCAL to Regex("\\d+(.+)").find(cname)!!.groupValues[1]
    } else {
        InnerType.INNER to cname
    }
    cls.addMetadata(InnerClassElement(type, mapOf(targetNs to (innerName to FullyQualifiedName(ObjectType(parentDstName), null)))))

    return newName
}

/**
 * fixes javac names when mappings are missing for class in a namespace.
 */
fun MutableMappingTree.renest(srcNs: Namespace, namespaces: Set<Namespace>, fixInner: Boolean = true) {
    for (cls in classes) {
        val srcName = cls.names[srcNs] ?: continue
        if ('$' !in srcName.value) continue
        for (namespace in namespaces) {
            fixNest(cls, srcNs, namespace)
        }
    }
}

fun MutableMappingTree.renest(srcNs: String, vararg namespaces: String, fixInner: Boolean = true) {
    renest(Namespace(srcNs), namespaces.map { Namespace(it) }.toSet(), fixInner)
}
