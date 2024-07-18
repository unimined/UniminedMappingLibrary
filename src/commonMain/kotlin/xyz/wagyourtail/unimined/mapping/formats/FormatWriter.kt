package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatWriter {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader")

    fun write(envType: EnvType = EnvType.JOINED, into: BufferedSink): MappingVisitor = write(envType, into::writeUtf8)

    fun write(envType: EnvType = EnvType.JOINED, append: (String) -> Unit): MappingVisitor

}

inline fun FormatWriter.writeToString(envType: EnvType = EnvType.JOINED, acceptor: (MappingVisitor) -> Unit) = buildString { acceptor(write(EnvType.JOINED, ::append)) }