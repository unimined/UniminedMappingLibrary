package xyz.wagyourtail.unimined.mapping.tree.node._class.member.method

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvableEntry
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MemberNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.EmptyMethodVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InvokableVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ParameterVisitor
import xyz.wagyourtail.unimined.mapping.visitor.WildcardVisitor

class ParameterNode<T: InvokableVisitor<T>>(
    parent: BaseNode<T, *>,
    index: Int?,
    lvOrd: Int?
) : MemberNode<ParameterVisitor, T>(parent), ParameterVisitor, LazyResolvableEntry<ParameterNode<T>, ParameterVisitor> {
    val LOGGER = KotlinLogging.logger {  }

    var index = index
        private set
    var lvOrd = lvOrd
        private set

    private val _names: MutableMap<Namespace, String> = mutableMapOf()
    val names: Map<Namespace, String> get() = _names

    fun setNames(names: Map<Namespace, String>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): ParameterVisitor? {
        val names = names.filterKeys { it in nsFilter }
        if (names.isEmpty()) return null
        return visitor.visitParameter(index, lvOrd, names)
    }

    override fun toString() = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitParameter(EmptyMethodVisitor(), index, lvOrd, names)
//        acceptInner(DelegateParameterVisitor(EmptyParameterVisitor(), delegator), root.namespaces)
    }

    fun doMerge(target: ParameterNode<T>) {
        acceptInner(target, root.namespaces)
    }

    override fun merge(element: ParameterNode<T>): ParameterNode<T>? {
        if (element.index == null && element.lvOrd == null) {
            doMerge(element)
            return element
        }
        if (element.index != null && element.index == index) {
            if (element.lvOrd != null && lvOrd != null && lvOrd != element.lvOrd) {
                LOGGER.warn {
                    """
                        Attempted to join params with same index but different lvOrd's
                        $element
                        $this
                    """.trimIndent()
                }
                return null
            }
            doMerge(element)
            return element
        }
        if (element.lvOrd != null && element.lvOrd == lvOrd) {
            if (index != null) element.index = index
            doMerge(element)
            return element
        }
        return null
    }

}
