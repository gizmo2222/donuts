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

    // Calm mode, set on the grown-ups page: the same still look as the system's "remove animations"
    var calmMotion: Boolean
        get()  = p.getBoolean("calm_motion", false)
        set(v) { p.edit().putBoolean("calm_motion", v).apply() }

    // The sprinkle party meter (see PartyMeter): donuts poured in, and party chains still to play
    var partyMeter: Int
        get()  = p.getInt("party_meter", 0)
        set(v) { p.edit().putInt("party_meter", v).apply() }

    var partyLeft: Int
        get()  = p.getInt("party_left", 0)
        set(v) { p.edit().putInt("party_left", v).apply() }

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

    // Donut friends met so far (0..12, see FriendRules). Kept forever: resetStickers leaves this alone on purpose.
    var friendsMet: Int
        get()  = p.getInt("friends_met", 0).coerceIn(0, FriendRules.FRIEND_COUNT)
        set(v) { p.edit().putInt("friends_met", v).apply() }

    // Toppings earned so far (0..ToppingRules.EARNABLE, PLAIN not counted). Kept forever like friendsMet.
    var toppingsEarned: Int
        get()  = p.getInt("toppings_earned", 0).coerceIn(0, ToppingRules.EARNABLE)
        set(v) { p.edit().putInt("toppings_earned", v).apply() }

    // Shop pieces built on the home screen (0..ShopRules.COUNT, see ShopRules.kept). Kept forever like friendsMet.
    var shopEarned: Int
        get()  = p.getInt("shop_earned", 0).coerceIn(0, ShopRules.COUNT)
        set(v) { p.edit().putInt("shop_earned", v).apply() }

    // Shop pieces Steven has watched arrive (0..shopEarned); the home screen pops in the ones above it
    var shopSeen: Int
        get()  = p.getInt("shop_seen", 0).coerceIn(0, ShopRules.COUNT)
        set(v) { p.edit().putInt("shop_seen", v).apply() }

    // The topping Steven picked (Topping.key); ToppingRules.chosen falls back to PLAIN if it is unknown or not earned
    var topping: String?
        get()  = p.getString("topping", null)
        set(v) { p.edit().putString("topping", v).apply() }

    // The seasonal look Steven has on (Season.key, "" when he took it off); SeasonRules.worn ignores it out of season
    var seasonLook: String?
        get()  = p.getString("season_look", null)
        set(v) { p.edit().putString("season_look", v).apply() }

    // The last season run revealed (SeasonRules.id, e.g. "snow-2026"), so each one is revealed once a year
    var seasonSeen: String?
        get()  = p.getString("season_seen", null)
        set(v) { p.edit().putString("season_seen", v).apply() }

    // How Steven plays (PlayTally text form): chain lengths and power-ups. Stays on the phone; no reset clears it.
    var playTally: String?
        get()  = p.getString("play_tally", null)
        set(v) { p.edit().putString("play_tally", v).apply() }

    // The picture prompt (PicturePrompts text form): what it asks for, how far along, how many were finished
    var picturePrompt: String?
        get()  = p.getString("picture_prompt", null)
        set(v) { p.edit().putString("picture_prompt", v).apply() }

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
