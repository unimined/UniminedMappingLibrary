package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.util.associateWithNonNull
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object ZipReader : FormatReader {

    override fun isFormat(fileName: String, inputType: BufferedSource): Boolean {
        inputType.peek().use {
            try {
                val bs = it.readByteString(4)
                return bs == "PK\u0003\u0004".encodeUtf8() || bs == "PK\u0005\u0006".encodeUtf8()
            } catch (e: Exception) {
                return false
            }
        }
    }

    override suspend fun read(inputType: BufferedSource, context: MappingTree?, into: MappingVisitor, unnamedNamespaceNames: List<String>) {
        ZipFS(inputType).use { zip ->
            val files = zip.getFiles().associateWithNonNull { FormatRegistry.autodetectFormat(it, zip.getContents(it)) }
            for ((file, format) in files.entries.sortedBy { FormatRegistry.formats.indexOf(it.value) }) {
                format.reader.read(zip.getContents(file), context, into, unnamedNamespaceNames)
            }
        }
    }

}