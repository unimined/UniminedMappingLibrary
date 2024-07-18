package xyz.wagyourtail.unimined.mapping.formats.mcp.v3

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * this reads the methods.csv from mcp 3.0-5.6
 */
object MCPv3MethodReader : FormatReader {

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "methods.csv") return false
        return input.peek().readUtf8Line()?.equals("\"searge\",\"name\",\"notch\",\"sig\",\"notchsig\",\"classname\",\"classnotch\",\"package\",\"side\"") ?: false
    }

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
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
                val searge = input.takeCol().second
                val name = input.takeCol().second
                val notch = input.takeCol().second
                input.takeCol() // sig
                val notchSig = MethodDescriptor.read(input.takeCol().second)
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
                        lastClass?.second?.visitEnd()
                        lastClass = cls to visitClass(mapOf(notchNs to cls))
                    }

                    // searge descriptors have no package remapping,
                    // and so must be ignored due to their difficulty to fix

                    lastClass.second?.visitMethod(
                        mapOf(
                            notchNs to (notch to notchSig),
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