package xyz.wagyourtail.unimined.mapping.formats.csrg

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object CsrgReader : FormatReader {

    const val CLASS_MAPPING = 2
    const val FIELD_MAPPING = 3
    const val METHOD_MAPPING = 4

    fun AbstractMappingTree?.mapPackage(srcNs: Namespace, dstNs: Namespace, name: InternalName): InternalName {
        if (this == null) return name
        val parts = name.getParts()
        val mappedPkg = mapPackage(srcNs, dstNs, parts.first)
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
                    append(tree.mapPackage(fromNs, toNs, obj))
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

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        return fileName.endsWith(".csrg")
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        into.use {
            visitHeader(srcNs.name, dstNs.name)

            while (!input.exhausted()) {
                input.takeWhitespace()
                if (input.peek() == '#') {
                    input.takeLine()
                    continue
                }
                val parts = input.takeRemainingOnLine()
                when (parts.size) {
                    CLASS_MAPPING -> {
                        val src = InternalName.read(parts[0].second)
                        val dst = context.mapPackage(srcNs, dstNs, InternalName.read(parts[1].second))
                        visitClass(mapOf(srcNs to src, dstNs to dst))?.visitEnd()
                    }

                    FIELD_MAPPING -> {
                        val dstCls = context.mapPackage(srcNs, dstNs, InternalName.read(parts[0].second))
                        val srcName = parts[1].second
                        val dstName = parts[2].second
                        visitClass(mapOf(dstNs to dstCls))?.use {
                            visitField(mapOf(srcNs to (srcName to null), dstNs to (dstName to null)))?.visitEnd()
                        }
                    }

                    METHOD_MAPPING -> {
                        val dstCls = context.mapPackage(srcNs, dstNs, InternalName.read(parts[0].second))
                        val srcName = parts[1].second
                        val dstDesc = context.mapDescPackages(srcNs, dstNs, MethodDescriptor.read(parts[2].second))
                        val dstName = parts[3].second
                        into.visitClass(mapOf(dstNs to dstCls))?.use {
                            visitMethod(mapOf(srcNs to (srcName to null), dstNs to (dstName to dstDesc)))?.visitEnd()
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Invalid line: ${parts.joinToString(" ") { it.second }}")
                    }
                }
            }
        }
    }

}