package xyz.wagyourtail.unimined.mapping.formats.zip

import kotlinx.coroutines.await
import okio.Buffer
import okio.BufferedSource
import okio.Closeable
import kotlin.js.Promise

@JsModule("jszip")
external class JSZip {
    val files: dynamic
    fun loadAsync(blob: dynamic): Promise<JSZip>
    fun file(path: String): JSZipFile
}

@JsModule("jszip")
external class JSZipFile {
    fun async(type: String): Promise<String>
}


private fun getFiles(jsZip: JSZip): Array<String> = js("""
    Object.keys(jsZip.files)
""").unsafeCast<Array<String>>()

actual class ZipFS actual constructor(zip: BufferedSource) : Closeable {
    private val bytes = zip.readByteArray().toTypedArray()
    private lateinit var jsZip: JSZip

    private suspend fun getJSZip(): JSZip {
        if (!::jsZip.isInitialized) {
            jsZip = JSZip().loadAsync(bytes).await()
        }
        return jsZip
    }

    actual suspend fun getFiles(): List<String> {
        return getFiles(getJSZip()).toList()
    }

    actual suspend fun getContents(path: String): BufferedSource {
        getJSZip().file(path).async("string").await().let {
            return Buffer().writeUtf8(it)
        }
    }

    override fun close() {}

}