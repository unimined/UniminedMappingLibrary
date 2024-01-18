package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object ZipReader : FormatReader {
    override fun isFormat(fileName: String, inputType: BufferedSource): Boolean {
        inputType.peek().use {
            val bs = it.readByteString(2)
            return bs == "PK\u0003\u0004".encodeUtf8() || bs == "PK\u0005\u0006".encodeUtf8()
        }
    }

    override fun read(inputType: BufferedSource, into: MappingVisitor, unnamedNamespaceNames: List<String>) {
        TODO("Not yet implemented")
    }
}