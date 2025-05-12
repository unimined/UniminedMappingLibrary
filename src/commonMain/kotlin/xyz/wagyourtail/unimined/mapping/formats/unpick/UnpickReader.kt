package xyz.wagyourtail.unimined.mapping.formats.unpick

import okio.BufferedSource
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * FabricMC's unpick format to represent constant groups.
 */
object UnpickReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return fileName.endsWith(".unpick") && input.peek().readUtf8Line()?.startsWith("v2") ?: false
    }

    data class UnpickConstant(val type: String, val intlName: InternalName, val fieldName: UnqualifiedName)
    data class UnpickTarget(val intlName: InternalName, val targetName: UnqualifiedName, val targetDesc: MethodDescriptor, val params: MutableList<UnpickParam>)
    data class UnpickParam(val index: Int, val group: String)

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        if (input.takeLine() != "v2") throw IllegalArgumentException("Invalid unpick file")
        val constants = defaultedMapOf<String, MutableList<UnpickConstant>> { mutableListOf() }
        val targets = mutableListOf<UnpickTarget>()
        var currentTarget: UnpickTarget? = null
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val indent = input.takeWhitespace().length
            val line = input.takeRemainingOnLine()
            if (line.isEmpty()) continue
            if (indent == 0) {
                when (line[0]) {
                    "constant" -> {
                        val key = line[1]
                        val intlName = InternalName.read(line[2])
                        val fieldName = UnqualifiedName.read(line[3])
                        constants.getValue(key).add(UnpickConstant("constant", intlName, fieldName))
                        currentTarget = null
                    }

                    "flag" -> {
                        val key = line[1]
                        val intlName = InternalName.read(line[2])
                        val fieldName = UnqualifiedName.read(line[3])
                        constants.getValue(key).add(UnpickConstant("flag", intlName, fieldName))
                        currentTarget = null
                    }

                    "target_method" -> {
                        val intlName = InternalName.read(line[1])
                        val targetName = UnqualifiedName.read(line[2])
                        val targetDesc = MethodDescriptor.read(line[3])
                        targets.add(UnpickTarget(intlName, targetName, targetDesc, mutableListOf()))
                        currentTarget = targets.last()
                    }
                }
            } else {
                if (currentTarget == null) {
                    throw IllegalArgumentException("Invalid unpick file, found double indent")
                }
                when (line[0]) {
                    "param" -> {
                        val index = line[1].toInt()
                        val group = line[2]
                        currentTarget.params.add(UnpickParam(index, group))
                    }
                    "return" -> {
                        val group = line[1]
                        currentTarget.params.add(UnpickParam(-1, group))
                    }
                }
            }
        }
        val ns = Namespace(nsMapping["source"] ?: "source")

        into.use {
            visitHeader(ns.name)

            val targetsByGroup = defaultedMapOf<String, MutableList<UnpickTarget>> { mutableListOf() }
            for (target in targets) {
                for (param in target.params) {
                    targetsByGroup.getValue(param.group).add(target)
                }
            }

            for ((group, consts) in constants) {
                val targs = targetsByGroup[group]
                val type = if (consts.any { it.type == "flag" }) {
                    ConstantGroupNode.InlineType.BITFIELD
                } else {
                    ConstantGroupNode.InlineType.PLAIN
                }

                visitConstantGroup(type, group, ns, setOf())?.use {
                    for (const in consts) {
                        visitConstant(const.intlName, const.fieldName, null)?.visitEnd()
                    }
                    for (target in targs) {
                        for (param in target.params) {
                            if (param.group != group) continue
                            visitTarget(
                                FullyQualifiedName(
                                    ObjectType(target.intlName),
                                    NameAndDescriptor(target.targetName, FieldOrMethodDescriptor(target.targetDesc))
                                ), param.index
                            )?.visitEnd()
                        }
                    }
                }
            }
        }
    }
}