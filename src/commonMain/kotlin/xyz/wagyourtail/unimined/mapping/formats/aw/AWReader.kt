package xyz.wagyourtail.unimined.mapping.formats.aw

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object AWReader : FormatReader {
    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        // check content begins with "accessWidener"
        return inputType.peek().readUtf8Line()?.startsWith("accessWidener") ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

    }

}