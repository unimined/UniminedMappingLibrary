package xyz.wagyourtail.unimined.mapping.inheritance

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.coroutineScope
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.utils.coroutines.parallelMap
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.mapping.PackageMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.WildcardMapping
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.mapping.ParameterMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.LocalVariableMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.ConstantGroupMapping
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.contains
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.element.mapping.ClassMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.FieldMapping
import xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit
import kotlin.collections.filterValues
import xyz.wagyourtail.unimined.mapping.newSmallSet

abstract class InheritanceTree {
    val LOGGER = KotlinLogging.logger {  }

    abstract val fns: Namespace

    abstract val classes: Map<InternalName, ClassInfo>

    suspend fun propagate(tree: MutableMappingTree, targets: Set<Namespace>) = coroutineScope {
        val classes = classes.filterValues { it.inMappings(tree) }

        // write classes
        classes.values.parallelMap {
            it.propagate(tree, targets)
        }

        // write class mappings
        for (clazz in classes.values) {
            writePropData(tree, clazz)
        }
    }

    private fun writePropData(tree: MutableMappingTree, classInfo: ClassInfo) {
        val cls = tree.clazz(mapOf(fns to classInfo.name))
        for (field in classInfo.fields) {
            cls.field(mapOf(fns to field.value))
        }

        for (method in classInfo.methods) {
            val m = cls.method(mapOf(fns to method.value))
            var lvtIdx = if (method.access.contains(AccessFlag.STATIC)) 0 else 1
            for ((i, param) in method.descriptor.getParts().second.withIndex()) {
                m.parameter(i, lvtIdx, emptyMap())
                lvtIdx += param.value.getWidth()
            }
        }

        for ((method, names) in classInfo.methodData) {
            cls.method(
                (names.mapValues { MethodNameAndDescriptor(it.value, null) }.toMap()) +
                        mapOf(fns to method.value)
            )
        }
    }

    fun filtered(tree: MappingTree, output: MappingSink) {
        var currentClass: InternalName? = null
        tree.emit(output) { el ->
            when (el) {
                is ClassMapping -> {
                    currentClass = el.names[fns] ?: return@emit null
                    if (currentClass !in classes) null else el
                }
                is MethodMapping -> {
                    val (name, desc) = el.entries[fns] ?: return@emit null
                    val cls = classes[currentClass] ?: return@emit el
                    val matches = if (desc == null) cls.methods.any { it.name == name }
                        else cls.methods.any { it.name == name && it.descriptor == desc }
                    if (matches) el else null
                }
                is FieldMapping -> {
                    val (name, desc) = el.entries[fns] ?: return@emit null
                    val cls = classes[currentClass] ?: return@emit el
                    val matches = if (desc == null) cls.fields.any { it.name == name }
                        else cls.fields.any { it.name == name && it.descriptor == desc }
                    if (matches) el else null
                }
                is PackageMapping, is WildcardMapping, is ParameterMapping,
                is LocalVariableMapping, is ConstantGroupMapping, is MappingTree -> el
                else -> el
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

        val children: List<ClassInfo> by lazy {
            classes.values.filter { it.superClass == this || it.interfaceClasses.contains(this) }
        }

        val allParents: List<ClassInfo> by lazy {
            buildList {
                if (superClass != null) {
                    add(superClass!!)
                    addAll(superClass!!.allParents)
                }
                for (interfaceClass in interfaceClasses) {
                    add(interfaceClass)
                    addAll(interfaceClass.allParents)
                }
            }
        }

        fun inMappings(tree: MappingTree): Boolean {
            return tree.getClass(fns, name) != null || allParents.any { it.inMappings(tree) }
        }

        private val propagateLock = ReentrantLock()

        /**
         * map of inheritable methods to their names
         */
        val methodData = mutableMapOf<MethodInfo, MutableMap<Namespace, UnqualifiedName>>()
        private val visitedNs = newSmallSet(fns)

        suspend fun propagate(tree: MappingTree, targets: Set<Namespace>): Unit = coroutineScope {
            val targets = targets - visitedNs
            if (targets.isEmpty()) return@coroutineScope
            propagateLock.withLock {
                val targets = targets - visitedNs
                if (targets.isEmpty()) return@withLock

                superClass?.propagate(tree, targets)
                interfaceClasses.parallelMap { it.propagate(tree, targets) }

                val initialMethods = methods.filter { md ->
                    // modify access
                    val acc = AccessFlag.of(ElementType.METHOD, md.access).toMutableSet()
                    val cls = tree.getClass(fns, name)
                    val methods = with (tree) { cls?.getMethods(fns, md.name, md.descriptor) }
                    methods?.flatMap { it.access }?.forEach {
                        it.apply(acc)
                    }
                    val wildcards = with (tree) { cls?.getWildcards(WildcardType.METHOD, fns, md.descriptor) }
                    wildcards?.flatMap { it.access }?.forEach {
                        it.apply(acc)
                    }
                    AccessFlag.isInheritable(acc) && !md.name.value.startsWith("<")
                }.toMutableList()

                for (method in superClass?.methodData?.keys ?: emptySet()) {
                    if (method !in initialMethods) {
                        initialMethods.add(method)
                    }
                }

                for (intf in interfaceClasses) {
                    for (method in intf.methodData.keys) {
                        if (method !in initialMethods) {
                            initialMethods.add(method)
                        }
                    }
                }

                val methods = initialMethods.parallelMap { md ->
                    val found = with (tree) { getClass(fns, name)?.getMethods(fns, md.name, md.descriptor) }
                    // a method may be split across multiple rows when a ns name is missing
                    // (eg `m _ named;(I)Lv; yarn` + `m official;()V _`): merge names from all
                    val merged = mutableMapOf<Namespace, UnqualifiedName>()
                    found?.forEach { m ->
                        for ((ns, n) in m.names) {
                            if (ns in targets && ns !in merged) merged[ns] = n
                        }
                    }
                    md to merged
                }.parallelMap { (md, names) ->
                    // traverse parents, retrieve matching mappings
                    val superNames = superClass?.methodData?.get(md)
                    val interfaces = interfaceClasses.map { it to it.methodData[md] }
                    val needsOverwrite = newSmallSet<Namespace>()
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
                        overwriteParentMethodNames(md, toOverwrite, mutableSetOf(name))
                    }

                    names[fns] = md.name
                    md to names
                }.associate { it }.toMutableMap()

                for ((method, names) in methods) {
                    methodData.getOrPut(method) { mutableMapOf() }.putAll(names)
                }
                visitedNs += targets
            } }


        private suspend fun overwriteMethodNames(md: MethodInfo, names: Map<Namespace, UnqualifiedName>, visited: MutableSet<InternalName>) {
            if (name in visited) return
            visited += name
            if (md in methodData) {
                propagateLock.withLock {
                    methodData[md]!!.putAll(names)
                    overwriteParentMethodNames(md, names, visited)
                    overwriteChildMethodNames(md, names, visited)
                }
            }
        }

        private suspend fun overwriteParentMethodNames(md: MethodInfo, names: Map<Namespace, UnqualifiedName>, visited: MutableSet<InternalName>) {
            superClass?.overwriteMethodNames(md, names, visited)
            for (interfaceClass in interfaceClasses) {
                interfaceClass.overwriteMethodNames(md, names, visited)
            }
        }

        private suspend fun overwriteChildMethodNames(md: MethodInfo, names: Map<Namespace, UnqualifiedName>, visited: MutableSet<InternalName>) {
            for (child in children) {
                child.overwriteMethodNames(md, names, visited)
            }
        }

        override fun toString(): String {
            return "$name extends $superType implements $interfaces"
        }

    }

    class MethodInfo(
        val name: UnqualifiedName,
        val descriptor: MethodDescriptor,
        var access: Int
    ) {

        val value get() = name.withMethodDesc(descriptor)

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
        val name: UnqualifiedName,
        val descriptor: FieldDescriptor,
        var access: Int
    ) {

        val value get() = name.withFieldDesc(descriptor)

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