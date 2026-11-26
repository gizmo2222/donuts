package com.donuts.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

// Drawing shared by the game (GameView) and the home screen (MainView). Each caller passes its
// own paint, path and rect, so nothing is allocated here and the two views keep their own state.

/** Rewinds [path] to a closed regular polygon of [sides] corners on radius [r], the first at [startDeg]. */
fun polygonPath(path: Path, cx: Float, cy: Float, r: Float, sides: Int, startDeg: Float): Path {
    path.rewind()
    for (i in 0 until sides) {
        val a = Math.toRadians((startDeg + i * (360f / sides)).toDouble())
        val x = cx + (r * cos(a)).toFloat(); val y = cy + (r * sin(a)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * A 3D-style highlight: a white sheen clipped to an upper-left oval, then a bright specular dot.
 * Call after the main body and before any hole or overlay. Leaves [paint] at full alpha.
 */
fun drawSheen(canvas: Canvas, paint: Paint, path: Path, rect: RectF, cx: Float, cy: Float, r: Float,
              alpha: Int, sheenAlpha: Float, specAlpha: Float) {
    path.rewind()
    path.addOval(rect.apply { set(cx - r * 0.88f, cy - r * 1.05f, cx + r * 0.52f, cy + r * 0.05f) },
        Path.Direction.CW)
    canvas.save()
    canvas.clipPath(path)
    paint.color = Color.argb((alpha * sheenAlpha).toInt(), 255, 255, 255)
    canvas.drawRect(cx - r * 2f, cy - r * 2f, cx + r * 2f, cy + r * 2f, paint)
    canvas.restore()
    paint.color = Color.argb((alpha * specAlpha).toInt(), 255, 255, 255)
    canvas.drawCircle(cx - r * 0.30f, cy - r * 0.44f, r * 0.16f, paint)
    paint.alpha = 255
}
