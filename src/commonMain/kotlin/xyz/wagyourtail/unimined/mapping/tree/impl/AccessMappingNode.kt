package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.tree.AccessProperty
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class AccessMappingNode internal constructor(
    parent: AbstractMappingNode<*>,
    override var action: AccessPropertyView.Action,
    override val access: AccessFlag,
    vararg dstNs: String
) : AbstractMappingNode<AccessPropertyView>(parent), AccessProperty {
    override val type = ElementType.ACCESS
    val namespaces = mutableSetOf<String>()

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitAccess(action, access, *namespaces.toTypedArray())
    }

    override fun asVisitableIntl() = object : StackedVisitor(this) {}

    override fun compareTo(other: AbstractMappingNode<*>): Int {
        if (other !is AccessMappingNode) throw IllegalArgumentException("Cannot compare AccessMappingNode to ${other::class.simpleName}")
        return access.compareTo(other.access)
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