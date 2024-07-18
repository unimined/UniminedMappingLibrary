package xyz.wagyourtail.unimined.mapping.formats.unsupported

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object UnsupportedReader : FormatReader {
    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return false
    }

    override suspend fun read(
        input: BufferedSource,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        throw UnsupportedOperationException("Unsupported format")
    }

    override suspend fun read(
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>
    ) {
        throw UnsupportedOperationException("Unsupported format")
    }

}