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
import xyz.wagyourtail.unimined.mapping.tree.MemoryMappingTree
import xyz.wagyourtail.unimined.mapping.visitor.*
import xyz.wagyourtail.unimined.mapping.visitor.delegate.Delegator
import xyz.wagyourtail.unimined.mapping.visitor.delegate.delegator

abstract class InheritanceTree(val tree: AbstractMappingTree) {
    val LOGGER = KotlinLogging.logger {  }

    abstract val fns: Namespace

    abstract val classes: Map<InternalName, ClassInfo>

    suspend fun propagate(targets: Set<Namespace>) = coroutineScope {
        // write classes
        classes.values.parallelMap {
            it.propagate(targets)
        }

        // write class mappings
        if (tree is MemoryMappingTree) {
            val classLock = Mutex()

            classes.values.parallelMap { clazz ->
                classLock.withLock {
                    tree.visitClass(fns to clazz.name)
                }?.use {
                    writePropData(clazz)
                }
            }

        } else {
            for (clazz in classes.values) {

                tree.visitClass(fns to clazz.name)?.use {
                    writePropData(clazz)
                }

            }
        }
    }

    private fun ClassVisitor.writePropData(classInfo: ClassInfo) {
        for (field in classInfo.fields) {
            visitField(
                mapOf(fns to (field.name to field.descriptor))
            )?.visitEnd()
        }

        for (method in classInfo.methods) {
            visitMethod(
                mapOf(fns to (method.name to method.descriptor))
            )?.use {
                var lvtIdx = if (method.access.contains(AccessFlag.STATIC)) 0 else 1
                for ((i, param) in method.descriptor.getParts().second.withIndex()) {
                    visitParameter(i, lvtIdx, emptyMap())?.visitEnd()
                    lvtIdx += param.value.getWidth()
                }
            }
        }

        for ((method, names) in classInfo.methodData) {
            visitMethod(
                (names.mapValues { it.value to null }.toMap()) +
                        mapOf(fns to (method.name to method.descriptor))
            )?.visitEnd()
        }
    }

    fun filtered(output: AbstractMappingTree) {

        tree.accept(output.delegator(object : Delegator() {

            var currentClass: InternalName? = null

            override fun visitClass(delegate: MappingVisitor, names: Map<Namespace, InternalName>): ClassVisitor? {
                currentClass = names[fns] ?: return null
                if (currentClass !in classes) return null
                return super.visitClass(delegate, names)
            }

            override fun visitMethod(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, MethodDescriptor?>>
            ): MethodVisitor? {
                val (name, desc) = names[fns] ?: return null
                if (desc == null) {
                    if (classes[currentClass]!!.methods.any { it.name == name }) {
                        return super.visitMethod(delegate, names)
                    }
                } else {
                    if (classes[currentClass]!!.methods.any { it.name == name && it.descriptor == desc }) {
                        return super.visitMethod(delegate, names)
                    }
                }
                return null
            }

            override fun visitField(
                delegate: ClassVisitor,
                names: Map<Namespace, Pair<String, FieldDescriptor?>>
            ): FieldVisitor? {
                val (name, desc) = names[fns] ?: return null
                if (desc == null) {
                    if (classes[currentClass]!!.fields.any { it.name == name }) {
                        return super.visitField(delegate, names)
                    }
                } else {
                    if (classes[currentClass]!!.fields.any { it.name == name && it.descriptor == desc }) {
                        return super.visitField(delegate, names)
                    }
                }
                return null
            }

        }))

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

                val initialMethods = methods.filter { md ->
                    // modify access
                    val acc = AccessFlag.of(ElementType.METHOD, md.access).toMutableSet()
                    val methods = tree.getClass(fns, name)?.getMethods(fns, md.name, md.descriptor)
                    methods?.flatMap { it.access }?.forEach {
                        it.apply(acc)
                    }
                    AccessFlag.isInheritable(acc) && !md.name.startsWith("<")
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
                    md to (tree.getClass(fns, name)?.getMethods(fns, md.name, md.descriptor)?.firstOrNull()?.names?.filterKeys { it in targets } ?: emptyMap()).toMutableMap()
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

                    names[fns] = md.name
                    md to names
                }.associate { it }.toMutableMap()

                for ((method, names) in methods) {
                    methodData.getOrPut(method) { mutableMapOf() }.putAll(names)
                }
                visitedNs += targets
            }
        }


        private fun overwriteMethodNames(md: MethodInfo, names: Map<Namespace, String>) {
            if (md in methodData) {
                methodData[md]!!.putAll(names)
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
            return "$name extends $superType implements $interfaces"
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