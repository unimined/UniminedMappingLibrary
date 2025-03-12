package xyz.wagyourtail.unimined.mapping.jvms

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.commonskt.utils.escape

interface TypeCompanion<T: Type> {

    fun shouldRead(reader: CharReader<*>): Boolean

    fun read(value: String) = try {
        StringCharReader(value).let { buf ->
            val readVal = read(buf)
            if (!buf.exhausted()) {
                throw IllegalArgumentException("Invalid type: \"${value.escape()}\", not fully read, remaining: \"${buf.takeRemaining().let { if (it.length > 100) it.substring(0, 100) + "..." else it }.escape()}\"")
            }
            readVal
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid type: $value", e)
    }

    fun read(reader: CharReader<*>): T

    fun unchecked(value: String): T

}