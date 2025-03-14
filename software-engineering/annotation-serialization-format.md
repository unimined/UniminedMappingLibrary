# Annotation Serialization Format

## Introduction

This document describes the serialization format for annotations in the unimined mapping format,
notation is very similar to the JVMS (Java Virtual Machine Specification) notation found in section 4.3 and 4.7.9.1

## Format

Annotation identifiers may be any valid unicode character except for the following:
`.`, `;`, `[`, `/`, `,`, `)`, `}` and `=`

### Parts

#### Annotation

&nbsp;&nbsp;&nbsp;&nbsp;@ [ObjectType](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-ObjectType) ( [[AnnotationElements](#AnnotationElements)] ) [[invisible](#Invisible)]

#### Invisible

&nbsp;&nbsp;&nbsp;&nbsp;.invisible

#### AnnotationElements

&nbsp;&nbsp;&nbsp;&nbsp;[AnnotationElements](#AnnotationElements) , [AnnotationElement](#AnnotationElement)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[AnnotationElement](#AnnotationElement)

#### AnnotationElement

&nbsp;&nbsp;&nbsp;&nbsp;[AnnotationElementName](#AnnotationElementName) = [AnnotationElementValue](#AnnotationElementValue)

#### AnnotationElementName

&nbsp;&nbsp;&nbsp;&nbsp;*AnnotationIdentifier*
<br>
&nbsp;&nbsp;&nbsp;&nbsp;" *String* "

* further restriction on annotation identifier to not contain `<` and `>` (since they are basically method names)
* in the case of String, the values `.`, `;`, `[`, `/`, `<` and `>` are still all illegal

#### AnnotationElementValue

&nbsp;&nbsp;&nbsp;&nbsp;[Annotation](#Annotation)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[ArrayConstant](#ArrayConstant)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[EnumConstant](#EnumConstant)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[ClassConstant](#ClassConstant)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[Constant](#Constant)

#### ArrayConstant

&nbsp;&nbsp;&nbsp;&nbsp;{ [[ArrayElements](#ArrayElements)] }

#### ArrayElements

&nbsp;&nbsp;&nbsp;&nbsp;[AnnotationElementValue](#AnnotationElementValue) , [ArrayElements](#ArrayElements)
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[AnnotationElementValue](#AnnotationElementValue)

#### EnumConstant

&nbsp;&nbsp;&nbsp;&nbsp;[ObjectType](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-ObjectType) . *AnnotationIdentifier*
<br>
&nbsp;&nbsp;&nbsp;&nbsp;[ObjectType](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-ObjectType) . " *String* "

* in the case of String, the values `;`, `[`, and `/` are still all illegal

#### ClassConstant

&nbsp;&nbsp;&nbsp;&nbsp;[ObjectType](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-ObjectType)

#### Constant

see constant format in [Constant Serialization Format](./constant-serialization-format.md)

must not be null

## Examples

#### Simple annotation
```
@Annotation
```
becomes
```
@LAnnotation;
```

#### Annotation with elements
```
@Annotation(element1 = "Hello World", element2 = 42)
```
becomes
```
@LAnnotation;(element1="Hello World",element2=42)
```

#### Annotation with array element
```
@Annotation(element1 = {"Hello World", "Hello World 2"})
```
becomes
```
@LAnnotation;(element1={"Hello World","Hello World 2"})
```

#### Annotation with enum element
```
@Annotation(element1 = Enum.VALUE)
```
becomes
```
@LAnnotation;(element1=LEnum;.VALUE)
```