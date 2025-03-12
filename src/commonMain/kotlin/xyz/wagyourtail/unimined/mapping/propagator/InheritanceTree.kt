package xyz.wagyourtail.unimined.mapping.propagator

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
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

        private val propagateLock = Mutex()

        /**
         * map of inheritable methods to their names
         */
        val methodData = mutableMapOf<MethodInfo, MutableMap<Namespace, String>>()
        private val visitedNs = mutableSetOf(fns)

        suspend fun propagate(targets: Set<Namespace>): Unit = coroutineScope {
            val targets = targets - visitedNs
            if (targets.isEmpty()) return@coroutineScope
            propagateLock.withLock {
                val targets = targets - visitedNs
                if (targets.isEmpty()) return@withLock

                superClass?.propagate(targets)
                interfaceClasses.parallelMap { it.propagate(targets) }

                if (visitedNs.isEmpty()) {
                    for (field in fields) {
                        clsNode?.visitField(mapOf(fns to (field.name to field.descriptor)))?.visitEnd()
                    }

                    for (method in methods) {
                        clsNode?.visitMethod(mapOf(fns to (method.name to method.descriptor)))?.use {
                            var lvOrd = if (AccessFlag.STATIC in method.access) 0 else 1
                            method.descriptor.getParts().second.forEachIndexed { i, p ->
                                visitParameter(i, lvOrd, emptyMap())?.visitEnd()
                                lvOrd += p.value.getWidth()
                            }
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
                    val needsOverwrite = mutableSetOf<Namespace>()
                    val setFromParent = defaultedMapOf<Namespace, Boolean> { false }
                    if (superNames != null) {
                        for (ns in targets) {
                            if (names[ns] != superNames[ns]) {
                                if (superNames[ns] == null || (names[ns] != null && setFromParent[ns])) {
                                    needsOverwrite += ns
                                } else {
                                    val newName = superNames.getValue(ns)
                                    LOGGER.trace { "setting ${name}.${md} in $ns to $newName" }
                                    names[ns] = newName
                                    setFromParent[ns] = true
                                }
                            } else if (names[ns] != null) {
                                setFromParent[ns] = true
                            }
                        }
                    }
                    for ((intf, intfNames) in interfaces) {
                        if (intfNames != null) {
                            for (ns in targets) {
                                if (names[ns] != intfNames[ns]) {
                                    if (intfNames[ns] == null || (names[ns] != null && setFromParent[ns])) {
                                        needsOverwrite += ns
                                    } else {
                                        val newName = intfNames.getValue(ns)
                                        LOGGER.trace { "setting ${name}.${md} in $ns to $newName" }
                                        names[ns] = newName
                                        setFromParent[ns] = true
                                    }
                                }
                            }
                        }
                    }
                    val toOverwrite = names.filterKeys { it in needsOverwrite }
                    if (toOverwrite.isNotEmpty()) {
                        LOGGER.trace { "overwriting ${name}.${md} $toOverwrite" }
                        overwriteParentMethodNames(md, toOverwrite)
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
                        methods[method.key] = method.value.filterKeys { it in targets }.toMutableMap()
                    }
                }

                for (intf in interfaceClasses) {
                    for (method in intf.methodData) {
                        if (method.key !in methods) {
                            methods[method.key] = method.value.filterKeys { it in targets }.toMutableMap()
                        }
                    }
                }

                for ((method, names) in methodData) {
                    methodData.getOrPut(method) { mutableMapOf() }.putAll(names)
                }
                visitedNs += targets
            }
        }

        private fun overwriteMethodNames(md: MethodInfo, names: Map<Namespace, String>) {
            if (md in methodData) {
                methodData[md]!!.putAll(names)
                if (md in methods) {
                    clsNode?.visitMethod(
                        names.mapValues { it.value to null } +
                        mapOf(fns to (md.name to md.descriptor))
                    )?.visitEnd()
                }
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
            return "$name;$descriptor"
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
            return "$name;$descriptor"
        }

        override fun equals(other: Any?): Boolean {
            return other is FieldInfo && name == other.name && descriptor == other.descriptor
        }

        override fun hashCode(): Int {
            return name.hashCode() * 31 + descriptor.hashCode()
        }

    }
}