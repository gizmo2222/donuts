package com.donuts.game

import android.graphics.RectF
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import kotlin.math.min

// TalkBack for the game screen: what each part of the canvas is called and what pressing it does.
// The nodes follow what is on screen: the show-someone card alone, else an open panel's page, else the
// HUD and the board. Panel buttons reuse their touch codes (settingsHit, stickersHit) as ids, so a
// TalkBack press goes through the same handleSettingsTap / handleStickersTap as a finger.
// The board itself is described, not played: dragging chains is not offered through TalkBack.

internal const val ACCESS_STICKERS = 1
internal const val ACCESS_SETTINGS = 2
internal const val ACCESS_COUNTER  = 3
internal const val ACCESS_PROMPT   = 4
internal const val ACCESS_BOARD    = 5
internal const val ACCESS_CARD     = 9
internal const val ACCESS_SETTINGS_BASE = 1000   // + settingsHit code
internal const val ACCESS_STICKERS_BASE = 2000   // + stickersHit code
internal const val ACCESS_STICKER_TILE  = 3000   // + sticker index, on the stickers page (read only)
internal const val ACCESS_BOX           = 3100   // the donut box and the stats line under it
internal const val ACCESS_TALLY         = 3200   // the grown-ups play tally

/** Frames between TalkBack refreshes while it is on (about four a second). */
internal const val ACCESS_TICK_FRAMES = 15

/** The nodes TalkBack sees right now, in reading order. */
internal fun GameView.accessNodes(): List<AccessNode> = synchronized(holder) {
    val out = ArrayList<AccessNode>(24)
    when {
        showSomeoneMs >= 0 -> cardNodes(out)
        settingsOpen || settingsAnim > 0f -> settingsNodes(out)
        stickersOpen || stickersAnim > 0f -> stickersNodes(out)
        else -> hudNodes(out)
    }
    out
}

/** Every 15th frame while TalkBack is on, the UI thread checks whether the nodes changed. */
internal fun GameView.tickAccess() {
    if (++accessFrame % ACCESS_TICK_FRAMES != 0) return
    val am = context.getSystemService(AccessibilityManager::class.java) ?: return
    if (am.isEnabled) post(accessRefresh)
}

private fun GameView.cardNodes(out: MutableList<AccessNode>) {
    val words = if (showSomeoneKind == SHOW_REST) "Great playing! Time for a rest?" else "Show someone! Steven did it!"
    val whole = RectF(0f, 0f, surfaceW.toFloat(), surfaceH.toFloat())
    out += accessNode(ACCESS_CARD, "$words Tap to close", whole, act = {
        synchronized(holder) {
            val now = SystemClock.elapsedRealtime()
            if (showSomeoneMs >= 0 && now - showSomeoneMs >= SHOW_CARD_GUARD_MS) closeShowSomeone(now)
        }
    })
}

private fun GameView.hudNodes(out: MutableList<AccessNode>) {
    out += accessNode(ACCESS_STICKERS, "Stickers", stickersBtnRect, act = {
        synchronized(holder) { stickersPressMs = SystemClock.elapsedRealtime(); stickersOpen = true }
    })
    out += accessNode(ACCESS_SETTINGS, "Settings", settingsBtnRect, act = {
        synchronized(holder) {
            settingsPressMs = SystemClock.elapsedRealtime(); showSettingsPage(SETTINGS_KID); settingsOpen = true
        }
    })
    val counter = RectF(palRect).apply { union(counterDigits) }
    if (!counter.isEmpty) out += accessNode(ACCESS_COUNTER, donutsWord(displayedCount), counter)
    val p = prompt
    if (p != null && !promptRect.isEmpty) out += accessNode(ACCESS_PROMPT, promptWords(p), promptRect)
    val area = RectF(boardLeft, boardTop, boardLeft + board.cols * cellSize, boardTop + board.rows * cellSize)
    if (!area.isEmpty) out += accessNode(ACCESS_BOARD,
        "Donut board, ${board.cols} by ${board.rows}. Drag across three or more matching donuts to pop them.", area)
}

internal fun donutsWord(n: Int): String = if (n == 1) "1 donut" else "$n donuts"

internal fun promptWords(p: Prompt): String {
    val name = DonutType.values()[p.type.coerceIn(0, DonutType.values().size - 1)].name.lowercase()
    return if (p.complete) "Goal done: $name" else "Goal: pop ${p.left} more $name"
}

private fun GameView.settingsNodes(out: MutableList<AccessNode>) {
    val dy = settingsSlideY()
    fun add(hit: Int, label: String, r: RectF, selected: Boolean = false) {
        if (r.isEmpty) return
        val s = RectF(r).apply { offset(0f, dy) }
        out += accessNode(ACCESS_SETTINGS_BASE + hit, label, s, selected = selected, act = {
            synchronized(holder) { if (settingsOpen) handleSettingsTap(hit) }
        })
    }
    fun pair(hit: Int, word: String, rects: Array<RectF>, on: Boolean) {
        add(hit, "$word on", rects[0], on)
        add(hit + 1, "$word off", rects[1], !on)
    }
    val now = SystemClock.elapsedRealtime()
    when (settingsPage) {
        SETTINGS_KID -> {
            pair(0, "Sound", soundRects, prefs.soundEnabled)
            pair(10, "Buzz", buzzRects, prefs.hapticEnabled)
            pair(2, "Hints", hintRects, prefs.hintDelayMs != 0L)
            add(14, "Grown-ups only", settingsGrownRect)
            add(7, "Done", settingsCloseRect)
        }
        SETTINGS_GATE -> {
            val typed = parentGate.entry.ifEmpty { "nothing yet" }
            if (!gateAnswerRect.isEmpty) out += accessNode(ACCESS_SETTINGS_BASE + 99,
                "Grown-ups only. ${parentGate.question} Typed: $typed",
                RectF(gateAnswerRect).apply { offset(0f, dy) })
            for (d in 0..9) add(30 + d, "$d", gateKeyRects[d])
            add(15, "Back", settingsCloseRect)
        }
        else -> {
            for (i in gridOptions.indices) add(4 + i, "Size: " + sizeWords(gridLabels[i]), gridRects[i], gridOptions[i] == prefs.gridSize)
            add(40, "Motion: ${motionLabels[0]}", motionRects[0], !prefs.calmMotion)
            add(41, "Motion: ${motionLabels[1]}", motionRects[1], prefs.calmMotion)
            for (j in WindDown.OPTIONS.indices)
                add(42 + j, "Rest reminder: ${WindDown.LABELS[j]}", restRects[j], prefs.windDownMin == WindDown.OPTIONS[j])
            add(6, if (newGameConfirm.isArmed(now)) "New game? Tap again to start over" else "New game, tap twice", settingsResetRect)
            add(9, if (stickersConfirm.isArmed(now)) "Clear all stickers? Tap again to clear" else "Reset stickers, tap twice", settingsStickersRect)
            if (!settingsTallyRect.isEmpty) out += accessNode(ACCESS_TALLY, tallyLines.joinToString(". "),
                RectF(settingsTallyRect).apply { offset(0f, dy) })
            add(7, "Done", settingsCloseRect)
        }
    }
}

/** "Big  6×6" read aloud: "Big, 6 by 6". */
internal fun sizeWords(label: String): String =
    label.replace("×", " by ").replace(Regex("\\s+"), " ").trim().replaceFirst(" ", ", ")

private fun GameView.stickersNodes(out: MutableList<AccessNode>) {
    val dy = stickersSlideY()
    placePanelTabs()
    val pages = panelTabPages()
    val page = panelPageNow()
    fun shifted(r: RectF) = RectF(r).apply { offset(0f, dy) }
    fun add(hit: Int, label: String, r: RectF, selected: Boolean = false) {
        if (r.isEmpty) return
        out += accessNode(ACCESS_STICKERS_BASE + hit, label, shifted(r), selected = selected, act = {
            synchronized(holder) { if (stickersOpen) handleStickersTap(hit) }
        })
    }
    if (pages.size > 1) {
        add(12, "Stickers page", stickersTabRect, page == 0)
        if (1 in pages) add(13, "Friends page", friendsTabRect, page == 1)
        if (2 in pages) add(16, "Toppings page", toppingsTabRect, page == 2)
    }
    when (page) {
        1 -> for (i in 0 until min(prefs.friendsMet, DRAWN))
            add(20 + i, FriendRules.FRIENDS[i].name, stickerRects[i])
        2 -> {
            val tiles = toppingTiles()
            for (i in 0 until pickerTileCount()) {
                val look = if (i >= tiles.size) season else null
                val worn = if (look != null) seasonWorn == look else seasonWorn == null && tiles[i] == topping
                val name = if (look != null) seasonName(look) else toppingName(tiles[i])
                add(60 + i, if (worn) "$name, wearing" else name, stickerRects[i], worn)
            }
        }
        else -> {
            val mask = StickerRules.earnedMask(stickerStats())
            for (i in 0 until 12) {
                val earned = mask and (1 shl i) != 0
                if (!stickerRects[i].isEmpty) out += accessNode(ACCESS_STICKER_TILE + i,
                    if (earned) "${STICKER_NAMES[i]} sticker" else "${STICKER_NAMES[i]}, not earned yet",
                    shifted(stickerRects[i]), selected = earned)
            }
            if (!donutBoxRect.isEmpty) out += accessNode(ACCESS_BOX,
                "Donut box. " + StickerRules.statsLine(lifetimeDonuts(), Integer.bitCount(mask)),
                shifted(if (statsBeside) RectF(donutBoxRect).apply { union(statsRect) } else donutBoxRect))
        }
    }
    add(7, "Done", stickersCloseRect)
}
