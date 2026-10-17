package tv.withaibuild.customiuizer.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.palette.graphics.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.io.File
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AudioVisualizer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var mHeight = 0
    private var mWidth = 0
    private val mDensity: Float = context.resources.displayMetrics.density
    private val mPaint: Paint
    private var mGlowPaint = Paint()
    @Volatile
    private var mVisualizer: Visualizer? = null
    private val visualizerLock = Any()
    private val visualizerMutex = Mutex()
    @Volatile
    private var visualizerGeneration: Long = 0
    @Volatile
    private var viewAttached = false
    @Volatile
    private var detached = false
    internal val isDisposed: Boolean
        get() = detached
    internal var onDisposed: ((AudioVisualizer) -> Unit)? = null
    private var mVisualizerColorAnimator: ObjectAnimator? = null
    private var mVisualizerGlowColorAnimator: ObjectAnimator? = null

    private val mFFTPoints = FloatArray(128)
    private val mBands = floatArrayOf(
        50f, 90f, 130f, 180f, 220f, 260f, 320f, 380f, 430f, 520f, 610f, 700f, 770f, 920f, 1080f,
        1270f, 1480f, 1720f, 2000f, 2320f, 2700f, 3135f, 3700f, 4400f, 5300f, 6400f, 7700f, 9500f,
        10500f, 12000f, 16000f
    )
    private var maxDb = 50f
    private val maxDp = 280
    private val mBandsNum = 31
    private var mFftSize = 0
    private val mBandBinLimits = IntArray(mBandsNum) { Int.MAX_VALUE }
    private val mBandStarts = FloatArray(mBandsNum) { Float.MAX_VALUE }
    private val mBandTargets = FloatArray(mBandsNum) { Float.MAX_VALUE }
    private val mPendingTargets = FloatArray(mBandsNum) { Float.MAX_VALUE }
    private val mFrameLock = Any()

    private var isMusicPlaying = false
    @JvmField
    var isScreenOn = false
    private var isOnKeyguard = false
    private var isExpandedPanel = false
    private var isOnCustomLockScreen = false
    private var mPlaying = false
    @Volatile
    private var mDisplaying = false
    private var mOpaqueColor = Color.TRANSPARENT
    private var mColor = Color.TRANSPARENT

    private var mArt: Bitmap? = null
    private var mProcessedArt: Bitmap? = null

    private val mRainbow = IntArray(mBandsNum)
    private val mRainbowVertical = IntArray(mBandsNum)
    private val mPositions = FloatArray(mBandsNum)
    private val mLinePath = Path()
    private val mHsv = FloatArray(3)
    private val mDashIntervals = FloatArray(2)

    @Volatile
    private var mNewDataPending = false
    private var mFrameStartTime = 0L
    @Volatile
    private var mFrameCallbackScheduled = false
    private val mFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (detached || !mDisplaying) {
                mFrameCallbackScheduled = false
                return
            }
            if (mNewDataPending) {
                applyNewData(frameTimeNanos)
            }
            val elapsedMs = (frameTimeNanos - mFrameStartTime) / 1_000_000f
            val fraction = (elapsedMs / animDur).coerceIn(0f, 1f)
            var needsInvalidate = false
            for (i in 0 until mBandsNum) {
                val start = mBandStarts[i]
                val target = mBandTargets[i]
                if (start == Float.MAX_VALUE || target == Float.MAX_VALUE) continue
                val interp = if (target < start) decel.getInterpolation(fraction) else accel.getInterpolation(fraction)
                val newVal = start + (target - start) * interp
                if (mFFTPoints[i * 4 + 3] != newVal) {
                    mFFTPoints[i * 4 + 3] = newVal
                    needsInvalidate = true
                }
            }
            if (needsInvalidate) postInvalidateOnAnimation()
            if (!mDisplaying) {
                mFrameCallbackScheduled = false
                return
            }
            if (fraction >= 1f && !needsInvalidate) {
                // Every target has been reached and nothing new arrived: park the scheduler
                // instead of waking up at the refresh rate on a static picture. The capture
                // thread publishes mNewDataPending before reading mFrameCallbackScheduled and
                // this thread clears mFrameCallbackScheduled before reading mNewDataPending,
                // so one of the two always observes the other and no wake-up is lost.
                mFrameCallbackScheduled = false
                if (!mNewDataPending) return
                mFrameCallbackScheduled = true
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun applyNewData(frameTimeNanos: Long) {
        synchronized(mFrameLock) {
            if (mNewDataPending) {
                System.arraycopy(mPendingTargets, 0, mBandTargets, 0, mBandsNum)
                mNewDataPending = false
            }
        }
        for (i in 0 until mBandsNum) {
            mBandStarts[i] = mFFTPoints[i * 4 + 3]
        }
        mFrameStartTime = frameTimeNanos
    }

    private fun startFrameScheduler() {
        if (mFrameCallbackScheduled) return
        mFrameCallbackScheduled = true
        Choreographer.getInstance().postFrameCallback(mFrameCallback)
    }

    private fun stopFrameScheduler() {
        mFrameCallbackScheduled = false
        Choreographer.getInstance().removeFrameCallback(mFrameCallback)
    }

    @JvmField
    var showOnCustom = false
    private var animDur = 0
    private var transparency = 0
    private lateinit var colorMode: ColorMode
    private lateinit var barStyle: BarStyle
    private lateinit var renderType: RenderType
    private var glowLevel = 0
    private var customColor = 0
    private var randomizeInterval = 0
    @JvmField
    var showInDrawer = false
    @JvmField
    var showWithControllerOnly = false

    private val accel = AccelerateInterpolator()
    private val decel = DecelerateInterpolator()

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + ModuleHelper.coroutineFailureHandler)
    private var randomizeColorJob: Job? = null
    private var paletteGenerationJob: Job? = null

    enum class BarStyle {
        DUMMY, SOLID, SOLID_ROUNDED, DASHED, CIRCLES, LINE
    }

    enum class ColorMode {
        DUMMY, MATCH, STATIC, RAINBOW_H, RAINBOW_V, DYNAMIC
    }

    enum class RenderType {
        AUTO, LINES, PATH
    }

    private val preferenceObserver = AudioVisualizerPreferenceObserver(this)

    private fun handlePreferenceChanged(key: String?) = ModuleHelper.guarded {
        if (detached) return@guarded
        when (key) {
            "system_visualizer_animdur" ->
                animDur = MainModule.mPrefs.getInt("system_visualizer_animdur", 65)
            "system_visualizer_transp" -> {
                transparency = (255f - 255f * MainModule.mPrefs.getInt("system_visualizer_transp", 40) / 100f).roundToInt()
                setColor(mOpaqueColor)
                updateRainbowColors()
            }
            "system_visualizer_color" -> {
                colorMode = ColorMode.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_color", 1)]
                updateBarStyle()
                updateColorMode()
            }
            "system_visualizer_style" -> {
                barStyle = BarStyle.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_style", 1)]
                updateBarStyle()
            }
            "system_visualizer_render" -> {
                renderType = RenderType.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_render", 0)]
                updateBarStyle()
            }
            "system_visualizer_glowlevel" -> {
                glowLevel = MainModule.mPrefs.getInt("system_visualizer_glowlevel", 50)
                updateGlowPaint()
            }
            "system_visualizer_colorval" -> {
                customColor = MainModule.mPrefs.getInt("system_visualizer_colorval", Color.WHITE)
                setColor(customColor)
            }
            "system_visualizer_dyntime" -> {
                randomizeInterval = MainModule.mPrefs.getInt("system_visualizer_dyntime", 10) * 1000
                randomizeColorJob?.cancel()
                randomizeColorJob = viewScope.launch { runRandomizeColor() }
            }
            "system_visualizer_drawer" ->
                showInDrawer = MainModule.mPrefs.getBoolean("system_visualizer_drawer", false)
            "system_visualizer_controller" ->
                showWithControllerOnly = MainModule.mPrefs.getBoolean("system_visualizer_controller", false)
        }
    }

    private class AudioVisualizerPreferenceObserver(owner: AudioVisualizer) :
        ModuleHelper.PreferenceObserver {
        private val ownerRef = java.lang.ref.WeakReference(owner)

        override fun onChange(key: String?) {
            ownerRef.get()?.handlePreferenceChanged(key)
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        val res: Resources = context.resources
        mHeight = if (res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            res.displayMetrics.heightPixels
        } else {
            res.displayMetrics.widthPixels
        }
        mWidth = if (res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            res.displayMetrics.widthPixels
        } else {
            res.displayMetrics.heightPixels
        }

        mPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.MITER
            color = mColor
        }

        animDur = MainModule.mPrefs.getInt("system_visualizer_animdur", 65)
        for (i in 0 until mBandsNum) {
            mBandStarts[i] = mHeight.toFloat()
            mBandTargets[i] = mHeight.toFloat()
            mPendingTargets[i] = mHeight.toFloat()
        }

        for (i in 0 until mBandsNum) {
            mPositions[i] = (i + 1) / mBandsNum.toFloat()
        }

        showOnCustom = MainModule.mPrefs.getBoolean("system_visualizer_custom")
        transparency = (255f - 255f * MainModule.mPrefs.getInt("system_visualizer_transp", 40) / 100f).roundToInt()
        colorMode = ColorMode.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_color", 1)]
        barStyle = BarStyle.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_style", 1)]
        renderType = RenderType.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_render", 0)]
        glowLevel = MainModule.mPrefs.getInt("system_visualizer_glowlevel", 50)
        customColor = MainModule.mPrefs.getInt("system_visualizer_colorval", Color.WHITE)
        randomizeInterval = MainModule.mPrefs.getInt("system_visualizer_dyntime", 10) * 1000
        showInDrawer = MainModule.mPrefs.getBoolean("system_visualizer_drawer")
        showWithControllerOnly = MainModule.mPrefs.getBoolean("system_visualizer_controller")

        updateBarStyle()
        updateGlowPaint()
        updateRainbowColors()

        ModuleHelper.observePreferenceChange(preferenceObserver, this)
    }

    private val mVisualizerListener = object : Visualizer.OnDataCaptureListener {
        private var real: Byte = 0
        private var imaginary: Byte = 0
        private var dbValue = 0
        private var magnitude = 0f

        /** Capture-thread scratch buffer, published to [mPendingTargets] with a single lock. */
        private val scratchTargets = FloatArray(mBandsNum)

        override fun onWaveFormDataCapture(visualizer: Visualizer, bytes: ByteArray, samplingRate: Int) {}

        override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) {
            try {
                if (detached || !mDisplaying) return

                if (mFftSize != fft.size) {
                    computeBandBinLimits(fft.size)
                }

                // A silent frame needs no separate pass: every bin is zero, so the band loop
                // produces magnitude 0 and dbValue 0 on its own.
                var band = 0
                var i = 1
                val maxHeight = min(0.85f * maxDp * mDensity, mHeight / 2.0f)

                while (band < mBandsNum && i < mFftSize / 2) {
                    magnitude = 0f

                    while (i < mBandBinLimits[band]) {
                        real = fft[i * 2]
                        imaginary = fft[i * 2 + 1]
                        magnitude = max(magnitude, (real * real + imaginary * imaginary).toFloat())
                        i++
                    }

                    dbValue = if (magnitude > 0) (10 * log10(magnitude)).toInt() else 0
                    maxDb = max(maxDb, dbValue.toFloat())
                    scratchTargets[band] = mFFTPoints[band * 4 + 1] - maxHeight * dbValue / maxDb

                    band++
                }

                if (band > 0) {
                    synchronized(mFrameLock) {
                        System.arraycopy(scratchTargets, 0, mPendingTargets, 0, band)
                        mNewDataPending = true
                    }
                }

                // Make sure the frame scheduler is running. Choreographer must be used
                // from the main thread, so post to the view's handler.
                if (!mFrameCallbackScheduled) {
                    post { startFrameScheduler() }
                }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
        }
    }

    private fun computeBandBinLimits(fftSize: Int) {
        mFftSize = fftSize
        val half = fftSize / 2
        for (band in 0 until mBandsNum) {
            // FFT data contains fftSize/2 complex bins, so bin limits must be computed
            // against the usable half. +1 converts the inclusive frequency bound to the
            // exclusive bin index used by the inner while loop, matching the original <= logic.
            val limit = ((mBands[band] * half / 22050f).toInt() + 1).coerceAtMost(half)
            mBandBinLimits[band] = if (limit > 0) limit else 1
        }
    }

    private fun getRandomColor(): Int {
        mHsv[0] = (Math.random() * 360f).toFloat()
        mHsv[1] = 0.5f + (Math.random() * 0.5f).toFloat()
        mHsv[2] = 0.75f + (Math.random() * 0.25f).toFloat()
        return Color.HSVToColor(mHsv)
    }

    private fun updateGlowPaint() {
        mGlowPaint = Paint(mPaint)
        if (glowLevel == 0) return
        val scale = glowLevel / 100f
        mGlowPaint.pathEffect = null
        mGlowPaint.maskFilter = BlurMaskFilter(15 * mDensity * (1.25f + 0.25f * scale), BlurMaskFilter.Blur.NORMAL)
        mGlowPaint.alpha = min(transparency, 180)
        mGlowPaint.strokeWidth = (0.5f + 1.25f * scale) * mPaint.strokeWidth * if (barStyle == BarStyle.LINE) 4f else if (colorMode == ColorMode.RAINBOW_H) 1.15f else 1.3f
        if (barStyle == BarStyle.SOLID || barStyle == BarStyle.DASHED || mGlowPaint.strokeCap == Paint.Cap.ROUND) {
            mGlowPaint.strokeCap = Paint.Cap.SQUARE
        }
    }

    private val onPaletteGenerated: (Palette?) -> Unit = { palette ->
        try {
            var color = palette?.let {
                var c = Color.TRANSPARENT
                c = it.getLightVibrantColor(c)
                if (c == Color.TRANSPARENT) c = it.getVibrantColor(c)
                if (c == Color.TRANSPARENT) c = it.getDarkVibrantColor(c)
                c
            } ?: Color.TRANSPARENT
            setColor(color)
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    fun setBitmap() {
        try {
            if (mProcessedArt === mArt && mArt != null) return
            mProcessedArt = mArt
            val art = mProcessedArt
            if (art != null) {
                paletteGenerationJob?.cancel()
                paletteGenerationJob = viewScope.launch {
                    val palette = withContext(Dispatchers.Default) { Palette.from(art).generate() }
                    if (!isActive) return@launch
                    onPaletteGenerated(palette)
                }
            } else {
                setColor(Color.TRANSPARENT)
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    fun setColor(color: Int) {
        var c = color
        if (c == Color.TRANSPARENT) c = Color.WHITE
        val newColor = Color.argb(transparency, Color.red(c), Color.green(c), Color.blue(c))
        if (mColor == newColor) return
        mColor = newColor
        mOpaqueColor = c

        val viz = mVisualizer
        if (viz != null) {
            mVisualizerColorAnimator?.cancel()
            mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color", mPaint.color, mColor).apply {
                startDelay = (600 * animDur / 65f).roundToInt().toLong()
                duration = (1200 * animDur / 65f).roundToInt().toLong()
                start()
            }

            if (glowLevel > 0) {
                mVisualizerGlowColorAnimator?.cancel()
                mVisualizerGlowColorAnimator = ObjectAnimator.ofArgb(mGlowPaint, "color", mGlowPaint.color, mColor).apply {
                    startDelay = (600 * animDur / 65f).roundToInt().toLong()
                    duration = (1200 * animDur / 65f).roundToInt().toLong()
                    start()
                }
            }
        } else {
            mPaint.color = mColor
            if (glowLevel > 0) mGlowPaint.color = mColor
        }
    }

    private fun updateColorMode() {
        if (!isMusicPlaying) return
        when (colorMode) {
            ColorMode.MATCH -> setBitmap()
            ColorMode.DYNAMIC -> setColor(getRandomColor())
            ColorMode.STATIC -> setColor(customColor)
            else -> setColor(Color.WHITE)
        }
    }

    private fun updateRainbowColors() {
        val jump = 300f / mBandsNum
        mHsv[1] = 1.0f
        mHsv[2] = 1.0f
        for (i in 0 until mRainbow.size) {
            mHsv[0] = jump * i
            mRainbow[i] = Color.HSVToColor(transparency, mHsv)
        }

        for (i in 0 until mRainbowVertical.size) {
            var h = 140f + jump * i
            if (h > 360) h -= 360f
            mHsv[0] = h
            mRainbowVertical[i] = Color.HSVToColor(transparency, mHsv)
        }
    }

    private fun updateBarStyle() {
        when (colorMode) {
            ColorMode.RAINBOW_H -> mPaint.shader = LinearGradient(0f, 0f, mWidth.toFloat(), 0f, mRainbow, mPositions, Shader.TileMode.MIRROR)
            ColorMode.RAINBOW_V -> {
                val maxHeight = min(0.85f * maxDp * mDensity, mHeight / 2.0f)
                mPaint.shader = LinearGradient(0f, mHeight.toFloat(), 0f, mHeight - maxHeight, mRainbowVertical, mPositions, Shader.TileMode.CLAMP)
            }
            else -> mPaint.shader = null
        }

        when (barStyle) {
            BarStyle.SOLID -> {
                mPaint.pathEffect = null
                mPaint.strokeCap = Paint.Cap.BUTT
            }
            BarStyle.SOLID_ROUNDED -> {
                mPaint.pathEffect = null
                mPaint.strokeCap = Paint.Cap.ROUND
            }
            BarStyle.DASHED -> {
                mDashIntervals[0] = 4 * mDensity
                mDashIntervals[1] = 2 * mDensity
                mPaint.pathEffect = DashPathEffect(mDashIntervals, 0f)
                mPaint.strokeCap = Paint.Cap.BUTT
            }
            BarStyle.CIRCLES -> {
                mDashIntervals[0] = 1.0f
                mDashIntervals[1] = mPaint.strokeWidth + mDensity
                mPaint.pathEffect = DashPathEffect(mDashIntervals, 0f)
                mPaint.strokeCap = Paint.Cap.ROUND
            }
            BarStyle.LINE -> {
                mPaint.pathEffect = CornerPathEffect(18 * mDensity)
                mPaint.strokeCap = Paint.Cap.ROUND
                mPaint.strokeWidth = 3 * mDensity
            }
            else -> {}
        }

        updateGlowPaint()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewAttached = true
        checkStateChanged()
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dispose()
    }

    internal fun dispose() {
        if (detached) return
        detached = true
        viewAttached = false
        mDisplaying = false
        ModuleHelper.unregisterPreferenceObserver(this)
        stopFrameScheduler()
        val visualizer = synchronized(visualizerLock) {
            val current = mVisualizer
            mVisualizer = null
            current
        }
        viewScope.launch {
            releaseVisualizer(visualizer)
            viewScope.cancel()
        }
        resetBandsToBaseline()
        animate().cancel()
        mVisualizerColorAnimator?.cancel()
        mVisualizerGlowColorAnimator?.cancel()
        randomizeColorJob?.cancel()
        paletteGenerationJob?.cancel()
        mArt = null
        mProcessedArt = null
        val callback = onDisposed
        onDisposed = null
        callback?.invoke(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val barUnit = w / mBandsNum.toFloat()
        val barWidth = barUnit * 0.80f
        mHeight = h
        mWidth = w
        mPaint.strokeWidth = barWidth
        updateBarStyle()

        for (i in 0 until mBandsNum) {
            mFFTPoints[i * 4] = i * barUnit + (barWidth / 2)
            mFFTPoints[i * 4 + 1] = h.toFloat()
            mFFTPoints[i * 4 + 2] = mFFTPoints[i * 4]
            mFFTPoints[i * 4 + 3] = h.toFloat()
            mBandStarts[i] = h.toFloat()
            mBandTargets[i] = h.toFloat()
            mPendingTargets[i] = h.toFloat()
        }
    }

    override fun hasOverlappingRendering(): Boolean = mDisplaying

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!mDisplaying) return
        try {
            if (mVisualizer?.enabled != true) return
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return
        }

        if (barStyle == BarStyle.LINE) {
            mLinePath.reset()
            mLinePath.moveTo(0f, mFFTPoints[3])
            for (i in 1 until mBandsNum) {
                mLinePath.lineTo(if (i == mBandsNum - 1) mWidth.toFloat() else mFFTPoints[i * 4 + 2], mFFTPoints[i * 4 + 3])
            }
            if (glowLevel > 0) {
                canvas.drawPath(mLinePath, mGlowPaint)
            }
            canvas.drawPath(mLinePath, mPaint)
            return
        }

        val drawAsLines = when (renderType) {
            RenderType.LINES -> true
            RenderType.PATH -> false
            else -> glowLevel == 0
        }

        if (drawAsLines) {
            if (glowLevel > 0) {
                canvas.drawLines(mFFTPoints, mGlowPaint)
            }
            canvas.drawLines(mFFTPoints, mPaint)
        } else {
            mLinePath.reset()
            for (i in 0 until mBandsNum) {
                mLinePath.moveTo(mFFTPoints[i * 4], mFFTPoints[i * 4 + 1])
                mLinePath.lineTo(mFFTPoints[i * 4], mFFTPoints[i * 4 + 3])
            }
            if (glowLevel > 0) {
                canvas.drawPath(mLinePath, mGlowPaint)
            }
            canvas.drawPath(mLinePath, mPaint)
        }
    }

    fun setPlaying(playing: Boolean) {
        if (mPlaying != playing) {
            mPlaying = playing
            checkStateChanged()
        }
    }

    private suspend fun linkVisualizer(generation: Long) = withContext(Dispatchers.IO) {
        val candidate = createAndEnableVisualizer(resolveMediaAudioSessionIds()) ?: return@withContext

        withContext(NonCancellable) {
            visualizerMutex.withLock {
                val (previous, shouldReleaseCandidate) = synchronized(visualizerLock) {
                    if (detached || !mDisplaying || generation != visualizerGeneration) {
                        null to true
                    } else {
                        val current = mVisualizer
                        mVisualizer = candidate
                        current to false
                    }
                }

                if (shouldReleaseCandidate) {
                    releaseVisualizer(candidate)
                } else if (previous !== candidate) {
                    releaseVisualizer(previous)
                }
            }
        }
    }

    private fun resolveMediaAudioSessionIds(): List<Int> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
            ?: return listOf(0)
        try {
            val configs = am.activePlaybackConfigurations
            if (configs.isNullOrEmpty()) return listOf(0)

            val result = ArrayList<Int>(configs.size + 1)
            for (config in configs) {
                if (!isConfigActive(config)) continue
                val usage = try {
                    config.audioAttributes.usage
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    continue
                }
                if (usage == AudioAttributes.USAGE_MEDIA || usage == AudioAttributes.USAGE_GAME) {
                    val sessionId = getConfigSessionId(config)
                    if (sessionId > 0) result.add(sessionId)
                }
            }
            if (result.isEmpty()) result.add(0)
            return result
        } catch (t: Throwable) {
            XposedHelpers.log("AudioVisualizer", t)
            return listOf(0)
        }
    }

    private fun createAndEnableVisualizer(sessionIds: List<Int>): Visualizer? {
        val sessionsToTry = sessionIds.toMutableList()
        if (!sessionsToTry.contains(0)) sessionsToTry.add(0)

        for (session in sessionsToTry) {
            var visualizer: Visualizer? = null
            try {
                visualizer = Visualizer(session)
                visualizer.enabled = false
                visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
                visualizer.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                visualizer.setDataCaptureListener(mVisualizerListener, Visualizer.getMaxCaptureRate(), false, true)
                visualizer.enabled = true
                return visualizer
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                XposedHelpers.log("AudioVisualizer create session=$session failed: ${t.message}")
                try {
                    visualizer?.release()
                } catch (inner: Throwable) {
                    FatalErrors.rethrowIfFatal(inner)
                }
            }
        }
        return null
    }

    private fun isConfigActive(config: AudioPlaybackConfiguration): Boolean = try {
        config.javaClass.getDeclaredMethod("isActive").apply { isAccessible = true }.invoke(config) as Boolean
    } catch (t: Throwable) {
        FatalErrors.rethrowIfFatal(t)
        true
    }

    private fun getConfigSessionId(config: AudioPlaybackConfiguration): Int = try {
        config.javaClass.getDeclaredMethod("getSessionId").apply { isAccessible = true }.invoke(config) as Int
    } catch (t: Throwable) {
        FatalErrors.rethrowIfFatal(t)
        0
    }

    private fun resetBandsToBaseline() {
        val baseline = mHeight.toFloat()
        for (i in 0 until mBandsNum) {
            mBandStarts[i] = baseline
            mBandTargets[i] = baseline
            mPendingTargets[i] = baseline
            mFFTPoints[i * 4 + 1] = baseline
            mFFTPoints[i * 4 + 3] = baseline
        }
        postInvalidateOnAnimation()
    }

    private suspend fun releaseVisualizer(visualizer: Visualizer?) = withContext(Dispatchers.IO + NonCancellable) {
        if (visualizer == null) return@withContext
        try {
            visualizer.enabled = false
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
        try {
            visualizer.release()
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    private suspend fun runRandomizeColor() {
        while (coroutineContext.isActive && colorMode == ColorMode.DYNAMIC) {
            setColor(getRandomColor())
            delay(randomizeInterval.toLong())
        }
    }

    fun updateViewState(isPlaying: Boolean, isKeyguard: Boolean, isExpanded: Boolean) {
        isMusicPlaying = isPlaying
        isOnKeyguard = isKeyguard
        isExpandedPanel = showInDrawer && !isOnKeyguard && isExpanded
        isOnCustomLockScreen = File("/data/system/theme/lockscreen").exists()
        updatePlaying()
    }

    fun updateScreenOn(isOn: Boolean) {
        isScreenOn = isOn
        updatePlaying()
    }

    fun updateMusicArt(art: Bitmap?) {
        mArt = art
        updateColorMode()
    }

    fun updatePlaying() {
        setPlaying(isScreenOn && isMusicPlaying && ((isOnKeyguard && (!isOnCustomLockScreen || showOnCustom)) || isExpandedPanel))
    }

    private fun checkStateChanged() {
        if (detached) return
        val shouldDisplay = viewAttached && mPlaying
        if (shouldDisplay) {
            if (!mDisplaying) {
                val generation = ++visualizerGeneration
                mDisplaying = true
                startFrameScheduler()
                resetBandsToBaseline()
                viewScope.launch { linkVisualizer(generation) }
                randomizeColorJob?.cancel()
                randomizeColorJob = viewScope.launch { runRandomizeColor() }
                animate().alpha(1.0f).withEndAction(null).setDuration((800 * animDur / 65f).roundToInt().toLong())
            }
        } else {
            if (mDisplaying) {
                mDisplaying = false
                stopFrameScheduler()
                randomizeColorJob?.cancel()
                resetBandsToBaseline()
                val visualizer = synchronized(visualizerLock) {
                    val current = mVisualizer
                    mVisualizer = null
                    current
                }
                viewScope.launch { releaseVisualizer(visualizer) }
                animate().alpha(0.0f).withEndAction(null).setDuration((600 * animDur / 65f).roundToInt().toLong())
            }
        }
    }
}
