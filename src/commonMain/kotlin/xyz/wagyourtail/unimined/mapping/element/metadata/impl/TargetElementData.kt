@file:UseSerializers(
    NamespaceSerializer::class,
    InternalNameSerializer::class,
    UnqualifiedNameSerializer::class,
    FullyQualifiedNameSerializer::class,
    FieldNameAndDescriptorSerializer::class,
    ClassTypeSignatureSerializer::class,
    SignatureSerializer::class,
    AnnotationSerializer::class,
    AccessConditionsSerializer::class,
    ConstantSerializer::class,
    ExpressionSerializer::class
)

package xyz.wagyourtail.unimined.mapping.element.metadata.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.element.metadata.TargetElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.serialization.*

@Serializable
data class TargetElementData(
    override val target: FullyQualifiedName?,
    override val paramIdx: Int?
) : MetadataElementData(), TargetElement
