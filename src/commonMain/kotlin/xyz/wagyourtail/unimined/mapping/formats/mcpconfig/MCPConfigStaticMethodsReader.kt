package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator

object MCPConfigStaticMethodsReader : FormatReader {

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "static_methods.txt") return false
        // single field_
        val line = input.peek().readUtf8Line() ?: return false
        return line.matches(Regex("^func_\\d+_\\w*]$"))
    }

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {

        val srcNs = Namespace(nsMapping["searge"] ?: "searge")

        val statics = mutableSetOf<String>()
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            val line = input.takeLine()
            if (line.startsWith("func_")) {
                statics.add(line)
            }
        }

        context?.accept(DelegateMappingVisitor(into, object : NullDelegator() {

            override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                return default.visitClass(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                val name = names[srcNs] ?: return null
                if (!name.first.matches(Regex("^func_\\d+_\\w*]$"))) return null
                val mid = name.first.split("_")[1]
                // find a descriptor that matches
                val desc = names.mapNotNull { it.value.second }.firstOrNull()
                if (desc == null) return null
                val method = default.visitMethod(delegate, names) ?: return null
                val params = desc.getParts().second
                var lvtIdx = if (statics.contains(name.first)) 0 else 1
                for (idx in params.indices) {
                    method.visitParameter(idx, lvtIdx, mapOf(srcNs to "p_${mid}_${lvtIdx}"))
                    lvtIdx += params[idx].value.getWidth()
                }
                return method
            }

            override fun visitParameter(
                delegate: InvokableVisitor<*>,
                index: Int?,
                lvOrd: Int?,
                names: Map<Namespace, String>
            ): ParameterVisitor? {
                return default.visitParameter(delegate, index, lvOrd, names)
            }

        }))

    }

}