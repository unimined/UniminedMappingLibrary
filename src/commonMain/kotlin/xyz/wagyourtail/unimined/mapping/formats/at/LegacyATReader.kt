package xyz.wagyourtail.unimined.mapping.formats.at

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader.applyAccess
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader.parseAccess
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
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
                val memberName = if (input.peek() == '.') input.takeUntil { it.isWhitespace() || it == '(' } else null
                val memberDesc =
                    if (input.peek() == '(' && memberName != null) input.takeNextLiteral { it.isWhitespace() } else null

                val remaining = input.takeLine().trimStart()
                if (remaining.isNotEmpty() && remaining.first() != '#') {
                    throw IllegalArgumentException("Expected newline or comment, found $remaining")
                }

                visitClass(mapOf(ns to targetClass))?.use {
                    if (memberName == null) {
                        applyAccess(access, setOf(ns))
                    } else if (memberDesc == null) {
                        visitField(mapOf(ns to (memberName to null)))?.use {
                            applyAccess(access, setOf(ns))
                        }
                    } else {
                        visitMethod(mapOf(ns to (memberName to MethodDescriptor.read(memberDesc))))?.use {
                            applyAccess(access, setOf(ns))
                            visitEnd()
                        }
                    }
                }
            }
        }

    }

}