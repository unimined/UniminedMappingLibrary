package xyz.wagyourtail.unimined.mapping.formats.unpick

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.util.defaultedMapOf

/**
 * FabricMC's unpick format to represent constant groups.
 */
object UnpickReader : FormatReader {
    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return fileName.endsWith(".unpick") && inputType.peek().readUtf8Line()?.startsWith("v2") ?: false
    }

    data class UnpickConstant(val type: String, val intlName: InternalName, val fieldName: UnqualifiedName)
    data class UnpickTarget(val intlName: InternalName, val targetName: UnqualifiedName, val targetDesc: MethodDescriptor, val params: MutableList<UnpickParam>)
    data class UnpickParam(val index: Int, val group: String)

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
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
                when (line[0].second) {
                    "constant" -> {
                        val key = line[1].second
                        val intlName = InternalName.read(line[2].second)
                        val fieldName = UnqualifiedName.read(line[3].second)
                        constants.getValue(key).add(UnpickConstant("constant", intlName, fieldName))
                        currentTarget = null
                    }

                    "flag" -> {
                        val key = line[1].second
                        val intlName = InternalName.read(line[2].second)
                        val fieldName = UnqualifiedName.read(line[3].second)
                        constants.getValue(key).add(UnpickConstant("flag", intlName, fieldName))
                        currentTarget = null
                    }

                    "target_method" -> {
                        val intlName = InternalName.read(line[1].second)
                        val targetName = UnqualifiedName.read(line[2].second)
                        val targetDesc = MethodDescriptor.read(line[3].second)
                        targets.add(UnpickTarget(intlName, targetName, targetDesc, mutableListOf()))
                        currentTarget = targets.last()
                    }
                }
            } else {
                if (currentTarget == null) {
                    throw IllegalArgumentException("Invalid unpick file, found double indent")
                }
                when (line[0].second) {
                    "param" -> {
                        val index = line[1].second.toInt()
                        val group = line[2].second
                        currentTarget.params.add(UnpickParam(index, group))
                    }
                    "return" -> {
                        val group = line[1].second
                        currentTarget.params.add(UnpickParam(-1, group))
                    }
                }
            }
        }
        val ns = Namespace(nsMapping["source"] ?: "source")
        into.visitHeader(ns.name)

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

            val cg = into.visitConstantGroup(type, ns, setOf())
            if (cg != null) {
                for (const in consts) {
                    cg.visitConstant(const.intlName, const.fieldName, null)
                }
                for (target in targs) {
                    for (param in target.params) {
                        if (param.group != group) continue
                        cg.visitTarget(FullyQualifiedName(ObjectType(target.intlName), NameAndDescriptor(target.targetName, FieldOrMethodDescriptor(target.targetDesc))), param.index)
                    }
                }
            }
        }
    }
}