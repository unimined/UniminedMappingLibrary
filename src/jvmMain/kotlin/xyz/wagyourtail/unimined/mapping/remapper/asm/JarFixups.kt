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
     * Pass-3 package-private access check. Renaming moves classes across
     * packages; a reference to a package-private class that lands in a
     * different mapped package silently breaks (`IllegalAccessError`).
     *
     * [PackageAccess.WARN] logs; [PackageAccess.FIX] upgrades the target
     * class's access to public instead.
     */
    fun checkPackageAccess(out: List<ClassNode>, remapper: Remapper, options: Options, logger: KLogger) {
        // mappedName → access, built post-transform so metadata fixes are visible
        val access = HashMap<String, Int>(out.size)
        // owner → (name+desc) → access — for member-level package-private checks
        val memberAccess = HashMap<String, MutableMap<String, Int>>(out.size)
        val byName = HashMap<String, ClassNode>(out.size)
        for (node in out) {
            access[node.name] = node.access
            byName[node.name] = node
            val members = memberAccess.getOrPut(node.name, ::HashMap)
            for (m in node.methods) members["M${m.name} ${m.desc}"] = m.access
            for (f in node.fields) members["F${f.name} ${f.desc}"] = f.access
        }

        /** package-private = none of public/protected/private set */
        fun isPackagePrivate(acc: Int) = acc and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED) == 0

        fun isAccessible(target: String): Boolean {
            val acc = access[target] ?: return true // not in the output set — assume fine
            return acc and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE) != 0
        }

        fun pkg(name: String) = name.substringBeforeLast('/', "")

        val broken = LinkedHashMap<String, MutableSet<String>>() // target → accessors
        val brokenMembers = LinkedHashMap<String, MutableSet<String>>() // "owner member" → accessors
        for (accessor in out) {
            for (target in referencedClasses(accessor)) {
                if (target == accessor.name || isAccessible(target)) continue
                if (pkg(target) != pkg(accessor.name)) {
                    broken.getOrPut(target, ::mutableSetOf).add(accessor.name)
                }
            }
            // member refs: package-private member on a class that moved packages breaks
            for ((owner, member, isMethod) in referencedMembers(accessor)) {
                if (owner == accessor.name) continue
                val acc = memberAccess[owner]?.get(member) ?: continue // member not in output set
                if (isPackagePrivate(acc) && pkg(owner) != pkg(accessor.name)) {
                    brokenMembers.getOrPut("$owner $member", ::mutableSetOf).add(accessor.name)
                }
            }
        }

        for ((memberRef, accessors) in brokenMembers) {
            val owner = memberRef.substringBefore(' ')
            val member = memberRef.substringAfter(' ')
            val ownerNode = byName.getValue(owner)
            if (options.packageAccess == PackageAccess.FIX) {
                val key = member.substring(1).split(' ')
                if (member.startsWith('M')) {
                    ownerNode.methods.firstOrNull { it.name == key[0] && it.desc == key[1] }
                        ?.let { it.access = it.access or Opcodes.ACC_PUBLIC }
                } else {
                    ownerNode.fields.firstOrNull { it.name == key[0] && it.desc == key[1] }
                        ?.let { it.access = it.access or Opcodes.ACC_PUBLIC }
                }
                logger.warn { "packageAccess=FIX: made $memberRef public — accessed package-privately from ${accessors.joinToString()}" }
            } else {
                logger.warn { "Invalid access to package-private member $memberRef after remapping, from ${accessors.joinToString()}" }
            }
        }

        for ((target, accessors) in broken) {
            if (options.packageAccess == PackageAccess.FIX) {
                byName.getValue(target).access = byName.getValue(target).access or Opcodes.ACC_PUBLIC
                logger.warn { "packageAccess=FIX: made $target public — accessed package-privately from ${accessors.joinToString()}" }
            } else {
                logger.warn { "Invalid access to package-private $target after remapping, from ${accessors.joinToString()}" }
            }
        }
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
