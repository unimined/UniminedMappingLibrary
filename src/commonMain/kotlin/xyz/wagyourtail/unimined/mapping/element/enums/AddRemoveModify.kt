package xyz.wagyourtail.unimined.mapping.element

enum class AddRemoveModify(val key: Char) {
    ADD('+'),
    REMOVE('-'),
    MODIFY('m');

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
