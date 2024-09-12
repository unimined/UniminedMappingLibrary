package xyz.wagyourtail.unimined.mapping.formats.feather

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.translateEscapes
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object SignatureReader : FormatReader {

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return fileName.endsWith(".sigs")
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        val ns = Namespace(nsMapping["source"] ?: "source")

        into.use {
            visitHeader(ns.name)

            var cls: ClassVisitor? = null

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
                        cls?.visitSignature(sig.translateEscapes(), ns, emptySet())
                    }
                } else {
                    if (whitespace.length != 1) {
                        throw IllegalArgumentException("invalid line: $whitespace")
                    }
                    val mName = input.takeNextLiteral()!!.translateEscapes()
                    val desc = FieldOrMethodDescriptor.read(input.takeNextLiteral()!!.translateEscapes())
                    val sig = input.takeNextLiteral()!!.translateEscapes()
                    if (desc.isMethodDescriptor()) {
                        cls?.visitMethod(mapOf(ns to (mName to desc.getMethodDescriptor())))?.use {
                            visitSignature(sig, ns, emptySet())
                        }
                    } else {
                        cls?.visitField(mapOf(ns to (mName to desc.getFieldDescriptor())))?.use {
                            visitSignature(sig, ns, emptySet())
                        }
                    }
                }

            }
        }
    }

}