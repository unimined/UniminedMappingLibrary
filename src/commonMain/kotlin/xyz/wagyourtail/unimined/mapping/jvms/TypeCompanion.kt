package xyz.wagyourtail.unimined.mapping.jvms

import xyz.wagyourtail.unimined.mapping.util.CharReader

interface TypeCompanion<T> {

    fun shouldRead(reader: CharReader): Boolean

    fun read(value: String) = try {
        CharReader(value).let {
            val readVal = read(it)
            if (!it.exhausted()) {
                throw IllegalArgumentException("Invalid type: \"$value\", not fully read")
            }
            readVal
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid type: $value", e)
    }

    fun read(reader: CharReader): T

    fun unchecked(value: String): T

}