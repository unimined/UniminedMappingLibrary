package xyz.wagyourtail.unimined.mapping.util.change

class CollectionChangeListener<E>(val collection: MutableCollection<E>, val onChange: (ChangeType, E) -> Unit):
    MutableCollection<E>, Collection<E> by collection {
    override fun add(element: E): Boolean {
        onChange(ChangeType.ADD, element)
        return collection.add(element)
    }

    override fun addAll(elements: Collection<E>): Boolean {
        for (e in elements) {
            onChange(ChangeType.ADD, e)
        }
        return collection.addAll(elements)
    }

    override fun clear() {
        for (e in collection) {
            onChange(ChangeType.REMOVE, e)
        }
        collection.clear()
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        val removed = collection.filter { !elements.contains(it) }
        for (e in removed) {
            onChange(ChangeType.REMOVE, e)
        }
        return collection.retainAll(elements)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        for (e in elements) {
            onChange(ChangeType.REMOVE, e)
        }
        return collection.removeAll(elements)
    }

    override fun remove(element: E): Boolean {
        onChange(ChangeType.REMOVE, element)
        return collection.remove(element)
    }

    override fun iterator(): MutableIterator<E> {
        return collection.iterator().onChange(onChange)
    }

}

fun <E> MutableCollection<E>.onChange(onChange: (ChangeType, E) -> Unit): MutableCollection<E> {
    return CollectionChangeListener(this, onChange)
}