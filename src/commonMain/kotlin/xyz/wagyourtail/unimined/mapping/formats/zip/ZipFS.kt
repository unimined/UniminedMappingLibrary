package xyz.wagyourtail.unimined.mapping.formats.zip

import okio.BufferedSource
import okio.Closeable
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class ZipFS(zip: BufferedSource) : Closeable {

    suspend fun getFiles(): List<String>
    suspend fun getContents(path: String): BufferedSource

    override fun close()

}