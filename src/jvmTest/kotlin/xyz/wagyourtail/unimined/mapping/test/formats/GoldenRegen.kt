package xyz.wagyourtail.unimined.mapping.test.formats

import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

// Regenerates firstparty/expected/*.umf goldens — run after intentional
// reader/UMF changes, then review the diff before committing.
class GoldenRegen {
    @Test
    fun regen() = runTest {
        val dir = File("src/commonTest/resources/firstparty/expected").also { it.mkdirs() }
        for ((name, dump) in FirstPartyCorpus.all()) {
            File(dir, "$name.umf").writeText(dump + "\n")
        }
        println("wrote ${dir.listFiles()!!.size} goldens to ${dir.absolutePath}")
    }
}
