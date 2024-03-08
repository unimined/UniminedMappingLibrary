package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.associateWithNonNull
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object ZipReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        inputType.peek().use {
            try {
                val bs = it.readByteString(4)
                return bs == "PK\u0003\u0004".encodeUtf8() || bs == "PK\u0005\u0006".encodeUtf8()
            } catch (e: Exception) {
                return false
            }
        }
    }

    override suspend fun read(envType: EnvType, inputType: BufferedSource, context: AbstractMappingTree?, into: MappingVisitor, nsMapping: Map<String, String>) {
        ZipFS(inputType).use { zip ->
            val files = zip.getFiles().associateWithNonNull { FormatRegistry.autodetectFormat(envType, it.replace('\\', '/'), zip.getContents(it)) }
            for ((file, format) in files.entries.sortedBy { FormatRegistry.formats.indexOf(it.value) }) {
                if (!format.getSide(file, zip.getContents(file)).contains(envType)) continue
                format.reader.read(envType, zip.getContents(file), context, into, nsMapping.filterKeys { it.startsWith(format.name + "-") }.mapKeys { it.key.removePrefix(format.name + "-") })
            }
        }
    }

}