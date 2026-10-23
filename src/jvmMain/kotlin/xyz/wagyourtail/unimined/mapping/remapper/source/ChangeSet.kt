package xyz.wagyourtail.unimined.mapping.remapper.source

import java.util.*

/** collected `(offset-range → replacement)` edits applied back-to-front */
class ChangeSet {
    private val changes = TreeMap<IntRange, () -> String>(compareBy({ it.first }, { it.last }))

    /** replace `[start, end)` with [text] */
    fun replace(start: Int, end: Int, text: String) {
        changes.merge(start until end, { text }) { old, new -> { old() + new() } }
    }

    /** replace `[start, end)` lazily — [with] is evaluated at apply time, so
     *  it can observe state collected during the whole pass (e.g. wildcard
     *  imports expanding to every mapped name seen) */
    fun replaceLazy(start: Int, end: Int, with: () -> String) {
        changes.merge(start until end, with) { old, new -> { old() + new() } }
    }

    /** insert [text] at [offset] */
    fun insert(offset: Int, text: String) = replace(offset, offset, text)

    /** `true` when the range doesn't overlap a queued edit */
    fun valid(start: Int, end: Int): Boolean {
        val floor = changes.floorKey(start until end) ?: IntRange.EMPTY
        val ceil = changes.ceilingKey(start until end) ?: IntRange.EMPTY
        return floor.isEmpty() || floor.last < start && (ceil.isEmpty() || ceil.first >= end)
    }

    /** apply all edits back-to-front over [text] */
    fun apply(text: String): String {
        var result = text
        for ((range, new) in changes.descendingMap()) result = result.replaceRange(range, new())
        return result
    }

    /** per-line net delta — `(line, insertedCount)` for linemap adjustment.
     *  Zero for line-preserving transforms (the default — stages write
     *  inline so path-1 remapping never shifts lines). */
    fun lineDeltas(text: String): List<Pair<Int, Int>> =
        changes.mapNotNull { (range, new) ->
            val replaced = text.substring(range)
            val net = new().count { it == '\n' } - replaced.count { it == '\n' }
            if (net == 0) null else text.substring(0, range.first).count { it == '\n' } + 1 to net
        }
}
