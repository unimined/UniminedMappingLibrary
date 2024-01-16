package xyz.wagyourtail.unimined.mapping.formats

import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatWriter<T> {

    fun write(into: T): MappingVisitor

}