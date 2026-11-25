package com.donuts.game

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    // Set on the first PLAY tap so a second quick tap cannot open a second game; cleared when we come back
    private var launching = false
    private var main: MainView? = null

    override fun onResume() {
        super.onResume()
        launching = false
        main?.refreshShop()          // the game just banked its donuts (GameActivity.onPause): show any new piece
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = MainView(this) {
            if (!launching) {
                launching = true
                startActivity(Intent(this, GameActivity::class.java))
            }
        }
        main = view
        setContentView(view)
    }
}
