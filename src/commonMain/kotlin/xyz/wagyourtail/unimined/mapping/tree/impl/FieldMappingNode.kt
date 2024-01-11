package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.FieldProperty
import xyz.wagyourtail.unimined.mapping.tree.FieldPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedMemberVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class FieldMappingNode internal constructor(parent: AbstractMappingNode<*>):
    AbstractNamespacedMappingNode<FieldPropertyView>(parent), FieldProperty {
    override val type = ElementType.FIELD

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitField(namespaces)
    }

    override fun asVisitableIntl() = object : StackedMemberVisitor(this) {}

    override fun setName(namespace: String, name: String?) {
        val existing = this[namespace] ?: ""
        val desc = existing.substringAfter(';', "")
        this[namespace] = "$name;$desc"
    }

    override fun setDescriptor(namespace: String, desc: String?) {
        val existing = this[namespace] ?: ""
        val name = existing.substringBefore(';')
        this[namespace] = "$name;$desc"
    }

    override fun getName(namespace: String): String? {
        return this[namespace]?.substringBefore(';')
    }

    override fun hasDescriptor(): Boolean {
        for (v in this.namespaces.values) {
            if (v?.contains(';') == true) return true
        }
        return false
    }

    override fun getDescriptor(namespace: String): String? {
        val desc = this[namespace]?.substringAfter(';', "")
        if (desc == "" || desc == null) {
            // attempt to calculate from other namespaces
            for (ns in root.completedClasses.filter { it in namespaces }) {
                if (this[ns]?.contains(';') == true) {
                    return root.remapFieldDesc(ns, namespace, this[ns]!!.substringAfter(';'))
                }
            }
        }
        return desc
    }

}