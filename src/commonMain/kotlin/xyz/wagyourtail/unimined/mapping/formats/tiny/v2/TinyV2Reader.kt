package xyz.wagyourtail.unimined.mapping.formats.tiny.v2

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.escape
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

/**
 * FabricMC's tinyv2 format.
 */
object TinyV2Reader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    /**
     * translate escapes function that only does the ones actually allowed by tinyv2
     */
    private fun String.translateEscapes(leinient: Boolean = false): String {
        if (this.isEmpty() || !this.contains("\\")) {
            return this
        }

        return buildString(this.length) {
            var i = 0
            while (i < this@translateEscapes.length) {
                val c = this@translateEscapes[i++]
                if (c == '\\') {
                    if (i >= this@translateEscapes.length) throw IllegalArgumentException("Invalid escape, hit end of string")
                    when (val n = this@translateEscapes[i++]) {
                        '\\' -> append('\\')
                        'n' -> append('\n')
                        'r' -> append('\r')
                        't' -> append('\t')
                        '0' -> append('\u0000')
                        else -> {
                            throw IllegalArgumentException(
                                "Invalid escape: ${n.toString().escape(true)} in \"${this}\" at $i"
                            )
                        }
                    }
                } else {
                    append(c)
                }
            }
        }
    }

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        return input.peek().readUtf8Line()?.startsWith("tiny\t2\t0\t") ?: false
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
        if (v != "tiny") throw IllegalArgumentException("Invalid tinyv2 file")
        if (input.takeNextLiteral() != "2") throw IllegalArgumentException("Invalid tinyv2 file")
        if (input.takeNextLiteral() != "0") throw IllegalArgumentException("Invalid tinyv2 file")
        val namespaces = mutableListOf<Namespace>()
        while (true) {
            namespaces.add(input.takeNextLiteral()?.let { Namespace(nsMapping[it] ?: it) } ?: break)
        }
        val metadataKeys = mutableMapOf<String, String?>()
        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            if (input.peek() != '\t') {
                break
            }
            input.take()
            val key = input.takeNextLiteral()!!
            val value = input.takeNextLiteral()
            metadataKeys[key] = value
        }
        val escaped = metadataKeys.contains("escaped-names")

        with (into) {

            header(namespaces)
            val stack = mutableListOf<Element?>(null)
            outer@ while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                var col = input.takeNextLiteral() ?: continue
                var indent = 0
                while (col.isEmpty()) {
                    indent++
                    col = input.takeNextLiteral() ?: continue@outer
                }
                if (indent > stack.size - 1) {
                    throw IllegalArgumentException("Invalid tinyv2 file, found double indent")
                }
                while (indent < stack.size - 1) {
                    stack.removeLast()?.let { pop(it) }
                }
                val type = col
                val next: Element? = when (type) {
                    "c" -> {
                        if (indent == 0) {
                            // class
                            val names = mutableListOf<String>()
                            while (true) {
                                names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                    ?: break)
                            }
                            val nameIter = names.iterator()
                            val nsIter = namespaces.iterator()
                            val nameMap = mutableMapOf<Namespace, InternalName>()
                            nameMap[nsIter.next()] = InternalName.read(nameIter.next())
                            while (nameIter.hasNext()) {
                                val ns = nsIter.next()
                                val name = nameIter.next()
                                if (name.isNotEmpty()) {
                                    nameMap[ns] = InternalName.read(name)
                                }
                            }
                            push(MutableClassMapping(nameMap))
                        } else {
                            // comment
                            val comment = input.takeLine().removePrefix("\t").translateEscapes()
                            push(JavadocElement(comment, namespaces.first()))
                        }
                    }

                    "f" -> {
                        // field
                        val desc = input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }!!
                        val names = mutableListOf<String>()
                        while (true) {
                            names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                ?: break)
                        }
                        val nameIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, FieldNameAndDescriptor>()
                        nameMap[nsIter.next()] = UnqualifiedName.read(nameIter.next()).withFieldDesc(FieldDescriptor.read(desc))
                        while (nameIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = nameIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name).withFieldDesc(null)
                            }
                        }
                        push(MutableFieldMapping(nameMap))
                    }

                    "m" -> {
                        // method
                        val desc = input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }!!
                        val names = mutableListOf<String>()
                        while (true) {
                            names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                ?: break)
                        }
                        val nameIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, MethodNameAndDescriptor>()
                        nameMap[nsIter.next()] = UnqualifiedName.read(nameIter.next()).withMethodDesc(MethodDescriptor.read(desc))
                        while (nameIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = nameIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name).withMethodDesc(null)
                            }
                        }
                        push(MutableMethodMapping(nameMap))
                    }

                    "p" -> {
                        // parameter
                        val lvOrd = input.takeNextLiteral()?.toIntOrNull()
                        val names = mutableListOf<String>()
                        while (true) {
                            names.add(input.takeNextLiteral()?.let { if (escaped) it.translateEscapes() else it }
                                ?: break)
                        }
                        val nameIter = names.iterator()
                        val nsIter = namespaces.iterator()
                        val nameMap = mutableMapOf<Namespace, UnqualifiedName>()
                        while (nameIter.hasNext()) {
                            val ns = nsIter.next()
                            val name = nameIter.next()
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name)
                            }
                        }
                        push(MutableParameterMapping(null, lvOrd, nameMap))
                    }

                    else -> {
                        throw IllegalArgumentException("Invalid tinyv2 file, unknown type $type")
                    }
                }
                stack.add(next)
            }

            while (stack.size > 1) {
                stack.removeLast()?.let { pop(it) }
            }
        }
        into.end()
    }

}