package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.Buffer
import okio.BufferedSource
import okio.Closeable
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ZipFS actual constructor(zip: BufferedSource) : Closeable {
    private val zipFile = ZipFile.builder()
        .setSeekableByteChannel(SeekableInMemoryByteChannel(zip.readByteArray()))
        .setIgnoreLocalFileHeader(true)
        .get()

    actual suspend fun getFiles(): List<String> {
        val files = mutableListOf<String>()
        for (zipArchiveEntry in zipFile.entries) {
            files.add(zipArchiveEntry.name)
        }
        return files
    }

    actual suspend fun getContents(path: String): BufferedSource {
        return zipFile.getInputStream(zipFile.getEntry(path)).use { Buffer().readFrom(it) }
    }

    override fun close() {
        zipFile.close()
    }

}