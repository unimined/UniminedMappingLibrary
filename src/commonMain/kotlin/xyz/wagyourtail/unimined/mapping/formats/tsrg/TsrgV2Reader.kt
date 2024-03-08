package xyz.wagyourtail.unimined.mapping.formats.tsrg

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MethodVisitor

object TsrgV2Reader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        if (!fileName.endsWith(".tsrg")) return false
        return inputType.peek().readUtf8Line()?.startsWith("tsrg2 ") ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

        val tsrg2 = input.takeNextLiteral(' ')
        if (tsrg2 != "tsrg2") throw IllegalArgumentException("invalid tsrg2 file")
        val ns = mutableListOf<Namespace>()
        while (true) {
            val nsName = input.takeNextLiteral(' ') ?: break
            ns.add(Namespace(nsMapping[nsName] ?: nsName))
        }
        into.visitHeader(*ns.map { it.name }.toTypedArray())

        var cls: ClassVisitor? = null
        var md : MethodVisitor? = null

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
                cls = into.visitClass(names.withIndex().associate { ns[it.index] to it.value })
            } else if (whitespace.length == 1) {
                // method
                val srcName = input.takeNextLiteral(' ')!!
                input.takeWhitespace()
                if (input.peek() == '(') {
                    // method
                    val srcDesc = MethodDescriptor.read(input.takeNextLiteral(' ')!!)
                    val names = mutableListOf<Pair<String, MethodDescriptor?>>()
                    while (true) {
                        val name = input.takeNextLiteral(' ') ?: break
                        names.add(name to null)
                    }
                    md = cls?.visitMethod(names.withIndex().associate { ns[it.index + 1] to it.value } + mapOf(ns[0] to (srcName to srcDesc)))
                } else {
                    // field
                    val names = mutableListOf<Pair<String, FieldDescriptor?>>()
                    while (true) {
                        val name = input.takeNextLiteral(' ') ?: break
                        names.add(name to null)
                    }
                    cls?.visitField(names.withIndex().associate { ns[it.index + 1] to it.value } + mapOf(ns[0] to (srcName to null)))
                    md = null
                }
            } else if (whitespace.length == 2) {
                // param
                val index = input.takeNextLiteral(' ')!!
                if (index.toIntOrNull() != null) {
                    val names = mutableListOf<String>()
                    while (true) {
                        val name = input.takeNextLiteral(' ') ?: break
                        names.add(name)
                    }
                    md?.visitParameter(index.toInt(), null, ns.withIndex().associate { it.value to names[it.index] })
                } else {
                    input.takeLine()
                }
            } else {
                throw IllegalArgumentException("invalid line, unexpected whitespace: \"${whitespace.escape()}\"")
            }
        }


    }

}