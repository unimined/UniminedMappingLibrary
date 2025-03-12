package xyz.wagyourtail.unimined.mapping.visitor.fixes

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.visitor.use

private fun AbstractMappingTree.fixNest(target: ClassNode, srcNs: Namespace, targetNs: Namespace): InternalName? {
    val srcName = target.getName(srcNs) ?: return target.getName(targetNs)
    if ('$' !in srcName.value) return target.getName(targetNs)
    val parent = InternalName.unchecked(srcName.value.substringBeforeLast('$'))
    val parentNode = getClass(srcNs, parent) ?: return target.getName(targetNs)
    val parentDstName = fixNest(parentNode, srcNs, targetNs) ?: return target.getName(targetNs)
    val dstName = target.getName(targetNs) ?: srcName
    val clsName = if ('$' !in dstName.value) dstName.value.substringAfterLast('/') else dstName.value.substringAfterLast('$')
    val newName = InternalName.unchecked(parentDstName.value + '$' + clsName)

    visitClass(mapOf(srcNs to srcName, targetNs to newName))?.use {

        val cname = newName.value.substringAfterLast('$')
        val (type, innerName) = if (cname.toIntOrNull() != null) {
            InnerClassNode.InnerType.ANONYMOUS to cname
        } else if (cname.first().isDigit()) {
            InnerClassNode.InnerType.LOCAL to Regex("\\d+(.+)").find(cname)!!.groupValues[1]
        } else {
            InnerClassNode.InnerType.INNER to cname
        }

        visitInnerClass(type, mapOf(targetNs to (innerName to FullyQualifiedName(ObjectType(parentDstName), null))))?.visitEnd()
    }

    return newName
}

/**
 * fixes javac names when mappings are missing for class in a namespace.
 */
fun AbstractMappingTree.renest(srcNs: Namespace, namespaces: Set<Namespace>, fixInner: Boolean = true) {
    for (cls in classesIter()) {
        val srcName = cls.first[srcNs] ?: continue
        if ('$' !in srcName.value) continue
        for (namespace in namespaces) {
            fixNest(cls.second(), srcNs, namespace)
        }
    }

}

fun AbstractMappingTree.renest(srcNs: String, vararg namespaces: String, fixInner: Boolean = true) {
    renest(Namespace(srcNs), namespaces.map { Namespace(it) }.toSet(), fixInner)
}