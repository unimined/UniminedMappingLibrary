package xyz.wagyourtail.unimined.mapping.propogator

import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.propagator.InheritanceTree
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence
import kotlin.streams.asStream

class Propagator(tree: AbstractMappingTree, override val fns: Namespace, jars: Set<Path>): InheritanceTree(tree) {

    override val classes: Map<InternalName, ClassInfo> by lazy {
        jars.parallelStream().flatMap {
            scanJar(it).asStream()
        }.asSequence().toMap()
    }

    private fun scanJar(jar: Path): Sequence<Pair<InternalName, ClassInfo>> {
        ZipFile.builder().setSeekableByteChannel(Files.newByteChannel(jar)).setIgnoreLocalFileHeader(true).get().use { zf ->
            return zf.entries.asSequence().map {
                return@map if (!it.isDirectory && !it.name.startsWith("META-INF/versions") && it.name.endsWith(".class")) {
                    zf.getInputStream(it).use { stream -> scanClass(it.name, stream) }
                } else {
                    null
                }
            }.toList().asSequence().filterNotNull()
        }
    }

    private fun scanClass(path: String, input: InputStream): Pair<InternalName, ClassInfo> {
        val className = InternalName.read(path.removeSuffix(".class"))
        val reader = ClassReader(input)
        val node = ClassNode()
        reader.accept(node, ClassReader.SKIP_CODE)

        return className to ClassInfo(className, InternalName.read(node.superName), node.interfaces.map(InternalName::read)).apply {
            for (method in node.methods) {
                methods.add(MethodInfo(method.name, MethodDescriptor.read(method.desc), method.access))
            }
            for (field in node.fields) {
                fields.add(FieldInfo(field.name, FieldDescriptor.read(field.desc), field.access))
            }
        }
    }

}