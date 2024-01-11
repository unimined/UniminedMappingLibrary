package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag

interface AccessPropertyView: MappingPropertyView<AccessPropertyView> {

    val action: Action

    val access: AccessFlag

    operator fun get(namespace: String): Boolean

    operator fun compareTo(other: AccessPropertyView): Int {
        val c = action.compareTo(other.action)
        if (c != 0) return c
        return access.compareTo(other.access)
    }

    enum class Action {
        ADD,
        REMOVE
    }
}

interface AccessProperty: AccessPropertyView, MappingProperty<AccessPropertyView> {

    override var action: AccessPropertyView.Action

    operator fun set(namespace: String, value: Boolean)

}