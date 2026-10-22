package xyz.wagyourtail.unimined.mapping.element.metadata

/**
 * Marker for metadata that drives a **source-level** transform — a
 * remapped-source inserter (jar → decompile → rewrite) applies these.
 *
 * Elements may implement this together with [BytecodeTransform] when the
 * transform exists at both levels (e.g. access flags, annotations,
 * signatures). Query via `metadata.filterIsInstance<SourceTransform>()`.
 */
interface SourceTransform
