package xyz.wagyourtail.unimined.mapping.formats.aw

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.ListCompare
import xyz.wagyourtail.unimined.mapping.util.defaultedMapOf
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
    override fun write(envType: EnvType, append: (String) -> Unit): MappingVisitor {
        var ns: Namespace? = null
        var cls: InternalName? = null
        var member: NameAndDescriptor? = null

        val memberAccessesAdd = mutableSetOf<AccessFlag>()
        val memberAccessesRemove = mutableSetOf<AccessFlag>()

        val classAccessAdd = mutableSetOf<AccessFlag>()
        val classAccessRemove = mutableSetOf<AccessFlag>()

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
                if (desc == null) throw IllegalArgumentException("Field descriptor not found for $name on $cls")
                member = NameAndDescriptor(UnqualifiedName.read(name), FieldOrMethodDescriptor(desc))
                return default.visitField(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                val (name, desc) = names[ns] ?: throw IllegalArgumentException("Method name not found")
                if (desc == null) throw IllegalArgumentException("Method descriptor not found for $name on $cls")
                member = NameAndDescriptor(UnqualifiedName.read(name), FieldOrMethodDescriptor(desc))
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
                        classAccessAdd.add(value)
                    } else {
                        classAccessRemove.add(value)
                    }
                }
                return null
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
                        memberAccessesAdd.add(value)
                    } else {
                        memberAccessesRemove.add(value)
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
                        memberAccessesAdd.add(value)
                    } else {
                        memberAccessesRemove.add(value)
                    }
                }
                return null
            }

            override fun visitClassEnd(delegate: ClassVisitor) {
                val fqn = FullyQualifiedName(ObjectType(cls!!), null)
                if (classAccessAdd.contains(AccessFlag.PUBLIC)) {
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
                classAccessAdd.clear()
                classAccessRemove.clear()
                memberAccessesAdd.clear()
                memberAccessesRemove.clear()
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
                memberAccessesAdd.clear()
                memberAccessesRemove.clear()
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
                super.visitMethodEnd(delegate)
            }

            override fun visitFooter(delegate: MappingVisitor) {
                writeData(AWReader.AWMappings(ns!!, mappings.values.map { map ->
                    map.sortedBy {
                        ListCompare(it.target.toString(), it.access)
                } }.flatten().toSet()), append)
            }
        })
    }

    fun remapMappings(mappings: AWReader.AWMappings, context: AbstractMappingTree, targetNs: Namespace): AWReader.AWMappings {
        return AWReader.AWMappings(targetNs, mappings.targets.map {
            AWReader.AWData(it.access, context.map(mappings.namespace, targetNs, it.target))
        }.toSet())
    }

    fun writeData(mappings: AWReader.AWMappings, append: (String) -> Unit) {
        append("accessWidener\tv2\t${mappings.namespace.name}\n")
        for (target in mappings.targets) {
            append("${target.access}\t")
            val (cls, member) = target.target.getParts()
            if (member == null) {
                append("class\t${cls.getInternalName()}\n")
            } else {
                val (name, desc) = member.getParts()
                if (desc!!.isMethodDescriptor()) {
                    append("method\t${cls.getInternalName()}\t$name\t$desc\n")
                } else {
                    append("field\t${cls.getInternalName()}\t$name\t$desc\n")
                }
            }
        }
    }

}