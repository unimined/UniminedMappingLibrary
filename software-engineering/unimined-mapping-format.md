# Unimined Mapping Format

# Reasoning

The purpose of the unimined mapping format is to unify the capabilities of the different mapping formats that have been
created over the years.

# Requirements

1. Must support all features of other mapping formats
    * ~~package mappings~~ 
      * this will get inlined into the class mappings when read in from other formats.
        this does mean they will only remap classes that are present in the mappings,
        but this can be remedied with propagation. So parsers remapping to this format should probably support that.
    * [class mappings](#classes)
    * [method mappings](#methods)
    * [field mappings](#fields)
    * [parameter mappings](#parameters)
    * [local variable mappings](#variables)
    * [inner class / nest member mappings](#inner-class)
    * [signature mappings](#signatures)
    * [comment / javadoc mappings](#javadoc-comments)
    * [annotations](#annotations)
    * [access transformer / access mappings](#access)
2. Must be human-readable
3. Must be relatively easy to parse / write
4. all notations must be able to represent everything legal by the jvms
5. Must be easy to extend
    * so if I missed something, it can be added later
    * or even as a third party extension

# Format

The format will be based on the tiny v2 format, but with some changes.

this file is allowed to be either space or tab separated. and internal names (/ separated) will be used for all class/package references.
empty values will be represented as `_` (underscore). if a value is Literally `_` it should be represented as `__` (double underscore),
basically, if value is all underscores, add an extra underscore.

## significant whitespace

there are levels of components.
subcomponents must be indented with their parent component's indentation + at least 1 additional space or tab, and must be on a new line.
if tabs/spaces are mixed in the indentation, it may not look correct when read by a human, so this is not a recommended practice.
each tab is worth 4 spaces, so if a tab is used, it is equivalent to 4 spaces.

## whitespace escaping

whitespaces will be considered to be part of the variable only if in quotes.
this includes newlines, tabs, and spaces, so it is possible to have a single variable in this format span multiple lines.
if a quote is needed in a variable, the whole variable must be in quotes, and the quote must be escaped with a backslash.

## comments

any line that starts with a `#` (whitespace is allowed before the `#`) 
will be considered a comment and will be ignored by the parser.

## Header

the header will be
```
umf 1 0 <ext_key1> <ext_key2> ...
<t1> <t2>
<ns1> <ns2> <ns3> ...
```

where the first line is the magic string and version number,
followed by the list of used third party extensions. 
so mapping parsers can warn or throw if they don't support a required extension. 
tho parsing the known values should always work due to the other requirements of the format.
if an extension needs versioning, they can include the version in their key and parse it themselves.

third party extension keys must be at least 2 characters long. 
it is recommended to use a version suffix such as exampleExtension_1_0, but it is not required and the extension will
handle version parsing of the key itself when it is read.

The second line is a list of the tags that are used in the file, this allows for 
partial parsing with libraries that don't support all parts of this spec.

and the third line is a list of namespace names.
it is not recommended to have namespaces with spaces in them, but it is allowed by the format.

## Classes

classes will be defined as
```
c <ns1> <ns2> <ns3> ...
```

where `<ns1>` is the fully qualified internal class name using ns1 class names, `<ns2>` is for ns2, etc.

### Methods

methods will be defined as
```
m <ns1> <ns2> <ns3> ...
```

one of the namespaces should (but isn't required to, note: *really* should be required) be appended with a `;` followed by a method descriptor
for example:
```
m a;(Ljava/lang/String;)V namedName
```

behavior if multiple methods have the same name and no descriptors is undefined.

#### Parameters

parameters will be defined as
```
p <index> <lv ord> <ns1> <ns2> <ns3> ...
```

where `<index>` is the index of the parameter and `<lv ord>` is the local variable ordinal of the parameter.
so `<index>` is always just the 0 indexed position of the parameter, but `<lv ord>` will be +1 if the method isn't static (and + some more for wide types).
only one of these is required, but lvt ord is preferred. if they are both present they must be correct or behavior is undefined.

#### Variables

variables will be defined as
```
v <lv ord> <startOp> <ns1> <ns2> <ns3> ...
```

where `<lv ord>` is the local variable ordinal of the variable.
`<startOp>` is optional, and is the index of the first opcode that references this variable (where it gets defined usually), if it's not present,
all local variables using that lvt ordinal will be remapped.

### Fields

fields will be defined as
```
f <ns1> <ns2> <ns3> ...
```

one of the namespaces should (but isn't required to) be appended with a `;` followed by a field descriptor
for example:
```
f a;Ljava/lang/String; namedName
```

behavior if multiple fields have the same name and no descriptors is undefined.

### Inner Class

this notation is specifically for the inner/outer class attribute,
so the mapped names of the class should be written appropriate for that.

inner class information will be a component under the class that indicates the parent class/method of this class.
it will be defined as
```
i <t> <ns1> <ns2> <ns3> ...
```

where `<t>` is either `i` for inner classes, `a` for anonymous classes, or `l` for local classes.
* in the case of `<t>` being `i`, the descriptor is expected to be a class descriptor.
* in the case of `<t>` being `a`, the descriptor is expected to be a method descriptor or a field descriptor.
* in the case of `<t>` being `l`, the descriptor is expected to be a method descriptor.

one of the namespaces must be appended with a `;` followed by either a fully qualified class/method/field descriptor in ns1 names.
* a class descriptor is normal and matches the return value of `org.objectweb.asm.Type#getDescriptor()` for the class.
    * for example `Lcom/example/ExampleClass;`
* a method descriptor is a class descriptor + a method name + a method descriptor.
    * for example `Lcom/example/ExampleClass;exampleMethod(Ljava/lang/String;)V`
    * if the method's descriptor is unknown for some reason (note: please don't) it should be serialized as `()`
        * for example `Lcom/example/ExampleClass;exampleMethod()`
* a field descriptor is a class descriptor + a field name + ";" + a field descriptor.
* for example `Lcom/example/ExampleClass;exampleField;Ljava/lang/String;`
* if the field's descriptor is unknown for some reason (note: please don't) it should be omitted as well
    * for example `Lcom/example/ExampleClass;exampleField;`

the `<ns1> <ns2> <ns3> ...` are the inner names of the class in the namespaces, 
if it is anonymous this should be a number.
in order to skip a namespace, the value should be empty (`_`).

if this property is repeated, the namespaces that have values set should not overlap with others,
for example: a mapper might do
```
i i InnerNameA;Lcom/example/ExampleClass;exampleMethod(Ljava/lang/String;)V _
i a _ 0;Lcom/example/ExampleClass;exampleMethod2(Ljava/lang/String;)V
```
this would indicate that `ns1` wants the inner class under `exampleMethod` to be named `InnerNameA` 
while`ns2` wants the inner class under `exampleMethod2` to be named `InnerNameB`.
allowing for different mappings to represent nesting differently.

## Javadoc Comments

comments can occur under any component, and will be defined as
```
* <ns1 comment> <ns2 comment> <ns3 comment> ...
```

where `<ns1 comment>` is the comment text in for ns1.
if there is no comment for a namespace, the value should be empty (`_`).
if a comment is the same as another namespace, it should be the index of the namespace instead of the comment text in order to save space.
this does mean that a comment can't be just a number, so in order to support that numbers should be prefixed with an underscore (`_`).
for example, if the comment is `1`, it should be serialized as `_1`.
if the comment is `_1`, it should be serialized as `__1`, etc.

## Signatures

signatures (aka generics) can occur under classes/methods/fields and will be defined as
```
g <ns1 sig> <ns2 sig> <ns3 sig> ...
```

where `<ns1 sig>` is the generic signature using ns1 class names.
only one namespace is required, ns1 is preferred.

## Annotations

annotations can occur under classes/methods/fields and will be defined as
```
a <action> <key> <value> <ns1 name> <ns2 name> <ns3 name> ...
```

where `<ns1 name>`, `<ns2 name>`, `<ns3 name>`, etc are the namespace names to operate on.
if none of the namespaces in the file are just numbers, indexes can be used instead of names.

`<action>` is either `+`, `-` or `m`

`<key>` in the type descriptor for the annotation in `<ns1 name>`'s names

in the case of `+`, `<value>` is the annotation descriptor in the [annotation serialization format](./annotation-serialization-format.md) in `<ns1 name>`'s names, starting with the `(`

in the case of `-`, `<value>` should be left empty (`_`)

for example
```java
@ExampleAnnotation(value = 1.0f, name = "example")
method() {}
```

would be serialized as
```
@ + Lcom/example/ExampleAnnotation; "(value=1f,name=\"example\")" named
```

`m` is for modifying values on existing annotations, in which case the contents `<value>` are the new value to replace existing ones in the annotation to.

legal annotation values are:
* a string literal (in quotes)
* an integer literal
* a float literal (with a trailing `F`)
* a double literal (with a trailing `D`)
* a long literal (with a trailing `L`)
* a boolean literal (`true` or `false`)
* a class literal (in the format `Lcom/example/ExampleClass;`)
* an enum literal (in the format `Lcom/example/ExampleEnum;EXAMPLE_VALUE`)
* an annotation literal (see the [annotation serialization format](./annotation-serialization-format.md)), keep the `@` at the beginning)
* a comma separated list of one of the above with {} around it (for example `{1, 2, 3}`)

## access

access transformers / access mappings can occur under classes/methods/fields and will be defined as
```
a <action> <access> <ns1 name> <ns2 name> <ns3 name> ...
```

where `<ns1 name>`, `<ns2 name>`, `<ns3 name>`, etc are the namespace names to operate on.
if none of the namespaces in the file are just numbers, indexes can be used instead of names.

`<action>` is either `+` or `-` and `<access>` is the access modifier(s) to add or remove.
the access modifiers are the lowercase string names.

for example, to make a method public in `named`, you would do
```
a + public named
```

in the case of public/private/protected/package-private the access doesn't need to be removed for the previous one.
with static, final, abstract, synthetic, bridge, it's additive since these are flags.

package-private is represented as `package`

## Extensions

extensions are for third party extensions to the format.
they can occur under any component or at the top level, and will be defined as
```
e <ext_key> <ext_value1> <ext_value2> ...
```

where `<ext_key>` is the key of the extension, and `<ext_value1> <ext_value2> ...` are the values of the extension.


# Example

```
umf 1 0
obf intermediary yarn
c evi net/minecraft/class_310 net/minecraft/client/Minecraft
 m (Ljava/lang/String;)V a method_1 exampleMethod
  p 0 _ stringArg
  v 3 _ localVariable
   # "this is a comment about the local variable"
 f Ljava/util/List; exampleField
  g Ljava/util/List<Ljava/lang/String;>;
  * "this is a comment about the field"
  e exampleExtensionKey exampleExtensionValue
c evi$a net/minecraft/class_310$class_311 net/minecraft/client/Minecraft$ExampleInnerClass
 i i Lnet/minecraft/class_310;
 * "this is a comment about the inner class"
```