package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPv6ParamReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "params.csv") return false
        return input.peek().readUtf8Line()?.startsWith("param,name,side") ?: false
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
        if (!header.startsWith("param,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        val data = mutableMapOf<UnqualifiedName, UnqualifiedName>()
        val paramsByNumber = mutableMapOf<Int, MutableMap<UnqualifiedName, UnqualifiedName>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = UnqualifiedName.read(input.takeCol()!!)
            val name = UnqualifiedName.read(input.takeCol()!!)
            val side = input.takeCol()!!

            if (side == "2" || side.toInt() == envType.ordinal || envType == EnvType.JOINED) {
                data[searge] = name

                if (searge.value.matches(Regex("p_\\d+_\\d+_"))) {
                    val mid = searge.value.split('_')[1].toInt()
                    paramsByNumber.getOrPut(mid) { mutableMapOf() }[searge] = name
                }
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.accept(
            into.delegator(object : NullDelegator() {

                override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
                    val ns = setOf(*namespaces, srcNs, dstNs)
                    super.visitHeader(delegate, *ns.toTypedArray())
                }

                override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
                    return default.visitClass(delegate, names)
                }

                override fun visitMethod(
                    delegate: ClassMappingVisitor,
                    names: Map<Namespace, MethodNameAndDescriptor>
                ): MethodMappingVisitor? {
                    return default.visitMethod(delegate, names)?.also { mv ->
                        if (names[srcNs]?.name?.value?.matches(Regex("func_\\d+_.+")) == true) {
                            val mid = names[srcNs]?.name?.value?.split('_')?.get(1)?.toInt()
                            if (mid != null) {
                                for ((searge, name) in paramsByNumber[mid] ?: emptyMap()) {
                                    val lvOrd = searge.value.split('_')[2].toInt()
                                    mv.visitParameter(
                                        null,
                                        lvOrd,
                                        mapOf(srcNs to searge, dstNs to name)
                                    )?.visitEnd()
                                }
                            }
                        }
                    }
                }

                override fun visitParameter(
                    delegate: InvokableMappingVisitor,
                    index: Int?,
                    lvOrd: Int?,
                    names: Map<Namespace, UnqualifiedName>
                ): ParameterMappingVisitor? {
                    val searge = names[srcNs] ?: return null
                    val name = data[searge] ?: return null
                    return default.visitParameter(delegate, index, lvOrd, mapOf(dstNs to name))
                }

            })
        )

    }

}