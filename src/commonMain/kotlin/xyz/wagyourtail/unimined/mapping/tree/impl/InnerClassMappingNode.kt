package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.tree.InnerClassProperty
import xyz.wagyourtail.unimined.mapping.tree.InnerClassPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class InnerClassMappingNode internal constructor(
    parent: AbstractMappingNode<*>,
    override var innerType: InnerClassPropertyView.InnerType
): AbstractNamespacedMappingNode<InnerClassPropertyView>(parent), InnerClassProperty {

    override val type = ElementType.INNER_CLASS
    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitInnerClass(innerType, namespaces)
    }

    override fun asVisitableIntl() = object : StackedVisitor(this) {

        override fun visitAccess(
            action: AccessPropertyView.Action,
            access: AccessFlag,
            vararg namespaces: String
        ): StackedVisitor {
            // find existing based on action/access
            for (node in this@InnerClassMappingNode.access) {
                if (node.action == action && node.access == access) {
                    return (node as AccessMappingNode).asVisitableIntl()
                }
            }
            // create new
            val node = AccessMappingNode(this@InnerClassMappingNode, action, access)
            this@InnerClassMappingNode.access.add(node)
            return node.asVisitableIntl()
        }

    }

    override fun setName(namespace: String, name: String?) {
        val desc = namespaces[namespace]?.substringAfter(";", "") ?: ""
        namespaces[namespace] = "$name;$desc"
    }

    override fun setDescriptor(namespace: String, desc: String?) {
        val name = namespaces[namespace]?.substringBefore(";", "") ?: ""
        namespaces[namespace] = "$name;$desc"
    }

    override fun getName(namespace: String): String? {
        return namespaces[namespace]?.substringBefore(";", "")
    }

    override fun getDescriptor(namespace: String): String? {
        return namespaces[namespace]?.substringAfter(";", "")
    }

}