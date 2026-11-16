package xyz.wagyourtail.unimined.mapping.formats.access

import xyz.wagyourtail.unimined.mapping.MappingTree
import xyz.wagyourtail.unimined.mapping.Namespace
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readBytes

/**
 * JVM [java.nio.file.Path] conveniences for [AccessFormatConvert] — kept
 * out of the common artifact so the converter stays multiplatform.
 * Extensions on the object resolve at the same `AccessFormatConvert.*`
 * call sites the 1.x helpers used.
 */

/**
 * Sniff an access file's authored format — see [AccessFormatConvert.detect];
 * [path] supplies the filename signal (`accesstransformer.cfg`,
 * `*.accesswidener`, `*_at.cfg`).
 */
fun AccessFormatConvert.detect(path: Path?, bytes: ByteArray): AccessFormatConvert.Format? =
    detect(path?.name, bytes)

/**
 * Read [file] and emit in [to] — byte-level counterpart of
 * [AccessFormatConvert.convert].
 */
fun AccessFormatConvert.convert(
    file: Path,
    from: AccessFormatConvert.Format,
    to: AccessFormatConvert.Format,
    tree: MappingTree? = null,
    targetNs: Namespace? = null,
    warn: ((String) -> Unit)? = null
): ByteArray = convert(file.readBytes(), from, to, tree, targetNs, warn)
