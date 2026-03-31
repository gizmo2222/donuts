package com.donuts.game

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOVES   = "extra_moves"
        const val EXTRA_TARGET  = "extra_target"
        const val EXTRA_SANDBOX = "extra_sandbox"
    }

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = Prefs(this)
        val board = GameBoard(rows = prefs.gridSize, cols = prefs.gridSize, sandbox = true)
        gameView  = GameView(this, board, prefs)

        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        // Thread will keep running but that's fine for a simple game.
        // A production app would pause the RenderThread here.
    }
}
