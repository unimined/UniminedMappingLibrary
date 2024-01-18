package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.Closeable

expect class ZipFS(zip: BufferedSource) : Closeable {

    suspend fun getFiles(): List<String>
    suspend fun getContents(path: String): BufferedSource

}