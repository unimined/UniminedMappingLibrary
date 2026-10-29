package xyz.wagyourtail.unimined.mapping.formats

import xyz.wagyourtail.unimined.mapping.EnvType

/**
 * Writer-side mirror of [FormatReaderSettings]: the writer is its own
 * default settings object, tunables live in [extras] keyed by
 * [SettingsKey] constants declared on the writer's companion.
 */
interface FormatWriterSettings {

    /**
     * The target environment for side-filtered formats (e.g. MCP, AT, AW).
     * [EnvType.JOINED] writes everything.
     */
    val envType: EnvType
        get() = EnvType.JOINED

    /** per-writer tunables — keyed by the writer's
     *  [FormatReaderSettings.SettingsKey] constants (the same key type as
     *  the reader side, so a settings object can carry reader and writer
     *  options at once; e.g. `MCPv6FieldWriter.WRITE_COMMENTS`) */
    val extras: Map<FormatReaderSettings.SettingsKey<*>, Any?>
        get() = emptyMap()

    /** read a writer-specific setting, or null when unset */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: FormatReaderSettings.SettingsKey<T>): T? = extras[key] as? T

    /** return a copy with [key] = [value] layered on — for when the shared
     *  settings object exists before you know which writers need tuning.
     *  (Named [withExtra] rather than `with` because a [FormatProvider]
     *  implements both settings interfaces — the reader side's `with`
     *  returns `FormatReaderSettings`, so the writer side needs a
     *  distinct name to keep both implementable.) */
    fun <T> withExtra(key: FormatReaderSettings.SettingsKey<T>, value: T): FormatWriterSettings =
        object : FormatWriterSettings {
            override val envType get() = this@FormatWriterSettings.envType
            override val extras get() = this@FormatWriterSettings.extras + (key to value)
        }

    /** return a copy with [envType] overridden */
    fun withEnvType(envType: EnvType): FormatWriterSettings = object : FormatWriterSettings {
        override val envType get() = envType
        override val extras get() = this@FormatWriterSettings.extras
    }

    infix fun or(settings: FormatWriterSettings) = FormatWriterSettings(
        settings.envType,
        this.extras + settings.extras,
    )

    companion object {

        operator fun invoke(
            envType: EnvType = EnvType.JOINED,
            extras: Map<FormatReaderSettings.SettingsKey<*>, Any?> = emptyMap(),
        ) = object : FormatWriterSettings {
            override val envType: EnvType = envType
            override val extras: Map<FormatReaderSettings.SettingsKey<*>, Any?> = extras
        }

    }
}
