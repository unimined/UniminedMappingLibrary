package xyz.wagyourtail.unimined.mapping.jvms

import okio.Buffer
import okio.BufferedSource
import okio.use

interface TypeCompanion<T> {

    fun shouldRead(reader: BufferedSource): Boolean

    fun read(value: String) = try {
        Buffer().use {
            it.writeUtf8(value)
            val readVal = read(it)
            if (!it.exhausted()) {
                throw IllegalArgumentException("Invalid type: $value")
            }
            readVal
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid type: $value", e)
    }

    fun read(reader: BufferedSource): T

}