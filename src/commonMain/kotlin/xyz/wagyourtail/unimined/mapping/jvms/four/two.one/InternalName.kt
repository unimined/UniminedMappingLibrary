package xyz.wagyourtail.unimined.mapping.jvms.four.two.one

import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.commonskt.reader.CharReader
import kotlin.jvm.JvmInline

/**
 * InternalName:
 *   [[PackageName]] [UnqualifiedName]
 */
@JvmInline
value class InternalName private constructor(val value: String) {

    companion object: TypeCompanion<InternalName> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllegalChars
        }

        override fun read(reader: CharReader<*>) = try {
            InternalName(buildString {
                append(PackageName.read(reader))
                append(UnqualifiedName.read(reader))
            })
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid internal name", e)
        }

        override fun unchecked(value: String) = InternalName(value)

        operator fun invoke(pkg: PackageName, name: UnqualifiedName) = InternalName("${pkg.value}${name.value}")
    }

    fun getParts(): Pair<PackageName, UnqualifiedName> {
        val parts = value.split("/")
        val pkg = parts.dropLast(1).joinToString("/").let { if (it.isEmpty()) it else "$it/" }
        return PackageName.unchecked(pkg) to UnqualifiedName.unchecked(parts.last())
    }

    fun accept(visitor: (Any, Boolean) -> Boolean) {
        if (visitor(this, false)) {
            val parts = getParts()
            parts.first.accept(visitor)
            parts.second.accept(visitor)
        }
    }

    override fun toString() = value

}