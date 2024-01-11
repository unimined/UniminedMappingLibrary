package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.ElementType

/**
 * this is for setting InnerClass attribute mappings
 * NamespaceProperty#get is for inner names
 *
 * descriptor is the descriptor of the containing method/field/class
 * if a method/field descriptor,
 * will be the format discussed in the Unimined Mapping Format document
 */
interface InnerClassPropertyView: NamespacedPropertyView<InnerClassPropertyView> {

    val innerType: InnerType

    val access: Collection<AccessPropertyView>
        get() = this[ElementType.ACCESS]

    fun getName(namespace: String): String?
    fun getDescriptor(namespace: String): String?

    enum class InnerType {
        INNER,
        ANONYMOUS,
        LOCAL
    }
}

interface InnerClassProperty: InnerClassPropertyView, NamespacedProperty<InnerClassPropertyView> {

    override var innerType: InnerClassPropertyView.InnerType

    override val access: MutableCollection<AccessProperty>
        get() = this[ElementType.ACCESS]

    fun setName(namespace: String, name: String?)
    fun setDescriptor(namespace: String, desc: String?)

}