package xyz.wagyourtail.unimined.mapping.element

enum class InnerType(val key: Char) {
    INNER('i'),
    LOCAL('l'),
    ANONYMOUS('a');

    companion object {
        val byKey = entries.associateBy { it.key }

        init {
            // assert all keys are unique
            if (byKey.size != entries.size) {
                throw IllegalStateException("Duplicate keys found in ${this::class.simpleName}")
            }
        }
    }
}
