package xyz.wagyourtail.unimined.mapping.element.metadata

/**
 * Marker for metadata that drives a **bytecode-level** transform — a remapper
 * or class-file rewriter applies these (e.g. unpick constants, synthetic
 * bridge delegates, access flags).
 *
 * Elements may implement this together with [SourceTransform] when the
 * transform exists at both levels. Query via
 * `metadata.filterIsInstance<BytecodeTransform>()`.
 */
interface BytecodeTransform
