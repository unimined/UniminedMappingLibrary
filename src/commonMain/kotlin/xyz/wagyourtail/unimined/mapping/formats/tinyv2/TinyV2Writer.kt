package xyz.wagyourtail.unimined.mapping.formats.tinyv2

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
import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.visitor.*

object TinyV2Writer : FormatWriter {
    override fun write(envType: EnvType, into: BufferedSink): MappingVisitor {
        return TinyV2MappingWriter(into)
    }

    open class BaseTinyV2Writer<T: BaseVisitor<T>>(val into: BufferedSink, val parent: BaseTinyV2Writer<*>?, val indent: String = ""): BaseVisitor<T> {

        val root: TinyV2MappingWriter get() = (this as? TinyV2MappingWriter) ?: parent!!.root


        fun BufferedSink.writeNamespaced(names: Map<Namespace, String>) {
            root.namespaces.withIndex().forEach { (i, ns) ->
                write((names[ns]?.escape() ?: "").encodeUtf8())
                if (i != root.namespaces.lastIndex) {
                    write("\t".encodeUtf8())
                }
            }
        }

        override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
            return null
        }

    }

    open class TinyV2MemberWriter<T: MemberVisitor<T>>(into: BufferedSink, parent: BaseTinyV2Writer<*>?, indent: String = ""): BaseTinyV2Writer<T>(into, parent, indent), MemberVisitor<T> {
        override fun visitComment(values: Map<Namespace, String>): CommentVisitor? {
            // only 1 comment is allowed, so we will concat them all with \n\n after uniquifying them
            if (values.isEmpty()) return null
            into.write(indent.encodeUtf8())
            into.write("c\t".encodeUtf8())
            into.write(values.values.toSet().joinToString("\n\n").escape().encodeUtf8())
            into.write("\n".encodeUtf8())
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

    class TinyV2MappingWriter(into: BufferedSink) : BaseTinyV2Writer<MappingVisitor>(into, null), MappingVisitor {
        lateinit var namespaces: List<Namespace>

        override fun nextUnnamedNs(): Namespace {
            val ns = Namespace("unnamed_${namespaces.size}")
            namespaces += ns
            return ns
        }

        override fun visitHeader(vararg namespaces: String) {
            into.write("tiny\t2\t0\t".encodeUtf8())
            this.namespaces = namespaces.map { Namespace(it) }
            into.write(this.namespaces.joinToString("\t") { it.name }.encodeUtf8())
            into.write("\n".encodeUtf8())
        }

        override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
            return null
        }

        override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
            into.write("c\t".encodeUtf8())
            into.writeNamespaced(names.mapValues { it.value.toString() })
            into.write("\n".encodeUtf8())
            return TinyV2ClassWriter(into, this)
        }

        override fun visitConstantGroup(
            type: ConstantGroupNode.InlineType,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ConstantGroupVisitor? {
            return null
        }

    }

    class TinyV2ClassWriter(into: BufferedSink, parent: BaseTinyV2Writer<*>?): TinyV2MemberWriter<ClassVisitor>(into, parent, "\t"), ClassVisitor {
        override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
            // get desc in first namespace
            val desc = namespaces[root.namespaces.first()]!!.second!!
            into.write(indent.encodeUtf8())
            into.write("m\t".encodeUtf8())
            into.write(desc.toString().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.writeNamespaced(namespaces.mapValues { it.value.first })
            into.write("\n".encodeUtf8())
            return TinyV2MethodWriter(into, this)
        }

        override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
            // get desc in first namespace
            val desc = namespaces[root.namespaces.first()]!!.second!!
            into.write(indent.encodeUtf8())
            into.write("f\t".encodeUtf8())
            into.write(desc.toString().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.writeNamespaced(namespaces.mapValues { it.value.first })
            into.write("\n".encodeUtf8())
            return TinyV2FieldWriter(into, this, indent + "\t")
        }

        override fun visitInnerClass(
            type: InnerClassNode.InnerType,
            names: Map<Namespace, Pair<String, FullyQualifiedName?>>
        ): InnerClassVisitor? {
            return null
        }

    }

    class TinyV2MethodWriter(into: BufferedSink, parent: BaseTinyV2Writer<*>?): TinyV2MemberWriter<MethodVisitor>(into, parent, "\t\t"), MethodVisitor {
        override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
            if (lvOrd == null) return null
            into.write(indent.encodeUtf8())
            into.write("p\t".encodeUtf8())
            into.write(lvOrd.toString().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.writeNamespaced(names)
            into.write("\n".encodeUtf8())
            return object : TinyV2MemberWriter<ParameterVisitor>(into, this, indent + "\t"), ParameterVisitor {}
        }

        override fun visitLocalVariable(
            lvOrd: Int,
            startOp: Int?,
            names: Map<Namespace, String>
        ): LocalVariableVisitor? {
            into.write(indent.encodeUtf8())
            into.write("v\t".encodeUtf8())
            into.write(lvOrd.toString().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.write(startOp?.toString()?.encodeUtf8() ?: "".encodeUtf8())
            into.write("\t\t".encodeUtf8()) // skip lvt-idx
            into.writeNamespaced(names)
            into.write("\n".encodeUtf8())
            return object : TinyV2MemberWriter<LocalVariableVisitor>(into, this, indent + "\t"), LocalVariableVisitor {}
        }

        override fun visitException(
            type: ExceptionType,
            exception: InternalName,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ExceptionVisitor? {
            return null
        }

    }

    class TinyV2FieldWriter(into: BufferedSink, parent: BaseTinyV2Writer<*>?, indent: String = ""): TinyV2MemberWriter<FieldVisitor>(into, parent, indent), FieldVisitor

}