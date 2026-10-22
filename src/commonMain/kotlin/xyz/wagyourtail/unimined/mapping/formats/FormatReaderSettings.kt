package xyz.wagyourtail.unimined.mapping.formats

import xyz.wagyourtail.unimined.mapping.EnvType
import xyz.wagyourtail.unimined.mapping.element.*
import xyz.wagyourtail.unimined.mapping.element.mapping.*
import xyz.wagyourtail.unimined.mapping.element.metadata.*
import xyz.wagyourtail.unimined.mapping.element.metadata.impl.*
import xyz.wagyourtail.unimined.mapping.tree.impl.*

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

    /**
     * The target environment for side-filtered formats (e.g. MCP, AT, AW).
     * [EnvType.JOINED] reads everything.
     */
    val envType: EnvType
        get() = EnvType.JOINED


    infix fun or(settings: FormatReaderSettings) = FormatReaderSettings(
        this.unchecked || settings.unchecked,
        this.leinient || settings.leinient,
        this.envType
    )

    companion object {

        operator fun invoke(
            unchecked: Boolean = false,
            leinient: Boolean = false,
            envType: EnvType = EnvType.JOINED,
        ) = object : FormatReaderSettings {
            override val unchecked: Boolean = unchecked
            override val leinient: Boolean = leinient
            override val envType: EnvType = envType
        }

    }
}
