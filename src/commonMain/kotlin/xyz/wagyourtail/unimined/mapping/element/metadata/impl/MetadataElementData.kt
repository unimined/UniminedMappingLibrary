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
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.serialization.*

/** sealed base for default metadata impls — gives polymorphic serialization for free */
@Serializable
sealed class MetadataElementData : MetadataElement
