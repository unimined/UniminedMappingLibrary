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

    fun String.parseAccess(): Pair<AccessFlag, TriState> {
        if (!this.contains(Regex("[+-]"))) {
            return AccessFlag.valueOf(this.uppercase()) to TriState.LEAVE
        }
        val access = this.substring(0, this.length - 2).let { AccessFlag.valueOf(it.uppercase()) }
        if (access !in AccessFlag.visibility) {
            throw IllegalArgumentException("Unexpected access flag $access")
        }
        val final = when (this.substring(this.length - 2).lowercase()) {
            "+f" -> TriState.ADD
            "-f" -> TriState.REMOVE
            else -> throw IllegalArgumentException("Unexpected character ${this.last()}")
        }
        return access to final
    }

    fun <T: AccessParentVisitor<T>> AccessParentVisitor<T>.applyAccess(access: AccessFlag, final: TriState, ns: Set<Namespace>) {
        this.visitAccess(AccessType.ADD, access, AccessConditions.ALL, ns)?.visitEnd()
        when (final) {
            TriState.ADD -> this.visitAccess(AccessType.ADD, AccessFlag.FINAL, AccessConditions.ALL, ns)?.visitEnd()
            TriState.REMOVE -> this.visitAccess(AccessType.REMOVE, AccessFlag.FINAL, AccessConditions.ALL, ns)?.visitEnd()
            TriState.LEAVE -> {}
        }
    }

    data class ATData(
        val access: AccessFlag,
        val final: TriState,
        val targetClass: InternalName,
        val memberName: String?,
        val memberDesc: String?
    ) {
        fun isClass() = memberName == null && memberDesc == null
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

    enum class TriState {
        ADD,
        REMOVE,
        LEAVE
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val ns = Namespace(nsMapping["source"] ?: "source")
        val data = readData(input)
        applyData(data, into, ns)
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

            data.add(ATData(access.first, access.second, targetClass, memberName, memberDesc))
        }
        return data
    }

    fun applyData(data: List<ATData>, into: MappingVisitor, ns: Namespace) {
        val nsSet = setOf(ns)
        into.use {
            visitHeader(ns.name)
            for (at in data) {
                visitClass(mapOf(ns to at.targetClass))?.use {
                    if (at.isClass()) {
                        applyAccess(at.access, at.final, nsSet)
                    } else {
                        if (at.isWildcard()) {
                            if (at.isMethod()) {
                                visitWildcard(WildcardNode.WildcardType.METHOD, emptyMap())?.use {
                                    applyAccess(at.access, at.final, nsSet)
                                }
                            } else {
                                visitWildcard(WildcardNode.WildcardType.FIELD, emptyMap())?.use {
                                    applyAccess(at.access, at.final, nsSet)
                                }
                            }
                        } else {
                            if (at.isMethod()) {
                                visitMethod(mapOf(ns to (at.memberName!! to at.fixedDesc())))?.use {
                                    applyAccess(at.access, at.final, nsSet)
                                    visitEnd()
                                }
                            } else {
                                visitField(mapOf(ns to (at.memberName!! to null)))?.use {
                                    applyAccess(at.access, at.final, nsSet)
                                }
                            }

                        }
                    }
                }
            }
        }
    }

}