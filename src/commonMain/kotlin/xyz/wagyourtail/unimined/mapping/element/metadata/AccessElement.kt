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

    /**
     * Apply this element to [set]. Two visibility invariants live here:
     *
     * **Exclusivity** — a valid access set carries at most one visibility
     *  flag, so ADDing one displaces the others (and the [AccessFlag.DEFAULT]
     *  pseudo-flag) rather than accumulating illegal bit combinations into
     *  the rendered access int.
     *
     * **Widening floor** — with [widening] (the default), a visibility ADD
     *  applies only when it strictly raises permissiveness over the set's
     *  current visibility: private < default < protected < public. This is
     *  the FML/Forge `AccessTransformer.getFixedAccess` contract — AT rules
     *  never demote, and a `private` rule is a no-op. The floor compares
     *  against the evolving set, which is monotonic-equivalent to comparing
     *  against the original flags. Visibility REMOVEs are unaffected — they
     *  are explicit document intent, and no AT-family format emits them.
     *  Pass `widening = false` for raw document semantics (e.g. formats or
     *  tools that genuinely express demotion).
     */
    fun apply(set: MutableSet<AccessFlag>, widening: Boolean = true) {
        if (condition.check(set)) {
            when (type) {
                AddRemove.ADD -> {
                    if (value in AccessFlag.visibility) {
                        if (widening) {
                            // lenient on broken input bits: the effective
                            // current visibility is the most permissive present
                            val current = AccessFlag.visibility
                                .filter { it in set }
                                .maxByOrNull { AccessFlag.visibilityRank(it) }
                                ?: AccessFlag.DEFAULT
                            if (AccessFlag.visibilityRank(value) <= AccessFlag.visibilityRank(current)) return
                        }
                        set.removeAll(AccessFlag.visibility)
                    }
                    set.add(value)
                }
                AddRemove.REMOVE -> set.remove(value)
            }
        }
    }

    companion object {
        operator fun invoke(type: AddRemove, value: AccessFlag, condition: AccessConditions): AccessElement =
            AccessElementData(type, value, condition)
    }

}
