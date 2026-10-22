package xyz.wagyourtail.unimined.mapping.formats.tiny.v1

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
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

object TinyV1Reader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        return input.peek().readUtf8Line()?.startsWith("v1\t") ?: false
    }

    override suspend fun read(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val v = input.takeNextLiteral()
        if (v != "v1") throw IllegalArgumentException("Invalid tinyv1 file")
        val namespaces = input.takeRemainingOnLine().map { Namespace(nsMapping[it] ?: it) }

        with (into) {
            header(namespaces)

            var currentCls: Pair<InternalName, MappingElement?>? = null

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
                        currentCls?.second?.let { pop(it) }
                        currentCls = srcName to push(MutableClassMapping(nameMap))
                    }

                    "FIELD" -> {
                        val srcClass = InternalName.read(input.takeNextLiteral()!!)
                        val srcDesc = FieldDescriptor.read(input.takeNextLiteral()!!)
                        val srcName = UnqualifiedName.read(input.takeNextLiteral()!!)
                        val names = input.takeRemainingOnLine().map { it }
                        val namesIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, FieldNameAndDescriptor>()
                        nameMap[nsIter.next()] = srcName.withFieldDesc(srcDesc)
                        while (namesIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = namesIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name).withFieldDesc(srcDesc)
                            }
                        }
                        if (currentCls?.first != srcClass) {
                            currentCls?.second?.let { pop(it) }
                            currentCls = srcClass to push(MutableClassMapping(mapOf(namespaces.first() to srcClass)))
                        }
                        pushed(MutableFieldMapping(nameMap)) {}
                    }

                    "METHOD" -> {
                        val srcClass = InternalName.read(input.takeNextLiteral()!!)
                        val srcDesc = MethodDescriptor.read(input.takeNextLiteral()!!)
                        val srcName = UnqualifiedName.read(input.takeNextLiteral()!!)
                        val names = input.takeRemainingOnLine().map { it }
                        val namesIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, MethodNameAndDescriptor>()
                        nameMap[nsIter.next()] = srcName.withMethodDesc(srcDesc)
                        while (namesIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = namesIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name).withMethodDesc(null)
                            }
                        }
                        if (currentCls?.first != srcClass) {
                            currentCls?.second?.let { pop(it) }
                            currentCls = srcClass to push(MutableClassMapping(mapOf(namespaces.first() to srcClass)))
                        }
                        pushed(MutableMethodMapping(nameMap)) {}
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid tinyv1 file, unknown type $col")
                    }
                }
            }

            currentCls?.second?.let { pop(it) }
        }
into.end()
    }

}