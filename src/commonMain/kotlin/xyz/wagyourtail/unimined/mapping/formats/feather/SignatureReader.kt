package xyz.wagyourtail.unimined.mapping.formats.feather

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.translateEscapes
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
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
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.RootMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object SignatureReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return fileName.endsWith(".sigs")
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: RootMappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val ns = Namespace(nsMapping["source"] ?: "source")

        into.use {
            visitHeader(ns.name)

            var cls: ClassMappingVisitor? = null

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val whitespace = input.takeWhitespace()
                if (whitespace.isEmpty()) {
                    val name = InternalName.read(input.takeNextLiteral()!!.translateEscapes())
                    cls?.visitEnd()
                    cls = visitClass(mapOf(ns to name))
                    val sig = input.takeNextLiteral()
                    if (sig != null) {
                        cls?.visitSignature(ClassSignature.read(sig.translateEscapes()), ns)
                    }
                } else {
                    if (whitespace.length != 1) {
                        throw IllegalArgumentException("invalid line: $whitespace")
                    }
                    val mName = UnqualifiedName.read(input.takeNextLiteral()!!.translateEscapes())
                    val desc = FieldOrMethodDescriptor.read(input.takeNextLiteral()!!.translateEscapes())
                    val sig = input.takeNextLiteral()!!.translateEscapes()
                    if (desc.isMethodDescriptor()) {
                        cls?.visitMethod(mapOf(ns to MethodNameAndDescriptor(mName, desc.getMethodDescriptor())))?.use {
                            visitSignature(MethodSignature.read(sig), ns)
                        }
                    } else {
                        cls?.visitField(mapOf(ns to FieldNameAndDescriptor(mName, desc.getFieldDescriptor())))?.use {
                            visitSignature(FieldSignature.read(sig), ns)
                        }
                    }
                }

            }
        }
    }

}