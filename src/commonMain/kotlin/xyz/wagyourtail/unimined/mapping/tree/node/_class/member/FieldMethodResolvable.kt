package xyz.wagyourtail.unimined.mapping.tree.node._class.member

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvableEntry
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode
import xyz.wagyourtail.unimined.mapping.visitor.MemberVisitor

abstract class FieldMethodResolvable<T: FieldMethodResolvable<T, U>, U: MemberVisitor<U>>(parent: ClassNode, val create: (ClassNode) -> T) : AbstractFieldMethodNode<U>(parent),
    LazyResolvableEntry<T, U> {
    val LOGGER = KotlinLogging.logger {  }

    fun MemoryMappingTree.getDescriptor(namespace: Namespace): FieldOrMethodDescriptor? {
        if (descs.isEmpty()) return null
        if (namespace in descs) {
            return descs[namespace]
        }
        val fromNs = descs.keys.first()
        return mapDescriptor(fromNs, namespace, descs[fromNs]!!)
    }

    @Suppress("UNCHECKED_CAST")
    fun doMerge(target: T) {
        acceptInner(target as U, root.namespaces)
    }

    override fun merge(element: T): T? {
        // newly created, always merge
        if (element.names.isEmpty()) {
            element.setNames(names)
            element.setDescriptors(descs)
            doMerge(element)
            return element
        }

        var matched = false
        var notMatched = false
        for ((ns, nameVal) in names) {
            val elementNameVal = element.names[ns] ?: continue
            if (elementNameVal == nameVal) {
                matched = true
            } else {
                notMatched = true
            }
        }

        if (!matched) {
            // dont merge
            return null
        }

        // warn if a method name is getting implicitly overridden
        if (notMatched) {
            LOGGER.warn {
                """
                    Joining different names, second will take priority
                    $element
                    $this
                """.trimIndent()
            }
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
                element.doMerge(new)
                doMerge(new)
                new
            }
        } else {
            if (descs.isNotEmpty()) {
                // merge, but also create a new one with descs
                val new = create(parent as ClassNode)
                new.setNames(element.names)
                new.setNames(names)
                new.setDescriptors(descs)
                element.doMerge(new)
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