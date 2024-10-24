package xyz.wagyourtail.unimined.mapping.formats.tiny.v2

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter.minus
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.Delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object TinyV2Writer : FormatWriter {

    private fun String.escape(): String {
        if (this.isEmpty()) return this
        return buildString {
            for (c in this@escape) {
                when (c) {
                    '\t' -> append("\\t")
                    '\r' -> append("\\r")
                    '\n' -> append("\\n")
                    '\\' -> append("\\\\")
                    '\u0000' -> append("\\0")
                    else -> append(c)
                }
            }
        }
    }

    override fun write(append: (String) -> Unit, envType: EnvType): MappingVisitor {
        return EmptyMappingVisitor().delegator(TinyV2WriterDelegator(append))
    }

    class TinyV2WriterDelegator(
        val into: (String) -> Unit,
    ) : NullDelegator() {

        var indent = ""
        lateinit var namespaces: List<Namespace>

        fun ((String) -> Unit).writeNamespaced(names: Map<Namespace, String>) {
            namespaces.withIndex().forEach { (i, ns) ->
                this((names[ns]?.escape() ?: ""))
                if (i != namespaces.lastIndex) {
                    this("\t")
                }
            }
        }

        override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
            into("tiny\t2\t0\t")
            this.namespaces = namespaces.map { Namespace(it) }
            into(this.namespaces.joinToString("\t") { it.name })
            into("\n\tescaped-names\n")
        }

        override fun visitEnd(delegate: BaseVisitor<*>) {
            indent -= "\t"
        }

        override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
            if (namespaces.first() !in names) return null
            into("c\t")
            into.writeNamespaced(names.mapValues { it.value.toString() })
            into("\n")
            indent += "\t"
            return default.visitClass(delegate, names)
        }

        override fun visitMethod(
            delegate: ClassVisitor,
            names: Map<Namespace, Pair<String, MethodDescriptor?>>
        ): MethodVisitor? {
            if (namespaces.first() !in names) return null
            val srcDesc = names[namespaces.first()]?.second ?: return null
            into(indent)
            into("m\t")
            into(srcDesc.toString().escape())
            into("\t")
            into.writeNamespaced(names.mapValues { it.value.first })
            into("\n")
            indent += "\t"
            return default.visitMethod(delegate, names)
        }

        override fun visitField(
            delegate: ClassVisitor,
            names: Map<Namespace, Pair<String, FieldDescriptor?>>
        ): FieldVisitor? {
            if (namespaces.first() !in names) return null
            val srcDesc = names[namespaces.first()]?.second ?: return null
            into(indent)
            into("f\t")
            into(srcDesc.toString().escape())
            into("\t")
            into.writeNamespaced(names.mapValues { it.value.first })
            into("\n")
            indent += "\t"
            return default.visitField(delegate, names)
        }

        override fun visitJavadoc(
            delegate: JavadocParentNode<*>,
            value: String,
            namespaces: Set<Namespace>
        ): JavadocVisitor? {
            if (indent.isEmpty()) throw IllegalStateException("Top level javadoc?")
            into(indent)
            into("c\t")
            into(value.escape())
            into("\n")
            return null
        }

        override fun visitParameter(
            delegate: InvokableVisitor<*>,
            index: Int?,
            lvOrd: Int?,
            names: Map<Namespace, String>
        ): ParameterVisitor? {
            if (lvOrd == null) return null
            into(indent)
            into("p\t")
            into(lvOrd.toString())
            into("\t")
            into.writeNamespaced(names)
            into("\n")
            indent += "\t"
            return default.visitParameter(delegate, index, lvOrd, names)
        }

        override fun visitLocalVariable(
            delegate: InvokableVisitor<*>,
            lvOrd: Int,
            startOp: Int?,
            names: Map<Namespace, String>
        ): LocalVariableVisitor? {
            into(indent)
            into("v\t")
            into(lvOrd.toString())
            into("\t")
            into(startOp?.toString() ?: "")
            into("\t\t") // skip lvt-idx
            into.writeNamespaced(names)
            into("\n")
            return null
        }
    }

}