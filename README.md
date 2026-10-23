# UniminedMappingLibrary

A Kotlin Multiplatform library for reading, writing, transforming, and
combining Minecraft obfuscation/deobfuscation mappings.

## Formats

Supports SRG, CSRG, TSRG v1/v2, Tiny v1/v2, Proguard (Mojang), Feather
(.nests/.excs/.sigs), Unpick, Retroguard, MCP csvs (v1/v3/v6+), MCP exceptions,
MCPConfig side files, AccessTransformer/AccessWidener, ClassTweaker,
Parchment, zip containers — plus UMF, the library's own superset format.

See [FORMATS.md](software-engineering/external/FORMATS.md) for per-format
examples, where each file type is found, and what produces it.

## Remapping

Bytecode and source jars remap through the same `MappingTree`/`ExtensionContext`
model — a source jar always comes paired with its bytecode jar.

- **`AsmRemapper`** — bytecode (`remapper/asm/`): jar → transformed `ClassNode`s.
  `Options` + `AsmExtension` seam (analyze → transform → post-application →
  resources) — mixin tables, annotation grammar (`ParamRemap`/`AnnotationTable`),
  Kotlin metadata, javadoc/parchment, access transforms, unpick targets.
- **`SourceRemapper`** — source (`remapper/source/`): src jar → remapped text.
  `SourceEngine` seam (`PsiSourceEngine` today; `JavacSourceEngine` deferred).
  `SourceExtension` seam mirrors `AsmExtension` — built-ins:
  `MixinSourceExtension`, `InterfaceInjectionExtension`, `JavadocExtension`,
  `UnpickSourceExtension`, `AccessSourceExtension`. Renames, file relocation,
  import fixups, doc refs (`{@link}`/`KDoc`), linemap adjustments — all through
  a shared `ChangeSet`.
- **`remapPaired(srcJar, binJar, …)`** — source+bytecode pair is the expected
  input (bytecode jar goes on the resolution classpath).
- **`RemapSession`** — scan once (merged tree + libraries), then `remapJar` /
  `remapSources` per jar — the mod-loader shape.
- **Javadoc grammar** — `jvms/ext/javadoc/` is a real `Type`/`TypeCompanion`
  grammar like the other `jvms` formats: `Javadoc`/`DocSegment`/`DocTag`/
  `DocRef`/`DocMember`/`SourceType`. Typed in `JavadocElement.value` —
  refs (`{@link}`/`@see`/`@throws`/`[x]`/`[x#m]`) remap across namespaces;
  text/labels pass through. See `software-engineering/javadoc-serialization-format.md`.

## CLI

The `cli/` module builds a fat jar
(`cli/build/libs/*-cli-*-all.jar`, `java -jar …`, command name `uml`).
Parent options load mappings through `MappingResolver` (format autodetect,
zip expansion, `requires`/`provides` topological sort, inheritance
propagation via `ASMInheritanceTree` over `--propagation`/`--classpath`
jars); subcommands consume the resolved tree:

```bash
uml -i mappings.tiny --unmapped-ns obf --requires obf --provides named \
    remap in.jar out.jar --from obf --to named --library dep.jar

uml -i m.tiny --unmapped-ns obf --requires obf --provides named \
    export tinyv2 out.tiny

# path-1: authored sources paired with their bytecode jar
uml -i m.tiny --unmapped-ns obf --requires obf --provides named \
    remap-sources src.jar src-out.jar --bin bin.jar --from obf --to named

# path-2: decompiled sources — metadata transforms + linemap adjustment
uml -i m.tiny --unmapped-ns obf --requires obf --provides named \
    transform-sources decompiled.jar fixed.jar --bin bin-named.jar \
        --from obf --to named
uml linemap bin-named.jar fixed.jar bin-linemapped.jar
```

`linemap` needs no mappings — it applies a sources jar's `0x4646` linemap
extras back onto the bin jar's `LineNumberTable`. See
`software-engineering/cli-plan.md` for the design.
