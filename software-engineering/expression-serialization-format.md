# Expression Serialization Format

## Introduction

This document describes the serialization format for expressions in the unimined mapping format,
notation is very similar to the JVMS (Java Virtual Machine Specification) notation found in section 4.3 and 4.7.9.1

some parts of this document are based on [Earthcomputer's V3 proposal for unpick](https://github.com/FabricMC/unpick/issues/26)

## Format

### Parts

#### Expression

&nbsp;&nbsp;&nbsp;&nbsp; [BitOrExpression](#BitOrExpression)

#### BitOrExpression

&nbsp;&nbsp;&nbsp;&nbsp; [[BitOrExpression](#BitOrExpression) |] [BitXorExpression](#BitXorExpression)

#### BitXorExpression

&nbsp;&nbsp;&nbsp;&nbsp; [[BitXorExpression](#BitXorExpression) ^] [BitAndExpression](#BitAndExpression)

#### BitAndExpression

&nbsp;&nbsp;&nbsp;&nbsp; [[BitAndExpression](#BitAndExpression) &] [BitShiftExpression](#BitShiftExpression)

#### BitShiftExpression

&nbsp;&nbsp;&nbsp;&nbsp; [[BitShiftExpression](#BitShiftExpression) <<] [AdditiveExpression](#AdditiveExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [[BitShiftExpression](#BitShiftExpression) >>] [AdditiveExpression](#AdditiveExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [[BitShiftExpression](#BitShiftExpression) >>>] [AdditiveExpression](#AdditiveExpression)

#### AdditiveExpression

&nbsp;&nbsp;&nbsp;&nbsp; [[AdditiveExpression](#AdditiveExpression) +] [MultiplicativeExpression](#MultiplicativeExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [[AdditiveExpression](#AdditiveExpression) -] [MultiplicativeExpression](#MultiplicativeExpression)

#### MultiplicativeExpression

&nbsp;&nbsp;&nbsp;&nbsp; [[MultiplicativeExpression](#MultiplicativeExpression) *] [UnaryExpression](#UnaryExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [[MultiplicativeExpression](#MultiplicativeExpression) /] [UnaryExpression](#UnaryExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [[MultiplicativeExpression](#MultiplicativeExpression) %] [UnaryExpression](#UnaryExpression)

#### UnaryExpression

&nbsp;&nbsp;&nbsp;&nbsp; - [PrimaryExpression](#PrimaryExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; ~ [PrimaryExpression](#PrimaryExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; ! [PrimaryExpression](#PrimaryExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [PrimaryExpression](#PrimaryExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; - [CastExpression](#CastExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; ~ [CastExpression](#CastExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; ! [CastExpression](#CastExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [CastExpression](#CastExpression)


#### CastExpression

&nbsp;&nbsp;&nbsp;&nbsp; < [DataType](#DataType) > [UnaryExpression](#UnaryExpression)

#### PrimaryExpression

&nbsp;&nbsp;&nbsp;&nbsp; [ParenExpression](#ParenExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [FieldExpression](#FieldExpression)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [Constant](#Constant)

#### ParenExpression

&nbsp;&nbsp;&nbsp;&nbsp; ( [Expression](#Expression) )

#### FieldExpression

&nbsp;&nbsp;&nbsp;&nbsp; [ObjectType](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-ObjectType) [.this] . [UnqualifiedName](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.2.2) ; [[FieldDescriptor](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-FieldDescriptor)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; this. [UnqualifiedName](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.2.2) ; [[FieldDescriptor](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-FieldDescriptor)]

#### Constant

see [Constant Serialization Format](constant-serialization-format.md)

#### DataType

&nbsp;&nbsp;&nbsp;&nbsp; byte
<br>
&nbsp;&nbsp;&nbsp;&nbsp; short
<br>
&nbsp;&nbsp;&nbsp;&nbsp; char
<br>
&nbsp;&nbsp;&nbsp;&nbsp; int
<br>
&nbsp;&nbsp;&nbsp;&nbsp; long
<br>
&nbsp;&nbsp;&nbsp;&nbsp; float
<br>
&nbsp;&nbsp;&nbsp;&nbsp; double
<br>
&nbsp;&nbsp;&nbsp;&nbsp; boolean
<br>
&nbsp;&nbsp;&nbsp;&nbsp; String
<br>
&nbsp;&nbsp;&nbsp;&nbsp; Class

