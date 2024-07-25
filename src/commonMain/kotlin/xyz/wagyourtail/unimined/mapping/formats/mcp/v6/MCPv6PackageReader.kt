package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object MCPv6PackageReader : FormatReader {
    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "packages.csv") return false
        return input.peek().readUtf8Line()?.startsWith("class,package") ?: false
    }

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        val header = input.takeLine()
        if (!header.startsWith("class,package")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        into.use {
            visitHeader(srcNs.name, dstNs.name)

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val cls = input.takeCol().second
                val pkg = input.takeCol().second

                into.visitClass(mapOf(
                    srcNs to InternalName.read("net/minecraft/src/$cls"),
                    dstNs to InternalName.read("$pkg/$cls")
                ))?.visitEnd()
            }
        }
    }
}