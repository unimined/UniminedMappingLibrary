package xyz.wagyourtail.unimined.mapping.tree

/**
 * this is for setting parameter properties on methods
 *
 */
interface ParameterPropertyView: NamespacedPropertyView<ParameterPropertyView> {

    val index: Int?

    val lvOrdinal: Int?

    operator fun compareTo(other: ParameterPropertyView): Int {
        if (index != null && other.index == null) {
            return -1
        }
        if (index == null && other.index != null) {
            return 1
        }
        if (index == null && other.index == null) {
            return lvOrdinal?.compareTo(other.lvOrdinal ?: 0) ?: 0
        }
        return index!!.compareTo(other.index!!)
    }

}

interface ParameterProperty: ParameterPropertyView, NamespacedProperty<ParameterPropertyView> {

    override var index: Int?

    override var lvOrdinal: Int?

}