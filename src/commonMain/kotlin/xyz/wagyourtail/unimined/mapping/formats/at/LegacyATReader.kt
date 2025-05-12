package xyz.wagyourtail.unimined.mapping.formats.at

import io.github.oshai.kotlinlogging.KotlinLogging
import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader.ATData
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader.parseAccess
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * This reads AT files written in the format found in forge 1.3-1.6.4
 */
object LegacyATReader : FormatReader {
    private val logger = KotlinLogging.logger {  }

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false

    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        val cfg = fileName.substringAfterLast('.') == "cfg"
        val name = fileName.substringBeforeLast('.').lowercase()
        return (cfg && name.endsWith("_at"))
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val ns = Namespace(nsMapping["source"] ?: "source")
        val data = readData(input, settings.leinient || leinient)
        ATReader.applyData(data, into, ns)
    }

    fun readData(input: CharReader<*>, leinient: Boolean = LegacyATReader.leinient): List<ATReader.ATItem> {
        val data = mutableListOf<ATReader.ATItem>()
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                data.add(ATReader.ATNewline)
                continue
            }

            if (input.peek()?.isWhitespace() == true) {
                input.takeWhitespace()
                continue
            }

            if (input.peek() == '#') {
                data.add(ATReader.ATComment(input.takeLine(), true))

                if (input.peek() == '\n') {
                    input.take()
                }
                continue
            }

            input.mark()
            val access = input.takeNextLiteral { it.isWhitespace() }!!.parseAccess()

            val targetClass = InternalName.read(input.takeUntil { it.isWhitespace() || it == '.' })
            val memberName = if (input.peek() != '.') null else input.takeUntil { it.isWhitespace() || it == '(' }.ifEmpty {
                throw IllegalArgumentException("Expected member name")
            }.substring(1)
            val memberDesc = if (memberName == null) null else input.takeUntil { it.isWhitespace() }.ifEmpty { null }

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
                data.add(ATReader.ATComment(remaining, false))
            }

            if (input.peek() == '\n') {
                input.take()
            }
        }
        return data
    }

}