package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.ElementType

interface RootPropertyView: MappingPropertyView<RootPropertyView> {

    val namespaces: List<String>
    val completedClasses: Set<String>

    val classes: Collection<ClassPropertyView>
        get() = this[ElementType.CLASS]

    fun getClass(namespace: String, name: String): ClassPropertyView?
}

interface RootProperty: RootPropertyView, MappingProperty<RootPropertyView> {

    override val classes: MutableCollection<ClassProperty>

    override fun getClass(namespace: String, name: String): ClassProperty?

    fun addNamespacess(vararg namespaces: String)
}