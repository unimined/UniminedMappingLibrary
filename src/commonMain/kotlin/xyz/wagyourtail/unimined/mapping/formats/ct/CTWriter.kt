package xyz.wagyourtail.unimined.mapping.formats.ct

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.WildcardMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InterfaceElement
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.FormatWriterSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

/** Writes the access, interface-injection, and enum-extension subset supported by CT. */
object CTWriter : FormatWriter {
    override fun write(append: (String) -> Unit, settings: FormatWriterSettings): MappingSink {
        val envType = settings.envType
        return Sink(append)
    }

    fun remapMappings(mappings: CTReader.CTMappings, context: MappingTree, targetNs: Namespace): CTReader.CTMappings {
        val sourceNs = mappings.namespace
        return CTReader.CTMappings(mappings.version, targetNs, mappings.targets.map {
            when (it) {
                is CTReader.AccessDirective ->
                    CTReader.AccessDirective(it.access, context.map(sourceNs, targetNs, it.target))
                is CTReader.InterfaceDirective ->
                    CTReader.InterfaceDirective(it.access, context.map(sourceNs, targetNs, it.owner), context.map(sourceNs, targetNs, it.signature))
                is CTReader.EnumDirective ->
                    // the enum constant is an addition, not a mapped name
                    CTReader.EnumDirective(it.access, context.map(sourceNs, targetNs, it.owner), it.name)
                else -> it
            }
        })
    }

    fun writeData(mappings: CTReader.CTMappings, append: (String) -> Unit) {
        append("classTweaker\t${mappings.version}\t${mappings.namespace.name}\n")
        for ((i, item) in mappings.targets.withIndex()) {
            when (item) {
                is CTReader.AccessDirective -> {
                    if (i != 0) append("\n")
                    val (cls, member) = item.target.getParts()
                    if (member == null) {
                        append("${item.access}\tclass\t${cls.getInternalName()}")
                    } else {
                        val (name, desc) = member.getParts()
                        val kind = if (desc!!.isMethodDescriptor()) "method" else "field"
                        append("${item.access}\t$kind\t${cls.getInternalName()}\t$name\t$desc")
                    }
                }
                is CTReader.InterfaceDirective -> {
                    if (i != 0) append("\n")
                    val signature = item.signature.toString()
                    require(signature.startsWith("L") && signature.endsWith(";")) {
                        "Invalid injected-interface signature $signature"
                    }
                    append("${item.access}\t${item.owner}\t${signature.substring(1, signature.length - 1)}")
                }
                is CTReader.EnumDirective -> {
                    if (i != 0) append("\n")
                    append("${item.access}\t${item.owner}\t${item.name}")
                }
                is CTReader.CTComment -> {
                    if (i != 0) append("\n")
                    append(item.comment)
                }
                is CTReader.CTNewline -> append("\n")
                else -> {}
            }
        }
        append("\n")
    }

    private data class MemberTarget(val kind: String, val name: String, val descriptor: String)

    private class Sink(private val append: (String) -> Unit) : MappingSink {
        private var namespace: Namespace? = null
        private var className: InternalName? = null
        private var member: MemberTarget? = null
        private var wildcard: WildcardMapping? = null

        override fun header(namespaces: List<Namespace>) {
            require(namespaces.size == 1) { "CTWriter requires exactly one namespace" }
            namespace = namespaces.single()
            append("classTweaker\tv2\t${namespace!!.name}\n")
        }

        override fun push(element: MappingElement): MappingElement? {
            val ns = namespace ?: error("CTWriter requires a header before elements")
            when (element) {
                is ClassMapping -> {
                    className = element.names[ns] ?: return null
                    return element
                }
                is FieldMapping -> {
                    val entry = element.entries[ns] ?: return null
                    val desc = entry.descriptor ?: return null
                    member = MemberTarget("field", entry.name.value, desc.toString())
                    return element
                }
                is MethodMapping -> {
                    val entry = element.entries[ns] ?: return null
                    val desc = entry.descriptor ?: return null
                    member = MemberTarget("method", entry.name.value, desc.toString())
                    return element
                }
                is WildcardMapping -> {
                    wildcard = element
                    return element
                }
                else -> return element
            }
        }

        override fun push(meta: xyz.wagyourtail.unimined.mapping.element.MetadataElement): xyz.wagyourtail.unimined.mapping.element.MetadataElement? {
            val ns = namespace ?: error("CTWriter requires a header before metadata")
            when (meta) {
                is AccessElement -> writeAccess(meta)
                is InterfaceElement -> {
                    val owner = className
                    if (owner != null && meta.type == AddRemove.ADD && meta.baseNs == ns) {
                        val signature = meta.name.toString()
                        require(signature.startsWith("L") && signature.endsWith(";")) {
                            "Invalid injected-interface signature $signature"
                        }
                        line("inject-interface", owner.toString(), signature.substring(1, signature.length - 1))
                    }
                }
                is EnumExtensionElement -> {
                    val owner = className
                    if (owner != null && meta.type == AddRemove.ADD && meta.baseNs == ns) {
                        line("extend-enum", owner.toString(), meta.name.value)
                    }
                }
            }
            return meta
        }

        private fun writeAccess(meta: AccessElement) {
            if (meta.condition != xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions.ALL) return
            if (wildcard != null) return
            val owner = className ?: return
            val target = member
            val operation = when {
                meta.type == AddRemove.ADD && meta.value == AccessFlag.PUBLIC -> "accessible"
                meta.type == AddRemove.REMOVE && meta.value == AccessFlag.FINAL ->
                    when (target?.kind) {
                        "field" -> "mutable"
                        else -> "extendable"
                    }
                else -> return
            }
            if (target == null) {
                line(operation, "class", owner.toString())
            } else {
                line(operation, target.kind, owner.toString(), target.name, target.descriptor)
            }
        }

        private fun line(vararg fields: String) {
            append(fields.joinToString("\t"))
            append("\n")
        }

        override fun pop(element: MappingElement) {
            when (element) {
                is ClassMapping -> {
                    className = null
                    member = null
                    wildcard = null
                }
                is FieldMapping, is MethodMapping -> member = null
                is WildcardMapping -> wildcard = null
            }
        }

        override fun pop(meta: xyz.wagyourtail.unimined.mapping.element.MetadataElement) = Unit
        override fun end() = Unit
    }
}
