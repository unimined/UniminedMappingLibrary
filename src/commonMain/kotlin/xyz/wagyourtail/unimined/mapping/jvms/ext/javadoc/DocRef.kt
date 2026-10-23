package xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc

import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.jvms.Type
import xyz.wagyourtail.unimined.mapping.jvms.TypeCompanion
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName

/**
 * Ref:
 *   [InternalName] [ # [DocMember] ]
 *   # [DocMember]          — owner-less ("this class")
 */
data class DocRef(
    val owner: InternalName?,
    val member: DocMember?,
) : Type {

    init {
        require(owner != null || member != null) { "DocRef must carry an owner or a member" }
    }

    /** class/package ref when there's no member part */
    val isClassRef get() = member == null

    companion object : TypeCompanion<DocRef> {

        override fun shouldRead(reader: CharReader<*>): Boolean =
            reader.peek()?.let { it.isLetterOrDigit() || it == '#' } == true

        override fun read(reader: CharReader<*>, append: (Any) -> Unit) {
            append(read(reader))
        }

        override fun read(reader: CharReader<*>): DocRef {
            val head = reader.takeUntil { it == ' ' || it == '#' || it == '}' || it == ']' }
            var member: DocMember? = null
            if (!reader.exhausted() && reader.peek() == '#') {
                reader.expect('#')
                member = DocMember.read(reader)
            }
            val owner = head.takeIf { it.isNotEmpty() }
                ?.let { InternalName.unchecked(it.replace('.', '/')) }
            return DocRef(owner, member)
        }

        override fun unchecked(value: String): DocRef {
            val hash = value.indexOf('#')
            return if (hash < 0) {
                DocRef(InternalName.unchecked(value.replace('.', '/')), null)
            } else {
                val owner = value.substring(0, hash).takeIf { it.isNotEmpty() }
                    ?.let { InternalName.unchecked(it.replace('.', '/')) }
                DocRef(owner, DocMember.unchecked(value.substring(hash + 1)))
            }
        }
    }

    override fun accept(visitor: (Any) -> Boolean) {
        if (visitor(this)) {
            owner?.accept(visitor)
            member?.accept(visitor)
        }
    }

    override fun toString(): String = buildString {
        owner?.let { append(it.value.replace('/', '.')) }
        member?.let { append('#').append(it) }
    }
}
