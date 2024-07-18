package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatWriter {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader")

    fun write(into: BufferedSink, envType: EnvType = EnvType.JOINED): MappingVisitor = write(into::writeUtf8, envType)

    fun write(append: (String) -> Unit, envType: EnvType = EnvType.JOINED): MappingVisitor

}
