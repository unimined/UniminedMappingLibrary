package xyz.wagyourtail.unimined.mapping.formats.mcp.v1

import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

/**
 * this reads the MCP 1-2.12 method csv files
 */
object MCPv1MethodReader : FormatReader {

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "methods.csv") return false
        // check that 4th line starts with "class"
        input.peek().use {
            it.readUtf8Line()
            it.readUtf8Line()
            it.readUtf8Line()
            return it.readUtf8Line()?.startsWith("class") ?: false
        }
    }

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        input.takeLine()
        input.take()
        input.takeLine()
        input.take()
        input.takeLine()
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
            input.takeCol() // clientClsName
            val clientSrg = input.takeCol()

            input.takeCol() // serverClsName
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

        context?.accept(
            into.delegator(object : NullDelegator() {

                override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
                    val ns = setOf(*namespaces, srcNs.name, dstNs.name)
                    default.visitHeader(delegate, *ns.toTypedArray())
                }

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
                        visitor?.visitJavadoc(mData.second!!, dstNs, emptySet())?.visitEnd()
                    }
                    return visitor
                }

                override fun visitMethodJavadoc(
                    delegate: MethodVisitor,
                    value: String,
                    baseNs: Namespace,
                    namespaces: Set<Namespace>
                ): JavadocVisitor? {
                    return default.visitMethodJavadoc(delegate, value, baseNs, namespaces)
                }

            })
        )

        into.visitEnd()

    }

}