package xyz.wagyourtail.unimined.mapping.test.jvms

import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

fun buildStringAcceptor(type: Type) = buildString {
    type.accept { it, leaf ->
        if (leaf) append(it)
        true
    }
}
