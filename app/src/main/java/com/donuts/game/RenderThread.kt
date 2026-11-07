package com.donuts.game

import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.os.SystemClock

// The render thread: locks a canvas, draws one frame under the holder lock, posts it, paces to ~60 fps.

internal class RenderThread(private val view: GameView) : Thread("GameRenderThread") {
    @Volatile var running = true
    private var frames = 0; private var fpsMarkMs = 0L
    private var hwFailures = 0
    private fun lockFrame(): Canvas? {
        // The GPU canvas can fail transiently while the surface is being set up, so only
        // give up on it after a long run of consecutive failures.
        if (hwFailures < 120) {
            try {
                val c = view.holder.lockHardwareCanvas()
                if (c != null) { hwFailures = 0; return c }
            } catch (e: Exception) {
                if (view.debuggable && hwFailures == 0) android.util.Log.d("Donuts", "hardware canvas unavailable: " + e)
            }
            hwFailures++
        }
        return try { view.holder.lockCanvas() } catch (_: Exception) { null }
    }
    private var lockNs = 0L; private var drawNs = 0L; private var postNs = 0L
    override fun run() {
        var next = System.nanoTime()
        while (running) {
            val t0 = System.nanoTime()
            val canvas = lockFrame() // GPU-backed (API 26+), or the software canvas if unavailable
            if (canvas == null) { sleep(16L); continue }
            val t1 = System.nanoTime()
            try { synchronized(view.holder) { view.drawFrame(canvas) } }
            finally {
                val t2 = System.nanoTime()
                // The surface can vanish under us while we hold a canvas; that is not fatal
                try { view.holder.unlockCanvasAndPost(canvas) } catch (_: Exception) { }
                val t3 = System.nanoTime()
                if (view.debuggable) {
                    lockNs += t1 - t0; drawNs += t2 - t1; postNs += t3 - t2
                    frames++
                    val t = SystemClock.elapsedRealtime()
                    if (t - fpsMarkMs >= 1000L) {
                        if (fpsMarkMs > 0L && frames > 0) android.util.Log.d("Donuts",
                            "fps=" + frames + " hw=" + (hwFailures == 0) + " lock=" + (lockNs / frames / 1_000_000) + "ms draw=" + (drawNs / frames / 1_000_000) + "ms post=" + (postNs / frames / 1_000_000) + "ms")
                        frames = 0; lockNs = 0; drawNs = 0; postNs = 0; fpsMarkMs = t
                    }
                }
            }
            // Pace to 60 fps against a nanosecond deadline; resync when a frame ran long
            next += 16_666_667L
            val wait = next - System.nanoTime()
            if (wait > 0) sleep(wait / 1_000_000L, (wait % 1_000_000L).toInt()) else next = System.nanoTime()
        }
        // unlockCanvasAndPost returns when the frame is handed over; the system's RenderThread
        // is still drawing and swapping it. Making and destroying a renderer queues behind that
        // work, so this thread outlives the last swap (a swap on a dead surface can crash).
        if (android.os.Build.VERSION.SDK_INT >= 29) try { HardwareRenderer().destroy() } catch (_: Exception) { }
    }
}
