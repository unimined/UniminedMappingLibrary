package xyz.wagyourtail.unimined.mapping.element.mapping

import xyz.wagyourtail.unimined.mapping.element.MappingElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InvokableMetadataParent

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
