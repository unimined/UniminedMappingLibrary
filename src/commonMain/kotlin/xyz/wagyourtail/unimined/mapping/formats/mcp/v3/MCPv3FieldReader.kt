package xyz.wagyourtail.unimined.mapping.formats.mcp.v3

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * this reads the methods.csv from mcp 3.0-5.6
 */
object MCPv3FieldReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        return inputType.peek().readUtf8Line()?.equals("\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

        val notchNs = Namespace(nsMapping["notch"] ?: "notch")
        val seargeNs = Namespace(nsMapping["searge"] ?: "searge")
        val mcpNs = Namespace(nsMapping["mcp"] ?: "mcp")

        val header = input.takeLine()
        if (header != "\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") {
            throw IllegalArgumentException("invalid header: $header")
        }

        into.visitHeader(notchNs.name, seargeNs.name, mcpNs.name)

        var lastClass: Pair<InternalName, ClassVisitor?>? = null

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = input.takeCol().second
            val name = input.takeCol().second
            val notch =input.takeCol().second
            input.takeCol() // sig
            FieldDescriptor.read(input.takeCol().second) // notchsig
            val className = input.takeCol().second
            var classNotch = input.takeCol().second
            val pkg = input.takeCol().second
            val side = input.takeCol().second

            if (side == "2" || side.toInt() == envType.ordinal) {
                if (className == classNotch) {
                    classNotch = "$pkg/$classNotch"
                }

                val cls = InternalName.read(classNotch)
                if (lastClass?.first != cls) {
                    lastClass = cls to into.visitClass(mapOf(notchNs to cls))
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

    }

}