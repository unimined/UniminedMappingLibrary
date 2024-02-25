package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.Closeable

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ZipFS(zip: BufferedSource) : Closeable {

    suspend fun getFiles(): List<String>
    suspend fun getContents(path: String): BufferedSource

}