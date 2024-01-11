package xyz.wagyourtail.unimined.mapping.tree

import xyz.wagyourtail.unimined.mapping.ElementType

/**
 * this is the base for classes/fields/methods to inherit
 * it implements helpers for accessing sub-elements
 */
interface MemberPropertyView<V: MemberPropertyView<V>>: NamespacedProperty<V> {
    val signature: SignaturePropertyView?
        get() = this.get<SignaturePropertyView>(ElementType.SIGNATURE).firstOrNull()

    val comment: CommentPropertyView?
        get() = this.get<CommentPropertyView>(ElementType.COMMENT).firstOrNull()

    val annotation: Collection<AnnotationPropertyView>
        get() = this[ElementType.ANNOTATION]

    val access: Collection<AccessPropertyView>
        get() = this[ElementType.ACCESS]

}

interface MemberProperty<V: MemberPropertyView<V>>: MemberPropertyView<V>, NamespacedPropertyView<V> {
    override var signature: SignatureProperty?
        get() = this.get<SignatureProperty>(ElementType.SIGNATURE).firstOrNull()
        set(value) {
            this.get<SignatureProperty>(ElementType.SIGNATURE).clear()
            if (value != null) {
                this.get<SignatureProperty>(ElementType.SIGNATURE).add(value)
            }
        }

    override var comment: CommentProperty?
        get() = this.get<CommentProperty>(ElementType.COMMENT).firstOrNull()
        set(value) {
            this.get<CommentProperty>(ElementType.COMMENT).clear()
            if (value != null) {
                this.get<CommentProperty>(ElementType.COMMENT).add(value)
            }
        }

    override val annotation: MutableCollection<AnnotationProperty>
        get() = this[ElementType.ANNOTATION]

    override val access: MutableCollection<AccessProperty>
        get() = this[ElementType.ACCESS]

}