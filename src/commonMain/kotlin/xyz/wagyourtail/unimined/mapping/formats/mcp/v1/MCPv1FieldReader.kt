package xyz.wagyourtail.unimined.mapping.formats.mcp.v1

import okio.BufferedSource
import okio.use
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

/**
 * this reads the MCP 1-2.12 field csv files
 */
object MCPv1FieldReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "fields.csv") return false
        // check that 4th line starts with "class"
        input.peek().use {
            it.readUtf8Line()
            it.readUtf8Line()
            return it.readUtf8Line()?.startsWith("class") ?: false
        }
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: RootMappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
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

        val data = mutableMapOf<UnqualifiedName, Pair<UnqualifiedName, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            input.takeCol() // clientClsName
            input.takeCol() // empty
            val clientSrg = UnqualifiedName.read(input.takeCol()!!)

            input.takeCol() // serverClsName
            input.takeCol() // empty
            val serverSrg = UnqualifiedName.read(input.takeCol()!!)

            val fieldName = input.takeCol()
            val comment = input.takeCol()

            if (input.peek() != '\n') {
                input.takeRemainingCol()
            }

            if (fieldName.isNullOrEmpty()) {
                continue
            }

            if (envType == EnvType.CLIENT) {
                if (clientSrg.value == "*") continue
                data[clientSrg] = UnqualifiedName.read(fieldName) to comment
            }
            if (envType == EnvType.SERVER) {
                if (serverSrg.value == "*") continue
                data[serverSrg] = UnqualifiedName.read(fieldName) to comment
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
                    val ns = names[srcNs] ?: return null
                    val fData = data[ns.name] ?: return null
                    val nameMap = names.toMutableMap()
                    nameMap[dstNs] = FieldNameAndDescriptor(fData.first, null)
                    val visitor = default.visitField(delegate, nameMap)
                    if (fData.second != null) {
                        visitor?.visitJavadoc(fData.second!!, dstNs)?.visitEnd()
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