package xyz.wagyourtail.unimined.mapping.formats.umf

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.visitor.*

object UMFWriter : FormatWriter {

    val EMPTY ="umf\t1\t0\n"

    fun String?.maybeEscape(): String {
        if (this == null) return "_"
        if (this.isEmpty()) return "\"\""
        if (any { it.isWhitespace() } || startsWith("\"")) {
            return "\"${escape(unicode = true)}\""
        }
        if (isNotEmpty() && all { it == '_' })  {
            return "${this}_"
        }
        return this
    }

    override fun write(envType: EnvType, into: (String) -> Unit): MappingVisitor {
        into("umf\t1\t0\n") //TODO: extensions
        return UMFMappingWriter(into)
    }

    abstract class BaseUMFWriter<T: BaseVisitor<T>>(val into: (String) -> Unit, val indent: String = "") : BaseVisitor<T> {

        abstract val namespaces: List<Namespace>

        fun ((String) -> Unit).writeNamespaced(names: Map<Namespace, String>) {
            namespaces.withIndex().forEach { (i, ns) ->
                this((names[ns]?.maybeEscape() ?: "_"))
                if (i != namespaces.lastIndex) {
                    this("\t")
                }
            }
        }

        override fun <V> visitExtension(key: String, vararg values: V): ExtensionVisitor<*, V>? {
            TODO()
        }

        fun visitAnnotation(
            type: AnnotationType,
            baseNs: Namespace,
            annotation: Annotation,
            namespaces: Set<Namespace>
        ): AnnotationVisitor? {
            into("${indent}@\t")
            when (type) {
                AnnotationType.ADD -> into("+\t")
                AnnotationType.REMOVE -> into("-\t")
                AnnotationType.MODIFY -> into("m\t")
            }
            val parts = annotation.getParts()
            into(parts.first.value.maybeEscape())
            into("\t")
            into(parts.second?.value.maybeEscape())
            if (parts.third != null) {
                into(parts.third!!.value.maybeEscape())
            }
            into("\t")
            into(baseNs.name.maybeEscape())
            into("\t")
            for (ns in namespaces) {
                if (ns in namespaces) {
                    into(ns.name.maybeEscape())
                }
            }
            into("\n")
            return UMFAnnotationWriter(into, indent + "\t", this.namespaces)
        }

    }

    open class UMFAccessParentWriter<T: AccessParentVisitor<T>>(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<T>(into, indent), AccessParentVisitor<T> {

        override fun visitAccess(
            type: AccessType,
            value: AccessFlag,
            condition: AccessConditions,
            namespaces: Set<Namespace>
        ): AccessVisitor? {
            into("${indent}a\t")
            when (type) {
                AccessType.ADD -> into("+\t")
                AccessType.REMOVE -> into("-\t")
            }
            into("${value.name.lowercase()}\t")
            into("$condition\t")
            into(namespaces.joinToString("\t") { it.name.maybeEscape() })
            into("\n")
            return UMFAccessWriter(into, indent + "\t", this.namespaces)
        }

    }

    open class UMFMemberWriter<T: MemberVisitor<T>>(into: (String) -> Unit, indent: String = "", namespaces: List<Namespace>) : UMFAccessParentWriter<T>(into, indent, namespaces), MemberVisitor<T> {
        override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
            into("${indent}${UMFReader.EntryType.JAVADOC.key}\t")
            into.writeNamespaced(values.mapValues {
                val num = it.value.toIntOrNull()
                if (num != null) {
                    "_${num}"
                } else {
                    val i = namespaces.indexOf(it.key)
                    if (i != -1) {
                        for (ns in namespaces.subList(0, i)) {
                            if (it.value == values[ns]) {
                                return@mapValues namespaces.indexOf(ns).toString()
                            }
                        }
                    } else {
                        throw IllegalArgumentException("Namespace not found ${it.key}")
                    }
                    it.value
                }
            })
            into("\n")
            return UMFCommentWriter(into, indent + "\t", namespaces)
        }

        fun visitSignature(values: Map<Namespace, String>): SignatureVisitor? {
            into("${indent}${UMFReader.EntryType.SIGNATURE.key}\t")
            into.writeNamespaced(values)
            into("\n")
            return UMFSignatureWriter(into, indent + "\t", namespaces)
        }
    }

    class UMFMappingWriter(into: (String) -> Unit) : BaseUMFWriter<MappingVisitor>(into), MappingVisitor {
        override lateinit var namespaces: List<Namespace>

        override fun nextUnnamedNs(): Namespace {
            val ns = Namespace("unnamed_${namespaces.size}")
            namespaces += ns
            return ns
        }

        override fun visitHeader(vararg namespaces: String) {
            this.namespaces = namespaces.map { Namespace(it) }
            into("${namespaces.joinToString("\t") { it.maybeEscape() }}\n")
        }

        override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
            into("${UMFReader.EntryType.PACKAGE.key}\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            return UMFPackageWriter(into, namespaces)
        }

        override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor? {
            into("${UMFReader.EntryType.CLASS.key}\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            return UMFClassWriter(into, namespaces)
        }

        override fun visitConstantGroup(
            type: ConstantGroupNode.InlineType,
            name: String?,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ConstantGroupVisitor? {
            into("${UMFReader.EntryType.CONSTANT_GROUP.key}\t${type.name.lowercase()}\t${name.maybeEscape()}\t${baseNs.name.maybeEscape()}")
            for (ns in namespaces) {
                if (ns in namespaces) {
                    into("\t${ns.name.maybeEscape()}")
                }
            }
            into("\n")
            return UMFConstantGroupWriter(into, this.namespaces)
        }

    }

    class UMFPackageWriter(into: (String) -> Unit, override val namespaces: List<Namespace>) : BaseUMFWriter<PackageVisitor>(into, "\t"), PackageVisitor {
        override fun visitJavadoc(values: Map<Namespace, String>): CommentVisitor? {
            into(indent)
            into("${UMFReader.EntryType.JAVADOC.key}}\t")
            into.writeNamespaced(values)
            into("\n")
            return UMFCommentWriter(into, indent + "\t", namespaces)
        }
    }

    class UMFClassWriter(into: (String) -> Unit, namespaces: List<Namespace>) : UMFMemberWriter<ClassVisitor>(into, "\t", namespaces), ClassVisitor {
        lateinit var names: Map<Namespace, InternalName>

        override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
            into(indent)
            into("${UMFReader.EntryType.METHOD.key}\t")
            into.writeNamespaced(namespaces.mapValues { v -> v.value.second?.let { "${v.value.first};${v.value.second!!.value}" } ?: v.value.first })
            into("\n")
            return UMFMethodWriter(into, indent + "\t", this.namespaces)
        }

        override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
            into(indent)
            into("${UMFReader.EntryType.FIELD.key}\t")
            into.writeNamespaced(namespaces.mapValues { v -> v.value.second?.let { "${v.value.first};${v.value.second!!.value}" } ?: v.value.first })
            into("\n")
            return UMFFieldWriter(into, indent + "\t", this.namespaces)
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
            into(indent)
            into("${UMFReader.EntryType.INNER_CLASS.key}\t$typeStr\t")
            into.writeNamespaced(names.mapValues { v -> v.value.second?.let { "${v.value.first};${v.value.second!!.value}" } ?: v.value.first })
            into("\n")
            return UMFInnerClassWriter(into, indent + "\t", namespaces)
        }

    }

    class UMFCommentWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<CommentVisitor>(into, indent), CommentVisitor

    class UMFSignatureWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<SignatureVisitor>(into, indent), SignatureVisitor

    class UMFAccessWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<AccessVisitor>(into, indent), AccessVisitor

    class UMFAnnotationWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<AnnotationVisitor>(into, indent), AnnotationVisitor

    class UMFConstantGroupWriter(into: (String) -> Unit, override val namespaces: List<Namespace>) : BaseUMFWriter<ConstantGroupVisitor>(into, "\t"), ConstantGroupVisitor {
        override fun visitConstant(
            fieldClass: InternalName,
            fieldName: UnqualifiedName,
            fieldDesc: FieldDescriptor?
        ): ConstantVisitor? {
            into(indent)
            into("n\t")
            into(fieldClass.value.maybeEscape())
            into("\t")
            into(fieldName.value.maybeEscape())
            if (fieldDesc != null) {
                into(";")
                into(fieldDesc.value.value.maybeEscape())
            }
            into("\n")
            return UMFConstantWriter(into, indent + "\t", namespaces)
        }

        override fun visitTarget(target: FullyQualifiedName, paramIdx: Int?): TargetVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CONSTANT_TARGET.key}\t")
            into(target.value.maybeEscape())
            into("\t")
            into(paramIdx?.toString().maybeEscape())
            into("\n")
            return UMFTargetWriter(into, indent + "\t", namespaces)
        }

    }

    class UMFMethodWriter(into: (String) -> Unit, indent: String = "", namespaces: List<Namespace>) : UMFMemberWriter<MethodVisitor>(into, indent, namespaces), MethodVisitor {
        override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
            into(indent)
            into("${UMFReader.EntryType.PARAMETER.key}\t")
            into(index?.toString().maybeEscape())
            into("\t")
            into(lvOrd?.toString().maybeEscape())
            into("\t")
            into.writeNamespaced(names)
            into("\n")
            return UMFParameterWriter(into, indent + "\t", namespaces)
        }

        override fun visitLocalVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, String>): LocalVariableVisitor? {
            into(indent)
            into("${UMFReader.EntryType.LOCAL_VARIABLE.key}\t")
            into(lvOrd.toString().maybeEscape())
            into("\t")
            into(startOp?.toString().maybeEscape())
            into("\t")
            into.writeNamespaced(names)
            into("\n")
            return UMFLocalVariableWriter(into, indent + "\t", namespaces)
        }

        override fun visitException(
            type: ExceptionType,
            exception: InternalName,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ExceptionVisitor? {
            into(indent)
            into("${UMFReader.EntryType.EXCEPTION.key}\t")
            when (type) {
                ExceptionType.ADD -> into("+\t")
                ExceptionType.REMOVE -> into("-\t")
            }
            into(exception.value.maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            for (ns in this.namespaces) {
                if (ns in namespaces) {
                    into("\t")
                    into(ns.name.maybeEscape())
                }
            }
            into("\n")
            return UMFExceptionWriter(into, indent + "\t", this.namespaces)
        }

    }

    class UMFFieldWriter(into: (String) -> Unit, indent: String = "", namespaces: List<Namespace>) : UMFMemberWriter<FieldVisitor>(into, indent, namespaces), FieldVisitor

    class UMFInnerClassWriter(into: (String) -> Unit, indent: String = "", namespaces: List<Namespace>) : UMFAccessParentWriter<InnerClassVisitor>(into, indent, namespaces), InnerClassVisitor

    class UMFConstantWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<ConstantVisitor>(into, indent), ConstantVisitor

    class UMFTargetWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<TargetVisitor>(into, indent), TargetVisitor

    class UMFParameterWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : UMFMemberWriter<ParameterVisitor>(into, indent, namespaces), ParameterVisitor

    class UMFLocalVariableWriter(into: (String) -> Unit, indent: String = "", namespaces: List<Namespace>) : UMFMemberWriter<LocalVariableVisitor>(into, indent, namespaces), LocalVariableVisitor

    class UMFExceptionWriter(into: (String) -> Unit, indent: String = "", override val namespaces: List<Namespace>) : BaseUMFWriter<ExceptionVisitor>(into, indent), ExceptionVisitor

}