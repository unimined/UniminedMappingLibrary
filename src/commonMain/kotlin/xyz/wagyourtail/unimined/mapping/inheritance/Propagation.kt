package xyz.wagyourtail.unimined.mapping.inheritance

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.coroutineScope
import xyz.wagyourtail.commonskt.collection.defaultedMapOf
import xyz.wagyourtail.commonskt.utils.coroutines.parallelMap
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.MutableMappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.contains
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.newSmallSet
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.emit

private val LOGGER = KotlinLogging.logger {}

/**
 * The bulk propagation machinery — emit-driven incremental propagation via
 * [InheritanceBackedMappingTree] is the normal path; these remain for callers
 * that batch-loaded a tree before wrapping (a manual escape hatch, not the
 * default route).
 */

suspend fun InheritanceTree.propagate(tree: MutableMappingTree, targets: Set<Namespace>) = coroutineScope {
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

private fun InheritanceTree.writePropData(tree: MutableMappingTree, classInfo: InheritanceTree.ClassInfo) {
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

fun InheritanceTree.filtered(tree: MappingTree, output: MappingSink) {
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

suspend fun InheritanceTree.ClassInfo.propagate(tree: MappingTree, targets: Set<Namespace>): Unit = coroutineScope {
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
                    val cls = tree.getClass(outer.fns, name)
                    val methods = with(tree) { cls?.getMethods(outer.fns, md.name, md.descriptor) }
                    methods?.flatMap { it.access }?.forEach {
                        it.apply(acc)
                    }
                    val wildcards = with(tree) { cls?.getWildcards(WildcardType.METHOD, outer.fns, md.descriptor) }
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
                    val found = with(tree) { getClass(outer.fns, name)?.getMethods(outer.fns, md.name, md.descriptor) }
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

                    names[outer.fns] = md.name
                    md to names
                }.associate { it }.toMutableMap()

                for ((method, names) in methods) {
                    methodData.getOrPut(method) { mutableMapOf() }.putAll(names)
                }
                visitedNs += targets
            }
        }

private suspend fun InheritanceTree.ClassInfo.overwriteMethodNames(
            md: InheritanceTree.MethodInfo,
            names: Map<Namespace, UnqualifiedName>,
            visited: MutableSet<InternalName>
        ) {
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

private suspend fun InheritanceTree.ClassInfo.overwriteParentMethodNames(
            md: InheritanceTree.MethodInfo,
            names: Map<Namespace, UnqualifiedName>,
            visited: MutableSet<InternalName>
        ) {
            superClass?.overwriteMethodNames(md, names, visited)
            for (interfaceClass in interfaceClasses) {
                interfaceClass.overwriteMethodNames(md, names, visited)
            }
        }

private suspend fun InheritanceTree.ClassInfo.overwriteChildMethodNames(
            md: InheritanceTree.MethodInfo,
            names: Map<Namespace, UnqualifiedName>,
            visited: MutableSet<InternalName>
        ) {
            for (child in children) {
                child.overwriteMethodNames(md, names, visited)
            }
        }
