package com.hermes.android.data

import android.content.Context
import com.hermes.android.domain.markdown.TableColumnWidths
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TableColumnWidths] in shared preferences, with Desktop's limits: a record
 * older than a week is forgotten and only the 64 most recently resized tables
 * are kept. Losing a record costs the reader one drag.
 */
@Singleton
class PrefsTableColumnWidths @Inject constructor(
    @ApplicationContext context: Context,
) : TableColumnWidths {

    private class Entry(val widths: List<Float>, val time: Long)

    private val prefs = context.getSharedPreferences("hermes_md_tables", Context.MODE_PRIVATE)

    // Read once, then authoritative: a drag never pays a parse.
    private val entries: MutableMap<String, Entry> by lazy { load() }

    @Synchronized
    override fun read(key: String, columns: Int): List<Float>? =
        entries[key]?.widths?.takeIf { it.size == columns }

    @Synchronized
    override fun write(key: String, widths: List<Float>) {
        if (widths.size < 2 || widths.any { !it.isFinite() || it <= 0f }) return
        entries[key] = Entry(widths, System.currentTimeMillis())
        flush()
    }

    @Synchronized
    override fun clear(key: String) {
        if (entries.remove(key) != null) flush()
    }

    private fun load(): MutableMap<String, Entry> {
        val out = HashMap<String, Entry>()
        val raw = prefs.getString(KEY, null) ?: return out
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        runCatching {
            val all = JSONObject(raw)
            for (key in all.keys()) {
                val item = all.getJSONObject(key)
                val time = item.getLong("t")
                val array = item.getJSONArray("w")
                val widths = (0 until array.length()).map { array.getDouble(it).toFloat() }
                if (time > cutoff && widths.size > 1 && widths.all { it.isFinite() && it > 0f }) {
                    out[key] = Entry(widths, time)
                }
            }
        }
        return out
    }

    private fun flush() {
        // Bounded: drop the coldest tables past MAX_ENTRIES.
        val excess = entries.size - MAX_ENTRIES
        if (excess > 0) {
            entries.entries.sortedBy { it.value.time }.take(excess).forEach { entries.remove(it.key) }
        }
        val all = JSONObject()
        for ((key, entry) in entries) {
            all.put(key, JSONObject().put("t", entry.time).put("w", JSONArray(entry.widths.map { it.toDouble() })))
        }
        prefs.edit().putString(KEY, all.toString()).apply()
    }

    private companion object {
        const val KEY = "widths_v1"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_ENTRIES = 64
    }
}
