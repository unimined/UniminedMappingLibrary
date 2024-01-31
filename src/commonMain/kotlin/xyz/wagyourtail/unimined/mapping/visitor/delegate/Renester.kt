package xyz.wagyourtail.unimined.mapping.visitor.delegate

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.ClassNode

private fun MappingTree.fixNest(target: ClassNode, srcNs: Namespace, targetNs: Namespace): InternalName? {
    val srcName = target.getName(srcNs) ?: return target.getName(targetNs)
    if ('$' !in srcName.value) return target.getName(targetNs)
    val parent = InternalName.unchecked(srcName.value.substringBeforeLast('$'))
    val parentNode = getClass(srcNs, parent) ?: return target.getName(targetNs)
    val parentDstName = fixNest(parentNode, srcNs, targetNs) ?: return target.getName(targetNs)
    val dstName = target.getName(targetNs) ?: srcName
    val clsName = if ('$' !in dstName.value) dstName.value.substringAfterLast('/') else dstName.value.substringAfterLast('$')
    val newName = InternalName.unchecked(parentDstName.value + '$' + clsName)
    target.setNames(mapOf(targetNs to newName))
    return newName
}

/**
 * fixes javac names when mappings are missing for class in a namespace.
 */
fun MappingTree.renest(srcNs: Namespace, namespaces: Set<Namespace>) {
    for (cls in classes) {
        val srcName = cls.getName(srcNs) ?: continue
        if ('$' !in srcName.value) continue
        for (namespace in namespaces) {
            fixNest(cls, srcNs, namespace)
        }
    }

}

fun MappingTree.renest(srcNs: String, vararg namespaces: String) {
    renest(Namespace(srcNs), namespaces.map { Namespace(it) }.toSet())
}