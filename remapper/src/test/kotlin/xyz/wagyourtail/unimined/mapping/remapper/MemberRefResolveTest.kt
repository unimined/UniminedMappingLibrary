package xyz.wagyourtail.unimined.mapping.test.remapper

import xyz.wagyourtail.unimined.mapping.remapper.ExtensionContext
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.MemberRefParts
import xyz.wagyourtail.unimined.mapping.remapper.ext.annotation.ParamRemap
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.cls
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.method
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.named
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.obf
import xyz.wagyourtail.unimined.mapping.test.remapper.Fixtures.treeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `MemberRefParts.resolve` treated `mapped == baseName` as "member not
 * found" — but a member can map to *itself* (`Closeable.close()` is
 * `close` in every namespace since the override can't be renamed). The
 * whole ref then stayed verbatim, leaking the source-namespace owner
 * into target space (the YACL `Lnet/minecraft/class_310;close()V`
 * launch regression on intermediaryfabric).
 */
class MemberRefResolveTest {

    /** same-name member + explicit owner — the ref commits on the owner:
     *  `La;close()V` must emit `Lpkg/Target;close()V`, not stay verbatim */
    @Test
    fun sameNameMemberWithExplicitOwnerRemapsOwner() {
        val t = treeOf()
        t.cls("a", "pkg/Target").method("close;()V", "close;()V")
        val ctx = ExtensionContext(t, obf, named)

        val ref = MemberRefParts.parse("La;close()V", isField = false)!!
        val resolved = ref.resolve(ctx, listOf("a"), isField = false)
        assertEquals("Lpkg/Target;close()V", resolved?.let(ref::render),
            "identical member name is still a resolution — owner must remap")
    }

    /** member absent from the tree entirely + explicit owner — the ref is
     *  committed by its owner; remap owner+desc, keep the written name */
    @Test
    fun missingMemberWithExplicitOwnerStillRemapsOwner() {
        val t = treeOf()
        t.cls("a", "pkg/Target")
        val ctx = ExtensionContext(t, obf, named)

        val ref = MemberRefParts.parse("La;unknown()V", isField = false)!!
        val resolved = ref.resolve(ctx, listOf("a"), isField = false)
        assertEquals("Lpkg/Target;unknown()V", resolved?.let(ref::render),
            "explicit owner commits the ref — owner+desc remap regardless")
    }

    /** ownerless ref (`name()V` — owner taken from hints): same-name member
     *  resolves only when a tree row exists under the written name */
    @Test
    fun ownerlessSameNameMemberNeedsTreeRow() {
        val t = treeOf()
        t.cls("a", "pkg/Target").method("close;()V", "close;()V")
        t.cls("b", "pkg/Other")
        val ctx = ExtensionContext(t, obf, named)

        val ref = MemberRefParts.parse("close()V", isField = false)!!
        assertEquals("close()V", ref.resolve(ctx, listOf("a"), isField = false)
            ?.let(ref::render), "ownerless same-name member resolves via row existence")
        assertNull(ref.resolve(ctx, listOf("b"), isField = false),
            "no row under the written name on this hint — must not resolve")
    }

    /** NEW-pointcut ctor descriptor — `(Largs;)Lret;` has no owner/name;
     *  every class literal inside the desc remaps (the fabric-particles-v1
     *  `LivingEntityMixin` NEW target shape) */
    @Test
    fun ctorDescriptorTargetRemapsInnerClasses() {
        val t = treeOf()
        t.cls("a", "pkg/Arg")
        t.cls("b", "pkg/Ret")
        val ctx = ExtensionContext(t, obf, named)

        val out = ParamRemap.MemberRef.apply("(La;I)Lb;", ctx, emptyList(), null)
        assertEquals("(Lpkg/Arg;I)Lpkg/Ret;", out)
    }

    /** unchanged-path sanity: renamed member resolves exactly as before */
    @Test
    fun renamedMemberStillResolves() {
        val t = treeOf()
        t.cls("a", "pkg/Target").method("method_1490;()V", "destroy;()V")
        val ctx = ExtensionContext(t, obf, named)

        val ref = MemberRefParts.parse("La;method_1490()V", isField = false)!!
        assertEquals("Lpkg/Target;destroy()V", ref.resolve(ctx, listOf("a"), isField = false)
            ?.let(ref::render))
    }
}
