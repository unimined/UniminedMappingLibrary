package xyz.wagyourtail.unimined.mapping.formats.aw

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use

object AWReader : FormatReader {

    @Suppress("MemberVisibilityCanBePrivate")
    var allowNonTransitive = true

    override fun isFormat(envType: EnvType, fileName: String, inputType: BufferedSource): Boolean {
        // check content begins with "accessWidener"
        return inputType.peek().readUtf8Line()?.startsWith("accessWidener") ?: false
    }

    override suspend fun read(
        envType: EnvType,
        input: CharReader,
        context: AbstractMappingTree?,
        into: MappingVisitor,
        nsMapping: Map<String, String>
    ) {
        val aw = input.takeNextLiteral { it.isWhitespace() }
        val version = input.takeNextLiteral { it.isWhitespace() }
        val namespace = input.takeNextLiteral { it.isWhitespace() }!!

        if (aw != "accessWidener") {
            throw IllegalArgumentException("Invalid access widener file")
        }
        if (version !in setOf("v1", "v2")) {
            throw IllegalArgumentException("Unknown version $version")
        }

        val remain = input.takeLine().trimStart()
        if (remain.isNotEmpty() && remain.first() != '#') {
            throw IllegalArgumentException("Expected newline or comment, found $remain")
        }

        into.use {
            into.visitHeader(nsMapping[namespace] ?: namespace)
            val ns = Namespace(nsMapping[namespace] ?: namespace)

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

                val target = input.takeNextLiteral { it.isWhitespace() }!!
                val access = input.takeNextLiteral { it.isWhitespace() }!!

                if (!access.startsWith("transitive-")) {
                    if (!allowNonTransitive) {
                        input.takeLine()
                        continue
                    }
                }

                val addAccess = mutableSetOf<Pair<AccessFlag, AccessConditions>>()
                val removeAccess = mutableSetOf<Pair<AccessFlag, AccessConditions>>()
                when (access) {
                    "accessible" -> {
                        when (target) {
                            "class" -> addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                            "method" -> {
                                addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                                addAccess.add(AccessFlag.FINAL to AccessConditions.unchecked("+${AccessFlag.PRIVATE}"))
                            }

                            "field" -> addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                        }
                    }

                    "mutable" -> {
                        if (target != "field") {
                            throw IllegalArgumentException("mutable is only valid for fields")
                        }
                        removeAccess.add(AccessFlag.FINAL to AccessConditions.ALL)
                    }

                    "extendable" -> {
                        when (target) {
                            "class" -> {
                                addAccess.add(AccessFlag.PUBLIC to AccessConditions.ALL)
                                removeAccess.add(AccessFlag.FINAL to AccessConditions.ALL)
                            }

                            "method" -> {
                                addAccess.add(AccessFlag.PROTECTED to AccessConditions.unchecked("-${AccessFlag.PUBLIC}"))
                                removeAccess.add(AccessFlag.FINAL to AccessConditions.ALL)
                            }

                            "field" -> throw IllegalArgumentException("extendable is not valid for fields")
                        }
                    }
                }

                when (target) {
                    "class" -> {
                        val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                        into.visitClass(mapOf(ns to cls))?.use {
                            for ((flag, conditions) in addAccess) {
                                visitAccess(AccessType.ADD, flag, conditions, setOf(ns))?.visitEnd()
                            }
                            for ((flag, conditions) in removeAccess) {
                                visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))?.visitEnd()
                            }
                        }
                    }

                    "method" -> {
                        val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                        val method = input.takeNextLiteral { it.isWhitespace() }!!
                        val desc = MethodDescriptor.read(input.takeNextLiteral { it.isWhitespace() }!!)
                        into.visitClass(mapOf(ns to cls))?.use {
                            var removeClsFinal = false
                            visitMethod(mapOf(ns to (method to desc)))?.use {
                                for ((flag, conditions) in addAccess) {
                                    visitAccess(AccessType.ADD, flag, conditions, setOf(ns))?.visitEnd()
                                }
                                for ((flag, conditions) in removeAccess) {
                                    visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))?.visitEnd()
                                    if (flag == AccessFlag.FINAL) {
                                        removeClsFinal = true
                                    }
                                }
                            }
                            if (removeClsFinal) {
                                visitAccess(
                                    AccessType.REMOVE,
                                    AccessFlag.FINAL,
                                    AccessConditions.ALL,
                                    setOf(ns)
                                )?.visitEnd()
                            }
                        }
                    }

                    "field" -> {
                        val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                        val field = input.takeNextLiteral { it.isWhitespace() }!!
                        val desc = FieldDescriptor.read(input.takeNextLiteral { it.isWhitespace() }!!)
                        into.visitClass(mapOf(ns to cls))?.use {
                            visitField(mapOf(ns to (field to desc)))?.use {
                                for ((flag, conditions) in addAccess) {
                                    visitAccess(AccessType.ADD, flag, conditions, setOf(ns))?.visitEnd()
                                }
                                for ((flag, conditions) in removeAccess) {
                                    visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))?.visitEnd()
                                }
                            }
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Unknown target $target")
                    }
                }

                val lineComment = input.takeLine().trimStart()
                if (lineComment.isNotEmpty() && lineComment.first() != '#') {
                    throw IllegalArgumentException("Expected newline or comment, found $lineComment")
                }
            }
        }
    }

}