package xyz.wagyourtail.unimined.mapping.formats.csrg

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableFieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableMethodMapping
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

object CsrgReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false

    const val CLASS_MAPPING = 2
    const val FIELD_MAPPING = 3
    const val METHOD_MAPPING = 4

    fun MappingTree?.mapPackage(srcNs: Namespace, dstNs: Namespace, name: InternalName): InternalName {
        if (this == null) return name
        val parts = name.getParts()
        val mappedPkg = map(srcNs, dstNs, parts.first)
        return InternalName(mappedPkg, parts.second)
    }

    fun MappingTree?.mapDescPackages(srcNs: Namespace, dstNs: Namespace, md: MethodDescriptor): MethodDescriptor {
        if (this == null) return md
        return MethodDescriptor.unchecked(buildString {
            md.accept(descRemapAcceptor(this@mapDescPackages, srcNs, dstNs))
        })
    }

    fun StringBuilder.descRemapAcceptor(tree: MappingTree, fromNs: Namespace, toNs: Namespace): (Any, Boolean) -> Boolean {
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

    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        return fileName.endsWith(".csrg")
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val srcNs = Namespace(nsMapping["source"] ?: "source")
        val dstNs = Namespace(nsMapping["target"] ?: "target")

        with (into) {
            header(listOf(srcNs, dstNs))

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
                        pushed(MutableClassMapping(mapOf(srcNs to src, dstNs to dst))) {}
                    }

                    FIELD_MAPPING -> {
                        val dstCls = context.mapPackage(srcNs, dstNs, InternalName.read(parts[0]))
                        val srcName = UnqualifiedName.read(parts[1])
                        val dstName = UnqualifiedName.read(parts[2])
                        pushed(MutableClassMapping(mapOf(dstNs to dstCls))) {
                            pushed(MutableFieldMapping(mapOf(srcNs to srcName.withFieldDesc(null), dstNs to FieldNameAndDescriptor(dstName, null)))) {}
                        }
                    }

                    METHOD_MAPPING -> {
                        val dstCls = context.mapPackage(srcNs, dstNs, InternalName.read(parts[0]))
                        val srcName = UnqualifiedName.read(parts[1])
                        val dstDesc = context.mapDescPackages(srcNs, dstNs, MethodDescriptor.read(parts[2]))
                        val dstName = UnqualifiedName.read(parts[3])
                        pushed(MutableClassMapping(mapOf(dstNs to dstCls))) {
                            pushed(MutableMethodMapping(mapOf(srcNs to srcName.withMethodDesc(null), dstNs to MethodNameAndDescriptor(dstName, dstDesc)))) {}
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Invalid line: ${parts.joinToString(" ") { it }}")
                    }
                }
            }
        }
        into.end()
    }

}