package xyz.wagyourtail.unimined.mapping.tree

interface ExtensionPropertyView<V: ExtensionPropertyView<V>>: MappingPropertyView<V> {
    val key: String

    /**
     * implementors should override this to compare values in whatever way makes sense
     */
    operator fun compareTo(other: V): Int {
        return key.compareTo(other.key)
    }

}

interface ExtensionProperty<V: ExtensionProperty<V>>: ExtensionPropertyView<V>, MappingProperty<V>