package com.donuts.game

/** Time a banner is guaranteed on screen before a waiting one may replace it. */
const val BANNER_FIRST_PART_MS = 1000L

/**
 * Banners wait their turn: a new one starts at once when none is showing or the current one has
 * shown its first [firstPartMs]; otherwise it queues and [poll] hands it out later. Pure, no Android types.
 */
class BannerQueue(private val firstPartMs: Long) {
    private var startMs = -1L
    private val waiting = ArrayDeque<String>()

    /** The label to show now, or null when it had to wait. */
    fun offer(now: Long, label: String): String? {
        if (waiting.isEmpty() && canStart(now)) { startMs = now; return label }
        waiting.addLast(label)
        return null
    }

    /** The next waiting label once the current banner has had its first part, else null. */
    fun poll(now: Long): String? {
        if (waiting.isEmpty() || !canStart(now)) return null
        startMs = now
        return waiting.removeFirst()
    }

    fun clear() { startMs = -1L; waiting.clear() }

    private fun canStart(now: Long) = startMs < 0 || now - startMs >= firstPartMs
}
