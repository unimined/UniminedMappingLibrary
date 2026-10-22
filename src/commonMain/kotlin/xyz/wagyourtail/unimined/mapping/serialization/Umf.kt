package xyz.wagyourtail.unimined.mapping.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.EmptySerializersModule
import okio.Buffer
import okio.use
import xyz.wagyourtail.commonskt.reader.StringCharReader
import xyz.wagyourtail.unimined.mapping.*
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFReader
import xyz.wagyourtail.unimined.mapping.formats.umf.UMFWriter
import xyz.wagyourtail.unimined.mapping.sink.emit
import xyz.wagyourtail.unimined.mapping.sink.emitElement

/**
 * kotlinx.serialization [StringFormat] for the Unimined Mapping Format.
 *
 * encodes a [MappingTree] as a full umf document, or a single [MappingElement]
 * as a header-less fragment (a subtree). decoding synthesizes a header from
 * [namespaces] and parses with [UMFReader].
 */
class Umf(
    /** namespace column order used for writing/reading fragments */
    var namespaces: List<Namespace> = emptyList(),
    var minimize: Boolean = false,
) : StringFormat {

    override val serializersModule = EmptySerializersModule()

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        if (value is MappingTree) {
            return Buffer().use { buf ->
                value.emit(UMFWriter.write(buf, minimize))
                buf.readUtf8()
            }
        }
        check(value is MappingElement) { "Umf can only encode MappingTree/MappingElement" }
        // emit element into a buffer with a synthesized header, then strip the header lines
        val text = Buffer().use { buf ->
            val sink = UMFWriter.write(buf, minimize)
            sink.header(namespaces)
            emitElement(value, sink, namespaces, sort = false) { it }
            sink.end()
            buf.readUtf8()
        }
        return text.substringAfter('\n').substringAfter('\n')
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        val doc = "umf\t1\t0\n" + namespaces.joinToString("\t") { it.name } + "\n" + string
        val tree = MutableMappingTree()
        UMFReader.readNonBlocking(input = StringCharReader(doc),
            context = null, into = tree, nsMapping = emptyMap())
        // single-element fragments decode to that element, whole docs to the tree
        return (if (tree.classes.toList().size == 1 && tree.packages.none() && tree.constantGroups.none())
            tree.classes.first()
        else tree) as T
    }

}
