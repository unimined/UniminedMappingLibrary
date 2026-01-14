package xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.method

import io.github.oshai.kotlinlogging.KotlinLogging
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.tree.mapping.BaseMappingImpl
import xyz.wagyourtail.unimined.mapping.tree.mapping.LazyResolvableEntry
import xyz.wagyourtail.unimined.mapping.tree.mapping._class.member.MemberMappingImpl
import xyz.wagyourtail.unimined.mapping.visitor.EmptyMethodMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.EmptyParameterMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.InvokableMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.ParameterMapping
import xyz.wagyourtail.unimined.mapping.visitor.ParameterMappingVisitor
import xyz.wagyourtail.unimined.mapping.visitor.delegate.DelegateParameterMappingVisitor
import kotlin.collections.filterKeys

class ParameterMappingImpl<T: InvokableMappingVisitor>(
    parent: BaseMappingImpl<T, *>,
    index: Int?,
    lvOrd: Int?
) : MemberMappingImpl<ParameterMappingVisitor, T>(parent), ParameterMapping, LazyResolvableEntry<ParameterMappingImpl<T>, ParameterMappingVisitor>, ParameterMappingVisitor {
    val LOGGER = KotlinLogging.logger {  }

    override var index = index
        private set
    override var lvOrd = lvOrd
        private set

    private val _names: MutableMap<Namespace, UnqualifiedName> = mutableMapOf()
    override val names: Map<Namespace, UnqualifiedName> get() = _names

    fun setNames(names: Map<Namespace, UnqualifiedName>) {
        root.mergeNs(names.keys)
        this._names.putAll(names)
    }

    override fun acceptOuter(visitor: T, nsFilter: Collection<Namespace>): ParameterMappingVisitor? {
        val names = names.filterKeys { it in nsFilter }
        return visitor.visitParameter(index, lvOrd, names)
    }

    fun doMerge(target: ParameterMappingImpl<T>) {
        target.setNames(names)
        acceptInner(target, root.namespaces, false)
    }

    override fun merge(element: ParameterMappingImpl<T>): Boolean {
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
        delegator.namespaces = root.namespaces.toList()
        delegator.visitParameter(EmptyMethodMappingVisitor(), index, lvOrd, names)
        if (inner) acceptInner(DelegateParameterMappingVisitor(EmptyParameterMappingVisitor(), delegator), root.namespaces, true)
    }

}
