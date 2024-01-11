package xyz.wagyourtail.unimined.mapping.tree

/**
 * this is the property for field mappings
 * NamespaceProperty#get is for the field's name in each namespace
 */
interface FieldPropertyView: MemberPropertyView<FieldPropertyView> {
    fun getName(namespace: String): String?
    fun hasDescriptor(): Boolean
    fun getDescriptor(namespace: String): String?
}

interface FieldProperty: FieldPropertyView, MemberProperty<FieldPropertyView> {
    fun setName(namespace: String, name: String?)
    fun setDescriptor(namespace: String, desc: String?)
}