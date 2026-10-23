package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import okio.use
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.associateWithNonNull
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.formats.FormatRegistry
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

object ZipReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
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
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                ZipFS(input).use { zip ->
            val files = zip.getFiles().associateWithNonNull { FormatRegistry.autodetectFormat(it.replace('\\', '/'), zip.getContents(it)) }
            for ((file, format) in files.entries.sortedBy { FormatRegistry.formats.indexOf(it.value) }) {
                if (!format.getSide(file, zip.getContents(file)).contains(envType)) continue
                format.read(
                    zip.getContents(file),
                    context,
                    into,
                    nsMapping.filterKeys { it.startsWith(format.name + "-") }.mapKeys { it.key.removePrefix(format.name + "-") },
                    FormatReaderSettings(envType = envType) or settings,
                )
            }
        }
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings,
    ) {
        val envType = settings.envType
        throw UnsupportedOperationException("ZipReader does not support CharReader")
    }

}