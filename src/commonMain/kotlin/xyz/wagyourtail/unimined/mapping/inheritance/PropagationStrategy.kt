package xyz.wagyourtail.unimined.mapping.inheritance

/**
 * How [xyz.wagyourtail.unimined.mapping.tree.impl.InheritanceBackedMappingTree]
 * resolves a propagated member name that disagrees with an existing name.
 *
 * An *explicit* name is one written directly to a member (through the wrapper's
 * own setters or [markExplicit]); a *propagated* name was pushed down from an
 * ancestor. A child's explicit name differing from its parent's is a legitimate
 * override, never a conflict — conflicts are propagated-vs-propagated
 * disagreements (e.g. two ancestors supplying different values).
 */
enum class PropagationStrategy {
    /**
     * Child's explicit name wins; a later propagated value overwrites an earlier
     * propagated one. Default — matches the pre-strategy behavior.
     */
    KEEP_EXPLICIT,

    /**
     * First propagated value wins; later propagated values are dropped.
     * Explicit names still win.
     */
    FIRST_INHERITED_WINS,

    /**
     * Any incoming propagated value overwrites whatever exists — including
     * explicit names.
     */
    LAST_WINS,

    /**
     * Throw [InheritanceConflictException] when a propagated value disagrees
     * with an existing propagated name. Explicit overrides are still allowed.
     */
    STRICT,

    /**
     * Keep the existing name (explicit or earlier-propagated) and attach a
     * [SyntheticBridgeElement][xyz.wagyourtail.unimined.mapping.element.metadata.SyntheticBridgeElement]
     * to the member recording the losing propagated name — the remapper emits
     * a delegate for it at write time (NeoForge-style unification, loom#206).
     */
    BRIDGE,
}

/**
 * Thrown by [PropagationStrategy.STRICT] when two propagated names disagree
 * for the same member + namespace.
 */
class InheritanceConflictException(message: String) : IllegalStateException(message)
