package xyz.wagyourtail.unimined.mapping.formats.tsrg

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

object TsrgV1Writer : FormatWriter {
        override fun write(append: (String) -> Unit, envType: EnvType): MappingSink {

        return object : MappingSink {
            lateinit var namespaces: List<Namespace>

            override fun header(namespaces: List<Namespace>) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("Srg requires 2 namespaces")
                }
                this.namespaces = namespaces
            }

            override fun push(element: MappingElement): MappingElement? {
                when (element) {
                    is ClassMapping -> {
                        val from = element.names[namespaces[0]] ?: return null
                        val to = element.names[namespaces[1]] ?: return null
                        append("${from.value} ${to.value}\n")
                    }
                    is FieldMapping -> {
                        val names = element.emitEntries()
                        val from = names[namespaces[0]] ?: return null
                        val to = names[namespaces[1]] ?: return null
                        append("\t${from.name} ${to.name}\n")
                        return null
                    }
                    is MethodMapping -> {
                        val names = element.emitEntries()
                        val from = names[namespaces[0]] ?: return null
                        if (!from.hasDescriptor) return null
                        val to = names[namespaces[1]] ?: return null
                        append("\t${from.name} ${from.descriptor} ${to.name}\n")
                        return null
                    }
                    else -> return null
                }
                return element
            }

            override fun push(meta: MetadataElement): MetadataElement? = null

            override fun pop(element: MappingElement) {}
        }
    }


}