package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.ElementType

/**
 * this is the property for method mappings
 * NamespaceProperty#get is for the method's name in each namespace
 */
interface MethodPropertyView: MemberPropertyView<MethodPropertyView> {

    val parameters: Collection<ParameterPropertyView>
        get() = this[ElementType.PARAMETER]

    val locals: Collection<VariablePropertyView>
        get() = this[ElementType.VARIABLE]

    fun getName(namespace: String): String?
    fun hasDescriptor(): Boolean
    fun getDescriptor(namespace: String): String?

}

interface MethodProperty: MethodPropertyView, MemberProperty<MethodPropertyView> {
    override val parameters: MutableCollection<ParameterProperty>
        get() = this[ElementType.PARAMETER]

    override val locals: MutableCollection<VariableProperty>
        get() = this[ElementType.VARIABLE]

    fun setName(namespace: String, name: String?)
    fun setDescriptor(namespace: String, desc: String?)

}