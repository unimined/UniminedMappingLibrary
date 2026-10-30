package xyz.wagyourtail.unimined.mapping.formats.tiny.v2

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.Element
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableLocalVariableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableParameterMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.newNsMap
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

/**
 * FabricMC's tinyv2 format.
 * First-party implementation: https://github.com/FabricMC/tiny-mappings-parser/blob/750be4e/src/main/java/net/fabricmc/mapping/tree/TinyMappingFactory.java; spec https://wiki.fabricmc.net/documentation:tiny2
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

    override suspend fun readImpl(
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
                val line = input.takeLine()
                if (line.isEmpty()) continue
                var indent = 0
                while (indent < line.length && line[indent] == '\t') indent++
                if (indent > stack.size - 1) {
                    throw IllegalArgumentException("Invalid tinyv2 file, found double indent")
                }
                while (indent < stack.size - 1) {
                    stack.removeLast()?.let { pop(it) }
                }
                val rest = line.substring(indent)
                val cells = rest.split('\t')
                val type = cells[0]
                val nsIter = namespaces.iterator()
                fun cell(i: Int) = cells[i].let { if (escaped) it.translateEscapes() else it }
                val next: Element? = when (type) {
                    "c" -> {
                        if (indent == 0) {
                            // class
                            if (cells.size < 2) {
                                throw IllegalArgumentException("Invalid tinyv2 file, class without src name")
                            }
                            val nameMap = newNsMap<InternalName>()
                            nameMap[nsIter.next()] = InternalName.read(cell(1))
                            for (i in 2 until cells.size) {
                                val ns = nsIter.next()
                                val name = cell(i)
                                if (name.isNotEmpty()) {
                                    nameMap[ns] = InternalName.read(name)
                                }
                            }
                            push(MutableClassMapping(nameMap))
                        } else {
                            // comment — raw text after the type cell; tabs inside are part of it
                            val comment = rest.substringAfter('\t', "").translateEscapes()
                            push(JavadocElement(comment, namespaces.first()))
                        }
                    }

                    "f" -> {
                        // field
                        val desc = cell(1)
                        val nameMap = newNsMap<FieldNameAndDescriptor>()
                        nameMap[nsIter.next()] = UnqualifiedName.read(cell(2)).withFieldDesc(FieldDescriptor.read(desc))
                        for (i in 3 until cells.size) {
                            val ns = nsIter.next()
                            val name = cell(i)
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name).withFieldDesc(null)
                            }
                        }
                        push(MutableFieldMapping(nameMap))
                    }

                    "m" -> {
                        // method
                        val desc = cell(1)
                        val nameMap = newNsMap<MethodNameAndDescriptor>()
                        nameMap[nsIter.next()] = UnqualifiedName.read(cell(2)).withMethodDesc(MethodDescriptor.read(desc))
                        for (i in 3 until cells.size) {
                            val ns = nsIter.next()
                            val name = cell(i)
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name).withMethodDesc(null)
                            }
                        }
                        push(MutableMethodMapping(nameMap))
                    }

                    "p" -> {
                        // parameter
                        val lvOrd = cells.getOrNull(1)?.toIntOrNull()
                        val nameMap = newNsMap<UnqualifiedName>()
                        for (i in 2 until cells.size) {
                            val ns = nsIter.next()
                            val name = cell(i)
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name)
                            }
                        }
                        push(MutableParameterMapping(null, lvOrd, nameMap))
                    }

                    "v" -> {
                        // local variable: v <lvIndex> <startOpIdx> <lvtRowIndex> <names...>
                        val lvOrd = cells.getOrNull(1)?.toIntOrNull() ?: -1
                        val startOp = cells.getOrNull(2)?.toIntOrNull()
                        // cells[3] (lvtRowIndex) is not represented in the tree
                        val nameMap = newNsMap<UnqualifiedName>()
                        for (i in 4 until cells.size) {
                            val ns = nsIter.next()
                            val name = cell(i)
                            if (name.isNotEmpty()) {
                                nameMap[ns] = UnqualifiedName.read(name)
                            }
                        }
                        push(MutableLocalVariableMapping(lvOrd, startOp, nameMap))
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