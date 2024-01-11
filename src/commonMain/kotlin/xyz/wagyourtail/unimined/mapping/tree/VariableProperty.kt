package xyz.wagyourtail.unimined.mapping.tree

interface VariablePropertyView: NamespacedPropertyView<VariablePropertyView> {

    val lvOrdinal: Int

    val startOpIdx: Int?

    operator fun compareTo(other: VariablePropertyView): Int {
        if (startOpIdx != null && other.startOpIdx == null) {
            return -1
        }
        if (startOpIdx == null && other.startOpIdx != null) {
            return 1
        }
        if (startOpIdx == null && other.startOpIdx == null) {
            return lvOrdinal.compareTo(other.lvOrdinal)
        }
        return startOpIdx!!.compareTo(other.startOpIdx!!)
    }

}

interface VariableProperty: VariablePropertyView, NamespacedProperty<VariablePropertyView> {

    override var lvOrdinal: Int

    override var startOpIdx: Int?

}