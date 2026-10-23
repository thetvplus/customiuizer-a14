package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.session.PlaybackState
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.R
import kotlin.math.abs
import kotlin.math.sin

/** One capsule shell, one finite motion clock. Width changes never resize glyphs or end caps. */
internal class IslandActivityView(context: Context) : FrameLayout(context) {
    private val shape = IslandShape()
    private val icon = ImageView(context)
    private val wave = IslandWaveView(context)
    private val title = TextView(context).apply {
        setTextColor(Color.WHITE); textSize = 13f; maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        includeFontPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val statusPaint = TextPaint(title.paint)
    private var statusFit: IslandStatusLayout.Fit? = null
    private val left = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.START }
    private val text = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private var controlActions = 0L
    private var controlsEnabled = false
    private var avatar = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var recall = IslandRecallGesture.onCancel()
    private var downY = 0f
    private var downX = 0f
    private var dragging = false
    private var multiTouch = false
    private var dragAxis = 0
    private var cameraY = 0f
    private var cameraDiameter = dp(16).toFloat()
    private var geometryWidth = 1
    private var geometryHeight = 1
    private var maximumWidth = 1f
    private var cameraGap = 0
    private var labelWidth = 0f
    private var textReveal = 1f
    private var layerReveal = 1f
    private var entryCameraAnchor = false
    private var motion: ValueAnimator? = null
    private val breadth = IslandMotion.Spring()
    private val depth = IslandMotion.Spring()
    private val shiftX = IslandMotion.Spring()
    private val shiftY = IslandMotion.Spring()
    private val opacity = IslandMotion.Spring()
    private var disposed = false
    private var leaving = false
    private val iconMask = GradientDrawable().apply { setColor(Color.BLACK) }
    var simplified = false
    var entranceStyle = IslandEntranceStyle.TOP
    var motionScale = 1f
    var windowTop = 0
    var isMedia = false
        private set
    var onDismiss: () -> Unit = {}
    var onOpen: () -> Unit = {}
    var onControl: (Long) -> Unit = {}

    init {
        background = ColorDrawable(Color.BLACK)
        outlineProvider = shape
        clipToOutline = true
        isClickable = true
        isFocusable = true
        isLongClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        icon.background = iconMask
        icon.clipToOutline = true
        left.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        text.addView(title, LinearLayout.LayoutParams(-1, -2))
        addView(left); addView(text); addView(wave)
        setOnClickListener { ModuleHelper.guarded { onOpen() } }
    }

    fun bind(label: String, detail: String?, drawable: Drawable?, playing: Boolean,
        media: Boolean, waveform: Boolean, actions: Long, showControls: Boolean,
        playbackState: Int = PlaybackState.STATE_NONE, avatar: Boolean = false, status: Boolean = false) {
        isMedia = media
        this.avatar = avatar
        controlsEnabled = showControls
        controlActions = actions
        title.ellipsize = if (status) null else TextUtils.TruncateAt.END
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (status) statusFit?.size
            ?: 13f * resources.displayMetrics.scaledDensity else 13f * resources.displayMetrics.scaledDensity)
        // Updates retain the same measured line and visibility; no delayed replacement can
        // publish an old label after a successor or blink during repeated state changes.
        title.text = label
        labelWidth = title.paint.measureText(label)
        text.visibility = if (media) GONE else VISIBLE
        icon.scaleType = if (status) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        icon.clipToOutline = !status
        icon.setImageDrawable(drawable)
        icon.visibility = if (drawable == null) GONE else VISIBLE
        wave.visibility = if (media && waveform) VISIBLE else GONE
        wave.playing = media && playing && waveform && !simplified
        val playbackDescription = if (!media) null else ModuleHelper.getModuleRes(context).getString(when (playbackState) {
            PlaybackState.STATE_PLAYING -> R.string.system_island_playing
            PlaybackState.STATE_PAUSED -> R.string.system_island_paused
            else -> R.string.system_island_connecting
        })
        contentDescription = listOfNotNull(label, playbackDescription, detail).joinToString(", ")
    }

    fun fitsToast(value: String, width: Int, cameraGap: Int, height: Int = dp(IslandSizing.COLLAPSED_HEIGHT_DP)): Boolean =
        statusPaint.measureText(value) <= ((width - cameraGap) / 2 - dp(16)).coerceAtLeast(0) &&
            statusPaint.fontSpacing <= height

    fun textWidth(value: String): Float = statusPaint.measureText(value)

    fun statusWidth(label: String): Float {
        statusPaint.textSize = 13f * resources.displayMetrics.scaledDensity
        return statusPaint.measureText(label)
    }

    fun statusHeight(label: String, width: Int, gap: Int, height: Int): Int? {
        val density = resources.displayMetrics.scaledDensity
        statusFit = IslandStatusLayout.fit(label, statusPaint,
            (width - gap) / 2 - dp(IslandSizing.EDGE_DP), 13f * density, 11f * density, height)
        return statusFit?.let { height }
    }

    fun geometry(width: Int, height: Int, cameraGap: Int, maximumWidth: Int = width) {
        pivotX = width * .5f
        geometryWidth = width; geometryHeight = height
        this.maximumWidth = maximumWidth.coerceAtLeast(width).toFloat()
        this.cameraGap = cameraGap
        val half = ((width - cameraGap) / 2).coerceAtLeast(1)
        icon.layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
        iconMask.cornerRadius = dp(7).toFloat()
        iconMask.shape = if (avatar) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        left.setPadding(dp(IslandSizing.EDGE_DP), 0, 0, 0)
        left.layoutParams = LayoutParams(half, height, Gravity.TOP or Gravity.START)
        text.layoutParams = LayoutParams(half - dp(IslandSizing.EDGE_DP), height, Gravity.TOP or Gravity.END).apply {
            marginEnd = dp(IslandSizing.EDGE_DP)
        }
        wave.layoutParams = LayoutParams(dp(24), dp(20), Gravity.TOP or Gravity.END).apply {
            topMargin = (height - dp(20)) / 2; marginEnd = dp(IslandSizing.EDGE_DP)
        }
        updateContour()
    }

    fun cameraAnchor(centerY: Float, diameter: Float) {
        cameraY = centerY
        cameraDiameter = diameter.coerceAtLeast(dp(8).toFloat())
    }

    private fun compactWidth() = maxOf(cameraDiameter, geometryHeight.toFloat())
        .coerceAtMost(geometryWidth.toFloat())

    fun refreshConfiguration() {
        title.textSize = 13f
        statusPaint.set(title.paint); statusFit = null
    }

    fun prepareEntry() {
        motion?.cancel(); motion = null
        disposed = false; leaving = false
        if (simplified || !ValueAnimator.areAnimatorsEnabled()) resetTransform()
        else {
            alpha = 0f
            entryCameraAnchor = entranceStyle == IslandEntranceStyle.CENTER
            pivotY = if (entryCameraAnchor) cameraY else 0f
            // Establish the final height before anything becomes visible, then grow
            // only the middle. The first shape is a full-height camera-centred circle.
            scaleX = if (entryCameraAnchor) compactWidth() / geometryWidth.coerceAtLeast(1) else 1f
            scaleY = 1f
            translationX = 0f
            translationY = if (entryCameraAnchor) 0f else -topTravel()
            updateContour()
        }
    }

    fun resumeEntry() { leaving = false }

    fun transition(fromWidth: Int, fromHeight: Int, width: Int, height: Int, entering: Boolean,
        secondary: Boolean = false) {
        recall = IslandRecallGesture.onCancel()
        leaving = false
        if (!entering) {
            scaleX *= fromWidth.toFloat() / width.coerceAtLeast(1)
            scaleY *= fromHeight.toFloat() / height.coerceAtLeast(1)
        }
        if (secondary) { layerReveal = 0f; textReveal = 0f }
        animateTo(1f, 1f, 0f, 0f, 1f,
            if (entering) IslandMotion.ENTER else if (secondary) IslandMotion.SECONDARY else IslandMotion.MORPH,
            layered = secondary)
    }

    fun exit(onEnd: () -> Unit) {
        leaving = true
        isPressed = false; cancelLongPress()
        wave.playing = false
        recall = IslandRecallGesture.onCancel()
        if (!isAttachedToWindow || simplified || !ValueAnimator.areAnimatorsEnabled()) {
            alpha = 0f
            updateContour()
            ModuleHelper.guarded { onEnd() }
            return
        }
        entryCameraAnchor = entranceStyle == IslandEntranceStyle.CENTER
        if (entryCameraAnchor) {
            changePivot(geometryWidth * .5f, cameraY)
            animateTo(compactWidth() / geometryWidth.coerceAtLeast(1),
                1f, 0f, 0f, 0f, IslandMotion.EXIT, onEnd)
        } else {
            changePivot(geometryWidth * .5f, 0f)
            animateTo(1f, 1f, 0f, -topTravel(), 0f, IslandMotion.EXIT, onEnd)
        }
    }

    private fun changePivot(x: Float, y: Float) {
        translationX += (pivotX - x) * (1f - scaleX)
        translationY += (pivotY - y) * (1f - scaleY)
        pivotX = x; pivotY = y
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) beginTouch(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && !multiTouch)
            dragging = dragging || abs(event.rawY - downY) > slop || abs(event.rawX - downX) > slop
        return dragging || multiTouch || super.onInterceptTouchEvent(event)
    }

    private fun beginTouch(event: MotionEvent) {
        if (motion == null) { entryCameraAnchor = false; resetTransform(); pivotY = 0f }
        downX = event.rawX; downY = event.rawY
        dragging = false; multiTouch = false; dragAxis = 0
        recall = IslandRecallGesture.onDown(event.getPointerId(0), event.rawX, event.rawY)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (disposed || leaving) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !shape.contains(event.x, event.y)) return false
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            multiTouch = true; recall = IslandRecallGesture.onCancel()
            isPressed = false; cancelLongPress()
        }
        val result = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            recall = IslandRecallGesture.onCancel()
            if (!leaving && motion == null && (dragging || multiTouch) && (scaleX != 1f || scaleY != 1f))
                animateTo(1f, 1f, 0f, 0f, 1f, IslandMotion.REBOUND)
            dragging = false; multiTouch = false; dragAxis = 0
        }
        return result
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (multiTouch) { isPressed = false; cancelLongPress(); return true }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX; val dy = event.rawY - downY
                dragging = dragging || abs(dx) > slop || abs(dy) > slop
                if (dragAxis == 0 && dragging) {
                    if (abs(dx) > abs(dy) * 1.2f) dragAxis = 1
                    else if (abs(dy) > abs(dx) * 1.2f) dragAxis = 2
                }
                if (dragAxis == 2) recall = IslandRecallGesture.onMove(recall,
                    event.getPointerId(0), event.rawX, event.rawY, IslandRecallGesture.configFromSlop(slop))
                if (dragging) {
                    isPressed = false; cancelLongPress()
                    if (motion != null) return true
                    translationX = 0f; translationY = 0f
                    scaleX = if (dragAxis == 1)
                        minOf(1f + .065f * abs(dx) / (dp(56) + abs(dx)), maximumWidth / geometryWidth) else 1f
                    scaleY = if (dragAxis == 2) 1f + .14f * abs(dy) / (dp(48) + abs(dy)) else 1f
                    updateContour()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> if (dragging) {
                val dx = event.rawX - downX; val dy = event.rawY - downY
                if (dragAxis == 2 && dy <= -slop * 2.5f && abs(dy) > abs(dx) && IslandRecallGesture.isTriggered(recall)) onDismiss()
                else if (dragAxis == 1 && isMedia && controlsEnabled &&
                    abs(dx) >= maxOf(dp(32).toFloat(), slop * 3f) && abs(dx) > abs(dy) * 1.2f) {
                    val action = if (dx < 0) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    if (IslandMediaPolicy.canSkip(action, controlActions)) onControl(action)
                }
                isPressed = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> { isPressed = false; cancelLongPress(); return true }
        }
        return super.onTouchEvent(event)
    }

    private fun animateTo(x: Float, y: Float, tx: Float, ty: Float, opacity: Float, duration: Long,
        onEnd: (() -> Unit)? = null, layered: Boolean = false) {
        val carry = motion != null
        motion?.cancel(); motion = null
        if (disposed) return
        if (simplified || !ValueAnimator.areAnimatorsEnabled()) {
            scaleX = x; scaleY = y; translationX = tx; translationY = ty; alpha = opacity
            layerReveal = 1f
            updateContour(); onEnd?.invoke()
            return
        }
        val milliseconds = (duration * motionScale.coerceIn(.65f, 1f)).toLong()
        val seconds = milliseconds / 1000f
        breadth.retarget(geometryWidth * scaleX, geometryWidth * x, seconds, carry)
        depth.retarget(geometryHeight * scaleY, geometryHeight * y, seconds, carry)
        // Braking after a reversal needs the remaining safe space, not the old/new
        // target envelope. Clamping that envelope drops velocity before a real edge.
        val maximumHeight = maxOf(dp(44), geometryHeight) * 1.14f
        shiftX.retarget(translationX, tx, seconds, carry)
        shiftY.retarget(translationY, ty, seconds, carry)
        this.opacity.retarget(alpha, opacity, seconds, carry)
        val layerFrom = layerReveal
        var textAt: Long? = if (contentFits() && text.alpha > 0f) -100L else null
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = milliseconds
            interpolator = LinearInterpolator()
            addUpdateListener { ModuleHelper.guarded {
                if (motion !== it) return@guarded
                val time = it.currentPlayTime / 1000f
                scaleX = breadth.sample(time, compactWidth(), maximumWidth) / geometryWidth.coerceAtLeast(1)
                scaleY = depth.sample(time, cameraDiameter, maximumHeight) / geometryHeight.coerceAtLeast(1)
                translationX = shiftX.sample(time); translationY = shiftY.sample(time)
                if (opacity > 0f) layerReveal = layerFrom + (1f - layerFrom) *
                    ((it.currentPlayTime - if (layered) 60L else 0L) / 160f).coerceIn(0f, 1f)
                alpha = this@IslandActivityView.opacity.sample(time, 0f, 1f)
                if (contentFits()) {
                    val start = textAt ?: it.currentPlayTime.also { began -> textAt = began }
                    textReveal = ((it.currentPlayTime - start).toFloat() /
                        minOf(100L, milliseconds - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
                } else { textAt = null; textReveal = 0f }
                updateContour()
            } }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) = ModuleHelper.guarded {
                    if (motion !== animation) return@guarded
                    motion = null
                    if (!cancelled && !disposed) {
                        breadth.finish(); depth.finish(); shiftX.finish(); shiftY.finish(); this@IslandActivityView.opacity.finish()
                        scaleX = x; scaleY = y; translationX = tx; translationY = ty; alpha = opacity
                        textReveal = 1f
                        layerReveal = 1f
                        updateContour(); onEnd?.invoke()
                    }
                }
            })
            motion = this
            start()
        }
    }

    private fun resetTransform() {
        scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f; alpha = 1f
        textReveal = 1f
        layerReveal = 1f
        updateContour()
    }

    private fun updateContour() {
        // Compensate the drawing transform. The physical shell changes width while text,
        // square artwork and wave bars retain their own aspect ratios, including drags.
        val sx = scaleX.coerceAtLeast(.01f); val sy = scaleY.coerceAtLeast(.01f)
        icon.scaleX = 1f / sx; icon.scaleY = 1f / sy
        text.scaleX = 1f / sx; text.scaleY = 1f / sy
        wave.scaleX = 1f / sx; wave.scaleY = 1f / sy
        // Keep a physical 16dp margin instead of stretching target-layout coordinates.
        // Counter-scaling alone pushes the text into an end cap while lengthening.
        val edge = dp(IslandSizing.EDGE_DP)
        icon.translationX = dp(28) * (1f / sx - 1f)
        wave.translationX = -dp(28) * (1f / sx - 1f)
        val labelRoom = ((geometryWidth - cameraGap) / 2 - edge).coerceAtLeast(0)
        text.translationX = -(edge + labelRoom * .5f) * (1f / sx - 1f)
        val reveal = ((alpha - .12f) / .88f).coerceIn(0f, 1f) * layerReveal
        val iconRoom = geometryWidth * sx >= cameraGap + 2 * (edge + dp(24))
        left.alpha = if (iconRoom && geometryHeight * sy >= dp(24)) reveal else 0f
        text.alpha = if (contentFits()) reveal * textReveal else 0f
        wave.alpha = if (iconRoom && geometryHeight * sy >= dp(20)) reveal else 0f
        shape.resize(geometryWidth, geometryHeight, sx, sy)
        invalidateOutline()
    }

    private fun contentFits(): Boolean {
        val edge = dp(IslandSizing.EDGE_DP)
        val targetRoom = ((geometryWidth - cameraGap) / 2 - edge).coerceAtLeast(0)
        val room = (geometryWidth * scaleX - cameraGap) / 2f - edge
        return room + .01f >= minOf(labelWidth, targetRoom.toFloat())
    }

    private fun topTravel() = (windowTop + geometryHeight + dp(8)).coerceAtLeast(geometryHeight).toFloat()
    override fun onAttachedToWindow() { disposed = false; super.onAttachedToWindow() }
    override fun onDetachedFromWindow() {
        disposed = true
        motion?.cancel(); motion = null
        recall = IslandRecallGesture.onCancel()
        dragging = false; multiTouch = false; dragAxis = 0
        leaving = false; entryCameraAnchor = false
        resetTransform()
        super.onDetachedFromWindow()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}

/** Playback-state animation, deliberately not an audio spectrum. No audio capture or permissions. */
internal class IslandWaveView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var aggregatedVisible = false
    private var scheduled = false
    private var phase = 0f
    var playing = false
        set(value) { field = value; updateRendering(); invalidate() }
    private val frame = object : Runnable {
        override fun run() = ModuleHelper.guarded {
            scheduled = false
            if (!shouldRender()) return@guarded
            phase = (phase + 0.22f) % 6.283185f
            invalidate()
            schedule()
        }
    }

    private fun shouldRender() = playing && aggregatedVisible && isAttachedToWindow &&
        ValueAnimator.areAnimatorsEnabled()
    private fun schedule() {
        if (!scheduled && shouldRender()) { scheduled = true; postOnAnimationDelayed(frame, 48) }
    }
    private fun updateRendering() {
        if (shouldRender()) schedule() else { removeCallbacks(frame); scheduled = false }
    }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        aggregatedVisible = isVisible
        updateRendering()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateRendering() }
    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        scheduled = false
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = width / 5f
        val barWidth = step * 0.48f
        for (index in 0..4) {
            val fraction = if (playing) 0.25f + 0.65f * abs(sin(phase + index * 1.4f)) else 0.22f
            val h = height * fraction
            val x = step * index + (step - barWidth) * 0.5f
            canvas.drawRoundRect(x, (height - h) * 0.5f, x + barWidth, (height + h) * 0.5f,
                barWidth * 0.5f, barWidth * 0.5f, paint)
        }
    }
}
