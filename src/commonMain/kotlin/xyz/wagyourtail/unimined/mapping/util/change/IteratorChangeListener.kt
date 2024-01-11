package xyz.wagyourtail.unimined.mapping.util.change

class IteratorChangeListener<E>(val iterator: MutableIterator<E>, val onChange: (ChangeType, E) -> Unit):
    MutableIterator<E>, Iterator<E> by iterator {
    var current: E? = null

    override fun next(): E {
        current = iterator.next()
        return current!!
    }

    override fun remove() {
        onChange(ChangeType.REMOVE, current!!)
        iterator.remove()
    }

}

fun <E> MutableIterator<E>.onChange(onChange: (ChangeType, E) -> Unit): MutableIterator<E> {
    return IteratorChangeListener(this, onChange)
}