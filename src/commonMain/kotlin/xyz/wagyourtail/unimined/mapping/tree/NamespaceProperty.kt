package xyz.wagyourtail.unimined.mapping.tree

interface NamespacePropertyView: MappingPropertyView<NamespacePropertyView> {

    val namespaces: List<String>

}

interface NamespaceProperty: NamespacePropertyView, MappingProperty<NamespacePropertyView> {

    override var namespaces: MutableList<String>

}