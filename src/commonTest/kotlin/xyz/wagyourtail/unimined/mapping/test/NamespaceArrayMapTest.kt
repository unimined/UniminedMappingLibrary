package xyz.wagyourtail.unimined.mapping.test

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.NamespaceArrayMap
import xyz.wagyourtail.unimined.mapping.toNsMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamespaceArrayMapTest {

    @Test
    fun getPutRemoveContains() {
        val a = Namespace("test_arr_a")
        val b = Namespace("test_arr_b")
        val map = NamespaceArrayMap<String>()
        assertNull(map[a])
        assertFalse(a in map)

        map[a] = "x"
        assertEquals("x", map[a])
        assertTrue(a in map)
        assertEquals(1, map.size)

        map[b] = "y"
        assertEquals("y", map[b])
        assertEquals(2, map.size)

        assertEquals("x", map.remove(a))
        assertNull(map[a])
        assertFalse(a in map)
        assertEquals(1, map.size)
        assertNull(map.remove(a))
    }

    @Test
    fun iterationOrderMatchesSourceOrder() {
        // entries iterate in namespace ordinal (i.e. insertion-into-intern-pool) order
        val a = Namespace("test_ord_a")
        val b = Namespace("test_ord_b")
        val c = Namespace("test_ord_c")
        val map = NamespaceArrayMap<Int>()
        map[c] = 3
        map[a] = 1
        map[b] = 2
        val ordered = listOf(a, b, c).sortedBy { it.ordinal }
        assertEquals(ordered, map.keys.toList())
        assertEquals(ordered.map { map[it] }, map.values.toList())
        assertEquals(ordered, map.entries.map { it.key })
    }

    @Test
    fun clearAndOverwrite() {
        val a = Namespace("test_co_a")
        val map = NamespaceArrayMap<String>()
        map[a] = "x"
        map[a] = "z"
        assertEquals("z", map[a])
        assertEquals(1, map.size)
        map.clear()
        assertTrue(map.isEmpty())
        assertNull(map[a])
    }

    @Test
    fun equalityWithStandardMap() {
        val a = Namespace("test_eq_a")
        val b = Namespace("test_eq_b")
        val arr = NamespaceArrayMap<String>()
        arr[a] = "1"
        arr[b] = "2"
        val std = mutableMapOf(a to "1", b to "2")
        assertEquals(std, arr)
        assertEquals(arr, std)
        assertEquals(std.hashCode(), arr.hashCode())
    }

    @Test
    fun dynamicallyInternedNamespacesAreHandled() {
        // a namespace interned *after* the map exists must still be storable
        val map = NamespaceArrayMap<String>()
        val fresh = Namespace("test_dyn_${kotlin.random.Random.nextInt()}")
        map[fresh] = "v"
        assertEquals("v", map[fresh])
    }

    @Test
    fun toNsMapProducesEquivalentMap() {
        val a = Namespace("test_cvt_a")
        val b = Namespace("test_cvt_b")
        val src = mapOf(a to "1", b to "2")
        val converted = src.toNsMap()
        assertEquals(src, converted)
        assertEquals("1", converted[a])
        // and is independently mutable
        converted.remove(a)
        assertTrue(a in src)
    }

    @Test
    fun storedNullIsDistinctFromAbsent() {
        val a = Namespace("test_null_a")
        val b = Namespace("test_null_b")
        val map = NamespaceArrayMap<String?>()
        map[a] = null
        assertTrue(a in map)
        assertNull(map[a])
        assertEquals(1, map.size)
        assertNull(map.remove(a))
        assertFalse(a in map)
        assertFalse(b in map)
    }

    @Test
    fun missingKeyLookupsDontThrow() {
        val map = NamespaceArrayMap<String>()
        val unknown = Namespace("test_never_inserted")
        assertNull(map[unknown])
        assertNull(map.remove(unknown))
        assertFalse(unknown in map)
    }
}
