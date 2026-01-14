package xyz.wagyourtail.unimined.mapping.formats.mcp.v1

import okio.BufferedSource
import okio.use
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.JavadocMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.RootMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

/**
 * this reads the MCP 1-2.12 method csv files
 */
object MCPv1MethodReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

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
        input.takeLine()
        input.take()
        val l4 = input.takeLine()
        input.take()

        if (!l4.startsWith("class")) {
            throw IllegalArgumentException("invalid header for older method csv")
        }

        val data = mutableMapOf<UnqualifiedName, Pair<UnqualifiedName, String?>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            input.takeCol() // clientClsName
            val clientSrg = UnqualifiedName.read(input.takeCol()!!)

            input.takeCol() // serverClsName
            val serverSrg = UnqualifiedName.read(input.takeCol()!!)

            val methodName = input.takeCol()
            val comment = input.takeCol()

            if (input.peek() != '\n') {
                input.takeRemainingCol()
            }

            if (methodName.isNullOrEmpty()) {
                continue
            }

            if (envType == EnvType.CLIENT) {
                if (clientSrg.value == "*") continue
                data[clientSrg] = UnqualifiedName.read(methodName) to comment
            }
            if (envType == EnvType.SERVER) {
                if (serverSrg.value == "*") continue
                data[serverSrg] = UnqualifiedName.read(methodName) to comment
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

                override fun visitMethod(
                    delegate: ClassMappingVisitor,
                    names: Map<Namespace, MethodNameAndDescriptor>
                ): MethodMappingVisitor? {
                    val ns = names[srcNs] ?: return null
                    val mData = data[ns.name] ?: return null
                    val nameMap = names.toMutableMap()
                    nameMap[dstNs] = MethodNameAndDescriptor(mData.first, null)
                    val visitor = default.visitMethod(delegate, nameMap)
                    if (mData.second != null) {
                        visitor?.visitJavadoc(mData.second!!, dstNs)?.visitEnd()
                    }
                    return visitor
                }

                override fun visitMethodJavadoc(
                    delegate: MethodMappingVisitor,
                    value: String,
                    baseNs: Namespace
                ): JavadocMappingVisitor? {
                    return default.visitMethodJavadoc(delegate, value, baseNs)
                }

            })
        )

        into.visitEnd()

    }

}