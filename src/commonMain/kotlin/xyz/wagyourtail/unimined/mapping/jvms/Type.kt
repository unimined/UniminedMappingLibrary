package xyz.wagyourtail.unimined.mapping.jvms

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.commonskt.utils.escape
import kotlin.jvm.JvmOverloads

interface Type {

    fun accept(visitor: (Any) -> Boolean)

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        accept { it -> visitor(it, it !is Type) }
    }

}

interface TypeCompanion<T: Type> {

    fun shouldRead(reader: CharReader<*>): Boolean

    fun read(value: String, unchecked: Boolean = false) = try {
        if (unchecked) {
            unchecked(value)
        } else {
            StringCharReader(value).let { buf ->
                val readVal = read(buf)
                if (!buf.exhausted()) {
                    throw IllegalArgumentException(
                        "Invalid ${unchecked("null")::class.simpleName}: \"${value.escape()}\", not fully read, remaining: \"${
                            buf.takeRemaining().let { if (it.length > 100) it.substring(0, 100) + "..." else it }
                                .escape()
                        }\""
                    )
                }
                readVal
            }
        }
    } catch (e: Exception) {
        throw IllegalArgumentException("${e.message}, reading \"${value.escape()}\"", e)
    }

    fun read(reader: CharReader<*>, append: (Any) -> Unit)

    fun read(reader: CharReader<*>): T = try {
        unchecked(buildString {
            read(reader) { append(it) }
        })
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid ${unchecked("null")::class.simpleName}", e)
    }

    fun unchecked(value: String): T

}