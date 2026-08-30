package xyz.wagyourtail.unimined.mapping.formats

import java.util.ServiceLoader

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class FormatSupplier {
    actual val providers = ServiceLoader.load(FormatProvider::class.java).toList()
}
