package xyz.wagyourtail.unimined.mapping.formats.mcp.v3

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * this reads the methods.csv from mcp 3.0-5.6
 */
object MCPv3FieldReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        return input.peek().readUtf8Line()?.equals("\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") ?: false
    }

    override suspend fun readImpl(
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
                FieldDescriptor.read(input.takeCol()!!) // notchsig
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

                    // ignoring notch field descriptor due to
                    // "field_1003_b","mc","b","LMinecraft;","LMinecraft;","LoadingScreenRenderer","io","net/minecraft/src","0"
                    // being annoying to fix the notch descriptor

                    // ignoring searge field descriptor for basically the same reason, they have NO package remapping

                    pushed(MutableFieldMapping(
                        mapOf(
                            notchNs to FieldNameAndDescriptor(notch, null),
                            seargeNs to FieldNameAndDescriptor(searge, null),
                            mcpNs to FieldNameAndDescriptor(name, null)
                        ),
                    )) {}

                }
            }

            lastClass?.second?.let { pop(it) }
        }
        into.end()

    }

}