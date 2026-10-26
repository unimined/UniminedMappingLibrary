package xyz.wagyourtail.unimined.mapping.jvms.four.two.one

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.JVMS
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCache
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import kotlin.jvm.JvmInline

/**
 * InternalName:
 *   [[PackageName]] [UnqualifiedName]
 */
@JvmInline
value class InternalName private constructor(val value: String) : Type {

    companion object: TypeCompanion<InternalName> {
        override fun shouldRead(reader: CharReader<*>): Boolean {
            return reader.take() !in JVMS.unqualifiedNameIllegalChars
        }

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            val cname = reader.takeUntil { it in JVMS.unqualifiedNameIllegalChars && it != '/' }
            if (cname.isEmpty() || cname.contains("//")) {
                throw IllegalArgumentException("Invalid internal name")
            }
            if (!cname.contains("/")) {
                append(UnqualifiedName.unchecked(cname))
            } else {
                append(PackageName.unchecked(cname.substringBeforeLast("/") + "/"))
                append(UnqualifiedName.unchecked(cname.substringAfterLast("/")))
            }
        }

        /** char-scan fast path — validates and wraps [value] directly */
        override fun read(value: String, unchecked: Boolean): InternalName {
            if (!unchecked && (value.isEmpty() || value.contains("//") ||
                    value.any { it == '.' || it == ';' || it == '[' })
            ) {
                throw IllegalArgumentException("Invalid internal name")
            }
            return InternalName(value)
        }

        override fun unchecked(value: String) = InternalName(value)

        operator fun invoke(pkg: PackageName, name: UnqualifiedName) = InternalName("${pkg.value}${name.value}")
    }

    fun getParts(): Pair<PackageName, UnqualifiedName> = TypeCache.getOrPut(Companion, value) {
        val parts = it.split("/")
        val pkg = parts.dropLast(1).joinToString("/").let { s -> if (s.isEmpty()) s else "$s/" }
        PackageName.unchecked(pkg) to UnqualifiedName.unchecked(parts.last())
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            val parts = getParts()
            parts.first.accept(visitor)
            parts.second.accept(visitor)
        }
    }

    override fun toString() = value

}