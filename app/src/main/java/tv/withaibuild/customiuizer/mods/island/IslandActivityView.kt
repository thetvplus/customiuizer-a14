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
import android.view.GestureDetector
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
    private var controlPlayback = PlaybackState.STATE_NONE
    private var baseTextSize = title.textSize
    private var iconSize = dp(24)
    private var waveHeight = dp(20)
    private var controlsEnabled = false
    private var avatar = false
    private var slop = ViewConfiguration.get(context).scaledTouchSlop
    private var recall = IslandRecallGesture.onCancel()
    private var downY = 0f
    private var downX = 0f
    private var dragging = false
    private var multiTouch = false
    private var dragAxis = 0
    private var longConsumed = false
    private var doubleCandidate = false
    private var inputCancelled = false
    private var tapsCancelled = false
    private var gestureIdentity: Any? = null
    private var musicActions = IslandMusicActions()
    private val tapListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!doubleEnabled() && !longConsumed && !inputCancelled) performClick()
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (doubleEnabled() && !longConsumed && !inputCancelled && !leaving && !disposed) performClick()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleCandidate = doubleEnabled()
            return doubleCandidate
        }
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP && doubleCandidate && !inputCancelled && !dragging && !multiTouch)
                submit(IslandMusicGesture.DOUBLE_TAP)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (!inputCancelled && !dragging && !multiTouch && !leaving && !disposed && isMedia &&
                musicActions.resolve(IslandMusicGesture.LONG_PRESS, controlsEnabled) != 1) {
                longConsumed = true
                submit(IslandMusicGesture.LONG_PRESS)
            }
        }
    }
    private val taps = GestureDetector(context, tapListener)
    private var cameraY = 0f
    private var cameraDiameter = dp(16).toFloat()
    private var geometryWidth = 1
    private var geometryHeight = 1
    private var maximumWidth = 1f
    private var cameraGap = 0
    private var alignment = 1
    var touchMargin = 0
    private var labelWidth = 0f
    private var textReveal = 1f
    private var layerReveal = 1f
    private class Content(val label: String, val drawable: Drawable?, val media: Boolean,
        val waveform: Boolean, val avatar: Boolean, val status: Boolean) {
        fun matches(label: String, drawable: Drawable?, media: Boolean, waveform: Boolean,
            avatar: Boolean, status: Boolean) = this.label == label && this.drawable === drawable &&
            this.media == media && this.waveform == waveform && this.avatar == avatar && this.status == status
    }
    private var displayedContent: Content? = null
    private var pendingContent: Content? = null
    private var contentFadeRemaining = 0L
    private var entryCameraAnchor = false
    private var motion: ValueAnimator? = null
    private var mediaReturnProgress = 1f
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
    var onGesture: (IslandMusicGesture) -> Unit = {}

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
        taps.setIsLongpressEnabled(false)
        taps.setOnDoubleTapListener(null)
    }

    private fun doubleEnabled() = isMedia && musicActions.doubleTap != 1

    fun configureGestures(identity: Any?, actions: IslandMusicActions, trackSwipes: Boolean) {
        if (gestureIdentity != identity || musicActions != actions || controlsEnabled != trackSwipes) cancelInputs()
        gestureIdentity = identity
        musicActions = actions
        controlsEnabled = trackSwipes
        taps.setIsLongpressEnabled(isMedia && actions.longPress != 1)
        taps.setOnDoubleTapListener(if (doubleEnabled()) tapListener else null)
    }

    /** Cancels the platform detector's pending single tap/long press as well as a drag. */
    fun cancelInputs(cancelDrag: Boolean = true) {
        inputCancelled = true
        if (!tapsCancelled) {
            val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            try { taps.onTouchEvent(cancel) } finally { cancel.recycle() }
            tapsCancelled = true
        }
        isPressed = false
        cancelLongPress()
        if (cancelDrag) recall = IslandRecallGesture.onCancel()
        doubleCandidate = false
    }

    private fun submit(gesture: IslandMusicGesture) {
        val action = musicActions.resolve(gesture, controlsEnabled)
        // Snapshot admission avoids advertising unavailable controls; the owner rechecks
        // the actual session immediately before execution using the same resolver.
        if (action in 87..88 && !IslandMediaPolicy.canSkip(
                if (action == 87) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS, controlActions)) return
        if ((action == 85 || action == IslandMusicActions.PLAY || action == IslandMusicActions.PAUSE) &&
            IslandTransportAction.resolve(action, controlPlayback, controlActions) == null) return
        if (!disposed && !leaving && isMedia && musicActions.resolve(gesture, controlsEnabled) != 1)
            ModuleHelper.guarded { onGesture(gesture) }
    }

    fun bind(label: String, detail: String?, drawable: Drawable?, playing: Boolean,
        media: Boolean, waveform: Boolean, actions: Long, showControls: Boolean,
        playbackState: Int = PlaybackState.STATE_NONE, avatar: Boolean = false, status: Boolean = false): Boolean {
        if (isMedia != media) cancelInputs()
        isMedia = media
        taps.setIsLongpressEnabled(media && musicActions.longPress != 1)
        taps.setOnDoubleTapListener(if (doubleEnabled()) tapListener else null)
        controlsEnabled = showControls
        controlActions = actions
        controlPlayback = playbackState
        // Action identity and accessibility follow the owner immediately. Only the visual
        // handoff is deferred, with one latest value and the shell's existing finite clock.
        val changed = (pendingContent ?: displayedContent)?.matches(label, drawable, media, waveform, avatar, status) != true
        if (changed) {
            val content = Content(label, drawable, media, waveform, avatar, status)
            if (isAttachedToWindow && !leaving && !simplified && ValueAnimator.areAnimatorsEnabled() &&
                displayedContent != null && alpha > .01f && layerReveal > .01f) {
                if (pendingContent == null) contentFadeRemaining = 60L
                pendingContent = content
            } else {
                pendingContent = null; contentFadeRemaining = 0L
                applyContent(content)
            }
        } else if (pendingContent == null && status) {
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, statusFit?.size ?: baseTextSize)
        }
        wave.playing = media && playing && waveform && !simplified
        val playbackDescription = if (!media) null else ModuleHelper.getModuleRes(context).getString(when (playbackState) {
            PlaybackState.STATE_PLAYING -> R.string.system_island_playing
            PlaybackState.STATE_PAUSED -> R.string.system_island_paused
            else -> R.string.system_island_connecting
        })
        contentDescription = listOfNotNull(label, playbackDescription, detail).joinToString(", ")
        return changed
    }

    private fun applyContent(content: Content) {
        displayedContent = content
        avatar = content.avatar
        title.ellipsize = if (content.status) null else TextUtils.TruncateAt.END
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (content.status) statusFit?.size
            ?: baseTextSize else baseTextSize)
        title.text = content.label
        labelWidth = title.paint.measureText(content.label)
        text.visibility = if (content.media) GONE else VISIBLE
        icon.scaleType = if (content.status) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
        icon.clipToOutline = !content.status
        icon.setImageDrawable(content.drawable)
        icon.visibility = if (content.drawable == null) GONE else VISIBLE
        wave.visibility = if (content.media && content.waveform) VISIBLE else GONE
        iconMask.shape = if (content.avatar) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
    }

    private fun commitContent() {
        val latest = pendingContent ?: return
        pendingContent = null; contentFadeRemaining = 0L
        applyContent(latest)
    }

    fun spectrum(values: FloatArray?) { wave.spectrum(values) }

    fun fitsToast(value: String, width: Int, cameraGap: Int, height: Int = dp(IslandSizing.COLLAPSED_HEIGHT_DP)): Boolean =
        statusPaint.measureText(value) <= captionRoom(width, cameraGap) &&
            statusPaint.fontSpacing <= height

    fun textWidth(value: String): Float = statusPaint.measureText(value)

    fun statusWidth(label: String): Float {
        statusPaint.textSize = baseTextSize
        return statusPaint.measureText(label)
    }

    fun statusHeight(label: String, width: Int, gap: Int, height: Int): Int? {
        val density = resources.displayMetrics.scaledDensity
        statusFit = IslandStatusLayout.fit(label, statusPaint,
            captionRoom(width, gap), baseTextSize, 11f * density, height)
        return statusFit?.let { height }
    }

    fun geometry(width: Int, height: Int, cameraGap: Int, maximumWidth: Int = width, alignment: Int = 1) {
        if (this.alignment != alignment) cancelInputs()
        this.alignment = alignment
        pivotX = horizontalPivot(width)
        geometryWidth = width; geometryHeight = height
        this.maximumWidth = maximumWidth.coerceAtLeast(width).toFloat()
        this.cameraGap = cameraGap
        iconSize = iconSizeForHeight(height)
        val half = (if (cameraGap > 0) (width - cameraGap) / 2
            else dp(IslandSizing.EDGE_DP) + iconSize + dp(8)).coerceAtLeast(1)
        val iconParams = icon.layoutParams
        if (iconParams.width != iconSize || iconParams.height != iconSize) {
            iconParams.width = iconSize; iconParams.height = iconSize
            icon.layoutParams = iconParams
        }
        val scale = iconSize.toFloat() / dp(24).coerceAtLeast(1)
        iconMask.cornerRadius = dp(7) * scale
        iconMask.shape = if (avatar) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        left.setPadding(dp(IslandSizing.EDGE_DP), 0, 0, 0)
        layoutChild(left, half, height, Gravity.TOP or Gravity.START)
        layoutChild(text, captionRoom(width, cameraGap), height, Gravity.TOP or Gravity.END,
            end = dp(IslandSizing.EDGE_DP))
        waveHeight = kotlin.math.round(dp(20) * scale).toInt().coerceAtLeast(1)
        layoutChild(wave, iconSize, waveHeight, Gravity.TOP or Gravity.END,
            top = (height - waveHeight) / 2, end = dp(IslandSizing.EDGE_DP))
        updateContour()
    }

    private fun layoutChild(child: View, width: Int, height: Int, gravity: Int, top: Int = 0, end: Int = 0) {
        val params = child.layoutParams as LayoutParams
        if (params.width == width && params.height == height && params.gravity == gravity &&
            params.topMargin == top && params.marginEnd == end) return
        params.width = width; params.height = height; params.gravity = gravity
        params.topMargin = top; params.marginEnd = end
        child.layoutParams = params
    }

    fun cameraAnchor(centerY: Float, diameter: Float) {
        cameraY = centerY
        cameraDiameter = diameter.coerceAtLeast(dp(8).toFloat())
    }

    private fun compactWidth() = maxOf(cameraDiameter, geometryHeight.toFloat())
        .coerceAtMost(geometryWidth.toFloat())

    fun refreshConfiguration() {
        slop = ViewConfiguration.get(context).scaledTouchSlop
        title.textSize = 13f
        baseTextSize = title.textSize
        statusPaint.set(title.paint); statusFit = null
    }

    fun contentHeight(heightDp: Int) {
        val height = heightDp.coerceIn(IslandSizing.HEIGHT_MIN_DP, IslandSizing.HEIGHT_MAX_DP)
        val size = (13f + (height - 40) * .1f) * resources.displayMetrics.scaledDensity
        if (abs(title.textSize - size) > .001f) title.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        baseTextSize = size
        iconSize = iconSizeForHeight(dp(height))
        if (statusPaint.textSize != size) { statusPaint.textSize = size; statusFit = null }
    }

    private fun iconSizeForHeight(height: Int) =
        (dp(24) * (1f + (height.toFloat() / dp(40).coerceAtLeast(1) - 1f) / 3f)).toInt()
            .coerceIn(dp(16).coerceAtLeast(1), maxOf(dp(16), height - dp(8)))

    fun prepareEntry(mediaReturn: Boolean = false) {
        motion?.cancel(); motion = null
        commitContent()
        disposed = false; leaving = false
        mediaReturnProgress = if (mediaReturn && isMedia && icon.drawable != null &&
            !simplified && ValueAnimator.areAnimatorsEnabled()) 0f else 1f
        if (simplified || !ValueAnimator.areAnimatorsEnabled()) resetTransform()
        else {
            alpha = 0f
            entryCameraAnchor = mediaReturnProgress == 0f || entranceStyle == IslandEntranceStyle.CENTER
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
        secondary: Boolean = false, visualLeft: Float? = null, visualTop: Float? = null) {
        recall = IslandRecallGesture.onCancel()
        leaving = false
        if (!entering) {
            scaleX *= fromWidth.toFloat() / width.coerceAtLeast(1)
            scaleY *= fromHeight.toFloat() / height.coerceAtLeast(1)
            // The window already has its final bounds; preserve the last visible origin
            // before retargeting the same physical springs, including pivot changes.
            visualLeft?.let { translationX = it - pivotX * (1f - scaleX) }
            visualTop?.let { translationY = it - pivotY * (1f - scaleY) }
        }
        animateTo(1f, 1f, 0f, 0f, 1f,
            if (mediaReturnProgress == 0f) IslandMotion.MEDIA_RETURN else if (entering) IslandMotion.ENTER
                else if (secondary) IslandMotion.SECONDARY else IslandMotion.MORPH,
            returning = mediaReturnProgress == 0f)
    }

    fun exit(onEnd: () -> Unit) {
        pendingContent = null; contentFadeRemaining = 0L
        cancelMediaReturn()
        leaving = true
        cancelInputs()
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
            changePivot(horizontalPivot(geometryWidth), cameraY)
            animateTo(compactWidth() / geometryWidth.coerceAtLeast(1),
                1f, 0f, 0f, 0f, IslandMotion.EXIT, onEnd)
        } else {
            changePivot(horizontalPivot(geometryWidth), 0f)
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
        longConsumed = false; inputCancelled = false; tapsCancelled = false
        recall = IslandRecallGesture.onDown(event.getPointerId(0), event.rawX, event.rawY)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (disposed || leaving) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !shape.contains(event.x, event.y) &&
            (touchMargin <= 0 || event.x < -touchMargin / scaleX.coerceAtLeast(.01f) || event.x > width + touchMargin / scaleX.coerceAtLeast(.01f) ||
                event.y < -touchMargin / scaleY.coerceAtLeast(.01f) || event.y > height + touchMargin / scaleY.coerceAtLeast(.01f))) return false
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            multiTouch = true; recall = IslandRecallGesture.onCancel()
            cancelInputs()
        }
        val result = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (longConsumed) cancelInputs()
            recall = IslandRecallGesture.onCancel()
            if (!leaving && motion == null && (dragging || multiTouch) && (scaleX != 1f || scaleY != 1f))
                animateTo(1f, 1f, 0f, 0f, 1f, IslandMotion.REBOUND)
            dragging = false; multiTouch = false; dragAxis = 0
        }
        return result
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (multiTouch || inputCancelled) return true
        if (longConsumed) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val dx = event.rawX - downX; val dy = event.rawY - downY
                dragging = dragging || abs(dx) > slop || abs(dy) > slop
                if (dragAxis == 0 && dragging) {
                    if (abs(dx) > abs(dy) * 1.2f) dragAxis = 1
                    else if (abs(dy) > abs(dx) * 1.2f) dragAxis = 2
                }
                val config = IslandRecallGesture.configFromSlop(slop, geometryHeight)
                if (dragAxis == 2) recall = if (event.actionMasked == MotionEvent.ACTION_UP)
                    IslandRecallGesture.onUp(recall, event.getPointerId(0), event.rawX, event.rawY,
                        config, event.eventTime - event.downTime)
                    else IslandRecallGesture.onMove(recall, event.getPointerId(0), event.rawX, event.rawY, config)
                if (dragging) {
                    cancelInputs(cancelDrag = false)
                    // The detector is cancelled, but this pointer still owns the locked drag.
                    inputCancelled = false
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (dragAxis == 2 && dy < -slop && abs(dx) <= -dy * config.maxHorizontalRatio &&
                            IslandRecallGesture.isTriggered(recall)) ModuleHelper.guarded { onDismiss() }
                        else if (dragAxis == 1 && isMedia &&
                            abs(dx) >= maxOf(dp(32).toFloat(), slop * 3f) && abs(dx) > abs(dy) * 1.2f)
                            submit(if (dx < 0) IslandMusicGesture.LEFT else IslandMusicGesture.RIGHT)
                        else if (dragAxis == 2 && dy >= maxOf(dp(32).toFloat(), slop * 3f) && abs(dy) > abs(dx) * 1.2f)
                            submit(IslandMusicGesture.DOWN)
                        isPressed = false
                        return true
                    }
                    if (motion != null) return true
                    translationX = 0f; translationY = 0f
                    scaleX = if (dragAxis == 1)
                        minOf(1f + .065f * abs(dx) / (dp(56) + abs(dx)), maximumWidth / geometryWidth) else 1f
                    scaleY = if (dragAxis == 2) 1f + .14f * abs(dy) / (dp(48) + abs(dy)) else 1f
                    updateContour()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> { cancelInputs(); return true }
        }
        taps.onTouchEvent(event)
        return true
    }

    private fun animateTo(x: Float, y: Float, tx: Float, ty: Float, opacity: Float, duration: Long,
        onEnd: (() -> Unit)? = null, returning: Boolean = false) {
        if (!returning) cancelMediaReturn()
        val carry = motion != null
        motion?.cancel(); motion = null
        if (disposed) return
        if (simplified || !ValueAnimator.areAnimatorsEnabled()) {
            commitContent()
            cancelMediaReturn()
            scaleX = x; scaleY = y; translationX = tx; translationY = ty; alpha = opacity
            textReveal = 1f
            layerReveal = 1f
            updateContour(); onEnd?.invoke()
            return
        }
        val milliseconds = (duration * motionScale.coerceIn(.65f, 1f)).toLong()
        val seconds = milliseconds / 1000f
        breadth.retarget(geometryWidth * scaleX, geometryWidth * x, seconds, carry)
        depth.retarget(geometryHeight * scaleY, geometryHeight * y, seconds, carry)
        // Side placements cap their target at its new width. Keep the already visible
        // breadth admissible while shrinking instead of clamping it on the first frame.
        val maximumBreadth = maxOf(maximumWidth, geometryWidth * scaleX)
        // Braking after a reversal needs the remaining safe space, not the old/new
        // target envelope. Clamping that envelope drops velocity before a real edge.
        val maximumHeight = maxOf(maxOf(dp(44), geometryHeight) * 1.14f, geometryHeight * scaleY)
        shiftX.retarget(translationX, tx, seconds, carry)
        shiftY.retarget(translationY, ty, seconds, carry)
        this.opacity.retarget(alpha, opacity, seconds, carry)
        var layerFrom = layerReveal
        val fadeOut = if (pendingContent != null) contentFadeRemaining else 0L
        var revealAt = 0L
        var textAt: Long? = if (contentFits() && text.alpha > 0f) -100L else null
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = milliseconds
            interpolator = LinearInterpolator()
            addUpdateListener { ModuleHelper.guarded {
                if (motion !== it) return@guarded
                val time = it.currentPlayTime / 1000f
                scaleX = breadth.sample(time, compactWidth(), maximumBreadth) / geometryWidth.coerceAtLeast(1)
                scaleY = depth.sample(time, cameraDiameter, maximumHeight) / geometryHeight.coerceAtLeast(1)
                if (returning && mediaReturnProgress < 1f) {
                    mediaReturnProgress = (it.currentPlayTime.toFloat() / milliseconds.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    scaleY += .045f * IslandMotion.mediaReturnCatch(mediaReturnProgress)
                    (parent as? View)?.invalidate()
                }
                translationX = shiftX.sample(time); translationY = shiftY.sample(time)
                if (opacity > 0f) {
                    if (pendingContent != null) {
                        contentFadeRemaining = (fadeOut - it.currentPlayTime).coerceAtLeast(0L)
                        layerReveal = layerFrom * (contentFadeRemaining.toFloat() / fadeOut.coerceAtLeast(1L))
                        if (contentFadeRemaining == 0L) {
                            commitContent()
                            layerFrom = 0f; revealAt = fadeOut; textAt = null
                        }
                    }
                    if (pendingContent == null) layerReveal = layerFrom + (1f - layerFrom) *
                        ((it.currentPlayTime - revealAt) / 160f).coerceIn(0f, 1f)
                }
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
                        commitContent()
                        breadth.finish(); depth.finish(); shiftX.finish(); shiftY.finish(); this@IslandActivityView.opacity.finish()
                        scaleX = x; scaleY = y; translationX = tx; translationY = ty; alpha = opacity
                        textReveal = 1f
                        layerReveal = 1f
                        cancelMediaReturn()
                        updateContour(); onEnd?.invoke()
                    }
                }
            })
            motion = this
            start()
        }
    }

    private fun resetTransform() {
        commitContent()
        cancelMediaReturn()
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
        icon.translationX = (edge + iconSize * .5f) * (1f / sx - 1f)
        wave.translationX = -dp(28) * (1f / sx - 1f)
        val labelRoom = captionRoom(geometryWidth, cameraGap)
        text.translationX = -(edge + labelRoom * .5f) * (1f / sx - 1f)
        val reveal = ((alpha - .12f) / .88f).coerceIn(0f, 1f) * layerReveal
        val iconRoom = geometryWidth * sx >= cameraGap + 2 * (edge + iconSize)
        left.alpha = if (iconRoom && geometryHeight * sy >= iconSize) reveal else 0f
        if (mediaReturnProgress < 1f) left.alpha *= ((mediaReturnProgress - .72f) / .28f).coerceIn(0f, 1f)
        text.alpha = if (contentFits()) reveal * textReveal else 0f
        wave.alpha = if (iconRoom && geometryHeight * sy >= waveHeight) reveal else 0f
        shape.resize(geometryWidth, geometryHeight, sx, sy)
        invalidateOutline()
    }

    fun cancelMediaReturn() {
        if (mediaReturnProgress >= 1f) return
        mediaReturnProgress = 1f
        (parent as? View)?.invalidate()
        updateContour()
    }

    private fun contentFits(): Boolean {
        val edge = dp(IslandSizing.EDGE_DP)
        val targetRoom = captionRoom(geometryWidth, cameraGap)
        val room = if (cameraGap > 0) (geometryWidth * scaleX - cameraGap) / 2f - edge
            else geometryWidth * scaleX - 2 * edge - iconSize - dp(8)
        return room + .01f >= minOf(labelWidth, targetRoom.toFloat())
    }

    private fun captionRoom(width: Int, gap: Int) = (if (gap > 0)
        (width - gap) / 2 - dp(IslandSizing.EDGE_DP)
        else width - 2 * dp(IslandSizing.EDGE_DP) - iconSize - dp(8)).coerceAtLeast(0)
    private fun horizontalPivot(width: Int) = when (alignment) { 0 -> 0f; 2 -> width.toFloat(); else -> width * .5f }

    private fun topTravel() = (windowTop + geometryHeight + dp(8)).coerceAtLeast(geometryHeight).toFloat()
    override fun onAttachedToWindow() { disposed = false; super.onAttachedToWindow() }
    override fun onDetachedFromWindow() {
        disposed = true
        pendingContent = null; displayedContent = null; contentFadeRemaining = 0L
        cancelInputs()
        motion?.cancel(); motion = null
        recall = IslandRecallGesture.onCancel()
        dragging = false; multiTouch = false; dragAxis = 0
        leaving = false; entryCameraAnchor = false
        resetTransform()
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
        if (isMedia) {
            val res = ModuleHelper.getModuleRes(context)
            val labels = intArrayOf(R.string.system_island_gesture_left, R.string.system_island_gesture_right,
                R.string.system_island_gesture_down, R.string.system_island_gesture_double, R.string.system_island_gesture_long)
            for (gesture in IslandMusicGesture.entries) if (musicActions.resolve(gesture, controlsEnabled) != 1)
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                    accessibilityActions[gesture.ordinal], res.getString(labels[gesture.ordinal])))
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (disposed || leaving) return false
        if (action == android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS) {
            ModuleHelper.guarded { onDismiss() }; return true
        }
        val index = accessibilityActions.indexOf(action)
        if (index >= 0) {
            val gesture = IslandMusicGesture.entries[index]
            if (!isMedia || musicActions.resolve(gesture, controlsEnabled) == 1) return false
            submit(gesture); return true
        }
        return super.performAccessibilityAction(action, arguments)
    }

    private companion object { val accessibilityActions = IntArray(5) { View.generateViewId() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
}

/** Lightweight playback animation, or five measured bands provided by the owning capture lease. */
internal class IslandWaveView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private var aggregatedVisible = false
    private var scheduled = false
    private var phase = 0f
    private val energies = FloatArray(5)
    private var real = false
    fun spectrum(values: FloatArray?) {
        if (values == null && !real) return
        real = values != null
        if (values != null) System.arraycopy(values, 0, energies, 0, 5)
        else energies.fill(0f)
        updateRendering()
        invalidate()
    }
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

    private fun shouldRender() = !real && playing && aggregatedVisible && isAttachedToWindow &&
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
            val fraction = if (real && playing) .12f + .83f * energies[index] else if (playing) 0.25f + 0.65f * abs(sin(phase + index * 1.4f)) else 0.22f
            val h = height * fraction
            val x = step * index + (step - barWidth) * 0.5f
            canvas.drawRoundRect(x, (height - h) * 0.5f, x + barWidth, (height + h) * 0.5f,
                barWidth * 0.5f, barWidth * 0.5f, paint)
        }
    }
}
