package xyz.wagyourtail.unimined.mapping.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.jvms.ext.*
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.ext.constant.Constant
import xyz.wagyourtail.unimined.mapping.jvms.ext.expression.Expression
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.Signature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.`class`.ClassSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.field.FieldSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.method.MethodSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.seven.nine.one.reference.ClassTypeSignature
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.FieldDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.PackageName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName

private inline fun <T> stringSer(
    name: String,
    crossinline read: (String) -> T,
    crossinline write: (T) -> String = { it.toString() }
) = object : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(write(value))
    override fun deserialize(decoder: Decoder): T = read(decoder.decodeString())
}

object NamespaceSerializer : KSerializer<Namespace> by stringSer("Namespace", { Namespace(it) }, { it.name })
object InternalNameSerializer : KSerializer<InternalName> by stringSer("InternalName", { InternalName.read(it) }, { it.value })
object PackageNameSerializer : KSerializer<PackageName> by stringSer("PackageName", { PackageName.read(it) }, { it.value })
object UnqualifiedNameSerializer : KSerializer<UnqualifiedName> by stringSer("UnqualifiedName", { UnqualifiedName.read(it) }, { it.value })
object FieldNameAndDescriptorSerializer : KSerializer<FieldNameAndDescriptor> by stringSer("FieldNameAndDescriptor", { FieldNameAndDescriptor.read(it) }, { it.value })
object MethodNameAndDescriptorSerializer : KSerializer<MethodNameAndDescriptor> by stringSer("MethodNameAndDescriptor", { MethodNameAndDescriptor.read(it) }, { it.value })
object NameAndDescriptorSerializer : KSerializer<NameAndDescriptor> by stringSer("NameAndDescriptor", { NameAndDescriptor.read(it) }, { it.toString() })
object FieldOrMethodDescriptorSerializer : KSerializer<FieldOrMethodDescriptor> by stringSer("FieldOrMethodDescriptor", { FieldOrMethodDescriptor.read(it) }, { it.toString() })
object FieldDescriptorSerializer : KSerializer<FieldDescriptor> by stringSer("FieldDescriptor", { FieldDescriptor.read(it) }, { it.value.value })
object MethodDescriptorSerializer : KSerializer<MethodDescriptor> by stringSer("MethodDescriptor", { MethodDescriptor.read(it) }, { it.value })
object JavadocSerializer : KSerializer<xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.Javadoc> by stringSer("Javadoc", { xyz.wagyourtail.unimined.mapping.jvms.ext.javadoc.Javadoc.unchecked(it) }, { it.toString() })
object FullyQualifiedNameSerializer : KSerializer<FullyQualifiedName> by stringSer("FullyQualifiedName", { FullyQualifiedName.read(it) }, { it.value })
object ClassTypeSignatureSerializer : KSerializer<ClassTypeSignature> by stringSer("ClassTypeSignature", { ClassTypeSignature.read(it) }, { it.value.toString() })
object ClassSignatureSerializer : KSerializer<ClassSignature> by stringSer("ClassSignature", { ClassSignature.read(it) }, { it.value })
object MethodSignatureSerializer : KSerializer<MethodSignature> by stringSer("MethodSignature", { MethodSignature.read(it) }, { it.value })
object FieldSignatureSerializer : KSerializer<FieldSignature> by stringSer("FieldSignature", { FieldSignature.read(it) }, { it.value.toString() })
object AnnotationSerializer : KSerializer<Annotation> by stringSer("Annotation", { Annotation.read(it) }, { it.value })
object AccessConditionsSerializer : KSerializer<AccessConditions> by stringSer("AccessConditions", { AccessConditions.read(it) }, { it.value })
object ConstantSerializer : KSerializer<Constant> by stringSer("Constant", { Constant.read(it) }, { it.value })
object ExpressionSerializer : KSerializer<Expression> by stringSer("Expression", { Expression.read(it) }, { it.value.toString() })

/** Signature is an interface — tag with the signature kind for round-tripping */
object SignatureSerializer : KSerializer<Signature> by stringSer("Signature", { value ->
    when (value.substringBefore('|')) {
        "c" -> ClassSignature.read(value.substringAfter('|'))
        "m" -> MethodSignature.read(value.substringAfter('|'))
        "f" -> FieldSignature.read(value.substringAfter('|'))
        else -> throw IllegalArgumentException("unknown signature kind in $value")
    }
}, { value ->
    when (value) {
        is ClassSignature -> "c|$value"
        is MethodSignature -> "m|$value"
        is FieldSignature -> "f|$value"
        else -> throw IllegalArgumentException("unknown signature type $value")
    }
})
