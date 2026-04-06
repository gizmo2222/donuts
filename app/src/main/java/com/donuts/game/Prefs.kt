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

    var soundEnabled: Boolean
        get()  = p.getBoolean("sound_enabled", false)
        set(v) { p.edit().putBoolean("sound_enabled", v).apply() }

    var hapticEnabled: Boolean
        get()  = p.getBoolean("haptic_enabled", false)
        set(v) { p.edit().putBoolean("haptic_enabled", v).apply() }

    var lifetimeDonuts: Int
        get()  = p.getInt("lifetime_donuts", 0)
        set(v) { p.edit().putInt("lifetime_donuts", v).apply() }

    var highScore6x6: Int
        get()  = p.getInt("hs_6x6", 0)
        set(v) { p.edit().putInt("hs_6x6", v).apply() }

    var highScore8x8: Int
        get()  = p.getInt("hs_8x8", 0)
        set(v) { p.edit().putInt("hs_8x8", v).apply() }

    var soundPackIndex: Int
        get()  = p.getInt("sound_pack", 0)
        set(v) { p.edit().putInt("sound_pack", v).apply() }

    var hapticTheme: Int
        get()  = p.getInt("haptic_theme", 1)
        set(v) { p.edit().putInt("haptic_theme", v).apply() }
}
