package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.MutableMetadataParent
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

sealed interface MutableInvokableMapping : InvokableMapping, MutableMetadataParent {

    /**
     * find or create a parameter node matching the given index/lvOrd (merge-by-identity)
     */
    fun parameter(index: Int?, lvOrd: Int?, names: Map<Namespace, UnqualifiedName>): MutableParameterMapping

    /**
     * find or create a local variable node matching the given lvOrd/startOp (merge-by-identity)
     */
    fun localVariable(lvOrd: Int, startOp: Int?, names: Map<Namespace, UnqualifiedName>): MutableLocalVariableMapping

}
