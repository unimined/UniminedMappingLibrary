package xyz.wagyourtail.unimined.mapping.formats.feather

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.translateEscapes
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.SignatureElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * First-party implementation: OrnitheMC feather mapping extras (`.sigs`); no public upstream parser — corpus is schema-derived.
 */
object SignatureReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        return fileName.endsWith(".sigs")
    }

    override suspend fun readImpl(
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
                    val name = InternalName.read(input.takeNextLiteral()!!.translateEscapes())
                    cls?.let { pop(it) }
                    cls = push(MutableClassMapping(mapOf(ns to name)))
                    val sig = input.takeNextLiteral()
                    if (sig != null) {
                        pushed(SignatureElement(ClassSignature.read(sig.translateEscapes()), ns)) {}
                    }
                } else {
                    if (whitespace.length != 1) {
                        throw IllegalArgumentException("invalid line: $whitespace")
                    }
                    val mName = UnqualifiedName.read(input.takeNextLiteral()!!.translateEscapes())
                    val desc = FieldOrMethodDescriptor.read(input.takeNextLiteral()!!.translateEscapes())
                    val sig = input.takeNextLiteral()!!.translateEscapes()
                    if (desc.isMethodDescriptor()) {
                        pushed(MutableMethodMapping(mapOf(ns to MethodNameAndDescriptor(mName, desc.getMethodDescriptor())))) {
                            pushed(SignatureElement(MethodSignature.read(sig), ns)) {}
                        }
                    } else {
                        pushed(MutableFieldMapping(mapOf(ns to FieldNameAndDescriptor(mName, desc.getFieldDescriptor())))) {
                            pushed(SignatureElement(FieldSignature.read(sig), ns)) {}
                        }
                    }
                }

            }
        }
        into.end()
    }

}