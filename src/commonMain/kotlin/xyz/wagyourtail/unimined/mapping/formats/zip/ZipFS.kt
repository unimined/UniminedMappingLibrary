package xyz.wagyourtail.unimined.mapping.formats.zip

import no.synth.kmpzip.okio.asSource
import no.synth.kmpzip.zip.ZipFile
import okio.BufferedSource
import okio.Closeable
import okio.buffer

class ZipFS(zip: BufferedSource) : Closeable {
    private val zipFile = ZipFile(zip.readByteArray())

    fun getFiles(): List<String> = zipFile.entries.map { it.name }

    fun getContents(path: String): BufferedSource {
        return zipFile.getInputStream(zipFile.getEntry(path)
            ?: error("ZIP entry not found: $path")).asSource().buffer()
    }

    override fun close() {
        zipFile.close()
    }
}
