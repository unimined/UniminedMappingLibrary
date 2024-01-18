package xyz.wagyourtail.unimined.mapping.test.zip

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipFS
import kotlin.test.Test
import kotlin.test.assertTrue

expect suspend fun getResource(name: String): ByteArray?

class ZipReadWriteTest {

    @Test
    fun testZipRead() = runTest {
        val zip = getResource("test.zip") ?: return@runTest
        Buffer().write(zip).use { buf ->
            val fs = ZipFS(buf)
            fs.getFiles().forEach {
                println(it)
            }
            fs.getContents("test.tiny").use {
                assertTrue(it.readUtf8Line()?.startsWith("tiny") == true)
            }
        }
    }

}