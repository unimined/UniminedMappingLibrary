package xyz.wagyourtail.unimined.mapping.propagator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.wagyourtail.commonskt.utils.coroutines.parallelMap
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.contains
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.AbstractMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.use

abstract class InheritanceTree(val tree: AbstractMappingTree, val fns: Namespace) {
    val LOGGER = KotlinLogging.logger {  }

    abstract val classes: Map<InternalName, ClassInfo>

    suspend fun propagate(targets: Set<Namespace>) = coroutineScope {
        classes.values.parallelMap {
            it.propagate(targets)
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

        suspend fun propagate(targets: Set<Namespace>): Unit = coroutineScope {
            if (::methodData.isInitialized) methodData
            propagateLock.withLock {
                if (::methodData.isInitialized) methodData

                superClass?.propagate(targets)
                interfaceClasses.parallelMap { it.propagate(targets) }

                for (method in methods) {
                    clsNode?.visitMethod(mapOf(fns to (method.name to method.descriptor)))?.use {
                        var lvOrd = if (AccessFlag.STATIC in method.access) 0 else 1
                        method.descriptor.getParts().second.forEachIndexed { i, p ->
                            visitParameter(i, lvOrd, emptyMap())?.visitEnd()
                            lvOrd += p.value.getWidth()
                        }
                    }
                }

                val methods = methods.filter { md ->
                    // modify access
                    val acc = AccessFlag.of(ElementType.METHOD, md.access).toMutableSet()
                    val methods = clsNode?.getMethods(fns, md.name, md.descriptor)
                    methods?.flatMap { it.access }?.forEach {
                        it.apply(acc)
                    }
                    AccessFlag.isInheritable(acc) && !md.name.startsWith("<")
                }.parallelMap { md ->
                    md to (clsNode?.getMethods(fns, md.name, md.descriptor)?.firstOrNull()?.names?.filterKeys { it in targets } ?: emptyMap()).toMutableMap()
                }.parallelMap { (md, names) ->
                    // traverse parents, retrieve matching mappings
                    val superNames = superClass?.methodData?.get(md)
                    val interfaces = interfaceClasses.map { it to it.methodData[md] }
                    val needsOverwrite = mutableListOf<Namespace>()
                    if (superNames != null) {
                        val needsOverwrite = mutableListOf<Namespace>()
                        for (ns in targets) {
                            if (names[ns] != superNames[ns]) {
                                if (superNames[ns] == null) {
                                    needsOverwrite += ns
                                } else {
                                    names[ns] = superNames.getValue(ns)
                                }
                            }
                        }
                    }
                    for ((intf, intfNames) in interfaces) {
                        if (intfNames != null) {
                            for (ns in targets) {
                                if (names[ns] != intfNames[ns]) {
                                    if (intfNames[ns] == null) {
                                        needsOverwrite += ns
                                    } else {
                                        names[ns] = intfNames.getValue(ns)
                                    }
                                }
                            }
                        }
                    }
                    if (needsOverwrite.isNotEmpty()) {
                        overwriteParentMethodNames(md, names.filterKeys { it in needsOverwrite })
                    }
                    clsNode?.visitMethod(
                        names.mapValues { it.value to null } +
                                mapOf(fns to (md.name to md.descriptor))
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

        private fun overwriteMethodNames(md: MethodInfo, names: Map<Namespace, String>) {
            if (md in methodData) {
                methodData[md]!!.putAll(names)
                clsNode?.visitMethod(mapOf(
                    *names.mapValues { it.value to null }.entries.map { it.key to it.value }.toTypedArray(),
                    fns to (md.name to md.descriptor)
                ))?.visitEnd()
                overwriteParentMethodNames(md, names)
            }
        }

        private fun overwriteParentMethodNames(md: MethodInfo, names: Map<Namespace, String>) {
            superClass?.overwriteMethodNames(md, names)
            for (interfaceClass in interfaceClasses) {
                interfaceClass.overwriteMethodNames(md, names)
            }
        }

        override fun toString(): String {
            return "$name extends $superClass implements $interfaces"
        }

    }

    class MethodInfo(
        val name: String,
        val descriptor: MethodDescriptor,
        var access: Int
    ) {

        override fun toString(): String {
            return "${AccessFlag.of(ElementType.METHOD, access)} $name;$descriptor"
        }

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

        override fun toString(): String {
            return "${AccessFlag.of(ElementType.FIELD, access)} $name;$descriptor"
        }

        override fun equals(other: Any?): Boolean {
            return other is FieldInfo && name == other.name && descriptor == other.descriptor
        }

        override fun hashCode(): Int {
            return name.hashCode() * 31 + descriptor.hashCode()
        }

    }
}