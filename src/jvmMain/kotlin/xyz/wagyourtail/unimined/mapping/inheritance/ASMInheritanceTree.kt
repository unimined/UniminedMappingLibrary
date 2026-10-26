package xyz.wagyourtail.unimined.mapping.inheritance

import no.synth.kmpzip.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import java.io.InputStream
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.streams.asSequence
import kotlin.streams.asStream

class ASMInheritanceTree(override val fns: Namespace, jars: Set<Path>): InheritanceTree() {


    override val classes: Map<InternalName, ClassInfo> by lazy {
        jars.parallelStream().flatMap {
            scanJar(it).asStream()
        }.asSequence().toMap()
    }

    private fun scanJar(jar: Path): Sequence<Pair<InternalName, ClassInfo>> {
        // read the whole jar — fileSeekableSource round-trips through a String
        // path; a plain byte read avoids any path-resolution surprises
        val raw = ZipFile(java.nio.file.Files.readAllBytes(jar)).use { zf ->
            zf.entries.asSequence().mapNotNull {
                if (!it.isDirectory && !it.name.startsWith("META-INF/versions") && it.name.endsWith(".class")) {
                    it.name to zf.getInputStream(it).use { stream -> stream.readBytes() }
                } else {
                    null
                }
            }.toList()
        }
        // ClassReader parses are independent — parallelize per class, not just
        // per jar (a single merged jar dominates real inputs)
        return raw.parallelStream().map {
            scanClass(it.first, it.second.inputStream())
        }.collect(Collectors.toList()).asSequence()
    }

    private fun scanClass(path: String, input: InputStream): Pair<InternalName, ClassInfo> {
        val className = InternalName.read(path.removeSuffix(".class"))
        val reader = ClassReader(input)
        val node = ClassNode()
        reader.accept(node, ClassReader.SKIP_CODE)

        return className to ClassInfo(className, InternalName.read(node.superName), node.interfaces.map(InternalName::read)).apply {
            for (method in node.methods) {
                methods.add(MethodInfo(UnqualifiedName.read(method.name), MethodDescriptor.read(method.desc), method.access))
            }
            for (field in node.fields) {
                fields.add(FieldInfo(UnqualifiedName.read(field.name), FieldDescriptor.read(field.desc), field.access))
            }
        }
    }

}