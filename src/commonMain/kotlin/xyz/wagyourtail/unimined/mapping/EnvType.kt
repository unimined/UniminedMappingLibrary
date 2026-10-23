package xyz.wagyourtail.unimined.mapping

import kotlinx.serialization.Serializable

@Serializable
enum class EnvType {
    CLIENT,
    SERVER,
    JOINED
}