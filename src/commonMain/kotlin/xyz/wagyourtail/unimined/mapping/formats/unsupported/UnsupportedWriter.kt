package xyz.wagyourtail.unimined.mapping.formats.unsupported

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.formats.FormatWriter
import xyz.wagyourtail.unimined.mapping.formats.FormatWriterSettings
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

object UnsupportedWriter : FormatWriter {

    override fun write(append: (String) -> Unit, settings: FormatWriterSettings): MappingSink {
        val envType = settings.envType
        throw UnsupportedOperationException("Unsupported format")
    }

}