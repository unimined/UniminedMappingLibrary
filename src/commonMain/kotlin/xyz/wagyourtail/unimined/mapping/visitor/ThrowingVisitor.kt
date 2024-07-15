package xyz.wagyourtail.unimined.mapping.visitor

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode

open class ThrowingVisitor : MappingVisitor {
    override fun nextUnnamedNs(): Namespace {
        throw UnsupportedOperationException()
    }

    override fun visitHeader(vararg namespaces: String) {
        throw UnsupportedOperationException()
    }

    override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
        throw UnsupportedOperationException()
    }

    override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
        throw UnsupportedOperationException()
    }

    override fun visitConstantGroup(
        type: ConstantGroupNode.InlineType,
        name: String?,
        baseNs: Namespace,
        namespaces: Set<Namespace>
    ): ConstantGroupVisitor? {
        throw UnsupportedOperationException()
    }

    override fun visitEnd() {
        throw UnsupportedOperationException()
    }
}