package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.tree.AnnotationProperty
import xyz.wagyourtail.unimined.mapping.tree.AnnotationPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class AnnotationMappingNode internal constructor(
    parent: AbstractMappingNode<*>,
    override var action: AnnotationPropertyView.Action,
    override var key: String,
    override var value: String?,
    override var srcNs: String,
    vararg dstNs: String
): AbstractMappingNode<AnnotationPropertyView>(parent), AnnotationProperty {
    override val type = ElementType.ANNOTATION
    val namespaces = mutableSetOf(srcNs, *dstNs)

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitAnnotation(action, key, value, *namespaces.toTypedArray())
    }

    override fun asVisitableIntl(): StackedVisitor {
        return object : StackedVisitor(this) {}
    }

    override fun compareTo(other: AbstractMappingNode<*>): Int {
        if (other !is AnnotationMappingNode) throw IllegalArgumentException("Cannot compare AnnotationMappingNode to ${other::class.simpleName}")
        val action = action.compareTo(other.action)
        if (action != 0) return action
        val key = key.compareTo(other.key)
        if (key != 0) return key
        val value = value?.compareTo(other.value ?: "") ?: -1
        if (value != 0) return value
        return srcNs.compareTo(other.srcNs)
    }

    fun getAnnotation(namespace: String): String {
        if (namespace !in namespaces) {
            throw IllegalArgumentException("namespace $namespace not found in annotation")
        }
        return when (action) {
            AnnotationPropertyView.Action.ADD, AnnotationPropertyView.Action.MODIFY -> {
                // remap annotation
                if (namespace != srcNs) {
                    root.remapAnnotation(namespace, srcNs, "@$key$value")
                } else {
                    "@$key$value"
                }
            }
            AnnotationPropertyView.Action.REMOVE -> {
                root.remapAnnotation(namespace, srcNs, "@$key()")
            }
        }
    }

    override fun set(namespace: String, value: Boolean) {
        if (namespace !in namespaces && value) {
            namespaces.add(namespace)
        } else if (namespace in namespaces && !value) {
            namespaces.remove(namespace)
        }
    }

    override fun get(namespace: String): Boolean {
        return namespace in namespaces
    }

}