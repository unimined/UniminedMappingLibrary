package xyz.wagyourtail.unimined.mapping

import kotlin.jvm.JvmInline

@JvmInline
value class Namespace(val name: String) {

    override fun toString(): String = name

}