package repro

import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import kotlin.test.Test

class UmfHangRepro {
    @Test
    fun readRealYarnUmf() {
        for (name in listOf("440df64368fe1009919bfa55d734034b1e18d174", "0388db6540c93ef7027a6934719d375666412bef", "a4692fa346ae89a4fc667fc5fff03c359ad07074", "b5de37b42ccb73a6ffd0cb029fbc5793f17c37a9")) {
            val p = "/tmp/umf-$name.umf".toPath()
            val content = FileSystem.SYSTEM.read(p) { readUtf8().replace("\r", "") }
            println("reading $name (${content.length}B)")
            runBlocking { UMFReader.read(StringCharReader(content)) }
            println("ok $name")
        }
    }
}
