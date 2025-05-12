package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.associateWithNonNull
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object ZipReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        input.peek().use {
            try {
                val bs = it.readByteString(4)
                return bs == "PK\u0003\u0004".encodeUtf8() || bs == "PK\u0005\u0006".encodeUtf8()
            } catch (e: Exception) {
                return false
            }
        }
    }

    override suspend fun read(
        input: BufferedSource,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        ZipFS(input).use { zip ->
            val files = zip.getFiles().associateWithNonNull { FormatRegistry.autodetectFormat(envType, it.replace('\\', '/'), zip.getContents(it)) }
            for ((file, format) in files.entries.sortedBy { FormatRegistry.formats.indexOf(it.value) }) {
                if (!format.getSide(file, zip.getContents(file)).contains(envType)) continue
                format.read(
                    zip.getContents(file),
                    context,
                    into,
                    envType,
                    nsMapping.filterKeys { it.startsWith(format.name + "-") }.mapKeys { it.key.removePrefix(format.name + "-") },
                    settings or this,
                )
            }
        }
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings,
    ) {
        throw UnsupportedOperationException("ZipReader does not support CharReader")
    }

}