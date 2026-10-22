package xyz.wagyourtail.unimined.mapping.formats.zip

import kotlinx.coroutines.await
import okio.Buffer
import okio.BufferedSource
import okio.Closeable
import kotlin.js.Promise

@JsModule("jszip")
external class JSZip : JsAny {
    val files: JsAny
    fun loadAsync(blob: JsAny): Promise<JSZip>
    fun file(path: String): JSZipFile
}

@JsModule("jszip")
external class JSZipFile : JsAny {
    fun async(type: String): Promise<JsString>
}

@JsFun("(jsZip) => Object.keys(jsZip.files)")
private external fun getFileNames(jsZip: JSZip): JsArray<JsString>

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class ZipFS actual constructor(zip: BufferedSource) : Closeable {
    private val bytes = zip.readByteArray().let { data ->
        JsArray<JsNumber>().apply { for (i in data.indices) set(i, (data[i].toInt() and 0xFF).toJsNumber()) }
    }
    private var jsZip: JSZip? = null

    private suspend fun getJSZip(): JSZip {
        val cached = jsZip
        if (cached != null) return cached
        val loaded = JSZip().loadAsync(bytes).await()
        jsZip = loaded
        return loaded
    }

    actual suspend fun getFiles(): List<String> {
        return getFileNames(getJSZip()).toList().map { it.toString() }
    }

    actual suspend fun getContents(path: String): BufferedSource {
        return Buffer().writeUtf8(getJSZip().file(path).async("string").await().toString())
    }

    actual override fun close() {}

}
