package xyz.wagyourtail.unimined.mapping.formats.at

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * This reads AT files written in the format found in forge 1.7-current
 */
object ATReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        val cfg = fileName.substringAfterLast('.') in setOf("at", "cfg")
        val name = fileName.substringBeforeLast('.').lowercase()
        return (cfg && name.endsWith("_at") || name.startsWith("accesstransformer"))
    }

    fun String.parseAccess(): Pair<List<AccessFlag>, List<AccessFlag>> {
        val add = mutableListOf<AccessFlag>()
        val remove = mutableListOf<AccessFlag>()
        CharReader("+$this").use {
            while (!it.exhausted()) {
                val addRemove = it.take()
                val str = it.takeUntil { it in "+-" }
                val access = if (str == "f") {
                    AccessFlag.FINAL
                } else {
                    AccessFlag.valueOf(str.uppercase())
                }
                when (addRemove) {
                    '+' -> {
                        add.add(access)
                    }
                    '-' -> {
                        remove.add(access)
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid access transformer, expected + or -, found $addRemove")
                    }
                }
            }
        }
        return add to remove
    }

    fun <T: AccessParentVisitor<T>> AccessParentVisitor<T>.applyAccess(access: Pair<List<AccessFlag>, List<AccessFlag>>, ns: Set<Namespace>) {
        for (flag in access.first) {
            this.visitAccess(AccessType.ADD, flag, AccessConditions.ALL, ns)?.visitEnd()
        }
        for (flag in access.second) {
            this.visitAccess(AccessType.REMOVE, flag, AccessConditions.ALL, ns)?.visitEnd()
        }
    }

    data class ATData(
        val access: Pair<List<AccessFlag>, List<AccessFlag>>,
        val targetClass: InternalName,
        val memberName: String?,
        val memberDesc: String?
    ) {
        fun isMethod() = memberName != null && memberDesc != null
        fun isField() = memberName != null && memberDesc == null
        fun isWildcard() = memberName == "*"

        fun fixedDesc() = MethodDescriptor.read(if (memberName == "<init>") {
            if (memberDesc!!.endsWith(")")) {
                memberDesc + "V"
            } else {
                memberDesc
            }
        } else {
            memberDesc!!
        })

    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

        val ns = Namespace(nsMapping["source"] ?: "source")

        into.use {
            visitHeader(ns.name)
            val data = readData(input)

            for (at in data) {
                visitClass(mapOf(ns to at.targetClass))?.use {
                    if (at.isWildcard()) {
                        if (at.isMethod()) {
                            visitWildcard(WildcardNode.WildcardType.METHOD, emptyMap())?.use {
                                applyAccess(at.access, setOf(ns))
                            }
                        } else {
                            visitWildcard(WildcardNode.WildcardType.FIELD, emptyMap())?.use {
                                applyAccess(at.access, setOf(ns))
                            }
                        }
                    } else {
                        if (at.isMethod()) {
                            visitMethod(mapOf(ns to (at.memberName!! to at.fixedDesc())))?.use {
                                applyAccess(at.access, setOf(ns))
                                visitEnd()
                            }
                        } else {
                            visitField(mapOf(ns to (at.memberName!! to null)))?.use {
                                applyAccess(at.access, setOf(ns))
                            }
                        }

                    }
                }
            }
        }

    }

    fun readData(input: CharReader): List<ATData> {
        val data = mutableListOf<ATData>()
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            if (input.peek() == '#') {
                input.takeLine()
                continue
            }
            if (input.peek()?.isWhitespace() == true) {
                throw IllegalStateException("Unexpected whitespace")
            }

            val access = input.takeNextLiteral { it.isWhitespace() }!!.parseAccess()

            val targetClass = InternalName.read(input.takeUntil { it.isWhitespace() }.replace(".", "/"))
            input.takeNonNewlineWhitespace()
            val memberName = if (input.peek() == '#') null else input.takeUntil { it.isWhitespace() || it == '(' }.ifEmpty { null }
            val memberDesc = if (memberName == null) null else input.takeUntil { it.isWhitespace() }.ifEmpty { null }

            val remaining = input.takeLine().trimStart()
            if (remaining.isNotEmpty() && remaining.first() != '#') {
                throw IllegalArgumentException("Expected newline or comment, found $remaining")
            }

            data.add(ATData(access, targetClass, memberName, memberDesc))
        }
        return data
    }

}