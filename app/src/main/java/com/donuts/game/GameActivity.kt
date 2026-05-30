package com.donuts.game

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class GameActivity : AppCompatActivity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = Prefs(this)
        val board = GameBoard(rows = prefs.gridSize, cols = prefs.gridSize)
        gameView  = GameView(this, board, prefs)

        setContentView(gameView)
    }

    // The render thread is started in GameView.surfaceCreated and stopped in
    // surfaceDestroyed, which fires when the activity is backgrounded — so there is
    // nothing extra to pause here.
}
