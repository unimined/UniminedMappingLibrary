package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.sink.MappingSink
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

interface FormatWriter {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader")

    fun write(into: Appendable, envType: EnvType = EnvType.JOINED): MappingSink = write(into::append, envType)

    fun write(into: BufferedSink, envType: EnvType = EnvType.JOINED): MappingSink = write(into::writeUtf8, envType)

    fun write(append: (String) -> Unit, envType: EnvType = EnvType.JOINED): MappingSink

}
