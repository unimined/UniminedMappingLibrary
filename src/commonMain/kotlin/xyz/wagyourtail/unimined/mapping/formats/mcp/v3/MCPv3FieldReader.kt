package xyz.wagyourtail.unimined.mapping.formats.mcp.v3

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * this reads the methods.csv from mcp 3.0-5.6
 */
object MCPv3FieldReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        return input.peek().readUtf8Line()?.equals("\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") ?: false
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {

        val notchNs = Namespace(nsMapping["notch"] ?: "notch")
        val seargeNs = Namespace(nsMapping["searge"] ?: "searge")
        val mcpNs = Namespace(nsMapping["mcp"] ?: "mcp")

        val header = input.takeLine()
        if (header != "\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") {
            throw IllegalArgumentException("invalid header: $header")
        }

        into.use {
            visitHeader(notchNs.name, seargeNs.name, mcpNs.name)

            var lastClass: Pair<InternalName, ClassVisitor?>? = null

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val searge = input.takeCol()!!
                val name = input.takeCol()!!
                val notch = input.takeCol()!!
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
                        lastClass?.second?.visitEnd()
                        lastClass = cls to visitClass(mapOf(notchNs to cls))
                    }

                    // ignoring notch field descriptor due to
                    // "field_1003_b","mc","b","LMinecraft;","LMinecraft;","LoadingScreenRenderer","io","net/minecraft/src","0"
                    // being annoying to fix the notch descriptor

                    // ignoring searge field descriptor for basically the same reason, they have NO package remapping

                    lastClass.second?.visitField(
                        mapOf(
                            notchNs to (notch to null),
                            seargeNs to (searge to null),
                            mcpNs to (name to null)
                        ),
                    )

                }
            }

            lastClass?.second?.visitEnd()
        }

    }

}