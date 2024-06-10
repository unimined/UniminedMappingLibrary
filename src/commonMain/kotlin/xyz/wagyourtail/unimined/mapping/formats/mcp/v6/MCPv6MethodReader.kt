package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPv6MethodReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "methods.csv") return false
        return inputType.peek().readUtf8Line()?.startsWith("searge,name,side") ?: false
    }

    private data class FieldData(
        val source: String,
        val target: String,
        val comment: String?
    )

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val header = input.takeLine()
        if (!header.startsWith("searge,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        val data = mutableMapOf<String, Pair<String, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = input.takeCol().second
            val name = input.takeCol().second
            val side = input.takeCol().second
            val comment = input.takeCol().second

            if (side == "2" || side.toInt() == envType.ordinal || envType == EnvType.JOINED) {
                data[searge] = name to comment
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        into.visitHeader(srcNs.name, dstNs.name)

        context?.accept(
            into.delegator(object : NullDelegator() {

                override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                    return default.visitClass(delegate, names)
                }

                override fun visitMethod(
                    delegate: ClassVisitor,
                    names: Map<Namespace, Pair<String, MethodDescriptor?>>
                ): MethodVisitor? {
                    val ns = names[srcNs] ?: return super.visitMethod(delegate, names)
                    val mData = data[ns.first] ?: (ns.first to null)
                    val nameMap = names.toMutableMap()
                    nameMap[dstNs] = mData.first to ns.second
                    val visitor = default.visitMethod(delegate, nameMap)
                    if (mData.second != null) {
                        visitor?.visitJavadoc(mapOf(dstNs to mData.second!!))
                    }
                    return visitor
                }

                override fun visitMethodJavadoc(delegate: MethodVisitor, values: Map<Namespace, String>): CommentVisitor? {
                    return default.visitJavadoc(delegate, values)
                }

            })
        )

    }

}