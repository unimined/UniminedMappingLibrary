# Upstream (First-Party) Parsers — Lineage & Validation Matrix

Every `FormatReader`/`FormatWriter` under
`src/commonMain/.../mapping/formats/` is validated against a committed
corpus (`src/commonTest/resources/firstparty/`) whose inputs are either
the upstream parser's own test resources or slices of real artifacts
that the upstream toolchain produces/consumes.

This table is the lineage record: for each format it links the
canonical first-party implementation (pinned to a commit/tag), the spec
where one exists, where the corpus came from, and any intentional
divergences between UML and upstream.

Upstream snapshots were pinned at these commits:

- `FabricMC/fabric-tooling` @ `b01b36a` — class-tweaker
- `FabricMC/access-widener` @ `30e2d00`
- `FabricMC/mapping-io` @ `f6f0990`
- `FabricMC/tiny-mappings-parser` @ `750be4e`
- `FabricMC/unpick` @ `c96a591`
- `MinecraftForge/AccessTransformers` @ `15fa1e5`
- `MinecraftForge/SrgUtils` @ `3c679e5`
- `MinecraftForge/MCPConfig` @ `751a4f3`
- `md-5/SpecialSource` @ `c89b093`
- `ModCoderPack/RetroGuard` @ `be337bc`
- `ParchmentMC/Feather` @ `9ae3ddb`

## Matrix

| UML class(es) | First-party implementation | Spec | Corpus |
|---|---|---|---|
| `UMFReader`/`UMFWriter` | self — `../unimined-mapping-format.md` | same | existing `UMFReadWriteTest` |
| `CTReader`/`CTWriter` | [ClassTweakerReaderImpl.java](https://github.com/FabricMC/fabric-tooling/blob/b01b36a/class-tweaker/src/main/java/net/fabricmc/classtweaker/reader/ClassTweakerReaderImpl.java), [ClassTweakerWriterImpl.java](https://github.com/FabricMC/fabric-tooling/blob/b01b36a/class-tweaker/src/main/java/net/fabricmc/classtweaker/writer/ClassTweakerWriterImpl.java) | upstream reader is the spec | `firstparty/ct/*` — upstream test resources |
| `AWReader`/`AWWriter` | [AccessWidenerReader.java](https://github.com/FabricMC/access-widener/blob/30e2d00/src/main/java/net/fabricmc/accesswidener/AccessWidenerReader.java), [AccessWidenerWriter.java](https://github.com/FabricMC/access-widener/blob/30e2d00/src/main/java/net/fabricmc/accesswidener/AccessWidenerWriter.java) | reader is the spec | `firstparty/aw/*` — upstream test resources |
| `ATReader`/`ATWriter` | [AccessTransformerList.java](https://github.com/MinecraftForge/AccessTransformers/blob/15fa1e5/src/main/java/net/minecraftforge/accesstransformer/parser/AccessTransformerList.java) (+ `parser/ATParser`) | [Forge AT docs](https://docs.minecraftforge.net/en/latest/advanced/accesstransformers/) | `firstparty/at/*` — upstream `at-test` resources |
| `LegacyATReader`/`LegacyATWriter` | pre-1.7 FML `cpw.mods.fml.common.asm.transformers.AccessTransformer` (`Class.member` targets) | reader is the spec | synthesized per FML-era `*_at.cfg` schema |
| `UnpickReader` | [UnpickV2Reader.java](https://github.com/FabricMC/unpick/blob/c96a591/unpick-format-utils/src/main/java/daomephsta/unpick/constantmappers/datadriven/parser/v2/UnpickV2Reader.java), [UnpickV3Reader.java](https://github.com/FabricMC/unpick/blob/c96a591/unpick-format-utils/src/main/java/daomephsta/unpick/constantmappers/datadriven/parser/v3/UnpickV3Reader.java) | [spec issue #26](https://github.com/FabricMC/unpick/issues/26) | schema-derived (no upstream resources shipped) |
| `RetroguardReader` | [RgsEntryType.java](https://github.com/ModCoderPack/Retroguard/blob/be337bcfce238db2c6fc8a6a4737eb9f6e73582a/src/main/java/com/rl/obf/RgsEntryType.java), `RgsEntry` | reader is the spec | schema-derived `.rgs` |
| `SrgReader`/`SrgWriter` | [IMappingFile.java `Format.SRG`](https://github.com/MinecraftForge/SrgUtils/blob/3c679e5/src/main/java/net/minecraftforge/srgutils/IMappingFile.java); [mapping-io SrgFileReader](https://github.com/FabricMC/mapping-io/blob/f6f0990/src/main/java/net/fabricmc/mappingio/format/srg/SrgFileReader.java) | srgutils/mapping-io | `firstparty/srg/mio_srg.srg` — mapping-io test resource |
| `PackageSrgReader` | Spigot BuildData `package.srg` — produced by [SpecialSource](https://github.com/md-5/SpecialSource/blob/c89b093/src/main/java/net/md_5/specialsource/JarMapping.java) (`-pm` option) | producer is the spec | schema-derived |
| `CsrgReader` | [CompactSearge.java](https://github.com/md-5/SpecialSource/blob/c89b093/src/main/java/net/md_5/specialsource/writer/CompactSearge.java); SrgUtils `Format.CSRG` | producers are the spec | `firstparty/csrg/mio_csrg.csrg` — mapping-io resource |
| `TsrgV1Reader`/`TsrgV1Writer`, `TsrgV2Reader` | SrgUtils `Format.TSRG`/`TSRG2` ([IMappingFile.java](https://github.com/MinecraftForge/SrgUtils/blob/3c679e5/src/main/java/net/minecraftforge/srgutils/IMappingFile.java)) | same | `firstparty/tsrg/mio_tsrg.tsrg`, `mio_tsrgv2.tsrg` — mapping-io resources; real `joined.tsrg` excerpt in `mcpconfig/` |
| `ProguardReader` | ProGuard ReTrace mapping reader ([guardsquare/proguard](https://github.com/Guardsquare/proguard)); SrgUtils `Format.PG` secondary | [retrace manual](https://www.guardsquare.com/manual/tools/retrace) | `firstparty/proguard/*` — mapping-io resource + Mojang `client_mappings` (1.20.1) excerpt |
| `TinyV1Reader`, `TinyV2Reader`/`TinyV2Writer` | [TinyMappingFactory.java](https://github.com/FabricMC/tiny-mappings-parser/blob/750be4e/src/main/java/net/fabricmc/mapping/tree/TinyMappingFactory.java); [mapping-io Tiny1/Tiny2FileReader](https://github.com/FabricMC/mapping-io/blob/f6f0990/src/main/java/net/fabricmc/mappingio/format/tiny/) | [tiny spec](https://wiki.fabricmc.net/documentation:tiny2) | `firstparty/tiny/*` — mapping-io + tiny-mappings-parser + srgutils resources |
| `NestReader`, `ExceptionReader`, `SignatureReader` | OrnitheMC feather extras (`.nests`/`.excs`/`.sigs`) — produced by their mapping toolchain for legacy versions | FORMATS.md section is the working spec | schema-derived |
| `MCPv1*`, `MCPv3*`, `MCPv6*` readers/writers, `MCPExceptionReader` | MCP conf-era csv/`.exc` schema — MCPBot exports; consumed by SpecialSource-era pipelines | header schemas in FORMATS.md | `firstparty/mcp/*` — real `mcp_stable-39-1.12` csvs (v6); v1/v3 schema-derived |
| `MCPConfig{Constructor,Access,Exceptions,StaticMethods}Reader` | [MinecraftForge/MCPConfig](https://github.com/MinecraftForge/MCPConfig/tree/751a4f3/versions/release/1.12.2) `config/` files | the files themselves | `firstparty/mcpconfig/*` — real 1.12.2 `joined.tsrg`/`constructors.txt`/`exceptions.txt` excerpts |
| `ParchmentReader` | [ParchmentMC/Feather `io-gson` adapters](https://github.com/ParchmentMC/Feather/tree/9ae3ddb/io-gson/src/main/java/org/parchmentmc/feather/io/gson) (`MDCGsonAdapterFactory` + `metadata/*Adapter`) | parchment data format | `firstparty/parchment/parchment_excerpt.json` — real `org.parchmentmc.data:parchment-1.20.1:2023.09.03` |
| `ZipReader`/`ZipFS` | container — no oracle | n/a | composed zip of committed corpus |

**Note:** `ZipReader` is deliberately not registered in
`FormatRegistry.builtinFormats` (commented out) — jars match the `PK`
magic, so zips must be read explicitly. The corpus test covers
`ZipReader.read` directly, not autodetect.

## Validation mechanism

- Readers: `Reader.read(corpus)` → emit `UMFWriter(minimize = true)` →
  compare to committed `expected/*.umf` golden.
- Writers: write corpus-derived tree → compare literal output to golden,
  then re-read → UMF dump (round-trip).
- `FormatDetectionTest` feeds every corpus file through
  `FormatRegistry.autodetectFormat`.
- Overlay formats (MCP csvs, CSRG + `package.srg`, `.exc`,
  MCPConfig side files) chain off a context file in the corpus.

## Divergences

Intentional differences vs upstream (per-format details live next to the
golden tests):

- **Retroguard** — UML hardcodes the `net/minecraft/src/` package
  mapping for the alpha-era MCP bastardization; upstream RetroGuard
  script files carry no package context.
- **CT header** — upstream `ClassTweakerReaderImpl` accepts an
  `accessWidener` header for legacy compat; UML routes
  `accessWidener`-headed files to `AWReader` instead, so `CTReader`
  only accepts `classTweaker`.
- **CT/AW** — `allowNonTransitive`/`transitive-*` filtering is a UML
  reader tunable, not an upstream parse decision.
- **Tiny v2** — UML restricts escapes to the spec set
  (`\\ \n \r \t \0`); upstream/mapping-io accept additional escapes.
- **Unpick v3/v4** — UML reads `target_annotation` (v4) and
  `@scope`/`@strict`/`@format`/`@flags` attributes which postdate the
  shipped upstream parser.
- **MCP csvs / `.exc` / MCPConfig sides** — no standalone upstream
  parser exists; correctness is schema-corpus only.
- `.nests`/`.excs`/`.sigs` (Ornithe extras) — same: no public parser,
  schema-corpus only.

## Fixes applied during corpus validation

- `TsrgV2Reader` — field lines now handle the source descriptor
  correctly in both variants: with desc (`srcName srcDesc dstName...`,
  mapping-io/SrgUtils style) and without (`srcName dstName...`, real
  mcp_config `joined.tsrg` files). Discriminated by column count vs the
  namespace count. Previously the desc was treated as a name and overran
  the namespace list (`IndexOutOfBoundsException` on spec-valid tsrg2).
- `TinyV2Reader` — added `v` (local variable) row support per the tiny2
  spec: `v <lvIndex> <startOpIdx> <lvtRowIndex> <names...>`;
  `lvtRowIndex` is not represented in the tree and is skipped.
- `MCPConfigExceptionsReader` — `isFormat` checked `constructors.txt`
  instead of `exceptions.txt`, and `readImpl` parsed a fused
  `class/method` token; real `exceptions.txt` is space-separated
  `class method desc exc...`. Both fixed against real MCPConfig 1.12.2
  files.
