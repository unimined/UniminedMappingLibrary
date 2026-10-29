package xyz.wagyourtail.unimined.mapping.formats

import okio.BufferedSink
import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.sink.MappingSink

interface FormatWriter : FormatWriterSettings {

    val name: String
        get() = this::class.simpleName!!.removeSuffix("Reader")

    fun write(into: Appendable, settings: FormatWriterSettings = this): MappingSink =
        write(into::append, settings)

    fun write(into: BufferedSink, settings: FormatWriterSettings = this): MappingSink =
        write(into::writeUtf8, settings)

    fun write(append: (String) -> Unit, settings: FormatWriterSettings = this): MappingSink

    @Deprecated("set within the settings argument instead", level = DeprecationLevel.HIDDEN)
    fun write(into: Appendable, envType: EnvType): MappingSink = write(into, withEnvType(envType))

    @Deprecated("set within the settings argument instead", level = DeprecationLevel.HIDDEN)
    fun write(into: BufferedSink, envType: EnvType): MappingSink = write(into, withEnvType(envType))

    @Deprecated("set within the settings argument instead", level = DeprecationLevel.HIDDEN)
    fun write(append: (String) -> Unit, envType: EnvType): MappingSink = write(append, withEnvType(envType))

}
