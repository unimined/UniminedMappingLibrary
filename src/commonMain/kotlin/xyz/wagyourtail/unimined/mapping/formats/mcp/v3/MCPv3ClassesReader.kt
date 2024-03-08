package xyz.wagyourtail.unimined.mapping.formats.mcp.v3

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * this reads the classes.csv from mcp 3.0-5.6
 */
object MCPv3ClassesReader : FormatReader {
    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "classes.csv") return false
        return inputType.peek().readUtf8Line()?.startsWith("\"name\",\"notch\"") ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val srcNs = Namespace(nsMapping["notch"] ?: "notch")
        val dstNs = Namespace(nsMapping["searge"] ?: "searge")

        val header = input.takeLine()
        if (header != "\"name\",\"notch\",\"supername\",\"package\",\"side\"") {
            throw IllegalArgumentException("invalid header: $header")
        }

        into.visitHeader(srcNs.name, dstNs.name)

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val dstName = input.takeCol()
            val srcName = input.takeCol()
            input.takeCol() // supername
            val pkg = input.takeCol()
            val side = input.takeCol()

            if (side.second == "2" || side.second.toInt() == envType.ordinal) {
                val dstCls = InternalName.read(if (pkg.second.isNotEmpty()) pkg.second + "/" + dstName.second else dstName.second)
                val srcCls = InternalName.read(if (dstName.second == srcName.second && pkg.second.isNotEmpty()) "${pkg.second}/${srcName.second}" else srcName.second)
                into.visitClass(mapOf(srcNs to srcCls, dstNs to dstCls))
            }
        }
    }

}