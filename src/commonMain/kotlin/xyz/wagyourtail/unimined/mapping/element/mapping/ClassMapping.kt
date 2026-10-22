package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.*
interface ClassMapping : MappingElement, MetadataParent {

    val names: Map<Namespace, InternalName>

    val methods: Iterable<MethodMapping>

    val fields: Iterable<FieldMapping>

    val wildcards: Iterable<WildcardMapping>

    val innerClasses: Iterable<InnerClassElement>
        get() = metadata.filterIsInstance<InnerClassElement>()

    override val contents: Iterable<MappingElement>
        get() = wildcards + fields + methods

    companion object {
        operator fun invoke(
            names: Map<Namespace, InternalName> = emptyMap(),
            methods: Iterable<MethodMapping> = emptyList(),
            fields: Iterable<FieldMapping> = emptyList(),
            wildcards: Iterable<WildcardMapping> = emptyList(),
            metadata: Iterable<MetadataElement> = emptyList()
        ): ClassMapping = MutableClassMapping(names, methods, fields, wildcards, metadata)
    }

}
