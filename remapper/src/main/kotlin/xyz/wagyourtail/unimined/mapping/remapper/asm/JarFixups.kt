package xyz.wagyourtail.unimined.mapping.remapper.asm

import io.github.oshai.kotlinlogging.KLogger
import org.objectweb.asm.Handle
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.*
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.at.ATReader
import xyz.wagyourtail.unimined.mapping.formats.at.ATWriter
import xyz.wagyourtail.unimined.mapping.formats.aw.AWReader
import xyz.wagyourtail.unimined.mapping.formats.aw.AWWriter
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
        /** superclass internal name (post-remap), null for java.lang.Object/interfaces */
        val superName: String?,
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
        return AccessSummary(node.name, node.access, node.superName, members, referencedClasses(node), referencedMembers(node))
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

        // protected members are package-accessible too — but also reachable
        // from subclasses in other packages, so those accessors are legal
        val supers = HashMap<String, String?>(out.size)
        for (s in out) supers[s.name] = s.superName
        fun isSubclassOf(accessor: String, owner: String): Boolean {
            var cur: String? = accessor
            var hops = 0
            while (cur != null && cur != owner && hops++ < 64) cur = supers[cur]
            return cur == owner
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
                if (pkg(owner) == pkg(accessor.name)) continue
                if (isPackagePrivate(acc) ||
                    (acc and Opcodes.ACC_PROTECTED != 0 && !isSubclassOf(accessor.name, owner))
                ) {
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

        // access_flags / this_class / super_class / interfaces — replace
        // the visibility bits, don't OR onto them (protected|public is illegal);
        // classFlags == 0 means member-only patch — leave class flags untouched
        if (classFlags != 0) {
            putU2(off, (u2(off) and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or classFlags)
        }
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
                    putU2(
                        memberOff,
                        (u2(memberOff) and (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()) or Opcodes.ACC_PUBLIC
                    )
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
     * bytecode they describe, or the runtime AW/AT silently misses. Rounds the
     * file through the format readers (`ATReader`/`AWReader`) and writers so
     * comments, wildcards, desc-less field targets and blank lines survive —
     * [ATWriter] also emits the `.`-separated class names FML's AT grammar
     * requires.
     */
    fun remapAccessFile(
        name: String,
        bytes: ByteArray,
        tree: MappingTree,
        sourceNs: Namespace,
        targetNs: Namespace
    ): ByteArray {
        val text = bytes.toString(Charsets.UTF_8)
        val out = StringBuilder()
        if (name.endsWith(".accesswidener", ignoreCase = true)) {
            val mappings = AWReader.readData(StringCharReader(text))
            AWWriter.writeData(AWWriter.remapMappings(mappings, tree, targetNs)) { out.append(it) }
        } else {
            val items = ATReader.readData(StringCharReader(text), leinient = true)
            ATWriter.writeData(ATWriter.remapMappings(items, tree, sourceNs, targetNs)) { out.append(it) }
        }
        return out.toString().toByteArray()
    }


    /**
     * Proguard mangles constructor parameter-annotation attributes:
     * it strips the synthetic leading params (enum `String,int`;
     * inner-class `this$0`) from the descriptor view but the emitted
     * `Runtime[In]VisibleParameterAnnotations` counts/indices still
     * reflect them, so annotation indices overflow the real param
     * list. Port of 1.x `FixParamAnnotations` to ClassNode form —
     * rewrites the annotable counts and drops the phantom leading
     * entries on `<init>`s of enum/inner classes.
     */
    fun fixParamAnnotations(cn: ClassNode) {
        val synthetic = syntheticParams(cn) ?: return
        for (m in cn.methods) {
            if (m.name != "<init>") continue
            val realParams = org.objectweb.asm.Type.getArgumentTypes(m.desc).size - synthetic.size
            fixAnnotationArray(m, true, realParams)
            fixAnnotationArray(m, false, realParams)
        }
    }

    /** Synthetic leading ctor params, or null when none expected
     *  (not enum/inner). Mirrors 1.x `expectedSynthetic`. */
    private fun syntheticParams(cn: ClassNode): List<String>? {
        if (cn.access and Opcodes.ACC_ENUM != 0)
            return listOf("Ljava/lang/String;", "I")
        val info = cn.innerClasses?.firstOrNull { it.name == cn.name }
            ?: return null
        if (info.access and (Opcodes.ACC_STATIC or Opcodes.ACC_INTERFACE) != 0)
            return null
        if (info.innerName == null) return null // anonymous
        if (info.outerName == null) {
            val idx = cn.name.lastIndexOf('$')
            if (idx == -1) return null
            return listOf("L${cn.name.substring(0, idx)};")
        }
        return listOf("L${info.outerName};")
    }

    private fun fixAnnotationArray(m: MethodNode, visible: Boolean, realParams: Int) {
        val count = if (visible) m.visibleAnnotableParameterCount
        else m.invisibleAnnotableParameterCount
        val anns = if (visible) m.visibleParameterAnnotations
        else m.invisibleParameterAnnotations ?: return
        val offset = count - realParams
        if (offset <= 0 || anns == null) return
        // proguard's phantom leading entries — shift the real ones left
        for (i in 0 until anns.size - offset) {
            anns[i] = anns[i + offset]
        }
        for (i in anns.size - offset until anns.size) {
            anns[i] = null
        }
        if (visible) m.visibleAnnotableParameterCount = realParams
        else m.invisibleAnnotableParameterCount = realParams
    }

    /**
     * Rebuild missing `InnerClasses` attributes for named inner
     * classes — old/proguard jars list `Foo$Bar` siblings without the
     * attribute, so post-merge/remap the parent<->child linkage is
     * broken. Port of 1.x `FixInnerClasses` (set-level: needs sibling
     * visibility): for each top-level `Foo`, named `Foo$X` children
     * (anonymous `$N` skipped) get `InnerClasses` entries with
     * nearest named parent, added to BOTH sides, `ACC_STATIC` forced.
     */
    fun fixInnerClasses(classes: List<Pair<String, ClassNode>>) {
        val anon = Regex("\\$\\d+")
        for ((_, parent) in classes) {
            val simpleName = parent.name.substringAfterLast('/')
            if ('$' in simpleName) continue
            val pkg = parent.name.substringBeforeLast('/', "")
                .let { if (it.isEmpty()) "" else "$it/" }
            val children = classes.map { it.second }.filter {
                val s = it.name.substringAfterLast('/')
                s.startsWith("$simpleName$") && !anon.containsMatchIn(s)
            }
            if (children.isEmpty()) continue
            val childSimples = children.map { it.name.substringAfterLast('/') }.toSet()
            val nodes = children.map { child ->
                val childSimple = child.name.substringAfterLast('/')
                var simpleParent = childSimple.substringBeforeLast('$')
                while (simpleParent !in childSimples && simpleParent != simpleName)
                    simpleParent = simpleParent.substringBeforeLast('$')
                InnerClassNode(
                    child.name, "$pkg$simpleParent",
                    childSimple.removePrefix("$simpleParent$"),
                    child.access or Opcodes.ACC_STATIC
                )
            }
            fun merge(cn: ClassNode) {
                if (cn.innerClasses == null) cn.innerClasses = mutableListOf()
                val existing = cn.innerClasses.map { it.name }.toSet()
                cn.innerClasses.addAll(nodes.filter { it.name !in existing })
            }
            merge(parent)
            children.forEach(::merge)
        }
    }
}
