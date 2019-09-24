package com.xyx.flappybird

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.fragment_flappybird.*

class MainActivity : AppCompatActivity(), FlappyBirdFragment.EventCallback {

    private val fbFragment = FlappyBirdFragment()
    private var scores = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fbFragment.apply {
                eventCallback = this@MainActivity
            }).commit()
    }

    override fun onResume() {
        super.onResume()
        window.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    override fun onGameOver(): String? {
        return scores.toString()
    }

    override fun onBirdPassPipe(index: Int) {
        println("You've passed pipe No.$index")
        scores++
    }

    override fun onGameCompleted(): String? {
        return scores.toString()
    }

    override fun onStartBtnClick() {}

    override fun onTapStart() {}

    override fun onContinueClick() {
        fbFragment.continueGame()
    }

    override fun onStartAgainClick() {
        scores = 0
    }

    override fun onNextLevelClick() {
        scores = 0
        fbFragment.fb_view.gapRatio *= 1.01f
    }
}
