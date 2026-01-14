package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.FieldMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.RootMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPv6FieldReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

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
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: RootMappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val header = input.takeLine()
        if (!header.startsWith("searge,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        val data = mutableMapOf<UnqualifiedName, Pair<UnqualifiedName, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = UnqualifiedName.read(input.takeCol()!!)
            val name = UnqualifiedName.read(input.takeCol()!!)
            val side = input.takeCol()!!
            val comment = input.takeCol()

            if (side == "2" || side.toInt() == envType.ordinal || envType == EnvType.JOINED) {
                data[searge] = name to comment
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.accept(
            into.delegator(object : NullDelegator() {


                override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
                    val ns = setOf(*namespaces, srcNs, dstNs)
                    default.visitHeader(delegate, *ns.toTypedArray())
                }

                override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
                    return default.visitClass(delegate, names)
                }


                override fun visitField(
                    delegate: ClassMappingVisitor,
                    names: Map<Namespace, FieldNameAndDescriptor>
                ): FieldMappingVisitor? {
                    val ns = names[srcNs] ?: return super.visitField(delegate, names)
                    val fdata = data[ns.name] ?: return super.visitField(delegate, names)
                    val nameMap = names.toMutableMap()
                    nameMap[dstNs] = FieldNameAndDescriptor(fdata.first, null)
                    val visitor = default.visitField(delegate, nameMap)
                    if (fdata.second != null) {
                        visitor?.visitJavadoc(fdata.second!!, dstNs)?.visitEnd()
                    }
                    return visitor
                }

                override fun visitFieldJavadoc(
                    delegate: FieldMappingVisitor,
                    value: String,
                    baseNs: Namespace
                ): JavadocMappingVisitor? {
                    return default.visitFieldJavadoc(delegate, value, baseNs)
                }

            })
        )

    }

}