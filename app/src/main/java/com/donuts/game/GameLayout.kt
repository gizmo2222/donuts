package com.donuts.game

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    val boardPx = min(safeW - margin * 2f, safeH - margin * 2f - (btnH + baseGap) - baseCtrH - baseStrip)
    cellSize  = boardPx / board.cols
    boardLeft = safeL + (safeW - boardPx) / 2f

    // Spare height (tall phones) goes to the chrome: bigger counter, taller strip, a wider gap
    val spare     = safeH - (btnH + baseGap + boardPx + baseCtrH + baseStrip)
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
    settingsSc = 1f
    val ph = placeSettings(pw, safeL, safeT, safeW, safeH)
    if (ph > availH) {
        settingsSc = availH / ph
        placeSettings(pw, safeL, safeT, safeW, safeH)
    }
}

internal fun GameView.placeSettings(pw: Float, safeL: Float, safeT: Float, safeW: Float, safeH: Float): Float {
    val k      = u * settingsSc
    val pad    = 16f * k
    val titleH = 84f * k
    val rowH   = 56f * k
    val secGap = 40f * k      // room for a section label above each row
    val closeH = 56f * k
    val verH   = 24f * k      // the small version line under Done
    val ph = titleH + rowH + (secGap + rowH) * 4f + secGap + closeH + pad + verH
    val pl = safeL + (safeW - pw) / 2f
    val pt = safeT + (safeH - ph) / 2f
    panelRect = RectF(pl, pt, pl + pw, pt + ph)

    fun row(rects: Array<RectF>, top: Float) {
        val n  = rects.size
        val bw = (pw - pad * (n + 1)) / n
        for (i in 0 until n) {
            val x = pl + pad + i * (bw + pad)
            rects[i].set(x, top, x + bw, top + rowH)
        }
    }
    val sTop = pt + titleH
    val bTop = sTop + rowH + secGap
    val hTop = bTop + rowH + secGap
    val gTop = hTop + rowH + secGap
    val rTop = gTop + rowH + secGap
    row(soundRects, sTop); row(buzzRects, bTop); row(hintRects, hTop); row(gridRects, gTop)
    val rw = (pw - pad * 3f) / 2f
    settingsResetRect    = RectF(pl + pad, rTop, pl + pad + rw, rTop + rowH)
    settingsStickersRect = RectF(pl + pad * 2f + rw, rTop, pl + pw - pad, rTop + rowH)
    settingsCloseRect = RectF(pl + pad, pt + ph - verH - closeH - pad, pl + pw - pad, pt + ph - verH - pad)
    return ph
}

/** Sticker panel: 4 rows of 3 tiles sized by whichever of width or height is tighter. */
internal fun GameView.layoutStickersPanel(safeL: Float, safeT: Float, safeW: Float, safeH: Float) {
    val maxW     = min(safeW - 24f * u, 560f * u)
    val minW     = min(320f * u, maxW)   // still fits the donut box row, the stats line and Close
    val spPad    = 10f * u
    val spTitleH = 56f * u
    val spBoxH   = 64f * u      // the donut box: the goal that never runs out
    val spStatsH = 28f * u
    val spCloseH = 56f * u
    val minGut   = 14f * u
    val sideFromW = LayoutMath.fitSide(maxW - spPad * 2f, 3, minGut)
    val fixedH    = spTitleH + spBoxH + spStatsH + spCloseH + spPad * 5f
    val sideFromH = LayoutMath.fitSide(safeH - 24f * u - fixedH, 4, minGut)
    val side = min(sideFromW, sideFromH)
    val gut  = LayoutMath.gutter(side, minGut)
    val gridW = side * 3f + gut * 2f
    // Hugs the grid when height limits the tiles; never narrower than minW
    val spW  = (gridW + spPad * 2f).coerceIn(minW, maxW)
    val spH  = fixedH + side * 4f + gut * 3f
    val spL  = safeL + (safeW - spW) / 2f
    val spT  = safeT + (safeH - spH) / 2f
    stickerPanelRect = RectF(spL, spT, spL + spW, spT + spH)
    val gridX0 = spL + (spW - gridW) / 2f
    val row1Y  = spT + spTitleH
    for (i in 0 until 12) {
        val col = i % 3; val row = i / 3
        val sx = gridX0 + col * (side + gut)
        val sy = row1Y + row * (side + gut)
        stickerRects[i].set(sx, sy, sx + side, sy + side)
    }
    val boxTop = stickerRects[11].bottom + spPad * 2f
    donutBoxRect.set(spL + spPad, boxTop, spL + spW - spPad, boxTop + spBoxH)
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
