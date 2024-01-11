package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.SignatureProperty
import xyz.wagyourtail.unimined.mapping.tree.SignaturePropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class SignatureMappingNode internal constructor(parent: AbstractMappingNode<*>):
    AbstractNamespacedMappingNode<SignaturePropertyView>(parent), SignatureProperty {
    override val type = ElementType.SIGNATURE

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitSignature(namespaces)
    }

    override fun asVisitableIntl(): StackedVisitor {
        return object : StackedVisitor(this) {}
    }
}