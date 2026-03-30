package com.blackmoondev.donuts

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MOVES  = "extra_moves"
        const val EXTRA_TARGET = "extra_target"
    }

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val moves  = intent.getIntExtra(EXTRA_MOVES,  30)
        val target = intent.getIntExtra(EXTRA_TARGET, 1_000)

        val board = GameBoard(movesAllowed = moves, targetScore = target)
        gameView  = GameView(this, board)

        // After a game-over reset the board still runs; just let the view handle it.
        gameView.onRestart = { /* board.reset() already called inside GameView */ }

        setContentView(gameView)
    }

    override fun onPause() {
        super.onPause()
        // Thread will keep running but that's fine for a simple game.
        // A production app would pause the RenderThread here.
    }
}
