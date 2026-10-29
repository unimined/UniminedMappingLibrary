package xyz.wagyourtail.unimined.mapping.formats.at

import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.WildcardMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.FormatWriterSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

object ATWriter : FormatWriter {

    var defaultToPublic: Boolean = false

    /** writer tunable — promote DEFAULT access rows to PUBLIC on write */
    val DEFAULT_TO_PUBLIC = FormatReaderSettings.SettingsKey<Boolean>("defaultToPublic")

    override fun write(append: (String) -> Unit, settings: FormatWriterSettings): MappingSink {
        val envType = settings.envType
        return assembleAts(settings[DEFAULT_TO_PUBLIC] ?: defaultToPublic) {
            writeData(it, append)
        }
    }

    fun remapMappings(mappings: List<ATReader.ATItem>, context: MappingTree, sourceNs: Namespace, targetNs: Namespace) =
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

    fun assembleAts(
        defaultToPublic: Boolean = this.defaultToPublic,
        finalizer: (List<ATReader.ATItem>) -> Unit,
    ): MappingSink {
        var ns: Namespace? = null
        var cls: InternalName? = null
        var member: NameAndDescriptor? = null

        val memberAccessesAdd = mutableListOf<AccessFlag>()
        val memberAccessesRemove = mutableListOf<AccessFlag>()

        val classAccessAdd = mutableListOf<AccessFlag>()
        val classAccessRemove = mutableListOf<AccessFlag>()

        val mappings = defaultedMapOf<InternalName, MutableList<ATReader.ATData>> { mutableListOf() }

        return object : MappingSink {

            override fun header(namespaces: List<Namespace>) {
                if (namespaces.size != 1) {
                    throw IllegalArgumentException("AWWriter requires exactly one namespace")
                }
                ns = namespaces[0]
            }

            override fun push(element: MappingElement): MappingElement? {
                when (element) {
                    is ClassMapping -> {
                        cls = element.names[ns] ?: throw IllegalArgumentException("Class name not found")
                    }
                    is FieldMapping -> {
                        member = element.emitEntries()[ns] ?: throw IllegalArgumentException("Field name not found")
                    }
                    is MethodMapping -> {
                        val target = element.emitEntries()[ns] ?: throw IllegalArgumentException("Method name not found")
                        if (!target.hasDescriptor) throw IllegalArgumentException("Method descriptor not found for $element on $cls")
                        member = target
                    }
                    is WildcardMapping -> {
                        member = when (element.type) {
                            WildcardType.METHOD -> {
                                UnqualifiedName.unchecked("*").withMethodDesc(element.descs[ns]?.getMethodDescriptor())
                            }
                            WildcardType.FIELD -> {
                                UnqualifiedName.unchecked("*").withFieldDesc(null)
                            }
                        }
                    }
                    else -> {}
                }
                return element
            }

            private fun memberAccess(type: AddRemove, value: AccessFlag, conditions: AccessConditions) {
                if (conditions == AccessConditions.ALL) {
                    (if (type == AddRemove.ADD) memberAccessesAdd else memberAccessesRemove).add(value)
                }
            }

            override fun push(meta: MetadataElement): MetadataElement? {
                if (meta is AccessElement) {
                    if (member == null) {
                        if (meta.condition == AccessConditions.ALL) {
                            (if (meta.type == AddRemove.ADD) classAccessAdd else classAccessRemove).add(meta.value)
                        }
                    } else {
                        memberAccess(meta.type, meta.value, meta.condition)
                    }
                }
                return meta
            }

            private fun fieldEnd() {
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

            private fun methodEnd() {
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

            private fun wildcardEnd() {
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

            private fun classEnd() {
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

            override fun pop(element: MappingElement) {
                when (element) {
                    is ClassMapping -> classEnd()
                    is FieldMapping -> fieldEnd()
                    is MethodMapping -> methodEnd()
                    is WildcardMapping -> wildcardEnd()
                    else -> {}
                }
            }

            override fun end() {
                finalizer(mappings.values.map { members ->
                    members.sortedBy {
                    buildString {
                        append(it.targetClass.toString())
                        when (it) {
                            is ATReader.ATDataClass -> {}
                            is ATReader.ATDataField -> {
                                append(" ")
                                append(it.memberName.toString())
                            }
                            is ATReader.ATDataMethod -> {
                                append(" ")
                                append(it.memberName.toString())
                                append(it.memberDesc.toString())
                            }
                            is ATReader.ATDataFieldWildcard -> {
                                append(" *")
                            }
                            is ATReader.ATDataMethodWildcard -> {
                                append(" *")
                                append(it.memberDesc?.toString() ?: "()")
                            }
                            else -> error("Unknown ATData type $it")
                        }
                    }
                } }.flatten())
            }
        }
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