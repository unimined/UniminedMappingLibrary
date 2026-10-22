package xyz.wagyourtail.unimined.mapping.test.jvms

import xyz.wagyourtail.unimined.mapping.jvms.TypeCache
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TestTypeCache {

    @AfterTest
    fun cleanup() {
        TypeCache.maxSize = 0
        TypeCache.clear()
    }

    @Test
    fun cachesPerUniqueKey() {
        var computed = 0
        val a = TypeCache.getOrPut("tag", "key") { computed++; "v$computed" }
        val b = TypeCache.getOrPut("tag", "key") { computed++; "v$computed" }
        assertSame(a, b)
        assertEquals(1, computed)
    }

    @Test
    fun tagsSeparateEntries() {
        var computed = 0
        TypeCache.getOrPut("tagA", "same") { computed++; "v$computed" }
        TypeCache.getOrPut("tagB", "same") { computed++; "v$computed" }
        assertEquals(2, computed)
    }

    @Test
    fun disabledWhenNegative() {
        TypeCache.maxSize = -1
        var computed = 0
        TypeCache.getOrPut("tag", "key") { computed++; "v$computed" }
        TypeCache.getOrPut("tag", "key") { computed++; "v$computed" }
        assertEquals(2, computed)
        assertEquals(0, TypeCache.size)
    }

    @Test
    fun boundedEvictsEldest() {
        TypeCache.maxSize = 2
        TypeCache.getOrPut("tag", "a") { "va" }
        TypeCache.getOrPut("tag", "b") { "vb" }
        TypeCache.getOrPut("tag", "c") { "vc" }
        assertEquals(2, TypeCache.size)
        // "a" evicted — recomputes
        var computed = 0
        TypeCache.getOrPut("tag", "a") { computed++; "va$computed" }
        assertEquals(1, computed)
        // "b" was still cached before this get evicted it... "a"'s get evicts "b" now
        TypeCache.getOrPut("tag", "c") { }
        // "c" survives
        var cComputed = 0
        TypeCache.getOrPut("tag", "c") { cComputed++ }
        assertEquals(0, cComputed)
    }

    @Test
    fun clearEmpties() {
        TypeCache.getOrPut("tag", "a") { "va" }
        TypeCache.clear()
        assertEquals(0, TypeCache.size)
        var computed = 0
        TypeCache.getOrPut("tag", "a") { computed++; "va" }
        assertEquals(1, computed)
    }

}
