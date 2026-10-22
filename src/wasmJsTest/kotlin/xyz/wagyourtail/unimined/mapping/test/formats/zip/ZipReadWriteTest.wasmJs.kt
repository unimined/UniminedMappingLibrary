package xyz.wagyourtail.unimined.mapping.test.formats.zip


@JsFun("() => typeof process === 'object' && process + '' === '[object process]'")
private external fun checkIsNodeJS(): Boolean

@JsFun("() => import.meta.dirname")
private external fun dirname(): String

@JsModule("node:fs")
private external object Fs {
    fun readFileSync(path: String): JsAny
}

val isNodeJS get() = checkIsNodeJS()

actual suspend fun getResource(name: String): ByteArray? {
    if (!isNodeJS) {
        return null
    }
    val data = Fs.readFileSync(dirname() + "/$name") as JsArray<JsNumber>
    return ByteArray(data.length) { data[it]!!.toInt().toByte() }
}
