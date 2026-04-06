package com.donuts.game

import android.content.Context

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("donuts", Context.MODE_PRIVATE)

    var themeIndex: Int
        get()  = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply() }

    var hintDelayMs: Long
        get()  = p.getLong("hint_ms", 5_000L)
        set(v) { p.edit().putLong("hint_ms", v).apply() }

    var gridSize: Int
        get()  = p.getInt("grid_size", 8)
        set(v) { p.edit().putInt("grid_size", v).apply() }

    var tutorialSeen: Boolean
        get()  = p.getBoolean("tutorial_seen", false)
        set(v) { p.edit().putBoolean("tutorial_seen", v).apply() }
}
