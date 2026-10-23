package xyz.wagyourtail.unimined.mapping.cli

import java.nio.file.Path
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.inheritance.ASMInheritanceTree
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree
import xyz.wagyourtail.unimined.mapping.resolver.MappingResolver

/**
 * Concrete [MappingResolver] for the CLI — the JVM half of the seam:
 * commonMain's resolver can't scan bytecode, so the inheritance graph comes
 * from an [ASMInheritanceTree] over [scanJars] (propagation jars + classpath).
 * [resolve] then wraps the loaded tree in an `InheritanceBackedMappingTree`
 * so member names propagate to scanned-but-unmapped descendants on load.
 */
class CliMappingResolver(
    val propagationNs: Namespace,
    val scanJars: MutableSet<Path>,
    unmapped: Set<Namespace>,
) : MappingResolver<CliMappingResolver>("cli") {

    override val unmappedNs = unmapped

    override fun createForPostProcess(key: String, process: MutableMappingTree.() -> Unit): CliMappingResolver {
        // postProcessDependency isn't wired by the CLI today — clone the
        // config anyway so the hook produces a consistent resolver if used
        return CliMappingResolver(propagationNs, scanJars, unmappedNs)
    }

    override suspend fun applyInheritanceTree(
        tree: MutableMappingTree,
        apply: suspend (InheritanceTree) -> MutableMappingTree,
    ): MutableMappingTree {
        if (scanJars.isEmpty()) return tree
        return apply(ASMInheritanceTree(propagationNs, scanJars))
    }

}
