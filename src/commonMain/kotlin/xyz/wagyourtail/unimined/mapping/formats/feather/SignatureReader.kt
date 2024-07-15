package xyz.wagyourtail.unimined.mapping.formats.feather

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object SignatureReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return fileName.endsWith(".sigs")
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
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
                    val name = InternalName.read(input.takeNextLiteral()!!)
                    cls?.visitEnd()
                    cls = visitClass(mapOf(ns to name))
                    val sig = input.takeNextLiteral()
                    if (sig != null) {
                        cls?.visitSignature(sig, ns, emptySet())
                    }
                } else {
                    if (whitespace.length != 1) {
                        throw IllegalArgumentException("invalid line: $whitespace")
                    }
                    val mName = input.takeNextLiteral()!!
                    val desc = MethodDescriptor.read(input.takeNextLiteral()!!)
                    val sig = input.takeNextLiteral()!!
                    cls?.visitMethod(mapOf(ns to (mName to desc)))?.use {
                        visitSignature(sig, ns, emptySet())
                    }
                }

            }
        }
    }

}