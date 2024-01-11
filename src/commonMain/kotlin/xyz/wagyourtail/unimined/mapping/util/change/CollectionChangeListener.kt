package xyz.wagyourtail.unimined.mapping.util.change

class CollectionChangeListener<E>(val collection: MutableCollection<E>, val onChange: () -> Unit) : MutableCollection<E>, Collection<E> by collection {
    override fun add(element: E): Boolean {
        onChange()
        return collection.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        onChange()
        return collection.addAll(elements)
    }

    override fun clear() {
        onChange()
        collection.clear()
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        onChange()
        return collection.retainAll(elements)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        onChange()
        return collection.removeAll(elements)
    }

    override fun remove(element: E): Boolean {
        onChange()
        return collection.remove(element)
    }

    override fun iterator(): MutableIterator<E> {
        return collection.iterator().onChange(onChange)
    }

}

fun <E> MutableCollection<E>.onChange(onChange: () -> Unit): MutableCollection<E> {
    return CollectionChangeListener(this, onChange)
}