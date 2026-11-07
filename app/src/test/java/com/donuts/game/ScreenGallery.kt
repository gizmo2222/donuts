package com.donuts.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders every screen state to PNG files for the UI review, at phone and tablet sizes.
 * It is skipped in the normal suite; tools\gallery.ps1 runs it with the system property
 * `donuts.gallery` set to the output folder (via the init script tools\gallery.gradle).
 * No window insets are drawn here; the emulator shots cover those.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenGallery {

    private val outDir: String? = System.getProperty("donuts.gallery")?.takeIf { it.isNotBlank() }

    private class Size(val name: String, val w: Int, val h: Int, val qualifiers: String, val allStates: Boolean)

    private val sizes = listOf(
        Size("phone", 1080, 2400, "w411dp-h914dp-port-420dpi", true),
        Size("phone-land", 2400, 1080, "w914dp-h411dp-land-420dpi", false),
        Size("small", 720, 1280, "w360dp-h640dp-port-xhdpi", false),
        Size("tablet", 2560, 1600, "w1280dp-h800dp-land-xhdpi", false),
        Size("tablet-port", 1600, 2560, "w800dp-h1280dp-port-xhdpi", false),
    )

    private fun save(bmp: Bitmap, size: Size, state: String) {
        val dir = File(outDir!!); dir.mkdirs()
        FileOutputStream(File(dir, "${size.name}__$state.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun clearPrefs() {
        TestViews.app().getSharedPreferences("donuts", 0).edit().clear().commit()
    }

    private fun game(size: Size, grid: Int = 6, tutorialSeen: Boolean = true, setup: (Prefs) -> Unit = {}): GameView {
        clearPrefs()
        val p = Prefs(TestViews.app())
        p.soundEnabled = false; p.hapticEnabled = false
        p.stickersAnnounced = 0xFFF          // no sticker fly unless a state asks for one
        setup(p)
        return TestViews.gameView(w = size.w, h = size.h, grid = grid, seed = 3, tutorialSeen = tutorialSeen)
    }

    private fun shoot(v: GameView, size: Size, state: String, warm: Int = 3, step: Long = 16L, act: (Canvas) -> Unit = {}) {
        val bmp = Bitmap.createBitmap(v.surfaceW, v.surfaceH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        TestViews.frames(v, c, 2)
        act(c)
        TestViews.frames(v, c, warm, step)
        save(bmp, size, state)
    }

    /** A playable path of [n] cells: row 0 left to right, then row 1 back, all set to one type. */
    private fun drag(v: GameView, n: Int, release: Boolean) {
        val cols = v.board.cols
        val path = (0 until cols).map { 0 to it } + (cols - 1 downTo 0).map { 1 to it }
        val cells = path.take(n)
        cells.forEach { (r, c) -> v.board.grid[r][c] = v.board.grid[r][c].copy(type = DonutType.STRAWBERRY) }
        val pts = cells.map { (r, c) -> TestViews.cellCenter(v, r, c) }
        TestViews.touch(v, MotionEvent.ACTION_DOWN, pts[0].first, pts[0].second)
        pts.drop(1).forEach { TestViews.touch(v, MotionEvent.ACTION_MOVE, it.first, it.second) }
        if (release) TestViews.touch(v, MotionEvent.ACTION_UP, pts.last().first, pts.last().second)
    }

    private fun home(size: Size) {
        val mv = MainView(TestViews.app()) {}
        mv.measure(View.MeasureSpec.makeMeasureSpec(size.w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(size.h, View.MeasureSpec.EXACTLY))
        mv.layout(0, 0, size.w, size.h)
        val bmp = Bitmap.createBitmap(size.w, size.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        repeat(5) { TestViews.advance(100); mv.draw(c) }
        save(bmp, size, "01-home")
    }

    private fun render(size: Size) {
        RuntimeEnvironment.setQualifiers(size.qualifiers)
        home(size)
        shoot(game(size), size, "02-board")
        shoot(game(size, tutorialSeen = false), size, "03-tutorial", warm = 40, step = 40L)
        shoot(game(size).also { drag(it, 6, release = false) }, size, "04-drag")
        run {
            val v = game(size)
            shoot(v, size, "05-celebration", warm = 20, step = 50L) { v.celebrate(TestViews.now(), "WOW!") }
        }
        run {
            val v = game(size) { it.lifetimeDonuts = 75; it.bestChainLength = 8; it.highScore6x6 = 70; it.goldenPopped = true }
            shoot(v, size, "06-stickers", warm = 30, step = 20L) { v.stickersOpen = true }
        }
        run {
            val v = game(size)
            shoot(v, size, "07-settings", warm = 30, step = 20L) { v.settingsOpen = true }
        }
        if (!size.allStates) return
        run {
            val v = game(size)
            shoot(v, size, "08-settings-confirm", warm = 30, step = 20L) {
                v.settingsOpen = true; v.newGameConfirm.tap(TestViews.now())
            }
        }
        run {
            val v = game(size) { it.stickersAnnounced = 0 }
            shoot(v, size, "09-stickers-empty", warm = 30, step = 20L) { v.stickersOpen = true }
        }
        run {
            val v = game(size) { it.lifetimeDonuts = 125 }
            shoot(v, size, "10-stickers-box-full", warm = 30, step = 20L) { v.stickersOpen = true }
        }
        shoot(game(size, grid = 8), size, "11-board-8x8")
        run {
            val v = game(size)
            shoot(v, size, "12-popping", warm = 4) { drag(v, 3, release = true) }
        }
        run {
            val v = game(size)
            shoot(v, size, "13-bomb", warm = 10, step = 40L) { drag(v, 5, release = true) }
        }
        run {
            val v = game(size)
            shoot(v, size, "14-colour-burst", warm = 12, step = 40L) { drag(v, 9, release = true) }
        }
        run {
            val v = game(size)
            val b = v.board
            for ((r, c) in listOf(0 to 0, 2 to 3, 4 to 5)) b.grid[r][c] = GameCell(b.grid[r][c].type, r, c, isGolden = true)
            shoot(v, size, "15-golden")
        }
        run {
            val v = game(size) { it.stickersAnnounced = 0; it.bestChainLength = 4 }
            shoot(v, size, "16-sticker-fly", warm = 15, step = 40L)
        }
        run {
            val v = game(size)
            shoot(v, size, "17-no-moves", warm = 20, step = 50L) { v.noMovesWarningMs = TestViews.now() }
        }
        run {
            val v = game(size)
            shoot(v, size, "18-short-chain", warm = 6) {
                val a = TestViews.cellCenter(v, 0, 0)
                TestViews.touch(v, MotionEvent.ACTION_DOWN, a.first, a.second)
                TestViews.touch(v, MotionEvent.ACTION_UP, a.first, a.second)
            }
        }
    }

    @Test fun phone() { assumeTrue(outDir != null); render(sizes[0]) }
    @Test fun phoneLandscape() { assumeTrue(outDir != null); render(sizes[1]) }
    @Test fun smallPhone() { assumeTrue(outDir != null); render(sizes[2]) }
    @Test fun tablet() { assumeTrue(outDir != null); render(sizes[3]) }
    @Test fun tabletPortrait() { assumeTrue(outDir != null); render(sizes[4]) }
}
