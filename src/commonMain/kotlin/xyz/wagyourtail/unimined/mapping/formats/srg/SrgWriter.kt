package xyz.wagyourtail.unimined.mapping.formats.srg

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
import xyz.wagyourtail.unimined.mapping.tree.node._constant.ConstantGroupNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.InnerClassNode
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.NullDelegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

object SrgWriter : FormatWriter {
    override fun write(envType: EnvType, append: (String) -> Unit): MappingVisitor {
        return EmptyMappingVisitor().delegator(object : NullDelegator() {
            lateinit var namespaces: List<Namespace>
            var currentClsNames: Map<Namespace, String>? = null

            fun Map<Namespace, String>.fillNull(): Map<Namespace, String>? {
                var srcName = this[namespaces[0]]
                var dstName = this[namespaces[1]]
                if (srcName == null) {
                    srcName = dstName
                }
                if (dstName == null) {
                    dstName = srcName
                }
                if (srcName == null) return null
                return mapOf(namespaces[0] to srcName, namespaces[1] to dstName!!)
            }

            fun ((String) -> Unit).writeEntry(prefix: String, names: Map<Namespace, String>) {
                val fixed = names.fillNull()
                if (fixed != null) {
                    this(prefix)
                    this(" ")
                    this(fixed[namespaces[0]]!!)
                    this(" ")
                    this(fixed[namespaces[1]]!!)
                    this("\n")
                }
            }

            override fun visitHeader(delegate: MappingVisitor, vararg namespaces: String) {
                if (namespaces.size != 2) {
                    throw IllegalArgumentException("Srg requires 2 namespaces")
                }
                this.namespaces = namespaces.map { Namespace(it) }
                default.visitHeader(delegate, *namespaces)
            }

            override fun visitPackage(delegate: MappingVisitor, names: Map<Namespace, PackageName>): PackageVisitor? {
                append.writeEntry("PK:", names.mapValues { it.value.value.substringBeforeLast('/').ifEmpty { "." } })
                return null
            }

            override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                append.writeEntry("CL:", names.mapValues { it.value.value })
                currentClsNames = names.mapValues { it.value.value }.fillNull()
                if (currentClsNames == null) return null
                return default.visitClass(delegate, names)
            }

            override fun visitField(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, FieldDescriptor?>>
            ): FieldVisitor? {
                append.writeEntry("FD:", names.mapValues { "${currentClsNames!![it.key]}/${it.value.first}" })
                return null
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                append.writeEntry("MD:", names.mapValues { "${currentClsNames!![it.key]}/${it.value.first} ${it.value.second?.value ?: ""}" })
                return null
            }

        })
    }
}