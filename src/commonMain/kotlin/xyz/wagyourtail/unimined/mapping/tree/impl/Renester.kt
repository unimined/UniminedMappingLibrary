package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.*

private fun MutableMappingTree.fixNest(target: ClassMapping, srcNs: Namespace, targetNs: Namespace): InternalName? {
    val srcName = target.names[srcNs] ?: return target.names[targetNs]
    if ('$' !in srcName.value) return target.names[targetNs]
    val parent = InternalName.unchecked(srcName.value.substringBeforeLast('$'))
    val parentNode = getClass(srcNs, parent) ?: return target.names[targetNs]
    val parentDstName = fixNest(parentNode, srcNs, targetNs) ?: return target.names[targetNs]
    val dstName = target.names[targetNs] ?: srcName
    val clsName = if ('$' !in dstName.value) dstName.value.substringAfterLast('/') else dstName.value.substringAfterLast('$')
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
