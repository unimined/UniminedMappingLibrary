package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import java.util.ServiceLoader

actual class FormatSupplier {

    actual val providers = ServiceLoader.load(FormatProvider::class.java).toList()

}