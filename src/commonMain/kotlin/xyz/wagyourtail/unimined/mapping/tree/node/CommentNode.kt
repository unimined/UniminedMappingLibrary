package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.visitor.CommentVisitor
import xyz.wagyourtail.unimined.mapping.visitor.MemberVisitor

class CommentNode<U: MemberVisitor<U>>(parent: MemberNode<U, *>) : BaseNode<CommentVisitor, U>(parent), CommentVisitor {
    private val _comments: MutableMap<Namespace, String> = mutableMapOf()
    val comments: Map<Namespace, String> get() = _comments

    fun addComments(comments: Map<Namespace, String>) {
        root.mergeNs(comments.keys)
        this._comments.putAll(comments)
    }

    override fun acceptOuter(visitor: U, minimize: Boolean) = visitor.visitComment(comments)

}