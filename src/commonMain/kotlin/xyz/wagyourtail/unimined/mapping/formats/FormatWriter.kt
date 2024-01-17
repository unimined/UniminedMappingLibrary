package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor

interface FormatWriter {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader")

    fun write(into: BufferedSink): MappingVisitor

}