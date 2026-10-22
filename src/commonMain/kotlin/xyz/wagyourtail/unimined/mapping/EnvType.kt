package xyz.wagyourtail.unimined.mapping

import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import kotlinx.serialization.Serializable

@Serializable
enum class EnvType {
    CLIENT,
    SERVER,
    JOINED
}