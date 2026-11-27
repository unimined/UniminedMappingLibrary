package xyz.wagyourtail.unimined.mapping.remapper.nil

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Handle
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AnnotationRemapper
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.MethodRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.VarInsnNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.SourceInterpreter
import org.objectweb.asm.tree.analysis.SourceValue
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.NamespaceRemapper
import xyz.wagyourtail.unimined.mapping.inheritance.InheritanceTree
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.streams.asSequence

/**
 * Tracked-usage scan producing a NIL [NilMappings] — the port of
 * NilGradle's `TrackingLorenzRemapper` + `NilGradleClassRemapper` onto
 * UML's mapping model.
 *
 * The mod's compiled classes (in the dev/compile namespace) are walked
 * through a discarding ASM [ClassRemapper] whose [Remapper] facade
 * records every class/member reference it is consulted for — the exact
 * set of references a runtime remap would resolve, with none of the
 * hand-enumeration a bespoke visitor would need. On top of that pass,
 * the string channels a bytecode remap never sees are scanned
 * explicitly:
 *
 *  - `@Patch.Class`/`@Patch.Method` annotation values (mini-transformer
 *    patch targets — never rewritten by anything, so they must resolve
 *    through the json at runtime)
 *  - `ldc` string constants — class-name and descriptor literals
 *    (`remapType`/`remapMethodDesc` single-arg forms) and the
 *    consecutive owner/name/desc triples `InsnBuilder`/`PatchContext`
 *    DSL calls take (`remapField`/`remapMethod` shapes)
 *
 * Member entries emit keyed by the *referenced* owner — `MiniUtils`'s
 * runtime lookup is flat, with no inheritance walk — resolved through
 * the scan classpath's inheritance so a member declared on a supertype
 * still lands under the owner the mod references. Mod classes whose
 * methods override game methods get entries under their own (identity)
 * class entry for the same reason.
 *
 * Widening analysis ([NilMappings.widenClasses]/[widenMethods]/
 * [widenFields]) checks runtime accessibility of every referenced
 * game class/member from the referencing mod class. Access truth comes
 * from [accessInheritance] — a scan of the *production* (official)
 * artifact — since dev artifacts are typically access-widened already;
 * falls back to [inheritance] with the documented under-reporting risk.
 * Entries emit in the target namespace, matching NIL's live-classload
 * comparison.
 */
class NilUsageScanner(
    val tree: MappingTree,
    val remapper: NamespaceRemapper,
    val inheritance: InheritanceTree,
    val accessInheritance: InheritanceTree? = null,
    val logger: (String) -> Unit = {},
) {

    data class MemberRef(val owner: String, val name: String, val desc: String)

    private class Resolution(val declaringOwner: String, val targetName: String)

    private val from = remapper.from
    private val to = remapper.to

    private val result = NilMappings()

    private val usedClasses = sortedSetOf<String>()
    private val usedMethods = sortedSetOf<MemberRef>(compareBy({ it.owner }, { it.name }, { it.desc }))
    private val usedFields = sortedSetOf<MemberRef>(compareBy({ it.owner }, { it.name }, { it.desc }))

    private val methodResolutionCache = HashMap<MemberRef, Resolution?>()
    private val fieldResolutionCache = HashMap<MemberRef, Resolution?>()

    // ------------------------------------------------------------------
    // scanning entry points
    // ------------------------------------------------------------------

    /** scan one `.class` file's bytes */
    fun scanClass(bytes: ByteArray) {
        val reader = ClassReader(bytes)
        // dataflow pre-pass — `remap*` call arguments rarely survive as
        // consecutive literals (mods hoist owners into locals and chain
        // `.replace` calls), so the ldc state machine alone under-
        // captures; constant-propagation binds them properly
        analyzeRemapCallArguments(reader)
        // the ClassWriter exists only to give the remapper chain a
        // downstream — output is discarded, tracking is the point
        reader.accept(ScanClassRemapper(ClassWriter(0)), 0)
    }

    /** scan every `.class` in a jar or exploded classes directory */
    fun scanJarOrDir(path: Path) {
        if (Files.isDirectory(path)) {
            Files.walk(path).use { stream ->
                stream.asSequence()
                    .filter { Files.isRegularFile(it) && it.toString().endsWith(".class") }
                    .forEach { scanClass(Files.readAllBytes(it)) }
            }
        } else {
            ZipFile(path.toFile()).use { zip ->
                for (entry in zip.entries().asSequence()) {
                    if (!entry.isDirectory && entry.name.endsWith(".class") &&
                        !entry.name.startsWith("META-INF/versions")
                    ) {
                        zip.getInputStream(entry).use { scanClass(it.readBytes()) }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // remap* call argument analysis (dataflow pre-pass)
    // ------------------------------------------------------------------
    //
    // MiniTransformer/MiniUtils `remapType`/`remapMethod`/`remapField`/
    // `remapMethodDesc`/`remapFieldDesc` invocations carry the string
    // references a runtime lookup resolves through the json. Their
    // arguments almost never sit in the bytecode as consecutive `ldc`
    // constants — Omnilook's shape is typical:
    //
    //     String owner = "net/minecraft/client/options/GameOptions";
    //     remapField(owner, "keyBindings", "[Lnet/.../KeyBinding;")
    //     remapMethod(lcpe.replace('/', '.'), "updateLocalPlayerCamera", "(FF)V")
    //
    // A `SourceInterpreter` analysis propagates each value's producing
    // instructions through locals, method calls and string concatenation
    // machinery, so the literal candidates for every argument position
    // are recoverable at the call site. Binding is then shape-based and
    // resolution-gated: candidate owners must name mapped classes,
    // member rows must exist — over-approximation costs only harmless
    // extra entries, never wrong ones.

    private enum class RemapCallKind(val methodName: String, val argCount: Int) {
        TYPE("remapType", 1),
        METHOD("remapMethod", 3),
        FIELD("remapField", 3),
        METHOD_DESC("remapMethodDesc", 1),
        FIELD_DESC("remapFieldDesc", 1);

        companion object {
            private val byName = entries.associateBy { it.methodName }
            fun match(insn: MethodInsnNode): RemapCallKind? {
                val kind = byName[insn.name] ?: return null
                // the trailing String parameters are the interesting
                // args — instance forms are all-String, the MiniUtils
                // statics prepend the mapping-set Optional
                if (trailingStringCount(insn.desc) < kind.argCount) return null
                return kind
            }

            private fun trailingStringCount(desc: String): Int {
                val stringType = "Ljava/lang/String;"
                val params = desc.substring(1, desc.indexOf(')'))
                var count = 0
                var i = params.length
                while (i >= stringType.length && params.substring(i - stringType.length, i) == stringType) {
                    count++
                    i -= stringType.length
                }
                return count
            }

        }

    }

    /**
     * `SourceInterpreter` sources copied values at the copy instruction
     * itself — a value hoisted into a local traces to the `aload`, not
     * the original producer. Expansion walks the copy chain: a load's
     * real producers are the sources of that local slot in the frame at
     * the load; a store's are the top-of-stack sources at the store.
     * Method-call merges (`replace`, concat machinery) already carry
     * their inputs in `insns`, so they join the walk transparently.
     * The seen-set terminates store/load cycles loops would otherwise
     * spin on.
     */
    private fun expandSources(
        value: SourceValue,
        frames: Array<out org.objectweb.asm.tree.analysis.Frame<SourceValue>?>,
        insnIndex: java.util.IdentityHashMap<AbstractInsnNode, Int>,
    ): Set<AbstractInsnNode> {
        val result = mutableSetOf<AbstractInsnNode>()
        val seen = mutableSetOf<AbstractInsnNode>()
        val queue = ArrayDeque<AbstractInsnNode>()
        queue.addAll(value.insns)
        while (queue.isNotEmpty()) {
            val insn = queue.removeFirst()
            if (!seen.add(insn)) continue
            val idx = insnIndex[insn]
            if (insn is VarInsnNode && idx != null) {
                val frame = frames.getOrNull(idx)
                val next = when (insn.opcode) {
                    in Opcodes.ILOAD..Opcodes.ALOAD ->
                        (frame?.getLocal(insn.`var`) as? SourceValue)?.insns
                    in Opcodes.ISTORE..Opcodes.ASTORE ->
                        frame?.takeIf { it.stackSize > 0 }
                            ?.let { it.getStack(it.stackSize - 1) as? SourceValue }?.insns
                    else -> null
                }
                if (next != null) {
                    queue.addAll(next)
                    continue
                }
            }
            if (insn is MethodInsnNode && idx != null) {
                // invocation results source at the call — the interesting
                // producers are the receiver/argument values feeding it
                // (`receiver.replace(...)`, concat machinery)
                val frame = frames.getOrNull(idx)
                if (frame != null) {
                    val argSlots = org.objectweb.asm.Type.getArgumentTypes(insn.desc)
                        .sumOf { it.size } + if (insn.opcode == Opcodes.INVOKESTATIC) 0 else 1
                    if (frame.stackSize >= argSlots) {
                        var added = false
                        for (slot in frame.stackSize - argSlots until frame.stackSize) {
                            val input = frame.getStack(slot) as? SourceValue
                            if (input != null) {
                                queue.addAll(input.insns)
                                added = true
                            }
                        }
                        if (added) continue
                    }
                }
            }
            if (insn is org.objectweb.asm.tree.InvokeDynamicInsnNode && idx != null) {
                val frame = frames.getOrNull(idx)
                if (frame != null) {
                    val argSlots = org.objectweb.asm.Type.getArgumentTypes(insn.desc)
                        .sumOf { it.size }
                    if (frame.stackSize >= argSlots && argSlots > 0) {
                        for (slot in frame.stackSize - argSlots until frame.stackSize) {
                            val input = frame.getStack(slot) as? SourceValue
                            if (input != null) queue.addAll(input.insns)
                        }
                        continue
                    }
                }
            }
            result += insn
        }
        return result
    }

    private fun analyzeRemapCallArguments(reader: ClassReader) {
        val node = ClassNode(Opcodes.ASM9)
        reader.accept(node, ClassReader.SKIP_FRAMES)
        for (method in node.methods) {
            if (method.instructions.size() == 0) continue
            val frames = try {
                Analyzer(SourceInterpreter()).analyze(node.name, method)
            } catch (e: Exception) {
                // unanalyzable methods lose only the dataflow binding —
                // the ldc/annotation scans still apply
                continue
            }
            val insns = method.instructions.toArray()
            val insnIndex = java.util.IdentityHashMap<AbstractInsnNode, Int>()
            for ((i, insn) in insns.withIndex()) insnIndex[insn] = i
            for ((i, insn) in insns.withIndex()) {
                if (insn !is MethodInsnNode) continue
                val kind = RemapCallKind.match(insn) ?: continue
                val frame = frames[i] ?: continue
                if (frame.stackSize < kind.argCount) continue
                val candidates = (0 until kind.argCount).map { arg ->
                    val value = frame.getStack(frame.stackSize - kind.argCount + arg)
                        as? SourceValue
                    value?.let {
                        expandSources(it, frames, insnIndex)
                            .mapNotNull { src -> (src as? LdcInsnNode)?.cst as? String }
                            .toSet()
                    } ?: emptySet()
                }
                bindRemapCall(kind, candidates)
            }
        }
    }

    /** owner-shape classification — slash-form internal names pass
     *  through; dotted names normalize to slash (the runtime lookup
     *  keys slash-form json regardless of what the mod passes, so the
     *  author's intent is the only sensible binding) */
    private fun ownerCandidate(raw: String): String? {
        if (raw.isEmpty() || raw.startsWith("(") || raw.startsWith("[") || raw.startsWith("L")) return null
        return when {
            '/' in raw -> raw
            '.' in raw -> raw.replace('.', '/')
            else -> null
        }
    }

    private fun bindRemapCall(kind: RemapCallKind, candidates: List<Set<String>>) {
        when (kind) {
            RemapCallKind.TYPE -> {
                for (c in candidates[0]) {
                    ownerCandidate(c)?.takeIf { inTree(it) }?.let { trackClass(it) }
                }
            }
            RemapCallKind.METHOD_DESC, RemapCallKind.FIELD_DESC -> {
                for (c in candidates[0]) {
                    if ((kind == RemapCallKind.METHOD_DESC && tryParseMethodDescStatic(c)) ||
                        (kind == RemapCallKind.FIELD_DESC && tryParseFieldDescStatic(c))
                    ) {
                        extractObjectTypesStatic(c)?.let { types ->
                            for (t in types) trackClass(t)
                        }
                    }
                }
            }
            RemapCallKind.METHOD, RemapCallKind.FIELD -> {
                val owners = candidates[0].mapNotNull { ownerCandidate(it) }.toSet()
                val descs = candidates[2].filter {
                    if (kind == RemapCallKind.METHOD) tryParseMethodDescStatic(it)
                    else tryParseFieldDescStatic(it)
                }.toSet()
                // member names never carry `/`, `(`, `;` or `.` — class
                // and desc literals leaking into the name position are
                // not names
                val names = candidates[1].filter {
                    it.isNotEmpty() && '/' !in it && '(' !in it && ';' !in it && '.' !in it && '[' !in it
                }.toSet()
                if (owners.isEmpty() || descs.isEmpty()) return
                if (owners.size * descs.size * maxOf(names.size, 1) > 24) {
                    logger("remap* call with ${owners.size}x${names.size}x${descs.size} " +
                            "owner/name/desc candidates — too ambiguous to bind, skipping")
                    return
                }
                if (names.isEmpty()) {
                    // fully-computed member name (`"prev" + capitalized`)
                    // — statically unresolvable, but the owner+desc pair
                    // is bound, so emit every same-desc member row the
                    // owner's hierarchy declares. Over-approximation is
                    // safe: extra entries never remap anything incorrectly
                    for (owner in owners) for (desc in descs) {
                        enumerateSameDescMembers(owner, desc, kind == RemapCallKind.METHOD)
                    }
                    return
                }
                for (desc in descs) {
                    for (t in extractObjectTypesStatic(desc).orEmpty()) trackClass(t)
                }
                for (owner in owners) for (name in names) for (desc in descs) {
                    if (kind == RemapCallKind.METHOD) {
                        usedMethods += MemberRef(owner, name, desc)
                    } else {
                        usedFields += MemberRef(owner, name, desc)
                    }
                }
            }
        }
    }

    /** every member row of [owner] or its supertypes whose descriptor
     *  matches — emitted as refs keyed by [owner] (the runtime flat
     *  lookup owner); resolution at emission time drops nonexistent
     *  ones and retargets the names */
    private fun enumerateSameDescMembers(owner: String, desc: String, isMethod: Boolean) {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(owner)
        while (queue.isNotEmpty()) {
            val cls = queue.removeFirst()
            if (!visited.add(cls)) continue
            val rows = tree.getClass(from, internal(cls))
            if (rows != null) {
                if (isMethod) {
                    for (m in rows.methods) {
                        val entry = m.entries[from] ?: continue
                        if (entry.descriptor?.value == desc) {
                            usedMethods += MemberRef(owner, entry.name.value, desc)
                        }
                    }
                } else {
                    for (f in rows.fields) {
                        val entry = f.entries[from] ?: continue
                        if (entry.descriptor?.toString() == desc) {
                            usedFields += MemberRef(owner, entry.name.value, desc)
                        }
                    }
                }
            }
            val info = inheritance.classInfo(internal(cls)) ?: continue
            info.superType?.let { queue.add(it.value) }
            info.interfaces.forEach { queue.add(it.value) }
        }
    }

    /**
     * The accumulated mappings: an entry per used class, member entries
     * resolved and keyed by referenced owner, ancestor closure
     * synthesized, widen block as collected during the scan.
     */
    fun result(): NilMappings {
        for (c in usedClasses) ensureClassEntry(c)
        for (ref in usedMethods) emitMember(ref, true)
        for (ref in usedFields) emitMember(ref, false)
        closeAncestors()
        return result
    }

    private fun emitMember(ref: MemberRef, isMethod: Boolean) {
        val res = resolveMember(ref, isMethod) ?: return
        val entry = ensureClassEntry(ref.owner)
        if (isMethod) {
            val fromSig = ref.name + ref.desc
            val toSig = res.targetName + mapMethodDesc(ref.desc)
            // identity members carry no information — the runtime lookup
            // missing has the same effect (name passes through)
            if (fromSig != toSig) entry.methods[fromSig] = toSig
        } else {
            val fromSig = "${ref.name}:${ref.desc}"
            val toSig = "${res.targetName}:${mapFieldDesc(ref.desc)}"
            if (fromSig != toSig) entry.fields[fromSig] = toSig
        }
    }

    /** every emitted member's owner needs a class row for the runtime's
     *  flat lookup to reach it; every inner entry needs its outer chain
     *  present or lorenz's per-level walk never reaches it */
    private fun closeAncestors() {
        val queue = ArrayDeque(result.classes.keys.toList())
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            val parent = parentOf(name) ?: continue
            if (parent !in result.classes) {
                ensureClassEntry(parent)
                queue.add(parent)
            }
        }
    }

    private fun parentOf(name: String): String? {
        val slash = name.lastIndexOf('/')
        val dollar = name.lastIndexOf('$')
        return if (dollar > slash) name.substring(0, dollar) else null
    }

    private fun ensureClassEntry(devName: String): NilClassEntry =
        result.classes.getOrPut(devName) {
            NilClassEntry(devName, mapClass(devName))
        }

    // ------------------------------------------------------------------
    // mapping helpers
    // ------------------------------------------------------------------

    private fun internal(name: String) = InternalName.unchecked(name)
    private fun unqual(name: String) = UnqualifiedName.unchecked(name)

    private fun mapClass(devName: String): String =
        remapper.mapClass(internal(devName)).value

    private fun mapMethodDesc(desc: String): String =
        remapper.map(MethodDescriptor.read(desc)).value

    private fun mapFieldDesc(desc: String): String =
        remapper.map(FieldDescriptor.read(desc)).toString()

    private fun inTree(devName: String): Boolean =
        tree.getClass(from, internal(devName)) != null

    private fun rowTargetName(owner: String, name: String, desc: String, isMethod: Boolean): String? {
        val cls = tree.getClass(from, internal(owner)) ?: return null
        return if (isMethod) {
            with(tree) {
                cls.getMethod(from, unqual(name), MethodDescriptor.read(desc))
            }?.names?.get(to)?.value
        } else {
            with(tree) {
                cls.getField(from, unqual(name), FieldDescriptor.read(desc))
            }?.names?.get(to)?.value
        }
    }

    /**
     * Inheritance-aware resolution of a member reference — the port of
     * NilGradle's `scanAndMemoizeRemap*`. Rows are declaration evidence:
     * a mapping row for `(name, desc)` exists on a class only where the
     * member is declared. The chain walk collects every distinct target
     * name the ref could bind to; exactly one means consistent renaming,
     * more means an unresolvable conflict (warned + skipped, NilGradle
     * parity), none means the member isn't mapped anywhere and the
     * runtime pass-through is already correct.
     */
    private fun resolveMember(ref: MemberRef, isMethod: Boolean): Resolution? {
        if (ref.name.startsWith("<")) return null
        val cache = if (isMethod) methodResolutionCache else fieldResolutionCache
        if (cache.containsKey(ref)) return cache[ref]
        val resolved = resolveMemberUncached(ref, isMethod)
        cache[ref] = resolved
        return resolved
    }

    private fun resolveMemberUncached(ref: MemberRef, isMethod: Boolean): Resolution? {
        val candidates = LinkedHashMap<String, String>()
        // direct row first — the common case (owner declares the member);
        // checked outside the inheritance walk so owners missing from the
        // scan classpath still resolve their own rows
        rowTargetName(ref.owner, ref.name, ref.desc, isMethod)?.let {
            candidates[ref.owner] = it
        }
        val visited = mutableSetOf(ref.owner)
        val queue = ArrayDeque<String>()
        fun enqueueSupers(cls: String) {
            val info = inheritance.classInfo(internal(cls)) ?: return
            info.superType?.let { if (it.value !in visited) queue.add(it.value) }
            // fields can't inherit through interfaces (interface fields
            // are static finals referenced by their declaring owner)
            if (isMethod) {
                for (iface in info.interfaces) {
                    if (iface.value !in visited) queue.add(iface.value)
                }
            }
        }
        enqueueSupers(ref.owner)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (!visited.add(cur)) continue
            rowTargetName(cur, ref.name, ref.desc, isMethod)?.let {
                candidates.putIfAbsent(cur, it)
            }
            enqueueSupers(cur)
        }
        val distinct = candidates.values.distinct()
        return when {
            distinct.size > 1 -> {
                logger("unresolvable conflict: ${ref.owner}.${ref.name}${ref.desc} has multiple target names " +
                        "across the hierarchy (${candidates.entries.joinToString { "${it.key}→${it.value}" }}) — skipping")
                null
            }
            distinct.isEmpty() -> null
            else -> Resolution(candidates.keys.first(), distinct[0])
        }
    }

    // ------------------------------------------------------------------
    // widen analysis
    // ------------------------------------------------------------------

    /** per-scanned-class context for access analysis — mod classes are
     *  never in the mapping tree, so their names are namespace-stable */
    private inner class ScanContext(val className: String, superName: String?) {
        val classPackage: String = className.substringBeforeLast('/', "")
        val superChainTarget: Set<String> = run {
            val chain = sortedSetOf<String>()
            val visited = mutableSetOf<String>()
            val queue = ArrayDeque<String>()
            superName?.let(queue::add)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                if (!visited.add(cur)) continue
                chain.add(mapClass(cur))
                inheritance.classInfo(internal(cur))?.superType?.value?.let(queue::add)
            }
            chain
        }
    }

    private fun accessible(ownerTarget: String, access: Int, isMember: Boolean, ctx: ScanContext): Boolean {
        if (access and Opcodes.ACC_PUBLIC != 0) return true
        if (access and Opcodes.ACC_PRIVATE != 0) return ownerTarget == ctx.className
        val pkg = ownerTarget.substringBeforeLast('/', "")
        if (access and Opcodes.ACC_PROTECTED != 0) {
            if (pkg == ctx.classPackage) return true
            if (isMember && ownerTarget in ctx.superChainTarget) return true
            return ownerTarget == ctx.className
        }
        if (pkg == ctx.classPackage) return true
        return ownerTarget == ctx.className
    }

    private fun widenClassCheck(ownerDev: String, ctx: ScanContext) {
        if (!inTree(ownerDev)) return
        val accTree = accessInheritance ?: inheritance
        val target = mapClass(ownerDev)
        val lookup = if (accessInheritance != null) target else ownerDev
        val info = accTree.classInfo(internal(lookup)) ?: return
        if (!accessible(target, info.access, false, ctx)) {
            result.widenClasses += target
        }
    }

    private val objectTypeRegex = Regex("L([\\w/$]+);")

    private fun widenDescTypes(desc: String, ctx: ScanContext) {
        for (m in objectTypeRegex.findAll(desc)) {
            widenClassCheck(m.groupValues[1], ctx)
        }
    }

    private fun widenMemberCheck(owner: String, name: String, desc: String, isMethod: Boolean, ctx: ScanContext) {
        widenDescTypes(desc, ctx)
        if (!inTree(owner)) return
        widenClassCheck(owner, ctx)
        val res = resolveMember(MemberRef(owner, name, desc), isMethod) ?: return
        if (res.declaringOwner != owner) widenClassCheck(res.declaringOwner, ctx)
        val accTree = accessInheritance ?: inheritance
        val target = mapClass(res.declaringOwner)
        val (lookupClass, lookupName, lookupDesc) = if (accessInheritance != null) {
            Triple(target, res.targetName, if (isMethod) mapMethodDesc(desc) else mapFieldDesc(desc))
        } else {
            Triple(res.declaringOwner, name, desc)
        }
        val info = accTree.classInfo(internal(lookupClass)) ?: return
        val memberAccess = if (isMethod) {
            info.methods.firstOrNull { it.name.value == lookupName && it.descriptor.value == lookupDesc }?.access
        } else {
            info.fields.firstOrNull { it.name.value == lookupName && it.descriptor.toString() == lookupDesc }?.access
        } ?: return
        if (!accessible(target, memberAccess, true, ctx)) {
            val sig = if (isMethod) {
                res.targetName + mapMethodDesc(desc)
            } else {
                "${res.targetName}:${mapFieldDesc(desc)}"
            }
            val map = if (isMethod) result.widenMethods else result.widenFields
            map.getOrPut(target) { sortedSetOf() } += sig
        }
    }

    private fun tryParseMethodDescStatic(s: String): Boolean = try {
        s.startsWith("(") && run { MethodDescriptor.read(s); true }
    } catch (e: Exception) {
        false
    }

    private fun tryParseFieldDescStatic(s: String): Boolean = try {
        // no prefix gate — primitive descs (`F` for the yaw/pitch shapes)
        // are field types too; validation rejects everything else
        s.length <= 512 && run { FieldDescriptor.read(s); true }
    } catch (e: Exception) {
        false
    }

    private fun extractObjectTypesStatic(desc: String): List<String>? =
        objectTypeRegex.findAll(desc).map { it.groupValues[1] }.toList()

    // ------------------------------------------------------------------
    // tracking remapper facade
    // ------------------------------------------------------------------

    private fun trackClass(devName: String) {
        if (inTree(devName)) usedClasses += devName
    }

    private val trackingRemapper = object : Remapper() {
        override fun map(typeName: String): String {
            trackClass(typeName)
            return mapClass(typeName)
        }

        override fun mapInnerClassName(name: String, ownerName: String, innerName: String): String {
            trackClass(name)
            return super.mapInnerClassName(name, ownerName, innerName)
        }

        override fun mapMethodName(owner: String, name: String, descriptor: String): String {
            if (!name.startsWith("<")) {
                usedMethods += MemberRef(owner, name, descriptor)
            }
            return remapper.mapMethod(
                internal(owner), unqual(name), MethodDescriptor.read(descriptor)
            ).value
        }

        override fun mapFieldName(owner: String, name: String, descriptor: String): String {
            usedFields += MemberRef(owner, name, descriptor)
            return remapper.mapField(
                internal(owner), unqual(name), FieldDescriptor.read(descriptor)
            ).value
        }
    }

    // ------------------------------------------------------------------
    // scan visitors
    // ------------------------------------------------------------------

    private inner class ScanClassRemapper(cv: ClassVisitor) :
        ClassRemapper(Opcodes.ASM9, cv, trackingRemapper) {

        var patchTarget: String? = null
        lateinit var ctx: ScanContext

        override fun visit(
            version: Int, access: Int, name: String, signature: String?,
            superName: String?, interfaces: Array<out String>?
        ) {
            ctx = ScanContext(name, superName)
            super.visit(version, access, name, signature, superName, interfaces)
        }


        override fun createAnnotationRemapper(descriptor: String, annotationVisitor: AnnotationVisitor): AnnotationVisitor {
            if (descriptor == "Lnilloader/api/lib/mini/annotation/Patch\$Class;") {
                return object : AnnotationRemapper(Opcodes.ASM9, descriptor, annotationVisitor, trackingRemapper) {
                    override fun visit(name: String?, value: Any?) {
                        if (name == "value" && value is String) {
                            // dotted at runtime too — MiniTransformer does
                            // the same replace; `$` survives for inners
                            val internal = value.replace('.', '/')
                            patchTarget = internal
                            trackClass(internal)
                        }
                        super.visit(name, value)
                    }
                }
            }
            return super.createAnnotationRemapper(descriptor, annotationVisitor)
        }

        override fun visitMethod(
            access: Int, name: String, descriptor: String,
            signature: String?, exceptions: Array<out String>?
        ): MethodVisitor {
            // declared-member override scan — a mod method overriding a
            // game method must carry the rename under the mod's own
            // (identity) class entry or the runtime remap breaks the
            // override. Resolution drops non-overriding methods.
            if (!name.startsWith("<")) {
                usedMethods += MemberRef(className, name, descriptor)
            }
            return super.visitMethod(access, name, descriptor, signature, exceptions)
        }

        override fun createMethodRemapper(methodVisitor: MethodVisitor): MethodVisitor {
            return ScanMethodRemapper(this, methodVisitor)
        }
    }

    private inner class ScanMethodRemapper(
        private val cls: ScanClassRemapper,
        mv: MethodVisitor,
    ) : MethodRemapper(Opcodes.ASM9, mv, trackingRemapper) {

        // ldc triple state machine — owner (stage 1) → subject (stage 2)
        // → desc (completes). Any non-matching insn resets.
        private var stage = 0
        private var ldcOwner: String? = null
        private var ldcSubject: String? = null

        override fun visitLdcInsn(value: Any?) {
            if (value is String) {
                scanStringConstant(value)
                when (stage) {
                    0 -> if (inTree(value)) {
                        stage = 1
                        ldcOwner = value
                        // the owner string is a class reference regardless
                        // of what follows — covers single-arg remapType
                        trackClass(value)
                        super.visitLdcInsn(value)
                        return
                    }
                    1 -> {
                        stage = 2
                        ldcSubject = value
                        super.visitLdcInsn(value)
                        return
                    }
                    2 -> {
                        val owner = ldcOwner!!
                        val subject = ldcSubject!!
                        completeTriple(owner, subject, value)
                        resetLdc()
                        super.visitLdcInsn(value)
                        return
                    }
                }
            } else {
                resetLdc()
            }
            super.visitLdcInsn(value)
        }

        /** owner/name/desc triple completed — record the member ref.
         *  Method shape tried first, then field (NilGradle parity) */
        private fun completeTriple(owner: String, subject: String, desc: String) {
            if (subject.startsWith("<")) return
            if (tryParseMethodDesc(desc)) {
                usedMethods += MemberRef(owner, subject, desc)
            } else if (tryParseFieldDesc(desc)) {
                usedFields += MemberRef(owner, subject, desc)
            }
        }

        /** single-string constant scan — class-name literals and
         *  descriptor literals (`remapType`/`remapMethodDesc` shapes);
         *  superset-safe: entries never hurt */
        private fun scanStringConstant(value: String) {
            if (inTree(value)) {
                trackClass(value)
                return
            }
            when {
                tryParseMethodDesc(value) -> extractObjectTypes(value)
                tryParseFieldDesc(value) -> extractObjectTypes(value)
            }
        }

        private fun extractObjectTypes(desc: String) {
            for (m in objectTypeRegex.findAll(desc)) {
                trackClass(m.groupValues[1])
            }
        }

        private fun tryParseMethodDesc(s: String): Boolean = tryParseMethodDescStatic(s)

        private fun tryParseFieldDesc(s: String): Boolean = tryParseFieldDescStatic(s)

        private fun resetLdc() {
            stage = 0
            ldcOwner = null
            ldcSubject = null
        }

        override fun createAnnotationRemapper(descriptor: String, annotationVisitor: AnnotationVisitor): AnnotationVisitor {
            if (descriptor == "Lnilloader/api/lib/mini/annotation/Patch\$Method;" ||
                descriptor == "Lnilloader/api/lib/mini/annotation/Patch\$Methods;"
            ) {
                return object : AnnotationRemapper(Opcodes.ASM9, descriptor, annotationVisitor, trackingRemapper) {
                    override fun visit(name: String?, value: Any?) {
                        if (name == "value" && value is String) {
                            scanPatchMethodValue(value)
                        }
                        super.visit(name, value)
                    }

                    // the repeatable container nests @Patch.Method
                    // annotations — recursing into the same visitor
                    // processes their value strings identically
                    override fun visitAnnotation(name: String?, descriptor: String?): AnnotationVisitor = this
                }
            }
            return super.createAnnotationRemapper(descriptor, annotationVisitor)
        }

        private fun scanPatchMethodValue(value: String) {
            val paren = value.indexOf('(')
            if (paren <= 0) {
                logger("@Patch.Method value \"$value\" is not a full binary descriptor — skipping")
                return
            }
            val name = value.substring(0, paren)
            val desc = value.substring(paren)
            if (!tryParseMethodDesc(desc)) {
                logger("@Patch.Method value \"$value\" is not a full binary descriptor — skipping")
                return
            }
            val owner = cls.patchTarget
            if (owner == null) {
                logger("MiniTransformer with no @Patch.Class — patchers cannot be remapped " +
                        "(ignorable when patching unobfuscated code)")
                return
            }
            usedMethods += MemberRef(owner, name, desc)
            // the patch signature's object types ride the runtime
            // remapMethodDesc lookup — they need class entries too
            for (t in extractObjectTypesStatic(desc).orEmpty()) trackClass(t)
        }

        // -- widen hooks — the ldc state machine resets on any other
        //    insn, matching NilGradle's consecutive-triple requirement --

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            resetLdc()
            cls.ctx.let { widenMemberCheck(owner, name, descriptor, false, it) }
            super.visitFieldInsn(opcode, owner, name, descriptor)
        }

        override fun visitMethodInsn(
            opcodeAndSource: Int, owner: String, name: String,
            descriptor: String, isInterface: Boolean
        ) {
            resetLdc()
            cls.ctx.let { widenMemberCheck(owner, name, descriptor, true, it) }
            super.visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface)
        }

        override fun visitTypeInsn(opcode: Int, type: String) {
            resetLdc()
            cls.ctx.let {
                val internal = when {
                    type.startsWith("[") -> type.substringAfterLast('[').removeSurrounding("L", ";")
                    else -> type
                }
                if (!type.startsWith("[") || type.contains("L")) {
                    widenClassCheck(internal, it)
                }
            }
            super.visitTypeInsn(opcode, type)
        }

        override fun visitInvokeDynamicInsn(
            name: String, descriptor: String, bootstrapMethodHandle: Handle,
            vararg bootstrapMethodArguments: Any
        ) {
            resetLdc()
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, *bootstrapMethodArguments)
        }

        override fun visitInsn(opcode: Int) {
            resetLdc()
            super.visitInsn(opcode)
        }

        override fun visitIntInsn(opcode: Int, operand: Int) {
            resetLdc()
            super.visitIntInsn(opcode, operand)
        }

        override fun visitVarInsn(opcode: Int, `var`: Int) {
            resetLdc()
            super.visitVarInsn(opcode, `var`)
        }

        override fun visitJumpInsn(opcode: Int, label: org.objectweb.asm.Label) {
            resetLdc()
            super.visitJumpInsn(opcode, label)
        }

        override fun visitIincInsn(`var`: Int, increment: Int) {
            resetLdc()
            super.visitIincInsn(`var`, increment)
        }

        override fun visitTableSwitchInsn(min: Int, max: Int, dflt: org.objectweb.asm.Label, vararg labels: org.objectweb.asm.Label) {
            resetLdc()
            super.visitTableSwitchInsn(min, max, dflt, *labels)
        }

        override fun visitLookupSwitchInsn(dflt: org.objectweb.asm.Label, keys: IntArray, labels: Array<out org.objectweb.asm.Label>) {
            resetLdc()
            super.visitLookupSwitchInsn(dflt, keys, labels)
        }

        override fun visitFrame(type: Int, numLocal: Int, local: Array<out Any>?, numStack: Int, stack: Array<out Any>?) {
            resetLdc()
            super.visitFrame(type, numLocal, local, numStack, stack)
        }
    }

}
