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

/**
 * shared parent for elements that can own parameters/local variables
 * (methods and method-wildcards)
 */
sealed interface InvokableMapping : MappingElement, InvokableMetadataParent {

    val parameters: Iterable<ParameterMapping>

    val localVariables: Iterable<LocalVariableMapping>

    override val contents: Iterable<MappingElement>
        get() = parameters + localVariables

}
