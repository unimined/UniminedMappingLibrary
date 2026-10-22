package xyz.wagyourtail.unimined.mapping.formats.aw

import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.contains
import xyz.wagyourtail.unimined.mapping.jvms.four.plus
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

object AWWriter : FormatWriter {

    /**
     * this writer is not perfect, AW is not 1:1 stored in memory, so some things may be added/lost
     *
     * for a better experience, read with [AWReader.readData],
     * use [AWWriter.remapMappings] to convert to the correct mappings,
     * and [AWWriter.writeData] to write the mappings
     */
    override fun write(append: (String) -> Unit, envType: EnvType): MappingSink {
        var ns: Namespace? = null
        var cls: InternalName? = null
        var member: NameAndDescriptor? = null

        var wildcardType: WildcardType? = null

        var wildcardFieldAdd = 0
        var wildcardFieldRemove = 0

        var wildcardMethodAdd = 0
        var wildcardMethodRemove = 0

        var memberAccessesAdd = 0
        var memberAccessesRemove = 0

        var classAccessAdd = 0
        var classAccessRemove = 0

        val mappings = defaultedMapOf<InternalName, MutableList<AWReader.AWData>> { mutableListOf() }

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
                        val target = element.emitEntries()[ns] ?: throw IllegalArgumentException("Field name not found")
                        if (!target.hasDescriptor) return null
                        member = target
                        memberAccessesAdd = wildcardFieldAdd
                        memberAccessesRemove = wildcardFieldRemove
                    }
                    is MethodMapping -> {
                        val target = element.emitEntries()[ns] ?: throw IllegalArgumentException("Method name not found")
                        if (!target.hasDescriptor) return null
                        member = target
                        memberAccessesAdd = wildcardMethodAdd
                        memberAccessesRemove = wildcardMethodRemove
                    }
                    is WildcardMapping -> {
                        wildcardType = element.type
                    }
                    else -> {}
                }
                return element
            }

            override fun push(meta: MetadataElement): MetadataElement? {
                if (meta is AccessElement && meta.condition == AccessConditions.ALL) {
                    val add = meta.type == AddRemove.ADD
                    when {
                        wildcardType != null -> {
                            if (wildcardType == WildcardType.FIELD) {
                                if (add) wildcardFieldAdd += meta.value else wildcardFieldRemove += meta.value
                            } else {
                                if (add) wildcardMethodAdd += meta.value else wildcardMethodRemove += meta.value
                            }
                        }
                        member != null -> {
                            if (add) memberAccessesAdd += meta.value else memberAccessesRemove += meta.value
                        }
                        else -> {
                            if (add) classAccessAdd += meta.value else classAccessRemove += meta.value
                        }
                    }
                }
                return meta
            }

            override fun pop(element: MappingElement) {
                when (element) {
                    is ClassMapping -> {
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
                    is FieldMapping -> {
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
                    is MethodMapping -> {
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
                    is WildcardMapping -> {
                        wildcardType = null
                    }
                    else -> {}
                }
            }

            override fun end() {
                writeData(AWReader.AWMappings(ns!!, mappings.values.map { map ->
                    map.sorted()
                }.flatten()), append)
            }
        }
    }

    fun remapMappings(mappings: AWReader.AWMappings, context: MappingTree, targetNs: Namespace): AWReader.AWMappings {
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