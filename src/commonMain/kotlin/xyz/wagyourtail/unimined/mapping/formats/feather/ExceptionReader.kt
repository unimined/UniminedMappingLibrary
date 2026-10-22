package xyz.wagyourtail.unimined.mapping.formats.feather

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

object ExceptionReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        return fileName.endsWith(".excs")
    }

    override suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val ns = Namespace(nsMapping["source"] ?: "source")

        with (into) {
            header(listOf(ns))

            var cls: MappingElement? = null

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val whitespace = input.takeWhitespace()
                if (whitespace.isEmpty()) {
                    val name = InternalName.read(input.takeNextLiteral()!!)
                    cls?.let { pop(it) }
                    cls = push(MutableClassMapping(mapOf(ns to name)))
                } else {
                    if (whitespace.length != 1) {
                        throw IllegalArgumentException("invalid line: $whitespace")
                    }
                    val mName = UnqualifiedName.read(input.takeNextLiteral()!!)
                    val desc = MethodDescriptor.read(input.takeNextLiteral()!!)
                    val exceptions = mutableListOf<String>()
                    while (true) {
                        exceptions.add(input.takeNextLiteral() ?: break)
                    }
                    pushed(MutableMethodMapping(mapOf(ns to MethodNameAndDescriptor(mName, desc)))) {
                        for (exc in exceptions) {
                            pushed(ExceptionElement(AddRemove.ADD, InternalName.read(exc), ns)) {}
                        }
                    }
                }
            }
        }
        into.end()
    }

}