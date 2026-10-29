package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor

/**
 * Grafts [target] onto this tree through a shared namespace — the
 * `TinyMappingsJoiner` equivalent used by `migrateMappings`.
 *
 * Each of this tree's class/member rows is looked up in [target] by its
 * [via]-namespace name; the match's [toNs] name is grafted on as a new
 * column. The result is a `{fromNs -> toNs}` tree — a *direct* one-hop
 * mapping — so remappers can translate [fromNs] sources to [toNs] in a
 * single pass while resolving against [fromNs]-named classpath entries.
 *
 * Example (yarn update): `yarnOld.graft(yarnNew, intermediary, named,
 * named)` joins rows on the shared intermediary id — classes that
 * persist across versions share ids, so the graft follows them.
 *
 * Rows with no [via]-ns match on the target side keep their [fromNs]
 * name (identity fallback) — partial joins degrade to "leave it alone"
 * rather than dropping rows. Packages, wildcards and class metadata are
 * grafted/copied so downstream consumers (wildcard member inference,
 * package moves) keep working. Member children (params/lvars) and
 * constant groups are not grafted yet.
 *
 * @param via namespace both trees share — the join key (e.g.
 *   `intermediary`, or `official` for yarn->mojmap)
 * @param fromNs the source column to carry from this tree (the old dev ns)
 * @param toNs the target column to graft on (the new dev ns)
 */
fun MappingTree.graft(
    target: MappingTree,
    via: Namespace,
    fromNs: Namespace,
    toNs: Namespace
): MutableMappingTree {
    checkNamespace(fromNs)
    target.checkNamespace(toNs)
    val out = MutableMappingTree(listOf(fromNs, toNs))

    for (pkg in packages) {
        val fromName = pkg.names[fromNs] ?: continue
        val viaName = pkg.names[via] ?: map(fromNs, via, fromName)
        out.package_(
            mapOf(
                fromNs to fromName,
                toNs to target.map(via, toNs, viaName)
            )
        )
    }

    for (cls in classes) {
        val fromName = cls.names[fromNs] ?: continue
        val viaName = cls.names[via] ?: map(fromNs, via, fromName)
        val targetCls = target.getClass(via, viaName)
        val toName = targetCls?.names?.get(toNs) ?: fromName

        val outCls = out.clazz(mapOf(fromNs to fromName, toNs to toName))
        for (m in cls.metadata) outCls.addMetadata(m)

        for (method in cls.methods) {
            val fromEntry = method.entries[fromNs] ?: method.entries.values.firstOrNull()
                ?: continue
            val viaEntry = method.entries[via] ?: fromEntry
            val toEntry = targetCls?.let { target.methodAt(it, via, toNs, viaEntry) } ?: fromEntry
            outCls.method(mapOf(fromNs to fromEntry, toNs to toEntry))
        }
        for (field in cls.fields) {
            val fromEntry = field.entries[fromNs] ?: field.entries.values.firstOrNull()
                ?: continue
            val viaEntry = field.entries[via] ?: fromEntry
            val toEntry = targetCls?.let { target.fieldAt(it, via, toNs, viaEntry) } ?: fromEntry
            outCls.field(mapOf(fromNs to fromEntry, toNs to toEntry))
        }
        for (wc in cls.wildcards) {
            val fromDesc = wc.descs[fromNs] ?: continue
            val viaDesc = wc.descs[via] ?: map(fromNs, via, fromDesc)
            val toDesc = target.map(via, toNs, viaDesc)
            outCls.wildcard(wc.type, mapOf(fromNs to fromDesc, toNs to toDesc))
        }
    }

    return out
}

/** member lookup on the target side by [via]-ns name + optional desc,
 *  returning the member's [toNs] entry */
private fun MappingTree.methodAt(
    cls: ClassMapping, via: Namespace, toNs: Namespace,
    viaEntry: MethodNameAndDescriptor
): MethodNameAndDescriptor? {
    val matches = cls.getMethods(via, viaEntry.name, viaEntry.descriptor)
    return matches.firstOrNull()?.entries?.get(toNs)
        ?: matches.firstOrNull()?.entries?.values?.firstOrNull()
}

private fun MappingTree.fieldAt(
    cls: ClassMapping, via: Namespace, toNs: Namespace,
    viaEntry: FieldNameAndDescriptor
): FieldNameAndDescriptor? {
    val match = cls.getField(via, viaEntry.name, viaEntry.descriptor)
    return match?.entries?.get(toNs) ?: match?.entries?.values?.firstOrNull()
}
