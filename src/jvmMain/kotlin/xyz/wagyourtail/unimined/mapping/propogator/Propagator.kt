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
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.util.DefaultMap
import xyz.wagyourtail.unimined.mapping.util.defaultedMapOf
import xyz.wagyourtail.unimined.mapping.visitor.AccessType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.use
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asStream

class Propagator(val namespace: Namespace, val tree: AbstractMappingTree, required: Set<Path>) {
    val LOGGER = KotlinLogging.logger {  }

    val classes: Map<InternalName, ClassInfo> = run {
        val classes = mutableMapOf<InternalName, ClassInfo>()
        required.parallelStream().forEach { path ->
            ZipFile.builder().setSeekableByteChannel(Files.newByteChannel(path)).get().use { zf ->
                zf.entries.asSequence().asStream().parallel().forEach {
                    if (!it.isDirectory && !it.name.startsWith("META-INF/versions") && it.name.endsWith(".class")) {
                        val className = InternalName.read(it.name.removeSuffix(".class"))
                        classes[className] = ClassInfo(className, zf.getInputStream(it))
                    }
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

    private val propagationList: Map<Pair<InternalName, Pair<String, MethodDescriptor?>>, Set<InternalName>> by lazy {
        val methods = mutableMapOf<Pair<InternalName, Pair<String, MethodDescriptor?>>, MutableSet<InternalName>>()
        for (c in classes.values) {
            for ((method, classes) in c.resolved) {
                var set: MutableSet<InternalName>? = null
                for (clazz in classes) {
                    if (clazz to method in methods) {
                        set = methods[clazz to method]!!
                        break
                    }
                }
                if (set == null) {
                    set = mutableSetOf()
                }
                set.addAll(classes)
                for (clazz in classes) {
                    methods[clazz to method] = set
                }
            }
        }
        methods
    }

    fun propagate(targetNs: Set<Namespace>, visitor: MappingVisitor = tree) {
        val names =
            defaultedMapOf<InternalName, DefaultMap<Pair<String, MethodDescriptor?>, MutableMap<Namespace, MutableSet<String>>>> { cls ->
                defaultedMapOf { md ->
                    val mds = tree.getClass(namespace, cls)?.getMethods(namespace, md.first, md.second) ?: setOf()
                    val methodNames = mutableMapOf<Namespace, MutableSet<String>>()
                    for (ns in targetNs) {
                        for (mn in mds) {
                            val name = mn.getName(ns)
                            if (name != null) {
                                methodNames.getOrPut(ns) { mutableSetOf() } += name
                            }
                        }
                    }
                    methodNames
                }
            }
        val propogationListRemaining = propagationList.toMutableMap()
        while (propogationListRemaining.isNotEmpty()) {
            val (method, classes) = propogationListRemaining.entries.first()
            val md = method.second
            val methodNames = mutableMapOf<Namespace, MutableSet<String>>()
            val targets = mutableSetOf<Pair<InternalName, Pair<String, MethodDescriptor?>>>()
            for (clazz in classes) {
                targets.add(clazz to md)
                val cn = tree.getClass(namespace, clazz) ?: continue
                val mns = cn.getMethods(namespace, md.first, md.second)
                for (mn in mns) {
                    val desc = mn.getMethodDesc(namespace)
                    for (ns in targetNs) {
                        val name = mn.getName(ns)
                        if (name != null) {
                            methodNames.getOrPut(ns) { mutableSetOf() } += name
                        }
                    }
                    targets.add(clazz to (md.first to desc))
                }
            }
            val firstTarget = targets.first()
            for ((ns, mNames) in methodNames) {
                if (mNames.size > 1) {
                    LOGGER.warn { "Multiple names found for ${firstTarget.first} ${firstTarget.second} in $ns: $mNames" }
                    val first = mNames.first()
                    mNames.clear()
                    mNames += first
                }
            }
            for (target in targets) {
                propogationListRemaining.remove(target)
                names[target.first][target.second] = methodNames
            }
        }

        visitor.use {
            visitHeader(namespace.name, *targetNs.map { it.name }.toTypedArray())

            val visitClasses = tree.classList().mapNotNull { it.first[namespace] }
            for ((cls, info) in classes) {
                val clsNames = mutableMapOf<Namespace, InternalName>()
                val c = tree.getClass(namespace, cls)
                for (ns in targetNs) {
                    val name = c?.getName(ns)
                    if (name != null) {
                        clsNames[ns] = name
                    }
                }
                visitor.visitClass(clsNames + (namespace to cls))?.use {
                    val nsNameCls = names[cls]
                    for (method in info.methods) {
                        val orig = (namespace to (method.first to method.second))
                        val targets = nsNameCls[method].mapValues { it.value.first() to null } + orig
                        visitMethod(targets)?.visitEnd()
                    }
                    for (field in info.fields) {
                        val fds =
                            tree.getClass(namespace, cls)?.getFields(namespace, field.first, field.second) ?: setOf()
                        val fieldNames = mutableMapOf<Namespace, MutableSet<String>>()
                        for (ns in targetNs) {
                            for (fd in fds) {
                                val name = fd.getName(ns)
                                if (name != null) {
                                    fieldNames.getOrPut(ns) { mutableSetOf() } += name
                                }
                            }
                        }
                        for ((ns, fNames) in fieldNames) {
                            if (fNames.size > 1) {
                                LOGGER.warn { "Multiple names found for $cls ${field.first} in $ns: $fNames" }
                                val first = fNames.first()
                                fNames.clear()
                                fNames += first
                            }
                        }
                        val targets = fieldNames.mapValues { it.value.first() to null } + (namespace to field)
                        visitField(targets)?.visitEnd()
                    }
                }
            }
        }
    }


    inner class ClassInfo(val name: InternalName, val inputStream: InputStream) {

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

        val classNode by lazy {
            tree.getClass(namespace, name)
        }

        val parent = InternalName.read(self.superName)

        val interfaces = self.interfaces.map { InternalName.read(it) }

        val methods: List<Pair<String, MethodDescriptor?>> = self.methods.map {
            it.name to MethodDescriptor.read(it.desc)
        }

        val publicMethods: List<Pair<String, MethodDescriptor?>> = self.methods.filter { method ->
            var access = method.access

            // modify access according to mapping's access rules for this class
            classNode?.getMethods(namespace, method.name, MethodDescriptor.read(method.desc))?.forEach { mNode ->
                for (node in mNode.access) {
                    if (node.conditions.check(AccessFlag.of(ElementType.METHOD, access))) {
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

            access and (Opcodes.ACC_STATIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL) == 0
        }.map { it.name to MethodDescriptor.read(it.desc) }

        val fields: List<Pair<String, FieldDescriptor?>> = self.fields.map {
            it.name to FieldDescriptor.read(it.desc)
        }

        val resolved: Map<Pair<String, MethodDescriptor?>, Set<InternalName>> by lazy {
            val md = mutableMapOf<Pair<String, MethodDescriptor?>, MutableSet<InternalName>>()
            for (c in traverseParents()) {
                for ((name, desc) in c.publicMethods) {
                    md.getOrPut(name to desc) { mutableSetOf() } += c.name
                }
            }
            md
        }

        private fun traverseParents(): Sequence<ClassInfo> {
            var current: ClassInfo? = this
            return generateSequence {
                val c = current
                current = c?.parent?.let { classes[it] }
                c
            }.flatMap { c ->
                sequenceOf(c) + c.interfaces.asSequence()
                    .mapNotNull { classes[it] }
                    .flatMap { sequenceOf(it) + it.traverseParents() }
            }
        }

    }

}