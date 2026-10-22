# UMF metadata entries — generated reference

GENERATED from `@MetadataElementSpec` annotations — do not edit.
The hand-written format description lives in `unimined-mapping-format.md`.

| key | element | source | bytecode | args |
|-----|---------|--------|----------|------|
| `*` | `JavadocElement` | x |  | `value`:String + `baseNs`:Namespace |
| `@` | `AnnotationElement` | x | x | `type`:AddRemoveModify + `annotation`:Annotation + `baseNs`:Namespace |
| `a` | `AccessElement` | x | x | `type`:AddRemove + `value`:AccessFlag + `condition`:AccessConditions |
| `b` | `SyntheticBridgeElement` |  | x | `names`:Map<Namespace, UnqualifiedName> |
| `e` | `ExpressionElement` | x | x | `value`:Constant + `expression`:Expression |
| `g` | `SignatureElement` | x | x | `value`:Signature + `baseNs`:Namespace |
| `i` | `InnerClassElement` | x | x | `type`:InnerType + `names`:Map<Namespace, Pair<String, FullyQualifiedName?>> |
| `j` | `InterfaceElement` | x | x | `type`:AddRemove + `name`:ClassTypeSignature + `baseNs`:Namespace |
| `n` | `ConstantElement` | x | x | `owner`:InternalName + `field`:FieldNameAndDescriptor |
| `s` | `SealElement` | x | x | `type`:AddRemoveClear + `name`:InternalName? + `baseNs`:Namespace |
| `t` | `TargetElement` | x | x | `target`:FullyQualifiedName? + `paramIdx`:Int? |
| `x` | `ExceptionElement` | x | x | `type`:AddRemove + `exception`:InternalName + `baseNs`:Namespace |
| `z` | `EnumExtensionElement` | x | x | `type`:AddRemove + `name`:UnqualifiedName + `baseNs`:Namespace |

### `a` — AccessElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `a\t+	AccessFlag	AccessConditions`

### `@` — AnnotationElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `@\t+	Annotation	base`

### `n` — ConstantElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `n\ta/b/C	field;I`

### `z` — EnumExtensionElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `z\t+	name	base`

### `x` — ExceptionElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `x\t+	a/b/C	base`

### `e` — ExpressionElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `e\t42	expr`

### `i` — InnerClassElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `i\ti	<ns columns>`

### `j` — InterfaceElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `j\t+	La/b/C;	base`

### `*` — JavadocElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `*\ttext	base`

### `s` — SealElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `s\t+	a/b/C	base`

### `g` — SignatureElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `g\tsig	base`

### `b` — SyntheticBridgeElement

read arm: generated (standard shape)
write arm: generated (standard shape)

Per-namespace alias columns; a member needs a synthetic bridge emitted for each ns where a propagated name lost a conflict.

example: `b\t<ns columns>`

### `t` — TargetElement

read arm: generated (standard shape)
write arm: generated (standard shape)

example: `t\ta/b/C.D	0`

