package xyz.wagyourtail.unimined.mapping.util.change

class IteratorChangeListener<E>(val iterator: MutableIterator<E>, val onChange: () -> Unit) : MutableIterator<E>, Iterator<E> by iterator {
    override fun remove() {
        onChange()
        iterator.remove()
    }
}

fun <E> MutableIterator<E>.onChange(onChange: () -> Unit): MutableIterator<E> {
    return IteratorChangeListener(this, onChange)
}