package xyz.wagyourtail.unimined.mapping

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

class Namespace private constructor(val name: String) {

    companion object {
        val names = mutableMapOf<String, Namespace>()
        val sync = SynchronizedObject()

        operator fun invoke(name: String): Namespace {
            synchronized(sync) {
                return names.getOrPut(name) { Namespace(name) }
            }
        }
    }

    override fun toString(): String = name

}