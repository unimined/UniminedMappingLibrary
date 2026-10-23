package xyz.wagyourtail.unimined.mapping.test.sink

import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * emit-time `nsRenamer` must rewrite `NsBoundMetadata.baseNs` (and ns-keyed
 * metadata name maps) — not just element name keys — or emitted rows carry
 * a stale namespace.
 */
class EmitNsRenameTest {

    private val intermediary = Namespace("intermediary")
    private val renamed = Namespace("renamed")

    @Test
    fun nsRenamerRewritesMetadataBaseNs() {
        val tree = MutableMappingTree()
        val cls = tree.clazz(mapOf(intermediary to InternalName.unchecked("a/Cls")))
        cls.addMetadata(JavadocElement("cls javadoc", intermediary))
        cls.method(mapOf(intermediary to MethodNameAndDescriptor.unchecked("m_1;()V")))
            .addMetadata(JavadocElement("method javadoc", intermediary))

        val out = Buffer().use { buf ->
            tree.emit(
                UMFWriter.write(buf),
                nsRenamer = { if (it == intermediary) renamed else it },
            )
            buf.readUtf8()
        }

        // name keys renamed
        assertTrue("a/Cls" in out, "class row missing: $out")
        // javadoc rows carry the renamed baseNs, not the original
        val javadocRows = out.lines().filter { "javadoc" in it }
        assertTrue(javadocRows.isNotEmpty(), "no javadoc rows emitted: $out")
        for (row in javadocRows) {
            assertTrue(row.endsWith(renamed.name), "baseNs not renamed: $row")
        }
    }

}
