package xyz.wagyourtail.unimined.mapping.element.metadata

import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.MetadataElement
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.SyntheticBridgeElementData
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.toNsMap

/**
 * Marks a member that needs synthetic bridge/delegate methods generated at
 * remap-output time.
 *
 * [names] is a per-namespace alias map — like [MethodMapping][xyz.wagyourtail.unimined.mapping.element.mapping.MethodMapping]'s
 * entry names: each entry means "in that namespace, this member must also be
 * reachable under this name". Produced when propagated ancestor names lose a
 * conflict in that ns (e.g. a unifying interface — loom#206). Only namespaces
 * where a conflict existed get entries, so an empty/single-key map is a
 * namespace-scoped bridge, not one applied everywhere.
 */
interface SyntheticBridgeElement : MetadataElement, BytecodeTransform {

    /** alias names this member must additionally expose, keyed by namespace */
    val names: Map<Namespace, UnqualifiedName>

    companion object {
        operator fun invoke(names: Map<Namespace, UnqualifiedName>): SyntheticBridgeElement =
            SyntheticBridgeElementData(names.toNsMap())
    }

}
