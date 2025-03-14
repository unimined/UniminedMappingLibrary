# Constant Serialization Format

## Introduction

This document describes the serialization format for constants in the unimined mapping format,
notation is very similar to the JVMS (Java Virtual Machine Specification) notation found in section 4.3 and 4.7.9.1

## Format

### Parts

#### Constant

&nbsp;&nbsp;&nbsp;&nbsp; [StringConstant](#StringConstant)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [BooleanConstant](#BooleanConstant)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [NumberConstant](#NumberConstant)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; null

#### StringConstant

&nbsp;&nbsp;&nbsp;&nbsp; " *String* "

String will be escaped using the same rules as java.

#### BooleanConstant

&nbsp;&nbsp;&nbsp;&nbsp; true
<br>
&nbsp;&nbsp;&nbsp;&nbsp; false

#### NumberConstant

&nbsp;&nbsp;&nbsp;&nbsp; \[-] [WholeConstant](#WholeConstant) [. [DecimalConstant](#DecimalConstant)] [e [ExponentConstant](#ExponentConstant)] [[FloatSuffix](#FloatSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; \[-] [WholeConstant](#WholeConstant) [[LongSuffix](#LongSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] 0. [DecimalConstant](#DecimalConstant) [e [ExponentConstant](#ExponentConstant)] [[FloatSuffix](#FloatSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] 0x [HexConstant](#HexConstant) [[LongSuffix](#LongSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] 0b [BinaryConstant](#BinaryConstant) [[LongSuffix](#LongSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] 0 [[OctalConstant](#OctalConstant)] [[LongSuffix](#LongSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] 0 [[LongSuffix](#LongSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] 0 [[FloatSuffix](#FloatSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] NaN [[FloatSuffix](#FloatSuffix)]
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [-] Infinity [[FloatSuffix](#FloatSuffix)]

#### FloatSuffix

&nbsp;&nbsp; *(one of)*
<br>
&nbsp;&nbsp;&nbsp;&nbsp; f F d D

#### LongSuffix

&nbsp;&nbsp; *(one of)*
<br>
&nbsp;&nbsp;&nbsp;&nbsp; l L

#### WholeConstant

&nbsp;&nbsp;&nbsp;&nbsp; `/[1-9][0-9]*/`

#### DecimalConstant

&nbsp;&nbsp;&nbsp;&nbsp; `/\d+/`

#### ExponentConstant

&nbsp;&nbsp;&nbsp;&nbsp; [-] [DecimalConstant](#DecimalConstant)
<br>
&nbsp;&nbsp;&nbsp;&nbsp; [+] [DecimalConstant](#DecimalConstant)

#### HexConstant

&nbsp;&nbsp;&nbsp;&nbsp; `/[0-9a-f]+/i`

#### BinaryConstant

&nbsp;&nbsp;&nbsp;&nbsp; `/[0-1]+/`

#### OctalConstant

&nbsp;&nbsp;&nbsp;&nbsp; `/[0-7]+/`
