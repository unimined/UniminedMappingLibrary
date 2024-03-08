package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatWriter {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader")

    fun write(into: BufferedSink): MappingVisitor = write(EnvType.JOINED, into)

    fun write(envType: EnvType, into: BufferedSink): MappingVisitor = write(envType) { into.writeUtf8(it) }

    fun write(envType: EnvType, append: (String) -> Unit): MappingVisitor

}