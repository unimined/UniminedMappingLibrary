package xyz.wagyourtail.unimined.mapping.element

import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType

enum class WildcardType {
    METHOD,
    FIELD
    ;

    fun asElementType(): ElementType {
        return when (this) {
            METHOD -> ElementType.METHOD
            FIELD -> ElementType.FIELD
        }
    }
}
