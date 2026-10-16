package com.hermes.android.voice

/*
 * TTS self-echo guard, ported from Hermes Desktop (apps/desktop/src/lib/voice-tts-echo.ts, MIT,
 * Nous Research), itself a port of tools/voice_mode_transcript.is_tts_echo: over the speaker the
 * reply bleeds into the mic, trips the barge-in trigger and gets transcribed. A transcript that
 * matches what was being spoken is Hermes hearing itself, not the user.
 */

const val DEFAULT_TTS_ECHO_SIMILARITY_THRESHOLD = 0.6
const val MIN_FRAGMENT_LENGTH_FOR_ECHO = 10

private fun normalizeForEchoCompare(text: String): List<Int> =
    text.replace(Regex("\\s+"), " ").trim().lowercase().codePoints().toArray().toList()

/** difflib.SequenceMatcher(None, a, b).ratio(): isjunk=None, autojunk=True. */
fun sequenceMatcherRatio(a: List<Int>, b: List<Int>): Double {
    val total = a.size + b.size
    if (total == 0) return 1.0
    val b2j = HashMap<Int, MutableList<Int>>()
    b.forEachIndexed { j, ch -> b2j.getOrPut(ch) { mutableListOf() } += j }
    if (b.size >= 200) {
        val ntest = b.size / 100 + 1
        b2j.entries.removeAll { it.value.size > ntest }
    }

    fun findLongestMatch(alo: Int, ahi: Int, blo: Int, bhi: Int): Triple<Int, Int, Int> {
        var besti = alo
        var bestj = blo
        var bestsize = 0
        var j2len = HashMap<Int, Int>()
        for (i in alo until ahi) {
            val next = HashMap<Int, Int>()
            for (j in b2j[a[i]].orEmpty()) {
                if (j < blo) continue
                if (j >= bhi) break
                val k = (j2len[j - 1] ?: 0) + 1
                next[j] = k
                if (k > bestsize) {
                    besti = i - k + 1
                    bestj = j - k + 1
                    bestsize = k
                }
            }
            j2len = next
        }
        while (besti > alo && bestj > blo && a[besti - 1] == b[bestj - 1]) {
            besti--
            bestj--
            bestsize++
        }
        while (besti + bestsize < ahi && bestj + bestsize < bhi && a[besti + bestsize] == b[bestj + bestsize]) {
            bestsize++
        }
        return Triple(besti, bestj, bestsize)
    }

    var matches = 0
    val queue = ArrayDeque<IntArray>()
    queue.addLast(intArrayOf(0, a.size, 0, b.size))
    while (queue.isNotEmpty()) {
        val (alo, ahi, blo, bhi) = queue.removeLast()
        val (i, j, k) = findLongestMatch(alo, ahi, blo, bhi)
        if (k > 0) {
            matches += k
            if (alo < i && blo < j) queue.addLast(intArrayOf(alo, i, blo, j))
            if (i + k < ahi && j + k < bhi) queue.addLast(intArrayOf(i + k, ahi, j + k, bhi))
        }
    }
    return 2.0 * matches / total
}

/** True when [transcript] looks like the mic catching [spokenText], the reply being spoken. */
fun isTtsEcho(transcript: String, spokenText: String, threshold: Double = DEFAULT_TTS_ECHO_SIMILARITY_THRESHOLD): Boolean {
    val a = normalizeForEchoCompare(transcript)
    val b = normalizeForEchoCompare(spokenText)
    if (a.isEmpty() || b.isEmpty()) return false
    if (sequenceMatcherRatio(a, b) >= threshold) return true
    if (a.size < MIN_FRAGMENT_LENGTH_FOR_ECHO || a.size >= b.size) return false
    for (start in 0..b.size - a.size) {
        if (sequenceMatcherRatio(a, b.subList(start, start + a.size)) >= threshold) return true
    }
    return false
}
