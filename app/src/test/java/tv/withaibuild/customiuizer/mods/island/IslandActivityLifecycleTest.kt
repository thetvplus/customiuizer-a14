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
@Config(sdk = [34])
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
    private val window = Proxy.newProxyInstance(WindowManager::class.java.classLoader,
        arrayOf(WindowManager::class.java)) { _, method, args ->
        when (method.name) {
            "getCurrentWindowMetrics" -> WindowMetrics(Rect(0, 0, 1080, 2400), WindowInsets.Builder().build())
            "addView" -> { if (reject) throw WindowManager.BadTokenException("injected"); shell = args!![0] as IslandActivityView; latestParams = args[1] as WindowManager.LayoutParams; firstAlpha = shell!!.alpha; adds++;
                if (!drawFrames) shell!!.visibility = View.INVISIBLE
                frame.addView(shell, FrameLayout.LayoutParams(latestParams!!.width, latestParams!!.height)); null }
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
    @After fun cleanup() { owner?.dispose(); scheduler.runCurrent(); host.finish(); Dispatchers.resetMain() }

    private fun controller(media: Boolean = false, width: Int = 328, simplified: Boolean = false,
        chat: Boolean = false, chatExcluded: Set<String> = emptySet(),
        notificationApps: Set<String> = setOf(context.packageName)): IslandActivityController {
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3"); put("system_toast_capsule", true)
            put("system_island_media", media); put("system_island_controls", true)
            put("system_island_expanded_width", width)
            put("system_island_simplified", simplified)
            put("system_island_chat", chat)
            put("system_island_chat_excluded_apps", chatExcluded)
            put("system_island_notification_apps", notificationApps)
        }
        return IslandActivityController(context, prefs, IslandOptions.read(prefs), native, dispatcher).also {
            owner = it
            it.connectSafety(Any())
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
    private fun revealMedia() {
        drain()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(750)); drain()
        shell?.let { org.robolectric.util.ReflectionHelpers.getField<android.animation.ValueAnimator?>(it, "motion")?.end() }
        drain()
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
        assertEquals(expectedWidth, latestParams!!.width)
        val insets = org.robolectric.util.ReflectionHelpers.getField<Rect>(latestParams, "surfaceInsets")
        // InputMonitor exports one surfaceInset scalar, chosen from the left edge.
        // Asymmetric values render correctly but move or collapse the real touch frame.
        assertEquals(insets.left, insets.top)
        assertEquals(insets.left, insets.right)
        assertEquals(insets.left, insets.bottom)
        assertTrue(insets.left >= (expectedWidth - 184 * density) / 2)
        assertTrue(org.robolectric.util.ReflectionHelpers.getField<Boolean>(latestParams, "hasManualSurfaceInsets"))
    }

    private class Native : IslandNativePort {
        var allowed = true
        var shade = false
        var listeners = 0
        var changed: (() -> Unit)? = null
        var player: MediaController? = null
        var track: MediaMetadata? = null
        override fun controller(owner: Any) = player
        override fun metadata(owner: Any) = track
        override fun isAllowed(owner: Any) = allowed
        override fun isShadeBlocked(owner: Any) = shade
        override fun trust(params: WindowManager.LayoutParams) = Unit
        override fun listen(owner: Any, changed: () -> Unit): Any { this.changed = changed; listeners++; return Any() }
        override fun unlisten(owner: Any, listener: Any) { listeners-- }
    }
}
