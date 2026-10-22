package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.commonskt.utils.mapNotNullValues
import xyz.wagyourtail.unimined.mapping.tree.impl.MethodMappingData
import xyz.wagyourtail.unimined.mapping.tree.impl.asData
import xyz.wagyourtail.unimined.mapping.jvms.ext.MethodNameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.*

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
