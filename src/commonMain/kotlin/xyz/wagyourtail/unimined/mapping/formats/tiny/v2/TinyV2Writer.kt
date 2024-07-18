package xyz.wagyourtail.unimined.mapping.formats.tiny.v2

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
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.util.escape
import xyz.wagyourtail.unimined.mapping.visitor.*

@Suppress("UNUSED_PARAMETER")
object TinyV2Writer : FormatWriter {
    override fun write(append: (String) -> Unit, envType: EnvType): MappingVisitor {
        return TinyV2MappingWriter(append)
    }

    open class BaseTinyV2Writer<T: BaseVisitor<T>>(val into: (String) -> Unit, val parent: BaseTinyV2Writer<*>?, val indent: String = ""): BaseVisitor<T> {

        val root: TinyV2MappingWriter get() = (this as? TinyV2MappingWriter) ?: parent!!.root


        fun ((String) -> Unit).writeNamespaced(names: Map<Namespace, String>) {
            root.namespaces.withIndex().forEach { (i, ns) ->
                this((names[ns]?.escape() ?: ""))
                if (i != root.namespaces.lastIndex) {
                    this("\t")
                }
            }
        }

        override fun visitEnd() {}

    }

    open class TinyV2MemberWriter<T: MemberVisitor<T>>(into: (String) -> Unit, parent: BaseTinyV2Writer<*>?, indent: String = ""): BaseTinyV2Writer<T>(into, parent, indent), MemberVisitor<T> {
        override fun visitJavadoc(value: String, baseNs: Namespace, namespaces: Set<Namespace>): JavadocVisitor? {
            if (value.isEmpty()) return null
            into(indent)
            into("c\t")
            into(value.escape())
            into("\n")
            return null
        }

        fun visitSignature(value: String, baseNs: Namespace, namespaces: Set<Namespace>): SignatureVisitor? {
            return null
        }

        override fun visitAccess(
            type: AccessType,
            value: AccessFlag,
            condition: AccessConditions,
            namespaces: Set<Namespace>
        ): AccessVisitor? {
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

    class TinyV2MappingWriter(into: (String) -> Unit) : BaseTinyV2Writer<MappingVisitor>(into, null), MappingVisitor {
        lateinit var namespaces: List<Namespace>

        override fun nextUnnamedNs(): Namespace {
            val ns = Namespace("unnamed_${namespaces.size}")
            namespaces += ns
            return ns
        }

        override fun visitHeader(vararg namespaces: String) {
            into("tiny\t2\t0\t")
            this.namespaces = namespaces.map { Namespace(it) }
            into(this.namespaces.joinToString("\t") { it.name })
            into("\n")
        }

        override fun visitPackage(names: Map<Namespace, PackageName>): PackageVisitor? {
            return null
        }

        override fun visitClass(names: Map<Namespace, InternalName>): ClassVisitor {
            into("c\t")
            into.writeNamespaced(names.mapValues { it.value.toString() })
            into("\n")
            return TinyV2ClassWriter(into, this)
        }

        override fun visitConstantGroup(
            type: ConstantGroupNode.InlineType,
            name: String?,
            baseNs: Namespace,
            namespaces: Set<Namespace>
        ): ConstantGroupVisitor? {
            return null
        }

    }

    class TinyV2ClassWriter(into: (String) -> Unit, parent: BaseTinyV2Writer<*>?): TinyV2MemberWriter<ClassVisitor>(into, parent, "\t"), ClassVisitor {
        override fun visitMethod(namespaces: Map<Namespace, Pair<String, MethodDescriptor?>>): MethodVisitor? {
            // get desc in first namespace
            val desc = namespaces[root.namespaces.first()]?.second ?: return null
            into(indent)
            into("m\t")
            into(desc.toString())
            into("\t")
            into.writeNamespaced(namespaces.mapValues { it.value.first })
            into("\n")
            return TinyV2MethodWriter(into, this)
        }

        override fun visitField(namespaces: Map<Namespace, Pair<String, FieldDescriptor?>>): FieldVisitor? {
            // get desc in first namespace
            val desc = namespaces[root.namespaces.first()]?.second ?: return null
            into(indent)
            into("f\t")
            into(desc.toString())
            into("\t")
            into.writeNamespaced(namespaces.mapValues { it.value.first })
            into("\n")
            return TinyV2FieldWriter(into, this, indent + "\t")
        }

        override fun visitInnerClass(
            type: InnerClassNode.InnerType,
            names: Map<Namespace, Pair<String, FullyQualifiedName?>>
        ): InnerClassVisitor? {
            return null
        }

        override fun visitWildcard(
            type: WildcardNode.WildcardType,
            descs: Map<Namespace, FieldOrMethodDescriptor>
        ): WildcardVisitor? {
            return null
        }

    }

    class TinyV2MethodWriter(into: (String) -> Unit, parent: BaseTinyV2Writer<*>?): TinyV2MemberWriter<MethodVisitor>(into, parent, "\t\t"), MethodVisitor {
        override fun visitParameter(index: Int?, lvOrd: Int?, names: Map<Namespace, String>): ParameterVisitor? {
            if (lvOrd == null) return null
            into(indent)
            into("p\t")
            into(lvOrd.toString())
            into("\t")
            into.writeNamespaced(names)
            into("\n")
            return object : TinyV2MemberWriter<ParameterVisitor>(into, this, indent + "\t"), ParameterVisitor {}
        }

        override fun visitLocalVariable(
            lvOrd: Int,
            startOp: Int?,
            names: Map<Namespace, String>
        ): LocalVariableVisitor {
            into(indent)
            into("v\t")
            into(lvOrd.toString())
            into("\t")
            into(startOp?.toString() ?: "")
            into("\t\t") // skip lvt-idx
            into.writeNamespaced(names)
            into("\n")
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

    class TinyV2FieldWriter(into: (String) -> Unit, parent: BaseTinyV2Writer<*>?, indent: String = ""): TinyV2MemberWriter<FieldVisitor>(into, parent, indent), FieldVisitor

}