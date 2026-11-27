package xyz.wagyourtail.unimined.mapping.remapper.nil

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NilMappingsWriterTest {

    @Test
    fun flatEntriesWithMembers() {
        val m = NilMappings()
        val e = NilClassEntry("net/minecraft/client/render/GameRenderer", "ban")
        e.methods["transformCamera(F)V"] = "g(F)V"
        e.methods["render(F)V"] = "b(F)V"
        m.classes[e.from] = e
        val out = NilMappingsWriter.write(m)
        assertEquals(
            """
            {
            	"default": {
            		"classes": [
            			{
            				"from": "net/minecraft/client/render/GameRenderer",
            				"to": "ban",
            				"methods": {
            					"render(F)V": "b(F)V",
            					"transformCamera(F)V": "g(F)V"
            				}
            			}
            		]
            	}
            }
            
            """.trimIndent(),
            out
        )
    }

    @Test
    fun innerClassesNestPerLevel() {
        val m = NilMappings()
        m.classes["a/b/C"] = NilClassEntry("a/b/C", "x")
        m.classes["a/b/C\$D"] = NilClassEntry("a/b/C\$D", "x\$y")
        m.classes["a/b/C\$D\$E"] = NilClassEntry("a/b/C\$D\$E", "x\$y\$z")
        val out = NilMappingsWriter.write(m)
        // D nests inside C with simple name, E inside D
        val cIdx = out.indexOf("\"from\": \"a/b/C\"")
        val innerArr = out.indexOf("\"inner-classes\"", cIdx)
        val dIdx = out.indexOf("\"from\": \"D\"", innerArr)
        val dTo = out.indexOf("\"to\": \"y\"", dIdx)
        val eArr = out.indexOf("\"inner-classes\"", dIdx)
        val eIdx = out.indexOf("\"from\": \"E\"", eArr)
        val eTo = out.indexOf("\"to\": \"z\"", eIdx)
        assertTrue(cIdx != -1 && innerArr != -1 && dIdx != -1 && dTo != -1 && eArr != -1 && eIdx != -1 && eTo != -1,
            "nested structure missing:\n$out")
    }

    @Test
    fun widenBlockEmitsInSections() {
        val m = NilMappings()
        m.widenClasses += "xy"
        m.widenMethods.getOrPut("ban") { sortedSetOf() } += "a(Lqx;)V"
        m.widenFields.getOrPut("ban") { sortedSetOf() } += "f:I"
        val out = NilMappingsWriter.write(m)
        assertTrue(out.contains("\"widen\":"), out)
        assertTrue(out.contains("\"classes\": [\"xy\"]"), out)
        assertTrue(out.contains("{ \"owner\": \"ban\", \"sig\": \"a(Lqx;)V\" }"), out)
        assertTrue(out.contains("{ \"owner\": \"ban\", \"sig\": \"f:I\" }"), out)
    }

    @Test
    fun multiTargetIds() {
        val a = NilMappings()
        a.classes["c/A"] = NilClassEntry("c/A", "x")
        val b = NilMappings()
        b.classes["c/A"] = NilClassEntry("c/A", "y")
        val out = NilMappingsWriter.write(mapOf("default" to a, "1.5.2" to b))
        assertTrue(out.indexOf("\"default\":") != -1, out)
        assertTrue(out.indexOf("\"1.5.2\":") != -1, out)
    }

    @Test
    fun escaping() {
        val m = NilMappings()
        m.classes["c/A\"q"] = NilClassEntry("c/A\"q", "x")
        val out = NilMappingsWriter.write(m)
        assertTrue(out.contains("c/A\\\"q"), out)
    }

}
