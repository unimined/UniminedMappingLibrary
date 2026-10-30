package xyz.wagyourtail.unimined.mapping.formats.tsrg

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * First-party implementation: SrgUtils `IMappingFile.Format.TSRG2` (https://github.com/MinecraftForge/SrgUtils/blob/3c679e5/src/main/java/net/minecraftforge/srgutils/IMappingFile.java).
 */
object TsrgV2Reader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        if (!fileName.endsWith(".tsrg")) return false
        return input.peek().readUtf8Line()?.startsWith("tsrg2 ") ?: false
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
        
        val tsrg2 = input.takeNextLiteral(' ')
        if (tsrg2 != "tsrg2") throw IllegalArgumentException("invalid tsrg2 file")
        val ns = mutableListOf<Namespace>()
        while (true) {
            val nsName = input.takeNextLiteral(' ') ?: break
            ns.add(Namespace(nsMapping[nsName] ?: nsName))
        }

        with (into) {
            header(ns)

            var cls: MappingElement? = null
            var md: MappingElement? = null

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val whitespace = input.takeWhitespace()
                if (whitespace.isEmpty()) {
                    // cls
                    val names = mutableListOf<InternalName>()
                    while (true) {
                        val name = input.takeNextLiteral(' ') ?: break
                        names.add(InternalName.read(name))
                    }
                    md?.let { pop(it) }
                    md = null
                    cls?.let { pop(it) }
                    cls = push(MutableClassMapping(names.withIndex().associate { ns[it.index] to it.value }))
                } else if (whitespace.length == 1) {
                    // method
                    val srcName = UnqualifiedName.read(input.takeNextLiteral(' ')!!)
                    input.takeWhitespace()
                    if (input.peek() == '(') {
                        // method
                        val srcDesc = MethodDescriptor.read(input.takeNextLiteral(' ')!!)
                        val names = mutableListOf<MethodNameAndDescriptor>()
                        while (true) {
                            val name = UnqualifiedName.read(input.takeNextLiteral(' ') ?: break)
                            names.add(name.withMethodDesc(null))
                        }
                        md?.let { pop(it) }
                        md = push(MutableMethodMapping(
                            names.withIndex()
                                .associate { ns[it.index + 1] to it.value } + mapOf(ns[0] to srcName.withMethodDesc(srcDesc))))
                    } else {
                        // field — tsrg2 field src desc is optional in practice
                        // (e.g. mcp_config joined.tsrg omits it); discriminate by
                        // token count: srcName + desc + one name per dst ns
                        val tokens = mutableListOf<String>()
                        while (true) {
                            tokens.add(input.takeNextLiteral(' ') ?: break)
                        }
                        val srcDesc = if (tokens.size == ns.size) FieldDescriptor.read(tokens.removeAt(0)) else null
                        val names = tokens.map { UnqualifiedName.read(it).withFieldDesc(null) }
                        pushed(MutableFieldMapping(
                            names.withIndex()
                                .associate { ns[it.index + 1] to it.value } + mapOf(ns[0] to srcName.withFieldDesc(srcDesc)))) {}
                        md = null
                    }
                } else if (whitespace.length == 2) {
                    // param
                    val index = input.takeNextLiteral(' ')!!
                    if (index.toIntOrNull() != null) {
                        val names = mutableListOf<UnqualifiedName>()
                        while (true) {
                            val name = UnqualifiedName.read(input.takeNextLiteral(' ') ?: break)
                            names.add(name)
                        }
                        pushed(MutableParameterMapping(
                            index.toInt(),
                            null,
                            ns.withIndex().associate { it.value to names[it.index] })) {}
                    } else {
                        input.takeLine()
                    }
                } else {
                    throw IllegalArgumentException("invalid line, unexpected whitespace: \"${whitespace.escape()}\"")
                }
            }

            md?.let { pop(it) }
            cls?.let { pop(it) }
        }
        into.end()


    }

}