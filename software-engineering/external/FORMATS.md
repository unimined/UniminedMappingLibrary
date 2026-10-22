# Supported Mapping Formats

A reference for each format this library reads/writes: what the files look
like, where they come from, and who produces them.

Format detection lives in `FormatRegistry`; each reader's `isFormat` shows the
exact filename/content heuristic used.

## UMF — Unimined Mapping Format

This library's own superset format: every element type (packages, classes,
members, params, local vars, wildcards, javadoc, signatures, exceptions,
access, inner classes, constant groups) in a single namespaced text file.

```
umf	1	2
official	intermediary	named
c	a	net/minecraft/class_1	net/minecraft/Minecraft
	m	a;()V	method_1	method1
		p	_	0	_	_	arg0
```

- Full spec: [`../unimined-mapping-format.md`](../unimined-mapping-format.md)
- Read/write: `UMFReader` / `UMFWriter`, and `serialization/Umf` (kotlinx
  `StringFormat`)

## SRG (`.srg`)

The classic MCP/FML mapping format — `PK:`/`CL:`/`FD:`/`MD:` prefixed lines.
Two namespaces (source → target). No descriptors on fields; method lines carry
both source and target descriptors.

```
PK: . net/minecraft/src
CL: a net/minecraft/src/Minecraft
FD: a/a net/minecraft/src/Minecraft/field_1724
MD: a/a ()V net/minecraft/src/Minecraft/func_1_a ()V
```

- **Found in:** MCP `conf/` folders (`joined.srg`, `client.srg`, `server.srg`),
  FML-era configs, produced historically by MCPBot/SpecialSource.
- Read/write: `SrgReader` / `SrgWriter`
- Related: `package.srg` — package-only variant, two bare columns per line
  (`PackageSrgReader`):

```
./ net/minecraft/server/
```

## CSRG (`.csrg`)

SpecialSource's compact srg. Unprefixed lines distinguished by column count:
2 = class, 3 = field, 4 = method. Member-owner and method-descriptor columns
carry dst-simple names in *source-package* form — consumers apply a
`package.srg` context to repackage them (`mapPackage`/`mapDescPackages`).

```
a net/minecraft/server/Entity
AttributeInstance a ()LIAttribute; getAttribute
AxisAlignedBB b (DDD)LAxisAlignedBB; grow
```

- **Found in:** Spigot BuildData (`mappings/bukkit-*-cl.csrg`,
  `bukkit-*-members.csrg` alongside `package.srg`), FML-era MCP configs.
- Written by `net.md_5.specialsource.writer.CompactSearge`.
- Read: `CsrgReader` (pass a `package.srg` context tree when repackaging is
  needed).

## TSRG (`.tsrg`)

SpecialSource's indented variant: class lines flush-left, members indented
one tab. One source descriptor on methods.

```
a net/minecraft/src/Minecraft
	a field_1724
	a ()V method_1507
```

- **Found in:** Forge MCPConfig zips (`config/joined.tsrg`,
  `config/split.tsrg`) for mc 1.7.10+.
- Read/write: `TsrgV1Reader` / `TsrgV1Writer`

## TSRG v2 (`.tsrg` with `tsrg2` header)

Multi-namespace tsrg. `tsrg2` header declares all namespaces; class lines
carry a name per namespace; methods carry src name+desc then a name per
remaining namespace; params at two tabs.

```
tsrg2 obf mcp
a net/minecraft/src/Minecraft
	a ()V method_1507
		0 param0_s param0_t
```

- **Found in:** newer Forge MCPConfig zips.
- Read: `TsrgV2Reader`

## Proguard (`.txt`)

Mojang's official obfuscation mappings: `DottedClass -> obf:` class lines,
indented `type name -> obf` members, Java-type method descriptors.

```
net.minecraft.src.Minecraft -> a:
    int field1 -> a
    net.minecraft.src.World method2(int,boolean) -> b
```

- **Found in:** Mojang's official mappings — `client_mappings.txt` /
  `server_mappings.txt` linked from each version's
  `version_manifest.json` download entry (mc 1.14.4+).
- Read: `ProguardReader` (`client.txt`/`server.txt` filenames also select the
  EnvType side)

## Tiny v1

FabricMC's first tiny format. `v1` header + namespace columns; `CLASS`,
`FIELD`, `METHOD` rows. Descriptors in the first namespace only.

```
v1	notch	searge	mcp
CLASS	a	net/minecraft/src/Minecraft	Minecraft
FIELD	a	I	a	field_1	field1
METHOD	a	()V	a	method_1	method1
```

- **Found in:** older Fabric intermediary/yarn artifacts.
- Read: `TinyV1Reader`

## Tiny v2

The current Fabric format: `tiny	2	0` header, per-element comment metadata
(`c` rows), src+dst descriptor support.

```
tiny	2	0	official	intermediary	named
c	a	net/minecraft/class_1	net/minecraft/Minecraft
	m	()V	a	method_1	method1
```

- **Found in:** Fabric yarn/intermediary, Quilt mappings on their mavens;
  produced by `tiny-remapper` / stitch / loom.
- Spec: https://wiki.fabricmc.net/documentation:tiny2 (see also
  https://wiki.fabricmc.net/documentation:mapping_formats)
- Read/write: `TinyV2Reader` / `TinyV2Writer`

## Feather (`.nests`, `.excs`, `.sigs`)

FeatherMC's supplemental formats used for early-version (alpha/beta-era)
mappings:

- `NestReader` (`.nests`) — inner-class/enclosure metadata
- `ExceptionReader` (`.excs`) — declared exceptions; class lines flush-left,
  `name desc exc...` members indented:

```
a
	m ()V java/lang/Exception java/lang/Throwable
```

- `SignatureReader` (`.sigs`) — generic signatures; class line `name sig`,
  members `name desc sig` indented:

```
a <T:Ljava/lang/Object;>Ljava/lang/Object;
	m ()V (TT;)V
	f I TT;
```

## Unpick (`.unpick`)

FabricMC's unpick format (`v2` header) — targets, constant groups, and
remapping expressions for enum/flag constants. Used in yarn/legacy-yarn
pipelines to document magic-number constants.

- Spec/reference: https://github.com/FabricMC/unpick
- Read: `UnpickReader`

### Unpick v3 (`unpick v3` header)

Newer spec with typed `group <type> [name]` blocks, expression constants
(`Mth.PI / 3`, `Cls.*` wildcards), `@scope`/`@strict`/`@format`/`@flags`
attributes, `param`/`return` method targets, `target_field` lines, and (v4)
`target_annotation` lines. Binary (dotted) class names; `param` indexes are
arg indexes, not LVT.

Mapping into the element model:

- group data type → `Lunimined/unpick/DataType;` annotation on the group
- `@scope`/`@strict`/`@format` → `Lunimined/unpick/Scope;`/`Strict;`/`Format;`
  annotations on the group; `@flags` → bitfield group
- `#` comments immediately preceding a group → group javadoc
- `Cls.FIELD[:type]` consts (incl. `Cls.*` wildcards) → constant elements;
  `:instance` markers and other expressions → verbatim expression elements
- `target_field`/`target_annotation` → class-level targets (annotation
  targets have no member descriptor); `param`/`return` → indexed targets

- Spec: https://github.com/FabricMC/unpick/issues/26 (v3),
  `unpick v4` adds `target_annotation`
- Read: `UnpickReader` (shared v2/v3/v4)

## Retroguard (`.rgs`)

RetroGuard script format — the original obfuscation mapping output produced
by running RetroGuard in old (alpha era) MCP toolchains. `.class`/`method`
/`field` mapping statements plus `-out`/`-startclass` style directives.

- Read: `RetroguardReader`

## MCP CSVs

The Mod Coder Pack's crowd-sourced name tables, layered over an obf→srg
mapping. Three generations:

### MCP v1 (mc pre-1.3 era)

`methods.csv`/`fields.csv` with `NULL,NULL,NULL` header rows and columns
`class,name,searge,...,comment`. Example:

```
class (ref),Reference,class (ref),Reference,Name,Notes,188
a (Minecraft),method_1507,*,*,minecraftMethod,comment data,,[1]
```

- Readers: `MCPv1MethodReader`, `MCPv1FieldReader` (need an existing srg-context
  tree)

### MCP v3 (mcp 3.0–5.6)

`classes.csv`, `methods.csv`, `fields.csv` with the quoted header
`"searge","name","notch","sig","notchsig","classname","classnotch","package","side"`.

- Readers: `MCPv3ClassesReader`, `MCPv3MethodReader`, `MCPv3FieldReader`
- Side column: 0 client / 1 server / 2 both.

### MCP v6+ (mcp 6.0 onward)

`methods.csv`/`fields.csv`/`params.csv` with header `searge,name,side,desc`;
`packages.csv` with `class,package`. These are *name overlays* — they need a
base obf→searge mapping as context.

```
searge,name,side,desc
func_1_a,mcMethod,0,method docs
```

- **Found in:** MCP zips (from MCPBot exports / files.minecraftforge.net
  `de/oceanlabs/mcp`), Forge mcp_config bundles.
- Readers: `MCPv6{Method,Field,Param,Package}Reader`
- Writers: `MCPv6{Method,Field,Param}Writer`

## MCP exceptions (`.exc`)

MCP's method-metadata file: `cls.method(desc)=exc1,exc2|param1,param2-Access=FLAG`.

```
a.b()V=java/lang/Exception|p_1_0_-Access=PUBLIC
```

- **Found in:** old MCP conf folders (`joined.exc`, `client.exc`/`server.exc`).
- Read: `MCPExceptionReader`

## MCPConfig side files (mc 1.12.2+)

Plain-text files inside Forge MCPConfig zips (`config/`):

- `constructors.txt` — `<id> <class> <desc>`; generates `<init>` entries with
  `p_i<id>_<idx>_` param names (`MCPConfigConstructorReader`)
- `access.txt` — `<FLAG> <class> <name> <desc>` access entries
  (`MCPConfigAccessReader`)
- exceptions — `<class>/<name> <desc> exc...` (`MCPConfigExceptionsReader`)
- `static_methods.txt` — bare `func_*` list; marks those methods static so
  param lvOrd starts at 0 (`MCPConfigStaticMethodsReader`)

## AccessTransformer / AccessWidener

- `ATReader`/`ATWriter` — Forge accesstransformer format:
  `public+f net.minecraft.Class member(desc)`; found in
  `META-INF/accesstransformer.cfg`, Forge/fml_at.cfg files.
- `LegacyATReader`/`LegacyATWriter` — pre-1.7 variant with `Class.member`
  syntax (`public-f net/minecraft/class_3720.field_19158`).
- `AWReader`/`AWWriter` — Fabric AccessWidener:
  `accessible	field	net/minecraft/class_1	field_1	I`; found in
  `*.accesswidener` files inside fabric mod jars / yarn.

## Parchment (`parchment.json`)

ParchmentMC's javadoc/parameter-name overlay for official mappings. JSON:
`packages[].{name,javadoc}`, `classes[].{name,methods[]:{name,descriptor,javadoc,parameters[]},fields[]}`.

- **Found in:** `org.parchmentmc.data:parchment-*` artifacts on the
  ParchmentMC maven (`https://maven.parchmentmc.org`).
- Read: `ParchmentReader`

## Zip containers

`ZipReader` unwraps archives and feeds each contained file through format
detection — this is how real-world bundles are consumed:

- MCP `conf/` zips (srg + csv + exc + package.srg)
- Forge MCPConfig zips (`config/joined.tsrg`, constructors.txt, etc.)
- Spigot BuildData zips (cl.csrg + members.csrg + package.srg + .at)
- Mapping jars (tiny/umf/etc.)

## Other

- `UnsupportedReader`/`UnsupportedWriter` — fallbacks that throw
- `EnvType` — CLIENT/SERVER/JOINED; governs which `side` column values are
  kept by MCP-family readers and which files format detection accepts
