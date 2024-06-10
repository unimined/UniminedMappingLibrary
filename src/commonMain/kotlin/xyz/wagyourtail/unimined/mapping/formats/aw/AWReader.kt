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

        val remain = input.takeRemainingOnLine()
        if (remain.firstOrNull()?.second?.startsWith("#") == false) {
            throw IllegalArgumentException("Expected newline or comment, found ${remain.firstOrNull()?.second}")
        }

        into.visitHeader(namespace)
        val ns = Namespace(namespace)

        while (!input.exhausted()) {
            if (input.peek() == '\n') {
                input.take()
                continue
            }
            if (input.peek() == '#') {
                input.takeLine()
                continue
            }

            val target = input.takeNextLiteral { it.isWhitespace() }!!
            val access = input.takeNextLiteral { it.isWhitespace() }!!

            if (!access.startsWith("transitive-")) {
                if (!allowNonTransitive) {
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
                    val visitor = into.visitClass(mapOf(ns to cls))
                    for ((flag, conditions) in addAccess) {
                        visitor?.visitAccess(AccessType.ADD, flag, conditions, setOf(ns))
                    }
                    for ((flag, conditions) in removeAccess) {
                        visitor?.visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))
                    }
                }
                "method" -> {
                    val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    val method = input.takeNextLiteral { it.isWhitespace() }!!
                    val desc = MethodDescriptor.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    val visitor = into.visitClass(mapOf(ns to cls))?.visitMethod(mapOf(ns to (method to desc)))
                    for ((flag, conditions) in addAccess) {
                        visitor?.visitAccess(AccessType.ADD, flag, conditions, setOf(ns))
                    }
                    for ((flag, conditions) in removeAccess) {
                        visitor?.visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))
                    }
                }
                "field" -> {
                    val cls = InternalName.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    val field = input.takeNextLiteral { it.isWhitespace() }!!
                    val desc = FieldDescriptor.read(input.takeNextLiteral { it.isWhitespace() }!!)
                    val visitor = into.visitClass(mapOf(ns to cls))?.visitField(mapOf(ns to (field to desc)))
                    for ((flag, conditions) in addAccess) {
                        visitor?.visitAccess(AccessType.ADD, flag, conditions, setOf(ns))
                    }
                    for ((flag, conditions) in removeAccess) {
                        visitor?.visitAccess(AccessType.REMOVE, flag, conditions, setOf(ns))
                    }
                }
                else -> {
                    throw IllegalArgumentException("Unknown target $target")
                }
            }

            val lineComment = input.takeRemainingOnLine()
            if (lineComment.firstOrNull()?.second?.startsWith("#") == false) {
                throw IllegalArgumentException("Expected newline or comment, found ${lineComment.firstOrNull()?.second}")
            }
        }


    }

}