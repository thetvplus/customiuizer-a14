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
@Config(sdk = [34], shadows = [IslandLaunchPackageManagerShadow::class])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class IslandActivityLifecycleTest {
    private var owner: IslandActivityController? = null
    private var adds = 0
    private var removes = 0
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
        geometryHost: View? = null): IslandActivityController {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3"); put("system_toast_capsule", true)
            put("system_island_media", media); put("system_island_controls", true)
            put("system_island_expanded_width", width)
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
            freeform = freeform, pipStateReader = pipStateReader, geometryHost = geometryHost).also {
            owner = it
            it.connectSafety(Any())
            it.refreshFloatingWindows()
            drain()
        }
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
    private fun revealMedia() {
        drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750)); drain()
        shell?.let { org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(it, "motion")?.end() }
        drain()
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
        assertNotNull("Selected playback must retain one pending return", org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        assertEquals(android.os.SystemClock.uptimeMillis() + IslandMotion.MEDIA_RETURN_DELAY,
            org.robolectric.util.ReflectionHelpers.getField<Long?>(controller, "mediaRevealAt"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandMotion.MEDIA_RETURN_DELAY - 1))
        assertEquals(0, adds)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1)); drain()
        assertEquals(1, adds)
        val motion = org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion")
        assertEquals(IslandMotion.MEDIA_RETURN, motion.duration)
        motion.currentPlayTime = 140
        assertTrue(org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress") < 1f)
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        prefs.put("system_island_media_return_apps", emptySet<String>())
        observer.onChange("system_island_media_return_apps"); drain()
        assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
        motion.currentPlayTime = 280 // An old clock cannot resume the canceled cover.
        assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(shell, "mediaReturnProgress"), 0f)
        motion.end()
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
        assertNotNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(IslandMotion.MEDIA_RETURN_DELAY)); drain()
        assertEquals(1, adds)
        assertEquals(IslandMotion.MEDIA_RETURN,
            org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator>(shell, "motion").duration)
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
        nativeReturn(controller)
        controller.foregroundChanged("com.miui.home"); drain()
        val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
        val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
        prefs.put("system_island_media_return", false)
        observer.onChange("system_island_media_return"); drain()
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
        val controller = controller(media = true)
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
        val controller = controller(media = true, simplified = true)
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

    @Test fun hiddenOrStashedPipRestoresMusicUnlessTheWholeIslandSwitchIsEnabled() {
        for (hideAll in listOf(false, true)) {
            val before = adds
            val controller = controller(media = true, simplified = true, hideFloating = hideAll)
            controller.foregroundChanged(context.packageName)
            native.player = launchablePlayer("pip-window-visibility-$hideAll")
            controller.updateFromNative(native); drain()
            pip(controller, 1, 4)
            controller.foregroundChanged("com.miui.home"); revealMedia()
            assertEquals(before, adds)
            pip(controller, 2, 4, visible = false); revealMedia()
            assertEquals(before + if (hideAll) 0 else 1, adds)
            repeat(3) { pip(controller, 2, 4, visible = false); controller.updateFromNative(native); revealMedia() }
            assertEquals(before + if (hideAll) 0 else 1, adds)
            pip(controller, 3, 4); revealMedia()
            pip(controller, 4, 4, stashed = true); revealMedia()
            assertEquals(before + if (hideAll) 0 else 2, adds)
            pip(controller, 5, 4); revealMedia()
            pip(controller, 6, 0); revealMedia()
            assertEquals(before + if (hideAll) 1 else 3, adds)
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

    @Test fun pipCancelsAPreparedWindowBeforeItsFirstDrawAndDoesNotReenterWhenPinned() {
        val controller = controller(media = true)
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
        val controller = controller(media = true, simplified = true)
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
        val controller = controller(media = true, simplified = true, pipStateReader = { latest })
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

    @Test fun hidingTheEntireIslandRetainsNativeToastAndHeadsUpAndDropsOldFeedback() {
        val controller = controller(chat = true, hideFloating = true)
        controller.foregroundChanged("com.miui.home")
        assertTrue(controller.takeNotification(message(801)))
        assertEquals(1, adds)
        pip(controller, 1, 4, packageName = "other.video")
        assertEquals(1, removes)
        assertFalse(controller.takeNotification(message(802)))
        assertNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
        controller.systemActivity(IslandSystemActivity(IslandSystemKind.DND, 1))
        pip(controller, 2, 0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3000)); drain()
        assertEquals(1, adds)
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
        val publisher = org.robolectric.util.ReflectionHelpers.getField<Any>(controller, "musicReturnPublisher")
        pip(controller, 1, 3, packageName = "other.video")
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
        assertNotNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(controller, "mediaReturn"))
        revealMedia()
        assertEquals(1, adds)
    }

    @Test fun theFloatingWindowSwitchRemainsAnInstallationSnapshotAcrossPreferenceRefreshes() {
        for (installed in listOf(false, true)) {
            val controller = controller(hideFloating = installed)
            pip(controller, 1, 4, packageName = "other.video")
            val prefs = org.robolectric.util.ReflectionHelpers.getField<PrefMap>(controller, "prefs")
            val observer = org.robolectric.util.ReflectionHelpers.getField<tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver>(controller, "preferenceObserver")
            prefs.put("system_island_hide_in_floating_window", !installed)
            observer.onChange("system_island_hide_in_floating_window")
            observer.onChange("system_island_height"); observer.onChange(null); drain()
            val current = org.robolectric.util.ReflectionHelpers.getField<IslandOptions>(controller, "options")
            assertEquals(installed, current.hideInFloatingWindow)
            val lease = controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000)
            assertEquals(installed, lease == null)
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
        assertNull(controller.showToast(IslandToastContent("Saved", context.packageName, null), 1000))
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
        val expectedWidth = IslandSizing.collapsed(density,1080,(184*density).toInt(),(40*density).toInt(),(24*density),0f)
        assertEquals(expectedWidth + 2 * (shell!!.parent as IslandTouchWindow).margin, latestParams!!.width)
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
            val visualLeft = latestParams!!.x + (metricsWidth - latestParams!!.width) / 2 + root.margin
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
