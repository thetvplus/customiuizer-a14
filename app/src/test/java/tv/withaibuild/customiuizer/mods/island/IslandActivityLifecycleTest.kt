package tv.withaibuild.customiuizer.mods.island

import android.content.Context
import android.app.Activity
import android.widget.FrameLayout
import android.content.ContextWrapper
import android.graphics.Rect
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Looper
import android.view.View
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1080dp-h2400dp-mdpi", shadows = [IslandLaunchPackageManagerShadow::class])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class IslandActivityLifecycleTest {
    private var owner: IslandActivityController? = null
    private var adds = 0
    private var removes = 0
    private var windowUpdates = 0
    private var reject = false
    private var shell: IslandActivityView? = null
    private var latestParams: WindowManager.LayoutParams? = null
    private var firstAlpha = 1f
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val native = Native()
    private lateinit var host: Activity
    private lateinit var frame: FrameLayout
    private var drawFrames = true
    private var onAdd: (() -> Unit)? = null
    private var metricsWidth = 1080
    private var metricsHeight = 2400
    private var metricsReads = 0
    private var metricsInsets = WindowInsets.Builder().build()
    private val window = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java)) { _, method, args ->
        when (method.name) {
            "getCurrentWindowMetrics" -> { metricsReads++; WindowMetrics(Rect(0, 0, metricsWidth, metricsHeight), metricsInsets) }
            "addView" -> { if (reject) throw WindowManager.BadTokenException("injected"); shell = (args!![0] as IslandTouchWindow).shell; latestParams = args[1] as WindowManager.LayoutParams; firstAlpha = shell!!.alpha; adds++;
                if (!drawFrames) shell!!.visibility = View.INVISIBLE
                frame.addView(args[0] as View, FrameLayout.LayoutParams(latestParams!!.width, latestParams!!.height)); onAdd?.invoke(); null }
            "removeViewImmediate" -> { frame.removeView(args!![0] as View); removes++; null }
            "updateViewLayout" -> { val params = args!![1] as WindowManager.LayoutParams
                windowUpdates++
                (args[0] as View).layoutParams = FrameLayout.LayoutParams(params.width, params.height); null }
            "toString" -> "IslandTestWindow"
            else -> error("Unexpected WindowManager call: ${method.name}, ${args?.size}")
        }
    } as WindowManager
    private val context get() = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getSystemService(name: String): Any? = if (name == Context.WINDOW_SERVICE) window else super.getSystemService(name)
    }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        frame = FrameLayout(host)
        host.setContentView(frame)
    }
    @After fun cleanup() {
        owner?.dispose(); scheduler.runCurrent(); host.finish(); Dispatchers.resetMain()
        org.robolectric.shadows.ShadowChoreographer.setPaused(false)
    }

    private fun controller(media: Boolean = false, width: Int = 328, simplified: Boolean = false,
        chat: Boolean = false, chatExcluded: Set<String> = emptySet(),
        notificationApps: Set<String> = setOf(context.packageName),
        waveformMode: Int = 1, spectrumBackend: IslandAudioSpectrum.Backend? = null,
        mediaReturn: Boolean = false, mediaReturnApps: Set<String> = emptySet(),
        hideFloating: Boolean = false, freeform: IslandFreeformPort? = null,
        pipStateReader: (() -> IslandPipSnapshot?)? = null,
        io: kotlinx.coroutines.CoroutineDispatcher = dispatcher,
        entranceStyle: IslandEntranceStyle = IslandEntranceStyle.TOP,
        geometryHost: View? = null, pipCandidate: IslandPipCandidatePort? = null,
        height: Int = 40, automaticWidth: Boolean = true, shortWidth: Int = 184, longWidth: Int = 240,
        idle: Boolean = false, liveKinds: Set<String> = emptySet()): IslandActivityController {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3"); put("system_toast_capsule", true)
            put("system_island_media", media); put("system_island_controls", true)
            put("system_island_expanded_width", width)
            put("system_island_height", height); put("system_island_width_auto", automaticWidth)
            put("system_island_idle", idle)
            put("system_island_live_kinds", liveKinds)
            put("system_island_width_short", shortWidth); put("system_island_width_medium", longWidth)
            put("system_island_simplified", simplified)
            put("system_island_chat", chat)
            put("system_island_chat_excluded_apps", chatExcluded)
            put("system_island_notification_apps", notificationApps)
            put("system_island_waveform_mode", waveformMode.toString())
            put("system_island_media_return", mediaReturn)
            put("system_island_media_return_apps", mediaReturnApps)
            put("system_island_hide_in_floating_window", hideFloating)
            put("system_island_entrance_style", entranceStyle.ordinal.toString())
        }
        return IslandActivityController(context, prefs, IslandOptions.read(prefs), native, io, spectrumBackend = spectrumBackend,
            freeform = freeform, pipStateReader = pipStateReader, geometryHost = geometryHost, pipCandidate = pipCandidate).also {
            owner = it
            it.connectSafety(Any())
            it.refreshFloatingWindows()
            drain()
        }
    }

    @Test fun disposedOrForeignThreadReturnAlwaysReleasesTheIncomingProgressMapping() {
        val controller = controller()
        val frame = IslandMusicReturnSignal.Frame(context.packageName, android.os.UserHandle.getUserHandleForUid(1000),
            100f, 10f, 200f, 50f, 1080, 2400)
        val foreign = IslandReturnProgress(java.nio.ByteBuffer.allocateDirect(4))
        Thread { controller.nativeMusicReturn(frame, progress = foreign) }.apply { start(); join() }
        assertEquals(-1f, foreign.read(), 0f)
        val late = IslandReturnProgress(java.nio.ByteBuffer.allocateDirect(4))
        controller.dispose(); drain()
        controller.nativeMusicReturn(frame, progress = late)
        assertEquals(-1f, late.read(), 0f)
        assertEquals(0, adds)
    }

    @Test fun foregroundActivityKeepsItsLandingTargetAndMorphsFromTheExistingIslandWithoutCapture() {
        val controller = controller(idle = true, liveKinds = setOf("stopwatch"))
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        val activity = IslandLiveActivity(IslandLiveKind.STOPWATCH, IslandLiveKind.CLOCK_PACKAGE, 10345,
            IslandLiveClock(android.os.SystemClock.elapsedRealtime(), false), session = 9)
        controller.foregroundChanged(activity.packageName)
        registry.update(activity.kind, activity)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertTrue("The source app owns the activity; only the optional idle shell remains", shell!!.idle)
        val target = org.robolectric.util.ReflectionHelpers.callInstanceMethod<IslandMusicReturnSignal.Frame?>(controller,
            "liveReturnFrame", org.robolectric.util.ReflectionHelpers.ClassParameter.from(java.lang.Long.TYPE, android.os.SystemClock.uptimeMillis()))!!
        assertEquals(activity.packageName, target.packageName)
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        controller.nativeMusicReturn(target); drain()
        assertTrue(shell!!.idle)
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        assertFalse(shell!!.idle)
        assertEquals(1, adds)
        val arrival = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion")
        assertNotNull("The already-visible idle island must morph to the activity", arrival)
        assertEquals(IslandMotion.CONTENT_ARRIVAL, arrival.duration)
        assertEquals(0f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertSame("A repeated render cannot restart the same arrival", arrival,
            org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
        arrival.end()
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
        registry.update(activity.kind, activity.copy(clock = activity.clock!!.copy(base = activity.clock.base - 1000)))
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
        assertEquals(1f, shell!!.scaleX, .0001f)
    }

    @Test fun delayedNativeConfirmationCannotReplayTheSameForegroundAdmission() {
        val controller = controller(idle = true, liveKinds = setOf("stopwatch"))
        controller.foregroundChanged(IslandLiveKind.CLOCK_PACKAGE)
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        val source = IslandLiveActivity(IslandLiveKind.STOPWATCH, IslandLiveKind.CLOCK_PACKAGE, 10345,
            IslandLiveClock(0, false), session = 10)
        registry.update(source.kind, source)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        val target = org.robolectric.util.ReflectionHelpers.callInstanceMethod<IslandMusicReturnSignal.Frame?>(controller,
            "liveReturnFrame", org.robolectric.util.ReflectionHelpers.ClassParameter.from(java.lang.Long.TYPE, android.os.SystemClock.uptimeMillis()))!!
        val committedAt = android.os.SystemClock.uptimeMillis()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        assertTrue("Home is not confirmed while the source is only transitioning", shell!!.idle)
        controller.nativeMusicReturn(target, committedAt); drain()
        assertFalse(shell!!.idle)
        val arrival = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion")
        assertNotNull(arrival)
        controller.nativeMusicReturn(target, committedAt); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5))
        controller.nativeMusicReturn(target, committedAt); drain()
        assertSame("Duplicate confirmations cannot replace an in-flight arrival", arrival,
            org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
        arrival.end()
        controller.nativeMusicReturn(target, committedAt); drain()
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
        assertEquals(1f, shell!!.scaleX, 0f)
        assertEquals(1, adds)
    }

    @Test fun recordingToastHandsBackToTheSameRecordingOnHomeAndItsLateLeaseCannotCoverItAgain() {
        val controller = controller(simplified = true, liveKinds = setOf("recorder"))
        val source = IslandLiveActivity(IslandLiveKind.RECORDER, IslandLiveKind.RECORDER_PACKAGE,
            context.applicationInfo.uid, IslandLiveClock(0, false), session = 17)
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        controller.foregroundChanged(source.packageName); registry.update(source.kind, source)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertEquals(0, adds)
        val toast = IslandToastContent("已静音", source.packageName, null)
        val lease = controller.showToast(toast, 3500)
        assertNotNull(lease)
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        val state = org.robolectric.util.ReflectionHelpers.getField<IslandActivityState<IslandToastContent, IslandMediaContent>>(controller, "state")
        assertEquals(source.key, state.activity(android.os.SystemClock.uptimeMillis())?.liveActivity?.key)
        assertNull(controller.showToast(toast, 3500))
        controller.releaseToast(lease!!); drain()
        assertEquals(source.key, state.activity(android.os.SystemClock.uptimeMillis())?.liveActivity?.key)
        assertEquals(1, adds)
    }

    @Test fun pendingRecorderMuteFeedbackDoesNotArriveAfterTheRecordingBoards() = recorderMuteHandoff(false)
    @Test fun visibleRecorderMuteFeedbackHandsBackButALaterUserRingerChangeStillAppears() = recorderMuteHandoff(true)

    private fun recorderMuteHandoff(presented: Boolean) {
        val controller = controller(simplified = true, liveKinds = setOf("recorder"))
        val source = IslandLiveActivity(IslandLiveKind.RECORDER, IslandLiveKind.RECORDER_PACKAGE,
            context.applicationInfo.uid, IslandLiveClock(0, false), session = 18)
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        controller.foregroundChanged(source.packageName); registry.update(source.kind, source)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.RINGER, android.media.AudioManager.RINGER_MODE_SILENT))
        if (presented) shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandSystemFeedback.COLLECT))
        assertEquals(if (presented) 1 else 0, adds)
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandSystemFeedback.COLLECT))
        val state = org.robolectric.util.ReflectionHelpers.getField<IslandActivityState<IslandToastContent, IslandMediaContent>>(controller, "state")
        assertNull(state.toast(android.os.SystemClock.uptimeMillis()))
        assertEquals(source.key, state.activity(android.os.SystemClock.uptimeMillis())?.liveActivity?.key)
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.RINGER, android.media.AudioManager.RINGER_MODE_NORMAL))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandSystemFeedback.COLLECT))
        assertNotNull("A subsequent independent ringer event must remain visible", state.toast(android.os.SystemClock.uptimeMillis()))
        assertEquals(1, adds)
    }

    @Test fun foregroundClockWithoutIdleHasNoActivityWindowUntilItLeavesTheSource() {
        val controller = controller(simplified = true, liveKinds = setOf("stopwatch"))
        controller.foregroundChanged(IslandLiveKind.CLOCK_PACKAGE)
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        registry.update(IslandLiveKind.STOPWATCH, IslandLiveActivity(IslandLiveKind.STOPWATCH,
            IslandLiveKind.CLOCK_PACKAGE, 10345, IslandLiveClock(0, false), session = 10))
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertEquals(0, adds)
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        assertEquals(1, adds)
        assertFalse(shell!!.idle)
    }

    @Test fun nativeTimerAndFeedbackShareTheExistingShellAndDismissalReturnsToMedia() {
        val controller = controller(media = true, idle = true, simplified = true, liveKinds = setOf("timer"))
        native.player = launchablePlayer("live-timer-music")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        val activity = IslandLiveActivity(IslandLiveKind.TIMER, context.packageName, context.applicationInfo.uid,
            IslandLiveClock(android.os.SystemClock.elapsedRealtime() + 30000, true), session = 10)
        registry.update(IslandLiveKind.TIMER, activity)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertSame(original, shell); assertEquals(1, adds)
        val chronometer = org.robolectric.util.ReflectionHelpers.getField<android.widget.Chronometer>(original, "title")
        assertTrue(chronometer.isCountDown)
        val text = chronometer.text.toString()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1100)); drain()
        assertNotEquals(text, chronometer.text.toString())
        assertEquals(1, adds)
        val lease = controller.showToast(IslandToastContent("Copied", context.packageName, null), 3000)!!; drain()
        assertFalse(original.isMedia)
        controller.releaseToast(lease); drain()
        assertTrue(chronometer.isCountDown)
        original.onDismiss(); drain()
        assertTrue(original.isMedia)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertTrue(original.isMedia)
        assertSame(original, shell); assertEquals(1, adds)
    }

    @Test fun selectedNativeActivityCanPublishItsExistingWindowTargetAndDisablingReturnWithdrawsIt() {
        val controller = controller(idle = true, liveKinds = setOf("recorder", "call", "privacy"))
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        val activity = IslandLiveActivity(IslandLiveKind.RECORDER, IslandLiveKind.RECORDER_PACKAGE, 10345,
            IslandLiveClock(android.os.SystemClock.elapsedRealtime(), false), session = 42)
        registry.update(IslandLiveKind.RECORDER, activity)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        fun frame() = org.robolectric.util.ReflectionHelpers.callInstanceMethod<IslandMusicReturnSignal.Frame?>(controller,
            "liveReturnFrame", org.robolectric.util.ReflectionHelpers.ClassParameter.from(java.lang.Long.TYPE, android.os.SystemClock.uptimeMillis()))
        assertEquals(activity.packageName, frame()?.packageName)
        assertTrue(frame()!!.valid())
        val options = org.robolectric.util.ReflectionHelpers.getField<IslandOptions>(controller, "options")
        org.robolectric.util.ReflectionHelpers.setField(controller, "options", options.copy(liveReturn = false))
        assertNull(frame())
        org.robolectric.util.ReflectionHelpers.setField(controller, "options", options)
        registry.update(IslandLiveKind.CALL, IslandLiveActivity(IslandLiveKind.CALL, IslandLiveKind.CALL_PACKAGE,
            1000, session = 43))
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertEquals(IslandLiveKind.CALL_PACKAGE, frame()?.packageName)
        registry.update(IslandLiveKind.CALL, null); registry.update(IslandLiveKind.RECORDER, null)
        registry.update(IslandLiveKind.PRIVACY, IslandLiveActivity(IslandLiveKind.PRIVACY, IslandLiveKind.RECORDER_PACKAGE,
            10345, privacy = IslandPrivacyAccess.MICROPHONE))
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertNull(frame())
    }

    @Test fun excludedFloatingMediaUsesTheExistingIdleCapsuleInsteadOfRemovingIt() {
        val controller = controller(media = true, idle = true, hideFloating = true, simplified = true)
        native.player = launchablePlayer("excluded-video-idle")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        assertTrue(original.isMedia)
        controller.pipChanged(IslandPipSnapshot(1, 4,
            android.content.ComponentName(context.packageName, "Player"), android.os.Process.myUserHandle()))
        drain()
        assertTrue(original.isAttachedToWindow)
        assertTrue(original.idle)
        assertEquals(1, adds)
        val lease = controller.showToast(IslandToastContent("Copied", context.packageName, null), 3000)!!
        drain(); assertFalse(original.idle)
        controller.releaseToast(lease); drain()
        assertTrue(original.idle)
        assertSame(original, shell)
        assertEquals(1, adds)
    }

    @Test fun excludingAPipPlayerCannotWithdrawAnIndependentRecorderReturnTarget() {
        val controller = controller(media = true, idle = true, hideFloating = true, liveKinds = setOf("recorder"))
        native.player = launchablePlayer("excluded-video-recorder")
        controller.updateFromNative(native); revealMedia()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        controller.pipChanged(IslandPipSnapshot(1, 4,
            android.content.ComponentName(context.packageName, "Player"), android.os.Process.myUserHandle()))
        val registry = org.robolectric.util.ReflectionHelpers.getField<IslandLiveRegistry>(controller, "live")
        registry.update(IslandLiveKind.RECORDER, IslandLiveActivity(IslandLiveKind.RECORDER,
            IslandLiveKind.RECORDER_PACKAGE, 10345, IslandLiveClock(android.os.SystemClock.elapsedRealtime(), false), session = 42))
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        val publisher = org.robolectric.util.ReflectionHelpers.getField<IslandMusicReturnSignal.Publisher?>(controller, "musicReturnPublisher")
        assertNotNull("Only the PiP media source is excluded; the recorder still owns a native return target", publisher)
        val current = org.robolectric.util.ReflectionHelpers.getField<() -> IslandMusicReturnSignal.Frame?>(publisher, "current")
        assertEquals(IslandLiveKind.RECORDER_PACKAGE, current()?.packageName)
        val motion = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion")
        motion.currentPlayTime = 120
        val progress = org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress")
        assertTrue(progress > 0f && progress < 1f)
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertEquals("Media invalidation must not cancel the recorder's shared content arrival",
            progress, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), .0001f)
    }

    @Test fun idleAndActivityReuseTheSameWindowAndDismissalStaysPassiveUntilWake() {
        val controller = controller(idle = true, simplified = true)
        drain()
        assertEquals(1, adds)
        val original = shell!!
        val idleWidth = original.width
        val canvasWidth = latestParams!!.width
        val lease = controller.showToast(IslandToastContent("OK", context.packageName, null), 3000)!!
        drain()
        assertSame(original, shell)
        assertEquals(1, adds)
        assertTrue(original.width > idleWidth)
        assertEquals(canvasWidth, latestParams!!.width)
        assertEquals(0, windowUpdates)
        controller.releaseToast(lease); drain()
        assertEquals(idleWidth, original.width)
        assertEquals(canvasWidth, latestParams!!.width)
        assertEquals(1, adds)
        shell!!.onDismiss(); drain()
        assertFalse(original.isAttachedToWindow)
        controller.onScreenStateChanged(true); drain()
        assertSame(original, shell)
        assertEquals(2, adds)
    }

    @Test fun unlockedShadeRetainsOnlyTheIdleCapsuleAndNeverItsTouchHotspotOrMedia() {
        val controller = controller(media = true, idle = true, simplified = true)
        val original = shell!!
        val idleWidth = original.width
        native.player = launchablePlayer("shade-idle-music")
        controller.updateFromNative(native); revealMedia()
        assertTrue(original.width > idleWidth)
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        assertSame(original, shell)
        assertEquals(idleWidth, original.width)
        assertFalse((original.parent as IslandTouchWindow).inputEnabled)
        assertEquals(1, adds)
        assertEquals(0, removes)
        native.shade = false; native.changed!!(); drain()
        assertEquals(1, removes) // A privacy block cannot retain the unlocked-shade capsule.
    }

    private fun drain() {
        shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent()
        if (drawFrames) {
            frame.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
            frame.layout(0, 0, 1080, 2400)
            shell?.takeIf { it.isAttachedToWindow }?.let {
                val attach = org.robolectric.util.ReflectionHelpers.getField<Any>(it, "mAttachInfo")
                org.robolectric.util.ReflectionHelpers.setField(attach, "mWindowVisibility", View.VISIBLE)
                it.viewTreeObserver.dispatchOnPreDraw()
            }
        }
    }

    @Test fun actualWindowRotationWithoutResourceConfigurationHidesAndRestoresTheIsland() {
        val geometry = View(host)
        frame.addView(geometry, FrameLayout.LayoutParams(1080, 133))
        geometry.layout(0, 0, 1080, 133)
        val display = shadowOf(geometry.display)
        val orientation = context.resources.configuration.orientation
        val controller = controller(geometryHost = geometry)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 5000))
        drain()
        assertTrue(shell!!.isAttachedToWindow)
        try {
            display.setRotation(Surface.ROTATION_90)
            geometry.layoutParams.width = 2400
            geometry.layout(0, 0, 2400, 133)
            shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent()
            assertEquals("This path must not depend on a resource callback", orientation, context.resources.configuration.orientation)
            assertFalse(shell!!.isAttachedToWindow)
            assertNull(controller.showToast(IslandToastContent("Landscape", context.packageName, null), 1000))
            display.setRotation(Surface.ROTATION_0)
            geometry.layoutParams.width = 1080
            geometry.layout(0, 0, 1080, 133)
            shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent()
            assertNotNull(controller.showToast(IslandToastContent("Portrait", context.packageName, null), 1000))
            drain()
            assertTrue(shell!!.isAttachedToWindow)
        } finally { display.setRotation(Surface.ROTATION_0) }
    }

    @Test fun disposalRemovesWindowGeometryListenerAndUnchangedLayoutsDoNotReadMetrics() {
        val geometry = View(host)
        frame.addView(geometry, FrameLayout.LayoutParams(1080, 133))
        geometry.layout(0, 0, 1080, 133)
        val controller = controller(geometryHost = geometry)
        val listeners = org.robolectric.util.ReflectionHelpers.getField<ArrayList<View.OnLayoutChangeListener>>(
            org.robolectric.util.ReflectionHelpers.getField<Any>(geometry, "mListenerInfo"), "mOnLayoutChangeListeners")
        assertEquals(1, listeners.size)
        val before = metricsReads
        geometry.layout(0, 1, 1080, 134)
        shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent()
        assertEquals(before, metricsReads)
        controller.dispose(); scheduler.runCurrent()
        assertTrue("The native View must release the owner's listener", listeners.isEmpty())
        geometry.layout(0, 0, 2400, 133)
        shadowOf(Looper.getMainLooper()).idle(); scheduler.runCurrent()
        assertEquals("Disposed owner must not start another geometry read", before, metricsReads)
    }
    @Test fun manualWideGeometryActuallyReachesTheWindowAndRespectsThePhysicalViewport() {
        val controller = controller(automaticWidth = false, shortWidth = 320, longWidth = 400, height = 64)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 5000))
        drain()
        val density = context.resources.displayMetrics.density
        assertEquals(kotlin.math.round(320 * density).toInt(), shell!!.width)
        assertEquals(kotlin.math.round(64 * density).toInt(), shell!!.height)
        assertTrue(shell!!.width <= metricsWidth)
    }

    private fun revealMedia() {
        drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750)); drain()
        shell?.let { org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(it, "motion")?.end() }
        drain()
    }

    @Test fun notificationWidensTheCurrentMusicAndDismissesToItAtTheSharedAutomaticHeight() {
        val controller = controller(chat = true, media = true)
        native.player = launchablePlayer("shared-notice-motion")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        val musicWidth = original.width
        val height = original.height
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        assertTrue(controller.takeNotification(message(701))); drain()
        assertSame(original, shell)
        assertEquals(1, adds); assertEquals(0, removes)
        assertTrue(original.expanded)
        assertTrue(original.width > musicWidth)
        assertEquals(musicWidth.toFloat(), original.width * original.scaleX, .001f)
        val arrival = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion")
        assertEquals(IslandMotion.CONTENT_ARRIVAL, arrival.duration)
        for (time in 0L until arrival.duration step 16L) {
            arrival.currentPlayTime = time
            assertEquals(height.toFloat(), original.height * original.scaleY, .001f)
            assertEquals(0f, original.translationY, .001f)
        }
        arrival.end()
        val longWidth = original.width
        assertTrue(original.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS, null))
        assertFalse(original.expanded)
        assertTrue("The source is released by the first gesture", original.isMedia)
        drain()
        assertEquals(longWidth.toFloat(), original.width * original.scaleX, .001f)
        val returning = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion")
        assertEquals(IslandMotion.CONTENT_ARRIVAL, returning.duration)
        returning.currentPlayTime = 80
        assertTrue(original.width * original.scaleX < longWidth)
        assertTrue(original.width * original.scaleX > musicWidth)
        assertEquals(height.toFloat(), original.height * original.scaleY, .001f)
        returning.end()
        assertEquals(musicWidth, original.width)
        assertSame(original, shell); assertEquals(1, adds); assertEquals(0, removes)
    }

    @Test fun noticeDismissalWithoutMusicReturnsDirectlyToTheConfiguredIdleIsland() {
        val controller = controller(chat = true, idle = true)
        drain()
        val original = shell!!
        org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(original, "motion")?.end()
        val idleWidth = original.width
        val height = original.height
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        assertTrue(controller.takeNotification(message(702))); drain()
        org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion").end()
        val longWidth = original.width
        original.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS, null)
        assertFalse(original.expanded)
        assertTrue(original.idle)
        drain()
        assertEquals(longWidth.toFloat(), original.width * original.scaleX, .001f)
        val returning = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion")
        returning.currentPlayTime = 80
        assertEquals(height.toFloat(), original.height * original.scaleY, .001f)
        assertEquals(0f, original.translationY, .001f)
        returning.end()
        assertEquals(idleWidth, original.width)
        assertSame(original, shell); assertEquals(1, adds); assertEquals(0, removes)
    }

    @Test fun finalNoticeInTopModeExitsInOneRigidVerticalFlight() {
        val controller = controller(chat = true)
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        assertTrue(controller.takeNotification(message(703))); drain()
        val original = shell!!
        org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion").end()
        val width = original.width
        val height = original.height
        original.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS, null)
        val exit = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion")
        assertEquals(IslandMotion.EXIT, exit.duration)
        var previousY = original.translationY
        for (time in 0L until exit.duration step 16L) {
            exit.currentPlayTime = time
            assertEquals(width.toFloat(), original.width * original.scaleX, .001f)
            assertEquals(height.toFloat(), original.height * original.scaleY, .001f)
            assertEquals(0f, original.translationX, .001f)
            assertTrue(original.translationY <= previousY)
            previousY = original.translationY
        }
        assertTrue(original.translationY < 0f)
        exit.end()
        assertEquals(1, removes)
        assertFalse(original.isAttachedToWindow)
    }

    @Test fun expandedMusicDismissalReleasesTheSourceInOneGesture() {
        val controller = controller(media = true, idle = true)
        native.player = launchablePlayer("music-detail-dismissal")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        val compactWidth = original.width
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        org.robolectric.util.ReflectionHelpers.getField<IslandFormState>(controller, "forms").expand(android.os.SystemClock.uptimeMillis())
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertTrue(original.expanded)
        org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion").end()
        original.onDismiss(); drain()
        assertTrue(original.idle)
        assertSame(original, shell); assertEquals(1, adds); assertEquals(0, removes)
    }

    @Test fun manuallyExpandedPlayingMusicAutomaticallyContractsAfterInteractionWithoutRemovingItsWindow() {
        val controller = controller(media = true, simplified = true)
        native.player = launchablePlayer("automatic-detail-contraction")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        org.robolectric.util.ReflectionHelpers.getField<IslandFormState>(controller, "forms").expand(android.os.SystemClock.uptimeMillis())
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertTrue(original.expanded)
        original.onInteraction(true); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8)); drain()
        assertTrue(original.expanded)
        val releasedAt = android.os.SystemClock.uptimeMillis()
        original.onInteraction(false); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(
            releasedAt + IslandMotion.EXPANDED_DWELL - android.os.SystemClock.uptimeMillis() - 1))
        assertTrue(original.expanded)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
        assertFalse(original.expanded); assertTrue(original.isMedia)
        assertSame(original, shell); assertEquals(1, adds); assertEquals(0, removes)
    }

    @Test fun touchingAnIdleIslandDoesNotQueueAnUnrelatedRenderThatCancelsItsOwnGesture() {
        controller(idle = true, simplified = true)
        val original = shell!!
        assertTrue(original.idle)
        val down = android.view.MotionEvent.obtain(0, 10, android.view.MotionEvent.ACTION_DOWN,
            original.width * .5f, original.height * .5f, 0)
        try { assertTrue(original.dispatchTouchEvent(down)) } finally { down.recycle() }
        drain()
        assertFalse(org.robolectric.util.ReflectionHelpers.getField(original, "inputCancelled"))
        assertTrue(org.robolectric.util.ReflectionHelpers.getField(original, "touchActive"))
        val up = android.view.MotionEvent.obtain(0, 30, android.view.MotionEvent.ACTION_UP,
            original.width * .5f, original.height * .5f, 0)
        try { original.dispatchTouchEvent(up) } finally { up.recycle() }
        assertNull(org.robolectric.util.ReflectionHelpers.getField<android.view.VelocityTracker?>(original, "velocityTracker"))
    }

    @Test fun aBatchGalleryDeletionCanUseTwoReadableLongLinesAndStillRetainsNativeOnOverflow() {
        val controller = controller(media = true, simplified = true, automaticWidth = false, shortWidth = 96, longWidth = 320)
        native.player = launchablePlayer("batch-gallery-delete")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        val message = "已将选中的 24 张照片和视频移至最近删除"
        val lease = requireNotNull(controller.showToast(IslandToastContent(message, "com.miui.gallery", null), 1800))
        assertTrue(original.expanded)
        val label = (original.getChildAt(1) as android.widget.LinearLayout).getChildAt(0) as android.widget.TextView
        assertEquals(message, label.text.toString()); assertEquals(2, label.maxLines)
        controller.releaseToast(lease); drain()
        assertTrue(original.isMedia)
        assertNull(controller.showToast(IslandToastContent("删除".repeat(80), "com.miui.gallery", null), 1800))
        assertTrue(original.isMedia); assertSame(original, shell)
    }

    @Test fun notificationAndMusicAlsoShareAnExplicitlyConfiguredHeight() {
        val controller = controller(chat = true, media = true, height = 48)
        native.player = launchablePlayer("manual-notice-height")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        assertEquals(48, original.height)
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        assertTrue(controller.takeNotification(message(704))); drain()
        val clock = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion")
        for (time in 0L until clock.duration step 16L) {
            clock.currentPlayTime = time
            assertEquals(48f, original.height * original.scaleY, .001f)
        }
        clock.end()
        original.onDismiss(); drain()
        assertFalse(original.expanded)
        assertTrue(original.isMedia)
        assertEquals(48f, original.height * original.scaleY, .001f)
    }

    @Test fun dismissingANoticeDuringExpansionCarriesTheVisibleWidthAndVelocityBackToMusic() {
        val controller = controller(chat = true, media = true)
        native.player = launchablePlayer("interrupted-notice-motion")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        val compactWidth = original.width
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        assertTrue(controller.takeNotification(message(705))); drain()
        val arrival = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion")
        arrival.currentPlayTime = 80
        val width = original.width * original.scaleX
        val velocity = org.robolectric.util.ReflectionHelpers.getField<IslandMotion.Spring>(original, "breadth").velocity +
            org.robolectric.util.ReflectionHelpers.getField<Float>(original, "breadthExtraVelocity")
        original.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_DISMISS, null)
        assertFalse(original.expanded)
        assertTrue(original.isMedia)
        drain()
        assertEquals(width, original.width * original.scaleX, .001f)
        assertEquals(velocity, org.robolectric.util.ReflectionHelpers.getField<IslandMotion.Spring>(original, "breadth").velocity, .01f)
        val returning = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(original, "motion")
        assertNotSame(arrival, returning)
        arrival.end()
        assertSame(returning, org.robolectric.util.ReflectionHelpers.getField(original, "motion"))
        returning.end()
        assertEquals(compactWidth, original.width)
        assertEquals(1, adds); assertEquals(0, removes)
    }

    @Test fun automaticHeightUsesPhysicalCutoutHeightRatherThanItsHorizontalSpan() {
        metricsWidth = 360
        metricsInsets = WindowInsets.Builder()
            .setInsets(android.view.WindowInsets.Type.statusBars(), android.graphics.Insets.of(0, 46, 0, 0))
            .setDisplayCutout(android.view.DisplayCutout(android.graphics.Insets.of(0, 32, 0, 0),
                null, Rect(120, 8, 240, 32), null, null)).build()
        val controller = controller(chat = true, simplified = true)
        assertTrue(controller.takeNotification(message(707))); drain()
        assertEquals("120 px wide / 24 px tall cutout must use the 24 px vertical input", 38, shell!!.height)
    }

    @Test fun expandedNoticeAndItsElasticCeilingRespectBothNativeRoundedCorners() {
        metricsWidth = 360
        metricsInsets = WindowInsets.Builder()
            .setRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT,
                android.view.RoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT, 40, 40, 40))
            .setRoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT,
                android.view.RoundedCorner(android.view.RoundedCorner.POSITION_TOP_RIGHT, 40, 320, 40)).build()
        val controller = controller(chat = true, simplified = true)
        assertTrue(controller.takeNotification(message(706))); drain()
        val original = shell!!
        val left = original.left - (original.parent as IslandTouchWindow).margin
        assertTrue(left >= 40)
        assertTrue(left + original.width <= 320)
        assertTrue(original.width <= 280)
        assertTrue(org.robolectric.util.ReflectionHelpers.getField<Float>(original, "maximumWidth") <= 280f)
    }

    @Test fun horizontalOffsetsCannotPushAWideCenteredNoticeIntoEitherScreenCorner() {
        metricsWidth = 360
        val controller = controller(chat = true, simplified = true, automaticWidth = false,
            shortWidth = 160, longWidth = 400)
        assertTrue(controller.takeNotification(message(707))); drain()
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        for (value in listOf(0, 80, 160)) {
            prefs.put("system_island_horizontal_offset", value)
            observer.onChange("system_island_horizontal_offset"); drain()
            val original = shell!!
            val left = original.left - (original.parent as IslandTouchWindow).margin
            assertTrue("left edge at stored offset $value", left >= 24)
            assertTrue("right edge at stored offset $value", left + original.width <= metricsWidth - 24)
            assertEquals(312, original.width)
        }
    }

    @Test fun bufferingKeepsTheVisibleTrackStillUntilPlaybackCommitsTheLatestMetadata() {
        val controller = controller(media = true, simplified = true)
        val player = launchablePlayer("buffered-track")
        native.player = player
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Track A").build()
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        val width = original.width
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_BUFFERING, 0, 0f).build())
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Track B pending").build()
        controller.updateFromNative(native); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(60)); drain()
        assertSame(original, shell)
        assertTrue(original.isAttachedToWindow)
        assertEquals(width, original.width)
        val waiting = org.robolectric.util.ReflectionHelpers.getField<IslandMediaContent>(controller, "lastContent")
        assertEquals("Track A", waiting.title)
        assertTrue(waiting.loading); assertFalse(waiting.playing)
        native.player = null // A transient native-card gap must not invent a new owner.
        controller.updateFromNative(native); drain()
        assertTrue(original.isAttachedToWindow)
        native.player = player
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Track B").build()
        controller.updateFromNative(native); drain()
        val committed = org.robolectric.util.ReflectionHelpers.getField<IslandMediaContent>(controller, "lastContent")
        assertEquals("Track B", committed.title)
        assertTrue(committed.playing); assertFalse(committed.loading)
        assertEquals(1, adds); assertEquals(0, removes)
    }

    @Test fun ambiguousPauseAfterBufferingHasBoundedGraceAndDuplicateSnapshotsCannotRenewIt() {
        val controller = controller(media = true, simplified = true)
        val player = launchablePlayer("buffer-pause")
        native.player = player
        controller.updateFromNative(native); revealMedia()
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_BUFFERING, 0, 0f).build())
        controller.updateFromNative(native); drain()
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.updateFromNative(native); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandMotion.LOADING_LIFETIME - 1)); drain()
        assertTrue(shell!!.isAttachedToWindow)
        controller.updateFromNative(native); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2)); drain()
        assertFalse(shell!!.isAttachedToWindow)
        controller.updateFromNative(native); drain()
        assertFalse(shell!!.isAttachedToWindow)
    }

    @Test fun anInitialLoadingSnapshotDoesNotInventAPlayingIsland() {
        val controller = controller(media = true, simplified = true)
        val player = launchablePlayer("initial-buffer")
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_CONNECTING, 0, 0f).build())
        native.player = player
        controller.updateFromNative(native); revealMedia()
        assertEquals(0, adds)
    }

    @Test fun mirroredCoversAndHiddenMetadataUpdatesDoNotFadeCompactMedia() {
        val controller = controller(media = true)
        val player = launchablePlayer("cover-copies")
        native.player = player
        val image = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
        }
        fun metadata(title: String, bitmap: android.graphics.Bitmap) = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap).build()
        native.track = metadata("A", image)
        controller.updateFromNative(native); revealMedia()
        val first = org.robolectric.util.ReflectionHelpers.getField<IslandMediaContent>(controller, "media").icon
        native.track = metadata("B", requireNotNull(image.copy(android.graphics.Bitmap.Config.ARGB_8888, false)))
        controller.updateFromNative(native); drain()
        val updated = org.robolectric.util.ReflectionHelpers.getField<IslandMediaContent>(controller, "media")
        assertSame(first, updated.icon)
        assertEquals("B", updated.title)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(shell, "motion"))
        assertEquals(1f, shell!!.getChildAt(0).alpha, .001f)
    }

    @Test fun anAlarmNativeCardGapCannotDismissTheStillActiveMusicSession() {
        val controller = controller(media = true, simplified = true)
        val player = launchablePlayer("alarm-native-card-gap")
        val manager = requireNotNull(context.getSystemService(android.media.session.MediaSessionManager::class.java))
        shadowOf(manager).addController(player)
        native.player = player
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        native.player = null
        controller.updateFromNative(native); drain()
        assertTrue(original.isAttachedToWindow)
        assertTrue(original.isMedia)
        assertEquals(0, removes)
        shadowOf(manager).clearControllers()
        controller.updateFromNative(native); drain()
        assertFalse(original.isAttachedToWindow)
        assertTrue(shadowOf(player).callbacks.isEmpty())
    }

    @Test fun anAlarmPauseWithoutANativeCardCanResumeThroughTheExistingCallback() {
        val controller = controller(media = true, simplified = true)
        val player = launchablePlayer("alarm-pause-card-gap")
        val manager = requireNotNull(context.getSystemService(android.media.session.MediaSessionManager::class.java))
        shadowOf(manager).addController(player)
        native.player = player
        controller.updateFromNative(native); revealMedia()
        val callback = shadowOf(player).callbacks.single()
        native.player = null
        val paused = PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build()
        shadowOf(player).setPlaybackState(paused); callback.onPlaybackStateChanged(paused); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2500)); drain()
        assertFalse(shell!!.isAttachedToWindow)
        assertSame(callback, shadowOf(player).callbacks.single())
        val playing = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build()
        shadowOf(player).setPlaybackState(playing); callback.onPlaybackStateChanged(playing); drain(); revealMedia()
        assertTrue(shell!!.isAttachedToWindow)
        assertTrue(shell!!.isMedia)
        assertEquals(2, adds)
        assertSame(callback, shadowOf(player).callbacks.single())
        val stopped = PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build()
        shadowOf(player).setPlaybackState(stopped); callback.onPlaybackStateChanged(stopped); drain()
        assertTrue(shadowOf(player).callbacks.isEmpty())
        assertFalse(shell!!.isAttachedToWindow)
    }

    @Test fun repeatedPausedNativeCardGapsCannotRenewTheSessionRetention() {
        val controller = controller(media = true, simplified = true)
        val player = launchablePlayer("alarm-expired-card-gap")
        val manager = requireNotNull(context.getSystemService(android.media.session.MediaSessionManager::class.java))
        shadowOf(manager).addController(player)
        native.player = player
        controller.updateFromNative(native); revealMedia()
        native.player = null
        val paused = PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build()
        shadowOf(player).setPlaybackState(paused)
        shadowOf(player).callbacks.single().onPlaybackStateChanged(paused); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20)); drain()
        controller.updateFromNative(native); drain()
        assertEquals(1, shadowOf(player).callbacks.size)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(11)); drain()
        assertTrue(shadowOf(player).callbacks.isEmpty())
        assertFalse(shell!!.isAttachedToWindow)
        assertNull(org.robolectric.util.ReflectionHelpers.getField(controller, "selected"))
        assertEquals(1, adds)
    }

    @Test fun aCompletedDeleteThatDoesNotFitShortUsesTheLongFormThenRestoresMusic() {
        val controller = controller(media = true, simplified = true, automaticWidth = false, shortWidth = 96, longWidth = 240)
        native.player = launchablePlayer("delete-feedback-over-music")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        val lease = requireNotNull(controller.showToast(IslandToastContent("删除成功", "com.miui.gallery", null), 1800))
        assertSame(original, shell)
        assertFalse(original.isMedia)
        assertTrue(original.expanded)
        drain()
        assertEquals(240, original.width)
        controller.releaseToast(lease); drain()
        assertSame(original, shell)
        assertTrue(original.isMedia)
        assertEquals(1, adds)
        assertEquals(0, removes)
    }

    @Test fun notificationPreemptsOrdinaryFeedbackAndCannotBeOverwrittenByAnotherToast() {
        val controller = controller(chat = true, simplified = true)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 5000))
        assertTrue(controller.takeNotification(message(812)))
        val lease = org.robolectric.util.ReflectionHelpers.getField<Any>(controller, "chatLease")
        assertNull(controller.showToast(IslandToastContent("Copied", context.packageName, null), 5000))
        assertSame(lease, org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
        assertEquals(1, adds)
        assertTrue(shell!!.isAttachedToWindow)
    }

    @Test fun idleResumeResolvesOnlyTheLastVerifiedSessionAfterItsNativeCardDisappears() {
        val controller = controller(media = true, idle = true, simplified = true)
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        prefs.put("system_island_idle_left_action", IslandMusicActions.PLAY)
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        observer.onChange("system_island_idle_left_action"); drain()
        val player = launchablePlayer("idle-resume")
        val manager = requireNotNull(context.getSystemService(android.media.session.MediaSessionManager::class.java))
        shadowOf(manager).addController(player)
        native.player = player
        controller.updateFromNative(native); revealMedia()
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f)
            .setActions(PlaybackState.ACTION_PLAY).build())
        native.player = null
        controller.updateFromNative(native); drain()
        assertFalse(shell!!.idle)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2100)); drain()
        assertTrue(shell!!.idle)
        shell!!.onGesture(IslandMusicGesture.LEFT); drain()
        assertEquals(PlaybackState.ACTION_PLAY, shadowOf(player.transportControls).lastPerformedAction)
        val other = launchablePlayer("unrelated-token")
        val baseline = shadowOf(other.transportControls).lastPerformedAction
        shadowOf(manager).clearControllers(); shadowOf(manager).addController(other)
        shell!!.onGesture(IslandMusicGesture.LEFT); drain()
        assertEquals(baseline, shadowOf(other.transportControls).lastPerformedAction)
    }

    @Test fun confirmedReturnCanPrecedeTheForegroundAppEdgeOfAnInterruptedOpening() {
        val controller = controller(media = true, simplified = true, mediaReturn = true,
            mediaReturnApps = setOf(context.packageName))
        // Simplified mode intentionally declines flight; enable normal motion for this contract.
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        prefs.put("system_island_simplified", false)
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        observer.onChange("system_island_simplified"); drain()
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER)
        native.player = launchablePlayer("opening-interrupted-before-foreground")
        controller.updateFromNative(native); drain()
        assertEquals(0, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
        nativeReturn(controller); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandMotion.MEDIA_RETURN_DELAY)); drain()
        assertEquals(1, adds)
    }

    private fun nativeReturn(controller: IslandActivityController, committedAt: Long = android.os.SystemClock.uptimeMillis()) {
        val media = org.robolectric.util.ReflectionHelpers.getField<IslandMediaContent>(controller, "media")
        controller.nativeMusicReturn(IslandMusicReturnSignal.Frame(media.packageName, requireNotNull(media.user),
            280f, 15f, 800f, 125f, 1080, 2400), committedAt)
    }

    private fun message(id: Int, time: Long = 1L, open: () -> Boolean = { true }): IslandNotificationAccess.Bound {
        val intent = android.app.PendingIntent.getActivity(context, id, android.content.Intent("island.message.$id"), android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = android.app.Notification.Builder(context, "chat")
            .setSmallIcon(android.R.drawable.ic_dialog_email).setCategory(android.app.Notification.CATEGORY_MESSAGE)
            .setContentTitle("Alice").setContentText("Public message").setContentIntent(intent).build()
        val sbn = android.service.notification.StatusBarNotification(context.packageName, context.packageName,
            id, "message", android.os.Process.myUid(), 0, 0, notification, android.os.Process.myUserHandle(), time)
        val ranking = android.service.notification.NotificationListenerService.Ranking()
        org.robolectric.util.ReflectionHelpers.setField(ranking, "mImportance", android.app.NotificationManager.IMPORTANCE_HIGH)
        org.robolectric.util.ReflectionHelpers.setField(ranking, "mMatchesInterruptionFilter", true)
        return IslandNotificationAccess.Bound(sbn, ranking, null, open)
    }

    @Test fun aNativePrivacyUpdateRevokesTheCurrentNoticeWithoutAFramePollingLoop() {
        val controller = controller(chat = true, simplified = true)
        var hidden = false
        val bound = message(499) { true }.copy(hidden = { hidden })
        assertTrue(controller.takeNotification(bound)); drain()
        controller.notificationPrivacyChanged(); drain()
        assertTrue(shell!!.isAttachedToWindow)
        hidden = true
        controller.notificationPrivacyChanged(); drain()
        assertFalse(shell!!.isAttachedToWindow)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "chatNotification"))
    }

    @Test fun privacyRevocationReenteringWindowInsertionCannotCommitAnObsoleteNotice() {
        val controller = controller(chat = true, simplified = true)
        var hidden = false
        val bound = message(498) { true }.copy(hidden = { hidden })
        onAdd = { hidden = true; controller.notificationPrivacyChanged() }
        assertFalse(controller.takeNotification(bound)); drain()
        assertFalse(shell!!.isAttachedToWindow)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "chatNotification"))
    }

    @Test fun notificationTakeoverIsExactAndUsesTheNativeClickOnlyOnce() {
        val controller = controller(chat = true, simplified = true)
        var firstClicks = 0; var secondClicks = 0
        val first = message(1) { firstClicks++; true }
        val second = message(2) { secondClicks++; true }
        assertTrue(controller.takeNotification(first))
        assertTrue(controller.takeNotification(second))
        controller.notificationRemoved(first.notification.key)
        assertEquals(0, removes)
        assertTrue(shell!!.performClick())
        assertEquals(0, firstClicks)
        assertEquals(1, secondClicks)
        shell!!.performClick()
        assertEquals(1, secondClicks)
        assertEquals(1, removes)
    }

    @Test fun shortMessageBodyUsesLongFormAndReturnsToTheSameCompactMediaWindow() {
        val controller = controller(chat = true, media = true, simplified = true)
        native.player = launchablePlayer("message-long-form")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        assertFalse(original.expanded)
        val bound = message(501)
        bound.notification.notification.extras.putCharSequence(android.app.Notification.EXTRA_TEXT, "好")
        assertTrue(controller.takeNotification(bound)); drain()
        assertTrue(original.expanded)
        val caption = org.robolectric.util.ReflectionHelpers.getField<android.widget.Chronometer>(original, "title")
        assertEquals("好", caption.text.toString())
        val identity = org.robolectric.util.ReflectionHelpers.getField<android.widget.TextView>(original, "identityTitle")
        assertEquals("Alice", identity.text.toString()); assertEquals(View.VISIBLE, identity.visibility)
        assertEquals(android.view.Gravity.END, caption.gravity and android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)
        assertTrue(identity.textSize >= caption.textSize)
        assertEquals(android.text.TextUtils.TruncateAt.END, caption.ellipsize)
        val lease = org.robolectric.util.ReflectionHelpers.getField<Any>(controller, "chatLease")
        org.robolectric.util.ReflectionHelpers.getField<IslandFormState>(controller, "forms").collapse()
        repeat(4) {
            controller.updateFromNative(native); drain()
            assertTrue(controller.takeNotification(bound)); drain()
            assertFalse(original.expanded)
        }
        assertSame(lease, org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
        controller.notificationRemoved(bound.notification.key); drain()
        assertTrue(original.isMedia); assertFalse(original.expanded)
        assertSame(original, shell); assertEquals(1, adds)
    }

    @Test fun messageBodySelectsTheConfiguredLongWidthEvenWhenAutomaticSizingIsOff() {
        val controller = controller(chat = true, simplified = true, automaticWidth = false,
            shortWidth = 160, longWidth = 300)
        val bound = message(502)
        bound.notification.notification.extras.putCharSequence(android.app.Notification.EXTRA_TEXT, "好")
        assertTrue(controller.takeNotification(bound)); drain()
        assertTrue(shell!!.expanded)
        val width = org.robolectric.util.ReflectionHelpers.getField<Int>(shell!!, "geometryWidth")
        assertEquals(kotlin.math.round(300 * context.resources.displayMetrics.density).toInt(), width)
    }

    @Test fun emptyNotificationAllowListLeavesTheNativeNotificationUntouched() {
        val controller = controller(chat = true, notificationApps = emptySet())
        assertFalse(controller.takeNotification(message(50)))
        assertEquals(0, adds)
        assertNull(org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
    }

    @Test fun notificationBindRechecksSafetyBeforeTheStatusBarCallbackArrives() {
        val controller = controller(chat = true, simplified = true)
        native.allowed = false // Keyguard changed; the cached status-bar callback has not run.
        assertFalse(controller.takeNotification(message(60)))
        assertEquals(0, adds)
        assertNull(org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
        native.changed!!(); drain()
        native.allowed = true; native.changed!!(); drain()
        assertTrue(controller.takeNotification(message(61)))
    }

    @Test fun aSnapshotFromBeforeDirectPlayerLaunchCannotRevealPausedMusicOnHome() {
        val ioScheduler = TestCoroutineScheduler()
        val controller = controller(media = true, simplified = true, io = StandardTestDispatcher(ioScheduler))
        ioScheduler.runCurrent(); drain() // Geometry is prepared by the same owned IO dispatcher.
        val session = MediaSession(context, "direct-player-pending-read")
        try {
            val player = MediaController(context, session.sessionToken)
            shadowOf(player).setPackageName(context.packageName)
            shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            native.player = player; controller.updateFromNative(native); drain()
            ioScheduler.runCurrent() // PLAYING has been read; its Main continuation is still queued.
            controller.foregroundChanged(context.packageName)
            shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
            controller.foregroundChanged("com.miui.home")
            drain(); ioScheduler.runCurrent(); drain(); revealMedia()
            assertEquals("An old snapshot must not invent playback after a direct App/Home switch", 0, adds)
            shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            controller.updateFromNative(native); drain(); ioScheduler.runCurrent(); drain(); revealMedia()
            assertEquals(1, adds)
        } finally { session.release() }
    }

    @Test fun keyguardChangesDuringWindowInsertionAbortBeforeNativeConsumption() {
        val controller = controller(chat = true, simplified = true)
        onAdd = { native.allowed = false }
        assertFalse(controller.takeNotification(message(62)))
        assertEquals(adds, removes)
        assertNull(org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        onAdd = null
    }

    @Test fun unknownSafetyFailsClosedAndOldSameKeyRollbackCannotRemoveANewerNotification() {
        val controller = controller(chat = true, simplified = true)
        native.failSafety = true
        assertFalse(controller.takeNotification(message(63)))
        assertEquals(0, adds)
        native.failSafety = false; native.changed!!(); drain()
        val old = message(64, time = 1L)
        val update = message(64, time = 1L) // Same key and millisecond, a different native notification.
        assertTrue(controller.takeNotification(old))
        controller.notificationPosted(update.notification)
        assertNull(org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
        assertTrue(controller.takeNotification(update))
        controller.cancelNotificationTakeover(old)
        assertSame(update.notification, org.robolectric.util.ReflectionHelpers.getField(controller, "chatNotification"))
        assertTrue(shell!!.isAttachedToWindow)
    }

    @Test fun fiftyDirectAppHomeSwitchesRecoverTopEntry() = exerciseDirectAppHomeSwitches(IslandEntranceStyle.TOP)
    @Test fun fiftyDirectAppHomeSwitchesRecoverCenterEntry() = exerciseDirectAppHomeSwitches(IslandEntranceStyle.CENTER)

    @Test fun sameNotificationRebindingRefreshesItsClickWithoutRenewingTheLeaseOrAllowingOldRollback() {
        val controller = controller(chat = true, simplified = true)
        var oldClicks = 0; var newClicks = 0
        val old = message(65) { oldClicks++; true }
        val rebound = old.copy(open = { newClicks++; true })
        assertTrue(controller.takeNotification(old))
        val lease = org.robolectric.util.ReflectionHelpers.getField<Any>(controller, "chatLease")
        assertTrue(controller.takeNotification(rebound))
        assertSame(rebound.open, org.robolectric.util.ReflectionHelpers.getField(controller, "chatOpen"))
        assertSame(lease, org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
        controller.cancelNotificationTakeover(old)
        assertSame(lease, org.robolectric.util.ReflectionHelpers.getField(controller, "chatLease"))
        assertTrue(shell!!.performClick())
        drain()
        assertEquals(0, oldClicks)
        assertEquals(1, newClicks)
    }

    @Test fun losingTheNativeMediaOwnerCannotLeaveAForegroundBaselineOrCallbackBehind() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "lost-native-media-owner")
        try {
            val player = MediaController(context, session.sessionToken)
            shadowOf(player).setPackageName(context.packageName)
            shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            native.player = player; controller.updateFromNative(native); drain(); revealMedia()
            org.robolectric.util.ReflectionHelpers.getField<java.lang.ref.WeakReference<Any>>(controller, "nativeManager").clear()
            controller.foregroundChanged(context.packageName); drain()
            controller.foregroundChanged("com.miui.home"); revealMedia()
            assertEquals(adds, removes)
            assertTrue(shadowOf(player).callbacks.isEmpty())
            assertFalse(org.robolectric.util.ReflectionHelpers.getField(controller, "foregroundBaseline"))
            controller.updateFromNative(native); drain(); revealMedia()
            assertEquals(1, adds - removes)
        } finally { session.release() }
    }

    private fun exerciseDirectAppHomeSwitches(style: IslandEntranceStyle) {
        val controller = controller(media = true, entranceStyle = style)
        val session = MediaSession(context, "direct-player-$style")
        try {
            val player = MediaController(context, session.sessionToken)
            shadowOf(player).setPackageName(context.packageName)
            shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
            controller.foregroundChanged(context.packageName)
            native.player = player; controller.updateFromNative(native); drain()
            repeat(50) { turn ->
                controller.foregroundChanged("com.miui.home"); drain()
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(listOf(0L, 749L, 750L)[turn % 3])); drain()
                val entering = shell?.let { org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(it, "motion") }
                entering?.currentPlayTime = (entering.duration * listOf(.25f, .5f, .75f)[turn % 3]).toLong()
                drain()
                controller.foregroundChanged(context.packageName); drain()
                val oldExit = shell?.let { org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(it, "motion") }
                controller.foregroundChanged("com.miui.home"); revealMedia()
                oldExit?.end(); drain() // Late old completion must not detach the latest window.
                assertTrue("$style switch $turn", shell!!.isAttachedToWindow)
                assertEquals(1f, shell!!.alpha, .001f)
                assertEquals(1f, shell!!.scaleX, .001f)
                assertEquals(1f, shell!!.scaleY, .001f)
                assertEquals(0f, shell!!.translationY, .001f)
                assertEquals(1, adds - removes)
                assertEquals(IslandWindowPresentation.Phase.VISIBLE,
                    org.robolectric.util.ReflectionHelpers.getField<IslandWindowPresentation>(controller, "presentation").phase)
                assertFalse(org.robolectric.util.ReflectionHelpers.getField(controller, "foregroundBaseline"))
                assertEquals(1, shadowOf(player).callbacks.size)
            }
            controller.dispose(); drain()
            assertEquals(adds, removes)
            assertEquals(0, native.listeners)
            assertTrue(shadowOf(player).callbacks.isEmpty())
        } finally { session.release() }
    }

    @Test fun aMediaRefreshCannotCancelAnUnchangedNotificationsClick() {
        val controller=controller(media=true,chat=true,simplified=true)
        val session=MediaSession(context,"media-behind-notification")
        val player=MediaController(context,session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build())
        native.player=player; controller.updateFromNative(native); drain(); revealMedia()
        var clicked=0
        assertTrue(controller.takeNotification(message(51){clicked++;true})); drain()
        val now=android.os.SystemClock.uptimeMillis()
        fun touch(action:Int) {
            val event=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,shell!!.width*.5f,shell!!.height*.5f,0)
            try { shell!!.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN)
        controller.updateFromNative(native); drain()
        touch(android.view.MotionEvent.ACTION_UP)
        assertEquals(1,clicked)
    }

    @Test fun queuedMusicActionsAreCanceledByPreferencesPreemptionExitAndSessionReplacement() {
        val controller=controller(media=true,simplified=true)
        val prefs=org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller,"prefs")
        val observer=org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller,"preferenceObserver")
        fun player(tag:String):MediaController {
            val token=MediaSession(context,tag).sessionToken
            return MediaController(context,token).also {
                shadowOf(it).setPackageName(context.packageName)
                shadowOf(it).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f)
                    .setActions(PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_PAUSE).build())
            }
        }
        val first=player("queued-one")
        native.player=first; controller.updateFromNative(native); drain(); revealMedia()
        shell!!.onGesture(IslandMusicGesture.LEFT)
        prefs.put("system_island_media_swipe",false); observer.onChange("system_island_media_swipe"); drain()
        assertEquals(0L,shadowOf(first.transportControls).lastPerformedAction)
        prefs.put("system_island_media_swipe",true); observer.onChange("system_island_media_swipe"); drain()
        shell!!.onGesture(IslandMusicGesture.LEFT)
        val notice=controller.showToast(IslandToastContent("Saved","other.app",null),2000)!!
        drain(); assertEquals(0L,shadowOf(first.transportControls).lastPerformedAction)
        controller.releaseToast(notice); drain()
        shell!!.onGesture(IslandMusicGesture.LEFT)
        shell!!.onDismiss(); drain()
        assertEquals(0L,shadowOf(first.transportControls).lastPerformedAction)
        val second=player("queued-two")
        native.player=second; controller.updateFromNative(native); drain(); revealMedia()
        shell!!.onGesture(IslandMusicGesture.LEFT)
        native.allowed=false; native.changed!!(); drain()
        assertEquals(0L,shadowOf(second.transportControls).lastPerformedAction)
        assertEquals(0L,shadowOf(first.transportControls).lastPerformedAction)
    }

    @Test fun disabledMediaDropsDelayedEntranceAndPreparedActionsWithoutOccupation() {
        val controller=controller(media=true,simplified=true)
        val session=MediaSession(context,"disabled-before-entry")
        val player=MediaController(context,session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build())
        native.player=player; controller.updateFromNative(native); drain()
        val prefs=org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller,"prefs")
        prefs.put("system_island_media",false)
        org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller,"preferenceObserver")
            .onChange("system_island_media")
        drain(); revealMedia()
        assertEquals(0,adds)
        assertTrue(shadowOf(player).callbacks.isEmpty())
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
    }

    @Test fun preparedMusicHasNoPauseLifetimeVisibleFrameOrStatusBarOccupation() {
        drawFrames = false
        val controller = controller(media = true)
        val session = MediaSession(context, "prepared-pause")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        native.player = player
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build())
        controller.updateFromNative(native); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750)); drain()
        val presentation = org.robolectric.util.ReflectionHelpers.getField<IslandWindowPresentation>(controller,"presentation")
        assertEquals(IslandWindowPresentation.Phase.PREPARED,presentation.phase)
        assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED,0,0f).build())
        controller.updateFromNative(native); drain()
        assertNull(org.robolectric.util.ReflectionHelpers.getField(controller,"pauseDeadline"))
        assertEquals(1,removes)
        repeat(180) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)); drain()
            assertEquals(IslandWindowPresentation.Phase.IDLE,presentation.phase)
            assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
            assertFalse(shell!!.isAttachedToWindow)
        }
        assertEquals(1,adds)
    }

    @Test fun stoppingInsideThePlayerAndRepeatedForegroundChangesNeverDrawOrOccupy() {
        val controller=controller(media=true,simplified=true)
        val session=MediaSession(context,"stop-on-home")
        val player=MediaController(context,session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        native.player=player
        controller.foregroundChanged(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build())
        controller.updateFromNative(native); drain(); revealMedia()
        val stale=shadowOf(player).callbacks.single()
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED,0,0f).build())
        controller.updateFromNative(native)
        controller.foregroundChanged("com.miui.home")
        repeat(120) {
            if(it % 20==0) controller.foregroundChanged(if(it % 40==0) "other.app" else "com.miui.home")
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)); drain()
            assertEquals(0,adds); assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        }
        stale.onPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING,0,1f).build())
        drain(); revealMedia()
        assertEquals(0,adds)
    }

    @Test fun rejectedOrExcludedTakeoverRetainsNativeBehaviorWithoutDisablingAppFeedback() {
        val controller = controller(chat = true, chatExcluded = setOf(context.packageName), simplified = true)
        assertFalse(controller.takeNotification(message(1)))
        assertEquals(0, adds)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 500))
        controller.dispose()
        val next = controller(chat = true, simplified = true)
        reject = true
        assertFalse(next.takeNotification(message(2)))
        assertNull(org.robolectric.util.ReflectionHelpers.getField(next, "chatLease"))
        reject = false
        assertTrue(next.takeNotification(message(3)))
    }

    @Test fun duplicateBindsNeverExtendNotificationExpiryAndPrivacyInvalidatesTheNativeClick() {
        val controller = controller(chat = true, simplified = true)
        var clicks = 0
        val bound = message(1) { clicks++; true }
        assertTrue(controller.takeNotification(bound))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000))
        assertTrue(controller.takeNotification(bound))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(1, removes)
        assertNull(org.robolectric.util.ReflectionHelpers.getField(controller, "chatOpen"))
        assertTrue(controller.takeNotification(message(2) { clicks++; true }))
        native.allowed = false; native.changed!!(); drain()
        shell!!.performClick()
        assertEquals(0, clicks)
        assertFalse(controller.takeNotification(message(3)))
    }

    @Test fun endingNativePreemptionCannotRevealANotificationInsideItsOwnForegroundApp() {
        val controller = controller(simplified = true)
        controller.showToast(IslandToastContent("Sender", "com.tencent.mm", null,
            notificationKey = "message", detail = "Public message"), 4000)!!
        assertEquals(1, adds)
        controller.foregroundChanged("com.tencent.mm")
        assertEquals(1, adds)
        controller.foregroundChanged("com.miui.home")
        assertEquals(1, adds) // Entering the source app consumes this already-delivered notice.
        assertNotNull(controller.showToast(IslandToastContent("Saved", "com.tencent.mm", null), 1000))
    }

    @Test fun aRealResumeInShadeWaitsButAnUnchangedCanceledDragDoesNotStartAnotherDelay() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "shade-resume")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        val callback = shadowOf(player).callbacks.single()
        callback.onPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        callback.onPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.shade = false; native.allowed = true; native.changed!!(); drain()
        assertEquals(1, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(749))
        assertEquals(1, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
        assertEquals(2, adds)
    }

    @Test fun selectedPlayingAppReturnsPromptlyOnceAndChangingSelectionCancelsTheCover() {
        val controller = controller(media = true, mediaReturn = true, mediaReturnApps = setOf(context.packageName))
        val player = launchablePlayer("selected-return")
        controller.foregroundChanged(context.packageName)
        native.player = player; controller.updateFromNative(native); revealMedia()
        assertEquals(0, adds)
        val prepared = org.robolectric.util.ReflectionHelpers.getField<IslandMediaContent?>(controller, "media")
        assertNotNull("Foreground playback must be prepared", prepared)
        assertTrue(prepared!!.playing)
        assertTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        nativeReturn(controller)
        controller.foregroundChanged("com.miui.home")
        assertNotNull("Foreground edge must create a return", org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        drain()
        assertNull("The accepted return is consumed by the direct presentation", org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Long?>(controller, "mediaRevealAt"))
        assertEquals(1, adds)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
        assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        prefs.put("system_island_media_return_apps", emptySet<String>())
        observer.onChange("system_island_media_return_apps"); drain()
        assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
        repeat(3) { controller.updateFromNative(native); drain() }
        assertEquals(1, adds)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
    }

    @Test fun returnSwitchRequiresAnExplicitAppAndKeepsTheUsualDelayOtherwise() {
        for ((enabled, apps) in listOf(false to setOf(context.packageName), true to emptySet(),
            true to setOf("other.music"), true to setOf(context.packageName))) {
            val before = adds
            val controller = controller(media = true, mediaReturn = enabled, mediaReturnApps = apps)
            val player = launchablePlayer("unselected-return-$before")
            controller.foregroundChanged(context.packageName)
            native.player = player; controller.updateFromNative(native); revealMedia()
            controller.foregroundChanged("com.miui.home"); drain()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(749)); drain()
            assertEquals(before, adds)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
            if (enabled && context.packageName in apps) {
                assertEquals("A selected task waits for native Home confirmation", before, adds)
                nativeReturn(controller); drain()
            }
            assertEquals(before + 1, adds)
            assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
            controller.dispose(); drain()
        }
    }

    @Test fun nativeCommitCanArriveAfterTheFirstHomeEdge() {
        val controller = controller(media = true, mediaReturn = true, mediaReturnApps = setOf(context.packageName))
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("commit-after-home"); controller.updateFromNative(native); revealMedia()
        org.robolectric.shadows.ShadowChoreographer.setPaused(true)
        val committedAt = android.os.SystemClock.uptimeMillis()
        controller.foregroundChanged("com.miui.home"); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
        nativeReturn(controller, committedAt); drain()
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        assertEquals(1, adds)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(shell, "motion"))
        assertEquals(1f, shell!!.alpha, 0f)
    }

    @Test fun aPlayerForegroundEdgeRenewsTheExpiredUnchangedReturnTargetBeforeGestureDown() {
        val application: android.app.Application = RuntimeEnvironment.getApplication()
        fun packets() = shadowOf(application).broadcastIntents.filter {
            it.action == IslandMusicReturnSignal.STATE && it.getBooleanExtra("active", false)
        }
        val controller = controller(media = true, mediaReturn = true,
            mediaReturnApps = setOf(context.packageName))
        native.player = launchablePlayer("rapid-open-return")
        controller.updateFromNative(native); revealMedia()
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        val before = packets().last()
        val old = IslandMusicReturnSignal.decode(before, android.os.SystemClock.uptimeMillis())!!
        val count = packets().size
        org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(IslandMusicReturnSignal.LIFETIME + 1))
        assertFalse(old.fresh(android.os.SystemClock.uptimeMillis()))
        controller.foregroundChanged(context.packageName); drain()
        val fresh = IslandMusicReturnSignal.decode(packets().last(), android.os.SystemClock.uptimeMillis())!!
        assertEquals("Same session and geometry, new readiness packet", old.frame, fresh.frame)
        assertTrue(fresh.issuedAt > old.issuedAt)
        assertEquals(count + 1, packets().size)
        controller.foregroundChanged("other.app"); drain()
        val afterSourceEdge = packets().size
        controller.foregroundChanged("another.app"); drain()
        assertEquals("Unrelated foreground changes do not renew the packet", afterSourceEdge, packets().size)
    }

    @Test fun lateNativeCommitsCannotCrossAnotherAppSwitchOrReenterThePlayer() {
        for (destinations in listOf(listOf("other.app"), listOf("other.app", "com.miui.home"), listOf(context.packageName))) {
            val controller = controller(media = true, mediaReturn = true, mediaReturnApps = setOf(context.packageName))
            controller.foregroundChanged(context.packageName)
            native.player = launchablePlayer("late-native-${destinations.last()}")
            controller.updateFromNative(native); revealMedia()
            val committedAt = android.os.SystemClock.uptimeMillis()
            controller.foregroundChanged("com.miui.home"); drain()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
            destinations.forEach(controller::foregroundChanged); drain()
            nativeReturn(controller, committedAt); drain()
            assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
            val currentShell = org.robolectric.util.ReflectionHelpers.getField<IslandActivityView>(controller, "shell")
            assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(currentShell, "mediaReturnProgress"), 0f)
            controller.dispose(); drain()
        }
    }

    @Test fun revokingTheSelectionBeforeEntryRestoresTheOrdinaryDeadline() {
        val controller = controller(media = true, mediaReturn = true, mediaReturnApps = setOf(context.packageName))
        val player = launchablePlayer("revoke-return")
        controller.foregroundChanged(context.packageName)
        native.player = player; controller.updateFromNative(native); revealMedia()
        controller.foregroundChanged("com.miui.home"); drain()
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        prefs.put("system_island_media_return", false)
        observer.onChange("system_island_media_return"); drain()
        nativeReturn(controller); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(749)); drain()
        assertEquals(0, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
        assertEquals(1, adds)
        assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
    }

    @Test fun aPauseInTheFreshReturnSnapshotAndPrivacyChangesCannotAnimateOldMusic() {
        val controller = controller(media = true, mediaReturn = true, mediaReturnApps = setOf(context.packageName))
        val player = launchablePlayer("return-pause-race")
        controller.foregroundChanged(context.packageName)
        native.player = player; controller.updateFromNative(native); revealMedia()
        nativeReturn(controller)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.foregroundChanged("com.miui.home"); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750)); drain()
        assertEquals(0, adds)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        controller.foregroundChanged(context.packageName); controller.updateFromNative(native); drain()
        controller.foregroundChanged("com.miui.home")
        native.allowed = false; native.changed!!(); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750)); drain()
        assertEquals(0, adds)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
    }

    @Test fun foregroundPlayerSuppressesOnlyMediaAndReturningHomeDoesNotReviveAnExpiredPause() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "foreground")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        controller.foregroundChanged(context.packageName)
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        assertEquals(0, adds)
        controller.foregroundChanged("com.miui.home")
        drain() // The foreground transition now requires a current serialized session snapshot.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(749))
        assertEquals(0, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(1, adds)
        controller.foregroundChanged(context.packageName)
        assertEquals(1, removes)
        val feedback = controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000)!!
        assertEquals(2, adds) // Immediate action feedback remains available inside its own app.
        controller.releaseToast(feedback)
        controller.foregroundChanged("com.miui.home"); revealMedia()
        assertEquals(3, adds)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.updateFromNative(native); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2000))
        controller.foregroundChanged(context.packageName)
        controller.foregroundChanged("com.miui.home"); revealMedia()
        assertEquals(3, adds)
    }

    private fun pip(controller: IslandActivityController, generation: Long, phase: Int,
        packageName: String = context.packageName, user: android.os.UserHandle = android.os.Process.myUserHandle(),
        taskId: Int? = 17, display: Int = 0, visible: Boolean = true, stashed: Boolean = false) {
        controller.pipChanged(IslandPipSnapshot(generation, phase,
            android.content.ComponentName(packageName, "$packageName.Player"), user, taskId, display, visible, stashed))
        drain()
    }

    @Test fun enteringAndPinnedPipNeverRevealTheSamePlayingSessionAcrossForegroundBounces() {
        val controller = controller(media = true, hideFloating = true)
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("pip-enter")
        controller.updateFromNative(native); drain()
        pip(controller, 1, 3, taskId = null) // Native swipe entry precedes onTaskAppeared.
        controller.foregroundChanged("com.miui.home"); revealMedia()
        assertEquals(0, adds)
        pip(controller, 2, 1)
        pip(controller, 3, 3)
        pip(controller, 4, 4)
        repeat(3) {
            controller.foregroundChanged(context.packageName)
            controller.foregroundChanged("com.miui.home")
            controller.updateFromNative(native); revealMedia()
            assertEquals(0, adds)
        }
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Long?>(controller, "mediaRevealAt"))
    }

    @Test fun cancelingPipAtHomeRestartsOnlyTheOrdinaryDeadlineAndIgnoresStaleEvents() {
        val controller = controller(media = true, simplified = true, hideFloating = true)
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("pip-cancel-home")
        controller.updateFromNative(native); drain()
        pip(controller, 1, 3, taskId = null)
        controller.foregroundChanged("com.miui.home"); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        val canceledAt = android.os.SystemClock.uptimeMillis()
        pip(controller, 2, 0)
        val deadline = org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "mediaRevealAt")
        assertEquals(canceledAt + IslandMotion.MEDIA_DELAY, deadline)
        pip(controller, 1, 3) // A queued callback from the canceled entry must not suppress music.
        repeat(3) { controller.updateFromNative(native); drain() }
        assertEquals(deadline, org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "mediaRevealAt"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(749)); drain()
        assertEquals(0, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
        assertEquals(1, adds)
    }

    @Test fun sourceExclusionRemainsStableAcrossVisibleHiddenAndStashedPip() {
        for (hideAll in listOf(false, true)) {
            val before = adds
            val controller = controller(media = true, simplified = true, hideFloating = hideAll)
            controller.foregroundChanged(context.packageName)
            native.player = launchablePlayer("pip-window-visibility-$hideAll")
            controller.updateFromNative(native); drain()
            pip(controller, 1, 4)
            controller.foregroundChanged("com.miui.home"); revealMedia()
            val duringPip = before + if (hideAll) 0 else 1
            assertEquals(duringPip, adds)
            if (!hideAll) {
                nativeReturn(controller)
                assertNull("Parallel media must not hijack the PiP video window",
                    org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
            }
            pip(controller, 2, 4, visible = false); revealMedia()
            assertEquals(duringPip, adds)
            repeat(3) { pip(controller, 2, 4, visible = false); controller.updateFromNative(native); revealMedia() }
            assertEquals(duringPip, adds)
            pip(controller, 3, 4); revealMedia()
            pip(controller, 4, 4, stashed = true); revealMedia()
            assertEquals(duringPip, adds)
            pip(controller, 5, 4); revealMedia()
            pip(controller, 6, 0); revealMedia()
            assertEquals(before + 1, adds)
            controller.dispose(); drain()
        }
    }

    @Test fun pipExitOrCancelInsideThePlayerStillAllowsTheNextOrdinaryHome() {
        for (entered in listOf(false, true)) {
            val before = adds
            val controller = controller(media = true, simplified = true)
            controller.foregroundChanged(context.packageName)
            native.player = launchablePlayer("pip-exit-$entered")
            controller.updateFromNative(native); drain()
            pip(controller, 1, 3)
            if (entered) { pip(controller, 2, 4); pip(controller, 3, 5) }
            pip(controller, 4, 0)
            revealMedia()
            assertEquals(before, adds)
            controller.foregroundChanged("com.miui.home"); revealMedia()
            assertEquals(before + 1, adds)
            controller.dispose(); drain()
        }
    }

    @Test fun unrelatedPipPackageUserOrDisplayCannotSuppressBackgroundMusic() {
        for (kind in 0..2) {
            val before = adds
            val controller = controller(media = true, simplified = true)
            controller.foregroundChanged(context.packageName)
            native.player = launchablePlayer("unrelated-pip-$kind")
            controller.updateFromNative(native); drain()
            pip(controller, 1, 4,
                packageName = if (kind == 0) "other.video" else context.packageName,
                user = if (kind == 1) android.os.UserHandle.getUserHandleForUid(1000000) else android.os.Process.myUserHandle(),
                display = if (kind == 2) 1 else 0)
            controller.foregroundChanged("com.miui.home"); revealMedia()
            assertEquals(before + 1, adds)
            controller.dispose(); drain()
        }
    }

    @Test fun anExcludedNativeVideoSelectionKeepsOnePreviouslySelectedIndependentMusicSession() {
        val controller = controller(media = true, hideFloating = true, simplified = true)
        controller.foregroundChanged("com.miui.home")
        val music = launchablePlayer("retained-music")
        native.player = music; controller.updateFromNative(native); revealMedia()
        assertEquals(1, adds)
        val original = shell
        pip(controller, 1, 4, packageName = "other.video")
        val video = launchablePlayer("floating-video")
        shadowOf(video).setPackageName("other.video")
        native.player = video; controller.updateFromNative(native); revealMedia()
        assertSame(music, org.robolectric.util.ReflectionHelpers.getField<MediaController>(controller, "selected"))
        assertSame(original, shell)
        assertEquals(1, adds)
        assertEquals(0, removes)
        shell!!.onDismiss(); drain()
        assertNull(org.robolectric.util.ReflectionHelpers.getField<MediaController?>(controller, "backgroundPlayer"))
        controller.updateFromNative(native); revealMedia()
        assertEquals(1, adds)
        native.player = null; controller.updateFromNative(native); drain()
        assertNull(org.robolectric.util.ReflectionHelpers.getField<MediaController?>(controller, "selected"))
    }

    @Test fun rebuildingAMediaControllerForTheSameTokenDoesNotReplaceItsOwnerOrWindow() {
        val controller = controller(media = true, simplified = true)
        val player = launchablePlayer("same-token")
        native.player = player; controller.updateFromNative(native); revealMedia()
        val original = shell
        val replacement = MediaController(context, player.sessionToken)
        shadowOf(replacement).setPackageName(context.packageName)
        shadowOf(replacement).setPlaybackState(player.playbackState)
        native.player = replacement; controller.updateFromNative(native); revealMedia()
        assertSame(player, org.robolectric.util.ReflectionHelpers.getField<MediaController>(controller, "selected"))
        assertSame(original, shell)
        assertEquals(1, adds)
        assertEquals(0, removes)
    }

    @Test fun pipCancelsAPreparedWindowBeforeItsFirstDrawAndDoesNotReenterWhenPinned() {
        val controller = controller(media = true, hideFloating = true)
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("pip-before-draw")
        controller.updateFromNative(native); drain()
        drawFrames = false
        controller.foregroundChanged("com.miui.home")
        revealMedia()
        assertEquals(1, adds)
        val presentation = org.robolectric.util.ReflectionHelpers.getField<IslandWindowPresentation>(controller, "presentation")
        assertEquals(IslandWindowPresentation.Phase.PREPARED, presentation.phase)
        pip(controller, 1, 3)
        assertEquals(1, removes)
        assertEquals(IslandWindowPresentation.Phase.IDLE, presentation.phase)
        pip(controller, 2, 4)
        drawFrames = true
        controller.updateFromNative(native); revealMedia()
        assertEquals(1, adds)
    }

    @Test fun pipAlreadyPinnedBeforeTheFirstMediaSnapshotCannotFlashOnRecreation() {
        val controller = controller(media = true, simplified = true, hideFloating = true)
        pip(controller, 7, 4)
        controller.foregroundChanged("com.miui.home")
        native.player = launchablePlayer("pip-recreate")
        controller.updateFromNative(native); revealMedia()
        assertEquals(0, adds)
        pip(controller, 8, 0)
        revealMedia()
        assertEquals(1, adds)
    }

    @Test fun aPublishedShellSnapshotBlocksEntryBeforeItsQueuedMainCallbackArrives() {
        var latest: IslandPipSnapshot? = null
        val controller = controller(media = true, simplified = true, hideFloating = true, pipStateReader = { latest })
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("shell-main-race")
        controller.updateFromNative(native); drain()
        controller.foregroundChanged("com.miui.home"); drain()
        latest = IslandPipSnapshot(1, 3, android.content.ComponentName(context.packageName, "Player"), android.os.Process.myUserHandle())
        revealMedia() // The expiry/render path sees the already-published native state.
        assertEquals(0, adds)
        latest = IslandPipSnapshot(2, 0)
        controller.pipChanged(latest!!); revealMedia()
        assertEquals(1, adds)
    }

    @Test fun aShellChangeBetweenRenderAndFirstDrawVetoesAndCoalescesCleanup() {
        var latest: IslandPipSnapshot? = null
        val controller = controller(media = true, hideFloating = true, pipStateReader = { latest })
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("shell-first-frame-race")
        controller.updateFromNative(native); drain()
        drawFrames = false
        controller.foregroundChanged("com.miui.home"); revealMedia()
        assertEquals(1, adds)
        val presentation = org.robolectric.util.ReflectionHelpers.getField<IslandWindowPresentation>(controller, "presentation")
        latest = IslandPipSnapshot(1, 3, android.content.ComponentName(context.packageName, "Player"), android.os.Process.myUserHandle())
        assertFalse(presentation.onPreDraw())
        assertFalse(presentation.onPreDraw())
        assertTrue(org.robolectric.util.ReflectionHelpers.getField(controller, "frameRecheckQueued"))
        drain()
        assertEquals("One rejected prepared window is removed once", 1, removes)
        assertEquals(IslandWindowPresentation.Phase.IDLE, presentation.phase)
        assertFalse(org.robolectric.util.ReflectionHelpers.getField(controller, "frameRecheckQueued"))
        latest = IslandPipSnapshot(2, 0)
        controller.pipChanged(latest!!)
        drawFrames = true; revealMedia()
        assertEquals(2, adds)
    }

    @Test fun anAlreadyVisibleIslandExitsSmoothlyForPipWhileLockStillRemovesItImmediately() {
        val controller = controller(media = true, hideFloating = true)
        native.player = launchablePlayer("pip-visible-exit")
        controller.updateFromNative(native); revealMedia()
        val presentation = org.robolectric.util.ReflectionHelpers.getField<IslandWindowPresentation>(controller, "presentation")
        assertEquals(IslandWindowPresentation.Phase.VISIBLE, presentation.phase)
        assertTrue(shell!!.isAttachedToWindow)
        assertFalse(shell!!.simplified)
        assertTrue(android.animation.ValueAnimator.areAnimatorsEnabled())
        controller.pipChanged(IslandPipSnapshot(1, 3,
            android.content.ComponentName(context.packageName, "Player"), android.os.Process.myUserHandle(), 17))
        assertEquals(0, removes)
        assertEquals(IslandWindowPresentation.Phase.EXITING, presentation.phase)
        assertTrue(presentation.onPreDraw())
        native.allowed = false; native.changed!!(); drain()
        assertEquals("Keyguard must not wait for the soft PiP exit", 1, removes)
        assertEquals(IslandWindowPresentation.Phase.IDLE, presentation.phase)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandMotion.EXIT + 50)); drain()
        assertEquals(1, removes)
    }

    @Test fun aRealPipRequestDuringPreparationVetoesTheFrameWhileNativeStateIsStillZero() {
        var latest: IslandPipSnapshot? = null
        val controller = controller(media = true, hideFloating = true, pipStateReader = { latest })
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("pip-request-before-task")
        controller.updateFromNative(native); drain()
        drawFrames = false
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); revealMedia()
        val presentation = org.robolectric.util.ReflectionHelpers.getField<IslandWindowPresentation>(controller, "presentation")
        latest = IslandPipSnapshot(1, 0, android.content.ComponentName(context.packageName, "Player"),
            android.os.Process.myUserHandle(), 17, pendingEntry = true)
        assertFalse(presentation.onPreDraw())
        drain()
        assertEquals(1, removes)
        assertEquals(IslandWindowPresentation.Phase.IDLE, presentation.phase)
    }

    @Test fun homeAdmissionWaitsForAutoPipBeforeApplyingEitherFloatingWindowPolicy() {
        for (hideAll in listOf(false, true)) {
            val before = adds
            var queries = 0
            val probe = IslandPipCandidatePort { pkg, user -> queries++; IslandPipCandidate(pkg, user, 17) }
            val controller = controller(media = true, hideFloating = hideAll, pipCandidate = probe)
            controller.foregroundChanged(context.packageName)
            native.player = launchablePlayer("late-auto-pip-$hideAll")
            controller.updateFromNative(native); drain()
            controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); revealMedia()
            assertEquals("No premature 750ms entrance", before, adds)
            assertEquals(1, queries)
            pip(controller, 1, 3)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(150)); drain()
            assertEquals(before, adds)
            pip(controller, 2, 4); drain()
            assertEquals(before + if (hideAll) 0 else 1, adds)
            repeat(3) { controller.updateFromNative(native); revealMedia() }
            assertEquals("One settled entrance, no half entrance followed by another", before + if (hideAll) 0 else 1, adds)
            pip(controller, 3, 0); revealMedia()
            assertEquals(before + 1, adds)
            controller.dispose(); drain()
        }
    }

    @Test fun delayedFirstMediaAtHomeStillUsesTheOutgoingAutoPipAdmissionGate() {
        val controller = controller(media = true, pipCandidate = IslandPipCandidatePort { pkg, user ->
            IslandPipCandidate(pkg, user, 17)
        })
        controller.foregroundChanged(context.packageName)
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        native.player = launchablePlayer("first-media-after-home-pip")
        controller.updateFromNative(native); revealMedia()
        assertEquals(0, adds)
        pip(controller, 1, 3); pip(controller, 2, 4); drain()
        assertEquals(1, adds)
    }

    @Test fun aNegativeAutoPipProbePreservesTheConfirmedMusicWindowReturnDeadline() {
        var queries = 0
        val controller = controller(media = true, mediaReturn = true, mediaReturnApps = setOf(context.packageName),
            pipCandidate = IslandPipCandidatePort { _, _ -> queries++; null })
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("ordinary-music-fast-gate")
        controller.updateFromNative(native); drain()
        nativeReturn(controller)
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandMotion.MEDIA_RETURN_DELAY)); drain()
        assertEquals(1, queries)
        assertEquals(1, adds)
    }

    @Test fun aMusicAutoPipAdmissionGateDoesNotBlockOrdinaryFeedback() {
        val controller = controller(media = true, hideFloating = true, simplified = true,
            pipCandidate = IslandPipCandidatePort { pkg, user -> IslandPipCandidate(pkg, user, 17) })
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("auto-pip-feedback")
        controller.updateFromNative(native); drain()
        controller.foregroundChanged(IslandMusicReturnSignal.LAUNCHER); revealMedia()
        assertEquals(0, adds)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
        assertEquals(1, adds)
    }

    @Test fun sourceExclusionKeepsApprovedMessagesAndOrdinaryFeedbackInTheSameWindow() {
        val controller = controller(chat = true, hideFloating = true, simplified = true)
        controller.foregroundChanged("com.miui.home")
        assertTrue(controller.takeNotification(message(801)))
        val original = shell
        assertEquals(1, adds)
        pip(controller, 1, 4, packageName = "other.video")
        assertEquals(0, removes)
        assertTrue(controller.takeNotification(message(802)))
        assertSame(original, shell)
        assertEquals(1, adds)
        assertNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
        pip(controller, 2, 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4500)); drain()
        assertEquals(1, adds) // Replacing a notice never queues it for later replay.
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
        assertEquals(2, adds)
    }

    @Test fun floatingWindowSuppressionInvalidatesMusicReturnAndRejectsAnOldCommitAfterRecovery() {
        val controller = controller(media = true, mediaReturn = true,
            mediaReturnApps = setOf(context.packageName), hideFloating = true)
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("floating-music-return")
        controller.updateFromNative(native); drain()
        val committedAt = android.os.SystemClock.uptimeMillis()
        nativeReturn(controller, committedAt)
        assertNotNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        assertEquals(0, adds)
        val publisher = org.robolectric.util.ReflectionHelpers.getField<Any>(controller, "musicReturnPublisher")
        pip(controller, 1, 3, packageName = context.packageName)
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "musicReturnPublisher"))
        assertTrue(org.robolectric.util.ReflectionHelpers.getField<Boolean>(publisher, "closed"))
        controller.foregroundChanged("com.miui.home"); drain()
        pip(controller, 2, 0)
        val ordinary = org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "mediaRevealAt")
        nativeReturn(controller, committedAt); drain()
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        assertEquals(ordinary, org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "mediaRevealAt"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        nativeReturn(controller); drain()
        assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        assertEquals(1, adds)
        revealMedia()
        assertEquals(1, adds)
    }

    @Test fun theFloatingWindowSwitchRemainsAnInstallationSnapshotAcrossPreferenceRefreshes() {
        for (installed in listOf(false, true)) {
            val before = adds
            val controller = controller(media = true, hideFloating = installed)
            pip(controller, 1, 4)
            val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
            val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
            prefs.put("system_island_hide_in_floating_window", !installed)
            observer.onChange("system_island_hide_in_floating_window")
            observer.onChange("system_island_height"); observer.onChange(null); drain()
            val current = org.robolectric.util.ReflectionHelpers.getField<IslandOptions>(controller, "options")
            assertEquals(installed, current.hideInFloatingWindow)
            controller.foregroundChanged("com.miui.home")
            native.player = launchablePlayer("installed-source-$installed")
            controller.updateFromNative(native); revealMedia()
            assertEquals(before + if (installed) 0 else 1, adds)
            val lease = controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000)
            assertNotNull(lease)
            controller.dispose(); drain()
        }
    }

    @Test fun freeformInventoryIsEventDrivenAndDisabledPolicyPerformsNoQueries() {
        var reads = 0
        var windows = true
        val source = IslandFreeformPort { reads++; windows }
        val disabled = controller(freeform = source)
        repeat(10) { disabled.refreshFloatingWindows(); drain() }
        assertEquals(0, reads)
        disabled.dispose(); drain()
        val controller = controller(hideFloating = true, freeform = source)
        assertEquals(1, reads)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000)); drain()
        assertEquals(1, reads)
        windows = false
        controller.refreshFloatingWindows(); drain()
        assertEquals(2, reads)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
        controller.dispose()
        repeat(10) { controller.refreshFloatingWindows(); drain() }
        assertEquals(2, reads)
    }

    @Test fun aSupersededFreeformReadCannotHideTheNewStateOrResurrectADisposedOwner() {
        var reads = 0
        var replaceOnRead = false
        var current: IslandActivityController? = null
        val source = IslandFreeformPort {
            reads++
            if (replaceOnRead) {
                replaceOnRead = false
                current!!.refreshFloatingWindows()
                true // This generation was canceled while the native query was in flight.
            } else false
        }
        val controller = controller(hideFloating = true, freeform = source)
        current = controller
        replaceOnRead = true
        controller.refreshFloatingWindows(); drain()
        assertEquals(3, reads)
        assertNotNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
        controller.dispose(); drain()
        assertEquals(adds, removes)
    }

    @Test fun duplicateForegroundAndMediaCallbacksDoNotRestartAnOrdinaryHomeEntrance() {
        val controller = controller(media = true)
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("duplicate-home")
        controller.updateFromNative(native); drain()
        controller.foregroundChanged("com.miui.home"); drain()
        val deadline = org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "mediaRevealAt")
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            controller.foregroundChanged("com.miui.home")
            controller.updateFromNative(native); drain()
            assertEquals(deadline, org.robolectric.util.ReflectionHelpers.getField<Long>(controller, "mediaRevealAt"))
            assertEquals(0, adds)
        }
        val remaining = deadline - android.os.SystemClock.uptimeMillis()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(remaining)); drain()
        assertEquals(1, adds)
        val entrance = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(shell, "motion")
        repeat(8) {
            controller.foregroundChanged("com.miui.home")
            controller.updateFromNative(native); drain()
            assertEquals(1, adds)
            assertEquals(0, removes)
            assertSame(entrance, org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(shell, "motion"))
        }
    }

    @Test fun canceledHomeReturnCannotRevealOldMusicAndTheNextRealHomeStillWorks() {
        val controller = controller(media = true, simplified = true)
        controller.foregroundChanged(context.packageName)
        native.player = launchablePlayer("cancel-home")
        controller.updateFromNative(native); drain()
        controller.foregroundChanged("com.miui.home"); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        controller.foregroundChanged(context.packageName); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000)); drain()
        assertEquals(0, adds)
        controller.foregroundChanged("com.miui.home"); revealMedia()
        assertEquals(1, adds)
        assertEquals(0, removes)
    }

    @Test fun pausingInsideThePlayerThenReturningImmediatelyNeverCreatesAPausedIsland() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "foreground-pause")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        controller.foregroundChanged(context.packageName)
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        assertEquals(0, adds)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.updateFromNative(native); drain()
        controller.foregroundChanged("com.miui.home")
        revealMedia()
        assertEquals(0, adds) // Still inside the original two-second pause lifetime.
        controller.updateFromNative(native); drain(); revealMedia()
        assertEquals(0, adds)
    }

    @Test fun returningBeforeThePauseReadCompletesCannotFlashEvenWithAToastAttached() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "pause-return-race")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        controller.foregroundChanged(context.packageName)
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        val feedback = controller.showToast(IslandToastContent("Saved", context.packageName, null), 5000)!!
        assertEquals(1, adds)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.updateFromNative(native)
        controller.foregroundChanged("com.miui.home") // Snapshot coroutine is still queued.
        drain()
        controller.updateFromNative(native); drain() // Repeated paused metadata is not new activity.
        controller.releaseToast(feedback)
        revealMedia()
        assertEquals(1, adds)
        assertEquals(1, removes)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        controller.updateFromNative(native); drain(); revealMedia()
        assertEquals(2, adds) // Genuine playback still returns after the normal entrance delay.
    }

    @Test fun enteringThePlayerDuringPauseGraceConsumesTheGraceWithoutReopeningOnHome() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "visible-pause")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.updateFromNative(native); drain()
        assertEquals(0, removes) // Visible music retains its existing pause grace.
        controller.foregroundChanged(context.packageName)
        controller.foregroundChanged("com.miui.home")
        controller.updateFromNative(native); drain(); revealMedia()
        assertEquals(1, adds)
        assertEquals(1, removes)
    }

    @Test fun repeatedPausedCallbacksAndANewHiddenPlayPauseCannotInventVisibleMusic() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "shade-edges")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        fun event(value: Int) {
            val state = PlaybackState.Builder().setState(value, 0, 1f).build()
            shadowOf(player).setPlaybackState(state)
            shadowOf(player).callbacks.single().onPlaybackStateChanged(state); drain()
        }
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        event(PlaybackState.STATE_PAUSED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000))
        event(PlaybackState.STATE_PAUSED)
        native.shade = false; native.allowed = true; native.changed!!(); drain()
        assertEquals(1, adds)
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        event(PlaybackState.STATE_PLAYING)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000))
        event(PlaybackState.STATE_PAUSED)
        native.shade = false; native.allowed = true; native.changed!!(); drain()
        // r14.25.2: an older displayed session is not evidence that this new PLAYING
        // activation was displayed. The old contract let this sequence flash a paused island.
        repeat(150) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16)); drain()
            assertEquals(1,adds); assertEquals(1,removes)
            assertNull(DynamicIslandStatusBarFade.ownerRef?.get())
        }
    }

    @Test fun holdingAndWaitingNeverCreatesAMediaPanelOrChangesCapsuleHeight() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "idle")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        val height = latestParams!!.height
        assertFalse(shell!!.isLongClickable)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10_000))
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Updated").build()
        controller.updateFromNative(native); drain()
        assertEquals(height, latestParams!!.height)
        assertEquals(3, shell!!.childCount)
        assertTrue(shell!!.isMedia)
        assertEquals(1, adds)
    }

    @Test fun shadeCancellationRetainsTheSelectedSessionButLockedPrivacyDropsItImmediately() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "shade")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        val selectedCallback = shadowOf(player).callbacks.single()
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        assertSame(selectedCallback, shadowOf(player).callbacks.single())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200))
        native.shade = false; native.allowed = true; native.changed!!(); drain()
        assertEquals(2, adds) // No fresh 750ms entrance delay for a canceled shade gesture.
        native.allowed = false; native.changed!!(); drain()
        assertEquals(0, shadowOf(player).callbacks.size)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        native.allowed = true; native.changed!!(); drain(); revealMedia()
        assertEquals(2, adds)
    }

    @Test fun quickSettingsModeFeedbackWaitsForShadeReturnButNeverCrossesLockOrNativeDuplicate() {
        val controller = controller(simplified = true)
        val mode = IslandSystemActivity(IslandSystemKind.WIFI, 1)
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        controller.systemActivity(mode)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(0, adds)
        native.shade = false; native.allowed = true; native.changed!!(); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(0))
        assertEquals(1, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(1, adds)
        assertTrue(shell!!.contentDescription.toString().contains("Wi-Fi"))
        assertEquals(1, adds)
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.DATA, 0))
        native.shade = false; native.changed!!(); drain() // Now locked, even though shade was transient.
        native.allowed = true; native.changed!!(); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(1, adds)
    }

    @Test fun slowDndShadeReturnUsesOnlyTheOriginalRemainingLifetime() {
        val controller = controller(simplified = true)
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.DND, 1))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(4500))
        assertEquals(0, adds)
        native.shade = false; native.allowed = true; native.changed!!(); drain()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500))
        assertEquals(1, removes)
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.DND, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(6100))
        native.shade = false; native.allowed = true; native.changed!!(); drain()
        assertEquals(1, adds)
    }

    @Test fun relatedModesDoNotRotateAndAnIndependentOperationReplacesTheCurrentFeedback() {
        val controller = controller(simplified = true)
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.AIRPLANE, 1))
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.WIFI, 0))
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.BLUETOOTH, 0))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandSystemFeedback.COLLECT))
        assertEquals(1, adds)
        assertTrue(shell!!.contentDescription.toString().contains(IslandSystemActivity(IslandSystemKind.AIRPLANE, 1).content(context).detail!!))
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.NFC, 1))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandSystemFeedback.COLLECT))
        assertTrue(shell!!.contentDescription.toString().contains(IslandSystemActivity(IslandSystemKind.NFC, 1).content(context).detail!!))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandSystemFeedback.DWELL + 1000))
        assertEquals(1, adds)
        assertEquals(1, removes)
    }

    @Test fun systemFeedbackPreemptsAppToastAndItsOldLeaseCannotRemoveTheNewActivity() {
        val controller = controller(simplified = true)
        val lease = controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000)!!
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.AIRPLANE, 1))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(199))
        assertEquals(1, adds)
        assertTrue(shell!!.contentDescription.toString().contains("Saved"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertTrue(shell!!.contentDescription.toString().contains(
            IslandSystemActivity(IslandSystemKind.AIRPLANE, 1).content(context).detail!!))
        val before = removes
        controller.releaseToast(lease)
        assertEquals(before, removes)
        assertTrue(shell!!.contentDescription.toString().contains(
            IslandSystemActivity(IslandSystemKind.AIRPLANE, 1).content(context).detail!!))
    }

    @Test fun initialPlaybackWaitsThreeQuartersOfASecondWithoutExtendingOnMetadataAndPrivacyCancelsIt() {
        val controller = controller(media = true)
        val session = MediaSession(context, "delayed")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player
        controller.updateFromNative(native); drain()
        assertEquals(0, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Latest").build()
        controller.updateFromNative(native); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(349))
        assertEquals(0, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
        assertEquals(1, adds)
        assertTrue(shell!!.contentDescription.toString().contains("Latest"))
        native.allowed = false; native.changed!!(); drain()
        native.allowed = true; native.changed!!(); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        native.allowed = false; native.changed!!(); drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(1, adds)
        native.allowed = true; native.changed!!(); drain()
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.updateFromNative(native); drain()
        revealMedia()
        assertEquals(1, adds) // Very short playback before reveal must not flash a paused island.
    }

    @Test fun pausedMetadataUpdatesCannotRenewTheGracePeriodAndStopReleasesTheCallback() {
        val controller = controller(media = true)
        val session = MediaSession(context, "paused")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Paused track").build()
        controller.updateFromNative(native)
        drain()
        revealMedia()
        assertEquals(1, adds)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        controller.updateFromNative(native)
        drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Updated title").build()
        controller.updateFromNative(native)
        drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1300))
        assertEquals(1, removes)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
        controller.updateFromNative(native)
        drain()
        assertEquals(0, shadowOf(player).callbacks.size)
        assertEquals(1, adds)
    }

    @Test fun nativeSelectedSessionSurvivesToastAndReleasesOnReplacementDestructionAndPrivacy() {
        val controller = controller(media = true)
        fun player(tag: String): MediaController {
            val session = MediaSession(context, tag)
            return MediaController(context, session.sessionToken).also {
                shadowOf(it).setPackageName(context.packageName)
                shadowOf(it).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .setActions(PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT).build())
            }
        }
        val first = player("one")
        native.player = first
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "First track").build()
        controller.updateFromNative(native)
        drain()
        revealMedia()
        assertEquals(1, adds)
        assertTrue(shell!!.contentDescription.toString().contains("First track"))
        assertEquals(1, shadowOf(first).callbacks.size)
        repeat(10) { controller.updateFromNative(native) }
        drain()
        assertEquals(1, shadowOf(first).callbacks.size)
        val feedback = controller.showToast(IslandToastContent("Saved", "app", null), 2000)!!
        assertEquals("Saved", shell!!.contentDescription)
        controller.releaseToast(feedback)
        assertTrue(shell!!.contentDescription.toString().contains("First track"))
        drain() // Admission starts after the replacement content has reached pre-draw.
        shell!!.onGesture(IslandMusicGesture.LEFT)
        scheduler.runCurrent()
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(first.transportControls).lastPerformedAction)
        shell!!.onGesture(IslandMusicGesture.LEFT)
        scheduler.runCurrent()
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(first.transportControls).lastPerformedAction)
        val staleDestruction = shadowOf(first).callbacks.single()
        val second = player("two")
        native.player = second
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Second track").build()
        controller.updateFromNative(native)
        drain()
        revealMedia()
        assertEquals(0, shadowOf(first).callbacks.size)
        assertEquals(1, shadowOf(second).callbacks.size)
        staleDestruction.onSessionDestroyed()
        assertTrue(shell!!.contentDescription.toString().contains("Second track"))
        native.allowed = false
        native.changed!!()
        drain()
        assertEquals(0, shadowOf(second).callbacks.size)
        assertEquals(1, removes)
        native.allowed = true
        native.changed!!()
        drain()
        revealMedia()
        assertEquals(1, shadowOf(second).callbacks.size)
        shadowOf(second).executeOnSessionDestroyed()
        drain()
        assertEquals(0, shadowOf(second).callbacks.size)
        assertEquals(2, removes)
        controller.updateFromNative(native)
        drain()
        assertEquals(2, adds)
    }

    @Test fun hiddenTimeStillExpiresAndDisposalRemovesEveryOwnedRegistration() {
        val controller = controller()
        assertNotNull(controller.showToast(IslandToastContent("Saved", "app", null), 1000))
        native.allowed = false
        native.changed!!()
        assertEquals(1, removes)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        native.allowed = true
        native.changed!!()
        assertEquals(1, adds)
        val staleCallback = native.changed!!
        controller.dispose()
        assertEquals(0, native.listeners)
        staleCallback()
        assertNull(controller.showToast(IslandToastContent("Saved", "app", null), 1000))
        assertEquals(1, adds)
    }

    @Test fun nativeWindowFailureMustKeepTheOrdinaryToastAndUnknownSafetyFailsClosed() {
        reject = true
        val controller = controller()
        assertNull(controller.showToast(IslandToastContent("Saved", "app", null), 1000))
        assertEquals(0, adds)
        controller.dispose()
        reject = false
        val prefs = PrefMap()
        owner = IslandActivityController(context, prefs, IslandOptions(true, true, false), native, dispatcher)
        assertNull(owner!!.showToast(IslandToastContent("Saved", "app", null), 1000))
        assertEquals(0, adds)
    }


    @Test fun pausedSessionsStayHiddenAcrossUnlockShadeAndControllerRebinding() {
        val controller = controller(media = true)
        val session = MediaSession(context, "background")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        native.player = player
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Background").build()
        fun state(value: Int) {
            shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(value, 0, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE).build())
            controller.updateFromNative(native); drain()
            if (value == PlaybackState.STATE_PLAYING) revealMedia()
        }
        state(PlaybackState.STATE_PAUSED)
        assertEquals(0, adds)
        repeat(3) {
            native.allowed = false; native.changed!!(); drain()
            native.allowed = true; native.changed!!(); drain()
            assertEquals(0, adds)
        }
        state(PlaybackState.STATE_PLAYING)
        assertEquals(1, adds)
        state(PlaybackState.STATE_PAUSED)
        shell!!.onGesture(IslandMusicGesture.DOUBLE_TAP); scheduler.runCurrent()
        assertNotEquals(PlaybackState.ACTION_PLAY, shadowOf(player.transportControls).lastPerformedAction)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2100))
        assertEquals(1, removes)
        repeat(3) {
            native.allowed = false; native.changed!!(); drain()
            native.allowed = true; native.changed!!(); drain()
            assertEquals(1, adds)
        }
    }

    @Test fun upwardDismissalSurvivesTrackChangesAndPrivacyForTheSameSession() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "dismissed")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player
        controller.updateFromNative(native); drain()
        revealMedia()
        assertEquals(1, adds)
        shell!!.onDismiss()
        assertEquals(1, removes)
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Next track").build()
        controller.updateFromNative(native); drain()
        native.allowed = false; native.changed!!(); drain()
        native.allowed = true; native.changed!!(); drain()
        assertEquals(1, adds)
    }

    @Test fun playbackResumeReopensDismissalButHiddenPausesDuplicatesAndPrivacyDoNot() {
        val controller = controller(media = true, simplified = true)
        val session = MediaSession(context, "transport")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        native.player = player
        fun playback(value: Int, callbackOnly: Boolean = false) {
            val state = PlaybackState.Builder().setState(value, 0, 1f).build()
            shadowOf(player).setPlaybackState(state)
            if (callbackOnly) shadowOf(player).callbacks.single().onPlaybackStateChanged(state)
            else controller.updateFromNative(native)
            drain()
            if (value == PlaybackState.STATE_PLAYING) revealMedia()
        }
        playback(PlaybackState.STATE_PLAYING)
        assertEquals(1, adds)
        shell!!.onDismiss()
        assertEquals(1, removes)
        playback(PlaybackState.STATE_PLAYING)
        assertEquals(1, adds)
        playback(PlaybackState.STATE_PAUSED, callbackOnly = true)
        assertEquals(1, adds) // A real pause edge cannot create an island that was hidden.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2100))
        assertEquals(1, removes)
        playback(PlaybackState.STATE_PAUSED, callbackOnly = true)
        assertEquals(1, adds)
        playback(PlaybackState.STATE_PLAYING, callbackOnly = true)
        assertEquals(2, adds)
        shell!!.onDismiss()
        // Both callbacks arrive before the serialized snapshot lane can run.
        val callback = shadowOf(player).callbacks.single()
        callback.onPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        callback.onPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        drain()
        assertEquals(2, adds)
        val revealAt = org.robolectric.util.ReflectionHelpers.getField<Long>(controller,"mediaRevealAt")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(revealAt-android.os.SystemClock.uptimeMillis()-1))
        assertEquals(2, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
        assertEquals(3, adds)
        shell!!.onDismiss()
        playback(PlaybackState.STATE_PAUSED, callbackOnly = true)
        assertEquals(3, adds)
        native.allowed = false; native.changed!!(); drain()
        native.allowed = true; native.changed!!(); drain()
        assertEquals(3, adds)
        playback(PlaybackState.STATE_PAUSED)
        assertEquals(3, adds)
        playback(PlaybackState.STATE_PLAYING)
        val stale = shadowOf(player).callbacks.single()
        native.allowed = false; native.changed!!(); drain()
        native.allowed = true; native.changed!!(); drain()
        revealMedia()
        shell!!.onDismiss()
        val beforeStale = adds
        stale.onPlaybackStateChanged(PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build())
        stale.onSessionDestroyed()
        drain()
        assertEquals(beforeStale, adds)
        assertEquals(1, shadowOf(player).callbacks.size)
    }

    @Test fun notificationNeverOpensADetailLayerAndReleasesBackToTheSameMedia() {
        val controller = controller(media = true)
        val session = MediaSession(context, "underlying")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player; controller.updateFromNative(native); drain(); revealMedia()
        val mediaHeight = latestParams!!.height
        val lease = controller.showToast(IslandToastContent("Sender", "app", null,
            notificationKey = "message", detail = "Public message detail"), 4000)!!
        val width = latestParams!!.width
        assertFalse(shell!!.isLongClickable)
        assertEquals(mediaHeight, latestParams!!.height)
        assertEquals(width, latestParams!!.width)
        assertTrue(shell!!.contentDescription.toString().contains("Public message detail"))
        controller.releaseToast(lease)
        assertTrue(shell!!.isMedia)
        assertEquals(mediaHeight, latestParams!!.height)
    }

    @Test fun artworkAndRetiredPanelPreferencesKeepBoundedPixelsAndSymmetricSurfaces() {
        val controller = controller(media = true, width = 600)
        val session = MediaSession(context, "artwork")
        val player = MediaController(context, session.sessionToken)
        shadowOf(player).setPackageName(context.packageName)
        shadowOf(player).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        native.player = player
        val source = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
        native.track = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "Album")
            .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, source).build()
        controller.updateFromNative(native); drain()
        revealMedia()
        assertEquals(0f, firstAlpha, 0.001f)
        val icon = ((shell!!.getChildAt(0) as android.widget.LinearLayout).getChildAt(0) as android.widget.ImageView)
        val prepared = icon.drawable
        assertEquals(128, (prepared as android.graphics.drawable.BitmapDrawable).bitmap.width)
        controller.updateFromNative(native); drain()
        assertSame(prepared, icon.drawable)
        assertFalse(source.isRecycled)
        assertFalse(shell!!.isLongClickable)
        val density = context.resources.displayMetrics.density
        val expectedWidth = IslandSizing.collapsed(density,1080,(184*density).toInt(),(40*density).toInt(),(24*density),0f,
            heightDp = IslandSizing.automaticHeight(0))
        assertEquals(expectedWidth, shell!!.width)
        assertEquals(metricsWidth + 2 * (shell!!.parent as IslandTouchWindow).margin, latestParams!!.width)
        val insets = org.robolectric.util.ReflectionHelpers.getField<Rect>(latestParams, "surfaceInsets")
        // InputMonitor exports one surfaceInset scalar, chosen from the left edge.
        // Asymmetric values render correctly but move or collapse the real touch frame.
        assertEquals(insets.left, insets.top)
        assertEquals(insets.left, insets.right)
        assertEquals(insets.left, insets.bottom)
        assertTrue(insets.left >= (expectedWidth - 184 * density) / 2)
        assertTrue(org.robolectric.util.ReflectionHelpers.getField<Boolean>(latestParams, "hasManualSurfaceInsets"))
    }

    @Test @Config(qualifiers = "xxhdpi")
    fun physicalCutoutSafeEdgesAndRotationReachTheActualSingleWindow() {
        val camera = Rect(492, 0, 588, 120)
        metricsInsets = WindowInsets.Builder().setDisplayCutout(android.view.DisplayCutout(
            android.graphics.Insets.of(0, 120, 0, 0), null, camera, null, null)).build()
        val controller = controller(media = true, simplified = true)
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        native.player = launchablePlayer("physical-camera")
        controller.updateFromNative(native); drain(); revealMedia()
        for (alignment in listOf(0, 2)) {
            prefs.put("system_island_position", alignment.toString())
            observer.onChange("system_island_position"); drain()
            val root = shell!!.parent as IslandTouchWindow
            val visualLeft = latestParams!!.x + (metricsWidth - latestParams!!.width) / 2 + shell!!.left
            if (alignment == 0) assertEquals(0, visualLeft)
            else assertEquals(metricsWidth, visualLeft + shell!!.width)
            assertEquals(0, org.robolectric.util.ReflectionHelpers.getField<Int>(shell, "cameraGap"))
            assertTrue(if (alignment == 0) visualLeft + shell!!.width <= camera.left else visualLeft >= camera.right)
        }
        val config = org.robolectric.util.ReflectionHelpers.getField<android.content.ComponentCallbacks>(controller, "configuration")
        metricsWidth = 2400; metricsHeight = 1080
        config.onConfigurationChanged(android.content.res.Configuration(context.resources.configuration)); drain()
        assertFalse(shell!!.isAttachedToWindow)
        assertNull((shell!!.parent as IslandTouchWindow).parent)
        metricsWidth = 1080; metricsHeight = 2400
        config.onConfigurationChanged(android.content.res.Configuration(context.resources.configuration)); drain(); revealMedia()
        assertNotNull(shell!!.parent)
        assertEquals(1, (shell!!.parent as IslandTouchWindow).childCount)
    }

    private fun launchablePlayer(tag: String, activity: android.app.PendingIntent? = null): MediaController {
        val session = MediaSession(context, tag)
        org.robolectric.util.ReflectionHelpers.setField(session.sessionToken, "mUid", context.applicationInfo.uid)
        return MediaController(context, session.sessionToken).also {
            shadowOf(it).setPackageName(context.packageName)
            shadowOf(it).setSessionActivity(activity)
            shadowOf(it).setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f)
                .setActions(PlaybackState.ACTION_SKIP_TO_NEXT).build())
        }
    }

    @Test fun downMappingAndSingleTapOpenOnlyTheCurrentPlayerWithAbsentOrCanceledSessionActivity() {
        val controller = controller(media = true, simplified = true)
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        prefs.put("system_island_music_down_action", IslandMusicActions.OPEN_PLAYER)
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        observer.onChange("system_island_music_down_action"); drain()
        val application = shadowOf(org.robolectric.RuntimeEnvironment.getApplication())
        for (entry in listOf("missing", "cancelled", "valid")) {
            val intent = android.content.Intent("island.player.$entry").setPackage(context.packageName)
                .setClassName(context.packageName, "tv.withaibuild.customiuizer.MainActivity")
            val pending = if (entry == "missing") null else android.app.PendingIntent.getActivity(context, 701,
                intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_CANCEL_CURRENT)
            if (entry == "cancelled") pending!!.cancel()
            native.player = launchablePlayer(entry, pending)
            controller.updateFromNative(native); drain(); revealMedia()
            for (gesture in listOf(false, true)) {
                if (gesture) {
                    val now = android.os.SystemClock.uptimeMillis()
                    for ((action, y) in listOf(android.view.MotionEvent.ACTION_DOWN to 20f,
                        android.view.MotionEvent.ACTION_MOVE to 100f, android.view.MotionEvent.ACTION_UP to 100f)) {
                        val event = android.view.MotionEvent.obtain(now, now + 80, action, shell!!.width / 2f, y, 0)
                        try { shell!!.dispatchTouchEvent(event) } finally { event.recycle() }
                    }
                } else shell!!.performClick()
                drain()
                val started = application.nextStartedActivity
                assertNotNull("$entry / down=$gesture; uid=${context.applicationInfo.uid}; " +
                    "user=${org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "foregroundUser")}; " +
                    "diagnostics=${org.robolectric.util.ReflectionHelpers.getField<Any>(org.robolectric.util.ReflectionHelpers.getField<Any>(controller, "diagnostics"), "emitted")}; " +
                    "launch=${context.packageManager.getLaunchIntentForPackage(context.packageName)}", started)
                assertEquals(context.packageName, started!!.component!!.packageName)
                assertNull(application.nextStartedActivity)
            }
        }
    }

    @Test fun aMissingPackageAndAWorkProfileTokenCannotOpenTheOwnersPlayer() {
        val controller = controller(media = true, simplified = true)
        for (profile in listOf(false, true)) {
            val player = launchablePlayer("restricted-$profile")
            if (profile) org.robolectric.util.ReflectionHelpers.setField(player.sessionToken, "mUid", context.applicationInfo.uid + 1_000_000)
            else shadowOf(player).setPackageName("uninstalled.player.example")
            native.player = player; controller.updateFromNative(native); drain(); revealMedia()
            if (profile) { assertNotNull(shell); shell!!.performClick(); drain() }
            else assertNull(shell) // The existing snapshot policy already rejects an uninstalled source.
            assertNull(shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).nextStartedActivity)
        }
    }

    @Test fun changingGeometryKeepsQueuedMusicActionsWhileSwitchingPlayersInvalidatesOldLaunches() {
        val controller = controller(media = true, simplified = true)
        val first = launchablePlayer("old")
        native.player = first; controller.updateFromNative(native); drain(); revealMedia()
        shell!!.onGesture(IslandMusicGesture.LEFT)
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        prefs.put("system_island_horizontal_offset", 90)
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        observer.onChange("system_island_horizontal_offset"); drain()
        assertEquals(PlaybackState.ACTION_SKIP_TO_NEXT, shadowOf(first.transportControls).lastPerformedAction)
        shell!!.performClick()
        native.player = launchablePlayer("replacement")
        controller.updateFromNative(native); drain()
        assertNull(shadowOf(org.robolectric.RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test fun expandedMediaReleasesItsHiddenSpectrumAndCompactMediaReacquiresOnlyOnce() {
        var acquired = 0; var released = 0; var subscribed = 0
        val backend = object : IslandAudioSpectrum.Backend {
            override fun permitted() = true
            override fun session(player: MediaController, user: android.os.UserHandle?) = 42
            override fun subscribe(changed: () -> Unit): AutoCloseable { subscribed++; return AutoCloseable { subscribed-- } }
            override fun capture(session: Int, fft: (ByteArray, Int) -> Unit): AutoCloseable {
                acquired++; fft(ByteArray(512) { 64 }, 48_000_000)
                return AutoCloseable { released++ }
            }
        }
        val controller = controller(media = true, waveformMode = 2, spectrumBackend = backend)
        native.player = launchablePlayer("expanded-wave-lease")
        controller.updateFromNative(native); revealMedia()
        val original = shell!!
        assertEquals(1, acquired); assertEquals(1, subscribed)
        org.robolectric.util.ReflectionHelpers.getField<IslandFormState>(controller, "forms").expand(android.os.SystemClock.uptimeMillis())
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertTrue(original.expanded)
        assertEquals("A layout without wave bars has no active effect", acquired, released)
        assertEquals(0, subscribed)
        repeat(4) { controller.updateFromNative(native); drain() }
        assertEquals(1, acquired)
        org.robolectric.util.ReflectionHelpers.getField<IslandFormState>(controller, "forms").collapse()
        org.robolectric.util.ReflectionHelpers.callInstanceMethod<Boolean>(controller, "render"); drain()
        assertFalse(original.expanded)
        assertEquals(2, acquired); assertEquals(1, subscribed)
        assertSame(original, shell); assertEquals(1, adds)
        controller.dispose(); drain()
        assertEquals(acquired, released); assertEquals(0, subscribed)
    }

    @Test fun realCaptureFollowsActualVisibilityPauseShadeScreenOffAndDisposal() {
        var acquired = 0; var released = 0; var subscribed = 0
        val backend = object : IslandAudioSpectrum.Backend {
            override fun permitted() = true
            override fun session(player: MediaController, user: android.os.UserHandle?) = 42
            override fun subscribe(changed: () -> Unit): AutoCloseable { subscribed++; return AutoCloseable { subscribed-- } }
            override fun capture(session: Int, fft: (ByteArray, Int) -> Unit): AutoCloseable {
                acquired++; fft(ByteArray(512) { 64 }, 48_000_000)
                return AutoCloseable { released++ }
            }
        }
        val controller = controller(media = true, waveformMode = 2, spectrumBackend = backend)
        val player = launchablePlayer("wave-owner")
        native.player = player; controller.updateFromNative(native); drain()
        assertEquals(0, acquired)
        revealMedia(); drain()
        assertEquals(1, acquired); assertEquals(1, subscribed)
        val paused = PlaybackState.Builder().setState(PlaybackState.STATE_PAUSED, 0, 0f).build()
        shadowOf(player).setPlaybackState(paused); shadowOf(player).executeOnPlaybackStateChanged(paused)
        controller.updateFromNative(native); drain()
        assertEquals(acquired, released); assertEquals(0, subscribed)
        val playing = PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build()
        shadowOf(player).setPlaybackState(playing); shadowOf(player).executeOnPlaybackStateChanged(playing)
        controller.updateFromNative(native); drain(); revealMedia(); drain()
        assertTrue(acquired > released)
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        prefs.put("system_island_waveform_mode", "0"); observer.onChange("system_island_waveform_mode"); drain()
        assertEquals(acquired, released); assertEquals(0, subscribed)
        assertEquals(View.GONE, shell!!.getChildAt(2).visibility)
        prefs.put("system_island_waveform_mode", "2"); observer.onChange("system_island_waveform_mode"); drain()
        assertEquals(acquired - 1, released); assertEquals(1, subscribed)
        native.shade = true; native.allowed = false; native.changed!!(); drain()
        assertEquals(acquired, released); assertEquals(0, subscribed)
        native.shade = false; controller.onScreenStateChanged(false); drain()
        controller.dispose(); drain()
        assertEquals(acquired, released); assertEquals(0, subscribed)
    }

    private class Native : IslandNativePort {
        var allowed = true
        var failSafety = false
        var shade = false
        var listeners = 0
        var changed: (() -> Unit)? = null
        var player: MediaController? = null
        var track: MediaMetadata? = null
        override fun controller(owner: Any) = player
        override fun metadata(owner: Any) = track
        override fun isAllowed(owner: Any): Boolean { check(!failSafety); return allowed }
        override fun isShadeBlocked(owner: Any) = shade
        override fun trust(params: WindowManager.LayoutParams) = Unit
        override fun listen(owner: Any, changed: () -> Unit): Any { this.changed = changed; listeners++; return Any() }
        override fun unlisten(owner: Any, listener: Any) { listeners-- }
    }
}

/** Robolectric 4.17 lacks the API 33 package-launch IntentSender Binder implementation. */
@org.robolectric.annotation.Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
class IslandLaunchPackageManagerShadow : org.robolectric.shadows.ShadowApplicationPackageManager() {
    @org.robolectric.annotation.Implementation(minSdk = 33)
    protected fun getLaunchIntentSenderForPackage(packageName: String): android.content.IntentSender {
        val intent = getLaunchIntentForPackage(packageName) ?: throw android.content.pm.PackageManager.NameNotFoundException(packageName)
        return android.app.PendingIntent.getActivity(org.robolectric.RuntimeEnvironment.getApplication(), 0,
            intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT).intentSender
    }
}
