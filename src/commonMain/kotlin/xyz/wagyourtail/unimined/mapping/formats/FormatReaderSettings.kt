package xyz.wagyourtail.unimined.mapping.formats

/**
 * @since 1.0.3
 */
interface FormatReaderSettings {

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


    infix fun or(settings: FormatReaderSettings) = FormatReaderSettings(
        this.unchecked || settings.unchecked,
        this.leinient || settings.leinient
    )

    companion object {

        operator fun invoke(
            unchecked: Boolean = false,
            leinient: Boolean = false
        ) = object : FormatReaderSettings {
            override val unchecked: Boolean = unchecked
            override val leinient: Boolean = leinient
        }

    }
}
