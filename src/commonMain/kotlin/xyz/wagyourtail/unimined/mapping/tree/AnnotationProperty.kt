package xyz.wagyourtail.unimined.mapping.tree

interface AnnotationPropertyView: MappingPropertyView<AnnotationPropertyView> {

    val action: Action

    val srcNs: String

    val key: String

    val value: String?

    operator fun get(namespace: String): Boolean

    operator fun compareTo(other: AnnotationPropertyView): Int {
        val c = action.compareTo(other.action)
        if (c != 0) return c
        val c3 = key.compareTo(other.key)
        if (c3 != 0) return c3
        return value?.compareTo(other.value ?: value!!) ?: 0
    }

    enum class Action {
        ADD,
        REMOVE,
        MODIFY
    }

}

interface AnnotationProperty: AnnotationPropertyView, MappingProperty<AnnotationPropertyView> {

    override var action: AnnotationPropertyView.Action

    override var key: String

    override var value: String?

    operator fun set(namespace: String, value: Boolean)

}