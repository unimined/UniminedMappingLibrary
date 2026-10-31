package xyz.wagyourtail.unimined.mapping.remapper.asm

import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.source.psi.Jsr308Extension

/**
 * Bytecode counterpart to [Jsr308Extension] — rewrites JSR-308 / JSR-305
 * annotation descriptors on classes, fields, methods, record components
 * and all TYPE_USE targets to `org.jetbrains.annotations` equivalents.
 * Pure per-class transform ([perClass] — safe on the streaming path).
 *
 * [table] keys/values are Java FQNs — converted to `Linternal/name;`
 * descriptors internally.
 */
class Jsr308AsmExtension(
    table: Map<String, String> = Jsr308Extension.DEFAULT_TABLE
) : AsmExtension {

    private val descTable = table.mapKeys { "L${it.key.replace('.', '/')};" }
        .mapValues { "L${it.value.replace('.', '/')};" }

    private fun map(nodes: List<AnnotationNode>?) {
        nodes?.forEach { it.desc = descTable[it.desc] ?: it.desc }
    }

    override fun postApply(input: ClassNode, output: ClassNode, cls: ClassMapping?) {
        map(output.visibleAnnotations)
        map(output.invisibleAnnotations)
        map(output.visibleTypeAnnotations)
        map(output.invisibleTypeAnnotations)
        for (f in output.fields) {
            map(f.visibleAnnotations)
            map(f.invisibleAnnotations)
            map(f.visibleTypeAnnotations)
            map(f.invisibleTypeAnnotations)
        }
        for (m in output.methods) {
            map(m.visibleAnnotations)
            map(m.invisibleAnnotations)
            map(m.visibleTypeAnnotations)
            map(m.invisibleTypeAnnotations)
            m.visibleParameterAnnotations?.forEach(::map)
            m.invisibleParameterAnnotations?.forEach(::map)
            (m.annotationDefault as? AnnotationNode)?.let {
                it.desc = descTable[it.desc] ?: it.desc
            }
        }
        output.recordComponents?.forEach { r ->
            map(r.visibleAnnotations)
            map(r.invisibleAnnotations)
            map(r.visibleTypeAnnotations)
            map(r.invisibleTypeAnnotations)
        }
    }

    companion object {

        /** factory — per-jar instance, transform-only */
        fun factory(
            table: Map<String, String> = Jsr308Extension.DEFAULT_TABLE
        ) = object : AsmExtensionFactory {
            override val perClass = true
            override fun create(ctx: ExtensionContext) = Jsr308AsmExtension(table)
        }

    }

}
