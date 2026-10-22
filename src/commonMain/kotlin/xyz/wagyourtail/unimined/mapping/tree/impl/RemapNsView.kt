package xyz.wagyourtail.unimined.mapping.tree.impl

import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*

/**
 * lazy namespace-remap view — re-keys [Namespace]-keyed maps and [NsBoundMetadata.baseNs]
 * without materializing a new tree. replaces the old `mapNs` delegator.
 */
fun <V> Map<Namespace, V>.remapNsKeys(fn: (Namespace) -> Namespace): Map<Namespace, V> =
    mapKeys { fn(it.key) }

fun MetadataElement.remapNs(fn: (Namespace) -> Namespace): MetadataElement = when (this) {
    is JavadocElement -> JavadocElement(value, fn(baseNs))
    is SignatureElement -> SignatureElement(value, fn(baseNs))
    is AnnotationElement -> AnnotationElement(type, fn(baseNs), annotation)
    is ExceptionElement -> ExceptionElement(type, exception, fn(baseNs))
    is SealElement -> SealElement(type, name, fn(baseNs))
    is InterfaceElement -> InterfaceElement(type, name, fn(baseNs))
    is EnumExtensionElement -> EnumExtensionElement(type, name, fn(baseNs))
    is InnerClassElement -> InnerClassElementData(
        type,
        names.remapNsKeys(fn).toSmallMap(),
        metadata.asMetadataDataList()
    )
    // group-relative (constant group carries the baseNs) or ns-free — unchanged
    is SyntheticBridgeElement -> SyntheticBridgeElementData(names.remapNsKeys(fn).toSmallMap())
    is AccessElement, is ConstantElement, is TargetElement, is ExpressionElement -> this
    else -> this
}

fun MappingElement.remapNs(fn: (Namespace) -> Namespace): MappingElement = when (this) {
    is MappingTree -> {
        val self = this
        object : MappingTree {
            override val namespaces get() = self.namespaces.map(fn)
            override val packages get() = self.packages.map { it.remapNs(fn) as PackageMapping }
            override val classes get() = self.classes.map { it.remapNs(fn) as ClassMapping }
            override val constantGroups get() = self.constantGroups.map { it.remapNs(fn) as ConstantGroupMapping }
            override fun getClass(namespace: Namespace, name: xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName) =
                self.getClass(fn(namespace), name)?.remapNs(fn) as? ClassMapping
        }
    }
    is PackageMapping -> {
        val self = this
        object : PackageMapping {
            override val names get() = self.names.remapNsKeys(fn)
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    is ClassMapping -> {
        val self = this
        object : ClassMapping {
            override val names get() = self.names.remapNsKeys(fn)
            override val methods get() = self.methods.map { it.remapNs(fn) as MethodMapping }
            override val fields get() = self.fields.map { it.remapNs(fn) as FieldMapping }
            override val wildcards get() = self.wildcards.map { it.remapNs(fn) as WildcardMapping }
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    is MethodMapping -> {
        val self = this
        object : MethodMapping {
            override val entries get() = self.entries.remapNsKeys(fn)
            override val parameters get() = self.parameters.map { it.remapNs(fn) as ParameterMapping }
            override val localVariables get() = self.localVariables.map { it.remapNs(fn) as LocalVariableMapping }
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    is FieldMapping -> {
        val self = this
        object : FieldMapping {
            override val entries get() = self.entries.remapNsKeys(fn)
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    is WildcardMapping -> {
        val self = this
        object : WildcardMapping {
            override val type get() = self.type
            override val descs get() = self.descs.remapNsKeys(fn)
            override val parameters get() = self.parameters.map { it.remapNs(fn) as ParameterMapping }
            override val localVariables get() = self.localVariables.map { it.remapNs(fn) as LocalVariableMapping }
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    is ParameterMapping -> {
        val self = this
        object : ParameterMapping {
            override val index get() = self.index
            override val lvOrd get() = self.lvOrd
            override val names get() = self.names.remapNsKeys(fn)
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    is LocalVariableMapping -> {
        val self = this
        object : LocalVariableMapping {
            override val lvOrd get() = self.lvOrd
            override val startOp get() = self.startOp
            override val names get() = self.names.remapNsKeys(fn)
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    is ConstantGroupMapping -> {
        val self = this
        object : ConstantGroupMapping {
            override val type get() = self.type
            override val name get() = self.name
            override val baseNs get() = fn(self.baseNs)
            override val metadata get() = self.metadata.map { it.remapNs(fn) }
        }
    }
    else -> this
}
