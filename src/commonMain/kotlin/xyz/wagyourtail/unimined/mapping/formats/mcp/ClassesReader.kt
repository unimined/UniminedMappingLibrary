package xyz.wagyourtail.unimined.mapping.formats.mcp

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * this reads the classes.csv from mcp 3.0-5.6
 */
object ClassesReader : FormatReader {
    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        val fileName = fileName.substringAfterLast('/') == "classes.csv"
        if (!fileName) return false
        return inputType.peek().readUtf8Line()?.startsWith("\"name\",\"notch\"") ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: MappingTree?,
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
                val srcCls = InternalName.read(srcName.second)
                into.visitClass(mapOf(srcNs to srcCls, dstNs to dstCls))
            }
        }
    }

}