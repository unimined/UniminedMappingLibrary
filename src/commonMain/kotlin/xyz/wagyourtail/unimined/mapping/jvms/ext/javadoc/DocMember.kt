package xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

/**
 * Member:
 *   [UnqualifiedName] [ ( [ParamList] ) ]
 * ParamList:
 *   [SourceType] , [ParamList] | [SourceType]
 */
data class DocMember(
    val name: UnqualifiedName,
    val params: List<SourceType>? = null,
) : Type {

    companion object : TypeCompanion<DocMember> {

        override fun shouldRead(reader: CharReader<*>): Boolean =
            reader.peek()?.let { it.isLetterOrDigit() || it == '<' } == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(read(reader))
        }

        override fun read(reader: CharReader<*>): DocMember {
            val name = reader.takeUntil { it == '(' || it == ' ' || it == '}' || it == ']' }
            val params = if (!reader.exhausted() && reader.peek() == '(') {
                reader.expect('(')
                val body = reader.takeUntil { it == ')' }
                if (!reader.exhausted()) reader.expect(')')
                body.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .map { SourceType.unchecked(it) }
            } else null
            return DocMember(UnqualifiedName.unchecked(name), params)
        }

        override fun unchecked(value: String): DocMember {
            val paren = value.indexOf('(')
            return if (paren < 0) {
                DocMember(UnqualifiedName.unchecked(value))
            } else {
                val name = value.substring(0, paren)
                val params = value.substring(paren + 1, value.lastIndexOf(')').coerceAtLeast(paren + 1))
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .map { SourceType.unchecked(it) }
                DocMember(UnqualifiedName.unchecked(name), params)
            }
        }
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            name.accept(visitor)
            params?.forEach { it.accept(visitor) }
        }
    }

    override fun toString(): String = buildString {
        append(name)
        params?.let { append('(').append(it.joinToString(", ")).append(')') }
    }
}
