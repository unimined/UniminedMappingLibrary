package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSink
import okio.ByteString.Companion.encodeUtf8
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree
import xyz.wagyourtail.unimined.mapping.tree.node.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node.InnerClassNode
import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.visitor.*

object UMFWriter : FormatWriter {

    fun String?.maybeEscape(): String {
        if (this == null) return "_"
        if (any { it.isWhitespace() } || startsWith("\"")) {
            return "\"${escape(unicode = true)}\""
        }
        if (all { it == '_' })  {
            return "${this}_"
        }
        return this
    }

    override fun write(into: BufferedSink): MappingVisitor {
        into.write("umf\t1\t0\n".encodeUtf8()) //TODO: extensions
        return UMFMappingWriter(into)
    }

    open class BaseUMFWriter<T: BaseVisitor<T>>(val into: BufferedSink, val parent: BaseUMFWriter<*>?, val indent: String = "") : BaseVisitor<T> {

        val root: UMFMappingWriter get() = (this as? UMFMappingWriter) ?: parent!!.root


        fun BufferedSink.writeNamespaced(names: Map<Namespace, String>) {
            root.namespaces.withIndex().forEach { (i, ns) ->
                write((names[ns]?.maybeEscape() ?: "_").encodeUtf8())
                if (i != root.namespaces.lastIndex) {
                    write("\t".encodeUtf8())
                }
            }
        }

        override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
            TODO()
        }

    }

    open class UMFMemberWriter<T: MemberVisitor<T>>(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<T>(into, parent, indent), MemberVisitor<T> {
        override fun visitComment(values: Map<Namespace, String>): CommentVisitor? {
            into.write("${indent}*\t".encodeUtf8())
            into.writeNamespaced(values.mapValues {
                val num = it.value.toIntOrNull()
                if (num != null) {
                    "_${num}"
                } else {
                    for (ns in root.namespaces.subList(0, root.namespaces.indexOf(it.key))) {
                        if (it.value == values[ns]) {
                            return@mapValues root.namespaces.indexOf(ns).toString()
                        }
                    }
                    it.value
                }
            })
            into.write("\n".encodeUtf8())
            return UMFCommentWriter(into, this, indent + "\t")
        }

        override fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
            into.write("${indent}g\t".encodeUtf8())
            into.writeNamespaced(values)
            into.write("\n".encodeUtf8())
            return UMFSignatureWriter(into, this, indent + "\t")
        }

        override fun visitAccess(type: AccessType, value: AccessFlag, namespaces: Set<Namespace>): AccessVisitor? {
            into.write("${indent}${type.name.lowercase()}\t${value.name.lowercase()}\t".encodeUtf8())
            into.write(namespaces.joinToString("\t") { it.name.maybeEscape() }.encodeUtf8())
            into.write("\n".encodeUtf8())
            return UMFAccessWriter(into, this, indent + "\t")
        }

        override fun visitAnnotation(
            type: AnnotationType,
            baseNs: Namespace,
            annotation: Annotation,
            namespaces: Set<Namespace>
        ): AnnotationVisitor? {
            into.write("${indent}@\t".encodeUtf8())
            when (type) {
                AnnotationType.ADD -> into.write("+\t".encodeUtf8())
                AnnotationType.REMOVE -> into.write("-\t".encodeUtf8())
                AnnotationType.MODIFY -> into.write("m\t".encodeUtf8())
            }
            val parts = annotation.getParts()
            into.write(parts.first.value.maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.write(parts.second?.value.maybeEscape().encodeUtf8())
            if (parts.third != null) {
                into.write(parts.third!!.value.maybeEscape().encodeUtf8())
            }
            into.write("\t".encodeUtf8())
            into.write(baseNs.name.maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            for (ns in root.namespaces) {
                if (ns in namespaces) {
                    into.write(ns.name.maybeEscape().encodeUtf8())
                }
            }
            into.write("\n".encodeUtf8())
            return UMFAnnotationWriter(into, this, indent + "\t")
        }
    }

    class UMFMappingWriter(into: BufferedSink) : BaseUMFWriter<MappingVisitor>(into, null), MappingVisitor {
        lateinit var namespaces: List<Namespace>

        override fun nextUnnamedNs(): Namespace {
            val ns = Namespace("unnamed_${namespaces.size}")
            namespaces += ns
            return ns
        }

        override fun visitHeader(vararg namespaces: String) {
            this.namespaces = namespaces.map { Namespace(it) }
            into.write("${namespaces.joinToString("\t") { it.maybeEscape() }}\n".encodeUtf8())
        }

        override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
            into.write("c\t".encodeUtf8())
            into.writeNamespaced(names.mapValues { it.value.value })
            into.write("\n".encodeUtf8())
            return UMFClassWriter(into, this)
        }

        override fun visitConstantGroup(
            type: ConstantGroupNode.InlineType,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ConstantGroupVisitor? {
            into.write("u\t${type.name.lowercase()}\t${baseNs.name.maybeEscape()}".encodeUtf8())
            for (ns in root.namespaces) {
                if (ns in namespaces) {
                    into.write("\t${ns.name.maybeEscape()}".encodeUtf8())
                }
            }
            into.write("\n".encodeUtf8())
            return UMFConstantGroupWriter(into, this)
        }

    }

    class UMFClassWriter(into: BufferedSink, parent: BaseUMFWriter<*>) : UMFMemberWriter<ClassVisitor>(into, parent, "\t"), ClassVisitor {
        lateinit var names: Map<Namespace, InternalName>

        override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
            into.write(indent.encodeUtf8())
            into.write("m\t".encodeUtf8())
            into.writeNamespaced(namespaces.mapValues { v -> v.value.second?.let { "${v.value.first};${v.value.second!!.value}" } ?: v.value.first })
            into.write("\n".encodeUtf8())
            return UMFMethodWriter(into, this, indent + "\t")
        }

        override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
            into.write(indent.encodeUtf8())
            into.write("f\t".encodeUtf8())
            into.writeNamespaced(namespaces.mapValues { v -> v.value.second?.let { "${v.value.first};${v.value.second!!.value}" } ?: v.value.first })
            into.write("\n".encodeUtf8())
            return UMFFieldWriter(into, this, indent + "\t")
        }

        override fun visitInnerClass(
            type: InnerClassNode.InnerType,
            names: Map<Namespace, Pair<String, FullyQualifiedName?>>
        ): InnerClassVisitor? {
            val typeStr = when (type) {
                InnerClassNode.InnerType.INNER -> "i"
                InnerClassNode.InnerType.LOCAL -> "l"
                InnerClassNode.InnerType.ANONYMOUS -> "a"
            }
            into.write(indent.encodeUtf8())
            into.write("i\t${typeStr}".encodeUtf8())
            into.writeNamespaced(names.mapValues { v -> v.value.second?.let { "${v.value.first};${v.value.second!!.value}" } ?: v.value.first })
            into.write("\n".encodeUtf8())
            return UMFInnerClassWriter(into, this, indent + "\t")
        }

    }

    class UMFCommentWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<CommentVisitor>(into, parent, indent), CommentVisitor

    class UMFSignatureWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<SignatureVisitor>(into, parent, indent), SignatureVisitor

    class UMFAccessWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<AccessVisitor>(into, parent, indent), AccessVisitor

    class UMFAnnotationWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<AnnotationVisitor>(into, parent, indent), AnnotationVisitor

    class UMFConstantGroupWriter(into: BufferedSink, parent: BaseUMFWriter<*>) : BaseUMFWriter<ConstantGroupVisitor>(into, parent, "\t"), ConstantGroupVisitor {
        override fun visitConstant(
            fieldClass: InternalName,
            fieldName: UnqualifiedName,
            fieldDesc: FieldDescriptor?
        ): ConstantVisitor? {
            into.write(indent.encodeUtf8())
            into.write("n\t".encodeUtf8())
            into.write(fieldClass.value.maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.write(fieldName.value.maybeEscape().encodeUtf8())
            if (fieldDesc != null) {
                into.write(";".encodeUtf8())
                into.write(fieldDesc.value.value.maybeEscape().encodeUtf8())
            }
            into.write("\n".encodeUtf8())
            return UMFConstantWriter(into, this, indent + "\t")
        }

        override fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
            into.write(indent.encodeUtf8())
            into.write("t\t".encodeUtf8())
            into.write(target.value.maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.write(paramIdx?.toString().maybeEscape().encodeUtf8())
            into.write("\n".encodeUtf8())
            return UMFTargetWriter(into, this, indent + "\t")
        }

    }

    class UMFMethodWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : UMFMemberWriter<MethodVisitor>(into, parent, indent), MethodVisitor {
        override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
            into.write(indent.encodeUtf8())
            into.write("p\t".encodeUtf8())
            into.write(index?.toString().maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.write(lvOrd?.toString().maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.writeNamespaced(names)
            into.write("\n".encodeUtf8())
            return UMFParameterWriter(into, this, indent + "\t")
        }

        override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
            into.write(indent.encodeUtf8())
            into.write("v\t".encodeUtf8())
            into.write(lvOrd.toString().maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.write(startOp?.toString().maybeEscape().encodeUtf8())
            into.write("\t".encodeUtf8())
            into.writeNamespaced(names)
            into.write("\n".encodeUtf8())
            return UMFLocalVariableWriter(into, this, indent + "\t")
        }

    }

    class UMFFieldWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : UMFMemberWriter<FieldVisitor>(into, parent, indent), FieldVisitor

    class UMFInnerClassWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<InnerClassVisitor>(into, parent, indent), InnerClassVisitor

    class UMFConstantWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<ConstantVisitor>(into, parent, indent), ConstantVisitor

    class UMFTargetWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : BaseUMFWriter<TargetVisitor>(into, parent, indent), TargetVisitor

    class UMFParameterWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : UMFMemberWriter<ParameterVisitor>(into, parent, indent), ParameterVisitor

    class UMFLocalVariableWriter(into: BufferedSink, parent: BaseUMFWriter<*>, indent: String = "") : UMFMemberWriter<LocalVariableVisitor>(into, parent, indent), LocalVariableVisitor


}