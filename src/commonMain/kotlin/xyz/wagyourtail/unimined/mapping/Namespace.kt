package xyz.wagyourtail.unimined.mapping

import kotlinx.atomicfu.locks.SynchronizedObject
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import kotlinx.atomicfu.locks.synchronized

class Namespace private constructor(val name: String) {

    companion object {
        private val names = newSmallMap<String, Namespace>()
        private val sync = SynchronizedObject()

        operator fun invoke(name: String): Namespace {
            synchronized(sync) {
                return names.getOrPut(name) { Namespace(name) }
            }
        }
    }

    override fun toString(): String = name

}