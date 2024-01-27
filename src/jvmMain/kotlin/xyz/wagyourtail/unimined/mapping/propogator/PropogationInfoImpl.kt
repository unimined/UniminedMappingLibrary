package xyz.wagyourtail.unimined.mapping.propogator

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.compress.archivers.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import java.io.InputStream
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.streams.asStream

class PropogationInfoImpl(namespace: Namespace, required: Set<Path>, classpath: Set<Path>): PropogationInfo<PropogationInfoImpl>(namespace) {
    private val classpath = URLClassLoader((required + classpath).map { it.toUri().toURL() }.toTypedArray())

    override val classes: Map<InternalName, ClassInfo<PropogationInfoImpl>> = run {
        val classes = mutableMapOf<InternalName, ClassInfoImpl>()
        required.parallelStream().forEach { path ->
            val f = ZipFile(path)
            f.entries.asSequence().asStream().parallel().forEach {
                if (!it.isDirectory && it.name.endsWith(".class")) {
                    val className = InternalName.read(it.name.removeSuffix(".class"))
                    classes[className] = ClassInfoImpl(this, className, f.getInputStream(it))
                }
            }
        }
        classes
    }

    init {
        classes.values.parallelStream().forEach {
            it.resolved
        }
    }

    class ClassInfoImpl(info: PropogationInfoImpl, name: InternalName, inputStream: InputStream): ClassInfo<PropogationInfoImpl>(info, name) {
        companion object {
            val LOGGER = KotlinLogging.logger {  }
        }

        private val self = try {
                ClassReader(inputStream).let { reader ->
                    val node = org.objectweb.asm.tree.ClassNode()
                    reader.accept(node, ClassReader.SKIP_CODE)
                    node
                }
            } catch (e: Exception) {
                LOGGER.error(e) { "Failed to read class $name" }
                val cn = org.objectweb.asm.tree.ClassNode()
                cn.name = name.value
                cn.superName = "java/lang/Object"
                cn
            }


        override val parent = InternalName.read(self.superName)

        override val interfaces = self.interfaces.map { InternalName.read(it) }

        override val methods: List<Pair<String, MethodDescriptor?>> = self.methods.filter { it.access and (Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE) == 0 }.map { it.name to MethodDescriptor.read(it.desc) }

    }

}