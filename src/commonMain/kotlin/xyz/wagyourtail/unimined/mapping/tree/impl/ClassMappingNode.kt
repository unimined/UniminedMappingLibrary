package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.*
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedMemberVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

class ClassMappingNode internal constructor(parent: AbstractMappingNode<*>):
    AbstractNamespacedMappingNode<ClassPropertyView>(parent), ClassProperty {
    override val type = ElementType.CLASS

    /**
     * cache for faster lookups
     */
    private var fieldCache =
        mutableMapOf<String, MutableMap<String, MutableSet<FieldMappingNode>>>().withDefault { ns ->
            val mutable = mutableMapOf<String, MutableSet<FieldMappingNode>>().withDefault { mutableSetOf() }
            for (f in fields) {
                if (f[ns] != null) {
                    mutable.getValue(f[ns]!!).add(f as FieldMappingNode)
                    // without desc
                    if (f[ns]!!.contains(';')) {
                        val noDesc = f[ns]!!.substringBefore(';')
                        mutable.getValue(noDesc).add(f)
                    }
                }
            }
            mutable
        }

    override fun getField(namespace: String, name: String, desc: String?): FieldProperty? {
        val nameWithDesc = if (desc == null) name else "$name;$desc"
        val value = fieldCache.getValue(namespace)[nameWithDesc]
        if (value == null) {
            // try without desc
            if (desc != null) {
                val value2 = fieldCache.getValue(namespace)[name]
                if (value2 != null && value2.any { it[namespace]?.substringBefore(';') == name }) {
                    fieldCache[namespace]!![nameWithDesc] = value2
                    return value2.first {
                        it[namespace]?.substringBefore(';') == name && (!it.hasDescriptor() || it.getDescriptor(
                            namespace
                        ) == desc)
                    }
                }
            }
            // cache miss, recalculate this value
            for (f in fields) {
                if (f[namespace] == name && (!f.hasDescriptor() || f.getDescriptor(namespace) == desc)) {
                    fieldCache[namespace]!!.getValue(nameWithDesc).add(f as FieldMappingNode)
                    return f
                }
            }
        }
        if (value != null && value.size > 1) {
            throw IllegalStateException("found multiple matching fields for $namespace: $nameWithDesc")
        }
        return value?.first()
    }

    /**
     * cache for faster lookups
     */
    private var methodCache =
        mutableMapOf<String, MutableMap<String, MutableSet<MethodMappingNode>>>().withDefault { ns ->
            val mutable = mutableMapOf<String, MutableSet<MethodMappingNode>>().withDefault { mutableSetOf() }
            for (m in methods) {
                if (m[ns] != null) {
                    mutable.getValue(m[ns]!!).add(m as MethodMappingNode)
                    // without desc
                    if (m[ns]!!.contains(';')) {
                        val noDesc = m[ns]!!.substringBefore(';')
                        mutable.getValue(noDesc).add(m)
                    }
                }
            }
            mutable
        }

    override fun getMethod(namespace: String, name: String, desc: String?): MethodProperty? {
        val nameWithDesc = if (desc == null) name else "$name;$desc"
        val value = methodCache.getValue(namespace)[nameWithDesc]
        if (value == null) {
            // try without desc
            if (desc != null) {
                val value2 = methodCache.getValue(namespace)[name]
                if (value2 != null && value2.any { it[namespace]?.substringBefore(';') == name }) {
                    methodCache[namespace]!![nameWithDesc] = value2
                    return value2.first {
                        it[namespace]?.substringBefore(';') == name && (!it.hasDescriptor() || it.getDescriptor(
                            namespace
                        ) == desc)
                    }
                }
            }
            // cache miss, recalculate this value
            for (m in methods) {
                if (m[namespace] == name && (!m.hasDescriptor() || m.getDescriptor(namespace) == desc)) {
                    methodCache[namespace]!!.getValue(nameWithDesc).add(m as MethodMappingNode)
                    return m
                }
            }
        }
        if (value != null && value.size > 1) {
            throw IllegalStateException("found multiple matching methods for $namespace: $nameWithDesc")
        }
        return value?.first()
    }

    override fun visit(visitor: MappingVisitor): Boolean {
        return visitor.visitClass(namespaces)
    }

    override fun asVisitableIntl(): StackedVisitor {
        val r = root
        return object: StackedMemberVisitor(this) {

            override fun visitField(names: Map<String, String?>): StackedVisitor {
                // calculate optimal namespace search order
                val namespaces = mutableListOf<String>()
                var hasDesc = false
                for (ns in root.completedClasses) {
                    if (names[ns] != null && names[ns]!!.contains(';')) {
                        hasDesc = true
                        namespaces.add(ns)
                    }
                }
                for (ns in root.completedClasses) {
                    if (names[ns] != null && !names[ns]!!.contains(';')) {
                        namespaces.add(ns)
                    }
                }
                for (ns in namespaces) {
                    for (f in fields) {
                        if (names[ns] == null) continue
                        if (f.getName(ns) == names[ns]!!.substringBefore(';') && (!f.hasDescriptor() || (hasDesc && f.getDescriptor(
                                ns
                            ) == names[ns]!!.substringAfter(';')))
                        ) {
                            return (f as FieldMappingNode).asVisitableIntl()
                        }
                    }
                }
                // no namespace match, create new field
                val fieldNode = FieldMappingNode(this@ClassMappingNode)
                fields.add(fieldNode)
                return fieldNode.asVisitableIntl()
            }

            override fun visitMethod(names: Map<String, String?>): StackedVisitor {
                // calculate optimal namespace search order
                val namespaces = mutableListOf<String>()
                var hasDesc = false
                for (ns in root.completedClasses) {
                    if (names[ns] != null && names[ns]!!.contains(';')) {
                        hasDesc = true
                        namespaces.add(ns)
                    }
                }
                for (ns in root.completedClasses) {
                    if (names[ns] != null && !names[ns]!!.contains(';')) {
                        namespaces.add(ns)
                    }
                }
                for (ns in namespaces) {
                    for (m in methods) {
                        if (names[ns] == null) continue
                        if (m.getName(ns) == names[ns]!!.substringBefore(';') && (!m.hasDescriptor() || (hasDesc && m.getDescriptor(
                                ns
                            ) == names[ns]!!.substringAfter(';')))
                        ) {
                            return (m as MethodMappingNode).asVisitableIntl()
                        }
                    }
                }
                // no namespace match, create new method
                val methodNode = MethodMappingNode(this@ClassMappingNode)
                methods.add(methodNode)
                return methodNode.asVisitableIntl()
            }

            override fun visitInnerClass(
                type: InnerClassPropertyView.InnerType,
                names: Map<String, String?>
            ): StackedVisitor? {
                // find existing inner class by type
                for (c in innerClass) {
                    if (c.innerType == type) {
                        return (c as InnerClassMappingNode).asVisitableIntl()
                    }
                }
                // no existing inner class, create new one
                val innerClassNode = InnerClassMappingNode(this@ClassMappingNode, type)
                innerClass.add(innerClassNode)
                return innerClassNode.asVisitableIntl()
            }

        }
    }

}