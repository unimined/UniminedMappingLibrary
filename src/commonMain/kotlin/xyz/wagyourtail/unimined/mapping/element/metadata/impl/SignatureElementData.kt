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
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.SignatureElement
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.serialization.*

@Serializable
data class SignatureElementData(
    override val value: Signature,
    override val baseNs: Namespace
) : MetadataElementData(), SignatureElement
