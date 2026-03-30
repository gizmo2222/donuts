package com.blackmoondev.donuts

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.blackmoondev.donuts.databinding.ActivityLevelSelectBinding

class LevelSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLevelSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLevelSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Level 1 – Easy: 30 moves, 1 000 target
        binding.btnLevel1.setOnClickListener {
            startGame(moves = 30, target = 1_000)
        }
        // Level 2 – Medium: 25 moves, 2 000 target
        binding.btnLevel2.setOnClickListener {
            startGame(moves = 25, target = 2_000)
        }
        // Level 3 – Hard: 20 moves, 3 500 target
        binding.btnLevel3.setOnClickListener {
            startGame(moves = 20, target = 3_500)
        }
    }

    private fun startGame(moves: Int, target: Int) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra(GameActivity.EXTRA_MOVES,  moves)
            putExtra(GameActivity.EXTRA_TARGET, target)
        }
        startActivity(intent)
    }
}
