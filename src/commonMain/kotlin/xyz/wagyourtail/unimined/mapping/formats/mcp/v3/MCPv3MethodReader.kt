package xyz.wagyourtail.unimined.mapping.formats.mcp.v3

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * this reads the methods.csv from mcp 3.0-5.6
 */
object MCPv3MethodReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "methods.csv") return false
        return input.peek().readUtf8Line()?.equals("\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") ?: false
    }

    override suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
        
        val notchNs = Namespace(nsMapping["notch"] ?: "notch")
        val seargeNs = Namespace(nsMapping["searge"] ?: "searge")
        val mcpNs = Namespace(nsMapping["mcp"] ?: "mcp")

        val header = input.takeLine()
        if (header != "\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") {
            throw IllegalArgumentException("invalid header: $header")
        }

        with (into) {
            header(listOf(notchNs, seargeNs, mcpNs))

            var lastClass: Pair<InternalName, MappingElement?>? = null

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val searge = UnqualifiedName.read(input.takeCol()!!)
                val name = UnqualifiedName.read(input.takeCol()!!)
                val notch = UnqualifiedName.read(input.takeCol()!!)
                input.takeCol() // sig
                val notchSig = MethodDescriptor.read(input.takeCol()!!)
                val className = input.takeCol()
                var classNotch = input.takeCol()!!
                val pkg = input.takeCol()
                val side = input.takeCol()!!

                if (side == "2" || side.toInt() == envType.ordinal) {
                    if (className == classNotch) {
                        classNotch = "$pkg/$classNotch"
                    }

                    val cls = InternalName.read(classNotch)
                    if (lastClass?.first != cls) {
                        lastClass?.second?.let { pop(it) }
                        lastClass = cls to push(MutableClassMapping(mapOf(notchNs to cls)))
                    }

                    // searge descriptors have no package remapping,
                    // and so must be ignored due to their difficulty to fix

                    pushed(MutableMethodMapping(
                        mapOf(
                            notchNs to MethodNameAndDescriptor(notch, notchSig),
                            seargeNs to MethodNameAndDescriptor(searge, null),
                            mcpNs to MethodNameAndDescriptor(name, null)
                        ),
                    )) {}

                }
            }

            lastClass?.second?.let { pop(it) }
        }
        into.end()

    }

}