package xyz.wagyourtail.unimined.mapping.tree.impl

import io.github.oshai.kotlinlogging.KotlinLogging

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object ExtensionMappingNodeFactory {
    private val LOGGER = KotlinLogging.logger {}
    private val registry = mutableMapOf<String, (String, Array<out String?>) -> ExtensionMappingNode<*>>()

    fun register(key: String, factory: (String, Array<out String?>) -> ExtensionMappingNode<*>) {
        registry[key] = factory
    }

    actual fun <V: ExtensionMappingNode<V>> create(parent: AbstractMappingNode<*>, key: String, vararg values: String?): V? {
        @Suppress("UNCHECKED_CAST")
        val value = registry[key]?.invoke(key, values) as? V
        return if (value != null) {
            value
        } else {
            LOGGER.warn {
                "Unknown extension key $key. ignoring."
            }
            null
        }
    }
}