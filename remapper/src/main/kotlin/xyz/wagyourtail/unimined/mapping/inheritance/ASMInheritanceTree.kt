package xyz.wagyourtail.unimined.mapping.inheritance

import no.synth.kmpzip.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.streams.asSequence
import kotlin.streams.asStream

class ASMInheritanceTree(override val fns: Namespace, data: ScanData): InheritanceTree() {

    constructor(fns: Namespace, jars: Set<Path>) : this(fns, scan(jars))

    override val classes: Map<InternalName, ClassInfo> by lazy {
        data.classes.mapValues { (name, raw) ->
            ClassInfo(name, raw.superType, raw.interfaces).apply {
                for (m in raw.methods) methods.add(MethodInfo(m.name, m.descriptor, m.access))
                for (f in raw.fields) fields.add(FieldInfo(f.name, f.descriptor, f.access))
            }
        }
    }

    /**
     * Immutable scan output for a jar set — shareable across tree
     * instances so batch remaps (e.g. one artifact transform per mod
     * jar over a common classpath) don't re-parse identical jars.
     * Sharing at this level is safe only because [ClassInfo]s — the
     * part propagation mutates — are rebuilt per tree; never share a
     * tree or its [ClassInfo] instances between remaps.
     */
    class ScanData(val classes: Map<InternalName, RawClassInfo>) {
        operator fun plus(other: ScanData) = ScanData(classes + other.classes)
    }

    class RawClassInfo(
        val superType: InternalName?,
        val interfaces: List<InternalName>,
        val methods: List<RawMethodInfo>,
        val fields: List<RawFieldInfo>,
    )

    class RawMethodInfo(val name: UnqualifiedName, val descriptor: MethodDescriptor, val access: Int)

    class RawFieldInfo(val name: UnqualifiedName, val descriptor: FieldDescriptor, val access: Int)

    companion object {

        /** Scan [jars]' classes into a shareable [ScanData]. */
        fun scan(jars: Set<Path>): ScanData = ScanData(
            jars.parallelStream().flatMap { scanJar(it).asStream() }
                .collect(Collectors.toMap({ it.first }, { it.second }, { _, b -> b }))
        )

        private fun scanJar(jar: Path): Sequence<Pair<InternalName, RawClassInfo>> {
            // read the whole jar — fileSeekableSource round-trips through
            // a String path; a plain byte read avoids any
            // path-resolution surprises
            val raw = ZipFile(java.nio.file.Files.readAllBytes(jar)).use { zf ->
                zf.entries.asSequence().mapNotNull {
                    if (!it.isDirectory && !it.name.startsWith("META-INF/versions") && it.name.endsWith(".class")) {
                        it.name to zf.getInputStream(it).use { stream -> stream.readBytes() }
                    } else {
                        null
                    }
                }.toList()
            }
            // ClassReader parses are independent — parallelize per class,
            // not just per jar (a single merged jar dominates real inputs)
            return raw.parallelStream().map {
                scanClass(it.first, it.second)
            }.collect(Collectors.toList()).asSequence()
        }

        // minimal visitor — super/interface names plus member signatures;
        // nothing propagation reads lives on a ClassNode
        internal class ScanVisitor : ClassVisitor(Opcodes.ASM9) {
            var superName: InternalName? = null
            var interfaces: List<InternalName> = emptyList()
            val methods = mutableListOf<RawMethodInfo>()
            val fields = mutableListOf<RawFieldInfo>()

            override fun visit(
                version: Int, access: Int, name: String,
                signature: String?, superName: String?, interfaces: Array<out String>?
            ) {
                this.superName = superName?.let(InternalName::read)
                this.interfaces = interfaces?.map(InternalName::read) ?: emptyList()
            }

            override fun visitMethod(
                access: Int, name: String, descriptor: String,
                signature: String?, exceptions: Array<out String>?
            ): org.objectweb.asm.MethodVisitor? {
                methods.add(RawMethodInfo(UnqualifiedName.read(name), MethodDescriptor.read(descriptor), access))
                return null
            }

            override fun visitField(
                access: Int, name: String, descriptor: String,
                signature: String?, value: Any?
            ): org.objectweb.asm.FieldVisitor? {
                fields.add(RawFieldInfo(UnqualifiedName.read(name), FieldDescriptor.read(descriptor), access))
                return null
            }
        }

        internal fun scanClass(path: String, bytes: ByteArray): Pair<InternalName, RawClassInfo> {
            val className = InternalName.read(path.removeSuffix(".class"))
            val visitor = ScanVisitor()
            ClassReader(bytes).accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            return className to RawClassInfo(visitor.superName, visitor.interfaces, visitor.methods, visitor.fields)
        }
    }

}
