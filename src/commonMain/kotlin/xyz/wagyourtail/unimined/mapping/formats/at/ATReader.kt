package xyz.wagyourtail.unimined.mapping.formats.at

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*

/**
 * This reads AT files written in the format found in forge 1.7-current
 */
object ATReader : FormatReader {
    private val logger = KotlinLogging.logger {  }

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false

    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        val cfg = fileName.substringAfterLast('.') in setOf("at", "cfg")
        val name = fileName.substringBeforeLast('.').lowercase()
        return (cfg && name.endsWith("_at") || name.startsWith("accesstransformer"))
    }

    fun String.parseAccess(): Pair<AccessFlag, TriState> {
        if (!this.contains(Regex("[+-]"))) {
            val accessStr = this.uppercase()
            return AccessFlag.valueOf(accessStr) to TriState.LEAVE
        }
        val accessStr = this.substring(0, this.length - 2).uppercase()
        val access = AccessFlag.valueOf(accessStr)

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

    private fun AccessParentMappingVisitor.applyAccess(access: AccessFlag, final: TriState) {
        this.visitAccess(AccessType.ADD, access, AccessConditions.ALL)?.visitEnd()
        when (final) {
            TriState.ADD -> this.visitAccess(AccessType.ADD, AccessFlag.FINAL, AccessConditions.ALL)?.visitEnd()
            TriState.REMOVE -> this.visitAccess(AccessType.REMOVE, AccessFlag.FINAL, AccessConditions.ALL)?.visitEnd()
            TriState.LEAVE -> {}
        }
    }

    sealed interface ATItem

    data class ATComment(
        val comment: String,
        val newline: Boolean
    ) : ATItem

    data object ATNewline : ATItem

    interface ATData : ATItem {

        val access: AccessFlag
        val final: TriState
        val targetClass: InternalName

        companion object {
            fun fixDesc(memberName: String, memberDesc: String): String {
                return if (memberName == "<init>" && memberDesc.endsWith(")")) {
                    memberDesc + "V"
                } else {
                    memberDesc
                }
            }

            operator fun invoke(
                access: AccessFlag,
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
        override val access: AccessFlag,
        override val final: TriState,
        override val targetClass: InternalName,
    ) : ATData

    data class ATDataMethod(
        override val access: AccessFlag,
        override val final: TriState,
        override val targetClass: InternalName,
        val memberName: UnqualifiedName,
        val memberDesc: MethodDescriptor
    ) : ATData {
        val member = memberName.withMethodDesc(memberDesc)
    }

    data class ATDataField(
        override val access: AccessFlag,
        override val final: TriState,
        override val targetClass: InternalName,
        val memberName: UnqualifiedName
    ) : ATData {
        val member = memberName.withFieldDesc(null)
    }

    interface ATDataWildcard : ATData

    data class ATDataFieldWildcard(
        override val access: AccessFlag,
        override val final: TriState,
        override val targetClass: InternalName
    ) : ATDataWildcard

    data class ATDataMethodWildcard(
        override val access: AccessFlag,
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
        into: RootMappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val ns = Namespace(nsMapping["source"] ?: "source")
        val data = readData(input, settings.leinient || leinient)
        applyData(data, into, ns)
    }

    fun readData(input: CharReader<*>, leinient: Boolean = ATReader.leinient): List<ATItem> {
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

            input.mark()
            val access = input.takeNextLiteral { it.isWhitespace() }!!.parseAccess()

            val targetClass = InternalName.read(input.takeUntil { it.isWhitespace() }.replace(".", "/"))
            input.takeNonNewlineWhitespace()
            val memberName = if (input.peek() == '#') null else input.takeUntil { it.isWhitespace() || it == '(' }.ifBlank { null }
            val memberDesc = if (memberName == null) null else input.takeUntil { it.isWhitespace() }.ifBlank { null }?.replace(".", "/")

            try {
                data.add(ATData(access.first, access.second, targetClass, memberName, memberDesc))
            } catch (e: Exception) {
                if (leinient) {
                    logger.warn(e) {
                        input.reset()
                        val line = input.takeLine()
                        "Failed to parse at line (skipping): ${line}"
                    }
                } else {
                    throw e
                }
            }

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

    fun applyData(data: List<ATItem>, into: RootMappingVisitor, ns: Namespace) {
        val nsSet = setOf(ns)
        into.use {
            visitHeader(ns.name)
            for (at in data) {
                if (at !is ATData) continue
                visitClass(mapOf(ns to at.targetClass))?.use {
                    if (at is ATDataClass) {
                        applyAccess(at.access, at.final)
                    } else {
                        if (at is ATDataWildcard) {
                            if (at is ATDataMethodWildcard) {
                                val map = if (at.memberDesc != null) {
                                    mapOf(ns to at.memberDesc)
                                } else {
                                    emptyMap()
                                }
                                visitWildcard(WildcardType.METHOD, map)?.use {
                                    applyAccess(at.access, at.final)
                                }
                            } else {
                                visitWildcard(WildcardType.FIELD, emptyMap())?.use {
                                    applyAccess(at.access, at.final)
                                }
                            }
                        } else {
                            if (at is ATDataMethod) {
                                visitMethod(mapOf(ns to at.member))?.use {
                                    applyAccess(at.access, at.final)
                                }
                            } else {
                                if (at !is ATDataField) error("unknown ATData type $at")
                                visitField(mapOf(ns to at.member))?.use {
                                    applyAccess(at.access, at.final)
                                }
                            }

                        }
                    }
                }
            }
        }
    }

}