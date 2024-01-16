package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MemberVisitor

abstract class FieldMethodResolvable<T: FieldMethodResolvable<T, U>, U: MemberVisitor<U>>(parent: ClassNode, val create: (ClassNode) -> T) : AbstractFieldMethodNode<U>(parent), LazyResolvableEntry<T, U> {

    fun MappingTree.getDescriptor(namespace: Namespace): FieldOrMethodDescriptor? {
        if (descs.isEmpty()) return null
        if (namespace in descs) {
            return descs[namespace]
        }
        val fromNs = descs.keys.first()
        return mapDescriptor(fromNs, namespace, descs[fromNs]!!)
    }

    @Suppress("UNCHECKED_CAST")
    fun doMerge(target: T) {
        acceptInner(target as U, false)
    }

    override fun merge(element: T): T? {
        // newly created, always merge
        if (element.names.isEmpty()) {
            element.setNames(names)
            element.setDescriptors(descs)
            doMerge(element)
            return element
        }
        val nameNs = names.keys.intersect(element.names.keys)
        if (nameNs.isEmpty()) {
            // dont merge
            return null
        }
        // check the values match
        if (nameNs.any { element.names[it] != names[it] }) {
            // dont merge
            return null
        }
        return if (element.hasDescriptor()) {
            if (descs.isNotEmpty()) {
                val descNs = descs.keys.first()
                if (element.getDescriptor(descNs) == descs[descNs]) {
                    // same descriptor, merge
                    element.setNames(names)
                    element.setDescriptors(descs)
                    doMerge(element)
                    element
                } else {
                    // dont merge
                    null
                }
            } else {
                // merge, but also create new unmerged one
                val new = create(parent as ClassNode)
                new.setNames(names)
                element.setNames(names)
                doMerge(element)
                doMerge(new)
                new
            }
        } else {
            if (descs.isNotEmpty()) {
                // merge, but also create a new one without descs
                val new = create(parent as ClassNode)
                new.setNames(element.names)
                new.setNames(names)
                new.setDescriptors(descs)
                element.setNames(names)
                doMerge(element)
                doMerge(new)
                new
            } else {
                // merge
                element.setNames(names)
                doMerge(element)
                element
            }
        }
    }

}