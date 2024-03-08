package xyz.wagyourtail.unimined.mapping.formats.unsupported

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

object UnsupportedWriter : FormatWriter {

    override fun write(envType: EnvType, append: (String) -> Unit): MappingVisitor {
        throw UnsupportedOperationException("Unsupported format")
    }

}