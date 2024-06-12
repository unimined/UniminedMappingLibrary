package xyz.wagyourtail.unimined.mapping.formats.at

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader.ATData
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader.applyAccess
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader.parseAccess
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * This reads AT files written in the format found in forge 1.3-1.6.4
 */
object LegacyATReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        val cfg = fileName.substringAfterLast('.') == "cfg"
        val name = fileName.substringBeforeLast('.').lowercase()
        return (cfg && name.endsWith("_at"))
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

            val targetClass = InternalName.read(input.takeUntil { it.isWhitespace() || it == '.' })
            val memberName = if (input.peek() != '.') null else input.takeUntil { it.isWhitespace() || it == '(' }.ifEmpty {
                throw IllegalArgumentException("Expected member name")
            }
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