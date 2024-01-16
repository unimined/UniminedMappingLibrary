package xyz.wagyourtail.unimined.mapping.tree.node

import xyz.wagyourtail.unimined.mapping.visitor.BaseVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ExtensionVisitor

abstract class ExtensionNode<T: ExtensionVisitor<T, V>, U: BaseVisitor<U>, V>(parent: BaseNode<U, *>, val key: String) : BaseNode<T, U>(parent), ExtensionVisitor<T, V>