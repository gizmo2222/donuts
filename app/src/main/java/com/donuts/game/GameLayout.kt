package com.donuts.game

import android.graphics.*
import androidx.core.view.ViewCompat
import kotlin.math.*

// Where everything sits: board, HUD and the two panels.
// Part of GameView: these are extension functions on it, split out of GameView.kt.

internal fun GameView.computeLayout() {
    val w = surfaceW.toFloat(); val h = surfaceH.toFloat()
    if (w == 0f || h == 0f) return
    uiScale.update(surfaceW, surfaceH)
    u = uiScale.u

    // Safe area: never lay content under the status bar, gesture bar, or a cutout.
    val safeL = insetL.toFloat();    val safeT = insetT.toFloat()
    val safeW = w - insetL - insetR; val safeH = h - insetT - insetB
    val margin = 12f * u

    // Chrome in design-dp: one quiet button row above the board, one number below it
    val btnH      = 56f * u
    val baseGap   = 10f * u
    val baseCtrH  = 88f * u
    val baseStrip = 64f * u     // the donut-box strip under the counter

    // Board: fills the safe width, but never taller than the space left after the
    // button row above and the counter below. Sized from the base chrome, so spare
    // height never widens it.
    val boardPx = LayoutMath.boardSide(safeW - margin * 2f, safeH - margin * 2f - (btnH + baseGap) - baseCtrH - baseStrip)
    cellSize  = boardPx / board.cols
    boardLeft = safeL + (safeW - boardPx) / 2f

    // Spare height (tall phones) goes to the chrome: bigger counter, taller strip, a wider gap
    val spare     = safeH - margin * 2f - (btnH + baseGap + boardPx + baseCtrH + baseStrip)
    counterH      = baseCtrH + LayoutMath.spareShare(spare, 0.25f, 32f * u)
    val stripH    = baseStrip + LayoutMath.spareShare(spare, 0.25f, 28f * u)
    val btnGap    = baseGap + LayoutMath.spareShare(spare, 0.15f, 16f * u)
    val btnBlockH = btnH + btnGap

    // Centre the whole block (buttons + board + counter) inside the safe area
    val totalBlockH = btnBlockH + boardPx + counterH + stripH
    boardTop = safeT + (safeH - totalBlockH) / 2f + btnBlockH

    // HUD: stickers at the board's left edge, settings at its right edge
    val btnY = boardTop - btnH - btnGap
    stickersBtnRect = RectF(boardLeft, btnY, boardLeft + btnH, btnY + btnH)
    settingsBtnRect = RectF(boardLeft + boardPx - btnH, btnY, boardLeft + boardPx, btnY + btnH)
    // The party meter: a chunky pill centred between the two buttons
    val meterGap = 14f * u; val meterH = btnH * 0.5f
    val meterW = min(240f * u, settingsBtnRect.left - stickersBtnRect.right - meterGap * 2f)
    val meterCx = boardLeft + boardPx / 2f; val meterCy = btnY + btnH / 2f
    if (meterW > meterH * 2f) partyMeterRect.set(meterCx - meterW / 2f, meterCy - meterH / 2f, meterCx + meterW / 2f, meterCy + meterH / 2f)
    else partyMeterRect.setEmpty()

    boxStrip.rect.set(boardLeft, boardTop + boardPx + counterH, boardLeft + boardPx, boardTop + boardPx + counterH + stripH)

    excludeBackSwipe(boardPx)
    layoutSettingsPanel(safeL, safeT, safeW, safeH)
    layoutStickersPanel(safeL, safeT, safeW, safeH)
    buildSprites()
}

/**
 * Settings panel: sized in design-dp, then uniformly shrunk ([settingsSc] < 1) when the
 * full-size panel would not fit the safe height, so small phones keep every control.
 */
internal fun GameView.layoutSettingsPanel(safeL: Float, safeT: Float, safeW: Float, safeH: Float) {
    val pw     = min(safeW - 24f * u, 480f * u)
    val availH = safeH - 24f * u
    for (page in SETTINGS_KID..SETTINGS_GROWN) {
        settingsSc = 1f
        val ph = placeSettings(page, pw, safeL, safeT, safeW, safeH)
        if (ph > availH) {
            settingsSc = availH / ph
            placeSettings(page, pw, safeL, safeT, safeW, safeH)
        }
        settingsPageSc[page] = settingsSc
    }
    showSettingsPage(settingsPage)
}

/** Make [page] the shown settings page: the panel, its fit factor and its bottom button follow it. */
internal fun GameView.showSettingsPage(page: Int) {
    settingsPage      = page
    panelRect         = settingsPageRects[page]
    settingsSc        = settingsPageSc[page]
    settingsCloseRect = settingsPageClose[page]
}

/** Lay out one settings page at the current [GameView.settingsSc]; returns its height. */
internal fun GameView.placeSettings(page: Int, pw: Float, safeL: Float, safeT: Float, safeW: Float, safeH: Float): Float {
    val k      = u * settingsSc
    val pad    = 16f * k
    val titleH = 84f * k
    val secH   = 76f * k      // a settings row: the picture over its word, beside the two choices
    val secGap = 12f * k
    val btnH   = 60f * k      // the choice buttons, centred in the row
    val iconW  = 88f * k      // the picture-and-word column on the left of each row
    val rowH   = 56f * k      // Grown-ups; New game / Reset stickers
    val confGap = 24f * k
    val closeH = 56f * k
    val verH   = 24f * k      // the small version line under Done
    val promiseH = 64f * k    // the two-line promise to parents under New game / Reset stickers
    val tallyH = 108f * k     // the play tally under the promise: a heading and three lines, then a gap
    val questH = 48f * k      // the gate's question
    val ansH   = 60f * k      // the box the typed digits show in
    val keyH   = 58f * k; val keyGap = 10f * k
    val ph = when (page) {
        SETTINGS_KID  -> titleH + secH * 3f + secGap * 2f + confGap + rowH + pad + closeH + pad + verH
        SETTINGS_GATE -> titleH + questH + ansH + keyGap * 2f + keyH * 4f + keyGap * 3f + pad + closeH + pad
        else          -> titleH + secH * 3f + secGap * 2f + confGap + rowH + promiseH + tallyH + closeH + pad + verH
    }
    val pl = safeL + (safeW - pw) / 2f
    val pt = safeT + (safeH - ph) / 2f
    settingsPageRects[page].set(pl, pt, pl + pw, pt + ph)

    fun row(i: Int, top: Float, rects: Array<RectF>) {
        settingsIconRects[i].set(pl + pad, top, pl + pad + iconW, top + secH)
        val n  = rects.size
        val x0 = pl + pad + iconW
        val bw = (pl + pw - x0 - pad * (n + 1)) / n
        val bTop = top + (secH - btnH) / 2f
        for (j in 0 until n) {
            val x = x0 + pad + j * (bw + pad)
            rects[j].set(x, bTop, x + bw, bTop + btnH)
        }
    }
    val verBelow = if (page == SETTINGS_GATE) 0f else verH
    settingsPageClose[page].set(pl + pad, pt + ph - verBelow - closeH - pad, pl + pw - pad, pt + ph - verBelow - pad)
    when (page) {
        SETTINGS_KID -> {
            row(ICON_SOUND, pt + titleH, soundRects)
            row(ICON_BUZZ, pt + titleH + secH + secGap, buzzRects)
            row(ICON_HINTS, pt + titleH + (secH + secGap) * 2f, hintRects)
            val gTop = pt + titleH + secH * 3f + secGap * 2f + confGap
            settingsGrownRect = RectF(pl + pad, gTop, pl + pw - pad, gTop + rowH)
        }
        SETTINGS_GATE -> {
            val aw = 150f * k; val aTop = pt + titleH + questH
            gateAnswerRect = RectF(pl + (pw - aw) / 2f, aTop, pl + (pw + aw) / 2f, aTop + ansH)
            val kw = min(104f * k, (pw - pad * 4f) / 3f)
            val gapX = 14f * k
            val x0 = pl + (pw - kw * 3f - gapX * 2f) / 2f
            val y0 = aTop + ansH + keyGap * 2f
            for (d in 0..9) {
                val r = if (d == 0) 3 else (d - 1) / 3
                val c = if (d == 0) 1 else (d - 1) % 3
                val x = x0 + c * (kw + gapX); val y = y0 + r * (keyH + keyGap)
                gateKeyRects[d].set(x, y, x + kw, y + keyH)
            }
        }
        else -> {
            row(ICON_GRID, pt + titleH, gridRects)
            row(ICON_MOTION, pt + titleH + secH + secGap, motionRects)
            row(ICON_REST, pt + titleH + (secH + secGap) * 2f, restRects)
            val rTop = pt + titleH + secH * 3f + secGap * 2f + confGap
            val rw = (pw - pad * 3f) / 2f
            settingsResetRect    = RectF(pl + pad, rTop, pl + pad + rw, rTop + rowH)
            settingsStickersRect = RectF(pl + pad * 2f + rw, rTop, pl + pw - pad, rTop + rowH)
            val tTop = rTop + rowH + promiseH
            settingsTallyRect = RectF(pl + pad, tTop, pl + pw - pad, tTop + tallyH - pad)
        }
    }
    return ph
}

/**
 * Sticker panel: 4 rows of 3 tiles, or 2 rows of 6 on wide short screens (landscape tablets)
 * when that gives bigger tiles. Tiles are sized by whichever of width or height is tighter.
 */
/** Tiles bigger than this (dp) mean a tablet: the donut box and the stats grow too. */
internal const val ROOMY_TILE = 115f

internal fun GameView.layoutStickersPanel(safeL: Float, safeT: Float, safeW: Float, safeH: Float) {
    val spPad    = 10f * u
    val spTitleH = 56f * u
    val spCloseH = 56f * u
    val minGut   = 14f * u
    fun availFor(boxH: Float, statsH: Float) = safeH - 24f * u - (spTitleH + boxH + statsH + spCloseH + spPad * 5f)
    fun sideFor(cols: Int, maxW: Float, availH: Float) =
        min(LayoutMath.fitSide(maxW - spPad * 2f, cols, minGut), LayoutMath.fitSide(availH, 12 / cols, minGut))
    val maxW3 = min(safeW - 24f * u, 560f * u)
    val maxW6 = min(safeW - 24f * u, 960f * u)
    val phoneAvail = availFor(64f * u, 28f * u)
    val wide  = sideFor(6, maxW6, phoneAvail) > sideFor(3, maxW3, phoneAvail)
    val cols  = if (wide) 6 else 3
    val rows  = 12 / cols
    val maxW  = if (wide) maxW6 else maxW3
    val minW  = min(320f * u, maxW)   // still fits the donut box row, the stats line and Close
    // A phone's tiles come out at about 110dp at most. Past ROOMY_TILE (a tablet) the donut box and the
    // stats grow to match the tiles; on a wide panel they share one row, the stats as two lines.
    val roomy = sideFor(cols, maxW, phoneAvail) > ROOMY_TILE * u
    statsBeside = roomy && wide
    val spBoxH   = (if (roomy) 88f else 64f) * u      // the donut box: the goal that never runs out
    val spStatsH = (if (statsBeside) 0f else if (roomy) 36f else 28f) * u
    statsSize    = (if (statsBeside) 26f else if (roomy) 24f else 18f) * u
    val fixedH   = spTitleH + spBoxH + spStatsH + spCloseH + spPad * 5f
    val side  = sideFor(cols, maxW, availFor(spBoxH, spStatsH))
    val gut   = LayoutMath.gutter(side, minGut)
    val gridW = side * cols + gut * (cols - 1)
    // Hugs the grid when height limits the tiles; never narrower than minW
    val spW  = (gridW + spPad * 2f).coerceIn(minW, maxW)
    val spH  = fixedH + side * rows + gut * (rows - 1)
    val spL  = safeL + (safeW - spW) / 2f
    val spT  = safeT + (safeH - spH) / 2f
    stickerPanelRect = RectF(spL, spT, spL + spW, spT + spH)
    val gridX0 = spL + (spW - gridW) / 2f
    val row1Y  = spT + spTitleH
    for (i in 0 until 12) {
        val col = i % cols; val row = i / cols
        val sx = gridX0 + col * (side + gut)
        val sy = row1Y + row * (side + gut)
        stickerRects[i].set(sx, sy, sx + side, sy + side)
    }
    val boxTop = stickerRects[11].bottom + spPad * 2f
    val inL = spL + spPad
    val inR = spL + spW - spPad
    if (statsBeside) {
        val mid = (inL + inR) / 2f
        donutBoxRect.set(inL, boxTop, mid - spPad / 2f, boxTop + spBoxH)
        statsRect.set(mid + spPad / 2f, boxTop, inR, boxTop + spBoxH)
    } else {
        donutBoxRect.set(inL, boxTop, inR, boxTop + spBoxH)
        statsRect.set(inL, boxTop + spBoxH, inR, boxTop + spBoxH + spStatsH)
    }
    // The tabs share the title row (56dp): each 48dp tall, a 4dp margin above and below
    placePanelTabs()
    val closeRowY = stickerPanelRect.bottom - spCloseH - spPad
    stickersCloseRect = RectF(spL + spPad, closeRowY, spL + spW - spPad, stickerPanelRect.bottom - spPad)
}

/** Keeps the system back swipe off the screen edges beside the board, so a chain can start there. */
internal fun GameView.excludeBackSwipe(boardPx: Float) {
    val strip = (24f * u).roundToInt()
    val tall  = min(boardPx, 200f * u)
    val top   = (boardTop + (boardPx - tall) / 2f).roundToInt()
    val bottom = top + tall.roundToInt()
    ViewCompat.setSystemGestureExclusionRects(this, listOf(
        Rect(0, top, strip, bottom),
        Rect(surfaceW - strip, top, surfaceW, bottom)))
}
