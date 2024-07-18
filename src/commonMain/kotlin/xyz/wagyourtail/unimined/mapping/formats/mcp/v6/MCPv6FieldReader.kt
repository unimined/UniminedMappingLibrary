package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPv6FieldReader : FormatReader {

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        return input.peek().readUtf8Line()?.startsWith("searge,name,side") ?: false
    }

    private data class FieldData(
        val source: String,
        val target: String,
        val comment: String?
    )

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
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
                    val fdata = data[ns.first] ?: (ns.first to null)
                    val nameMap = names.toMutableMap()
                    nameMap[dstNs] = fdata.first to ns.second
                    val visitor = default.visitField(delegate, nameMap)
                    if (fdata.second != null) {
                        visitor?.visitJavadoc(fdata.second!!, dstNs, emptySet())?.visitEnd()
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