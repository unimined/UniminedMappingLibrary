package xyz.wagyourtail.unimined.mapping.remapper.asm

import io.github.oshai.kotlinlogging.KLogger
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.*
import java.util.jar.Manifest

/**
 * Jar-level fixups that class-level visitors can't cover: manifest contents,
 * `META-INF/services` provider files, signature-file stripping, and the
 * package-private access check. See transform-plan §2.6.
 */
object JarFixups {

    /** `META-INF` signature files — `.SF`, `.RSA`, `.DSA`, `.EC`. */
    fun isSignatureFile(path: String): Boolean {
        if (!path.startsWith("META-INF/") || '/' in path.substring(9)) return false
        val ext = path.substringAfterLast('.', "")
        return ext.equals("SF", true) || ext.equals("RSA", true) ||
                ext.equals("DSA", true) || ext.equals("EC", true)
    }

    /** Manifest attributes whose values are dotted class names. */
    private val CLASS_NAME_ATTRIBUTES = setOf(
        "Main-Class", "Launcher-Agent-Class", "Agent-Class", "Premain-Class",
    )

    /**
     * Remaps dotted class names in manifest attributes and drops signature
     * sections (their digests are invalidated by any rename).
     */
    fun remapManifest(manifest: Manifest, remapper: Remapper): Manifest {
        for ((key, value) in manifest.mainAttributes) {
            val attr = key.toString()
            if (attr in CLASS_NAME_ATTRIBUTES) {
                manifest.mainAttributes.putValue(attr, remapDotted(value.toString(), remapper))
            }
        }
        manifest.entries.values.removeIf { attrs ->
            attrs.keys.any { it.toString().endsWith("-Digest", ignoreCase = true) || it.toString().contains("-Digest-") }
        }
        return manifest
    }

    /** `META-INF/services/com.foo.Bar` → `META-INF/services/com.mapped.Bar` */
    fun remapServiceName(path: String, remapper: Remapper): String =
        "META-INF/services/" + remapDotted(path.removePrefix("META-INF/services/"), remapper)

    /** Service-provider files list one dotted binary class name per line. */
    fun remapServiceContents(bytes: ByteArray, remapper: Remapper): ByteArray =
        bytes.toString(Charsets.UTF_8).lines().joinToString("\n") { line ->
            if (line.isBlank() || line.trimStart().startsWith("#")) line
            else remapDotted(line.trim(), remapper)
        }.toByteArray(Charsets.UTF_8)

    /** `a.b.C$D` → mapped `x.y.C$D` (dots↔slashes, `$` preserved for inners). */
    private fun remapDotted(dotted: String, remapper: Remapper): String {
        val internal = dotted.replace('.', '/')
        return remapper.map(internal).replace('/', '.')
    }

    /**
     * Per-class data [checkPackageAccess] needs, extracted in the remap
     * worker so the output [ClassNode] itself can be dropped — the
     * streaming path retains these (~bytes per class) instead of nodes.
     */
    class AccessSummary(
        val name: String,
        val access: Int,
        /** `"Mname desc"`/`"Fname desc"` → access */
        val memberAccess: Map<String, Int>,
        val classRefs: Set<String>,
        /** `(owner, "Mname desc"/"Fname desc", isMethod)` */
        val memberRefs: List<Triple<String, String, Boolean>>,
    )

    /** Build an [AccessSummary] for a remapped output node. */
    fun summarize(node: ClassNode): AccessSummary {
        val members = HashMap<String, Int>(node.methods.size + node.fields.size)
        for (m in node.methods) members["M${m.name} ${m.desc}"] = m.access
        for (f in node.fields) members["F${f.name} ${f.desc}"] = f.access
        return AccessSummary(node.name, node.access, members, referencedClasses(node), referencedMembers(node))
    }

    /** package-private = none of public/protected/private set */
    private fun isPackagePrivate(acc: Int) =
        acc and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED) == 0

    private fun pkg(name: String) = name.substringBeforeLast('/', "")

    /**
     * Cross-package package-private violations found by [checkPackageAccess]
     * — `brokenClasses`/`brokenMembers` map target → accessor names.
     */
    class AccessViolations(
        val brokenClasses: Map<String, Set<String>>,
        /** `"owner memberKey"` → accessors */
        val brokenMembers: Map<String, Set<String>>,
    ) {
        val isEmpty get() = brokenClasses.isEmpty() && brokenMembers.isEmpty()

        /** member keys per class — for flag patching */
        val memberPatchesByClass: Map<String, Set<String>> by lazy {
            brokenMembers.keys.groupBy { it.substringBefore(' ') }
                .mapValues { it.value.mapTo(mutableSetOf()) { k -> k.substringAfter(' ') } }
        }
    }

    /**
     * Detect (and log) package-private violations over [AccessSummary]s.
     * [PackageAccess.WARN] only logs; for [PackageAccess.FIX] the returned
     * [AccessViolations] describes what to widen — the caller applies it to
     * retained nodes or to serialized bytes via [patchClassAccess].
     */
    fun checkPackageAccess(out: List<AccessSummary>, options: Options, logger: KLogger): AccessViolations {
        // mappedName → access, built post-transform so metadata fixes are visible
        val access = HashMap<String, Int>(out.size)
        // owner → (name+desc) → access — for member-level package-private checks
        val memberAccess = HashMap<String, Map<String, Int>>(out.size)
        for (s in out) {
            access[s.name] = s.access
            memberAccess[s.name] = s.memberAccess
        }

        fun isAccessible(target: String): Boolean {
            val acc = access[target] ?: return true // not in the output set — assume fine
            return acc and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE) != 0
        }

        val broken = LinkedHashMap<String, MutableSet<String>>() // target → accessors
        val brokenMembers = LinkedHashMap<String, MutableSet<String>>() // "owner member" → accessors
        for (accessor in out) {
            for (target in accessor.classRefs) {
                if (target == accessor.name || isAccessible(target)) continue
                if (pkg(target) != pkg(accessor.name)) {
                    broken.getOrPut(target, ::mutableSetOf).add(accessor.name)
                }
            }
            // member refs: package-private member on a class that moved packages breaks
            for ((owner, member, _) in accessor.memberRefs) {
                if (owner == accessor.name) continue
                val acc = memberAccess[owner]?.get(member) ?: continue // member not in output set
                if (isPackagePrivate(acc) && pkg(owner) != pkg(accessor.name)) {
                    brokenMembers.getOrPut("$owner $member", ::mutableSetOf).add(accessor.name)
                }
            }
        }

        val fix = options.packageAccess == PackageAccess.FIX
        for ((memberRef, accessors) in brokenMembers) {
            if (fix) {
                logger.warn { "packageAccess=FIX: made $memberRef public — accessed package-privately from ${accessors.joinToString()}" }
            } else {
                logger.warn { "Invalid access to package-private member $memberRef after remapping, from ${accessors.joinToString()}" }
            }
        }
        for ((target, accessors) in broken) {
            if (fix) {
                logger.warn { "packageAccess=FIX: made $target public — accessed package-privately from ${accessors.joinToString()}" }
            } else {
                logger.warn { "Invalid access to package-private $target after remapping, from ${accessors.joinToString()}" }
            }
        }
        return AccessViolations(broken, brokenMembers)
    }

    /**
     * Pass-3 package-private access check. Renaming moves classes across
     * packages; a reference to a package-private class that lands in a
     * different mapped package silently breaks (`IllegalAccessError`).
     *
     * [PackageAccess.WARN] logs; [PackageAccess.FIX] upgrades the target
     * class's access to public instead.
     */
    fun checkPackageAccess(out: List<ClassNode>, remapper: Remapper, options: Options, logger: KLogger) {
        val summaries = out.map(::summarize)
        val violations = checkPackageAccess(summaries, options, logger)
        if (options.packageAccess != PackageAccess.FIX || violations.isEmpty) return
        val byName = out.associateBy { it.name }
        for ((owner, memberKeys) in violations.memberPatchesByClass) {
            val node = byName.getValue(owner)
            for (member in memberKeys) {
                val key = member.substring(1).split(' ')
                if (member.startsWith('M')) {
                    node.methods.firstOrNull { it.name == key[0] && it.desc == key[1] }
                        ?.let { it.access = it.access or Opcodes.ACC_PUBLIC }
                } else {
                    node.fields.firstOrNull { it.name == key[0] && it.desc == key[1] }
                        ?.let { it.access = it.access or Opcodes.ACC_PUBLIC }
                }
            }
        }
        for (target in violations.brokenClasses.keys) {
            byName.getValue(target).access = byName.getValue(target).access or Opcodes.ACC_PUBLIC
        }
    }

    /**
     * Patch access flags in serialized class bytes — the streaming path's
     * [PackageAccess.FIX] application. Parses just the constant pool and
     * member headers (no ASM re-read), ORs `ACC_PUBLIC` into the class
     * access flags and into each member in [memberKeys]
     * (`"Mname desc"`/`"Fname desc"`). Mutates [bytes] in place.
     */
    fun patchClassAccess(bytes: ByteArray, classFlags: Int, memberKeys: Set<String>) {
        fun u2(off: Int) = (bytes[off].toInt() and 0xFF shl 8) or (bytes[off + 1].toInt() and 0xFF)
        fun u4(off: Int) = (u2(off) shl 16) or u2(off + 2)
        fun putU2(off: Int, v: Int) {
            bytes[off] = (v ushr 8).toByte(); bytes[off + 1] = v.toByte()
        }

        var off = 8
        val cpCount = u2(off); off += 2
        val utf8 = arrayOfNulls<String>(cpCount)
        var i = 1
        while (i < cpCount) {
            when (bytes[off].toInt() and 0xFF) {
                1 -> { // Utf8
                    val len = u2(off + 1)
                    utf8[i] = String(bytes, off + 3, len, Charsets.UTF_8)
                    off += 3 + len
                }
                7, 8, 16, 19, 20 -> off += 3                     // Class, String, MethodHandle, Module, Package
                3, 4, 9, 10, 11, 12, 17, 18 -> off += 5          // Int, Float, refs, N&T, Indy, Dyn
                5, 6 -> { off += 9; i++ }                        // Long, Double (two slots)
                15 -> off += 4                                   // MethodHandle: refKind u1 + refIndex u2
                else -> throw IllegalArgumentException("bad cp tag ${bytes[off]} at $off")
            }
            i++
        }

        // access_flags / this_class / super_class / interfaces
        putU2(off, u2(off) or classFlags)
        off += 6
        off += 2 * u2(off); off += 2

        fun patchMembers(kind: Char) {
            val count = u2(off); off += 2
            repeat(count) {
                val memberOff = off
                val name = utf8[u2(off + 2)]
                val desc = utf8[u2(off + 4)]
                off += 6
                val attrs = u2(off); off += 2
                repeat(attrs) { off += 6 + u4(off + 2) }
                if (name != null && desc != null && memberKeys.contains("$kind$name $desc")) {
                    putU2(memberOff, u2(memberOff) or Opcodes.ACC_PUBLIC)
                }
            }
        }
        patchMembers('F')
        patchMembers('M')
    }

    /** Internal names referenced by [node]: supertype, interfaces, member types, code refs. */
    private fun referencedClasses(node: ClassNode): Set<String> {
        val refs = LinkedHashSet<String>()
        node.superName?.let { refs.add(it) }
        refs.addAll(node.interfaces)
        fun addType(t: Type) {
            when (t.sort) {
                Type.OBJECT -> refs.add(t.internalName)
                Type.ARRAY -> addType(t.elementType)
            }
        }
        for (f in node.fields) addType(Type.getType(f.desc))
        for (m in node.methods) {
            for (t in Type.getArgumentTypes(m.desc)) addType(t)
            addType(Type.getReturnType(m.desc))
            m.exceptions?.forEach { refs.add(it) }
            var insn: AbstractInsnNode? = m.instructions?.first
            while (insn != null) {
                when (insn) {
                    is TypeInsnNode -> {
                        // desc is an internal name, or an array descriptor for multi-ANEWARRAY
                        if (insn.desc.startsWith('[')) addType(Type.getType(insn.desc)) else refs.add(insn.desc)
                    }
                    is FieldInsnNode -> refs.add(insn.owner)
                    is MethodInsnNode -> refs.add(insn.owner)
                    is InvokeDynamicInsnNode -> {
                        refs.add(insn.bsm.owner)
                        insn.bsmArgs.forEach {
                            if (it is Handle) refs.add(it.owner)
                            if (it is Type) addType(it)
                        }
                    }
                    is LdcInsnNode -> if (insn.cst is Type) addType(insn.cst as Type)
                }
                insn = insn.next
            }
        }
        return refs
    }

    /** Member references made by [node]: `(owner, "Mname desc"/"Fname desc", isMethod)`. */
    private fun referencedMembers(node: ClassNode): List<Triple<String, String, Boolean>> {
        val refs = ArrayList<Triple<String, String, Boolean>>()
        fun handle(h: Handle) = refs.add(Triple(h.owner, "${if (h.tag <= Opcodes.H_PUTSTATIC) 'F' else 'M'}${h.name} ${h.desc}", h.tag > Opcodes.H_PUTSTATIC))
        for (m in node.methods) {
            var insn: AbstractInsnNode? = m.instructions?.first
            while (insn != null) {
                when (insn) {
                    is FieldInsnNode -> refs.add(Triple(insn.owner, "F${insn.name} ${insn.desc}", false))
                    is MethodInsnNode -> refs.add(Triple(insn.owner, "M${insn.name} ${insn.desc}", true))
                    is InvokeDynamicInsnNode -> {
                        handle(insn.bsm)
                        insn.bsmArgs.forEach { if (it is Handle) handle(it) }
                    }
                    is LdcInsnNode -> if (insn.cst is Handle) handle(insn.cst as Handle)
                }
                insn = insn.next
            }
        }
        return refs
    }


    /**
     * Remap access-widener / access-transformer file contents — class, method and
     * field targets inside the file must follow the same name mapping as the
     * bytecode they describe, or the runtime AW/AT silently misses. Handles the
     * line grammars of both formats; unknown lines pass through untouched.
     */
    fun remapAccessFile(name: String, bytes: ByteArray, remapper: Remapper, targetNs: String): ByteArray {
        val isAw = name.endsWith(".accesswidener", ignoreCase = true)
        val text = bytes.toString(Charsets.UTF_8)
        val out = StringBuilder()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> out.append(line).append('\n')
                trimmed.startsWith("accessWidener") -> {
                    // header: "accessWidener vN <ns>" — ns follows the mapping target
                    val parts = trimmed.split(Regex("\\s+"))
                    out.append(if (parts.size >= 3) "accessWidener ${parts[1]} $targetNs" else line).append('\n')
                }
                isAw -> out.append(remapAwLine(trimmed, remapper)).append('\n')
                else -> out.append(remapAtLine(trimmed, remapper)).append('\n')
            }
        }
        return out.toString().toByteArray()
    }

    /** `<directive> class|method|field <owner> [name [desc]]` */
    private fun remapAwLine(line: String, remapper: Remapper): String {
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 3) return line
        val kind = parts[1]
        val owner = remapper.map(parts[2])
        return when (kind) {
            "class" -> "${parts[0]} class $owner"
            "method" -> {
                if (parts.size < 5) return line
                val name = parts[3]
                val desc = parts[4]
                val n = if (name == "*" || name == "all" || name.startsWith("<")) name
                        else remapper.mapMethodName(parts[2], name, desc)
                "${parts[0]} method $owner $n ${remapper.mapMethodDesc(desc)}"
            }
            "field" -> {
                if (parts.size < 5) return line
                val name = parts[3]
                val desc = parts[4]
                val n = if (name == "*" || name == "all") name
                        else remapper.mapFieldName(parts[2], name, desc)
                "${parts[0]} field $owner $n ${remapper.mapDesc(desc)}"
            }
            else -> line
        }
    }

    /** `<access>[-f] <owner>[ <member>][ <desc>]` — owner is dot-separated binary name. */
    private fun remapAtLine(line: String, remapper: Remapper): String {
        val comment = line.indexOf('#').let { if (it >= 0) line.substring(it) else "" }
        val body = if (comment.isNotEmpty()) line.substring(0, line.indexOf('#')).trim() else line
        val parts = body.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size < 2) return line
        val owner = parts[1].replace('.', '/')
        if (parts.size == 2) return "${parts[0]} ${remapper.map(owner)}" + if (comment.isNotEmpty()) " $comment" else ""
        // member target: name, or name(desc)
        val member = parts[2]
        val paren = member.indexOf('(')
        return if (paren >= 0) {
            val mname = member.substring(0, paren)
            val desc = member.substring(paren)
            val n = remapper.mapMethodName(owner, mname, desc)
            "${parts[0]} ${remapper.map(owner)} $n${remapper.mapMethodDesc(desc)}" + if (comment.isNotEmpty()) " $comment" else ""
        } else {
            val desc = if (parts.size > 3) parts[3] else null
            val n = remapper.mapFieldName(owner, member, desc)
            "${parts[0]} ${remapper.map(owner)} $n" + (desc?.let { " ${remapper.mapDesc(it)}" } ?: "") + if (comment.isNotEmpty()) " $comment" else ""
        }
    }

}
