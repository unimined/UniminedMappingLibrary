package xyz.wagyourtail.unimined.mapping.test.zip

class Ref

actual suspend fun getResource(name: String): ByteArray? {
    return Ref::class.java.getResource("/$name")?.readBytes()
}