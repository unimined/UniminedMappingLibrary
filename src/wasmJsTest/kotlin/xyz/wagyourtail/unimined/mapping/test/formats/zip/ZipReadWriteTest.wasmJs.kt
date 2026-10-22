package xyz.wagyourtail.unimined.mapping.test.formats.zip


@JsFun("() => typeof process === 'object' && process + '' === '[object process]'")
private external fun checkIsNodeJS(): Boolean

@JsFun("() => import.meta.dirname")
private external fun dirname(): String

@JsFun("(path) => Array.from(require('node:fs').readFileSync(path))")
private external fun readFileBytes(path: String): JsArray<JsNumber>

val isNodeJS get() = checkIsNodeJS()

actual suspend fun getResource(name: String): ByteArray? {
    if (!isNodeJS) {
        return null
    }
    val data = readFileBytes(dirname() + "/$name")
    return ByteArray(data.length) { data[it]!!.toInt().toByte() }
}
