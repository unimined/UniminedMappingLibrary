package xyz.wagyourtail.unimined.mapping.formats.tiny.v1

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.ClassVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object TinyV1Reader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return input.peek().readUtf8Line()?.startsWith("v1\t") ?: false
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val v = input.takeNextLiteral()
        if (v != "v1") throw IllegalArgumentException("Invalid tinyv1 file")
        val namespaces = input.takeRemainingOnLine().map { Namespace(nsMapping[it] ?: it) }

        into.use {
            visitHeader(*namespaces.map { it.name }.toTypedArray())

            var currentCls: Pair<InternalName, ClassVisitor?>? = null

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                val col = input.takeNextLiteral() ?: continue
                if (col.startsWith("#")) {
                    input.takeLine()
                    continue
                }
                when (col) {
                    "CLASS" -> {
                        val names = input.takeRemainingOnLine().map { it }
                        val namesIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, InternalName>()
                        val srcName = InternalName.read(namesIter.next())
                        nameMap[nsIter.next()] = srcName
                        while (namesIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = namesIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = InternalName.read(name)
                            }
                        }
                        currentCls?.second?.visitEnd()
                        currentCls = srcName to visitClass(nameMap)
                    }

                    "FIELD" -> {
                        val srcClass = InternalName.read(input.takeNextLiteral()!!)
                        val srcDesc = FieldDescriptor.read(input.takeNextLiteral()!!)
                        val srcName = input.takeNextLiteral()!!
                        val names = input.takeRemainingOnLine().map { it }
                        val namesIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, Pair<String, FieldDescriptor?>>()
                        nameMap[nsIter.next()] = srcName to srcDesc
                        while (namesIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = namesIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = name to null
                            }
                        }
                        if (currentCls?.first != srcClass) {
                            currentCls?.second?.visitEnd()
                            currentCls = srcClass to visitClass(mapOf(namespaces.first() to srcClass))
                        }
                        currentCls.second?.visitField(nameMap)?.visitEnd()
                    }

                    "METHOD" -> {
                        val srcClass = InternalName.read(input.takeNextLiteral()!!)
                        val srcDesc = MethodDescriptor.read(input.takeNextLiteral()!!)
                        val srcName = input.takeNextLiteral()!!
                        val names = input.takeRemainingOnLine().map { it }
                        val namesIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, Pair<String, MethodDescriptor?>>()
                        nameMap[nsIter.next()] = srcName to srcDesc
                        while (namesIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = namesIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = name to null
                            }
                        }
                        if (currentCls?.first != srcClass) {
                            currentCls?.second?.visitEnd()
                            currentCls = srcClass to visitClass(mapOf(namespaces.first() to srcClass))
                        }
                        currentCls.second?.visitMethod(nameMap)?.visitEnd()
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid tinyv1 file, unknown type $col")
                    }
                }
            }

            currentCls?.second?.visitEnd()
        }
    }

}