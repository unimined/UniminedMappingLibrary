package xyz.wagyourtail.unimined.mapping.formats.srg

import okio.BufferedSink
import okio.ByteString.Companion.encodeUtf8
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node.InnerClassNode
import xyz.wagyourtail.unimined.mapping.visitor.*

object SrgWriter : FormatWriter {
    override fun write(envType: EnvType, into: BufferedSink): MappingVisitor {
        return SrgMappingWriter(into)
    }

    open class BaseSrgWriter<T: BaseVisitor<T>>(val into: BufferedSink, val parent: BaseSrgWriter<*>?): BaseVisitor<T> {

        val root: SrgMappingWriter get() = (this as? SrgMappingWriter) ?: parent!!.root

        fun Map<Namespace, String>.fillNull(): Map<Namespace, String>? {
            var srcName = this[root.namespaces[0]]
            var dstName = this[root.namespaces[1]]
            if (srcName == null) {
                srcName = dstName
            }
            if (dstName == null) {
                dstName = srcName
            }
            if (srcName == null) return null
            return mapOf(root.namespaces[0] to srcName, root.namespaces[1] to dstName!!)
        }

        fun BufferedSink.writeEntry(prefix: String, names: Map<Namespace, String>) {
            val fixed = names.fillNull()
            if (fixed != null) {
                write(prefix.encodeUtf8())
                write(" ".encodeUtf8())
                write(fixed[root.namespaces[0]]!!.encodeUtf8())
                write(" ".encodeUtf8())
                write(fixed[root.namespaces[1]]!!.encodeUtf8())
                write("\n".encodeUtf8())
            }
        }

        override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
            return null
        }

    }

    class SrgMappingWriter(into: BufferedSink): BaseSrgWriter<MappingVisitor>(into, null), MappingVisitor {
        lateinit var namespaces: List<Namespace>

        override fun nextUnnamedNs(): Namespace {
            throw IllegalStateException()
        }

        override fun visitHeader(vararg namespaces: String) {
            if (namespaces.size != 2) {
                throw IllegalArgumentException("Srg requires 2 namespaces")
            }
            this.namespaces = namespaces.map { Namespace(it) }
        }

        override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
            into.writeEntry("PK:", names.mapValues { it.value.value.substringBeforeLast('/').ifEmpty { "." } })
            return null
        }

        override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
            into.writeEntry("CL:", names.mapValues { it.value.value })
            val fixedNames = names.mapValues { it.value.value }.fillNull()
            if (fixedNames == null) return null
            return SrgClassWriter(into, this, fixedNames)
        }

        override fun visitConstantGroup(
            type: ConstantGroupNode.InlineType,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ConstantGroupVisitor? {
            return null
        }

    }

    class SrgClassWriter(into: BufferedSink, parent: BaseSrgWriter<*>?, val names: Map<Namespace, String>): BaseSrgWriter<ClassVisitor>(into, parent), ClassVisitor {
        override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
            into.writeEntry("MD:", namespaces.mapValues { "${names[it.key]}/${it.value.first} ${it.value.second?.value ?: ""}" })
            return null
        }

        override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
            into.writeEntry("FD:", namespaces.mapValues { "${names[it.key]}/${it.value.first}" })
            return null
        }

        override fun visitInnerClass(
            type: InnerClassNode.InnerType,
            names: Map<Namespace, Pair<String, FullyQualifiedName?>>
        ): InnerClassVisitor? {
            return null
        }

        override fun visitComment(values: Map<Namespace, String>): CommentVisitor? {
            return null
        }

        override fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
            return null
        }

        override fun visitAccess(type: AccessType, value: AccessFlag, namespaces: Set<Namespace>): AccessVisitor? {
            return null
        }

        override fun visitAnnotation(
            type: AnnotationType,
            baseNs: Namespace,
            annotation: Annotation,
            namespaces: Set<Namespace>
        ): AnnotationVisitor? {
            return null
        }

    }
}