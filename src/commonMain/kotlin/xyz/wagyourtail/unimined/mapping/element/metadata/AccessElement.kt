package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.AccessElementData
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag

interface AccessElement : MetadataElement, SourceTransform, BytecodeTransform {

    val type: AddRemove

    val value: AccessFlag

    val condition: AccessConditions

    fun apply(set: MutableSet<AccessFlag>) {
        if (condition.check(set)) {
            when (type) {
                AddRemove.ADD -> set.add(value)
                AddRemove.REMOVE -> set.remove(value)
            }
        }
    }

    companion object {
        operator fun invoke(type: AddRemove, value: AccessFlag, condition: AccessConditions): AccessElement =
            AccessElementData(type, value, condition)
    }

}
