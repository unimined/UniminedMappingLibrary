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
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.serialization.*

import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation

@Serializable
data class AnnotationElementData(
    override val type: AddRemoveModify,
    override val baseNs: Namespace,
    override val annotation: Annotation
) : MetadataElementData(), AnnotationElement
