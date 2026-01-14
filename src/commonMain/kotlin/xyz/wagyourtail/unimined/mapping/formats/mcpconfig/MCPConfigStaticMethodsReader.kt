package xyz.wagyourtail.unimined.mapping.formats.mcpconfig

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
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateMappingRootMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator

object MCPConfigStaticMethodsReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        if (fileName.substringAfterLast('/') != "static_methods.txt") return false
        // single field_
        val line = input.peek().readUtf8Line() ?: return false
        return line.matches(Regex("^func_\\d+_\\w*]$"))
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: RootMappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
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

        context?.accept(DelegateMappingRootMappingVisitor(into, object : NullDelegator() {

            override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
                return default.visitClass(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassMappingVisitor,
                names: Map<Namespace, MethodNameAndDescriptor>
            ): MethodMappingVisitor? {
                val name = names[srcNs] ?: return null
                if (!name.name.value.matches(Regex("^func_\\d+_\\w*]$"))) return null
                val mid = name.name.value.split("_")[1]
                // find a descriptor that matches
                val desc = names.mapNotNull { it.value.descriptor }.firstOrNull()
                if (desc == null) return null
                val method = default.visitMethod(delegate, names) ?: return null
                val params = desc.getParts().second
                var lvtIdx = if (statics.contains(name.name.value)) 0 else 1
                for (idx in params.indices) {
                    method.visitParameter(idx, lvtIdx, mapOf(srcNs to UnqualifiedName.read("p_${mid}_${lvtIdx}")))
                    lvtIdx += params[idx].value.getWidth()
                }
                return method
            }

            override fun visitParameter(
                delegate: InvokableMappingVisitor,
                index: Int?,
                lvOrd: Int?,
                names: Map<Namespace, UnqualifiedName>
            ): ParameterMappingVisitor? {
                return default.visitParameter(delegate, index, lvOrd, names)
            }

        }))

    }

}