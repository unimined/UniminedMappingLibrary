package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemoveModify
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.AnnotationElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.annotation.Annotation

interface AnnotationElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val type: AddRemoveModify

    val annotation: Annotation

    companion object {
        operator fun invoke(type: AddRemoveModify, baseNs: Namespace, annotation: Annotation): AnnotationElement =
            AnnotationElementData(type, baseNs, annotation)
    }

}
