package xyz.wagyourtail.unimined.mapping.formats.at

import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object ATWriter : FormatWriter {

    var defaultToPublic: Boolean = false

    override fun write(append: (String) -> Unit, envType: EnvType): RootMappingVisitor {
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
                                it.memberName.withFieldDesc(
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

    fun assembleAts(finalizer: (List<ATReader.ATItem>) -> Unit): RootMappingVisitor {
        var ns: Namespace? = null
        var cls: InternalName? = null
        var member: NameAndDescriptor? = null

        val memberAccessesAdd = mutableListOf<AccessFlag>()
        val memberAccessesRemove = mutableListOf<AccessFlag>()

        val classAccessAdd = mutableListOf<AccessFlag>()
        val classAccessRemove = mutableListOf<AccessFlag>()

        val mappings = defaultedMapOf<InternalName, MutableList<ATReader.ATData>> { mutableListOf() }

        return EmptyRootMappingVisitor().delegator(object : NullDelegator() {

            override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
                if (namespaces.size != 1) {
                    throw IllegalArgumentException("AWWriter requires exactly one namespace")
                }
                ns = namespaces[0]
            }

            override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
                cls = names[ns] ?: throw IllegalArgumentException("Class name not found")
                return default.visitClass(delegate, names)
            }

            override fun visitField(
                delegate: ClassMappingVisitor,
                names: Map<Namespace, FieldNameAndDescriptor>
            ): FieldMappingVisitor? {
                member = names[ns] ?: throw IllegalArgumentException("Field name not found")
                return default.visitField(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassMappingVisitor,
                names: Map<Namespace, MethodNameAndDescriptor>
            ): MethodMappingVisitor? {
                val target = names[ns] ?: throw IllegalArgumentException("Method name not found")
                if (!target.hasDescriptor) throw IllegalArgumentException("Method descriptor not found for $name on $cls")
                member = target
                return default.visitMethod(delegate, names)
            }

            override fun visitWildcard(
                delegate: ClassMappingVisitor,
                type: WildcardType,
                descs: Map<Namespace, FieldOrMethodDescriptor>
            ): WildcardMappingVisitor? {
                member = when (type) {
                    WildcardType.METHOD -> {
                        UnqualifiedName.unchecked("*").withMethodDesc(descs[ns]?.getMethodDescriptor())
                    }

                    WildcardType.FIELD -> {
                        UnqualifiedName.unchecked("*").withFieldDesc(null)
                    }
                }
                return default.visitWildcard(delegate, type, descs)
            }

            override fun visitClassAccess(
                delegate: ClassMappingVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
            ): AccessMappingVisitor? {
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
                delegate: FieldMappingVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
            ): AccessMappingVisitor? {
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
                delegate: MethodMappingVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
            ): AccessMappingVisitor? {
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
                delegate: WildcardMappingVisitor,
                type: AccessType,
                value: AccessFlag,
                conditions: AccessConditions,
            ): AccessMappingVisitor? {
                if (conditions == AccessConditions.ALL) {
                    if (type == AccessType.ADD) {
                        memberAccessesAdd += value
                    } else {
                        memberAccessesRemove += value
                    }
                }
                return super.visitWildcardAccess(delegate, type, value, conditions)
            }

            override fun visitClassEnd(delegate: ClassMappingVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in classAccessAdd -> ATReader.TriState.ADD
                    in classAccessRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(classAccessAdd)
                if (access != null || final != ATReader.TriState.LEAVE) {
                    mappings[cls]!!.add(
                        ATReader.ATDataClass(
                            if (access == AccessFlag.DEFAULT && defaultToPublic) AccessFlag.PUBLIC else access ?: AccessFlag.PUBLIC,
                            final,
                            cls!!,
                        ))
                }
                classAccessAdd.clear()
                classAccessRemove.clear()
                memberAccessesAdd.clear()
                memberAccessesRemove.clear()
                cls = null
                member = null
            }

            override fun visitFieldEnd(delegate: FieldMappingVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in memberAccessesAdd -> ATReader.TriState.ADD
                    in memberAccessesRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(memberAccessesAdd)
                val (name, desc) = member!!.getParts()
                if (access != null || final != ATReader.TriState.LEAVE) {
                    mappings[cls]!!.add(
                        ATReader.ATDataField(
                            if (access == AccessFlag.DEFAULT && defaultToPublic) AccessFlag.PUBLIC else access ?: AccessFlag.PUBLIC,
                            final,
                            cls!!,
                            name,
                        ))
                }

                memberAccessesAdd.clear()
                memberAccessesRemove.clear()
                member = null
            }

            override fun visitMethodEnd(delegate: MethodMappingVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in memberAccessesAdd -> ATReader.TriState.ADD
                    in memberAccessesRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(memberAccessesAdd)
                val (name, desc) = member!!.getParts()
                if (access != null || final != ATReader.TriState.LEAVE) {
                    mappings[cls]!!.add(
                        ATReader.ATDataMethod(
                            if (access == AccessFlag.DEFAULT && defaultToPublic) AccessFlag.PUBLIC else access ?: AccessFlag.PUBLIC,
                            final,
                            cls!!,
                            name,
                            desc?.getMethodDescriptor() ?: error("Method descriptor not found")
                        ))
                }
                memberAccessesAdd.clear()
                memberAccessesRemove.clear()
                member = null
            }

            override fun visitWildcardEnd(delegate: WildcardMappingVisitor) {
                val final = when (AccessFlag.FINAL) {
                    in memberAccessesAdd -> ATReader.TriState.ADD
                    in memberAccessesRemove -> ATReader.TriState.REMOVE
                    else -> ATReader.TriState.LEAVE
                }
                val access = AccessFlag.visibilityOf(memberAccessesAdd)
                val (name, desc) = member!!.getParts()
                if (access != null || final != ATReader.TriState.LEAVE) {
                    mappings[cls]!!.add(
                        if (member is MethodNameAndDescriptor) {
                            ATReader.ATDataMethodWildcard(
                                if (access == AccessFlag.DEFAULT && defaultToPublic) AccessFlag.PUBLIC else access ?: AccessFlag.PUBLIC,
                                final,
                                cls!!,
                                desc?.getMethodDescriptor()
                            )
                        } else {
                            ATReader.ATDataFieldWildcard(
                                if (access == AccessFlag.DEFAULT && defaultToPublic) AccessFlag.PUBLIC else access ?: AccessFlag.PUBLIC,
                                final,
                                cls!!,
                            )
                        }
                    )
                }
                memberAccessesAdd.clear()
                memberAccessesRemove.clear()
                member = null
            }

            override fun visitFooter(delegate: RootMappingVisitor) {
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