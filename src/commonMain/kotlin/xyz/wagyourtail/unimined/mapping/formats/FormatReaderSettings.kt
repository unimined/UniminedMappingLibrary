package xyz.wagyourtail.unimined.mapping.formats

import xyz.wagyourtail.unimined.mapping.EnvType

/**
 * @since 1.0.3
 */
interface FormatReaderSettings {

    /** A reader-specific setting slot — identity is the key instance itself;
     *  declare one per tunable on the reader's companion (e.g.
     *  `AWReader.ALLOW_NON_TRANSITIVE`). Lets a single settings object carry
     *  options for every reader it might feed. */
    data class SettingsKey<T>(val name: String)

    /**
     * Sets whether to check if jvms types are valid upon reading.
     * not supported by all readers, but can improve reading performance at a loss of validation.
     */
    val unchecked: Boolean

    /**
     * Sets whether invalid lines are skipped or throw an exception.
     * not supported by all readers, useful for reading AT files written by other people sometimes.
     */
    val leinient: Boolean

    /**
     * The target environment for side-filtered formats (e.g. MCP, AT, AW).
     * [EnvType.JOINED] reads everything.
     */
    val envType: EnvType
        get() = EnvType.JOINED

    /** per-reader tunables — keyed by the reader's [SettingsKey] constants */
    val extras: Map<SettingsKey<*>, Any?>
        get() = emptyMap()

    /** read a reader-specific setting, or null when unset */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: SettingsKey<T>): T? = extras[key] as? T

    /** return a copy with [key] = [value] layered on — for when the shared
     *  settings object exists before you know which readers need tuning */
    fun <T> with(key: SettingsKey<T>, value: T): FormatReaderSettings = object : FormatReaderSettings {
        override val unchecked get() = this@FormatReaderSettings.unchecked
        override val leinient get() = this@FormatReaderSettings.leinient
        override val envType get() = this@FormatReaderSettings.envType
        override val extras get() = this@FormatReaderSettings.extras + (key to value)
    }


    infix fun or(settings: FormatReaderSettings) = FormatReaderSettings(
        this.unchecked || settings.unchecked,
        this.leinient || settings.leinient,
        this.envType,
        this.extras + settings.extras,
    )

    companion object {

        operator fun invoke(
            unchecked: Boolean = false,
            leinient: Boolean = false,
            envType: EnvType = EnvType.JOINED,
            extras: Map<SettingsKey<*>, Any?> = emptyMap(),
        ) = object : FormatReaderSettings {
            override val unchecked: Boolean = unchecked
            override val leinient: Boolean = leinient
            override val envType: EnvType = envType
            override val extras: Map<SettingsKey<*>, Any?> = extras
        }

    }
}
