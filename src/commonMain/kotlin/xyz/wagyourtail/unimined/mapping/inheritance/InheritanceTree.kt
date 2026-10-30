package xyz.wagyourtail.unimined.mapping.inheritance

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.locks.ReentrantLock
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.WildcardType
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.newSmallSet

abstract class InheritanceTree {
    val LOGGER = KotlinLogging.logger { }

    abstract val fns: Namespace

    abstract val classes: Map<InternalName, ClassInfo>

    /** parent → direct-subclass index — built once; [ClassInfo.children]
     *  must not re-scan [classes] per query (O(n) per class → O(n²) walks). */
    internal val childrenIndex: Map<InternalName, List<ClassInfo>> by lazy {
        val m = HashMap<InternalName, MutableList<ClassInfo>>()
        for (c in classes.values) {
            c.superType?.let { m.getOrPut(it) { mutableListOf() }.add(c) }
            for (i in c.interfaces) m.getOrPut(i) { mutableListOf() }.add(c)
        }
        m
    }


    /**
     * Fold declared [xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement]s
     * (member rows + desc-grouped wildcards) into the scanned member flags — a
     * widen (private→protected) then behaves like ordinary inheritance for
     * propagation, matching AW/AT pipelines where the flag change precedes
     * name propagation. Runs against `fns`-namespace rows in [tree].
     */
    fun applyAccessChanges(tree: MappingTree) {
        // iterate the mapping rows and resolve through [classes] —
        // get() materializes on lazy trees, so classpath entries the
        // overlay widens are pulled in before flag folding; on eager
        // trees this is the same materialized-set lookup as before
        for (cls in tree.classes) {
            val fnsName = cls.names[fns] ?: continue
            val info = classes[fnsName] ?: continue
            for (mi in info.methods) {
                val els = with(tree) {
                    cls.getMethods(fns, mi.name, mi.descriptor).flatMap { it.access } +
                            cls.getWildcards(WildcardType.METHOD, fns, mi.descriptor).flatMap { it.access }
                }
                if (els.isEmpty()) continue
                val orig = AccessFlag.of(ElementType.METHOD, mi.access)
                val acc = orig.toMutableSet()
                els.filter { it.condition.check(orig) }.forEach { it.apply(acc) }
                mi.access = AccessFlag.toInt(acc)
            }
            for (fi in info.fields) {
                val els = cls.fields.filter {
                    it.entries[fns]?.let { e -> e.name == fi.name && e.descriptor == fi.descriptor } == true
                }
                    .flatMap { it.metadata.filterIsInstance<xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement>() } +
                        with(tree) { cls.getWildcards(WildcardType.FIELD, fns, fi.descriptor) }.flatMap { it.access }
                if (els.isEmpty()) continue
                val orig = AccessFlag.of(ElementType.FIELD, fi.access)
                val acc = orig.toMutableSet()
                els.filter { it.condition.check(orig) }.forEach { it.apply(acc) }
                fi.access = AccessFlag.toInt(acc)
            }
        }
    }

    

    

    

    inner class ClassInfo(
        val name: InternalName,
        val superType: InternalName?,
        val interfaces: List<InternalName>,
    ) {

        /** this ClassInfo's owning graph — lets extensions reach [fns]/[classes] */
        internal val outer get() = this@InheritanceTree
        val methods = mutableListOf<MethodInfo>()

        val fields = mutableListOf<FieldInfo>()

        val superClass by lazy {
            this@InheritanceTree.classes[superType]
        }

        val interfaceClasses by lazy {
            interfaces.mapNotNull { this@InheritanceTree.classes[it] }
        }

        val children: List<ClassInfo> by lazy {
            this@InheritanceTree.childrenIndex[name] ?: emptyList()
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

        internal val propagateLock = ReentrantLock()

        /**
         * map of inheritable methods to their names
         */
        val methodData = mutableMapOf<MethodInfo, MutableMap<Namespace, UnqualifiedName>>()
        internal val visitedNs = newSmallSet(fns)

        

        

        

        

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