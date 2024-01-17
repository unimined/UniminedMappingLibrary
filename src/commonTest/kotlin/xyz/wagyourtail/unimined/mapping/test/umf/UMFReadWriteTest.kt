package xyz.wagyourtail.unimined.mapping.test.umf

import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import kotlin.test.Test
import kotlin.test.assertEquals

class UMFReadWriteTest {

    @Test
    fun testReadWrite() {
        val inp = """
umf 1 0
intermediary named
c net/minecraft/class_310 net/minecraft/client/MinecraftClient
 * "example\tcomment" 0
 f field_1724 _
 m method_1507;()V testMethod
  p _ 0 _ this
  v 1 _ _ lv1
        """.trimIndent().replace(' ', '\t').trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output))
            output.readUtf8()
        }
        assertEquals(inp, output.trimEnd())
    }

    @Test
    fun testMergableMethods() {
        val inp = """
umf 1 0
intermediary named extra
c net/minecraft/class_310 net/minecraft/client/MinecraftClient _
 f field_1724 _ _
 m method_1507;()V testMethod _
  p _ 0 _ _ p1
 m _ testMethod;()V methodNameExtra
  p _ 0 _ this _
  v 1 _ _ lv1 lv1Extra
 m __ dontMerge;()V dontMerge2
        """.trimIndent().replace(' ', '\t').trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output))
            output.readUtf8()
        }
        val testOutput = """
umf 1 0
intermediary named extra
c net/minecraft/class_310 net/minecraft/client/MinecraftClient _
 f field_1724 _ _
 m method_1507;()V testMethod;()V methodNameExtra
  p _ 0 _ this p1
  v 1 _ _ lv1 lv1Extra
 m __ dontMerge;()V dontMerge2
        """.trimIndent().replace(' ', '\t').trimEnd()

        assertEquals(testOutput, output.trimEnd())
    }

    @Test
    fun testMinimize() {
        val inp = """
umf 1 0
intermediary named extra
c net/minecraft/class_310 net/minecraft/client/MinecraftClient _
 f field_1724;Lnet/minecraft/class_746; test;Lnamed/class; _
 m method_1507;()V testMethod;()V _
 m __ dontMerge;()V dontMerge2;()V
        """.trimIndent().replace(' ', '\t').trimEnd()

        val mappings = Buffer().use { input ->
            input.writeUtf8(inp)
            UMFReader.read(input)
        }
        val output = Buffer().use { output ->
            mappings.accept(UMFWriter.write(output), true)
            output.readUtf8()
        }
        val testOutput = """
umf 1 0
intermediary named extra
c net/minecraft/class_310 net/minecraft/client/MinecraftClient _
 f field_1724;Lnet/minecraft/class_746; test _
 m method_1507;()V testMethod _
 m _;()V dontMerge dontMerge2
        """.trimIndent().replace(' ', '\t').trimEnd()

        assertEquals(testOutput, output.trimEnd())
    }
}