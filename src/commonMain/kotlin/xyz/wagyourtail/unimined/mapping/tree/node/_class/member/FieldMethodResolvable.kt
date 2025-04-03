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
        return map(fromNs, namespace, descs[fromNs]!!)
    }

    @Suppress("UNCHECKED_CAST")
    fun doMerge(target: T) {
        acceptInner(target as U, root.namespaces)
    }

    open fun namesMatch(element: T): Pair<Boolean, Boolean> {
        var namesMatched = false
        var differentNames = false
        for ((ns, nameVal) in names) {
            val elementNameVal = element.names[ns] ?: continue
            if (elementNameVal == nameVal) {
                namesMatched = true
            } else {
                differentNames = true
            }
        }
        return namesMatched to differentNames
    }

    override fun merge(element: T): Boolean {
        val (namesMatched, differentNames) = namesMatch(element)

        if (!namesMatched) {
            return false
        }

        if (element.hasDescriptor()) {
            if (descs.isNotEmpty()) {
                // Both have descriptors
                val descNs = element.descs.keys.intersect(descs.keys).firstOrNull() ?: descs.keys.first()
                if (element.getDescriptor(descNs) == descs[descNs]) {
                    // same descriptor, merge

                    // warn if a method name is getting implicitly overridden
                    if (differentNames) {
                        LOGGER.info {
                            """
                                Joining different names, second will take priority
                                $element
                                $this
                            """.trimIndent()
                        }
                    }

                    element.setNames(names)
                    element.setDescriptors(descs)
                    doMerge(element)
                    return true
                } else {
                    return false
                }
            } else {
                mergeDescMismatch(element, this as T)
                return false
            }
        } else {
            if (descs.isNotEmpty()) {
                mergeDescMismatch(this as T, element)
                return false
            } else {
                // merge

                // warn if a method name is getting implicitly overridden
                if (differentNames) {
                    LOGGER.info {
                        """
                            Joining different names, second will take priority
                            $element
                            $this
                        """.trimIndent()
                    }
                }

                element.setNames(names)
                doMerge(element)
                return true
            }
        }
    }

    companion object {
        fun <T: FieldMethodResolvable<T, U>, U: MemberVisitor<U>> mergeDescMismatch(hasDesc: T, noDesc: T) {
            hasDesc.setNames(noDesc.names.filter { it.key !in hasDesc.names })
            noDesc.setNames(hasDesc.names.filter { it.key !in noDesc.names })
//            noDesc.setNames(noDesc.names)
            noDesc.doMerge(hasDesc)
        }
    }

}