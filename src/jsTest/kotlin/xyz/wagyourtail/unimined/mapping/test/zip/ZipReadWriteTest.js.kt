package xyz.wagyourtail.unimined.mapping.test.zip

val isNodeJS = js("typeof process === 'object' && process + '' === '[object process]'") as Boolean

actual suspend fun getResource(name: String): ByteArray? {
    if (!isNodeJS) {
        return null
    }
    val fs = js("require")("fs")
    return fs.readFileSync(js("__dirname") + "/$name").unsafeCast<ByteArray>()
}
