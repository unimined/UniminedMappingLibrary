package xyz.wagyourtail.unimined.mapping.formats.mcp.v1

import okio.BufferedSource
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

/**
 * this reads the MCP 1-2.12 field csv files
 */
object MCPv1FieldReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        // check that 4th line starts with "class"
        inputType.peek().use {
            it.readUtf8Line()
            it.readUtf8Line()
            return it.readUtf8Line()?.startsWith("class") ?: false
        }
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        input.takeLine()
        input.take()
        input.takeLine()
        input.take()
        val l3 = input.takeLine()
        input.take()

        if (!l3.startsWith("Class")) {
            throw IllegalArgumentException("invalid header for older method csv")
        }

        val data = mutableMapOf<String, Pair<String, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            input.takeCol() // clientClsName
            input.takeCol() // empty
            val clientSrg = input.takeCol()

            input.takeCol() // serverClsName
            input.takeCol() // empty
            val serverSrg = input.takeCol()

            val fieldName = input.takeCol()
            val comment = input.takeCol()

            if (input.peek() != '\n') {
                input.takeRemainingCol()
            }

            if (fieldName.second.isEmpty()) {
                continue
            }

            if (envType == EnvType.CLIENT) {
                if (clientSrg.second == "*") continue
                data[clientSrg.second] = fieldName.second to comment.second
            }
            if (envType == EnvType.SERVER) {
                if (serverSrg.second == "*") continue
                data[serverSrg.second] = fieldName.second to comment.second
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

                override fun visitField(
                    delegate: ClassVisitor,
                    names: Map<Namespace, Pair<String, FieldDescriptor?>>
                ): FieldVisitor? {
                    val ns = names[srcNs] ?: return super.visitField(delegate, names)
                    val fData = data[ns.first] ?: (ns.first to null)
                    val nameMap = names.toMutableMap()
                    nameMap[dstNs] = fData.first to ns.second
                    val visitor = default.visitField(delegate, nameMap)
                    if (fData.second != null) {
                        visitor?.visitJavadoc(fData.second!!, dstNs, emptySet())?.visitEnd()
                    }
                    return visitor
                }

                override fun visitFieldJavadoc(
                    delegate: FieldVisitor,
                    value: String,
                    baseNs: Namespace,
                    namespaces: Set<Namespace>
                ): JavadocVisitor? {
                    return default.visitFieldJavadoc(delegate, value, baseNs, namespaces)
                }

            })
        )

    }
}