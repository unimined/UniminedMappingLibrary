package xyz.wagyourtail.unimined.mapping.formats.unsupported

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

object UnsupportedWriter : FormatWriter {

    override fun write(append: (String) -> Unit, envType: EnvType): MappingSink {
        throw UnsupportedOperationException("Unsupported format")
    }

}