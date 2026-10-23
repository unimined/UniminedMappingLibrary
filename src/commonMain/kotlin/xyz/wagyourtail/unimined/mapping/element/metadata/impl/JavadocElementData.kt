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
    ExpressionSerializer::class,
    JavadocSerializer::class
)

package xyz.wagyourtail.unimined.mapping.element.metadata.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.JavadocElement
import xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.Javadoc
import xyz.wagyourtail.unimined.mapping.serialization.*

@Serializable
data class JavadocElementData(
    override val value: Javadoc,
    override val baseNs: Namespace
) : MetadataElementData(), JavadocElement
