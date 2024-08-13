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
import xyz.wagyourtail.unimined.mapping.util.CharReader
import xyz.wagyourtail.unimined.mapping.util.parallelMap

open class InheritanceTree(val tree: AbstractMappingTree, val ns: Namespace) {
    val LOGGER = KotlinLogging.logger {  }

    private val _classes = mutableMapOf<InternalName, ClassInfo>()
    val classes: Map<InternalName, ClassInfo> get() = _classes

    suspend fun propagate() = coroutineScope {
        classes.values.parallelMap {
            it.propagate()
        }
    }

    fun read(data: CharReader) {
        var ci: ClassInfo? = null
        while (!data.exhausted()) {
            if (data.peek() == '\n') {
                data.take()
                continue
            }
            var col = data.takeNextLiteral() ?: continue
            var indent = 0
            while (col.isEmpty()) {
                indent++
                col = data.takeNextLiteral() ?: continue
            }
            if (indent > 1) {
                throw IllegalArgumentException("expected method, found double indent")
            }
            if (indent == 0) {
                val cls = col
                val sup = data.takeNextLiteral()!!.ifEmpty { null }
                val intf = mutableListOf<String>()
                while (true) {
                    intf.add(data.takeNextLiteral() ?: break)
                }
                ci = ClassInfo(InternalName.read(cls), sup?.let { InternalName.read(it) }, intf.map { InternalName.read(it) })
                _classes[ci.name] = ci
            } else {
                val acc = col.split("|")
                val name = data.takeNextLiteral()!!
                val desc = FieldOrMethodDescriptor.read(data.takeNextLiteral()!!)

                if (desc.isMethodDescriptor()) {
                    ci!!._methods.add(
                        MethodInfo(
                            name,
                            desc.getMethodDescriptor(),
                            AccessFlag.toInt(acc.map { AccessFlag.valueOf(it.uppercase()) }.toSet())
                        )
                    )
                } else {
                    ci!!._fields.add(
                        FieldInfo(
                            name,
                            desc.getFieldDescriptor(),
                            AccessFlag.toInt(acc.map { AccessFlag.valueOf(it.uppercase()) }.toSet())
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

            for (method in cls._methods) {
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
        val _methods = mutableListOf<MethodInfo>()
        val methods: List<MethodInfo> get() = _methods

        val _fields = mutableListOf<FieldInfo>()
        val fields: List<FieldInfo> get() = _fields

        val superClass by lazy {
            this@InheritanceTree.classes[superType]
        }

        val interfaceClasses by lazy {
            interfaces.map { this@InheritanceTree.classes[it] }
        }

        val clsNode by lazy {
            tree.getClass(ns, name)
        }

        val propagateLock = Mutex()

        lateinit var methodData: MutableMap<Pair<String, MethodDescriptor>, Map<Namespace, String>>

        suspend fun propagate(): Map<Pair<String, MethodDescriptor>, Map<Namespace, String>> = coroutineScope {
            if (::methodData.isInitialized) methodData
            propagateLock.withLock {
                if (::methodData.isInitialized) methodData
                methods.filter { md ->
                    // modify access
                    val acc = AccessFlag.of(ElementType.METHOD, md.access).toMutableSet()
                    val methods = clsNode?.getMethods(ns, md.name, md.descriptor)
                    methods?.flatMap { it.access }?.forEach {
                        it.apply(acc)
                    }
                    AccessFlag.isInheritable(acc)
                }.parallelMap {
                    // traverse parents, retrieve matching mappings

                }
                methodData
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