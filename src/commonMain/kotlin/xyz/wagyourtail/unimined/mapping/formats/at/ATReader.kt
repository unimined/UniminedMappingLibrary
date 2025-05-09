package xyz.wagyourtail.unimined.mapping.formats.at

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * This reads AT files written in the format found in forge 1.7-current
 */
object ATReader : FormatReader {

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        val cfg = fileName.substringAfterLast('.') in setOf("at", "cfg")
        val name = fileName.substringBeforeLast('.').lowercase()
        return (cfg && name.endsWith("_at") || name.startsWith("accesstransformer"))
    }

    fun String.parseAccess(): Pair<AccessFlag?, TriState> {
        if (!this.contains(Regex("[+-]"))) {
            val accessStr = this.uppercase()
            if (accessStr == "DEFAULT") {
                return null to TriState.LEAVE
            }
            return AccessFlag.valueOf(accessStr) to TriState.LEAVE
        }
        val accessStr = this.substring(0, this.length - 2)
        val access = if (accessStr == "DEFAULT") {
            null
        } else {
            AccessFlag.valueOf(accessStr.uppercase())
        }
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

    private fun <T: AccessParentVisitor<T>> AccessParentVisitor<T>.applyAccess(access: AccessFlag?, final: TriState, ns: Set<Namespace>) {
        if (access != null) {
            this.visitAccess(AccessType.ADD, access, AccessConditions.ALL, ns)?.visitEnd()
        }
        when (final) {
            TriState.ADD -> this.visitAccess(AccessType.ADD, AccessFlag.FINAL, AccessConditions.ALL, ns)?.visitEnd()
            TriState.REMOVE -> this.visitAccess(AccessType.REMOVE, AccessFlag.FINAL, AccessConditions.ALL, ns)?.visitEnd()
            TriState.LEAVE -> {}
        }
    }

    sealed interface ATItem

    data class ATComment(
        val comment: String,
        val newline: Boolean
    ) : ATItem

    object ATNewline : ATItem

    interface ATData : ATItem {

        val access: AccessFlag?
        val final: TriState
        val targetClass: InternalName

        companion object {
            fun fixDesc(memberName: String, memberDesc: String): String {
                return if (memberName == "<init>") {
                    if (memberDesc.endsWith(")")) {
                        memberDesc + "V"
                    } else {
                        memberDesc
                    }
                } else if (!memberDesc.endsWith(";") && memberDesc.substringAfterLast(")").startsWith("L")) {
                    "$memberDesc;"
                } else {
                    memberDesc
                }
            }

            operator fun invoke(
                access: AccessFlag?,
                final: TriState,
                targetClass: InternalName,
                memberName: String?,
                memberDesc: String?
            ) =
                if (memberName == null && memberDesc == null) {
                    ATDataClass(access, final, targetClass)
                } else if (memberName == "*") {
                    if (memberDesc != null) {
                        ATDataMethodWildcard(
                            access, final, targetClass,
                            if (memberDesc == "()") null else MethodDescriptor.read(fixDesc(memberName, memberDesc))
                        )
                    } else {
                        ATDataFieldWildcard(access, final, targetClass)
                    }
                } else if (memberDesc == null) {
                    ATDataField(access, final, targetClass, UnqualifiedName.read(memberName!!))
                } else {
                    ATDataMethod(access, final, targetClass, UnqualifiedName.read(memberName!!), MethodDescriptor.read(fixDesc(memberName, memberDesc)))
                }
        }
    }

    data class ATDataClass(
        override val access: AccessFlag?,
        override val final: TriState,
        override val targetClass: InternalName,
    ) : ATData

    data class ATDataMethod(
        override val access: AccessFlag?,
        override val final: TriState,
        override val targetClass: InternalName,
        val memberName: UnqualifiedName,
        val memberDesc: MethodDescriptor
    ) : ATData

    data class ATDataField(
        override val access: AccessFlag?,
        override val final: TriState,
        override val targetClass: InternalName,
        val memberName: UnqualifiedName
    ) : ATData

    interface ATDataWildcard : ATData

    data class ATDataFieldWildcard(
        override val access: AccessFlag?,
        override val final: TriState,
        override val targetClass: InternalName
    ) : ATDataWildcard

    data class ATDataMethodWildcard(
        override val access: AccessFlag?,
        override val final: TriState,
        override val targetClass: InternalName,
        val memberDesc: MethodDescriptor?
    ) : ATDataWildcard

    enum class TriState {
        ADD,
        REMOVE,
        LEAVE
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        val ns = Namespace(nsMapping["source"] ?: "source")
        val data = readData(input)
        applyData(data, into, ns)
    }

    fun readData(input: CharReader<*>): List<ATItem> {
        val data = mutableListOf<ATItem>()
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                data.add(ATNewline)
                continue
            }

            if (input.peek()?.isWhitespace() == true) {
                input.takeWhitespace()
                continue
            }

            if (input.peek() == '#') {
                data.add(ATComment(input.takeLine(), true))

                if (input.peek() == '\n') {
                    input.take()
                }
                continue
            }

            val access = input.takeNextLiteral { it.isWhitespace() }!!.parseAccess()

            val targetClass = InternalName.read(input.takeUntil { it.isWhitespace() }.replace(".", "/"))
            input.takeNonNewlineWhitespace()
            val memberName = if (input.peek() == '#') null else input.takeUntil { it.isWhitespace() || it == '(' }.ifEmpty { null }
            val memberDesc = if (memberName == null) null else input.takeUntil { it.isWhitespace() }.ifEmpty { null }?.replace(".", "/")

            data.add(ATData(access.first, access.second, targetClass, memberName, memberDesc))

            val remaining = input.takeLine().trimStart()
            if (remaining.isNotEmpty()) {
                if (remaining.first() != '#') {
                    throw IllegalArgumentException("Expected newline or comment, found $remaining")
                }
                data.add(ATComment(remaining, false))
            }

            if (input.peek() == '\n') {
                input.take()
            }
        }
        return data
    }

    fun applyData(data: List<ATItem>, into: MappingVisitor, ns: Namespace) {
        val nsSet = setOf(ns)
        into.use {
            visitHeader(ns.name)
            for (at in data) {
                if (at !is ATData) continue
                visitClass(mapOf(ns to at.targetClass))?.use {
                    if (at is ATDataClass) {
                        applyAccess(at.access, at.final, nsSet)
                    } else {
                        if (at is ATDataWildcard) {
                            if (at is ATDataMethodWildcard) {
                                val map = if (at.memberDesc != null) {
                                    mapOf(ns to FieldOrMethodDescriptor(at.memberDesc))
                                } else {
                                    emptyMap()
                                }
                                visitWildcard(WildcardNode.WildcardType.METHOD, map)?.use {
                                    applyAccess(at.access, at.final, nsSet)
                                }
                            } else {
                                visitWildcard(WildcardNode.WildcardType.FIELD, emptyMap())?.use {
                                    applyAccess(at.access, at.final, nsSet)
                                }
                            }
                        } else {
                            if (at is ATDataMethod) {
                                visitMethod(mapOf(ns to (at.memberName.toString() to at.memberDesc)))?.use {
                                    applyAccess(at.access, at.final, nsSet)
                                    visitEnd()
                                }
                            } else {
                                if (at !is ATDataField) error("unknown ATData type $at")
                                visitField(mapOf(ns to (at.memberName.toString() to null)))?.use {
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