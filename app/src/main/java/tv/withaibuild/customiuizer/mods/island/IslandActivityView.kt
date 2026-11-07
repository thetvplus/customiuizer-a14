package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.text.LineBreaker
import android.media.session.PlaybackState
import android.text.TextUtils
import android.text.TextPaint
import android.text.Layout
import android.text.StaticLayout
import android.util.TypedValue
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Chronometer
import android.widget.TextView
import android.os.SystemClock
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.R
import kotlin.math.abs
import kotlin.math.sin

/** One capsule shell, one finite motion clock. Width changes never resize glyphs or end caps. */
internal class IslandActivityView(context: Context) : FrameLayout(context) {
    private val shape = IslandShape()
    private val icon = ImageView(context)
    private val wave = IslandWaveView(context)
    private val title = Chronometer(context).apply {
        setTextColor(Color.WHITE); textSize = 13f; maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        includeFontPadding = false
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG_LTR
        breakStrategy = LineBreaker.BREAK_STRATEGY_BALANCED
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        fontFeatureSettings = "tnum"
        setOnChronometerTickListener {
            ModuleHelper.guarded { if (isCountDown && SystemClock.elapsedRealtime() >= base) {
                stop(); text = format?.replace("%s", "00:00") ?: "00:00"
            } }
        }
    }
    private val statusPaint = TextPaint(title.paint)
    private val captionClip = Rect()
    private val identityClip = Rect()
    private var statusFit: IslandStatusLayout.Fit? = null
    private var statusLabel: String? = null
    private var measuredStatus: String? = null
    private var measuredStatusShort: String? = null
    private val statusBounds=IntArray(3)
    private var statusOffset=Float.NaN
    private var statusBase=Float.NaN
    private val left = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        // Counter-scaling preserves the physical edge margin while contracting.
        // Target-layout padding must not cut the compensated artwork in half.
        clipToPadding = false
    }
    private val subtitle = TextView(context).apply {
        setTextColor(0xffb8b8bd.toInt()); maxLines = 1
        ellipsize = TextUtils.TruncateAt.END; includeFontPadding = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val identityTitle = TextView(context).apply {
        setTextColor(Color.WHITE); maxLines = 1; includeFontPadding = false
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        pivotX = 0f
    }
    private val text = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
    }
    private var controlActions = 0L
    private var controlPlayback = PlaybackState.STATE_NONE
    private var baseTextSize = title.textSize
    private var contentHeightDp = IslandSizing.COLLAPSED_HEIGHT_DP
    private var iconSize = dp(24)
    private var waveHeight = dp(20)
    private var controlsEnabled = false
    private var avatar = false
    private var recording = false
    private val quietRecording = FloatArray(5)
    private var opticalX = 0f
    private var opticalY = 0f
    private var slop = ViewConfiguration.get(context).scaledTouchSlop
    private var recall = IslandRecallGesture.onCancel()
    private var recallConfig = IslandRecallGesture.configFromSlop(slop, dp(IslandSizing.COLLAPSED_HEIGHT_DP))
    private var downY = 0f
    private var downX = 0f
    private var windowEvent: MotionEvent? = null
    private fun pointerX(event: MotionEvent) = (windowEvent ?: event).rawX
    private fun pointerY(event: MotionEvent) = (windowEvent ?: event).rawY

    /** Recognition stays in screen coordinates while the drawing matrix changes. */
    fun dispatchFromWindow(local: MotionEvent, original: MotionEvent): Boolean {
        windowEvent = original
        return try { dispatchTouchEvent(local) } finally { windowEvent = null }
    }
    private var dragging = false
    private var multiTouch = false
    private var dragAxis = 0
    private var longConsumed = false
    private var doubleCandidate = false
    private var inputCancelled = false
    private var tapsCancelled = false
    private var tapSuppressed = false
    private var velocityTracker: VelocityTracker? = null
    private var touchActive = false
    private var dragCaptured = false
    private var pressedBody = false
    private var pressMotion: ValueAnimator? = null
    private var downScaleX = 1f
    private var downScaleY = 1f
    private var downTranslationX = 0f
    private var downTranslationY = 0f
    private var gestureIdentity: Any? = null
    private var musicActions = IslandMusicActions()
    private val tapListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!doubleEnabled() && !longConsumed && !inputCancelled && !tapSuppressed) performClick()
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (doubleEnabled() && !longConsumed && !inputCancelled && !tapSuppressed && !leaving && !disposed) performClick()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            doubleCandidate = doubleEnabled()
            return doubleCandidate
        }
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP && doubleCandidate && !inputCancelled && !tapSuppressed && !dragging && !multiTouch)
                submit(IslandMusicGesture.DOUBLE_TAP)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (!inputCancelled && !dragging && !multiTouch && !leaving && !disposed && longEnabled()) {
                longConsumed = true
                if ((isMedia || idle) && musicActions.resolve(IslandMusicGesture.LONG_PRESS, controlsEnabled) != 1)
                    submit(IslandMusicGesture.LONG_PRESS)
            }
        }
    }
    private val taps = GestureDetector(context, tapListener)
    private var cameraY = 0f
    private var cameraDiameter = dp(16).toFloat()
    private var cameraHeight = cameraDiameter
    private var cameraAnchored = false
    private var geometryDirty = true
    private var geometryWidth = 1
    private var geometryHeight = 1
    private var maximumWidth = 1f
    private var cameraGap = 0
    private var cameraOffset = 0f
    private var alignment = 1
    var touchMargin = 0
    private var labelWidth = 0f
    private var textReveal = 1f
    private var layerReveal = 1f
    private class Content(val label: String, val drawable: Drawable?, val media: Boolean,
        val waveform: Boolean, val avatar: Boolean, val status: Boolean, val expanded: Boolean, val photo: Boolean,
        val clock: IslandLiveClock? = null, val clockLabel: String? = null, val clockKey: IslandLiveKey? = null) {
        fun sameCompactVisual(other: Content) = media && other.media && !expanded && !other.expanded &&
            drawable === other.drawable && waveform == other.waveform && avatar == other.avatar &&
            status == other.status && photo == other.photo
        fun sameRunningClockVisual(other: Content) = !media && !other.media && clock != null && other.clock != null &&
            clockKey != null && clockKey == other.clockKey &&
            clock.pausedMillis == null && other.clock.pausedMillis == null && clock.countdown == other.clock.countdown &&
            drawable === other.drawable && clockLabel == other.clockLabel && waveform == other.waveform &&
            avatar == other.avatar && status == other.status && expanded == other.expanded && photo == other.photo
        fun matches(label: String, drawable: Drawable?, media: Boolean, waveform: Boolean,
            avatar: Boolean, status: Boolean, expanded: Boolean, photo: Boolean, clock: IslandLiveClock?, detail: String?, clockKey: IslandLiveKey?) = this.label == label && this.drawable === drawable &&
            this.media == media && this.waveform == waveform && this.avatar == avatar && this.status == status && this.expanded == expanded && this.photo == photo && this.clock == clock && clockLabel == detail && this.clockKey == clockKey
    }
    private var displayedContent: Content? = null
    private var pendingContent: Content? = null
    private var contentFadeRemaining = 0L
    private var entryCameraAnchor = false
    private var motion: ValueAnimator? = null
    private var mediaReturnProgress = 1f
    private var nativeArrival = false
    private var receivingExisting = false
    private var receivingContent = false
    private var returnProgress: IslandReturnProgress? = null

    fun acceptReturnProgress(progress: IslandReturnProgress?) {
        if (returnProgress !== progress) returnProgress?.close()
        returnProgress = progress
    }
    private val breadth = IslandMotion.Spring()
    private var breadthExtraVelocity = 0f
    private val depth = IslandMotion.Spring()
    private val shiftX = IslandMotion.Spring()
    private val shiftY = IslandMotion.Spring()
    private val opacity = IslandMotion.Spring()
    private var disposed = false
    private var leaving = false
    private val iconMask = GradientDrawable().apply { setColor(Color.BLACK) }
    var powerSaveMotion = false
        set(value) {
            if (field == value) return
            field = value
            if (value) motion?.end()
        }
    private val contentOpacity = IslandMotion.Spring()
    private val outgoingOpacity = IslandMotion.Spring()
    private var outgoing: IslandContentSnapshot? = null
    private var outgoingReveal = 0f
    private var outgoingFresh = false
    private var contentFromInert = false
    var entranceStyle = IslandEntranceStyle.TOP
    var motionScale = 1f
    var idle = false
        set(value) { field = value; isClickable = !value || idleTapAction != 1 }
    var idleTapAction = 1
        set(value) { field = value; isClickable = !idle || value != 1 }
    var expanded = false
    /** Target content owns capture; the old child may still be fading during a handoff. */
    val waveformRequested: Boolean get() = recording || isMedia && !expanded
    val waveformSampling: Boolean get() = waveformRequested && wave.isShown && wave.visibility == VISIBLE &&
        (wave.outputVisible || motion != null && !leaving)
    private val waveBounds = android.graphics.Rect()
    private val waveClip = android.graphics.Rect()
    private val waveVisibleBounds = android.graphics.RectF()
    private val waveClipBounds = android.graphics.RectF()
    private val waveClipMatrix = android.graphics.Matrix()

    /** Reuses presentation's pre-draw; alpha/clip changes do not emit layout events. */
    fun refreshWaveformVisibility() {
        var visible = alpha > 0f && wave.alpha > 0f && wave.isShown && wave.windowVisibility == VISIBLE && wave.width > 0 && wave.height > 0 &&
            wave.getGlobalVisibleRect(waveBounds) && !waveBounds.isEmpty
        waveVisibleBounds.set(waveBounds)
        var parent: View? = wave
        while (visible && parent != null) {
            if (parent.alpha <= 0f) { visible = false; break }
            // getGlobalVisibleRect includes layout clipping, but not explicit clipBounds.
            if (parent.getClipBounds(waveClip)) {
                waveClipMatrix.reset(); parent.transformMatrixToGlobal(waveClipMatrix)
                waveClipBounds.set(waveClip); waveClipMatrix.mapRect(waveClipBounds)
                visible = waveVisibleBounds.intersect(waveClipBounds)
            }
            parent = parent.parent as? View
        }
        wave.outputVisible = visible
    }
    var waveformGain: Int
        get() = wave.gainPercent
        set(value) { wave.gainPercent = value }
    var realtimeWaveform = false
    var privacy = 0
        set(value) { if (field != value) { field = value; invalidate() } }
    private val privacyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var flipDirection = 1f
    private var albumFlip = false
    // One live content node and one outgoing display list share the motion clock.
    private val contentLayer = FrameLayout(context).apply { clipChildren = false; clipToPadding = false }
    var windowTop = 0
    var isMedia = false
        private set
    var onDismiss: () -> Unit = {}
    var onOpen: () -> Unit = {}
    var onGesture: (IslandMusicGesture) -> Unit = {}
    var onInteraction: (Boolean) -> Unit = {}

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
        left.addView(identityTitle, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        text.addView(title, LinearLayout.LayoutParams(-1, -2))
        text.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        contentLayer.addView(left); contentLayer.addView(text); contentLayer.addView(wave)
        addView(contentLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        setOnClickListener { if (!disposed && !leaving && (!idle || idleTapAction != 1)) ModuleHelper.guarded { onOpen() } }
        taps.setIsLongpressEnabled(false)
        taps.setOnDoubleTapListener(null)
    }

    private fun doubleEnabled() = (isMedia || idle) && musicActions.doubleTap != 1
    private fun longEnabled() = (isMedia || idle) && musicActions.longPress != 1

    fun configureGestures(identity: Any?, actions: IslandMusicActions, trackSwipes: Boolean) {
        if (gestureIdentity != identity || musicActions != actions || controlsEnabled != trackSwipes) cancelInputs()
        gestureIdentity = identity
        musicActions = actions
        controlsEnabled = trackSwipes
        taps.setIsLongpressEnabled(longEnabled())
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
        cancelPendingInputEvents()
        cancelLongPress()
        if (cancelDrag) { recall = IslandRecallGesture.onCancel(); finishTouch() }
        doubleCandidate = false
        if (cancelDrag) { releasePress(); pressMotion = null } else pressedBody = false
    }

    private fun beginPress() {
        // A native min-duration-zero recognizer supplies the press preference.
        // Do not take a shell already owned by arrival or content/layout motion.
        if (motion != null || nativeArrival || disposed || leaving || powerSaveMotion ||
            !ValueAnimator.areAnimatorsEnabled() || idle && idleTapAction == 1) return
        pressedBody = true
        // 22G100 requests 1.2 then clips to its system container. Map that
        // request into A14's existing body envelope; width clipping must not
        // leave an independently inflated vertical axis.
        val pressedScale=minOf(1.2f,1.14f,maximumWidth/geometryWidth.coerceAtLeast(1))
        animateTo(pressedScale, pressedScale,
            translationX, translationY, alpha, IslandMotion.REBOUND, interaction = true)
        pressMotion = motion
    }

    private fun releasePress() {
        if (!pressedBody) return
        val owned = motion == null || motion === pressMotion
        pressedBody = false; pressMotion = null
        if (owned && !disposed && !leaving)
            animateTo(1f, 1f, 0f, 0f, 1f, IslandMotion.REBOUND, rebound = true, nativeProfile = true)
    }

    private fun submit(gesture: IslandMusicGesture) {
        val action = musicActions.resolve(gesture, controlsEnabled)
        // Snapshot admission avoids advertising unavailable controls; the owner rechecks
        // the actual session immediately before execution using the same resolver.
        if (!idle && action in 87..88 && !IslandMediaPolicy.canSkip(
                if (action == 87) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS, controlActions)) return
        if (!idle && (action == 85 || action == IslandMusicActions.PLAY || action == IslandMusicActions.PAUSE) &&
            IslandTransportAction.resolve(action, controlPlayback, controlActions) == null) return
        if (!disposed && !leaving && (isMedia || idle) && musicActions.resolve(gesture, controlsEnabled) != 1)
            ModuleHelper.guarded { onGesture(gesture) }
    }

    fun bind(label: String, detail: String?, drawable: Drawable?, playing: Boolean,
        media: Boolean, waveform: Boolean, actions: Long, showControls: Boolean,
        playbackState: Int = PlaybackState.STATE_NONE, avatar: Boolean = false, status: Boolean = false,
        expanded: Boolean = false, loading: Boolean = false, photo: Boolean = media || avatar,
        clock: IslandLiveClock? = null, recording: Boolean = false, clockKey: IslandLiveKey? = null): Boolean {
        val recordingChanged = this.recording != recording
        this.recording = recording
        if (recordingChanged) geometryDirty = true
        if (recordingChanged && recording) wave.spectrum(quietRecording)
        if (isMedia != media) cancelInputs()
        isMedia = media
        this.expanded = expanded
        taps.setIsLongpressEnabled(longEnabled())
        taps.setOnDoubleTapListener(if (doubleEnabled()) tapListener else null)
        controlsEnabled = showControls
        controlActions = actions
        val playbackChanged = controlPlayback != playbackState
        controlPlayback = playbackState
        // Action identity and accessibility follow the owner immediately. Only the visual
        // handoff is deferred, with one latest value and the shell's existing finite clock.
        val caption = label
        val previous = pendingContent ?: displayedContent
        val changed = recordingChanged || previous?.matches(caption, drawable, media, waveform, avatar, status, expanded, photo, clock, detail, clockKey) != true
        var visualChanged = changed
        if (changed && !recordingChanged && !albumFlip && pendingContent != null &&
            displayedContent?.matches(caption, drawable, media, waveform, avatar, status, expanded, photo, clock, detail, clockKey) == true) {
            // A -> B -> A before B becomes visible: keep A's prepared views and
            // recover from the current opacity instead of fading out/rebinding A.
            pendingContent = null; contentFadeRemaining = 0L; albumFlip = false
        } else if (changed) {
            val content = Content(caption, drawable, media, waveform, avatar, status, expanded, photo, clock, detail, clockKey)
            if (previous != null && (content.sameCompactVisual(previous) || content.sameRunningClockVisual(previous))) {
                // Compact media has no visible label. Metadata-only updates, including
                // mirrored Binder copies of the same cover, must not fade its artwork/wave.
                // A running clock's corrected base is also an in-place update:
                // preserve its entry opacity and finite clock instead of fading
                // the whole layer out when a second native snapshot arrives.
                if (pendingContent != null) pendingContent = content
                else if (content.sameRunningClockVisual(previous)) {
                    // Native ticks own the text. Refresh only a genuinely corrected base;
                    // do not stop/start the clock or rebind artwork/layout for snapshots.
                    displayedContent = content
                    if (title.base != content.clock!!.base) title.base = content.clock.base
                    val now = SystemClock.elapsedRealtime()
                    if (content.clock.countdown && previous.clock!!.base <= now && content.clock.base > now) title.start()
                    val width = maxOf(title.paint.measureText(content.label),
                        if (subtitle.visibility == VISIBLE) subtitle.paint.measureText(subtitle.text.toString()) else 0f)
                    if (labelWidth != width) { labelWidth = width; updateContour() }
                } else {
                    displayedContent = content
                    // Keep prepared hidden detail current for later expansion/accessibility.
                    val detailLabel = content.clockLabel?.trim()?.takeIf { it.isNotEmpty() && it != content.label }.orEmpty()
                    if (!TextUtils.equals(subtitle.text, detailLabel)) subtitle.text = detailLabel
                }
                visualChanged = false
            } else if (isAttachedToWindow && !leaving && !powerSaveMotion && ValueAnimator.areAnimatorsEnabled() &&
                displayedContent != null && alpha > .01f && layerReveal > .01f) {
                albumFlip = media && previous?.media == true && previous.drawable !== drawable &&
                    previous.expanded == expanded && previous.photo && photo && previous.drawable != null && drawable != null
                if (pendingContent == null) contentFadeRemaining = if (albumFlip) 140L else 60L
                if (pendingContent == null) prepareOutgoing()
                pendingContent = content
            } else {
                pendingContent = null; contentFadeRemaining = 0L
                applyContent(content)
            }
        } else if (pendingContent == null && status) {
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, statusFit?.size ?: baseTextSize)
            val visible=statusLabel ?: detail ?: label
            if(!TextUtils.equals(title.text,visible)) { title.text=visible; labelWidth=title.paint.measureText(visible) }
        }
        wave.holding = media && loading && waveform && !powerSaveMotion
        wave.playing = (media && playing || recording && clock?.pausedMillis == null) && waveform && !powerSaveMotion
        if (changed || playbackChanged || contentDescription == null) updateDescription(label, detail, media)
        return visualChanged
    }

    private fun updateDescription(label: String, detail: String?, media: Boolean) {
        val playbackDescription = if (!media) null else ModuleHelper.getModuleRes(context).getString(when (controlPlayback) {
            PlaybackState.STATE_PLAYING -> R.string.system_island_playing
            PlaybackState.STATE_PAUSED -> R.string.system_island_paused
            else -> R.string.system_island_connecting
        })
        contentDescription = listOfNotNull(label, playbackDescription, detail?.takeIf { it.isNotBlank() && it != label }).joinToString(", ")
    }

    private fun applyContent(content: Content) {
        geometryDirty = true
        title.stop(); title.format = null
        displayedContent = content
        avatar = content.avatar
        title.ellipsize = if (content.status) null else TextUtils.TruncateAt.END
        val detail = content.clockLabel?.trim()?.takeIf { it.isNotEmpty() && it != content.label }
        val split = content.expanded && !content.status && content.clock == null && detail != null
        val twoLevels = detail != null && (content.status || content.expanded && content.clock != null)
        applyTextSizes(content, split)
        val identityLabel = if (split) content.label else ""
        if (!TextUtils.equals(identityTitle.text, identityLabel)) identityTitle.text = identityLabel
        identityTitle.visibility = if (split) VISIBLE else GONE
        val detailLabel = if (content.status && detail != null) content.label else detail.orEmpty()
        if (!TextUtils.equals(subtitle.text, detailLabel)) subtitle.text = detailLabel
        subtitle.visibility = if (twoLevels) VISIBLE else GONE
        fitTextRows()
        val visibleLabel = if (content.status) statusLabel ?: detail ?: content.label
            else if (content.media && !content.expanded) "" else if (split) detail.orEmpty() else content.label
        if (title.text.toString() != visibleLabel) title.text = visibleLabel
        content.clock?.let { clock ->
            if (clock.pausedMillis != null) title.text = IslandLiveLabels.time(clock.pausedMillis)
            else {
                title.isCountDown = clock.countdown
                title.base = clock.base
                title.start()
            }
        }
        labelWidth = maxOf(visibleLabel.lineSequence().maxOfOrNull { title.paint.measureText(it) } ?: 0f,
            if (twoLevels) subtitle.paint.measureText(detailLabel) else 0f)
        text.visibility = if (content.media && !content.expanded) GONE else VISIBLE
        icon.scaleType = if (content.photo) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
        icon.clipToOutline = content.photo
        val insets = if (!content.photo) content.drawable?.opticalInsets else null
        opticalX = IslandContentLayout.opticalShift(insets?.left ?: 0, insets?.right ?: 0, content.drawable?.intrinsicWidth ?: 0)
        opticalY = IslandContentLayout.opticalShift(insets?.top ?: 0, insets?.bottom ?: 0, content.drawable?.intrinsicHeight ?: 0)
        icon.setImageDrawable(content.drawable)
        icon.visibility = if (content.drawable == null || recording) GONE else VISIBLE
        wave.visibility = if ((content.media && !content.expanded || recording) && content.waveform) VISIBLE else GONE
        geometry(geometryWidth, geometryHeight, cameraGap, maximumWidth.toInt(), alignment, cameraOffset)
    }

    private fun commitContent() {
        val latest = pendingContent ?: return
        pendingContent = null; contentFadeRemaining = 0L
        applyContent(latest)
    }

    fun spectrum(values: FloatArray?) { wave.spectrum(if ((recording || realtimeWaveform) && values == null) quietRecording else values) }

    fun fitsToast(value: String, width: Int, cameraGap: Int, height: Int = dp(IslandSizing.COLLAPSED_HEIGHT_DP),
        expanded: Boolean = false): Boolean {
        val room = captionRoom(width, cameraGap, height, split = false)
        if (room <= 0) return false
        return fitsToast(value, room, height, expanded, toastPaint(expanded, height))
    }

    private fun toastPaint(expanded: Boolean, height: Int) = TextPaint(statusPaint).apply {
        textSize = if (expanded) expandedToastSize(height) else baseTextSize
    }

    private var toastFontHeight = -1
    private var toastFontBase = -1f
    private var toastFontSize = 0f
    private fun expandedToastSize(height: Int): Float {
        if (toastFontHeight == height && toastFontBase == baseTextSize) return toastFontSize
        val paint = TextPaint(statusPaint)
        val upper = baseTextSize * .94f
        var low = minOf(upper, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics))
        var high = upper
        val room = (height - dp(4) - 2).coerceAtLeast(0) // Native line metrics round each row to pixels.
        paint.textSize = high
        if (paint.fontSpacing * 2 > room) repeat(8) {
            val middle = (low + high) * .5f
            paint.textSize = middle
            if (paint.fontSpacing * 2 <= room) low = middle else high = middle
        } else low = high
        toastFontHeight = height; toastFontBase = baseTextSize; toastFontSize = low
        return low
    }

    private fun fitsToast(value: String, room: Int, height: Int, expanded: Boolean, paint: TextPaint): Boolean {
        val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, room)
            .setIncludePad(false).setMaxLines(if (expanded) 2 else 1).build()
        return layout.height <= height - dp(4) && layout.getLineEnd(layout.lineCount - 1) == value.length &&
            (0 until layout.lineCount).all { layout.getLineWidth(it) <= room + .5f }
    }

    private var measuredToast: String? = null
    private val toastBounds = IntArray(5)
    private var toastTextSize = 0f
    private var measuredToastWidth = 0
    private var measuredToastExpanded = false
    private var measuredToastStatus = false
    private var measuredToastOffset = Float.NaN

    /** Cold, bounded native line layout; the selected width is reused at presentation. */
    fun toastWidth(value: String, minimum: Int, maximum: Int, gap: Int, height: Int, expanded: Boolean = true,
        status: Boolean = false, offset: Float = cameraOffset): Int {
        if (measuredToast == value && toastBounds[0] == minimum && toastBounds[1] == maximum &&
            toastBounds[2] == gap && toastBounds[3] == height && toastBounds[4] == resources.configuration.layoutDirection &&
            toastTextSize == baseTextSize && measuredToastExpanded == expanded &&
            measuredToastStatus == status && measuredToastOffset == offset) return measuredToastWidth
        var low = minimum.coerceAtMost(maximum)
        var high = maximum
        var preparedPaint: TextPaint? = null
        // Pixel-space monotone fit, at most fourteen layouts even at the supported 10k screen bound.
        while (low < high) {
            val middle = (low + high) ushr 1
            val room = captionRoom(middle, gap, height, split = false, offset = offset)
            val fits = if (status) statusFit(value,room,height)!=null else room > 0 && fitsToast(value, room, height, expanded,
                preparedPaint ?: toastPaint(expanded, height).also { preparedPaint = it })
            if (fits) high = middle else low = middle + 1
        }
        measuredToast = value; measuredToastWidth = high; toastTextSize = baseTextSize
        measuredToastExpanded = expanded
        measuredToastStatus = status; measuredToastOffset = offset
        toastBounds[0] = minimum; toastBounds[1] = maximum; toastBounds[2] = gap; toastBounds[3] = height
        toastBounds[4] = resources.configuration.layoutDirection
        return high
    }

    fun textWidth(value: String): Float = statusPaint.measureText(value)

    private fun statusFit(label: String,room: Int,height: Int): IslandStatusLayout.Fit? {
        val minimumTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        return IslandStatusLayout.fit(label, statusPaint,room,baseTextSize,minimumTextSize,height-dp(4))
    }

    fun statusHeight(label: String, width: Int, gap: Int, height: Int, offset: Float = cameraOffset,
        shortLabel: String? = null): Int? {
        if(measuredStatus==label && measuredStatusShort==shortLabel && statusBounds[0]==width &&
            statusBounds[1]==gap && statusBounds[2]==height && statusOffset==offset && statusBase==baseTextSize)
            return statusFit?.let { height }
        val room=captionRoom(width,gap,height,split=false,offset=offset)
        statusLabel=label
        statusFit=statusFit(label,room,height)
        if(statusFit==null && shortLabel!=null) {
            statusLabel=shortLabel; statusFit=statusFit(shortLabel,room,height)
        }
        measuredStatus=label; measuredStatusShort=shortLabel
        statusBounds[0]=width; statusBounds[1]=gap; statusBounds[2]=height
        statusOffset=offset; statusBase=baseTextSize
        return statusFit?.let { height }
    }

    fun geometry(width: Int, height: Int, cameraGap: Int, maximumWidth: Int = width, alignment: Int = 1,
        cameraOffset: Float = 0f) {
        if (!geometryDirty && geometryWidth == width && geometryHeight == height && this.cameraGap == cameraGap &&
            this.maximumWidth == maximumWidth.coerceAtLeast(width).toFloat() && this.alignment == alignment &&
            this.cameraOffset == cameraOffset) {
            // Layout is unchanged; a caller may still have advanced the current transform.
            updateContour()
            return
        }
        geometryDirty = false
        if (this.alignment != alignment) cancelInputs()
        this.alignment = alignment
        pivotX = horizontalPivot(width)
        geometryWidth = width; geometryHeight = height
        if (!cameraAnchored) cameraY = height * .5f
        this.maximumWidth = maximumWidth.coerceAtLeast(width).toFloat()
        this.cameraGap = cameraGap
        this.cameraOffset = cameraOffset
        val side = if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f
        // The camera side is physical; paragraph direction must not move a
        // Latin caption to the opposite edge of an RTL lane (or vice versa).
        val captionGravity = (if (cameraGap > 0) Gravity.getAbsoluteGravity(Gravity.START, layoutDirection)
            else Gravity.END) or Gravity.CENTER_VERTICAL
        if (title.gravity != captionGravity) title.gravity = captionGravity
        if (subtitle.gravity != captionGravity) subtitle.gravity = captionGravity
        iconSize = iconSizeForHeight(height)
        val half = (if (cameraGap > 0) ((width - cameraGap) / 2f + cameraOffset * side).toInt()
            else if (identityTitle.visibility == VISIBLE) (width - dp(8)) / 2
            else dp(IslandSizing.EDGE_DP) + iconSize + dp(8)).coerceAtLeast(1)
        val iconParams = icon.layoutParams
        if (iconParams.width != iconSize || iconParams.height != iconSize) {
            iconParams.width = iconSize; iconParams.height = iconSize
            icon.layoutParams = iconParams
        }
        val scale = iconSize.toFloat() / dp(24).coerceAtLeast(1)
        val radius = dp(7) * scale
        if (iconMask.cornerRadius != radius) iconMask.cornerRadius = radius
        val iconShape = if (avatar) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
        if (iconMask.shape != iconShape) iconMask.shape = iconShape
        left.setPadding(dp(IslandSizing.EDGE_DP), 0, if (identityTitle.visibility == VISIBLE) dp(8) else 0, 0)
        layoutChild(left, half, height, Gravity.TOP or Gravity.START)
        val labelRoom = captionRoom(width, cameraGap)
        text.pivotX = if (cameraGap > 0) { if (side > 0f) 0f else labelRoom.toFloat() } else labelRoom * .5f
        layoutChild(text, labelRoom, height, Gravity.TOP or Gravity.END,
            end = dp(IslandSizing.EDGE_DP))
        waveHeight = kotlin.math.round(dp(20) * scale).toInt().coerceAtLeast(1)
        layoutChild(wave, iconSize, waveHeight, Gravity.TOP or if (recording) Gravity.START else Gravity.END,
            top = (height - waveHeight) / 2, end = dp(IslandSizing.EDGE_DP))
        if (recording) {
            val params = wave.layoutParams as LayoutParams
            params.leftMargin = dp(IslandSizing.EDGE_DP)
        } else (wave.layoutParams as LayoutParams).leftMargin = 0
        fitTextRows()
        updateContour()
    }

    private fun fitTextRows() {
        val content = displayedContent ?: return
        val maxLines = if (content.status) statusFit?.lines ?: 1 else if (content.expanded && content.clock == null && !content.media &&
            title.paint.fontSpacing * 2f <= geometryHeight - dp(4)) 2 else 1
        if (title.maxLines != maxLines) title.maxLines = maxLines
        val detail = content.clockLabel?.trim()
        subtitle.visibility = if ((content.status || content.expanded && content.clock != null) && !detail.isNullOrEmpty() && detail != content.label &&
            title.paint.fontSpacing * title.maxLines + subtitle.paint.fontSpacing <= geometryHeight - dp(4)) VISIBLE else GONE
    }

    private fun layoutChild(child: View, width: Int, height: Int, gravity: Int, top: Int = 0, end: Int = 0) {
        val params = child.layoutParams as LayoutParams
        if (params.width == width && params.height == height && params.gravity == gravity &&
            params.topMargin == top && params.marginEnd == end) return
        params.width = width; params.height = height; params.gravity = gravity
        params.topMargin = top; params.marginEnd = end
        child.layoutParams = params
    }

    fun cameraAnchor(centerY: Float, diameter: Float, height: Float = diameter) {
        cameraAnchored = true
        cameraY = centerY
        cameraDiameter = diameter.coerceAtLeast(dp(8).toFloat())
        cameraHeight = height.coerceAtLeast(dp(8).toFloat())
    }

    private fun compactWidth() = maxOf(cameraDiameter, geometryHeight.toFloat())
        .coerceAtMost(geometryWidth.toFloat())

    fun refreshConfiguration() {
        geometryDirty = true
        slop = ViewConfiguration.get(context).scaledTouchSlop
        baseTextSize = -1f
        measuredToast = null
        measuredStatus = null
        title.textLocales = resources.configuration.locales
        statusPaint.set(title.paint)
        contentHeight(contentHeightDp)
        // Localized playback text can change independently of the latest bound content.
        (pendingContent ?: displayedContent)?.let { updateDescription(it.label, it.clockLabel, it.media) }
    }

    fun contentHeight(heightDp: Int) {
        val height = heightDp.coerceIn(IslandSizing.HEIGHT_MIN_DP, IslandSizing.HEIGHT_MAX_DP)
        contentHeightDp = height
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            13f + (height - 40) * .1f, resources.displayMetrics)
        if (abs(baseTextSize - size) <= .001f) return
        baseTextSize = size
        geometryDirty = true
        iconSize = iconSizeForHeight(dp(height))
        if (statusPaint.textSize != size) { statusPaint.textSize = size; statusFit = null }
        if (displayedContent == null) title.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        displayedContent?.let { content ->
            val split = identityTitle.visibility == VISIBLE
            applyTextSizes(content, split)
            labelWidth = maxOf(title.text.lineSequence().maxOfOrNull { title.paint.measureText(it) } ?: 0f,
                if (subtitle.visibility == VISIBLE) subtitle.paint.measureText(subtitle.text.toString()) else 0f)
        }
    }

    private fun applyTextSizes(content: Content, split: Boolean) {
        val completeToast = content.expanded && !content.status && !content.media && content.clock == null && content.clockLabel.isNullOrBlank()
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (content.status) statusFit?.size ?: baseTextSize
            else if (completeToast) expandedToastSize(dp(contentHeightDp))
            else baseTextSize * if (split && content.media) .82f else if (content.expanded) .94f else 1f)
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSize * .82f)
        identityTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSize * .94f)
    }

    private fun iconSizeForHeight(height: Int) =
        (dp(24) * (1f + (height.toFloat() / dp(40).coerceAtLeast(1) - 1f) / 3f)).toInt()
            .coerceIn(dp(16).coerceAtLeast(1), maxOf(dp(16), height - dp(8)))

    fun prepareEntry(mediaReturn: Boolean = false) {
        motion?.cancel(); motion = null
        commitContent()
        clearContentEffects()
        contentFromInert = !mediaReturn || returnProgress == null
        if (contentFromInert) layerReveal = 0f
        disposed = false; leaving = false
        nativeArrival = mediaReturn
        receivingExisting = false
        receivingContent = false
        mediaReturnProgress = if (mediaReturn && returnProgress != null &&
            !powerSaveMotion && ValueAnimator.areAnimatorsEnabled()) 0f else 1f
        if (mediaReturnProgress == 0f || powerSaveMotion || !ValueAnimator.areAnimatorsEnabled()) {
            resetTransform(preserveReturn = mediaReturn)
            if (mediaReturnProgress == 0f) {
                pivotY = geometryHeight * .5f
                scaleX = compactWidth() / geometryWidth.coerceAtLeast(1)
                updateContour()
            }
        } else {
            alpha = 0f
            // A Home return grows from the camera even when capture/progress is
            // unavailable. The selected direction belongs to ordinary entry.
            entryCameraAnchor = mediaReturn || entranceStyle == IslandEntranceStyle.CENTER
            pivotY = if (entryCameraAnchor) cameraY else 0f
            // Centre entry grows straight from the camera seed to the target form.
            // Top entry travels at the target width, with no short-form waypoint.
            scaleX = if (entryCameraAnchor) compactWidth() / geometryWidth.coerceAtLeast(1)
                else 1f
            scaleY = 1f
            translationX = 0f
            translationY = if (entryCameraAnchor) 0f else -topTravel()
            updateContour()
        }
    }

    fun resumeEntry() { leaving = false }

    /** A visible idle capsule is already the source object. Do not reset its opacity,
     * breadth or pivot when the native app window hands music back to it. */
    fun prepareReturnFromCurrent() {
        // An existing capsule must still morph when capture/progress is unavailable.
        // Only a real native channel owns the longer window-arrival clock.
        nativeArrival = true
        receivingExisting = true
        receivingContent = false
        changePivot(horizontalPivot(geometryWidth), geometryHeight * .5f)
        mediaReturnProgress = if (returnProgress != null && !powerSaveMotion && ValueAnimator.areAnimatorsEnabled()) 0f else 1f
    }

    fun prepareContentArrivalFromCurrent(receiveBreadth: Boolean = true) {
        acceptReturnProgress(null)
        nativeArrival = false
        receivingExisting = false
        receivingContent = receiveBreadth
        changePivot(horizontalPivot(geometryWidth), geometryHeight * .5f)
        // A new activity can receive horizontally without becoming a Home
        // window handoff. Metadata/form-only refreshes keep their current shell.
        mediaReturnProgress = 1f
    }

    fun transition(fromWidth: Int, fromHeight: Int, width: Int, height: Int, entering: Boolean,
        secondary: Boolean = false, visualLeft: Float? = null, visualTop: Float? = null,
        ordinaryBounds: Boolean = true, ringerShake: Boolean = false) {
        if (dragCaptured) cancelInputs()
        recall = IslandRecallGesture.onCancel()
        leaving = false
        val startWidth = (if (entering) geometryWidth else fromWidth) * scaleX
        val startHeight = (if (entering) geometryHeight else fromHeight) * scaleY
        val secondarySizeChange=IslandMotion.hasSignificantSizeChange(startWidth,startHeight,
            width.toFloat(),height.toFloat(),dp(6).toFloat())
        if (!entering) {
            scaleX *= fromWidth.toFloat() / width.coerceAtLeast(1)
            scaleY *= fromHeight.toFloat() / height.coerceAtLeast(1)
            // The window already has its final bounds; preserve the last visible origin
            // before retargeting the same physical springs, including pivot changes.
            visualLeft?.let { translationX = it - pivotX * (1f - scaleX) }
            visualTop?.let { translationY = it - pivotY * (1f - scaleY) }
        }
        val travel = if (mediaReturnProgress == 0f) {
            if (nativeArrival && returnProgress != null) IslandMotion.RETURN_TIMEOUT else IslandMotion.CONTENT_ARRIVAL
        }
            else if (entering) IslandMotion.ENTER
            else IslandMotion.morphDuration(startWidth, startHeight, width.toFloat(), height.toFloat(), secondary && secondarySizeChange)
        // A top entry travels at its final width. Home/camera arrivals and
        // already-visible content may receive horizontally on their own route.
        val verticalEntry = entering && !entryCameraAnchor
        animateTo(1f, 1f, 0f, 0f, 1f, maxOf(travel, if (albumFlip) IslandMotion.ALBUM else 0L),
            returning = nativeArrival || mediaReturnProgress == 0f,
            rigid = verticalEntry,
            rebound = entering && entranceStyle == IslandEntranceStyle.CENTER && !nativeArrival,
            boundsMotion = ordinaryBounds && !entering && mediaReturnProgress >= 1f &&
                maxOf(abs(startWidth - width), abs(startHeight - height)) > 1f, nativeProfile = true,
            ringerShake = ringerShake && !verticalEntry,
            arriving = entering && !verticalEntry || receivingContent)
        receivingContent = false
    }

    private fun requestDismiss() {
        if (leaving || disposed) return
        // The owner chooses the actual fallback first. A notification over music
        // morphs directly to that music; a final top exit must not visit a short form.
        ModuleHelper.guarded { onDismiss() }
    }

    fun exit(onEnd: () -> Unit) {
        pendingContent = null; contentFadeRemaining = 0L
        clearContentEffects()
        cancelMediaReturn()
        leaving = true
        cancelInputs()
        isPressed = false; cancelLongPress()
        wave.playing = false
        recall = IslandRecallGesture.onCancel()
        if (!isAttachedToWindow || powerSaveMotion || !ValueAnimator.areAnimatorsEnabled()) {
            alpha = 0f
            updateContour()
            ModuleHelper.guarded { onEnd() }
            return
        }
        entryCameraAnchor = entranceStyle == IslandEntranceStyle.CENTER
        if (entryCameraAnchor) {
            changePivot(horizontalPivot(geometryWidth), cameraY)
            animateTo(compactWidth() / geometryWidth.coerceAtLeast(1),
                1f, 0f, 0f, 0f, IslandMotion.EXIT, onEnd, nativeProfile = true)
        } else {
            changePivot(horizontalPivot(geometryWidth), 0f)
            animateTo(scaleX, scaleY, translationX, -topTravel(), 0f, IslandMotion.EXIT, onEnd,
                rigid = true)
        }
    }

    private fun changePivot(x: Float, y: Float) {
        translationX += (pivotX - x) * (1f - scaleX)
        translationY += (pivotY - y) * (1f - scaleY)
        pivotX = x; pivotY = y
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return dragging || multiTouch || super.onInterceptTouchEvent(event)
    }

    private fun beginTouch(event: MotionEvent) {
        finishTouch()
        velocityTracker = VelocityTracker.obtain()
        touchActive = true
        ModuleHelper.guarded { onInteraction(true) }
        if (motion == null) { entryCameraAnchor = false; changePivot(horizontalPivot(geometryWidth), geometryHeight * .5f) }
        downX = pointerX(event); downY = pointerY(event)
        downScaleX = scaleX; downScaleY = scaleY
        downTranslationX = translationX; downTranslationY = translationY
        dragCaptured = false
        dragging = false; multiTouch = false; dragAxis = 0
        longConsumed = false; inputCancelled = false; tapsCancelled = false; tapSuppressed = false
        recall = IslandRecallGesture.onDown(event.getPointerId(0), downX, downY)
        // Freeze thresholds at DOWN using the actual capsule, including automatic
        // sizing. Global navigation and the platform's shared touch slop are untouched.
        recallConfig = IslandRecallGesture.configFromSlop(slop,
            kotlin.math.round(geometryHeight * scaleY).toInt().coerceAtLeast(1))
        beginPress()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (disposed || leaving) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !shape.contains(event.x, event.y) &&
            (touchMargin <= 0 || event.x < -touchMargin / scaleX.coerceAtLeast(.01f) || event.x > width + touchMargin / scaleX.coerceAtLeast(.01f) ||
                event.y < -touchMargin / scaleY.coerceAtLeast(.01f) || event.y > height + touchMargin / scaleY.coerceAtLeast(.01f))) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) beginTouch(event)
        velocityTracker?.addMovement(windowEvent ?: event)
        // Resolve intent before View / GestureDetector sees UP, including a coalesced
        // flick with no MOVE. A small deliberate upward drag must never open the app.
        if (!multiTouch && !inputCancelled && !longConsumed &&
            (event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP)) {
            // Batched MOVE history can contain the upward excursion even when the
            // finger rolls back near DOWN at release. Use original window coordinates.
            val original = windowEvent ?: event
            val rawOffsetX = original.rawX - original.x
            val rawOffsetY = original.rawY - original.y
            for (sample in 0 until original.historySize) observeTouchSample(event.getPointerId(0),
                original.getHistoricalX(0, sample) + rawOffsetX, original.getHistoricalY(0, sample) + rawOffsetY)
            observeTouchSample(event.getPointerId(0), pointerX(event), pointerY(event))
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            multiTouch = true; recall = IslandRecallGesture.onCancel()
            cancelInputs()
        }
        val result = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            releasePress()
            if (longConsumed) cancelInputs()
            recall = IslandRecallGesture.onCancel()
            if (!leaving && motion == null && (dragging || multiTouch) &&
                (scaleX != 1f || scaleY != 1f || translationX != 0f || translationY != 0f))
                animateTo(1f, 1f, 0f, 0f, 1f, IslandMotion.REBOUND, rebound = true)
            dragging = false; multiTouch = false; dragAxis = 0
            dragCaptured = false
            finishTouch()
        }
        return result
    }

    private fun observeTouchSample(pointer: Int, x: Float, y: Float) {
        val dx = x - downX
        val dy = y - downY
        val upward = dragAxis != 1 && IslandRecallGesture.hasUpwardIntent(recall,
            pointer, x, y, recallConfig)
        if (upward && dragAxis == 0) dragAxis = 2
        dragging = dragging || upward || abs(dx) > slop || abs(dy) > slop
        if ((dragging || IslandRecallGesture.vetoesTap(recall, pointer, y, recallConfig)) && !tapSuppressed) {
            cancelInputs(cancelDrag = false)
            inputCancelled = false
            tapSuppressed = true
        }
        if (dragAxis == 2) recall = IslandRecallGesture.onMove(recall, pointer, x, y, recallConfig)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (multiTouch || inputCancelled) return true
        if (longConsumed) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val dx = pointerX(event) - downX; val dy = pointerY(event) - downY
                dragging = dragging || abs(dx) > slop || abs(dy) > slop
                if (dragAxis == 0 && dragging) {
                    if (abs(dx) > abs(dy) * 1.2f) dragAxis = 1
                    else if (abs(dy) > abs(dx) * 1.2f) dragAxis = 2
                }
                val config = recallConfig
                if (dragAxis == 2) recall = if (event.actionMasked == MotionEvent.ACTION_UP)
                    IslandRecallGesture.onUp(recall, event.getPointerId(0), pointerX(event), pointerY(event),
                        config, event.eventTime - event.downTime)
                    else IslandRecallGesture.onMove(recall, event.getPointerId(0), pointerX(event), pointerY(event), config)
                if (dragging) {
                    val horizontal = IslandMotion.resistance(dx, dp(56).toFloat())
                    val vertical = IslandMotion.resistance(dy, dp(48).toFloat())
                    if (!dragCaptured) {
                        if (motion != null) {
                            val fromPress = motion === pressMotion
                            downScaleX = scaleX - if (!fromPress && dragAxis == 1) IslandMotion.DRAG_STRETCH * abs(horizontal) else 0f
                            downScaleY = if (fromPress) scaleY else IslandMotion.compressionOrigin(scaleY,
                                if (dragAxis == 2) IslandMotion.DRAG_STRETCH * vertical else -IslandMotion.DRAG_SQUEEZE * abs(horizontal),
                                if (cameraGap > 0) cameraMinimumHeight() / geometryHeight.coerceAtLeast(1) else 0f)
                            downTranslationX = translationX
                            downTranslationY = translationY
                        }
                        motion?.cancel(); motion = null
                        pressMotion = null
                        cancelMediaReturn()
                        dragCaptured = true
                    }
                    updateDragVelocity(event, dx, dy)
                    // The platform tap detector was already cancelled before dispatch.
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (dragAxis == 2 && -dy >= config.intentDistancePx && abs(dx) <= -dy * config.maxHorizontalRatio &&
                            IslandRecallGesture.isTriggered(recall)) requestDismiss()
                        else if (dragAxis == 1 && (isMedia || idle) &&
                            abs(dx) >= maxOf(dp(32).toFloat(), slop * 3f) && abs(dx) > abs(dy) * 1.2f)
                            { flipDirection = if (dx < 0) -1f else 1f; submit(if (dx < 0) IslandMusicGesture.LEFT else IslandMusicGesture.RIGHT) }
                        else if (dragAxis == 2 && dy >= maxOf(dp(24).toFloat(), slop * 3f) && abs(dy) > abs(dx) * 1.2f) {
                            submit(IslandMusicGesture.DOWN)
                        }
                        isPressed = false
                        return true
                    }
                    scaleX = if (dragAxis == 1)
                        minOf(downScaleX + IslandMotion.DRAG_STRETCH * abs(horizontal),
                            maxOf(maximumWidth / geometryWidth, downScaleX)) else downScaleX
                    val compressionLimit = if (cameraGap > 0)
                        minOf(downScaleY, cameraMinimumHeight() / geometryHeight.coerceAtLeast(1)) else 0f
                    scaleY = IslandMotion.compressedScale(downScaleY,
                        if (dragAxis == 2) IslandMotion.DRAG_STRETCH * vertical else -IslandMotion.DRAG_SQUEEZE * abs(horizontal), compressionLimit)
                    translationX = downTranslationX
                    // Deform about the body's own centre. Camera clearance must
                    // never translate the whole island during a gesture.
                    translationY = downTranslationY
                    updateContour()
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                breadth.setVelocity(0f); depth.setVelocity(0f); shiftX.setVelocity(0f); shiftY.setVelocity(0f)
                cancelInputs(); return true
            }
        }
        if (!tapSuppressed) taps.onTouchEvent(event)
        return true
    }

    private fun updateDragVelocity(event: MotionEvent, dx: Float, dy: Float) {
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(1000, dp(8000).toFloat())
        val pointer = event.getPointerId(0)
        val horizontal = IslandMotion.resistanceVelocity(dx, tracker.getXVelocity(pointer), dp(56).toFloat())
        val vertical = IslandMotion.resistanceVelocity(dy, tracker.getYVelocity(pointer), dp(48).toFloat())
        val direction = if (dx < 0f) -1f else 1f
        val bound = dp(1200).toFloat()
        val widthVelocity = geometryWidth * IslandMotion.DRAG_STRETCH * horizontal * direction
        val capped = downScaleX + IslandMotion.DRAG_STRETCH * abs(IslandMotion.resistance(dx, dp(56).toFloat())) >=
            maxOf(maximumWidth / geometryWidth, downScaleX)
        breadth.setVelocity(if (dragAxis != 1 || capped && widthVelocity > 0f) 0f else widthVelocity.coerceIn(-bound, bound))
        val heightDelta = if (dragAxis == 2) IslandMotion.DRAG_STRETCH * IslandMotion.resistance(dy, dp(48).toFloat())
            else -IslandMotion.DRAG_SQUEEZE * abs(IslandMotion.resistance(dx, dp(56).toFloat()))
        val heightVelocity = IslandMotion.compressionVelocity(downScaleY, heightDelta,
            if (cameraGap > 0) minOf(downScaleY, cameraMinimumHeight() / geometryHeight.coerceAtLeast(1)) else 0f)
        depth.setVelocity(((if (dragAxis == 2) geometryHeight * IslandMotion.DRAG_STRETCH * vertical
            else if (dragAxis == 1) -geometryHeight * IslandMotion.DRAG_SQUEEZE * horizontal * direction else 0f) * heightVelocity).coerceIn(-bound, bound))
        shiftX.setVelocity(0f)
        shiftY.setVelocity(0f)
        breadthExtraVelocity = 0f
    }

    private fun finishTouch() {
        velocityTracker?.recycle(); velocityTracker = null
        if (touchActive) {
            touchActive = false
            ModuleHelper.guarded { onInteraction(false) }
        }
    }

    private fun cameraMinimumHeight() = minOf(geometryHeight.toFloat(),
        2f * abs(geometryHeight * .5f - cameraY) + cameraHeight)

    private fun animateTo(x: Float, y: Float, tx: Float, ty: Float, opacity: Float, duration: Long,
        onEnd: (() -> Unit)? = null, returning: Boolean = false, rigid: Boolean = false,
        rebound: Boolean = false, boundsMotion: Boolean = false, nativeProfile: Boolean = false,
        ringerShake: Boolean = false, interaction: Boolean = false, arriving: Boolean = false) {
        if (!interaction) { pressedBody = false; pressMotion = null }
        if (!returning) cancelMediaReturn()
        val carry = motion != null || dragCaptured
        motion?.cancel(); motion = null
        if (disposed) return
        if (powerSaveMotion || !ValueAnimator.areAnimatorsEnabled()) {
            commitContent()
            clearContentEffects()
            cancelMediaReturn()
            scaleX = x; scaleY = y; translationX = tx; translationY = ty; alpha = opacity
            textReveal = 1f
            layerReveal = 1f
            updateContour(); onEnd?.invoke()
            return
        }
        val nativeDriven = returning && nativeArrival && returnProgress != null
        // The accepted Home request owns the receive stages. The optional
        // window-progress mapping only coordinates the app surface/wait clock.
        val accepting = returning && nativeArrival
        // A final exit keeps its established bounded fade/seed handoff. Applying
        // the longer content clock here leaves a translucent seed after music ends.
        val iosContent = (nativeProfile || interaction) && !leaving
        // Window acceptance owns its body axes, while content still uses the
        // shared slot/snapshot channels. A missing native progress channel must
        // not silently restore the legacy catch wave in iOS mode.
        val experimental = iosContent && !nativeDriven
        if (!iosContent) { outgoing?.clear(); outgoingReveal = 0f; outgoingFresh = false }
        val bodyResponse = if (ringerShake && experimental)
            IslandRingerResponse(resources.displayMetrics.density, layoutDirection == LAYOUT_DIRECTION_RTL) else null
        var milliseconds = if (nativeDriven) duration + IslandMotion.REBOUND
            else (duration * motionScale.coerceIn(.65f, 1f)).toLong()
        // Native Acceptance reaches Accepted after .12s + .08s; this is time,
        // not a fraction of the independently owned Android window trajectory.
        var contentStart: Long? = if (nativeDriven) null else 0L
        val seconds = (if (nativeDriven) IslandMotion.ABSORPTION_BODY else milliseconds) / 1000f
        if (carry && !rigid) breadth.addVelocity(breadthExtraVelocity)
        breadthExtraVelocity = 0f
        breadth.retarget(geometryWidth * scaleX, geometryWidth * x, seconds, carry && !rigid, rebound,
            if (boundsMotion && (x - scaleX) * geometryWidth > 1f) IslandMotion.FORM_DAMPING else 1.0)
        depth.retarget(geometryHeight * scaleY, geometryHeight * y, seconds, carry && !rigid, rebound,
            if (boundsMotion && (y - scaleY) * geometryHeight > 1f) IslandMotion.FORM_DAMPING else 1.0)
        // Preserve an interrupted wider body instead of clamping its first frame.
        val maximumBreadth = maxOf(maximumWidth, geometryWidth * scaleX)
        // Native +26 belongs to its container request, not an unconditional dp
        // addition on A14's compact body. Keep the established safe body envelope.
        val maximumHeight = maxOf(maxOf(dp(44), geometryHeight) * 1.14f,
            geometryHeight * maxOf(scaleY, y))
        val restingMinimum = if ((rebound || nativeDriven) && cameraGap > 0)
            minOf(cameraMinimumHeight(), geometryHeight * scaleY)
            else minOf(cameraDiameter, geometryHeight.toFloat(), geometryHeight * scaleY)
        // Native acceptance has an early positive stretch. It does not justify
        // the former extra negative pulse after horizontal settling. Positive
        // acceptance height belongs to its explicit, independently retargeted axis.
        val minimumHeight = restingMinimum
        // Preserve the recovered +16 horizontal request. On an existing compact
        // A14 body, map the vertical request to the same relative excursion;
        // a new camera seed keeps its ordinary height transition entirely.
        val receiveHeightScale=if(receivingExisting)
            minOf(1f,geometryHeight*(16f/26f)/geometryWidth.coerceAtLeast(1)) else 0f
        val absorptionResponse = if (accepting) IslandAcceptanceResponse(
            geometryWidth * scaleX, geometryHeight * scaleY, geometryWidth * x, geometryHeight * y,
            breadth.velocity, depth.velocity, resources.displayMetrics.density,
            minOf(compactWidth(), geometryWidth * scaleX), maximumBreadth, minimumHeight, maximumHeight,
            heightScale=receiveHeightScale) else null
        val arrivalBreadth=absorptionResponse?.breadth ?: if(arriving && iosContent && !idle && !interaction)
            IslandArrivalBreadth(geometryWidth*scaleX,geometryWidth*x,breadth.velocity,resources.displayMetrics.density,
                minOf(compactWidth(),geometryWidth*scaleX),maximumBreadth) else null
        shiftX.retarget(translationX, tx, seconds, carry && !rigid, rebound)
        // A content handoff may interrupt a top exit. Keep its visible origin,
        // without inheriting the old exit's upward travel into a new activity.
        if (returning) shiftY.setVelocity(0f)
        shiftY.retarget(translationY, ty, seconds, carry && !rigid, rebound)
        this.opacity.retarget(alpha, opacity, seconds, carry)
        // A single live content layer is not an iOS snapshot. Its short visual
        // handoff must not wait for the old spring's numerical settling horizon.
        if (experimental) {
            val shrinking = geometryWidth * (scaleX - x) > 1f || geometryHeight * (scaleY - y) > 1f
            val profile = when { interaction -> IslandMotion.IOS_PRESS; idle -> IslandMotion.IOS_IDLE
                shrinking -> IslandMotion.IOS_COLLAPSE; else -> IslandMotion.IOS_BOUNDS }
            val dx = geometryWidth * (scaleX - x); val dy = geometryHeight * (scaleY - y)
            val vx = breadth.velocity; val vy = depth.velocity
            val px = shiftX.velocity; val py = shiftY.velocity; val av = this.opacity.velocity
            breadth.retarget(geometryWidth * scaleX, geometryWidth * x, profile, carry)
            depth.retarget(geometryHeight * scaleY, geometryHeight * y, profile, carry)
            shiftX.retarget(translationX, tx, profile, carry)
            shiftY.retarget(translationY, ty, profile, carry)
            this.opacity.retarget(alpha, opacity, IslandMotion.IOS_BOUNDS, carry)
            milliseconds = maxOf(profile.horizon(dx, vx), profile.horizon(dy, vy),
                profile.horizon(translationX - tx, px), profile.horizon(translationY - ty, py),
                IslandMotion.IOS_BOUNDS.horizon(alpha - opacity, av, .002, .02))
        }
        // Generic content/bounds timing must not truncate the receive recoil.
        milliseconds=maxOf(milliseconds,absorptionResponse?.durationMillis ?: 0L,arrivalBreadth?.durationMillis ?: 0L)
        val contentDelay = if (nativeDriven && pendingContent != null) 200L else 0L
        if (iosContent) {
            val swapping = pendingContent != null
            if (swapping) {
                // One frozen display list holds the old pixels while new native
                // views enter immediately. Rapid replacements retain the more
                // visible side, never queue history or allocate a third layer.
                outgoingOpacity.retarget(outgoingReveal, 0f, IslandMotion.IOS_SNAPSHOT, carry && !outgoingFresh)
                outgoingFresh = false
                commitContent()
                layerReveal = 0f
                contentFromInert = false
            }
            val contentProfile = if (displayedContent?.expanded == true) IslandMotion.IOS_CONTENT else IslandMotion.IOS_SLOTS
            val contentTime = contentProfile.horizon(if (swapping) 1f else 1f - layerReveal,
                if (swapping) 0f else contentOpacity.velocity, .002, .02)
            contentOpacity.retarget(layerReveal, 1f, contentProfile, carry && !swapping)
            milliseconds = maxOf(milliseconds, contentDelay + contentTime, contentDelay + if (outgoing?.ready == true)
                IslandMotion.IOS_SNAPSHOT.horizon(outgoingReveal, outgoingOpacity.velocity, .002, .02) else 0L)
            albumFlip = false
        }
        val bodyStart = if (bodyResponse != null && (alpha < .5f || scaleX < .75f)) .16f else 0f
        val formMilliseconds = milliseconds
        if (bodyResponse != null) milliseconds = maxOf(milliseconds, bodyResponse.durationMillis + (bodyStart * 1000).toLong())
        var layerFrom = layerReveal
        val fadeOut = if (pendingContent != null) minOf(contentFadeRemaining, milliseconds * 46 / 100) else 0L
        val fadeIn = minOf(180L, milliseconds - fadeOut).coerceAtLeast(1L).toFloat()
        val flipIn = minOf(160L, milliseconds - fadeOut).coerceAtLeast(1L).toFloat()
        var revealAt = 0L
        var textAt: Long? = if (contentFits() && text.alpha > 0f) -130L else null
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = milliseconds
            interpolator = LinearInterpolator()
            addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
                override fun onAnimationUpdate(animation: ValueAnimator) = ModuleHelper.guarded {
                    if (motion !== animation) return@guarded
                    val time = animation.currentPlayTime / 1000f
                    bodyResponse?.sample((time - bodyStart).coerceAtLeast(0f))
                    val nativeProgress = if (nativeDriven) returnProgress?.read() else null
                    val progress = if (nativeDriven) nativeProgress?.takeIf { p -> p >= 0f }?.coerceIn(0f, 1f) ?: 1f
                        else (animation.currentPlayTime.toFloat() / formMilliseconds.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    if (contentStart == null && animation.currentPlayTime >= 200L) contentStart = 200L
                    val contentTime = animation.currentPlayTime - (contentStart ?: animation.currentPlayTime)
                    absorptionResponse?.sample(time)
                    if(absorptionResponse==null) arrivalBreadth?.sample(time)
                    val physicalDepth = absorptionResponse?.height ?: if (experimental) depth.sample(time).coerceIn(minimumHeight, maximumHeight)
                        else depth.sample(time, minimumHeight, maximumHeight)
                    if (absorptionResponse != null) depth.setVelocity(absorptionResponse.velocityHeight)
                    scaleY = physicalDepth / geometryHeight.coerceAtLeast(1)
                    val physicalBreadth = if (arrivalBreadth != null) {
                        val width = arrivalBreadth.width
                        breadth.setVelocity(arrivalBreadth.velocity)
                        breadthExtraVelocity = 0f
                        width
                    } else {
                        val springBreadth = if (experimental) breadth.sample(time) else breadth.sample(time, compactWidth(), maximumBreadth)
                        // Content/Home receive owns its explicit width axis.
                        // Other callers have no receive wave; retain their original
                        // bounds and let ringer feedback carry its own velocity below.
                        breadthExtraVelocity = 0f
                        springBreadth.coerceIn(compactWidth(), maximumBreadth)
                    }
                    val bodyWidth = bodyResponse?.width ?: 0f
                    scaleX = minOf(maximumBreadth, physicalBreadth + bodyWidth) / geometryWidth.coerceAtLeast(1)
                    if (bodyResponse != null) breadthExtraVelocity =
                        if (physicalBreadth + bodyWidth < maximumBreadth) bodyResponse.velocityWidth else 0f
                    if (returning && mediaReturnProgress < 1f) {
                        mediaReturnProgress = progress
                        (parent as? View)?.invalidate()
                    }
                    translationX = shiftX.sample(time) + (bodyResponse?.x ?: 0f)
                    if (bodyResponse != null) shiftX.setVelocity(shiftX.velocity + bodyResponse.velocityX)
                    translationY = shiftY.sample(time) + (absorptionResponse?.y ?: 0f)
                    if (absorptionResponse != null) shiftY.setVelocity(shiftY.velocity + absorptionResponse.velocityY)
                    if (iosContent && opacity > 0f) {
                        val slotTime = (animation.currentPlayTime - contentDelay).coerceAtLeast(0L) / 1000f
                        layerReveal = contentOpacity.sample(slotTime).coerceIn(0f, 1f)
                        if (outgoing?.ready == true) {
                            outgoingReveal = outgoingOpacity.sample(slotTime).coerceIn(0f, 1f)
                            outgoing?.pose(outgoingReveal)
                            if (outgoingReveal <= .001f) outgoing?.clear()
                            invalidate()
                        }
                    } else if (opacity > 0f) {
                        if (pendingContent != null) {
                            contentFadeRemaining = (fadeOut - contentTime).coerceAtLeast(0L)
                            layerReveal = layerFrom * (contentFadeRemaining.toFloat() / fadeOut.coerceAtLeast(1L))
                            if (contentFadeRemaining == 0L) {
                                commitContent()
                                layerFrom = 0f; revealAt = fadeOut; textAt = null
                            }
                        }
                        if (pendingContent == null) layerReveal = layerFrom + (1f - layerFrom) *
                            ((contentTime - revealAt) / fadeIn).coerceIn(0f, 1f)
                    }
                    alpha = if (experimental) this@IslandActivityView.opacity.sample(time).coerceIn(0f, 1f)
                        else this@IslandActivityView.opacity.sample(time, 0f, 1f)
                    if (albumFlip) {
                        icon.cameraDistance = dp(640).toFloat()
                        icon.rotationY = flipDirection * if (pendingContent != null)
                            90f * (1f - contentFadeRemaining.toFloat() / fadeOut.coerceAtLeast(1L))
                            else -90f * (1f - ((contentTime - revealAt) / flipIn).coerceIn(0f, 1f))
                    }
                    if (iosContent) textReveal = 1f
                    else if (contentFits()) {
                        val start = textAt ?: animation.currentPlayTime.also { began -> textAt = began }
                        textReveal = ((animation.currentPlayTime - start).toFloat() /
                            minOf(130L, milliseconds - start).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    } else { textAt = null; textReveal = 0f }
                    updateContour()
                    if (nativeDriven && progress >= 1f && pendingContent == null && layerReveal >= 1f &&
                        (absorptionResponse?.settled(time) ?: (time >= seconds))) {
                        // end() synchronously emits another frame on A14. Retire this
                        // clock's update listener before entering its terminal callbacks.
                        animation.removeUpdateListener(this)
                        animation.end()
                    }
                }
            })
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) = ModuleHelper.guarded {
                    if (motion !== animation) return@guarded
                    motion = null
                    if (!cancelled && !disposed) {
                        commitContent()
                        breadth.finish(); depth.finish(); shiftX.finish(); shiftY.finish(); this@IslandActivityView.opacity.finish()
                        if (iosContent) contentOpacity.finish()
                        breadthExtraVelocity = 0f
                        scaleX = x; scaleY = y; translationX = tx; translationY = ty; alpha = opacity
                        textReveal = 1f
                        layerReveal = 1f
                        clearContentEffects()
                        cancelMediaReturn()
                        updateContour(); onEnd?.invoke()
                    }
                }
            })
            motion = this
            start()
        }
    }

    private fun resetTransform(preserveReturn: Boolean = false) {
        commitContent()
        clearContentEffects()
        breadthExtraVelocity = 0f
        if (!preserveReturn) cancelMediaReturn()
        scaleX = 1f; scaleY = 1f; translationX = 0f; translationY = 0f; alpha = 1f
        textReveal = 1f
        layerReveal = 1f
        updateContour()
    }

    private fun updateContour() {
        // Compensate the drawing transform. The physical shell changes width while text,
        // square artwork and wave bars retain their own aspect ratios, including drags.
        val sx = scaleX.coerceAtLeast(.01f); val sy = scaleY.coerceAtLeast(.01f)
        val squish = if (contentFromInert && displayedContent?.expanded != true) 1f - layerReveal else 0f
        val direction = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f else -1f
        left.pivotX = left.width * .5f
        left.scaleX = 1f - squish
        left.translationX = direction * squish * left.width * .5f
        icon.scaleX = 1f / sx; icon.scaleY = 1f / sy
        identityTitle.scaleX = 1f / sx; identityTitle.scaleY = 1f / sy
        text.scaleX = (1f - squish) / sx; text.scaleY = 1f / sy
        wave.scaleX = (1f - squish) / sx; wave.scaleY = 1f / sy
        // Keep a physical 16dp margin instead of stretching target-layout coordinates.
        // Counter-scaling alone pushes the text into an end cap while lengthening.
        val edge = dp(IslandSizing.EDGE_DP)
        icon.translationX = (edge + iconSize * .5f) * (1f / sx - 1f) + opticalX * iconSize / sx
        icon.translationY = opticalY * iconSize / sy
        identityTitle.translationX = (edge + (if (icon.visibility == VISIBLE) iconSize else 0) + dp(8)) * (1f / sx - 1f)
        wave.translationX = (if (recording) dp(28) else -dp(28)) * (1f / sx - 1f)
        wave.translationX += (if (recording) direction else -direction) * squish * wave.width * .5f / sx
        val labelRoom = captionRoom(geometryWidth, cameraGap)
        val reveal = ((alpha - .12f) / .88f).coerceIn(0f, 1f) * layerReveal
        val iconRoom = if (cameraGap > 0) IslandContentLayout.leading(geometryWidth * sx, cameraGap, cameraOffset, translationX) >= edge + iconSize + maxOf(0f, opticalX * iconSize)
            else geometryWidth * sx >= 2 * (edge + iconSize)
        left.alpha = if (iconRoom && geometryHeight * sy >= iconSize) reveal else 0f
        val identityLane = if (cameraGap > 0) IslandContentLayout.leading(geometryWidth * sx, cameraGap, cameraOffset, translationX)
            else (geometryWidth * sx - dp(8)) * .5f
        val identityInset = edge + (if (icon.visibility == VISIBLE) iconSize else 0) + dp(8) + left.paddingEnd
        val identityRoom = identityLane - identityInset
        run {
            // Reveal the same prepared text through the currently available lane.
            // Full-width fit was a binary switch: new labels could suddenly pop
            // in after their opacity spring had already advanced while hidden.
            val room = if (cameraGap > 0)
                IslandContentLayout.trailing(geometryWidth * sx, cameraGap, -cameraOffset * direction, -translationX * direction) - edge
                else if (identityTitle.visibility == VISIBLE) (geometryWidth * sx - dp(8)) * .5f - edge
                else geometryWidth * sx - 2 * edge - iconSize - dp(8)
            val lane = room.coerceIn(0f, labelRoom.toFloat())
            if (cameraGap > 0) {
                // Pin the inner edge to the physical camera reservation. Counter-
                // scaling alone anchors the outer cap and adds empty text space.
                text.translationX = (cameraOffset - direction * cameraGap * .5f - translationX) / sx +
                    direction * (geometryWidth * .5f - edge - labelRoom)
                if (direction < 0f) captionClip.set(0, 0, lane.toInt(), geometryHeight)
                else captionClip.set(kotlin.math.ceil(labelRoom - lane).toInt(), 0, labelRoom, geometryHeight)
            } else {
                text.translationX = -(edge + labelRoom * .5f) * (1f / sx - 1f) -
                    direction * squish * labelRoom * .5f / sx
                captionClip.set(kotlin.math.ceil(labelRoom - lane).toInt(), 0, labelRoom, geometryHeight)
            }
            text.clipBounds = captionClip
            identityClip.set(0, 0, identityRoom.toInt().coerceIn(0, identityTitle.width), geometryHeight)
            identityTitle.clipBounds = identityClip
            identityTitle.alpha = reveal * textReveal
        }

        text.alpha = reveal * textReveal
        val waveRoom = if (recording) iconRoom else if (cameraGap > 0) IslandContentLayout.trailing(geometryWidth * sx, cameraGap, cameraOffset, translationX) >= edge + iconSize else iconRoom
        wave.alpha = if (waveRoom && geometryHeight * sy >= waveHeight) reveal else 0f
        if (shape.resize(geometryWidth, geometryHeight, sx, sy)) invalidateOutline()
        // Stop immediately when this frame removes the lane; pre-draw resolves actual
        // clipping and activation once, after layout and all property updates settle.
        if (wave.alpha <= 0f || wave.visibility != VISIBLE) wave.outputVisible = false
    }

    fun cancelNativeArrival() { if (nativeArrival) cancelMediaReturn() }

    fun cancelMediaReturn() {
        acceptReturnProgress(null)
        nativeArrival = false
        receivingExisting = false
        if (mediaReturnProgress >= 1f) return
        mediaReturnProgress = 1f
        (parent as? View)?.invalidate()
        updateContour()
    }

    private fun clearContentEffects() {
        albumFlip = false
        icon.rotationY = 0f
        contentFromInert = false
        outgoing?.clear(); outgoingReveal = 0f; outgoingFresh = false
    }

    private fun prepareOutgoing() {
        outgoingFresh = false
        if (outgoing?.ready == true && layerReveal < .5f) return
        // Bind precedes the next geometry/layout commit. Capture here, while
        // actual child bounds and the shell's presented scale still agree.
        if (isHardwareAccelerated) {
            val snapshot = outgoing ?: IslandContentSnapshot().also { outgoing = it }
            outgoingFresh = snapshot.capture(this, contentLayer, scaleX, scaleY)
        }
        outgoingReveal = if (outgoingFresh) 1f else 0f
        if (outgoingFresh) {
            outgoing?.pose(1f)
        }
    }

    private fun contentFits(): Boolean {
        val edge = dp(IslandSizing.EDGE_DP)
        val targetRoom = captionRoom(geometryWidth, cameraGap)
        val side = if (layoutDirection == LAYOUT_DIRECTION_RTL) -1f else 1f
        val room = if (cameraGap > 0) IslandContentLayout.trailing(geometryWidth * scaleX, cameraGap, cameraOffset * side, translationX * side) - edge
            else if (identityTitle.visibility == VISIBLE) (geometryWidth * scaleX - dp(8)) * .5f - edge
            else geometryWidth * scaleX - 2 * edge - iconSize - dp(8)
        return room + .01f >= minOf(labelWidth, targetRoom.toFloat())
    }

    private fun captionRoom(width: Int, gap: Int, height: Int = geometryHeight,
        split: Boolean = identityTitle.visibility == VISIBLE, offset: Float = cameraOffset) = (if (gap > 0)
        IslandContentLayout.trailing(width.toFloat(), gap, if (layoutDirection == LAYOUT_DIRECTION_RTL) -offset else offset, 0f).toInt() - dp(IslandSizing.EDGE_DP)
        else if (split) (width - dp(8)) / 2 - dp(IslandSizing.EDGE_DP)
        else width - 2 * dp(IslandSizing.EDGE_DP) - iconSizeForHeight(height) - dp(8)).coerceAtLeast(0)
    private fun horizontalPivot(width: Int) = width * .5f

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateContour()
    }

    private fun topTravel() = (windowTop + geometryHeight + dp(8)).coerceAtLeast(geometryHeight).toFloat()
    override fun onAttachedToWindow() { disposed = false; super.onAttachedToWindow() }
    override fun onDetachedFromWindow() {
        title.stop()
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

    override fun dispatchDraw(canvas: Canvas) {
        outgoing?.draw(canvas, geometryWidth, geometryHeight, scaleX.coerceAtLeast(.01f), scaleY.coerceAtLeast(.01f))
        super.dispatchDraw(canvas)
        if (privacy != 0 && !idle) {
            // Reserved camera padding, never over the cutout or a second overlay window.
            val x = if (cameraGap > 0) width * .5f + cameraOffset + cameraGap * .5f - dp(4)
                else width - dp(6).toFloat()
            privacyPaint.color = if (privacy and IslandPrivacyAccess.CAMERA != 0) 0xff32d074.toInt() else 0xffffa52d.toInt()
            canvas.drawCircle(x, height * .5f, dp(2).toFloat(), privacyPaint)
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS)
        if (isMedia || idle) {
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
            requestDismiss(); return true
        }
        val index = accessibilityActions.indexOf(action)
        if (index >= 0) {
            val gesture = IslandMusicGesture.entries[index]
            if ((!isMedia && !idle) || musicActions.resolve(gesture, controlsEnabled) == 1) return false
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
    private var phaseAt = 0L
    private val energies = FloatArray(5)
    private val targets = FloatArray(5)
    private val measured = FloatArray(5)
    private var settling = false
    private var lastFrame = 0L
    private var real = false
    private var durationScaleListening = false
    // Native main-thread notification: a stopped synthetic clock has no audio event to wake it.
    private val durationScaleListener = ValueAnimator.DurationScaleChangeListener {
        if (durationScaleListening) {
            phaseAt = android.os.SystemClock.uptimeMillis()
            updateRendering()
            invalidate()
        }
    }
    var outputVisible = false
        set(value) { if (field != value) { field = value; updateRendering(); if (value) invalidate() } }
    var gainPercent = 200
        set(value) {
            val next = value.coerceIn(100, 800)
            if (field == next) return
            field = next
            if (real) applyLevels(false)
        }
    fun spectrum(values: FloatArray?) {
        if (values == null && !real) return
        val changedMode = real != (values != null)
        if (changedMode) phaseAt = android.os.SystemClock.uptimeMillis()
        real = values != null
        for (index in 0..4) measured[index] = values?.get(index) ?: 0f
        applyLevels(changedMode)
    }
    private fun applyLevels(changedMode: Boolean) {
        settling = false
        val immediate = real && !ValueAnimator.areAnimatorsEnabled()
        var snapped = false
        for (index in 0..4) {
            val raw = measured[index]
            // Gate quantisation before applying user gain. Silence never gains a fake beat.
            val amplified = raw.coerceIn(0f, 1f) * gainPercent / 100f
            val next = if (!raw.isFinite() || raw <= 0f) 0f
                else amplified / kotlin.math.sqrt(1f + amplified * amplified)
            if (changedMode || abs(next - targets[index]) >= .006f || next == 0f) targets[index] = next
            if (changedMode || immediate) {
                if (energies[index] != targets[index]) snapped = true
                energies[index] = targets[index]
            }
            if (abs(targets[index] - energies[index]) > .006f) settling = true
        }
        updateRendering()
        if ((changedMode || snapped) && outputVisible) invalidate()
    }
    var playing = false
        set(value) {
            if (field == value) return
            field = value; phaseAt = android.os.SystemClock.uptimeMillis()
            updateRendering(); if (outputVisible) invalidate()
        }
    var holding = false
        set(value) { if (field != value) { field = value; if (outputVisible) invalidate() } }
    private val frame = object : Runnable {
        override fun run() = ModuleHelper.guarded {
            scheduled = false
            if (!shouldRender()) {
                // Disabling animation stops interpolation, not the latest measured state.
                if (real && !ValueAnimator.areAnimatorsEnabled()) applyLevels(false)
                else updateRendering()
                return@guarded
            }
            val now = android.os.SystemClock.uptimeMillis()
            val dt = ((now - lastFrame) / 1000f).coerceIn(.001f, .1f)
            lastFrame = now
            if (real) {
                // One finite display envelope bridges the slower source cadence.
                val attack = (1.0 - kotlin.math.exp(-dt / .072)).toFloat()
                val release = (1.0 - kotlin.math.exp(-dt / .216)).toFloat()
                settling = false
                for (index in 0..4) {
                    val difference = targets[index] - energies[index]
                    val next = energies[index] + difference * (if (difference > 0f) attack else release)
                    energies[index] = if (abs(targets[index] - next) <= .006f) targets[index] else next
                    if (energies[index] != targets[index]) settling = true
                }
            } else {
                phase = (phase + (now - phaseAt) / 1000f * 4.35f) % 6.283185f
                phaseAt = now
            }
            invalidate()
            schedule()
        }
    }

    private fun shouldRender() = outputVisible && alpha > 0f && (!real || settling) && playing && aggregatedVisible && isAttachedToWindow &&
        ValueAnimator.areAnimatorsEnabled()
    private fun schedule() {
        if (!shouldRender()) { lastFrame = 0L; return }
        if (!scheduled) {
            if (lastFrame == 0L) lastFrame = android.os.SystemClock.uptimeMillis()
            scheduled = true
            postOnAnimation(frame)
        }
    }
    private fun updateRendering() {
        val listen = !real && outputVisible && alpha > 0f && playing && aggregatedVisible && isAttachedToWindow
        if (listen != durationScaleListening) {
            if (listen) ValueAnimator.registerDurationScaleChangeListener(durationScaleListener)
            else ValueAnimator.unregisterDurationScaleChangeListener(durationScaleListener)
            durationScaleListening = listen
        }
        if (shouldRender()) schedule() else { removeCallbacks(frame); scheduled = false; lastFrame = 0L }
    }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        aggregatedVisible = isVisible
        updateRendering()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateRendering() }
    override fun onDetachedFromWindow() {
        outputVisible = false
        removeCallbacks(frame)
        scheduled = false
        lastFrame = 0L
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = width / 5f
        val barWidth = step * 0.56f
        for (index in 0..4) {
            val fraction = if (real && (playing || holding)) .12f + .83f * energies[index]
                else if (playing || holding) 0.25f + 0.65f * abs(sin(phase + index * 1.4f)) else 0.22f
            val h = height * fraction
            val x = step * index + (step - barWidth) * 0.5f
            canvas.drawRoundRect(x, (height - h) * 0.5f, x + barWidth, (height + h) * 0.5f,
                barWidth * 0.5f, barWidth * 0.5f, paint)
        }
    }
}
