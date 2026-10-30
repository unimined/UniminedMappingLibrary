package xyz.wagyourtail.unimined.mapping.formats.feather

import okio.BufferedSource
import xyz.wagyourtail.commonskt.reader.CharReader
import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import xyz.wagyourtail.unimined.mapping.element.AddRemove
import xyz.wagyourtail.unimined.mapping.element.InnerType
import xyz.wagyourtail.unimined.mapping.element.mapping.MutableClassMapping
import xyz.wagyourtail.unimined.mapping.element.metadata.AccessElement
import xyz.wagyourtail.unimined.mapping.element.metadata.InnerClassElement
import xyz.wagyourtail.unimined.mapping.formats.FormatReader
import xyz.wagyourtail.unimined.mapping.formats.FormatReaderSettings
import xyz.wagyourtail.unimined.mapping.jvms.ext.FieldOrMethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.FullyQualifiedName
import xyz.wagyourtail.unimined.mapping.jvms.ext.NameAndDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.ext.condition.AccessConditions
import xyz.wagyourtail.unimined.mapping.jvms.four.AccessFlag
import xyz.wagyourtail.unimined.mapping.jvms.four.ElementType
import xyz.wagyourtail.unimined.mapping.jvms.four.three.three.MethodDescriptor
import xyz.wagyourtail.unimined.mapping.jvms.four.three.two.ObjectType
import xyz.wagyourtail.unimined.mapping.jvms.four.two.one.InternalName
import xyz.wagyourtail.unimined.mapping.jvms.four.two.two.UnqualifiedName
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.sink.pushed

/**
 * Ornithe's mapping format for adding inner class information to classes.
 *
 * First-party implementation: OrnitheMC feather mapping extras (`.nests`); no public upstream parser — corpus is schema-derived.
 */
object NestReader : FormatReader {

    @Deprecated("set within the settings argument instead")
    override var unchecked: Boolean = false
    @Deprecated("set within the settings argument instead")
    override var leinient: Boolean = false


    override fun isFormat(fileName: String, input: BufferedSource): Boolean {
        return fileName.endsWith(".nest")
    }

    override suspend fun readImpl(
        input: CharReader<*>,
        context: MappingTree?,
        into: MappingSink,
        nsMapping: Map<String, String>,
        settings: FormatReaderSettings
    ) {
        val envType = settings.envType
                val ns = Namespace(nsMapping["source"] ?: "source")

        with (into) {
            header(listOf(ns))

            while (!input.exhausted()) {
                input.takeWhitespace()
                val line = input.takeNextLiteral() ?: continue
                val className = InternalName.read(line)
                val outerClassName = InternalName.read(input.takeNextLiteral()!!)
                val outerMethodName =
                    input.takeNextLiteral()!!.let { if (it.isBlank()) null else UnqualifiedName.read(it) }
                val outerMethodDesc =
                    input.takeNextLiteral()!!.let { if (it.isBlank()) null else MethodDescriptor.read(it) }
                val innerName = UnqualifiedName.read(input.takeNextLiteral()!!)
                val access = input.takeNextLiteral()!!.toInt()


                val type = if (innerName.value.toIntOrNull() != null) {
                    InnerType.ANONYMOUS
                } else if (outerMethodName == null) {
                    InnerType.INNER
                } else {
                    InnerType.LOCAL
                }
                val fqn = FullyQualifiedName(
                    ObjectType(outerClassName),
                    if (outerMethodName == null) null else NameAndDescriptor(
                        outerMethodName,
                        FieldOrMethodDescriptor(outerMethodDesc!!)
                    )
                )

                pushed(MutableClassMapping(mapOf(ns to className))) {

                    pushed(InnerClassElement(type, mapOf(ns to (innerName.value to fqn)))) {
                        for (acc in AccessFlag.of(ElementType.INNER_CLASS, access)) {
                            if (acc.elements.contains(ElementType.CLASS)) continue
                            pushed(AccessElement(AddRemove.ADD, acc, AccessConditions.ALL)) {}
                        }
                    }

                }
            }

        }
        into.end()
    }

}