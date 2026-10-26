package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.ConstantAttributeElementData

/**
 * An unpick group directive — `group <type>`'s declared data type,
 * `@strict`, `@format`, `@scope`, `target_annotation`, or any other `@attr`
 * on an unpick v3+ group. These are remapper directives, not Java
 * annotations: they steer constant remapping (`data-type`/`strict` pick the
 * allowed literal sorts) and survive UMF round-trips for source-side
 * rendering (`format`, `scope`).
 *
 * [key] is the attribute name as written (`strict`, `format`, `scope`,
 * `data-type`, `target-annotation`, ...); [value] is the remainder of the
 * attribute line verbatim, null for marker-only attributes.
 */
interface ConstantAttributeElement : NsBoundMetadata, SourceTransform, BytecodeTransform {

    val key: String

    val value: String?

    companion object {
        operator fun invoke(key: String, value: String?, baseNs: Namespace): ConstantAttributeElement =
            ConstantAttributeElementData(key, value, baseNs)
    }

}
