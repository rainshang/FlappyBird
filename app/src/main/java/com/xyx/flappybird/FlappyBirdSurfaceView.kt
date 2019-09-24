/*
 * Copyright (c) 2019.
 * Created by Ethan at 8/20/19 1:10 PM
 */

package com.xyx.flappybird

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.RawRes
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.random.Random

internal class FlappyBirdSurfaceView @JvmOverloads constructor(
    context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr)
    , SurfaceHolder.Callback, Runnable {

    var flappyBirdEventListener: FlappyBirdEventListener? = null
    var passedCount: Int = 0 // pipe pairs the bird passed
        private set(value) {
            field = value
            if (field == pipePairCount) {
                gameStatue = GameStatus.WAITING
                mainExecutor.execute { flappyBirdEventListener?.onGameCompleted() }
            }
        }
    var pipePairCount = -1 // total pipe pairs in this game; -1 is infinite
    var gapRatio = 0.25f // pipeGap/viewHeight, (0,1)
        set(value) {
            if (value > 0 && value < 1) {
                field = value
            } else {
                throw IllegalArgumentException("the accepted range is (0, 1)")
            }
        }
    var theme = Theme.SKY
        @Synchronized set(value) {
            field = value
            isUIRunning = false
            Thread.sleep((1000 / FPS).toLong())
            background.recycle()
            land.recycle()
            background.bg =
                BitmapFactory.decodeResource(resources, getProperRatioResourceId(bgResourceName))
            land.bitmap = BitmapFactory.decodeResource(resources, loadingResId)
            restartRefreshUIThread()
        }
    private val loadingResId: Int
        @RawRes get() {
            return when (theme) {
                Theme.SKY -> R.raw.fb_sky_loading
                Theme.DESERT -> R.raw.fb_desert_loading
                Theme.SPACE -> R.raw.fb_space_loading
            }
        }
    private val bgResourceName: String
        get() {
            return when (theme) {
                Theme.SKY -> "fb_sky_bg"
                Theme.DESERT -> "fb_desert_bg"
                Theme.SPACE -> "fb_space_bg"
            }
        }
    var enableKeyboardControl = BuildConfig.DEBUG
        set(value) {
            field = value
            isFocusable = value
        }

    private val FPS = 30
    private var yGravitySpeed = 7
    var xScrollSpeed = 15
        set(value) {
            field = value
            land.scrollSpeed = value
        }

    private var gameStatue: GameStatus

    private val sprites: Array<Sprite>
    private val background: Background
    private val bird: Bird
    private val land: Land
    // 2 pipe-pairs in screen
    private val pipePairs: Array<PipePair>
    private val pipeGap
        get() = (height * gapRatio).toInt()

    private var yBirdNextMove = 0
    private var yBirdTouchRaiseStep = 0

    private var isUIRunning = false
    private lateinit var refreshUIThread: Thread
    private val mainExecutor = object : Executor {
        private val handler = Handler(Looper.getMainLooper())
        override fun execute(command: Runnable) {
            handler.post(command)
        }
    }

    init {
        holder.apply {
            addCallback(this@FlappyBirdSurfaceView)
            setFormat(PixelFormat.TRANSLUCENT)
        }
//        setZOrderOnTop(true)
        gameStatue = GameStatus.WAITING

        background = Background(
            BitmapFactory.decodeResource(
                resources,
                getProperRatioResourceId("fb_sky_bg")
            )
        )
        bird = Bird(
            arrayOf(
                BitmapFactory.decodeResource(resources, R.raw.fb_bird0),
                BitmapFactory.decodeResource(resources, R.raw.fb_bird1),
                BitmapFactory.decodeResource(resources, R.raw.fb_bird2)
            )
        )
        land = Land(BitmapFactory.decodeResource(resources, loadingResId), xScrollSpeed)
        val topPipeBitmap = BitmapFactory.decodeResource(resources, R.raw.fb_pipe_top)
        val bottomPipeBitmap = BitmapFactory.decodeResource(resources, R.raw.fb_pipe_btm)
        pipePairs = arrayOf(
            PipePair(topPipeBitmap, bottomPipeBitmap, 0),
            PipePair(topPipeBitmap, bottomPipeBitmap, 1)
        )

        sprites = arrayOf(
            background,
            bird,
            *pipePairs,
            land
        )
        enableKeyboardControl = BuildConfig.DEBUG
    }

    @RawRes
    private fun getProperRatioResourceId(resourceName: String): Int {
        return if (width == 0 || height == 0) {
            0
        } else {
            val ratio = height * 1f / width
            when {
                isLessThanMid(
                    ratio,
                    4f / 3,
                    16f / 9
                ) -> context.resources.getIdentifier(
                    "${resourceName}_4_3",
                    "raw",
                    context.packageName
                )
                isLessThanMid(
                    ratio,
                    16f / 9,
                    18f / 9
                ) -> context.resources.getIdentifier(
                    "${resourceName}_16_9",
                    "raw",
                    context.packageName
                )
                isLessThanMid(
                    ratio,
                    18f / 9,
                    21f / 9
                ) -> context.resources.getIdentifier(
                    "${resourceName}_18_9",
                    "raw",
                    context.packageName
                )
                else -> context.resources.getIdentifier(
                    "${resourceName}_21_9",
                    "raw",
                    context.packageName
                )
            }
        }
    }

    private fun isLessThanMid(v: Float, l: Float, h: Float) = v <= (h - l) / 2 + l

    fun destroy() {
        sprites.forEach {
            it.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        reset()
    }

    fun start() {
        isEnabled = true
        gameStatue = GameStatus.RUNNING
    }

    fun startFromGameOverPoint() {
        bird.apply {
            y = this@FlappyBirdSurfaceView.height / 2 - height
            pipePairs.minBy { abs(it.x - x) }?.let {
                y = it.y - it.gap / 2 // move bird to the center height of the gap
            }
        }
        yBirdNextMove = 0
        isEnabled = true
        gameStatue = GameStatus.RUNNING
    }

    fun reset() {
        passedCount = 0
        bird.apply {
            width = (this@FlappyBirdSurfaceView.width * Bird.SCALE_WIDTH_TO_VIEW).toInt()
            height = (width * Bird.SCALE_HEIGHT_TO_WIDTH).toInt()
            x = this@FlappyBirdSurfaceView.width / 2 - width
            y = this@FlappyBirdSurfaceView.height / 2 - height
        }
        yBirdNextMove = 0
        yBirdTouchRaiseStep = (bird.height * 0.5).toInt()
        yGravitySpeed = (yBirdTouchRaiseStep * 0.13).toInt()
        land.apply {
            width = this@FlappyBirdSurfaceView.width
            height = this@FlappyBirdSurfaceView.height / 40
            y = this@FlappyBirdSurfaceView.height - height
        }

        val pipeWidth = this@FlappyBirdSurfaceView.width / 5
        val sumPipeHeight = land.y - pipeGap
        PipePair.bottomPipeMaxHeight = (sumPipeHeight * 0.9).toInt()
        PipePair.bottomPipeMinHeight = (sumPipeHeight * 0.1).toInt()
        pipePairs[0].apply {
            x = this@FlappyBirdSurfaceView.width
            y = land.y - PipePair.getRandomBottomPipeHeight()
            width = pipeWidth
            height = land.y
            gap = pipeGap
            index = 0
            isPassed = false
        }
        pipePairs[1].apply {
            x = pipePairs[0].x + this@FlappyBirdSurfaceView.width / 2 + pipeWidth / 2
            y = land.y - PipePair.getRandomBottomPipeHeight()
            width = pipeWidth
            height = land.y
            gap = pipeGap
            index = 1
            isPassed = false
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        reset()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        isUIRunning = false
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        restartRefreshUIThread()
    }

    private fun restartRefreshUIThread() {
        if (background.bg == null) {
            background.bg =
                BitmapFactory.decodeResource(resources, getProperRatioResourceId(bgResourceName))
        }
        if (!isUIRunning) {
            refreshUIThread = Thread(this)
            isUIRunning = true
            refreshUIThread.start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (isEnabled && event?.action == MotionEvent.ACTION_DOWN) {
            doAction()
            true
        } else super.onTouchEvent(event)
    }

    private fun doAction() {
        when (gameStatue) {
            GameStatus.WAITING -> gameStatue = GameStatus.RUNNING
            GameStatus.RUNNING -> yBirdNextMove = -yBirdTouchRaiseStep
            GameStatus.OVER -> {
                gameStatue = GameStatus.WAITING
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return if (enableKeyboardControl
            && (keyCode == KeyEvent.KEYCODE_SPACE
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                    || keyCode == KeyEvent.KEYCODE_PAGE_UP
                    || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                    || keyCode == KeyEvent.KEYCODE_TV_NUMBER_ENTRY
                    )
        ) {
            doAction()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun run() {
        while (isUIRunning) {
            val startTime = System.currentTimeMillis()
            calc()
            holder.lockCanvas()?.let { canvas ->
                sprites.forEach {
                    it.onDraw(canvas, gameStatue)
                }
                holder.unlockCanvasAndPost(canvas)
            }
            val endTime = System.currentTimeMillis()
            if (endTime - startTime < 1000 / FPS) {
                Thread.sleep(1000 / FPS - (endTime - startTime))
            }
        }
    }

    private fun calc() {
        if (gameStatue == GameStatus.RUNNING) {
            // update bird
            yBirdNextMove += yGravitySpeed
            bird.apply {
                y += yBirdNextMove
                if (y < -height) { // Don't fly too high ;)
                    y = -height
                }
                if (y > this@FlappyBirdSurfaceView.height) { // Don't fall down too deep ;)
                    y = this@FlappyBirdSurfaceView.height
                }
            }
            // update pipes
            pipePairs.forEach {
                it.apply {
                    x -= xScrollSpeed
                    if (x <= -width) {
                        x = this@FlappyBirdSurfaceView.width
                        y = land.y - PipePair.getRandomBottomPipeHeight()
                        index += pipePairs.size
                        isPassed = false
                    }
                }
            }
            if (isGameOver()) {
                gameStatue = GameStatus.OVER
                mainExecutor.execute { flappyBirdEventListener?.onGameOver() }
            }
        }
    }

    private fun isGameOver(): Boolean {
        land.run {
            if (bird.y > y - bird.height) {
                bird.y = y - bird.height
                mainExecutor.execute { flappyBirdEventListener?.onBirdFallDown() }
                return true
            }
        }
        pipePairs.forEach {
            it.run {
                if (checkCollision(bird)) {
                    mainExecutor.execute { flappyBirdEventListener?.onBirdHitPipe() }
                    return true
                } else if (bird.x > x + width && !isPassed) {
                    isPassed = true
                    mainExecutor.execute { flappyBirdEventListener?.onBirdPassPipe(index) }
                    passedCount++ // logically, the value should be changed before the above line and then the onGameCompleted() callback should be called here. However, because of the delay of handler.post(), it works properly
                }
            }
        }
        return false
    }

    interface FlappyBirdEventListener {
        fun onGameOver()
        fun onBirdFallDown()
        fun onBirdHitPipe()
        fun onBirdPassPipe(index: Int)
        fun onGameCompleted()
    }

    enum class Theme {
        SKY,
        DESERT,
        SPACE
    }

    private enum class GameStatus {
        WAITING,
        RUNNING,
        OVER
    }

    private abstract class Sprite(
        var x: Int = 0,
        open var y: Int = 0,
        open var width: Int = 0,
        var height: Int = 0
    ) {
        abstract fun onDraw(canvas: Canvas, gameStatus: GameStatus)
        abstract fun recycle()
    }

    private class Background(bg: Bitmap?) : Sprite() {

        var bg: Bitmap? = bg
            set(value) {
                field?.recycle()
                field = value
            }

        override fun onDraw(canvas: Canvas, gameStatus: GameStatus) {
            bg?.let {
                canvas.drawBitmap(it, null, Rect(0, 0, canvas.width, canvas.height), null)
            }
        }

        override fun recycle() {
            bg?.recycle()
        }
    }

    private class Bird(val frames: Array<Bitmap>) : Sprite() {

        companion object {
            const val SCALE_WIDTH_TO_VIEW = 1f / 8
            const val SCALE_HEIGHT_TO_WIDTH = 0.74f
        }

        private var animationFrame = 0

        override fun onDraw(canvas: Canvas, gameStatus: GameStatus) {
            canvas.drawBitmap(
                if (gameStatus != GameStatus.OVER) {
                    animationFrame %= frames.size
                    val tmp = animationFrame
                    animationFrame++
                    frames[tmp % frames.size]
                } else {
                    frames[frames.size / 2]
                },
                null, Rect(x, y, x + width, y + height), null
            )
        }

        override fun recycle() {
            frames.forEach {
                it.recycle()
            }
        }
    }

    private class Land(bitmap: Bitmap, var scrollSpeed: Int) : Sprite() {

        var bitmap: Bitmap = bitmap
            set(value) {
                field.recycle()
                field = value
            }

        override fun onDraw(canvas: Canvas, gameStatus: GameStatus) {
            // Typically calculate should be done outside. However, Land is more like self-driven. So done here
            if (gameStatus != GameStatus.OVER) {
                x -= scrollSpeed
            }
            if (x <= -width) {
                x = 0
            }
            canvas.drawBitmap(bitmap, null, Rect(x, y, x + width, canvas.height), null)
            canvas.drawBitmap(
                bitmap,
                null,
                Rect(x + width, y, x + width + width, canvas.height),
                null
            )
        }

        override fun recycle() {
            bitmap.recycle()
        }
    }

    // y is the y of bottomPipe
    private class PipePair(
        val topPipe: Bitmap,
        val bottomPipe: Bitmap,
        var index: Int = 0,
        var isPassed: Boolean = false
    ) : Sprite() {

        companion object {
            var bottomPipeMaxHeight: Int = 0
            var bottomPipeMinHeight: Int = 0

            fun getRandomBottomPipeHeight() =
                Random.nextInt(bottomPipeMinHeight, bottomPipeMaxHeight)
        }

        private var topVisibleHeight = 0
        private var topPipeRadio = 1f
        private var bottomPipeRadio = 1f

        var gap = 0
            set(value) {
                field = value
                topVisibleHeight = y - value
            }

        override var y: Int
            get() = super.y
            set(value) {
                super.y = value
                topVisibleHeight = value - gap
            }

        override var width: Int
            get() = super.width
            set(value) {
                super.width = value
                if (value != 0) {
                    topPipeRadio = topPipe.width * 1f / value
                    bottomPipeRadio = bottomPipe.width * 1f / value
                }
            }

        fun checkCollision(bird: Bird): Boolean {
            if (x > bird.x + bird.width) return false // bird is on its way
            if (x + width < bird.x) return false // bird passed
            return bird.y !in topVisibleHeight..y - bird.height
        }

        override fun onDraw(canvas: Canvas, gameStatus: GameStatus) {
            canvas.save()

            val topActualHeight = topPipe.height / topPipeRadio
            canvas.translate(0f, -(topActualHeight - topVisibleHeight))
            canvas.drawBitmap(topPipe, null, Rect(x, 0, x + width, topActualHeight.toInt()), null)

            val bottomActualHeight = bottomPipe.height / bottomPipeRadio
            canvas.translate(0f, (topActualHeight - topVisibleHeight) + y)
            canvas.drawBitmap(
                bottomPipe,
                null,
                Rect(x, 0, x + width, bottomActualHeight.toInt()),
                null
            )

            canvas.restore()
        }

        override fun recycle() {
            if (!topPipe.isRecycled) {
                topPipe.recycle()
            }
            if (!bottomPipe.isRecycled) {
                bottomPipe.recycle()
            }
        }
    }

}