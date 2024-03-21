package xyz.wagyourtail.unimined.mapping.formats.nests

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

/**
 * Ornithe's mapping format for adding inner class information to classes.
 *
 */
object NestReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return fileName.endsWith(".nest")
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val ns = Namespace(nsMapping["source"] ?: "source")
        into.visitHeader(ns.name)

        while (!input.exhausted()) {
            input.takeWhitespace()
            val line = input.takeNextLiteral() ?: continue
            val className = InternalName.read(line)
            val outerClassName = InternalName.read(input.takeNextLiteral()!!)
            val outerMethodName = input.takeNextLiteral()!!.let { if (it.isBlank()) null else UnqualifiedName.read(it) }
            val outerMethodDesc = input.takeNextLiteral()!!.let { if (it.isBlank()) null else MethodDescriptor.read(it) }
            val innerName = UnqualifiedName.read(input.takeNextLiteral()!!)
            val access = input.takeNextLiteral()!!.toInt()


            val type = if (innerName.value.toIntOrNull() != null) {
                    InnerClassNode.InnerType.ANONYMOUS
                } else if (outerMethodName == null) {
                    InnerClassNode.InnerType.INNER
                } else {
                    InnerClassNode.InnerType.LOCAL
                }
            val c = into.visitClass(mapOf(ns to className)) ?: continue

            val fqn = FullyQualifiedName(ObjectType(outerClassName), if (outerMethodName == null) null else NameAndDescriptor(outerMethodName, FieldOrMethodDescriptor(outerMethodDesc!!)))

            val i = c.visitInnerClass(
                type,
                mapOf(ns to (innerName.value to fqn))
            ) ?: continue

            for (acc in AccessFlag.of(ElementType.INNER_CLASS, access)) {
                if (acc.elements.contains(ElementType.CLASS)) continue
                i.visitAccess(AccessType.ADD, acc, setOf(ns))
            }
        }

    }

}