package xyz.wagyourtail.unimined.mapping.formats.csrg

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object CsrgReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    const val CLASS_MAPPING = 2
    const val FIELD_MAPPING = 3
    const val METHOD_MAPPING = 4

    fun AbstractMappingTree?.mapPackage(srcNs: Namespace, dstNs: Namespace, name: InternalName): InternalName {
        if (this == null) return name
        val parts = name.getParts()
        val mappedPkg = map(srcNs, dstNs, parts.first)
        return InternalName(mappedPkg, parts.second)
    }

    fun AbstractMappingTree?.mapDescPackages(srcNs: Namespace, dstNs: Namespace, md: MethodDescriptor): MethodDescriptor {
        if (this == null) return md
        return MethodDescriptor.unchecked(buildString {
            md.accept(descRemapAcceptor(this@mapDescPackages, srcNs, dstNs))
        })
    }

    fun StringBuilder.descRemapAcceptor(tree: AbstractMappingTree, fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
        return { obj, leaf ->
            when (obj) {
                is PackageName -> {
                    append(tree.map(fromNs, toNs, obj))
                    false
                }
                else -> {
                    if (leaf) {
                        append(obj.toString())
                    }
                    true
                }
            }
        }
    }

    override fun isFormat(fileName: String, input: BufferedSource, envType: EnvType): Boolean {
        return fileName.endsWith(".csrg")
    }

    override suspend fun read(
        input: CharReader<*>,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        envType: EnvType,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.use {
            visitHeader(srcNs.name, dstNs.name)

            while (!input.exhausted()) {
                input.takeNonNewlineWhitespace()
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                if (input.peek() == '#') {
                    input.takeLine()
                    continue
                }

                val parts = input.takeRemainingLiteralOnLine()
                when (parts.size) {
                    CLASS_MAPPING -> {
                        val src = InternalName.read(parts[0])
                        val dst = context.mapPackage(srcNs, dstNs, InternalName.read(parts[1]))
                        visitClass(mapOf(srcNs to src, dstNs to dst))?.visitEnd()
                    }

                    FIELD_MAPPING -> {
                        val dstCls = context.mapPackage(srcNs, dstNs, InternalName.read(parts[0]))
                        val srcName = parts[1]
                        val dstName = parts[2]
                        visitClass(mapOf(dstNs to dstCls))?.use {
                            visitField(mapOf(srcNs to (srcName to null), dstNs to (dstName to null)))?.visitEnd()
                        }
                    }

                    METHOD_MAPPING -> {
                        val dstCls = context.mapPackage(srcNs, dstNs, InternalName.read(parts[0]))
                        val srcName = parts[1]
                        val dstDesc = context.mapDescPackages(srcNs, dstNs, MethodDescriptor.read(parts[2]))
                        val dstName = parts[3]
                        into.visitClass(mapOf(dstNs to dstCls))?.use {
                            visitMethod(mapOf(srcNs to (srcName to null), dstNs to (dstName to dstDesc)))?.visitEnd()
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Invalid line: ${parts.joinToString(" ") { it }}")
                    }
                }
            }
        }
    }

}