package xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.JavaTypeSignature
import kotlin.jvm.JvmInline

/**
 * SourceType:
 *   *source-form type* — `int` / `a.b.T` / `int[]` / `T` / `T...`
 *   (serialized to a [JavaTypeSignature] — `I`/`La/b/T;`/`[I`/`TT;`)
 */
@JvmInline
value class SourceType(val value: JavaTypeSignature) : Type {

    companion object : TypeCompanion<SourceType> {

        private val PRIMITIVES = mapOf(
            "boolean" to "Z", "byte" to "B", "short" to "S", "char" to "C",
            "int" to "I", "long" to "J", "float" to "F", "double" to "D", "void" to "V",
        )
        private val BASE_BY_DESC = PRIMITIVES.entries.associate { (n, d) -> d[0] to n }

        override fun shouldRead(reader: CharReader<*>): Boolean =
            reader.peek()?.isLetterOrDigit() == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(read(reader))
        }

        override fun read(reader: CharReader<*>): SourceType =
            unchecked(reader.takeUntil { it in ",) " })

        override fun unchecked(value: String): SourceType =
            SourceType(JavaTypeSignature.unchecked(descOf(value)))

        /** source-form javadoc type → desc — `int`→`I`, `int[]`→`[I`,
         *  `a.b.T`→`La/b/T;`, `T`→`TT;` */
        fun descOf(source: String): String {
            var t = source.trim()
            var arrays = 0
            while (t.endsWith("[]") || t.endsWith("...")) {
                arrays++
                t = t.removeSuffix("[]").removeSuffix("...")
            }
            val base = PRIMITIVES[t]
                ?: if (t.contains('.')) "L${t.replace('.', '/')};" else "T$t;"
            return "[".repeat(arrays) + base
        }

        /** desc → source form — `I`→`int`, `La/b/T;`→`a.b.T`, `[I`→`int[]`,
         *  `TT;`→`T` */
        fun sourceForm(desc: String): String {
            var d = desc
            var arrays = 0
            while (d.startsWith("[")) { arrays++; d = d.substring(1) }
            val base = BASE_BY_DESC[d.firstOrNull()]
                ?: if (d.startsWith("T") && d.endsWith(";")) d.substring(1, d.length - 1)
                else d.removePrefix("L").removeSuffix(";").replace('/', '.')
            return base + "[]".repeat(arrays)
        }
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) value.accept(visitor)
    }

    override fun toString(): String = sourceForm(value.type)
}
