package xyz.wagyourtail.unimined.mapping.formats.mcp.v6

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object MCPv6ParamReader : FormatReader {

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
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        val header = input.takeLine()
        if (!header.startsWith("param,name,side")) {
            throw IllegalArgumentException("invalid header: $header")
        }

        val data = mutableMapOf<String, String>()
        val paramsByNumber = mutableMapOf<Int, MutableMap<String, String>>()

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val searge = input.takeCol()!!
            val name = input.takeCol()!!
            val side = input.takeCol()!!

            if (side == "2" || side.toInt() == envType.ordinal || envType == EnvType.JOINED) {
                data[searge] = name

                if (searge.matches(Regex("p_\\d+_\\d+_"))) {
                    val mid = searge.split('_')[1].toInt()
                    paramsByNumber.getOrPut(mid) { mutableMapOf() }[searge] = name
                }
            }

        }

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")
        val dstNs = Namespace(nsMapping["mcp"] ?: "mcp")

        context?.accept(
            into.delegator(object : NullDelegator() {

                override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
                    val ns = setOf(*namespaces, srcNs.name, dstNs.name)
                    super.visitHeader(delegate, *ns.toTypedArray())
                }

                override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                    return default.visitClass(delegate, names)
                }

                override fun visitMethod(
                    delegate: ClassVisitor,
                    names: Map<Namespace, Pair<String, MethodDescriptor?>>
                ): MethodVisitor? {
                    return default.visitMethod(delegate, names)?.also { mv ->
                        if (names[srcNs]?.first?.matches(Regex("func_\\d+_.+")) == true) {
                            val mid = names[srcNs]?.first?.split('_')?.get(1)?.toInt()
                            if (mid != null) {
                                for ((searge, name) in paramsByNumber[mid] ?: emptyMap()) {
                                    val lvOrd = searge.split('_')[2].toInt()
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
                    delegate: InvokableVisitor<*>,
                    index: Int?,
                    lvOrd: Int?,
                    names: Map<Namespace, String>
                ): ParameterVisitor? {
                    val searge = names[srcNs] ?: return null
                    val name = data[searge] ?: return null
                    return default.visitParameter(delegate, index, lvOrd, mapOf(dstNs to name))
                }

            })
        )

    }

}