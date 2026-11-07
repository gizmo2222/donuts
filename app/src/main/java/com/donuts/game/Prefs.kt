package com.donuts.game

import android.content.Context

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("donuts", Context.MODE_PRIVATE)

    // Milliseconds of idle time before a chain is highlighted; 0 = hints off.
    var hintDelayMs: Long
        get()  = p.getLong("hint_ms", 5_000L)
        set(v) { p.edit().putLong("hint_ms", v).apply() }

    // 6 = big donuts (6x6), the easier board for small fingers; 8 = small donuts (8x8)
    var gridSize: Int
        get()  = p.getInt("grid_size", DEFAULT_GRID).let { if (it in GRID_SIZES) it else DEFAULT_GRID }
        set(v) { p.edit().putInt("grid_size", v).apply() }

    var tutorialSeen: Boolean
        get()  = p.getBoolean("tutorial_seen", false)
        set(v) { p.edit().putBoolean("tutorial_seen", v).apply() }

    var soundEnabled: Boolean
        get()  = p.getBoolean("sound_enabled", true)
        set(v) { p.edit().putBoolean("sound_enabled", v).apply() }

    var hapticEnabled: Boolean
        get()  = p.getBoolean("haptic_enabled", true)
        set(v) { p.edit().putBoolean("haptic_enabled", v).apply() }

    var lifetimeDonuts: Int
        get()  = p.getInt("lifetime_donuts", 0)
        set(v) { p.edit().putInt("lifetime_donuts", v).apply() }

    // Best single-session totals. Never shown as a score; they only unlock stickers.
    var highScore6x6: Int
        get()  = p.getInt("hs_6x6", 0)
        set(v) { p.edit().putInt("hs_6x6", v).apply() }

    var highScore8x8: Int
        get()  = p.getInt("hs_8x8", 0)
        set(v) { p.edit().putInt("hs_8x8", v).apply() }

    /** The best session total for a [size]x[size] board: 6 uses hs_6x6, any other size hs_8x8. */
    fun highScore(size: Int): Int = if (size == 6) highScore6x6 else highScore8x8
    fun setHighScore(size: Int, v: Int) { if (size == 6) highScore6x6 = v else highScore8x8 = v }

    var bestChainLength: Int
        get()  = p.getInt("best_chain", 0)
        set(v) { p.edit().putInt("best_chain", v).apply() }

    // True once the player has cleared a golden (wild) donut. Unlocks the Gold Finder sticker.
    var goldenPopped: Boolean
        get()  = p.getBoolean("golden_popped", false)
        set(v) { p.edit().putBoolean("golden_popped", v).apply() }

    // Visits to the game screen. Unlocks the Super Fan sticker.
    var sessionCount: Int
        get()  = p.getInt("sessions", 0)
        set(v) { p.edit().putInt("sessions", v).apply() }

    // Bitmask of stickers already shown to the player; -1 = never set (see GameView.checkStickers)
    var stickersAnnounced: Int
        get()  = p.getInt("stickers_announced", -1)
        set(v) { p.edit().putInt("stickers_announced", v).apply() }

    // The board and counter as left by the last pause (see SavedBoard); null = none
    var savedBoard: String?
        get()  = p.getString("saved_board", null)
        set(v) { p.edit().apply { if (v == null) remove("saved_board") else putString("saved_board", v) }.apply() }

    companion object {
        /** The board sizes Settings offers; anything else stored falls back to [DEFAULT_GRID]. */
        val GRID_SIZES = intArrayOf(6, 8)
        const val DEFAULT_GRID = 6
    }
}
