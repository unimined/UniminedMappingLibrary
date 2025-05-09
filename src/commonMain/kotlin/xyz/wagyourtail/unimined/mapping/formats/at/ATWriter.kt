package xyz.wagyourtail.unimined.mapping.formats.at

import xyz.wagyourtail.commonskt.collection.defaultedMapOf
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

object ATWriter : FormatWriter {

    var defaultToPublic: Boolean = false

    override fun write(append: (String) -> Unit, envType: EnvType): MappingVisitor {
        return assembleAts {
            writeData(it, append)
        }
    }

    fun remapMappings(mappings: List<ATReader.ATItem>, context: AbstractMappingTree, sourceNs: Namespace, targetNs: Namespace) =
        mappings.map {
            if (it is ATReader.ATData) {
                val mappedClass = context.map(sourceNs, targetNs, it.targetClass)
                when (it) {
                    is ATReader.ATDataClass -> {
                        ATReader.ATDataClass(
                            it.access,
                            it.final,
                            mappedClass
                        )
                    }

                    is ATReader.ATDataField -> {
                        val mappedField = context.map(
                            sourceNs,
                            targetNs,
                            FullyQualifiedName(
                                ObjectType(it.targetClass),
                                NameAndDescriptor(
                                    it.memberName,
                                    null
                                )
                            )
                        )
                        val (name, desc) = mappedField.getParts().second!!.getParts()
                        ATReader.ATDataField(
                            it.access,
                            it.final,
                            mappedClass,
                            name
                        )
                    }

                    is ATReader.ATDataMethod -> {
                        val mappedMethod = context.map(
                            sourceNs,
                            targetNs,
                            FullyQualifiedName(
                                ObjectType(it.targetClass),
                                NameAndDescriptor(
                                    it.memberName,
                                    FieldOrMethodDescriptor(it.memberDesc)
                                )
                            )
                        )
                        val (name, desc) = mappedMethod.getParts().second!!.getParts()
                        ATReader.ATDataMethod(
                            it.access,
                            it.final,
                            mappedClass,
                            name,
                            desc!!.getMethodDescriptor()
                        )
                    }

                    is ATReader.ATDataMethodWildcard -> {
                        val mappedDesc = it.memberDesc?.let {
                            context.map(
                                sourceNs,
                                targetNs,
                                it
                            )
                        }
                        ATReader.ATDataMethodWildcard(
                            it.access,
                            it.final,
                            mappedClass,
                            mappedDesc
                        )
                    }

                    is ATReader.ATDataFieldWildcard -> {
                        ATReader.ATDataFieldWildcard(
                            it.access,
                            it.final,
                            mappedClass
                        )
                    }
                    else -> error("Unknown ATData type")
                }
            } else {
                it
            }
        }

    fun assembleAts(finalizer: (List<ATReader.ATItem>) -> Unit): MappingVisitor {
        var ns: Namespace? = null
        var cls: InternalName? = null
        var member: NameAndDescriptor? = null

        var memberAccessesAdd = 0
        var memberAccessesRemove = 0

        var classAccessAdd = 0
        var classAccessRemove = 0

        val mappings = defaultedMapOf<InternalName, MutableList<ATReader.ATData>> { mutableListOf() }

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
                member = NameAndDescriptor(UnqualifiedName.read(name), desc ?.let { FieldOrMethodDescriptor(it) })
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

            override fun visitWildcard(
                delegate: ClassVisitor,
                type: WildcardNode.WildcardType,
                descs: Map<Namespace, FieldOrMethodDescriptor>
            ): WildcardVisitor? {
                member = when (type) {
                    WildcardNode.WildcardType.METHOD -> {
                        NameAndDescriptor(UnqualifiedName.unchecked("*"), FieldOrMethodDescriptor.unchecked("()V"))
                    }

                    WildcardNode.WildcardType.FIELD -> {
                        NameAndDescriptor(UnqualifiedName.unchecked("*"), null)
                    }
                }
                return default.visitWildcard(delegate, type, descs)
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

            override fun visitWildcardAccess(
                delegate: WildcardVisitor,
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
                return super.visitWildcardAccess(delegate, type, value, conditions, namespaces)
            }

            override fun visitClassEnd(delegate: ClassVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in classAccessAdd -> ATReader.TriState.ADD
                    in classAccessRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(classAccessAdd)
                if (access != null || final != ATReader.TriState.LEAVE) {
                    mappings[cls]!!.add(
                        ATReader.ATData(
                            access ?: if (defaultToPublic) AccessFlag.PUBLIC else null,
                            final,
                            cls!!,
                            null,
                            null
                        ))
                }
                classAccessAdd = 0
                classAccessRemove = 0
                memberAccessesAdd = 0
                memberAccessesRemove = 0
                cls = null
                member = null
            }

            override fun visitFieldEnd(delegate: FieldVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in memberAccessesAdd -> ATReader.TriState.ADD
                    in memberAccessesRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(memberAccessesAdd)
                val (name, desc) = member!!.getParts()
                if (access != null || final != ATReader.TriState.LEAVE) {
                    mappings[cls]!!.add(
                        ATReader.ATData(
                            access ?: if (defaultToPublic) AccessFlag.PUBLIC else null,
                            final,
                            cls!!,
                            name.toString(),
                            null
                        ))
                }

                memberAccessesAdd = 0
                memberAccessesRemove = 0
                member = null
            }

            override fun visitMethodEnd(delegate: MethodVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in memberAccessesAdd -> ATReader.TriState.ADD
                    in memberAccessesRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(memberAccessesAdd)
                val (name, desc) = member!!.getParts()
                if (access != null || final != ATReader.TriState.LEAVE) {
                    mappings[cls]!!.add(
                        ATReader.ATData(
                            access ?: if (defaultToPublic) AccessFlag.PUBLIC else null,
                            final,
                            cls!!,
                            name.toString(),
                            desc?.toString() ?: error("Method descriptor not found")
                        ))
                }
                memberAccessesAdd = 0
                memberAccessesRemove = 0
                member = null
            }

            override fun visitWildcardEnd(delegate: WildcardVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in memberAccessesAdd -> ATReader.TriState.ADD
                    in memberAccessesRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(memberAccessesAdd)
                val (name, desc) = member!!.getParts()
                if (access != null || final != ATReader.TriState.LEAVE) {
                    if (desc != null) {
                        mappings[cls]!!.add(
                            ATReader.ATData(
                                access ?: if (defaultToPublic) AccessFlag.PUBLIC else null,
                                final,
                                cls!!,
                                name.toString(),
                                "()"
                            )
                        )
                    } else {
                        mappings[cls]!!.add(
                            ATReader.ATData(
                                access ?: if (defaultToPublic) AccessFlag.PUBLIC else null,
                                final,
                                cls!!,
                                name.toString(),
                                null
                            )
                        )
                    }
                }
                memberAccessesAdd = 0
                memberAccessesRemove = 0
                member = null
            }

            override fun visitFooter(delegate: MappingVisitor) {
                finalizer(mappings.values.map { members ->
                    members.sortedBy {
                    buildString {
                        append(it.targetClass.toString())
                        when (it) {
                            is ATReader.ATDataClass -> {}
                            is ATReader.ATDataField -> {
                                append('.')
                                append(it.memberName)
                            }
                            is ATReader.ATDataMethod -> {
                                append('.')
                                append(it.memberName)
                                append(it.memberDesc)
                            }
                            is ATReader.ATDataFieldWildcard -> {
                                append('.')
                                append('*')
                            }
                            is ATReader.ATDataMethodWildcard -> {
                                append('.')
                                append('*')
                                append(it.memberDesc ?: "()")
                            }
                            else -> error("Unknown ATData type $it")
                        }
                    }
                } }.flatten())
            }
        })
    }

    fun writeData(mappings: List<ATReader.ATItem>, append: (String) -> Unit) {
        for ((i, data) in mappings.withIndex()) {
            when (data) {
                is ATReader.ATData -> {
                    if (i != 0) append("\n")
                    append(data.access?.toString()?.lowercase() ?: "default")
                    when (data.final) {
                        ATReader.TriState.ADD -> append("+f")
                        ATReader.TriState.REMOVE -> append("-f")
                        ATReader.TriState.LEAVE -> {}
                    }
                    append(" ")
                    append(data.targetClass.toString().replace('/', '.'))
                    when (data) {
                        is ATReader.ATDataClass -> {}
                        is ATReader.ATDataField -> {
                            append(" ")
                            append(data.memberName.toString())
                        }
                        is ATReader.ATDataMethod -> {
                            append(" ")
                            append(data.memberName.toString())
                            append(data.memberDesc.toString())
                        }
                        is ATReader.ATDataFieldWildcard -> {
                            append(" *")
                        }
                        is ATReader.ATDataMethodWildcard -> {
                            append(" *")
                            append(data.memberDesc?.toString() ?: "()")
                        }
                        else -> error("Unknown ATData type $data")
                    }
                }
                is ATReader.ATComment -> {
                    if (i != 0) append(if (data.newline) "\n" else " ")
                    append(data.comment)
                }
                is ATReader.ATNewline -> {
                    append("\n")
                }
            }
        }
    }

}