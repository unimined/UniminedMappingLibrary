package xyz.wagyourtail.unimined.mapping.propogator

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.compress.archivers.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.minus
import xyz.wagyourtail.unimined.mapping.jvms.four.plus
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.fixes.propogator.Propogator
import java.io.InputStream
import java.nio.file.Path
import kotlin.streams.asStream

class PropogatorImpl(namespace: Namespace, tree: AbstractMappingTree, required: Set<Path>): Propogator<PropogatorImpl>(namespace, tree) {
    companion object {
        val LOGGER = KotlinLogging.logger {  }
    }

    override val classes: Map<InternalName, ClassInfo<PropogatorImpl>> = run {
        val classes = mutableMapOf<InternalName, ClassInfoImpl>()
        required.parallelStream().forEach { path ->
            val f = ZipFile(path)
            f.entries.asSequence().asStream().parallel().forEach {
                if (!it.isDirectory && !it.name.startsWith("META-INF/versions") && it.name.endsWith(".class")) {
                    val className = InternalName.read(it.name.removeSuffix(".class"))
                    classes[className] = ClassInfoImpl(className, f.getInputStream(it))
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

    inner class ClassInfoImpl(name: InternalName, inputStream: InputStream): ClassInfo<PropogatorImpl>(this, name) {

        private val self = try {
                ClassReader(inputStream).let { reader ->
                    val node = ClassNode()
                    reader.accept(node, ClassReader.SKIP_CODE)
                    node
                }
            } catch (e: Exception) {
                LOGGER.error(e) { "Failed to read class $name" }
                val cn = ClassNode()
                cn.name = name.value
                cn.superName = "java/lang/Object"
                cn
            }


        override val parent = InternalName.read(self.superName)

        override val interfaces = self.interfaces.map { InternalName.read(it) }

        override val methods: List<Pair<String, MethodDescriptor?>> = self.methods.filter { method ->
            val originalAccess = AccessFlag.of(ElementType.METHOD, method.access)
            var access = method.access

            // modify access according to mapping's access rules for this class
            classNode?.getMethods(namespace, method.name, MethodDescriptor.read(method.desc))?.forEach { mNode ->
                for (node in mNode.access.values) {
                    if (node.conditions.check(originalAccess)) {
                        if (node.namespaces.contains(namespace)) {
                            if (node.accessType == AccessType.ADD) {
                                access = method.access + node.accessFlag
                            } else if (node.accessType == AccessType.REMOVE) {
                                access = method.access - node.accessFlag
                            }
                        }
                    }
                }
            }

            access and (Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE) == 0
        }.map { it.name to MethodDescriptor.read(it.desc) }

    }

}