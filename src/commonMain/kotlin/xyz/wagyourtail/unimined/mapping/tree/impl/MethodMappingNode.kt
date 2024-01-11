package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.MethodProperty
import xyz.wagyourtail.unimined.mapping.tree.MethodPropertyView
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedMemberVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class MethodMappingNode internal constructor(parent: AbstractMappingNode<*>):
    AbstractNamespacedMappingNode<MethodPropertyView>(parent), MethodProperty {
    override val type = ElementType.METHOD

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

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitMethod(namespaces)
    }

    override fun asVisitableIntl(): StackedVisitor {
        return object : StackedMemberVisitor(this) {

            override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<String, String?>): StackedVisitor? {
                // find existing parameter by index or lvOrd
                for (p in parameters) {
                    if (p.index == index || p.lvOrdinal == lvOrd) {
                        return (p as ParameterMappingNode).asVisitableIntl()
                    }
                }
                // no existing parameter, create new one
                val paramNode = ParameterMappingNode(this@MethodMappingNode, index, lvOrd)
                parameters.add(paramNode)
                return paramNode.asVisitableIntl()
            }

            override fun visitVariable(lvOrd: Int, startOp: Int?, names: Map<String, String?>): StackedVisitor? {
                // find existing variable by lvOrd & startop (if provided must match)
                for (v in locals) {
                    if (v.lvOrdinal == lvOrd && (startOp == null || v.startOpIdx == startOp)) {
                        return (v as VariableMappingNode).asVisitableIntl()
                    }
                }
                // no existing variable, create new one
                val varNode = VariableMappingNode(this@MethodMappingNode, lvOrd, startOp)
                locals.add(varNode)
                return varNode.asVisitableIntl()
            }

        }
    }

}