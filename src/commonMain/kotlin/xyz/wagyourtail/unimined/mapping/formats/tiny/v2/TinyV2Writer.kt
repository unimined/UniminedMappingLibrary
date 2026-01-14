package xyz.wagyourtail.unimined.mapping.formats.tiny.v2

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter.minus
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object TinyV2Writer : FormatWriter {

    private fun String.escape(): String {
        if (this.isEmpty()) return this
        return buildString(this.length) {
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

    override fun write(append: (String) -> Unit, envType: EnvType): RootMappingVisitor {
        return EmptyRootMappingVisitor().delegator(TinyV2WriterDelegator(append))
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

        override fun visitHeader(delegate: RootMappingVisitor, vararg namespaces: Namespace) {
            into("tiny\t2\t0\t")
            this.namespaces = namespaces.toList()
            into(this.namespaces.joinToString("\t") { it.name })
            into("\n\tescaped-names\n")
        }

        override fun visitEnd(delegate: BaseMappingVisitor) {
            indent -= "\t"
        }

        override fun visitClass(delegate: RootMappingVisitor, names: Map<Namespace, InternalName>): ClassMappingVisitor? {
            if (namespaces.first() !in names) return null
            into("c\t")
            into.writeNamespaced(names.mapValues { it.value.toString() })
            into("\n")
            indent += "\t"
            return default.visitClass(delegate, names)
        }

        override fun visitMethod(
            delegate: ClassMappingVisitor,
            names: Map<Namespace, MethodNameAndDescriptor>
        ): MethodMappingVisitor? {
            if (namespaces.first() !in names) return null
            val srcDesc = names[namespaces.first()]?.descriptor ?: return null
            into(indent)
            into("m\t")
            into(srcDesc.toString().escape())
            into("\t")
            into.writeNamespaced(names.mapValues { it.value.name.value })
            into("\n")
            indent += "\t"
            return default.visitMethod(delegate, names)
        }

        override fun visitField(delegate: ClassMappingVisitor, names: Map<Namespace, FieldNameAndDescriptor>): FieldMappingVisitor? {
            if (namespaces.first() !in names) return null
            val srcDesc = names[namespaces.first()]?.descriptor ?: return null
            into(indent)
            into("f\t")
            into(srcDesc.toString().escape())
            into("\t")
            into.writeNamespaced(names.mapValues { it.value.name.value })
            into("\n")
            indent += "\t"
            return default.visitField(delegate, names)
        }

        override fun visitJavadoc(
            delegate: JavadocParentMappingVisitor,
            value: String,
            baseNs: Namespace
        ): JavadocMappingVisitor? {
            if (indent.isEmpty()) throw IllegalStateException("Top level javadoc?")
            into(indent)
            into("c\t")
            into(value.escape())
            into("\n")
            return null
        }

        override fun visitParameter(
            delegate: InvokableMappingVisitor,
            index: Int?,
            lvOrd: Int?,
            names: Map<Namespace, UnqualifiedName>
        ): ParameterMappingVisitor? {
            if (lvOrd == null) return null
            into(indent)
            into("p\t")
            into(lvOrd.toString())
            into("\t")
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            indent += "\t"
            return default.visitParameter(delegate, index, lvOrd, names)
        }

        override fun visitLocalVariable(
            delegate: InvokableMappingVisitor,
            lvOrd: Int,
            startOp: Int?,
            names: Map<Namespace, UnqualifiedName>
        ): LocalVariableMappingVisitor? {
            into(indent)
            into("v\t")
            into(lvOrd.toString())
            into("\t")
            into(startOp?.toString() ?: "")
            into("\t\t") // skip lvt-idx
            into.writeNamespaced(names.mapValues { it.value.value })
            into("\n")
            return null
        }
    }

}