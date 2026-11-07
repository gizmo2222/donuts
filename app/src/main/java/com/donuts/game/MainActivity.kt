package com.donuts.game

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    // Set on the first PLAY tap so a second quick tap cannot open a second game; cleared when we come back
    private var launching = false

    override fun onResume() {
        super.onResume()
        launching = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(MainView(this) {
            if (!launching) {
                launching = true
                startActivity(Intent(this, GameActivity::class.java))
            }
        })
    }
}
