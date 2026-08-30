package xyz.wagyourtail.unimined.mapping.test.formats.zip

import com.goncalossilva.resources.Resource
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.zip.ZipFS
import kotlin.test.Test
import kotlin.test.assertEquals

class ZipReadWriteTest {
    @Test
    fun testZipRead() = runTest {
        val zip = Resource("test.zip").readBytes()
        Buffer().write(zip).use { buf ->
            val fs = ZipFS(buf)
            fs.getFiles().forEach {
                println(it)
            }
            fs.getContents("test.tiny").use {
                assertEquals(true, it.readUtf8Line()?.startsWith("tiny"))
            }
        }
    }
}
