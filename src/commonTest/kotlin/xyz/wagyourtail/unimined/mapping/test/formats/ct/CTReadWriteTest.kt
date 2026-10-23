package xyz.wagyourtail.unimined.mapping.test.formats.ct

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.element.metadata.EnumExtensionElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InterfaceElement
import xyz.wagyourtail.unimined.mapping.formats.ct.CTReader
import xyz.wagyourtail.unimined.mapping.formats.ct.CTWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CTReadWriteTest {
    private val ns = Namespace("intermediary")

    @Test
    fun readsAndWritesSupportedDirectives() = runTest {
        val input = """
            classTweaker v2 intermediary
            accessible class example/Target
            mutable field example/Target value I
            extendable method example/Target run ()V
            inject-interface example/Target example/Marker<TT;>
            extend-enum example/Mode EXTRA
        """.trimIndent()

        val tree = CTReader.read(input)
        val target = tree.getClass(ns, InternalName.unchecked("example/Target"))!!
        assertTrue(target.metadata.any { it is AccessElement && it.value == AccessFlag.PUBLIC })
        assertTrue(target.metadata.any { it is InterfaceElement && it.type == AddRemove.ADD })
        assertTrue(target.fields.single().metadata.any {
            it is AccessElement && it.type == AddRemove.REMOVE && it.value == AccessFlag.FINAL
        })
        assertTrue(target.methods.single().metadata.any {
            it is AccessElement && it.type == AddRemove.REMOVE && it.value == AccessFlag.FINAL
        })
        val enumClass = tree.getClass(ns, InternalName.unchecked("example/Mode"))!!
        assertEquals("EXTRA", enumClass.metadata.filterIsInstance<EnumExtensionElement>().single().name.value)

        val output = Buffer().use { buffer ->
            tree.emit(CTWriter.write(buffer), nsFilter = listOf(ns))
            buffer.readUtf8()
        }
        assertTrue(output.startsWith("classTweaker\tv2\tintermediary\n"))
        assertTrue(output.contains("inject-interface\texample/Target\texample/Marker<TT;>"))
        assertTrue(output.contains("extend-enum\texample/Mode\tEXTRA"))

        val roundTripped = CTReader.read(output)
        val roundTrippedClass = roundTripped.getClass(ns, InternalName.unchecked("example/Target"))!!
        assertTrue(roundTrippedClass.metadata.any { it is InterfaceElement })
        assertTrue(roundTrippedClass.fields.single().metadata.any { it is AccessElement })
    }
}
