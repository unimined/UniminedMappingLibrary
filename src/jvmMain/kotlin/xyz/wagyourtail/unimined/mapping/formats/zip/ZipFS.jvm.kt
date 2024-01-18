package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.Buffer
import okio.BufferedSource
import okio.Closeable
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel

actual class ZipFS actual constructor(zip: BufferedSource) : Closeable {
    val zipFile = ZipFile(SeekableInMemoryByteChannel(zip.readByteArray()))

    actual suspend fun getFiles(): List<String> {
        val files = mutableListOf<String>()
        for (zipArchiveEntry in zipFile.entries) {
            files.add(zipArchiveEntry.name)
        }
        return files
    }

    actual suspend fun getContents(path: String): BufferedSource {
        return Buffer().readFrom(zipFile.getInputStream(zipFile.getEntry(path)))
    }

    override fun close() {
        zipFile.close()
    }

}