package xyz.wagyourtail.unimined.mapping.remapper.nil

import java.util.SortedMap

/**
 * Renders NIL's `META-INF/nil/mappings.json`. The file is a JSON object
 * keyed by mapping-set id (`"default"` unless the mod selects another
 * through `ModRemapper.setTargetMapping`), each holding a [NilMappings].
 *
 * Inner classes nest per `$`-level — lorenz resolves `a/b/C$D` by walking
 * the outer mapping's `inner-classes` for simple segment `D`, so flat
 * entries would never be found. The `from` structure of the entries
 * drives the nesting; each level's `to` is the segment after the last
 * `$` of that class's own mapped name (which is all lorenz keeps — see
 * `InnerClassMapping.setDeobfuscatedName`).
 *
 * Hand-rolled rather than a json library: the artifact is a trivially
 * small subset of JSON and the remapper module carries no json dep.
 */
object NilMappingsWriter {

    fun write(sets: Map<String, NilMappings>): String {
        val sb = StringBuilder()
        sb.append("{\n")
        val ids = sets.entries.toList()
        ids.forEachIndexed { i, (id, mappings) ->
            sb.append(indent(1)).append(quote(id)).append(": ")
            writeMappings(sb, mappings, 1)
            if (i < ids.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("}\n")
        return sb.toString()
    }

    fun write(mappings: NilMappings): String = write(mapOf("default" to mappings))

    private fun writeMappings(sb: StringBuilder, mappings: NilMappings, level: Int) {
        sb.append("{\n")
        if (mappings.classes.isNotEmpty()) {
            sb.append(indent(level + 1)).append("\"classes\": [\n")
            val forest = forest(mappings)
            forest.forEachIndexed { i, node ->
                writeClassNode(sb, node, level + 2)
                if (i < forest.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append(indent(level + 1)).append("]")
        }
        val widen = StringBuilder()
        writeWiden(widen, mappings, level + 1)
        if (widen.isNotEmpty()) {
            if (mappings.classes.isNotEmpty()) sb.append(",\n")
            sb.append(indent(level + 1)).append("\"widen\": {\n")
            sb.append(widen)
            sb.append(indent(level + 1)).append("}")
        }
        sb.append("\n").append(indent(level)).append("}")
    }

    private fun writeWiden(sb: StringBuilder, mappings: NilMappings, level: Int) {
        var firstSection = true
        fun sectionSep() {
            if (!firstSection) sb.append(",\n")
            firstSection = false
        }
        if (mappings.widenClasses.isNotEmpty()) {
            sectionSep()
            sb.append(indent(level + 1)).append("\"classes\": [")
            mappings.widenClasses.forEachIndexed { i, c ->
                if (i > 0) sb.append(", ")
                sb.append(quote(c))
            }
            sb.append("]")
        }
        fun members(map: SortedMap<String, MutableSet<String>>, key: String) {
            if (map.isEmpty()) return
            val entries = map.entries.toList()
            sectionSep()
            sb.append(indent(level + 1)).append(quote(key)).append(": [\n")
            var first = true
            for ((owner, sigs) in entries) {
                for (sig in sigs.sorted()) {
                    if (!first) sb.append(",\n")
                    first = false
                    sb.append(indent(level + 2)).append("{ \"owner\": ").append(quote(owner))
                        .append(", \"sig\": ").append(quote(sig)).append(" }")
                }
            }
            sb.append("\n").append(indent(level + 1)).append("]")
        }
        members(mappings.widenMethods, "methods")
        members(mappings.widenFields, "fields")
        if (!firstSection) sb.append("\n")
    }

    // --- inner-class nesting ---

    /**
     * Split `net/minecraft/C$D$E` into `["net/minecraft/C", "D", "E"]` —
     * the top-level binary name plus one simple segment per nesting
     * level. `$` inside the package portion is not inner-class syntax.
     */
    internal fun splitInnerPath(name: String): List<String> {
        val slash = name.lastIndexOf('/')
        val pkg = if (slash == -1) "" else name.substring(0, slash + 1)
        val simple = name.substring(slash + 1)
        val parts = simple.split('$')
        return listOf(pkg + parts[0]) + parts.drop(1)
    }

    private class ClassNode(
        val entry: NilClassEntry,
        val simpleFrom: String,
        val simpleTo: String,
    ) {
        val children = sortedMapOf<String, ClassNode>()
    }

    /** groups flat entries into a nesting forest keyed on the from-side
     *  `$`-structure. Entries iterate in sorted order, so parents are
     *  always processed before their children; the scanner synthesizes
     *  ancestor entries, so genuinely missing parents shouldn't occur —
     *  if one does the child degrades to a top-level entry rather than
     *  being dropped */
    private fun forest(mappings: NilMappings): List<ClassNode> {
        val roots = sortedMapOf<String, ClassNode>()
        val nodesByPath = HashMap<String, ClassNode>()
        for (entry in mappings.classes.values) {
            val segments = splitInnerPath(entry.from)
            var path = segments[0]
            var node = nodesByPath[path] ?: run {
                // top level carries its full binary target name; a missing
                // ancestor (the scanner synthesizes them, so this shouldn't
                // occur) degrades to an identity placeholder rather than
                // stealing the child's entry
                val leaf = segments.size == 1
                val created = ClassNode(
                    if (leaf) entry else NilClassEntry(segments[0], segments[0]),
                    segments[0],
                    if (leaf) entry.to.split('$')[0] else segments[0],
                )
                nodesByPath[path] = created
                roots[segments[0]] = created
                created
            }
            for (i in 1 until segments.size) {
                path = "$path\$${segments[i]}"
                val parent = node
                val existing = nodesByPath[path]
                val child = if (existing != null) {
                    existing
                } else {
                    val leaf = path == entry.from
                    ClassNode(
                        if (leaf) entry else NilClassEntry(path, path),
                        segments[i],
                        if (leaf) entry.to.substringAfterLast('$') else segments[i],
                    ).also {
                        nodesByPath[path] = it
                        parent.children[segments[i]] = it
                    }
                }
                node = child
            }
        }
        return roots.values.toList()
    }

    private fun writeClassNode(sb: StringBuilder, node: ClassNode, level: Int) {
        sb.append(indent(level)).append("{\n")
        sb.append(indent(level + 1)).append("\"from\": ").append(quote(node.simpleFrom)).append(",\n")
        sb.append(indent(level + 1)).append("\"to\": ").append(quote(node.simpleTo))
        if (node.entry.methods.isNotEmpty()) {
            sb.append(",\n")
            writeMembers(sb, node.entry.methods, "methods", level + 1)
        }
        if (node.entry.fields.isNotEmpty()) {
            sb.append(",\n")
            writeMembers(sb, node.entry.fields, "fields", level + 1)
        }
        if (node.children.isNotEmpty()) {
            sb.append(",\n")
            sb.append(indent(level + 1)).append("\"inner-classes\": [\n")
            val children = node.children.values.toList()
            children.forEachIndexed { i, child ->
                writeClassNode(sb, child, level + 2)
                if (i < children.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append(indent(level + 1)).append("]")
        }
        sb.append("\n").append(indent(level)).append("}")
    }

    private fun writeMembers(sb: StringBuilder, members: SortedMap<String, String>, key: String, level: Int) {
        sb.append(indent(level)).append(quote(key)).append(": {\n")
        val entries = members.entries.toList()
        entries.forEachIndexed { i, (k, v) ->
            sb.append(indent(level + 1)).append(quote(k)).append(": ").append(quote(v))
            if (i < entries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append(indent(level)).append("}")
    }

    // --- json plumbing ---

    private fun indent(level: Int) = "\t".repeat(level)

    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

}
