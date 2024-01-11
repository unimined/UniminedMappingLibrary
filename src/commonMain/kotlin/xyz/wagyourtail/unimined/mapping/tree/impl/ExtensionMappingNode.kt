package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.ElementType
import xyz.wagyourtail.unimined.mapping.tree.ExtensionProperty
import xyz.wagyourtail.unimined.mapping.visitor.impl.StackedVisitor

abstract class ExtensionMappingNode<V: ExtensionMappingNode<V>> internal constructor(parent: AbstractMappingNode<*>, override val key: String) : AbstractMappingNode<V>(parent), ExtensionProperty<V> {
    override val type: ElementType = ElementType.EXTENSION

    override fun asVisitableIntl() = object : StackedVisitor(this) {}

    override fun compareTo(other: AbstractMappingNode<*>): Int {
        if (other !is ExtensionMappingNode<*>) throw IllegalArgumentException("Cannot compare ExtensionMappingNode to ${other::class.simpleName}")
        return key.compareTo(other.key)
    }

}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object ExtensionMappingNodeFactory {
    fun <V: ExtensionMappingNode<V>> create(parent: AbstractMappingNode<*>, key: String, vararg values: String?): V?
}