# Javadoc Serialization Format

## Introduction

This document describes the serialization of references inside javadoc/comment
metadata (`*` rows) in the unimined mapping format — how `{@link}`/`@see`/`@throws`
(and KDoc `[ref]`) link targets inside a comment string are expressed in
namespace-bound form, so that a comment viewed in a single namespace has its
internal references correctly rewritten.

Comments themselves serialize as opaque strings on `*` rows — the *refs inside
them* follow the grammar below. Notation borrows from JVMS 4.3 / 4.7.9.1, the
same conventions as the [annotation format](annotation-serialization-format.md).

## Format

### Parts

#### DocBody

&nbsp;&nbsp;&nbsp;&nbsp;*Text* — any unicode text; link refs embed as:

#### InlineTag

&nbsp;&nbsp;&nbsp;&nbsp;{@**link** [Ref](#Ref) [ [Label](#Label) ]}
<br>
&nbsp;&nbsp;&nbsp;&nbsp;{@**linkplain** [Ref](#Ref) [ [Label](#Label) ]}
<br>
&nbsp;&nbsp;&nbsp;&nbsp;{@**value** [Ref](#Ref) [ [Label](#Label) ]}

#### BlockTag

&nbsp;&nbsp;&nbsp;&nbsp;@**see** [Ref](#Ref) [ [Label](#Label) ]
<br>
&nbsp;&nbsp;&nbsp;&nbsp;@**throws** [Ref](#Ref)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;@**exception** [Ref](#Ref)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;@**serialField** [UnqualifiedName](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-UnqualifiedName) [ObjectType](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-ObjectType) *description*

#### KDocRef

&nbsp;&nbsp;&nbsp;&nbsp;[ [Ref](#Ref) ] &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;*— `[a.b.Cls]`, `[a.b.Cls#m]`, `[#f]`*
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[ [Ref](#Ref) | [Label](#Label) ]

#### Ref

&nbsp;&nbsp;&nbsp;&nbsp;[InternalName](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-InternalName) — *dotted on the wire: `a.b.Cls`*
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[InternalName](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-InternalName) # [Member](#Member)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;# [Member](#Member) &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;*— owner-less ("this class")*

#### Member

&nbsp;&nbsp;&nbsp;&nbsp;[UnqualifiedName](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-UnqualifiedName)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[UnqualifiedName](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-UnqualifiedName) ( [ParamList](#ParamList) )

#### ParamList

&nbsp;&nbsp;&nbsp;&nbsp;[SourceType](#SourceType) , [ParamList](#ParamList)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[SourceType](#SourceType)

#### SourceType

&nbsp;&nbsp;&nbsp;&nbsp;*source-form type* — `int`, `a.b.T`, `int[]`, `T`, `T...` —
&nbsp;&nbsp;&nbsp;&nbsp;*maps to* [JavaTypeSignature](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-JavaTypeSignature) *(`I` / `La/b/T;` / `TT;` / `[I`)*

#### Label

&nbsp;&nbsp;&nbsp;&nbsp;*free text — display label; never a ref*

## Remapping rule

Comments are namespace-bound strings — the `*` row's `<ns1>` selects the
namespace the comment text is written in, so refs inside it must be expressed
in that namespace. When a comment is emitted in a different namespace view
(`tree.getNamespace(ns)`), each [Ref](#Ref) remaps: `InternalName` through
class mappings, `Member` through field/method mappings (owner resolved first,
param types as `JavaTypeSignature`s, desc-less name match). Text and labels
never rewrite. Unresolvable refs (URLs, bare words, `*`) pass through as text.

Kotlin doc comments use KDoc `[ref]` links — same ref grammar, different tag.
