package xyz.wagyourtail.unimined.mapping.visitor.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.tree.AccessPropertyView
import xyz.wagyourtail.unimined.mapping.tree.AnnotationPropertyView
import xyz.wagyourtail.unimined.mapping.tree.impl.*

open class StackedMemberVisitor(node: AbstractMappingNode<*>): StackedVisitor(node) {

    override fun visitSignature(names: Map<String, String?>): StackedVisitor {
        val signature = node.get<SignatureMappingNode>(ElementType.SIGNATURE).firstOrNull() ?: SignatureMappingNode(node).also {
            node[ElementType.SIGNATURE].add(it)
        }
        return signature.asVisitableIntl()
    }

    override fun visitComment(names: Map<String, String?>): StackedVisitor {
        val comment = node.get<CommentMappingNode>(ElementType.COMMENT).firstOrNull() ?: CommentMappingNode(node).also {
            node[ElementType.COMMENT].add(it)
        }
        return comment.asVisitableIntl()
    }

    override fun visitAnnotation(
        action: AnnotationPropertyView.Action,
        key: String,
        value: String?,
        vararg namespaces: String
    ): StackedVisitor {
        // get annotation by key & first ns
        val it = namespaces.iterator()
        val key = it.next()
        val srcNs = it.next()
        val namespaces = it.asSequence().toList().toTypedArray()
        for (annotation in node.get<AnnotationMappingNode>(ElementType.ANNOTATION)) {
            if (annotation.key == key && annotation.srcNs == srcNs) {
                annotation.namespaces.addAll(namespaces.filter { it !in annotation.namespaces })
                return annotation.asVisitableIntl()
            }
        }
        return AnnotationMappingNode(node, action, key, value, srcNs, *namespaces).asVisitableIntl()
    }

    override fun visitAccess(
        action: AccessPropertyView.Action,
        access: AccessFlag,
        vararg namespaces: String
    ): StackedVisitor? {
        for (access in node.get<AccessMappingNode>(ElementType.ACCESS)) {
            if ((access.action == action) && (access.access == access.access)) {
                access.namespaces.addAll(namespaces.filter { it !in access.namespaces })
                return access.asVisitableIntl()
            }
        }
        return AccessMappingNode(node, action, access, *namespaces).asVisitableIntl()
    }

}