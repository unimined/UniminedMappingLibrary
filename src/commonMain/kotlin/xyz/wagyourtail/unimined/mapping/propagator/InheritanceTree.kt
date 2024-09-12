package xyz.wagyourtail.unimined.mapping.propagator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.commonskt.utils.coroutines.parallelMap
import xyz.wagyourtail.unimined.mapping.tree.node._class.ClassNode

open class InheritanceTree(val tree: AbstractMappingTree, val fns: Namespace, val targets: Set<Namespace>) {
    val LOGGER = KotlinLogging.logger {  }

    private val _classes = mutableMapOf<InternalName, ClassInfo>()
    val classes: Map<InternalName, ClassInfo> get() = _classes

    suspend fun propagate() = coroutineScope {
        classes.values.parallelMap {
            it.propagate()
        }
    }

    fun read(data: CharReader<*>) {
        var ci: ClassInfo? = null
        while (!data.exhausted()) {
            if (data.peek() == '\n') {
                data.take()
                continue
            }
            var col = data.takeNext()!!
            var indent = 0
            while (col.isEmpty()) {
                indent++
                col = data.takeNext()!!
            }
            if (indent > 1) {
                throw IllegalArgumentException("expected method, found double indent")
            }
            if (indent == 0) {
                val cls = col
                val sup = data.takeNext()!!.ifEmpty { null }
                val intf = data.takeRemainingOnLine().map { InternalName.read(it) }
                ci = ClassInfo(InternalName.read(cls), sup?.let { InternalName.read(it) }, intf)
                _classes[ci.name] = ci
            } else {
                val acc = col.split("|").map { AccessFlag.valueOf(it.uppercase()) }
                val name = data.takeNext()!!
                val desc = FieldOrMethodDescriptor.read(data.takeNext()!!)

                if (desc.isMethodDescriptor()) {
                    ci!!.methods.add(
                        MethodInfo(
                            name,
                            desc.getMethodDescriptor(),
                            AccessFlag.toInt(acc.toSet())
                        )
                    )
                } else {
                    ci!!.fields.add(
                        FieldInfo(
                            name,
                            desc.getFieldDescriptor(),
                            AccessFlag.toInt(acc.toSet())
                        )
                    )
                }
            }
        }
    }

    fun write(append: (String) -> Unit) {
        for (cls in _classes.values) {
            append(cls.name.toString())
            append("\t")
            append(cls.superType.toString())
            append("\t")
            append(cls.interfaces.joinToString("\t") { it.toString() })
            append("\n")

            for (method in cls.methods) {
                append("\t")
                append(AccessFlag.of(ElementType.METHOD, method.access).joinToString("|") { it.toString() })
                append("\t")
                append(method.name)
                append("\t")
                append(method.descriptor.toString())
                append("\n")
            }

        }
    }

    inner class ClassInfo(
        val name: InternalName,
        val superType: InternalName?,
        val interfaces: List<InternalName>,
    ) {
        val methods = mutableListOf<MethodInfo>()

        val fields = mutableListOf<FieldInfo>()

        val superClass by lazy {
            this@InheritanceTree.classes[superType]
        }

        val interfaceClasses by lazy {
            interfaces.mapNotNull { this@InheritanceTree.classes[it] }
        }

        val clsNode by lazy {
            tree.getClass(fns, name)
        }

        val propagateLock = Mutex()

        lateinit var methodData: MutableMap<MethodInfo, MutableMap<Namespace, String>>

        suspend fun propagate(): Unit = coroutineScope {
            if (::methodData.isInitialized) methodData
            propagateLock.withLock {
                if (::methodData.isInitialized) methodData

                superClass?.propagate()
                interfaceClasses.parallelMap { it.propagate() }

                for (method in methods) {
                    clsNode?.visitMethod(mapOf(fns to (method.name to method.descriptor))).visitEnd()
                }

                val methods = methods.filter { md ->
                    // modify access
                    val acc = AccessFlag.of(ElementType.METHOD, md.access).toMutableSet()
                    val methods = clsNode?.getMethods(fns, md.name, md.descriptor)
                    methods?.flatMap { it.access }?.forEach {
                        it.apply(acc)
                    }
                    AccessFlag.isInheritable(acc)
                }.parallelMap { md ->
                    val names = (clsNode?.getMethods(fns, md.name, md.descriptor)?.firstOrNull()?.names?.filterKeys { it in targets } ?: emptyMap()).toMutableMap()
                    // traverse parents, retrieve matching mappings
                    val superNames = superClass?.methodData?.get(md)
                    val interfaces = interfaceClasses.map { it to it.methodData[md] }
                    for (ns in targets) {
                        if (superNames != null) {
                            if (names[ns] != superNames[ns]) {
                                if (superNames[ns] == null) {
                                    superClass!!.overwriteMethodName(md, ns, names[ns]!!)
                                } else {
                                    names[ns] = superNames.getValue(ns)
                                }
                            }
                        }
                        for ((intf, intfNames) in interfaces) {
                            if (intfNames != null) {
                                if (names[ns] != intfNames[ns] && names[ns] != null) {
                                    intf.overwriteMethodName(md, ns, names[ns]!!)
                                }
                            }
                        }
                    }
                    clsNode?.visitMethod(
                        mapOf(fns to (md.name to md.descriptor)) +
                        names.mapValues { it.value to null }
                    )?.visitEnd()
                    names[fns] = md.name
                    md to names
                }.associate { it }.toMutableMap()

                for (method in superClass?.methodData ?: emptyMap()) {
                    if (method.key !in methods) {
                        methods[method.key] = method.value
                    }
                }

                for (intf in interfaceClasses) {
                    for (method in intf.methodData) {
                        if (method.key !in methods) {
                            methods[method.key] = method.value
                        }
                    }
                }

                methodData = methods
            }
        }

        private fun overwriteMethodName(md: MethodInfo, namespace: Namespace, newName: String) {
            if (md in methodData) {
                methodData[md]!![namespace] = newName
                clsNode?.visitMethod(mapOf(
                    fns to (md.name to md.descriptor),
                    namespace to (newName to null)
                ))?.visitEnd()
                superClass?.overwriteMethodName(md, namespace, newName)
                for (interfaceClass in interfaceClasses) {
                    interfaceClass.overwriteMethodName(md, namespace, newName)
                }
            }
        }

    }

    class MethodInfo(
        val name: String,
        val descriptor: MethodDescriptor,
        var access: Int
    ) {

        override fun equals(other: Any?): Boolean {
            return other is MethodInfo && name == other.name && descriptor == other.descriptor
        }

        override fun hashCode(): Int {
            return name.hashCode() * 31 + descriptor.hashCode()
        }

    }

    class FieldInfo(
        val name: String,
        val descriptor: FieldDescriptor,
        var access: Int
    ) {

        override fun equals(other: Any?): Boolean {
            return other is FieldInfo && name == other.name && descriptor == other.descriptor
        }

        override fun hashCode(): Int {
            return name.hashCode() * 31 + descriptor.hashCode()
        }

    }
}