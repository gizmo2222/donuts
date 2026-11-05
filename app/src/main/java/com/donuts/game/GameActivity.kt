package com.donuts.game

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
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

        // A restart (rotation, window resize, dark mode, or the system reclaiming the app)
        // carries on with the same board and is still the same visit.
        if (savedInstanceState == null) {
            prefs.sessionCount = prefs.sessionCount + 1   // one "play" per visit to the game screen
        } else {
            gameView.restoreState(savedInstanceState)
        }

        setContentView(gameView)

        // Back closes an open panel first; only a second Back leaves the game
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!gameView.onBackPressed()) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        gameView.saveState(outState)
    }

    // The render thread is started in GameView.surfaceCreated and stopped in
    // surfaceDestroyed, which fires when the activity is backgrounded, so there is
    // nothing extra to pause here.
}
