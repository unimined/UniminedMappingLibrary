package xyz.wagyourtail.unimined.mapping.formats.tiny.v2

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter.minus
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

object TinyV2Writer : FormatWriter {

    private fun String.escape(): String {
        if (this.isEmpty()) return this
        return buildString(this.length) {
            for (c in this@escape) {
                when (c) {
                    '\t' -> append("\\t")
                    '\r' -> append("\\r")
                    '\n' -> append("\\n")
                    '\\' -> append("\\\\")
                    '\u0000' -> append("\\0")
                    else -> append(c)
                }
            }
        }
    }

    override fun write(append: (String) -> Unit, envType: EnvType): MappingSink {
        return object : MappingSink {
            var indent = ""
            lateinit var namespaces: List<Namespace>

            fun ((String) -> Unit).writeNamespaced(names: Map<Namespace, String>) {
                namespaces.withIndex().forEach { (i, ns) ->
                    this((names[ns]?.escape() ?: ""))
                    if (i != namespaces.lastIndex) {
                        this("\t")
                    }
                }
            }

            override fun header(namespaces: List<Namespace>) {
                append("tiny\t2\t0\t")
                this.namespaces = namespaces
                append(this.namespaces.joinToString("\t") { it.name })
                append("\n\tescaped-names\n")
            }

            override fun push(element: MappingElement): MappingElement? {
                when (element) {
                    is ClassMapping -> {
                        if (namespaces.first() !in element.names) return null
                        append("c\t")
                        append.writeNamespaced(element.names.mapValues { it.value.toString() })
                        append("\n")
                        indent += "\t"
                    }
                    is MethodMapping -> {
                        val names = element.emitEntries()
                        if (namespaces.first() !in names) return null
                        val srcDesc = names[namespaces.first()]?.descriptor ?: return null
                        append(indent)
                        append("m\t")
                        append(srcDesc.toString().escape())
                        append("\t")
                        append.writeNamespaced(names.mapValues { it.value.name.value })
                        append("\n")
                        indent += "\t"
                    }
                    is FieldMapping -> {
                        val names = element.emitEntries()
                        if (namespaces.first() !in names) return null
                        val srcDesc = names[namespaces.first()]?.descriptor ?: return null
                        append(indent)
                        append("f\t")
                        append(srcDesc.toString().escape())
                        append("\t")
                        append.writeNamespaced(names.mapValues { it.value.name.value })
                        append("\n")
                        indent += "\t"
                    }
                    is ParameterMapping -> {
                        val lvOrd = element.lvOrd ?: return null
                        append(indent)
                        append("p\t")
                        append(lvOrd.toString())
                        append("\t")
                        append.writeNamespaced(element.names.mapValues { it.value.value })
                        append("\n")
                        indent += "\t"
                    }
                    is LocalVariableMapping -> {
                        append(indent)
                        append("v\t")
                        append(element.lvOrd.toString())
                        append("\t")
                        append(element.startOp?.toString() ?: "")
                        append("\t\t") // skip lvt-idx
                        append.writeNamespaced(element.names.mapValues { it.value.value })
                        append("\n")
                        return null
                    }
                    else -> {}
                }
                return element
            }

            override fun push(meta: MetadataElement): MetadataElement? {
                if (meta is JavadocElement) {
                    if (indent.isEmpty()) throw IllegalStateException("Top level javadoc?")
                    append(indent)
                    append("c\t")
                    append(meta.value.toString().escape())
                    append("\n")
                }
                return null
            }

            override fun pop(element: MappingElement) {
                indent -= "\t"
            }
        }
    }

}