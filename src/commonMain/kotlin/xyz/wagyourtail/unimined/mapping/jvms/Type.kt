package xyz.wagyourtail.unimined.mapping.jvms

interface Type {

    fun accept(visitor: (Any) -> Boolean)

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        accept { it -> visitor(it, it !is Type) }
    }

}