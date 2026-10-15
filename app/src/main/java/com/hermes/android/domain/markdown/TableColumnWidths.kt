package com.hermes.android.domain.markdown

/**
 * Column widths a reader dragged a markdown table to, as in Hermes Desktop's
 * `markdown-table-widths.ts`. A table has no id of its own (it is re-parsed from
 * text on every render), so the record is keyed by its header row; the same
 * table resolves to the same key after a re-render, a chat switch or a restart.
 *
 * Widths are in dp: on the phone a table scrolls sideways instead of fitting
 * a pane, so there is no box width to take percentages of.
 */
interface TableColumnWidths {
    /** Stored widths, or null when the table was never resized or now has another column count. */
    fun read(key: String, columns: Int): List<Float>?

    fun write(key: String, widths: List<Float>)

    /** Hands the table back to its default widths. */
    fun clear(key: String)
}

/**
 * Stable identity for a table from its header cells, with Desktop's hash
 * (`markdownTableKey`). A collision only means two identically headed tables
 * share a width record, which is what a reader would want anyway.
 */
fun markdownTableKey(headers: List<String>): String {
    var hash = 0L
    val joined = "${headers.size}\u0001${headers.joinToString("\u0001")}"
    for (ch in joined) hash = (hash * 31 + ch.code) and 0xFFFFFFFFL
    return hash.toString(36)
}
