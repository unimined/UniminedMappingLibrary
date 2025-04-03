package xyz.wagyourtail.unimined.mapping.tree.node._class.member.method

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.tree.node.BaseNode
import xyz.wagyourtail.unimined.mapping.tree.node.LazyResolvableEntry
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.MemberNode
import xyz.wagyourtail.unimined.mapping.tree.node._class.member.WildcardNode
import xyz.wagyourtail.unimined.mapping.visitor.EmptyMethodVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyParameterVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InvokableVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ParameterVisitor
import xyz.wagyourtail.unimined.mapping.visitor.WildcardVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateParameterVisitor

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
        return visitor.visitParameter(index, lvOrd, names)
    }

    fun doMerge(target: ParameterNode<T>) {
        target.setNames(names)
        acceptInner(target, root.namespaces, false)
    }

    override fun merge(element: ParameterNode<T>): Boolean {
        if (element.index == null && element.lvOrd == null) {
            element.index = index
            element.lvOrd = lvOrd
            doMerge(element)
            return true
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
                return false
            }
            if (lvOrd != null) element.lvOrd = lvOrd
            doMerge(element)
            return true
        }
        if (element.lvOrd != null && element.lvOrd == lvOrd) {
            if (index != null) element.index = index
            doMerge(element)
            return true
        }
        return false
    }

    override fun toUMF(inner: Boolean) = buildString {
        val delegator = UMFWriter.UMFWriterDelegator(::append, true)
        delegator.namespaces = root.namespaces
        delegator.visitParameter(EmptyMethodVisitor(), index, lvOrd, names)
        if (inner) acceptInner(DelegateParameterVisitor(EmptyParameterVisitor(), delegator), root.namespaces, true)
    }

}
