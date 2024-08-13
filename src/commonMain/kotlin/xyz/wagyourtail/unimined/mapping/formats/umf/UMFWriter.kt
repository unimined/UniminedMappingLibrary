package xyz.wagyourtail.unimined.mapping.formats.umf

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.util.firstAsMap
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.Delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object UMFWriter : FormatWriter {

    val EMPTY ="umf\t1\t0\n"

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

    override fun write(append: (String) -> Unit, envType: EnvType): MappingVisitor {
        return write(envType, append, global_minimize)
    }

    fun write(into: BufferedSink, minimize: Boolean): MappingVisitor {
        return write(EnvType.JOINED, into, minimize)
    }

    fun write(envType: EnvType, into: BufferedSink, minimize: Boolean): MappingVisitor {
        return write(envType, into::writeUtf8, minimize)
    }

    fun write(envType: EnvType, into: (String) -> Unit, minimize: Boolean): MappingVisitor {
        into(EMPTY)
        return EmptyMappingVisitor().delegator(UMFWriterDelegator(into, minimize))

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

        override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
            this.namespaces = namespaces.map { Namespace(it) }
            into(indent)
            into(namespaces.joinToString("\t") { it.maybeEscape() })
            into("\n")
        }

        override fun nextUnnamedNs(delegate: MappingVisitor): Namespace {
            val ns = Namespace("unnamed_${namespaces.size}")
            namespaces += ns
            return ns
        }

        override fun visitEnd(delegate: BaseVisitor<*>) {
            indent -= "\t"
        }

        override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
            into(indent)
            into("${UMFReader.EntryType.PACKAGE.key}\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return super.visitPackage(delegate, names)
        }

        override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CLASS.key}\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return super.visitClass(delegate, names)
        }

        override fun visitField(
            delegate: ClassVisitor,
            names: Map<Namespace, Pair<String, FieldDescriptor?>>
        ): FieldVisitor? {
            into(indent)
            into("${UMFReader.EntryType.FIELD.key}\t")
            into.writeNamespaced(if (minimize) {
                val map = mutableMapOf<Namespace, String>()
                var hasDesc = false
                for ((ns, entry) in names) {
                    val (name, desc) = entry
                    if (desc != null && !hasDesc) {
                        hasDesc = true
                        map[ns] = "$name;${desc.value}"
                    } else {
                        map[ns] = name
                    }
                }
                map
            } else {
                names.mapValues { v -> v.value.second?.let { "${v.value.first};${it.value}" } ?: v.value.first }
            })
            into("\n")
            indent += "\t"
            return super.visitField(delegate, names)
        }

        override fun visitMethod(
            delegate: ClassVisitor,
            names: Map<Namespace, Pair<String, MethodDescriptor?>>
        ): MethodVisitor? {
            into(indent)
            into("${UMFReader.EntryType.METHOD.key}\t")
            into.writeNamespaced(if (minimize) {
                val map = mutableMapOf<Namespace, String>()
                var hasDesc = false
                for ((ns, entry) in names) {
                    val (name, desc) = entry
                    if (desc != null && !hasDesc) {
                        hasDesc = true
                        map[ns] = "$name;${desc.value}"
                    } else {
                        map[ns] = name
                    }
                }
                map
            } else {
                names.mapValues { v -> v.value.second?.let { "${v.value.first};${it.value}" } ?: v.value.first }
            })
            into("\n")
            indent += "\t"
            return super.visitMethod(delegate, names)
        }

        override fun visitWildcard(
            delegate: ClassVisitor,
            type: WildcardNode.WildcardType,
            descs: Map<Namespace, FieldOrMethodDescriptor>
        ): WildcardVisitor? {
            into(indent)
            into("${UMFReader.EntryType.WILDCARD.key}\t")
            into(when (type) {
                WildcardNode.WildcardType.FIELD -> "f"
                WildcardNode.WildcardType.METHOD -> "m"
            })
            into("\t")
            into.writeNamespaced((if (minimize && descs.isNotEmpty()) descs.firstAsMap() else descs).mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return super.visitWildcard(delegate, type, descs)
        }

        override fun visitInnerClass(
            delegate: ClassVisitor,
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
            into.writeNamespaced(if (minimize) {
                val map = mutableMapOf<Namespace, String>()
                var hasDesc = false
                for ((ns, entry) in names) {
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
            delegate: ClassVisitor,
            type: SealedType,
            name: InternalName?,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): SealVisitor? {
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
            for (ns in this.namespaces) {
                if (ns in namespaces) {
                    into("\t")
                    into(ns.name.maybeEscape())
                }
            }
            into("\n")
            indent += "\t"
            return super.visitSeal(delegate, type, name, baseNs, namespaces)
        }

        override fun visitInterface(
            delegate: ClassVisitor,
            type: InterfacesType,
            name: InternalName,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): InterfaceVisitor? {
            val typeStr = when (type) {
                InterfacesType.ADD -> "+"
                InterfacesType.REMOVE -> "-"
            }
            into(indent)
            into("${UMFReader.EntryType.INTERFACE.key}\t$typeStr\t")
            into(name.value.maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            for (ns in this.namespaces) {
                if (ns in namespaces) {
                    into("\t")
                    into(ns.name.maybeEscape())
                }
            }
            into("\n")
            indent += "\t"
            return super.visitInterface(delegate, type, name, baseNs, namespaces)
        }

        override fun visitParameter(
            delegate: InvokableVisitor<*>,
            index: Int?,
            lvOrd: Int?,
            names: Map<Namespace, String>
        ): ParameterVisitor? {
            into(indent)
            into("${UMFReader.EntryType.PARAMETER.key}\t")
            into(index?.toString().maybeEscape())
            into("\t")
            into(lvOrd?.toString().maybeEscape())
            into("\t")
            into.writeNamespaced(names)
            into("\n")
            indent += "\t"
            return super.visitParameter(delegate, index, lvOrd, names)
        }

        override fun visitLocalVariable(
            delegate: InvokableVisitor<*>,
            lvOrd: Int,
            startOp: Int?,
            names: Map<Namespace, String>
        ): LocalVariableVisitor? {
            into(indent)
            into("${UMFReader.EntryType.LOCAL_VARIABLE.key}\t")
            into(lvOrd.toString().maybeEscape())
            into("\t")
            into(startOp?.toString().maybeEscape())
            into("\t")
            into.writeNamespaced(names)
            into("\n")
            indent += "\t"
            return super.visitLocalVariable(delegate, lvOrd, startOp, names)
        }

        override fun visitException(
            delegate: InvokableVisitor<*>,
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
            indent += "\t"
            return super.visitException(delegate, type, exception, baseNs, namespaces)
        }

        override fun visitAccess(
            delegate: AccessParentVisitor<*>,
            type: AccessType,
            value: AccessFlag,
            conditions: AccessConditions,
            namespaces: Set<Namespace>
        ): AccessVisitor? {
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
            return super.visitAccess(delegate, type, value, conditions, namespaces)
        }

        override fun visitJavadoc(
            delegate: JavadocParentNode<*>,
            value: String,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): JavadocVisitor? {
            into(indent)
            into("${UMFReader.EntryType.JAVADOC.key}\t")
            into(value.maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            for (ns in this.namespaces) {
                if (ns in namespaces) {
                    into("\t")
                    into(ns.name.maybeEscape())
                }
            }
            into("\n")
            indent += "\t"
            return super.visitJavadoc(delegate, value, baseNs, namespaces)
        }

        override fun visitSignature(
            delegate: SignatureParentVisitor<*>,
            value: String,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): SignatureVisitor? {
            into(indent)
            into("${UMFReader.EntryType.SIGNATURE.key}\t")
            into(value.maybeEscape())
            into("\t")
            into(baseNs.name.maybeEscape())
            for (ns in this.namespaces) {
                if (ns in namespaces) {
                    into("\t")
                    into(ns.name.maybeEscape())
                }
            }
            into("\n")
            indent += "\t"
            return super.visitSignature(delegate, value, baseNs, namespaces)
        }

        override fun visitAnnotation(
            delegate: AnnotationParentVisitor<*>,
            type: AnnotationType,
            baseNs: Namespace,
            annotation: Annotation,
            namespaces: Set<Namespace>
        ): AnnotationVisitor? {
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
            into("\t")
            for (ns in this.namespaces) {
                if (ns in namespaces) {
                    into(ns.name.maybeEscape())
                }
            }
            into("\n")
            indent += "\t"
            return super.visitAnnotation(delegate, type, baseNs, annotation, namespaces)
        }

        override fun visitConstantGroup(
            delegate: MappingVisitor,
            type: ConstantGroupNode.InlineType,
            name: String?,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ConstantGroupVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CONSTANT_GROUP.key}\t")
            into("${type.name.lowercase()}\t${name.maybeEscape()}\t${baseNs.name.maybeEscape()}")
            for (ns in this.namespaces) {
                if (ns in namespaces) {
                    into("\t${ns.name.maybeEscape()}")
                }
            }
            into("\n")
            indent += "\t"
            return super.visitConstantGroup(delegate, type, name, baseNs, namespaces)
        }

        override fun visitConstant(
            delegate: ConstantGroupVisitor,
            fieldClass: InternalName,
            fieldName: UnqualifiedName,
            fieldDesc: FieldDescriptor?
        ): ConstantVisitor? {
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
            delegate: ConstantGroupVisitor,
            target: FullyQualifiedName,
            paramIdx: Int?
        ): TargetVisitor? {
            into(indent)
            into("${UMFReader.EntryType.CONSTANT_TARGET.key}\t")
            into(target.value.maybeEscape())
            into("\t")
            into(paramIdx?.toString().maybeEscape())
            into("\n")
            indent += "\t"
            return super.visitTarget(delegate, target, paramIdx)
        }

    }


}
