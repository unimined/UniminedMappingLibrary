package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSink
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.FormatWriterSettings
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

/**
 * First-party implementation: this library — spec `software-engineering/unimined-mapping-format.md`.
 */
object UMFWriter : FormatWriter {

    val EMPTY ="umf\t1\t3\n"

    var global_minimize = false

    /** writer tunable — emit minimized names (see [global_minimize]) */
    val MINIMIZE = FormatReaderSettings.SettingsKey<Boolean>("minimize")

    fun String?.maybeEscape(): String {
        if (this == null) return "_"
        if (this.isEmpty()) return "\"\""
        if (any { it.isWhitespace() } || startsWith("\"")) {
            return "\"${escape(unicode = true)}\""
        }
        if (isNotEmpty() && all { it == '_' })  {
            return "${this}_"
        }
        return this
    }

    operator fun String.minus(s: String): String = this.removeSuffix(s)

    override fun write(append: (String) -> Unit, settings: FormatWriterSettings): MappingSink {
        val envType = settings.envType
        return write(envType, append, settings[MINIMIZE] ?: global_minimize)
    }

    fun write(into: BufferedSink, minimize: Boolean): MappingSink {
        return write(EnvType.JOINED, into, minimize)
    }

    fun write(envType: EnvType, into: BufferedSink, minimize: Boolean): MappingSink {
        return write(envType, into::writeUtf8, minimize)
    }

    fun write(into: (String) -> Unit, minimize: Boolean): MappingSink {
        into(EMPTY)
        return UMFWriterSink(into, minimize)
    }

    fun write(envType: EnvType, into: (String) -> Unit, minimize: Boolean): MappingSink {
        into(EMPTY)
        return UMFWriterSink(into, minimize)
    }

    class UMFWriterSink(
        val into: (String) -> Unit,
        val minimize: Boolean
    ) : MappingSink {

        lateinit var namespaces: List<Namespace>
        var indent = ""

        private fun ((String) -> Unit).writeNamespaced(names: Map<Namespace, String>) {
            namespaces.withIndex().forEach { (i, ns) ->
                this((names[ns]?.maybeEscape() ?: "_"))
                if (i != namespaces.lastIndex) {
                    this("\t")
                }
            }
        }

        private fun namespacedMinimized(names: Map<Namespace, NameAndDescriptor>, getDesc: (NameAndDescriptor) -> String): Map<Namespace, String> {
            return if (minimize) {
                val map = mutableMapOf<Namespace, String>()
                var hasDesc = false
                for ((ns, entry) in names.entries.sortedBy { namespaces.indexOf(it.key) }) {
                    if (entry.hasDescriptor && !hasDesc) {
                        hasDesc = true
                        map[ns] = getDesc(entry)
                    } else {
                        map[ns] = entry.name.value
                    }
                }
                map
            } else {
                names.mapValues { v -> v.value.value }
            }
        }

        override fun header(namespaces: List<Namespace>) {
            this.namespaces = namespaces
            into(indent)
            into(namespaces.joinToString("\t") { it.name.maybeEscape() })
            into("\n")
        }

        private inline fun <T : Any> writeLine(element: T, letter: Char, body: () -> Unit): T {
            into(indent)
            into("$letter\t")
            body()
            into("\n")
            indent += "\t"
            return element
        }

        override fun push(element: MappingElement): MappingElement? = when (element) {
            is PackageMapping -> writeLine(element, UMFReader.EntryType.PACKAGE.key) {
                into.writeNamespaced(element.names.mapValues { it.value.value })
            }
            is ClassMapping -> writeLine(element, UMFReader.EntryType.CLASS.key) {
                into.writeNamespaced(element.names.mapValues { it.value.value })
            }
            is FieldMapping -> writeLine(element, UMFReader.EntryType.FIELD.key) {
                into.writeNamespaced(namespacedMinimized(element.emitEntries()) { it.value })
            }
            is MethodMapping -> writeLine(element, UMFReader.EntryType.METHOD.key) {
                into.writeNamespaced(namespacedMinimized(element.emitEntries()) { it.value })
            }
            is WildcardMapping -> writeLine(element, UMFReader.EntryType.WILDCARD.key) {
                into(when (element.type) {
                    WildcardType.FIELD -> "f"
                    WildcardType.METHOD -> "m"
                })
                into("\t")
                val descs = element.descs
                into.writeNamespaced((if (minimize && descs.isNotEmpty()) mapOf(descs.entries.sortedBy { namespaces.indexOf(it.key) }.first().toPair()) else descs).mapValues { it.value.toString() })
            }
            is ParameterMapping -> writeLine(element, UMFReader.EntryType.PARAMETER.key) {
                into(element.index?.toString().maybeEscape())
                into("\t")
                into(element.lvOrd?.toString().maybeEscape())
                into("\t")
                into.writeNamespaced(element.names.mapValues { it.value.value })
            }
            is LocalVariableMapping -> writeLine(element, UMFReader.EntryType.LOCAL_VARIABLE.key) {
                into(element.lvOrd.toString().maybeEscape())
                into("\t")
                into(element.startOp?.toString().maybeEscape())
                into("\t")
                into.writeNamespaced(element.names.mapValues { it.value.value })
            }
            is ConstantGroupMapping -> writeLine(element, UMFReader.EntryType.CONSTANT_GROUP.key) {
                into("${element.type.name.lowercase()}\t${element.name.maybeEscape()}\t${element.baseNs.name.maybeEscape()}")
            }
            else -> null
        }

        override fun push(meta: MetadataElement): MetadataElement? = when (meta) {
            is AccessElement -> writeLine(meta, UMFReader.EntryType.ACCESS.key) {
                when (meta.type) {
                    AddRemove.ADD -> into("+\t")
                    AddRemove.REMOVE -> into("-\t")
                }
                into("${meta.value.name.lowercase()}\t")
                into(meta.condition.toString())
            }
            is JavadocElement -> writeLine(meta, UMFReader.EntryType.JAVADOC.key) {
                into(meta.value.toString().maybeEscape())
                into("\t")
                into(meta.baseNs.name.maybeEscape())
            }
            is SignatureElement -> writeLine(meta, UMFReader.EntryType.SIGNATURE.key) {
                into(meta.value.toString().maybeEscape())
                into("\t")
                into(meta.baseNs.name.maybeEscape())
            }
            is AnnotationElement -> writeLine(meta, UMFReader.EntryType.ANNOTATION.key) {
                when (meta.type) {
                    AddRemoveModify.ADD -> into("+\t")
                    AddRemoveModify.REMOVE -> into("-\t")
                    AddRemoveModify.MODIFY -> into("m\t")
                }
                val parts = meta.annotation.getParts()
                into(parts.first.value.maybeEscape())
                into("\t")
                into(parts.second?.value.maybeEscape())
                if (parts.third != null) {
                    into(parts.third!!.value.maybeEscape())
                }
                into("\t")
                into(meta.baseNs.name.maybeEscape())
            }
            is SealElement -> writeLine(meta, UMFReader.EntryType.SEAL.key) {
                into(when (meta.type) {
                    AddRemoveClear.ADD -> "+\t"
                    AddRemoveClear.REMOVE -> "-\t"
                    AddRemoveClear.CLEAR -> "c\t"
                })
                if (meta.type != AddRemoveClear.CLEAR) {
                    into(meta.name!!.value.maybeEscape())
                    into("\t")
                }
                into(meta.baseNs.name.maybeEscape())
            }
            is InterfaceElement -> writeLine(meta, UMFReader.EntryType.INTERFACE.key) {
                into(when (meta.type) {
                    AddRemove.ADD -> "+\t"
                    AddRemove.REMOVE -> "-\t"
                })
                into(meta.name.value.maybeEscape())
                into("\t")
                into(meta.baseNs.name.maybeEscape())
            }
            is ExceptionElement -> writeLine(meta, UMFReader.EntryType.EXCEPTION.key) {
                when (meta.type) {
                    AddRemove.ADD -> into("+\t")
                    AddRemove.REMOVE -> into("-\t")
                }
                into(meta.exception.value.maybeEscape())
                into("\t")
                into(meta.baseNs.name.maybeEscape())
            }
            is InnerClassElement -> writeLine(meta, UMFReader.EntryType.INNER_CLASS.key) {
                into(when (meta.type) {
                    InnerType.INNER -> "i\t"
                    InnerType.LOCAL -> "l\t"
                    InnerType.ANONYMOUS -> "a\t"
                })
                into.writeNamespaced(if (minimize) {
                    val map = mutableMapOf<Namespace, String>()
                    var hasDesc = false
                    for ((ns, entry) in meta.names.entries.sortedBy { namespaces.indexOf(it.key) }) {
                        val (name, desc) = entry
                        if (desc != null && !hasDesc) {
                            hasDesc = true
                            map[ns] = "$name;${desc.value}"
                        } else {
                            map[ns] = name
                        }
                    }
                    if (!hasDesc) throw IllegalArgumentException("No fqn found")
                    map
                } else {
                    meta.names.mapValues { v -> v.value.second?.let { "${v.value.first};${it.value}" } ?: v.value.first }
                })
            }
            is EnumExtensionElement -> writeLine(meta, UMFReader.EntryType.ENUM.key) {
                into(when (meta.type) {
                    AddRemove.ADD -> "+\t"
                    AddRemove.REMOVE -> "-\t"
                })
                into(meta.name.value.maybeEscape())
                into("\t")
                into(meta.baseNs.name.maybeEscape())
            }
            is SyntheticBridgeElement -> writeLine(meta, UMFReader.EntryType.SYNTHETIC_BRIDGE.key) {
                into.writeNamespaced(meta.names.mapValues { it.value.value })
            }
            is ConstantElement -> writeLine(meta, UMFReader.EntryType.CONSTANT.key) {
                into(meta.owner.value.maybeEscape())
                into("\t")
                into(meta.field.name.value.maybeEscape())
                if (meta.field.descriptor != null) {
                    into(";")
                    into(meta.field.descriptor!!.value.value.maybeEscape())
                }
            }
            is TargetElement -> writeLine(meta, UMFReader.EntryType.CONSTANT_TARGET.key) {
                meta.target?.let { into(it.value.maybeEscape()) }
                into("\t")
                into(meta.paramIdx?.toString().maybeEscape())
            }
            is ExpressionElement -> writeLine(meta, UMFReader.EntryType.CONSTANT_EXPRESSION.key) {
                into(meta.value.value.maybeEscape())
                into("\t")
                into(meta.expression.toString().maybeEscape())
            }
            is ConstantAttributeElement -> writeLine(meta, UMFReader.EntryType.CONSTANT_ATTRIBUTE.key) {
                into(meta.key.maybeEscape())
                into("\t")
                into(meta.value.maybeEscape())
                into("\t")
                into(meta.baseNs.name.maybeEscape())
            }
            else -> null
        }

        override fun pop(element: MappingElement) {
            indent -= "\t"
        }

        override fun pop(meta: MetadataElement) {
            indent -= "\t"
        }
    }


}