package xyz.wagyourtail.unimined.mapping.jvms.four.two.two

import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.util.checkedToChar
import xyz.wagyourtail.unimined.mapping.util.takeUTF8Until
import kotlin.jvm.JvmInline

@JvmInline
value class MethodName private constructor(val value: UnqualifiedName) {

    companion object: TypeCompanion<MethodName> {
        override fun shouldRead(reader: BufferedSource): Boolean {
            return UnqualifiedName.shouldRead(reader)
        }

        override fun read(reader: BufferedSource) = try {
            val value = UnqualifiedName.read(reader)
            if (value.value.contains('<') || value.value.contains('>')) {
                if (value.value != "<init>" && value.value != "<clinit>") {
                    throw IllegalArgumentException("Invalid method name, cannot contain < or >")
                }
            }
            MethodName(value)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid unqualified name", e)
        }

    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            value.accept(visitor)
        }
    }

    override fun toString() = value.toString()

}