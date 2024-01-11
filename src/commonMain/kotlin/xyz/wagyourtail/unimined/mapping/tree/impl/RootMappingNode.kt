package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.annotation.AnnotationElement
import xyz.wagyourtail.unimined.mapping.annotation.AnnotationElementName
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ArrayType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.tree.ClassProperty
import xyz.wagyourtail.unimined.mapping.tree.RootProperty
import xyz.wagyourtail.unimined.mapping.tree.RootPropertyView
import xyz.wagyourtail.unimined.mapping.util.associateNonNull
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

open class RootMappingNode: AbstractMappingNode<RootPropertyView>(null), RootProperty {
    override val type = null

    override val namespaces = mutableListOf<String>()

    override val completedClasses = mutableSetOf<String>()

    override fun addNamespacess(vararg namespaces: String) {
        this.namespaces.addAll(namespaces.toList().filter { it !in this.namespaces })
    }

    /**
     * cache for faster lookups
     */
    private var classCache = mutableMapOf<String, MutableMap<String, ClassMappingNode>>().withDefault { ns ->
        this.get<ClassMappingNode>(ElementType.CLASS).associateNonNull { if (it[ns] == null) null else it[ns]!! to it }
            .toMutableMap()
    }

    override val classes: MutableCollection<ClassProperty>
        get() = this[ElementType.CLASS]

    override fun getClass(namespace: String, name: String): ClassProperty? {
        val value = classCache.getValue(namespace)[name]
        if (value == null || value[namespace] != name) {
            if (value != null) {
                classCache.getValue(namespace).remove(name)
            }
            // cache miss, recalculate this value
            for (c in classes) {
                if (c[namespace] == name) {
                    classCache[namespace]?.set(name, c as ClassMappingNode)
                    return c
                }
            }
        }
        return value
    }

    override fun visit(visitor: MappingVisitor): Boolean {
        visitor.visitNamespaces(*namespaces.toTypedArray())
        return true
    }

    override fun compareTo(other: AbstractMappingNode<*>): Int {
        throw UnsupportedOperationException("RootMappingNode cannot be compared")
    }

    override fun asVisitableIntl() = object: StackedVisitor(this) {
        val namespaces = mutableListOf<String>()

        override fun visitNamespaces(vararg namespaces: String) {
            addNamespacess(*namespaces)
            this.namespaces.addAll(namespaces)
        }

        override fun visitClass(names: Map<String, String?>): StackedVisitor {
            for (ns in completedClasses) {
                for (c in classes) {
                    if (names[ns] == null) continue
                    if (c[ns] == names[ns]) {
                        return (c as ClassMappingNode).asVisitableIntl()
                    }
                }
            }
            // no namespace match, create new class
            val classNode = ClassMappingNode(this@RootMappingNode)
            classes.add(classNode)
            return classNode.asVisitableIntl()
        }

        override fun visitEnd() {
            super.visitEnd()
            completedClasses.addAll(namespaces)
        }

    }

    fun remapFieldDesc(fromNs: String, toNs: String, desc: String): String {
        if (fromNs == toNs) return desc
        if (fromNs !in completedClasses) throw IllegalArgumentException("fromNs ($fromNs) must be a complete namespace")
        if (toNs !in completedClasses) throw IllegalArgumentException("toNs ($toNs) must be a complete namespace")
        val fieldDesc = JVMS.parseFieldDescriptor(desc)
        if (fieldDesc.value.isBaseType()) return desc
        if (fieldDesc.value.isArrayType()) {
            val (dim, type) = fieldDesc.value.getArrayType().getDimensionsAndComponent()
            val ns = remapFieldDesc(fromNs, toNs, type.toString())
            return "[".repeat(dim) + ns
        } else {
            val name = fieldDesc.value.getObjectType().getInternalName()
            val classNode = getClass(fromNs, name) ?: return desc
            val toName = classNode[toNs] ?: return desc
            return "L$toName;"
        }
    }

    fun remapMethodDesc(fromNs: String, toNs: String, desc: String): String {
        if (fromNs == toNs) return desc
        if (fromNs !in completedClasses) throw IllegalArgumentException("fromNs ($fromNs) must be a complete namespace")
        if (toNs !in completedClasses) throw IllegalArgumentException("toNs ($toNs) must be a complete namespace")
        val methodDesc = JVMS.parseMethodDescriptor(desc)
        val (ret, params) = methodDesc.getParts()
        val newParams = params.map { remapFieldDesc(fromNs, toNs, it.toString()) }
        val newRet = remapFieldDesc(fromNs, toNs, ret.toString())
        return JVMS.createMethodDescriptor(newRet, *newParams.toTypedArray()).toString()
    }

    fun remapClassSignature(fromNs: String, toNs: String, desc: String): String {
        if (fromNs == toNs) return desc
        if (fromNs !in completedClasses) throw IllegalArgumentException("fromNs ($fromNs) must be a complete namespace")
        if (toNs !in completedClasses) throw IllegalArgumentException("toNs ($toNs) must be a complete namespace")
        val signature = JVMS.parseClassSignature(desc)
        return buildString {
            signature.accept { obj, leaf ->
                when (obj) {
                    is ObjectType, is ArrayType -> {
                        append(remapFieldDesc(fromNs, toNs, obj.toString()))
                        false
                    }

                    else -> {
                        if (leaf) {
                            append(obj.toString())
                        }
                        true
                    }
                }
            }
        }
    }

    fun remapMethodSignature(fromNs: String, toNs: String, desc: String): String {
        if (fromNs == toNs) return desc
        if (fromNs !in completedClasses) throw IllegalArgumentException("fromNs ($fromNs) must be a complete namespace")
        if (toNs !in completedClasses) throw IllegalArgumentException("toNs ($toNs) must be a complete namespace")
        val signature = JVMS.parseMethodSignature(desc)
        return buildString {
            signature.accept { obj, leaf ->
                when (obj) {
                    is ObjectType, is ArrayType -> {
                        append(remapFieldDesc(fromNs, toNs, obj.toString()))
                        false
                    }
                    else -> {
                        if (leaf) {
                            append(obj.toString())
                        }
                        true
                    }
                }
            }
        }
    }

    fun remapFieldSignature(fromNs: String, toNs: String, desc: String): String {
        if (fromNs == toNs) return desc
        if (fromNs !in completedClasses) throw IllegalArgumentException("fromNs ($fromNs) must be a complete namespace")
        if (toNs !in completedClasses) throw IllegalArgumentException("toNs ($toNs) must be a complete namespace")
        val signature = JVMS.parseFieldSignature(desc)
        return buildString {
            signature.accept { obj, leaf ->
                when (obj) {
                    is ObjectType, is ArrayType -> {
                        append(remapFieldDesc(fromNs, toNs, obj.toString()))
                        false
                    }

                    else -> {
                        if (leaf) {
                            append(obj.toString())
                        }
                        true
                    }
                }
            }
        }
    }

    fun remapAnnotation(fromNs: String, toNs: String, desc: String): String {
        if (fromNs == toNs) return desc
        if (fromNs !in completedClasses) throw IllegalArgumentException("fromNs ($fromNs) must be a complete namespace")
        if (toNs !in completedClasses) throw IllegalArgumentException("toNs ($toNs) must be a complete namespace")
        val annotation = Annotation.read(desc)
        return buildString {
            var nextIsAnnotation = false
            var holdingAnnotation: ClassProperty? = null

            annotation.accept { obj, leaf ->
                when (obj) {
                    is ObjectType, is ArrayType -> {
                        if (nextIsAnnotation) {
                            holdingAnnotation = getClass(fromNs, (obj as ObjectType).getInternalName())
                            nextIsAnnotation = false
                        }
                        append(remapFieldDesc(fromNs, toNs, obj.toString()))
                        false
                    }
                    is AnnotationElementName -> {
                        append(holdingAnnotation?.getMethod(fromNs, obj.toString(), null)?.getName(toNs) ?: obj.toString())
                        false
                    }
                    else -> {
                        if (leaf) {
                            if (obj == "@") {
                                nextIsAnnotation = true
                            }
                            append(obj.toString())
                        }
                        true
                    }
                }
            }
        }
    }

}