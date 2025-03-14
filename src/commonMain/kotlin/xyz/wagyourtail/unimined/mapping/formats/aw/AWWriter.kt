package xyz.wagyourtail.unimined.mapping.formats.aw

import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.utils.ListCompare
import xyz.wagyourtail.commonskt.utils.comparable
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.contains
import xyz.wagyourtail.unimined.mapping.jvms.four.plus
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object AWWriter : FormatWriter {

    /**
     * this writer is not perfect, AW is not 1:1 stored in memory, so some things may be added/lost
     *
     * for a better experience, read with [AWReader.readData],
     * use [AWWriter.remapMappings] to convert to the correct mappings,
     * and [AWWriter.writeData] to write the mappings
     */
    override fun write(append: (String) -> Unit, envType: EnvType): MappingVisitor {
        var ns: Namespace? = null
        var cls: InternalName? = null
        var member: NameAndDescriptor? = null

        var wildcardType: WildcardNode.WildcardType? = null

        var wildcardFieldAdd = 0
        var wildcardFieldRemove = 0

        var wildcardMethodAdd = 0
        var wildcardMethodRemove = 0

        var memberAccessesAdd = 0
        var memberAccessesRemove = 0

        var classAccessAdd = 0
        var classAccessRemove = 0

        val mappings = defaultedMapOf<InternalName, MutableList<AWReader.AWData>> { mutableListOf() }

        return EmptyMappingVisitor().delegator(object : NullDelegator() {

            override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
                if (namespaces.size != 1) {
                    throw IllegalArgumentException("AWWriter requires exactly one namespace")
                }
                ns = Namespace(namespaces[0])
            }

            override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                cls = names[ns] ?: throw IllegalArgumentException("Class name not found")
                return default.visitClass(delegate, names)
            }

            override fun visitField(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, FieldDescriptor?>>
            ): FieldVisitor? {
                val (name, desc) = names[ns] ?: throw IllegalArgumentException("Field name not found")
                if (desc == null) return null
                member = NameAndDescriptor(UnqualifiedName.read(name), FieldOrMethodDescriptor(desc))
                memberAccessesAdd = wildcardFieldAdd
                memberAccessesRemove = wildcardFieldRemove
                return default.visitField(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                val (name, desc) = names[ns] ?: throw IllegalArgumentException("Method name not found")
                if (desc == null) return null
                member = NameAndDescriptor(UnqualifiedName.read(name), FieldOrMethodDescriptor(desc))
                memberAccessesAdd = wildcardMethodAdd
                memberAccessesRemove = wildcardMethodRemove
                return default.visitMethod(delegate, names)
            }

            override fun visitClassAccess(
                delegate: ClassVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
                namespaces: Set<Namespace>
            ): AccessVisitor? {
                if (conditions == AccessConditions.ALL) {
                    if (type == AccessType.ADD) {
                        classAccessAdd += value
                    } else {
                        classAccessRemove += value
                    }
                }
                return null
            }

            override fun visitWildcard(
                delegate: ClassVisitor,
                type: WildcardNode.WildcardType,
                descs: Map<Namespace, FieldOrMethodDescriptor>
            ): WildcardVisitor? {
                wildcardType = type
                return default.visitWildcard(delegate, type, descs)
            }

            override fun visitWildcardAccess(
                delegate: WildcardVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
                namespaces: Set<Namespace>
            ): AccessVisitor? {
                if (conditions == AccessConditions.ALL) {
                    if (wildcardType == WildcardNode.WildcardType.FIELD) {
                        if (type == AccessType.ADD) {
                            wildcardFieldAdd += value
                        } else {
                            wildcardFieldRemove += value
                        }
                    } else {
                        if (type == AccessType.ADD) {
                            wildcardMethodAdd += value
                        } else {
                            wildcardMethodRemove += value
                        }
                    }
                }
                return null
            }

            override fun visitWildcardEnd(delegate: WildcardVisitor) {
                wildcardType = null
            }

            override fun visitFieldAccess(
                delegate: FieldVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
                namespaces: Set<Namespace>
            ): AccessVisitor? {
                if (conditions == AccessConditions.ALL) {
                    if (type == AccessType.ADD) {
                        memberAccessesAdd += value
                    } else {
                        memberAccessesRemove += value
                    }
                }
                return null
            }

            override fun visitMethodAccess(
                delegate: MethodVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
                namespaces: Set<Namespace>
            ): AccessVisitor? {
                if (conditions == AccessConditions.ALL) {
                    if (type == AccessType.ADD) {
                        memberAccessesAdd += value
                    } else {
                        memberAccessesRemove += value
                    }
                }
                return null
            }

            override fun visitClassEnd(delegate: ClassVisitor) {
                val fqn = FullyQualifiedName(ObjectType(cls!!), null)
                if (AccessFlag.PUBLIC in classAccessAdd) {
                    mappings[cls]!!.add(AWReader.AWData(
                        "accessible",
                        fqn
                    ))
                }
                if (classAccessRemove.contains(AccessFlag.FINAL)) {
                    mappings[cls]!!.add(AWReader.AWData(
                        "extendable",
                        fqn
                    ))
                }
                classAccessAdd = 0
                classAccessRemove = 0
                memberAccessesAdd = 0
                memberAccessesRemove = 0
                wildcardFieldAdd = 0
                wildcardFieldRemove = 0
                wildcardMethodAdd = 0
                wildcardMethodRemove = 0
                cls = null
            }

            override fun visitFieldEnd(delegate: FieldVisitor) {
                val fqn = FullyQualifiedName(ObjectType(cls!!), member!!)
                if (memberAccessesAdd.contains(AccessFlag.PUBLIC)) {
                    mappings[cls]!!.add(AWReader.AWData(
                        "accessible",
                        fqn
                    ))
                }
                if (memberAccessesRemove.contains(AccessFlag.FINAL)) {
                    mappings[cls]!!.add(AWReader.AWData(
                        "mutable",
                        fqn
                    ))
                }
                member = null
            }

            override fun visitMethodEnd(delegate: MethodVisitor) {
                val fqn = FullyQualifiedName(ObjectType(cls!!), member!!)
                if (memberAccessesAdd.contains(AccessFlag.PUBLIC)) {
                    mappings[cls]!!.add(AWReader.AWData(
                        "accessible",
                        fqn
                    ))
                }
                if (memberAccessesRemove.contains(AccessFlag.FINAL)) {
                    mappings[cls]!!.add(AWReader.AWData(
                        "extendable",
                        fqn
                    ))
                }
                member = null
            }

            override fun visitFooter(delegate: MappingVisitor) {
                writeData(AWReader.AWMappings(ns!!, mappings.values.map { map ->
                    map.sortedBy {
                        listOf(it.target.toString(), it.access).comparable()
                } }.flatten()), append)
            }
        })
    }

    fun remapMappings(mappings: AWReader.AWMappings, context: AbstractMappingTree, targetNs: Namespace): AWReader.AWMappings {
        return AWReader.AWMappings(targetNs, mappings.targets.map {
            if (it is AWReader.AWData) {
                AWReader.AWData(it.access, context.map(mappings.namespace, targetNs, it.target))
            } else {
                it
            }
        })
    }

    fun writeData(mappings: AWReader.AWMappings, append: (String) -> Unit) {
        append("accessWidener\tv2\t${mappings.namespace.name}\n")
        for ((i, target) in mappings.targets.withIndex()) {
            when (target) {
                is AWReader.AWData -> {
                    if (i != 0) append("\n")
                    append("${target.access}\t")
                    val (cls, member) = target.target.getParts()
                    if (member == null) {
                        append("class\t${cls.getInternalName()}")
                    } else {
                        val (name, desc) = member.getParts()
                        if (desc!!.isMethodDescriptor()) {
                            append("method\t${cls.getInternalName()}\t$name\t$desc")
                        } else {
                            append("field\t${cls.getInternalName()}\t$name\t$desc")
                        }
                    }
                }
                is AWReader.AWComment -> {
                    append(if (target.newline && i != 0) "\n" else " ")
                    append(target.comment)
                }
                is AWReader.AWNewline -> {
                    append("\n")
                }
            }
        }
        append("\n")
    }

}