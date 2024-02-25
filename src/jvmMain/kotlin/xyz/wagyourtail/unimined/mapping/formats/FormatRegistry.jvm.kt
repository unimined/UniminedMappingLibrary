package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import okio.BufferedSource
import xyz.wagyourtail.unimined.mapping.visitor.MappingVisitor
import java.util.ServiceLoader

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class FormatSupplier {

    actual val providers = ServiceLoader.load(FormatProvider::class.java).toList()

}