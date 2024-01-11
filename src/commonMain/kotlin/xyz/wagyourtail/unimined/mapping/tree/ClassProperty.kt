package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.ElementType

/**
 * this is the property for class mappings
 * NamespaceProperty#get is for the class's internal name in each namespace
 */
interface ClassPropertyView: MemberPropertyView<ClassPropertyView> {
    val fields: Collection<FieldPropertyView>
        get() = this[ElementType.FIELD]

    val methods: Collection<MethodPropertyView>
        get() = this[ElementType.METHOD]

    val innerClass: Collection<InnerClassPropertyView>
        get() = this[ElementType.INNER_CLASS]

    fun getField(namespace: String, name: String, desc: String?): FieldPropertyView?
    fun getMethod(namespace: String, name: String, desc: String?): MethodPropertyView?
}

interface ClassProperty: ClassPropertyView, MemberProperty<ClassPropertyView> {
    override val fields: MutableCollection<FieldProperty>
        get() = this[ElementType.FIELD]

    override val methods: MutableCollection<MethodProperty>
        get() = this[ElementType.METHOD]

    override val innerClass: MutableCollection<InnerClassProperty>
        get() = this[ElementType.INNER_CLASS]

    override fun getField(namespace: String, name: String, desc: String?): FieldProperty?
    override fun getMethod(namespace: String, name: String, desc: String?): MethodProperty?

}