package xyz.wagyourtail.unimined.mapping.test.jvms

import xyz.wagyourtail.unimined.mapping.jvms.Type

fun buildStringAcceptor(type: Type) = buildString {
    type.accept { it, leaf ->
        if (leaf) append(it)
        true
    }
}
