package xyz.wagyourtail.unimined.mapping.test.serialization

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.use
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.serialization.SerializableMappingTree
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SerializableMappingTreeTest {

    private val json = Json { prettyPrint = false }

    private val doc = """
        umf	1	3
        intermediary	named	extra
        k	com/mojang/	_
        c	net/minecraft/class_310	net/minecraft/client/MinecraftClient	_
        	*	"example comment"	named
        	f	field_1724	_	_
        	m	method_1507;()V	testMethod;()V	_
        		p	_	0	_	this
        		v	1	_	_	lv1
        	m	method_1	_	_
        	w	f	I	_
        	w	m	()V	_
        	u	PLAIN	SomeConstants	intermediary
        		n	some/Field	CONSTANT;I
        		t	Ltarget/Method;	2
        		e	42	"20 + 22"
        c	net/minecraft/class_1	_	a/ClassOne
        	m	m_1;()I	_	_
        """.trimIndent()

    @Test
    fun roundTripPreservesEmittedTree() = runTest {
        val tree = Buffer().use { input ->
            input.writeUtf8(doc)
            UMFReader.read(input)
        }

        val wire = SerializableMappingTree.of(tree)
        val encoded = json.encodeToString(SerializableMappingTree.serializer(), wire)
        val decoded = json.decodeFromString(SerializableMappingTree.serializer(), encoded)
        val restored = decoded.toTree()

        fun render(t: xyz.wagyourtail.unimined.mapping.MappingTree) = Buffer().use { out ->
            t.emit(UMFWriter.write(out, false), sort = true)
            out.readUtf8()
        }

        assertEquals(render(tree), render(restored))
    }

    @Test
    fun decodedTreeSupportsLookups() = runTest {
        val tree = Buffer().use { input ->
            input.writeUtf8(doc)
            UMFReader.read(input)
        }
        val restored = json.decodeFromString(
            SerializableMappingTree.serializer(),
            json.encodeToString(SerializableMappingTree.serializer(), SerializableMappingTree.of(tree))
        ).toTree()

        val intermediary = Namespace("intermediary")
        val cls = restored.getClass(intermediary, InternalName.unchecked("net/minecraft/class_310"))
        assertNotNull(cls)
        assertEquals(2, cls.methods.count())

        // a class row missing a namespace decodes its sparse cell correctly
        val sparse = restored.getClass(Namespace("extra"), InternalName.unchecked("a/ClassOne"))
        assertNotNull(sparse)
    }

    @Test
    fun namespaceTableIsSerializedOnce() = runTest {
        val tree = Buffer().use { input ->
            input.writeUtf8(doc)
            UMFReader.read(input)
        }
        val wire = SerializableMappingTree.of(tree)
        assertEquals(listOf("intermediary", "named", "extra"), wire.namespaces)
        // no Namespace objects / map keys in the rows — encoded rows are positional arrays
        val encoded = json.encodeToString(SerializableMappingTree.serializer(), wire)
        assertTrue(encoded.contains("\"namespaces\":[\"intermediary\",\"named\",\"extra\"]"))
        assertTrue(encoded.contains("\"names\":[\"com/mojang/\",null,null]"))
    }

}
