package xyz.wagyourtail.unimined.mapping.formats.srg

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.PackageMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.tree.impl.emitEntries

object SrgWriter : FormatWriter {

    override fun write(append: (String) -> Unit, envType: EnvType): MappingSink {

        return object : MappingSink {
            lateinit var namespaces: List<Namespace>
            var currentClsNames: Map<Namespace, String>? = null

            override fun header(namespaces: List<Namespace>) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("Srg requires 2 namespaces")
                }
                this.namespaces = namespaces
            }

            override fun push(element: MappingElement): MappingElement? {
                when (element) {
                    is PackageMapping -> {
                        val from = element.names[namespaces[0]]?.value?.substringBeforeLast('/')?.ifEmpty { "." } ?: return null
                        val to = element.names[namespaces[1]]?.value?.substringBeforeLast('/')?.ifEmpty { "." } ?: return null
                        append("PK: $from $to\n")
                        return null
                    }
                    is ClassMapping -> {
                        val from = element.names[namespaces[0]] ?: return null
                        val to = element.names[namespaces[1]] ?: return null
                        currentClsNames = element.names.mapValues { it.value.value }
                        append("CL: ${from.value} ${to.value}\n")
                    }
                    is FieldMapping -> {
                        val names = element.emitEntries()
                        val from = names[namespaces[0]]?.name ?: return null
                        val to = names[namespaces[1]]?.name ?: return null
                        append("FD: ${currentClsNames!!.getValue(namespaces[0])}/$from ${currentClsNames!!.getValue(namespaces[1])}/$to\n")
                        return null
                    }
                    is MethodMapping -> {
                        val names = element.emitEntries()
                        val from = names[namespaces[0]] ?: return null
                        if (!from.hasDescriptor) return null
                        val to = names[namespaces[1]] ?: return null
                        if (!to.hasDescriptor) return null
                        append("MD: ${currentClsNames!!.getValue(namespaces[0])}/${from.name} ${from.descriptor} ${currentClsNames!!.getValue(namespaces[1])}/${to.name} ${to.descriptor}\n")
                        return null
                    }
                    else -> return null
                }
                return element
            }

            override fun pop(element: MappingElement) {}

            override fun push(meta: MetadataElement): MetadataElement? = null
        }
    }


}