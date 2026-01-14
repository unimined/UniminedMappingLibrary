package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSink
import xyz.wagyourtail.commonskt.utils.escape
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.Delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator
import kotlin.collections.mapValues

object UMFWriter : FormatWriter {

    val EMPTY ="umf\t1\t1\n"

    var global_minimize = false

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

    operator fun String.minus(s: String): String = this.removeSuffix(s)

    override fun write(append: (String) -> Unit, envType: EnvType): RootMappingVisitor {
        return write(envType, append, global_minimize)
    }

    fun write(into: BufferedSink, minimize: Boolean): RootMappingVisitor {
        return write(EnvType.JOINED, into, minimize)
    }

    fun write(envType: EnvType, into: BufferedSink, minimize: Boolean): RootMappingVisitor {
        return write(envType, into::writeUtf8, minimize)
    }

    fun write(into: (String) -> Unit, minimize: Boolean): RootMappingVisitor {
        into(EMPTY)
        return EmptyRootMappingVisitor().delegator(UMFWriterDelegator(into, minimize))
    }

    fun write(envType: EnvType, into: (String) -> Unit, minimize: Boolean): RootMappingVisitor {
        into(EMPTY)
        return EmptyRootMappingVisitor().delegator(UMFWriterDelegator(into, minimize))
    }

    class UMFWriterDelegator(
        val into: (String) -> Unit,
        val minimize: Boolean
    ) : Delegator() {

        lateinit var namespaces: List<Namespace>
        var indent = ""

        private fun ((String) -> Unit).writeNamespaced(names: Map<Namespace, String>) {
            namespaces.withIndex().forEach { (i, ns) ->
                this((names[ns]?.maybeEscape() ?: "_"))
                if (i != namespaces.lastIndex) {
                    this("\t")
                }
            }
        }

        override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
            this.namespaces = namespaces.toList()
            into(indent)
            into(namespaces.joinToString("\t") { it.name.maybeEscape() })
            into("\n")
        }

        override fun visitEnd(delegate: BaseMappingVisitor) {
            indent -= "\t"
        }

        override fun visitPackage(delegate: RootMappingVisitor, names: Map<Namespace, PackageName>): PackageMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.PACKAGE.key}\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return super.visitPackage(delegate, names)
        }

        override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CLASS.key}\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return super.visitClass(delegate, names)
        }

        override fun visitField(
            delegate: ClassMappingVisitor,
            names: Map<Namespace, FieldNameAndDescriptor>
        ): FieldMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.FIELD.key}\t")
            into.writeNamespaced(if (minimize) {
                val map = mutableMapOf<Namespace, String>()
                var hasDesc = false
                for ((ns, entry) in names.entries.sortedBy { namespaces.indexOf(it.key) }) {
                    if (entry.hasDescriptor && !hasDesc) {
                        hasDesc = true
                        map[ns] = entry.value
                    } else {
                        map[ns] = entry.name.value
                    }
                }
                map
            } else {
                names.mapValues { v -> v.value.value }
            })
            into("\n")
            indent += "\t"
            return super.visitField(delegate, names)
        }

        override fun visitMethod(
            delegate: ClassMappingVisitor,
            names: Map<Namespace, MethodNameAndDescriptor>
        ): MethodMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.METHOD.key}\t")
            into.writeNamespaced(if (minimize) {
                val map = mutableMapOf<Namespace, String>()
                var hasDesc = false
                for ((ns, entry) in names.entries.sortedBy { namespaces.indexOf(it.key) }) {
                    if (entry.hasDescriptor && !hasDesc) {
                        hasDesc = true
                        map[ns] = entry.value
                    } else {
                        map[ns] = entry.name.value
                    }
                }
                map
            } else {
                names.mapValues { v -> v.value.value }
            })
            into("\n")
            indent += "\t"
            return super.visitMethod(delegate, names)
        }

        override fun visitWildcard(
            delegate: ClassMappingVisitor,
            type: WildcardType,
            descs: Map<Namespace, FieldOrMethodDescriptor>
        ): WildcardMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.WILDCARD.key}\t")
            into(when (type) {
                WildcardType.FIELD -> "f"
                WildcardType.METHOD -> "m"
            })
            into("\t")
            into.writeNamespaced((if (minimize && descs.isNotEmpty()) mapOf(descs.entries.sortedBy { namespaces.indexOf(it.key) }.first().toPair()) else descs).mapValues { it.value.toString() })
            into("\n")
            indent += "\t"
            return super.visitWildcard(delegate, type, descs)
        }

        override fun visitInnerClass(
            delegate: ClassMappingVisitor,
            type: InnerType,
            names: Map<Namespace, Pair<String, FullyQualifiedName?>>
        ): InnerClassMappingVisitor? {
            val typeStr = when (type) {
                InnerType.INNER -> "i"
                InnerType.LOCAL -> "l"
                InnerType.ANONYMOUS -> "a"
            }
            into(indent)
            into("${UMFReader.EntryType.INNER_CLASS.key}\t$typeStr\t")
            into.writeNamespaced(if (minimize) {
                val map = mutableMapOf<Namespace, String>()
                var hasDesc = false
                for ((ns, entry) in names.entries.sortedBy { namespaces.indexOf(it.key) }) {
                    val (name, desc) = entry
                    if (desc != null && !hasDesc) {
                        hasDesc = true
                        map[ns] = "$name;${desc.value}"
                    } else {
                        map[ns] = name
                    }
                }
                if (!hasDesc) throw IllegalArgumentException("No fqn found")
                map
            } else {
                names.mapValues { v -> v.value.second?.let { "${v.value.first};${it.value}" } ?: v.value.first }
            })
            into("\n")
            indent += "\t"
            return super.visitInnerClass(delegate, type, names)
        }

        override fun visitSeal(
            delegate: ClassMappingVisitor,
            type: SealedType,
            name: InternalName?,
            baseNs: Namespace,
        ): SealMappingVisitor? {
            val typeStr = when (type) {
                SealedType.ADD -> "+"
                SealedType.REMOVE -> "-"
                SealedType.CLEAR -> "c"
            }
            into(indent)
            into("${UMFReader.EntryType.SEAL.key}\t$typeStr\t")
            if (type != SealedType.CLEAR) {
                into(name!!.value.maybeEscape())
                into("\t")
            }
            into(baseNs.name.maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitSeal(delegate, type, name, baseNs)
        }

        override fun visitInterface(
            delegate: ClassMappingVisitor,
            type: InterfacesType,
            name: ClassTypeSignature,
            baseNs: Namespace,
        ): InterfaceMappingVisitor? {
            val typeStr = when (type) {
                InterfacesType.ADD -> "+"
                InterfacesType.REMOVE -> "-"
            }
            into(indent)
            into("${UMFReader.EntryType.INTERFACE.key}\t$typeStr\t")
            into(name.value.maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitInterface(delegate, type, name, baseNs)
        }

        override fun visitParameter(
            delegate: InvokableMappingVisitor,
            index: Int?,
            lvOrd: Int?,
            names: Map<Namespace, UnqualifiedName>
        ): ParameterMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.PARAMETER.key}\t")
            into(index?.toString().maybeEscape())
            into("\t")
            into(lvOrd?.toString().maybeEscape())
            into("\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return super.visitParameter(delegate, index, lvOrd, names)
        }

        override fun visitLocalVariable(
            delegate: InvokableMappingVisitor,
            lvOrd: Int,
            startOp: Int?,
            names: Map<Namespace, UnqualifiedName>
        ): LocalVariableMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.LOCAL_VARIABLE.key}\t")
            into(lvOrd.toString().maybeEscape())
            into("\t")
            into(startOp?.toString().maybeEscape())
            into("\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return super.visitLocalVariable(delegate, lvOrd, startOp, names)
        }

        override fun visitException(
            delegate: InvokableMappingVisitor,
            type: ExceptionType,
            exception: InternalName,
            baseNs: Namespace,
        ): ExceptionMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.EXCEPTION.key}\t")
            when (type) {
                ExceptionType.ADD -> into("+\t")
                ExceptionType.REMOVE -> into("-\t")
            }
            into(exception.value.maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitException(delegate, type, exception, baseNs)
        }

        override fun visitAccess(
            delegate: AccessParentMappingVisitor,
            type: AccessType,
            value: AccessFlag,
            conditions: AccessConditions,
        ): AccessMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.ACCESS.key}\t")
            when (type) {
                AccessType.ADD -> into("+\t")
                AccessType.REMOVE -> into("-\t")
            }
            into("${value.name.lowercase()}\t")
            into("$conditions\t")
            into(namespaces.joinToString("\t") { it.name.maybeEscape() })
            into("\n")
            indent += "\t"
            return super.visitAccess(delegate, type, value, conditions)
        }

        override fun visitJavadoc(
            delegate: JavadocParentMappingVisitor,
            value: String,
            baseNs: Namespace
        ): JavadocMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.JAVADOC.key}\t")
            into(value.maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitJavadoc(delegate, value, baseNs)
        }

        override fun <T: Signature> visitSignature(
            delegate: SignatureParentMappingVisitor<T>,
            value: T,
            baseNs: Namespace
        ): SignatureMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.SIGNATURE.key}\t")
            into(value.toString().maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitSignature(delegate, value, baseNs)
        }

        override fun visitAnnotation(
            delegate: AnnotationParentMappingVisitor,
            type: AnnotationType,
            baseNs: Namespace,
            annotation: Annotation,
        ): AnnotationMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.ANNOTATION.key}\t")
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
            into("\n")
            indent += "\t"
            return super.visitAnnotation(delegate, type, baseNs, annotation)
        }

        override fun visitConstantGroup(
            delegate: RootMappingVisitor,
            type: InlineType,
            name: String?,
            baseNs: Namespace,
        ): ConstantGroupMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CONSTANT_GROUP.key}\t")
            into("${type.name.lowercase()}\t${name.maybeEscape()}\t${baseNs.name.maybeEscape()}")
            into("\n")
            indent += "\t"
            return super.visitConstantGroup(delegate, type, name, baseNs)
        }

        override fun visitConstant(
            delegate: ConstantGroupMappingVisitor,
            fieldClass: InternalName,
            fieldName: UnqualifiedName,
            fieldDesc: FieldDescriptor?
        ): ConstantMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CONSTANT.key}\t")
            into(fieldClass.value.maybeEscape())
            into("\t")
            into(fieldName.value.maybeEscape())
            if (fieldDesc != null) {
                into(";")
                into(fieldDesc.value.value.maybeEscape())
            }
            into("\n")
            indent += "\t"
            return super.visitConstant(delegate, fieldClass, fieldName, fieldDesc)
        }

        override fun visitTarget(
            delegate: ConstantGroupMappingVisitor,
            target: FullyQualifiedName?,
            paramIdx: Int?
        ): TargetMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CONSTANT_TARGET.key}\t")
            if (target != null) {
                into(target.value.maybeEscape())
            }
            into("\t")
            into(paramIdx?.toString().maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitTarget(delegate, target, paramIdx)
        }

        override fun visitExpression(
            delegate: ConstantGroupMappingVisitor,
            value: Constant,
            expression: Expression
        ): ExpressionMappingVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CONSTANT_EXPRESSION.key}\t")
            into(value.value.maybeEscape())
            into("\t")
            into(expression.toString().maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitExpression(delegate, value, expression)
        }

    }


}
