package xyz.wagyourtail.unimined.mapping.formats

import xyz.wagyourtail.unimined.mapping.tree.MappingTree

interface FormatReader<T> {

    fun read(inputType: T): MappingTree = MappingTree().also { read(inputType, it) }

    fun read(inputType: T, into: MappingTree)

}