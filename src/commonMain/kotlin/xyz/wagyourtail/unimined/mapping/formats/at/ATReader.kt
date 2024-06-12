package xyz.wagyourtail.unimined.mapping.formats.at

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.AccessParentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

/**
 * This reads AT files written in the format found in forge 1.7-current
 */
object ATReader : FormatReader {

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        val cfg = fileName.substringAfterLast('.') in setOf("at", "cfg")
        val name = fileName.substringBeforeLast('.').lowercase()
        return (cfg && name.endsWith("_at") || name.startsWith("accesstransformer"))
    }

    fun String.parseAccess(): Pair<List<AccessFlag>, List<AccessFlag>> {
        val add = mutableListOf<AccessFlag>()
        val remove = mutableListOf<AccessFlag>()
        CharReader("+$this").use {
            while (!it.exhausted()) {
                val addRemove = it.take()
                val str = it.takeUntil { it in "+-" }
                val access = if (str == "f") {
                    AccessFlag.FINAL
                } else {
                    AccessFlag.valueOf(str.uppercase())
                }
                when (addRemove) {
                    '+' -> {
                        add.add(access)
                    }
                    '-' -> {
                        remove.add(access)
                    }
                    else -> {
                        throw IllegalArgumentException("Invalid access transformer, expected + or -, found $addRemove")
                    }
                }
            }
        }
        return add to remove
    }

    fun <T: AccessParentVisitor<T>> AccessParentVisitor<T>.applyAccess(access: Pair<List<AccessFlag>, List<AccessFlag>>, ns: Set<Namespace>) {
        for (flag in access.first) {
            this.visitAccess(AccessType.ADD, flag, AccessConditions.ALL, ns)?.visitEnd()
        }
        for (flag in access.second) {
            this.visitAccess(AccessType.REMOVE, flag, AccessConditions.ALL, ns)?.visitEnd()
        }
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {

        val ns = Namespace(nsMapping["source"] ?: "source")

        into.use {
            visitHeader(ns.name)

            while (!input.exhausted()) {
                if (input.peek() == '\n') {
                    input.take()
                    continue
                }
                if (input.peek() == '#') {
                    input.takeLine()
                    continue
                }
                if (input.peek()?.isWhitespace() == true) {
                    throw IllegalStateException("Unexpected whitespace")
                }

                val access = input.takeNextLiteral { it.isWhitespace() }!!.parseAccess()

                val targetClass = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!.replace(".", "/"))
                val memberName =
                    input.takeNextLiteral { it.isWhitespace() }?.let { if (it.startsWith("#")) null else it }
                val memberDesc = if (memberName == null) null else input.takeNextLiteral { it.isWhitespace() }
                    ?.let { if (it.startsWith("#")) null else it }

                if (memberDesc != null) {
                    val remaining = input.takeLine().trimStart()
                    if (remaining.isNotEmpty() && remaining.first() != '#') {
                        throw IllegalArgumentException("Expected newline or comment, found $remaining")
                    }
                }

                visitClass(mapOf(ns to targetClass))?.use {
                    if (memberName == null) {
                        applyAccess(access, setOf(ns))
                    } else if (memberDesc == null) {
                        visitField(mapOf(ns to (memberName to null)))?.use {
                            applyAccess(access, setOf(ns))
                        }
                    } else {
                        visitMethod(mapOf(ns to (memberName to MethodDescriptor.read(memberDesc))))?.use {
                            applyAccess(access, setOf(ns))
                            visitEnd()
                        }
                    }
                }
            }
        }

    }

}