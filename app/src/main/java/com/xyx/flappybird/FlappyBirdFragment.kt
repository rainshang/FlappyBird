/*
 * Copyright (c) 2019.
 * Created by Ethan at 8/26/19 12:14 PM
 */

package com.xyx.flappybird

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_flappybird.*

class FlappyBirdFragment : Fragment(), FlappyBirdSurfaceView.FlappyBirdEventListener {

    interface EventCallback {
        /**
         * return score to display
         */
        fun onGameOver(): String?

        fun onBirdPassPipe(index: Int)
        /**
         * return score to display
         */
        fun onGameCompleted(): String?

        fun onStartBtnClick()
        fun onTapStart()
        fun onContinueClick()
        fun onStartAgainClick()
        fun onNextLevelClick()
    }

    var level = 1
        set(value) {
            val interval = 10
            if (value / interval + 1 != field) { // change theme every 10 levels
                fb_view.theme =
                    FlappyBirdSurfaceView.Theme.values()[(value / interval) % FlappyBirdSurfaceView.Theme.values().size]
            }
            field = value
            fb_view.pipePairCount = value * 5
        }

    var eventCallback: EventCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(R.layout.fragment_flappybird, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        fb_view.apply {
            flappyBirdEventListener = this@FlappyBirdFragment
            gapRatio = 0.4f
            isEnabled = false
        }

        start_layout.apply {
            start_text.apply {
                val s0 = "Play Now\n"
                val s1 = "and receive "
                val s2 = "CHUR Points!"

                val span = SpannableString(s0 + s1 + s2)
                    .apply {
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            0,
                            s0.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            RelativeSizeSpan(3f),
                            0,
                            s0.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )

                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            (s0 + s1).length,
                            (s0 + s1 + s2).length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            RelativeSizeSpan(1.1f),
                            (s0 + s1).length,
                            (s0 + s1 + s2).length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                text = span
            }
            start_btn.setOnClickListener {
                eventCallback?.onStartBtnClick()
                start_layout.visibility = View.GONE
                tap_layout.visibility = View.VISIBLE
            }
            visibility = View.VISIBLE
        }

        tap_layout.apply {
            tap_title.apply {
                val s0 = "CHURPY\n"
                val s1 = "Level"

                val span = SpannableString(s0 + s1)
                    .apply {
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            s0.length,
                            (s0 + s1).length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            RelativeSizeSpan(1.5f),
                            s0.length,
                            (s0 + s1).length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                text = span
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    paint.measureText(s0),
                    0f,
                    Color.parseColor("#46ffa9"),
                    Color.parseColor("#029bbf"),
                    Shader.TileMode.CLAMP
                )
            }
            tap_level.text = level.toString()
            setOnClickListener {
                eventCallback?.onTapStart()
                it.visibility = View.GONE
                fb_view.start()
            }
        }

        tap_layout2.apply {
            setOnClickListener {
                visibility = View.GONE
                fb_view.startFromGameOverPoint()
            }
        }

        oh_no_layout.apply {
            oh_no_title.apply {
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    paint.measureText(text.toString()),
                    0f,
                    Color.parseColor("#46ffa9"),
                    Color.parseColor("#029bbf"),
                    Shader.TileMode.CLAMP
                )
            }
            oh_no_text.apply {
                val s0 = "don’t lose your "
                val s1 = "CHUR Points,\nyou already gained:"

                val span = SpannableString(s0 + s1)
                    .apply {
                        setSpan(
                            StyleSpan(Typeface.BOLD),
                            s0.length,
                            (s0 + s1).length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        setSpan(
                            RelativeSizeSpan(1.2f),
                            s0.length,
                            (s0 + s1).length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                text = span
            }
            continue_btn.setOnClickListener {
                eventCallback?.onContinueClick()
            }
            start_again_btn.setOnClickListener {
                eventCallback?.onStartAgainClick()
                oh_no_layout.visibility = View.GONE
                fb_view.reset()
                fb_view.start()
            }
        }

        congrats_layout.apply {
            congrats_title.apply {
                paint.shader = LinearGradient(
                    0f,
                    0f,
                    paint.measureText(text.toString()),
                    0f,
                    Color.parseColor("#46ffa9"),
                    Color.parseColor("#029bbf"),
                    Shader.TileMode.CLAMP
                )
            }
            next_level_btn.setOnClickListener {
                eventCallback?.onNextLevelClick()
                congrats_layout.visibility = View.GONE
                level++
                fb_view.reset()
                fb_view.start()
            }
        }
    }

    fun continueGame() {
        oh_no_layout.visibility = View.GONE
        tap_layout2.apply {
            visibility = View.VISIBLE
            isEnabled = true
        }
    }

    override fun onGameOver() {
        oh_no_layout.apply {
            oh_no_score.text = fb_view.passedCount.toString()
            eventCallback?.onGameOver()?.let {
                oh_no_score.text = it
            }
            visibility = View.VISIBLE
            fb_view.isEnabled = false
        }
    }

    override fun onBirdFallDown() {
        println("===========>You fell on ground man...")
    }

    override fun onBirdHitPipe() {
        println("===========>You hit pipes man...")
    }

    override fun onBirdPassPipe(index: Int) {
        eventCallback?.onBirdPassPipe(index)
    }

    override fun onGameCompleted() {
        congrats_layout.apply {
            gained_score.text = fb_view.passedCount.toString()
            eventCallback?.onGameCompleted()?.let {
                gained_score.text = it
            }
            visibility = View.VISIBLE
            fb_view.isEnabled = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fb_view.destroy()
    }

}