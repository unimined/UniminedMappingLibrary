package xyz.wagyourtail.unimined.mapping.propogator

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.tree.MappingTree

fun MappingTree.propogate(info: PropogationInfo<*>, targets: Set<Namespace>) {
    info.propogate(this, targets)
}

abstract class PropogationInfo<T: PropogationInfo<T>>(val namespace: Namespace) {
    private val LOGGER = KotlinLogging.logger {  }

    abstract val classes: Map<InternalName, ClassInfo<T>>

    private val propogationList: Map<Pair<InternalName, Pair<String, MethodDescriptor?>>, Set<InternalName>> by lazy {
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

    fun propogate(tree: MappingTree, targetNs: Set<Namespace>) {
        val names = mutableMapOf<Pair<InternalName, Pair<String, MethodDescriptor?>>, MutableMap<Namespace, MutableSet<String>>>()
        val propogationListRemaining = propogationList.toMutableMap()
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
                names[target] = methodNames
            }
        }

        for ((method, nsNames) in names) {
            val clazz = tree.getClass(namespace, method.first) ?: continue
            clazz.visitMethod(mapOf(namespace to (method.second.first to method.second.second)) + nsNames.mapValues { it.value.first() to null })
        }
    }

    abstract class ClassInfo<T: PropogationInfo<T>>(val info: T, val name: InternalName) {
        protected abstract val parent: InternalName?
        protected abstract val interfaces: List<InternalName>

        protected abstract val methods: List<Pair<String, MethodDescriptor?>>

        private fun traverseParents(): Sequence<ClassInfo<T>> {
            var current: ClassInfo<T>? = this
            return generateSequence {
                val c = current
                current = c?.parent?.let { info.classes[it] }
                c
            }.flatMap { c ->
                sequenceOf(c) + c.interfaces.asSequence()
                    .mapNotNull { info.classes[it] }
                    .flatMap { sequenceOf(it) + it.traverseParents() }
            }
        }

        val resolved: Map<Pair<String, MethodDescriptor?>, Set<InternalName>> by lazy {
            val md = mutableMapOf<Pair<String, MethodDescriptor?>, MutableSet<InternalName>>()
            for (c in traverseParents()) {
                for ((name, desc) in c.methods) {
                    md.getOrPut(name to desc) { mutableSetOf() } += c.name
                }
            }
            md
        }

    }
}
