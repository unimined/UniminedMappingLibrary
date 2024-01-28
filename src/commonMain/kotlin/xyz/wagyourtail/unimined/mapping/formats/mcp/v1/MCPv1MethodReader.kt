package xyz.wagyourtail.unimined.mapping.formats.mcp.v1

import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.*

/**
 * this reads the MCP 1-2.12 method csv files
 */
object MCPv1MethodReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        val fileName = fileName.substringAfterLast('/') == "methods.csv"
        if (!fileName) return false
        // check that 4th line starts with "class"
        inputType.peek().use {
            it.readUtf8Line()
            it.readUtf8Line()
            it.readUtf8Line()
            return it.readUtf8Line()?.startsWith("class") ?: false
        }
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: MappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val l1 = input.takeLine()
        input.take()
        val l2 = input.takeLine()
        input.take()
        val l3 = input.takeLine()
        input.take()
        val l4 = input.takeLine()
        input.take()

        if (!l4.startsWith("class")) {
            throw IllegalArgumentException("invalid header for older method csv")
        }

        val data = mutableMapOf<String, Pair<String, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val clientClsName = input.takeCol()
            val clientSrg = input.takeCol()

            val serverClsName = input.takeCol()
            val serverSrg = input.takeCol()

            val methodName = input.takeCol()
            val comment = input.takeCol()

            if (input.peek() != '\n') {
                input.takeRemainingCol()
            }

            if (methodName.second.isEmpty()) {
                continue
            }

            if (envType == EnvType.CLIENT) {
                if (clientSrg.second == "*") continue
                data[clientSrg.second] = methodName.second to comment.second
            }
            if (envType == EnvType.SERVER) {
                if (serverSrg.second == "*") continue
                data[serverSrg.second] = methodName.second to comment.second
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
                    val data = data[ns.first] ?: (ns.first to null)
                    val names = names.toMutableMap()
                    names[dstNs] = data.first to ns.second
                    val visitor = default.visitMethod(delegate, names)
                    if (data.second != null) {
                        visitor?.visitComment(mapOf(dstNs to data.second!!))
                    }
                    return visitor
                }

                override fun visitMethodComment(delegate: MethodVisitor, values: Map<Namespace, String>): CommentVisitor? {
                    return default.visitComment(delegate, values)
                }

            })
        )

    }

}