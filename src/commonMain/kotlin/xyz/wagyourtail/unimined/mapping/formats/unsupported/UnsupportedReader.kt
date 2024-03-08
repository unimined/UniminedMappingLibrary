package xyz.wagyourtail.unimined.mapping.formats.unsupported

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object UnsupportedReader : FormatReader {
    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return false
    }

    override suspend fun read(
        envType: EnvType,
        inputType: BufferedSource,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        throw UnsupportedOperationException("Unsupported format")
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        throw UnsupportedOperationException("Unsupported format")
    }

}