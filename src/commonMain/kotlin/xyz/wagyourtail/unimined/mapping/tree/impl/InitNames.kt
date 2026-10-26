package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.toNsMap

/**
 * fills `<init>` cells for namespaces the row lacks.
 *
 * Constructor names never change — `<init>` maps to itself in every
 * namespace — but their *descriptors* are namespace-relative, so the new
 * cell's desc is remapped from a donor namespace through the class's
 * descRemapper rather than copied — unmapped class refs inside the desc
 * carry their source name forward, per the usual convention.
 * Constructors with no row at all are not synthesized — existence can't
 * be derived from the tree alone — and rows never attached to a
 * descRemapper (built outside the `clazz` sink) are skipped.
 */
fun MutableMappingTree.fillInitNames() {
    val allNs = namespaces.toList()
    for (cls in classes) {
        for (m in cls.methods) {
            if (m !is MutableMethodMapping) continue
            val entries = m.entries
            if (entries.size == allNs.size) continue
            val donor = entries.entries.firstOrNull { it.value.name.value == "<init>" }
                ?: continue
            val donorDesc = donor.value.descriptor ?: continue
            val remapper = (m as? MethodMappingData)?.descRemapper ?: continue
            val out = entries.toNsMap()
            for (ns in allNs) {
                if (ns in out) continue
                val desc = remapper(donor.key, ns, donorDesc)
                    ?.let { it as? MethodDescriptor } ?: continue
                out[ns] = MethodNameAndDescriptor.unchecked("<init>;${desc.value}")
            }
            if (out.size != entries.size) m.setEntries(out)
        }
    }
}
