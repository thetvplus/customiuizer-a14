package tv.withaibuild.customiuizer.mods.island

import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks

import android.app.PendingIntent
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.service.notification.StatusBarNotification
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference

internal data class IslandMediaContent(
    val token: MediaSession.Token, val packageName: String, val title: String, val artist: String?,
    val icon: Drawable?, val playing: Boolean, val actions: Long, val launch: PendingIntent?,
    val playbackState: Int,
)

internal object IslandMediaPolicy {
    fun active(state: Int): Boolean = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED ||
        state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING ||
        state == PlaybackState.STATE_FAST_FORWARDING || state == PlaybackState.STATE_REWINDING ||
        state == PlaybackState.STATE_SKIPPING_TO_NEXT || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
        state == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
    fun playing(state: Int) = state == PlaybackState.STATE_PLAYING
    fun pauseResume(previous: Int?, current: Int): Boolean = previous != null && previous != current &&
        active(previous) && active(current) &&
        ((previous == PlaybackState.STATE_PAUSED) != (current == PlaybackState.STATE_PAUSED))
    fun canSkip(request: Long, actions: Long): Boolean =
        (request == PlaybackState.ACTION_SKIP_TO_NEXT || request == PlaybackState.ACTION_SKIP_TO_PREVIOUS) &&
            actions and request != 0L
}

/** Owned by one attached default-display status bar. All presentation state stays on Main. */
internal class IslandActivityController(
    private val context: Context,
    private val prefs: PrefMap,
    private var options: IslandOptions,
    private val access: IslandNativePort,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    dismissal: IslandActivityState.MediaDismissal = IslandActivityState.MediaDismissal(),
) : ScreenStateController.ScreenStateListener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + ModuleHelper.coroutineFailureHandler)
    private val state = IslandActivityState<IslandToastContent, IslandMediaContent>(dismissal)
    private val shell = IslandActivityView(context)
    private val presentation = IslandWindowPresentation(shell,
        eligible = { !disposed && attached && state.allowed &&
            (lastContent != null || exiting) })
    private val window = context.getSystemService(WindowManager::class.java)
    private var attached = false
    private var exiting = false
    private var exitGeneration = 0L
    private var disposed = false
    private var safetyOwner: Any? = null
    private var safetyListener: Any? = null
    private var nativeManager = WeakReference<Any>(null)
    private var selected: MediaController? = null
    private var destroyedToken: MediaSession.Token? = null
    private var sessionJob: Job? = null
    private var sessionEpoch = 0L
    private var readJob: Job? = null
    private var actionJob: Job? = null
    private var chatLease: IslandActivityState.Lease? = null
    private var chatKey: String? = null
    private var chatPostTime = 0L
    private var chatNotification: StatusBarNotification? = null
    private var chatOpen: (() -> Boolean)? = null
    private var foregroundUser: UserHandle? = if (options.chat) ModuleHelper.guarded(null) {
        val id = XposedHelpers.callStaticMethod(ActivityManager::class.java, "getCurrentUser") as Int
        // UserHandle.getIdentifier/constructors are hidden in the SDK; cache the public UID
        // conversion once per foreground-user change (A14 PER_USER_RANGE = 100000).
        if (id < 0) null else UserHandle.getUserHandleForUid(Math.multiplyExact(id, 100000))
    } else null
    private val snapshotMutex = Mutex()
    private val sessionMutex = Mutex()
    private val actionMutex = Mutex()
    private val geometryMutex = Mutex()
    private var geometryJob: Job? = null
    private var geometryGeneration = 0L
    private data class Screen(val width: Int, val height: Int, val holeWidth: Int, val holeTop: Int,
        val holeCenter: Float)
    private var screen: Screen? = null
    @Volatile private var actionEpoch = 0L
    private var readGeneration = 0L
    private var media: IslandMediaContent? = null
    private var foregroundPackage: String? = null
    private data class AppIcon(val token: MediaSession.Token, val epoch: Long,
        val label: String, val drawable: Drawable?)
    private val iconCacheLock = Any()
    @Volatile private var iconEpoch = 0L
    @Volatile private var appIcon: AppIcon? = null
    private data class Artwork(val source: WeakReference<Bitmap>, val epoch: Long, val drawable: Drawable)
    @Volatile private var artwork: Artwork? = null
    private var pauseDeadline: Long? = null
    private var observedPlaybackState: Int? = null
    private var mediaRevealAt: Long? = null
    private var shadeBlocked = false
    private var shadeBaseline = false
    private var shadePlaybackState: Int? = null
    private var shadePlaybackAt = 0L
    private var shadePlaybackReactivated = false
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastTop = 0
    private var lastContent: Any? = null
    private var systemActivities: IslandSystemActivities? = null
    private val systemFeedbackSlot = IslandSystemFeedback()
    private data class SystemPresentation(val lease: IslandActivityState.Lease, val activity: IslandSystemActivity)
    private var systemPresentation: SystemPresentation? = null
    private val systemFeedback = Runnable { ModuleHelper.guarded { presentSystemFeedback() } }
    private var userReceiverRegistered = false
    private var configurationRegistered = false
    private val params = WindowManager.LayoutParams(1, 1, 2017,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
        setTitle("CustoMIUIzer Dynamic Island")
        // A translated root still gets cropped by its Surface. Keep bounded rendering outsets
        // for drag and collapse, without enlarging the logical window or its touchable frame.
        (XposedHelpers.getObjectField(this, "surfaceInsets") as Rect).set(dp(80), dp(80), dp(80), dp(80))
        XposedHelpers.setBooleanField(this, "hasManualSurfaceInsets", true)
        XposedHelpers.setBooleanField(this, "preservePreviousSurfaceInsets", false)
        access.trust(this)
    }
    private val expiry = Runnable { ModuleHelper.guarded { render() } }
    private val windowOutsets = XposedHelpers.getObjectField(params, "surfaceInsets") as Rect
    private val nativeRefresh = Runnable { ModuleHelper.guarded {
        if (!disposed) nativeManager.get()?.let { refreshFromNative(it) }
    } }
    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key != null && key != "system_strong_toast_mode" &&
                key != "system_strong_toast_island_offset" && key != "system_toast_capsule" &&
                !key.startsWith("system_island_")) return
            onMain {
                val previousOptions = options
                options = IslandOptions.read(prefs)
                if (previousOptions.mediaSwipe && !options.mediaSwipe) {
                    actionEpoch++
                    actionJob?.cancel(); actionJob = null
                }
                if (!options.enabled) dispose()
                else {
                    if (!options.toast && state.toast(SystemClock.uptimeMillis())?.let {
                            !it.status && it.notificationKey == null } == true) state.clearToast()
                    if (!options.media) { clearMedia(); state.allowMediaAgain() }
                    if (!options.chat || state.toast(SystemClock.uptimeMillis())?.packageName in options.chatExcludedApps) clearChat()
                    nativeManager.get()?.let { updateFromNative(it) }
                    render()
                }
            }
        }
    }
    private val configuration = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = ModuleHelper.guarded {
            onMain {
                systemActivities?.configurationChanged(newConfig)
                readGeneration++
                invalidateIcon()
                media = null
                state.setMedia(null, null)
                hide()
                shell.refreshConfiguration()
                screen = null
                refreshGeometry()
                nativeManager.get()?.let { updateFromNative(it) }
                render()
            }
        }
        override fun onLowMemory() = Unit
    }
    private val userReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = ModuleHelper.guarded {
            // System-protected user-switch broadcasts only; discard the old user's feedback and session.
            if (intent.action == USER_SWITCHED) {
                val userId = intent.getIntExtra("android.intent.extra.user_handle", -1)
                foregroundUser = if (userId < 0) null else UserHandle.getUserHandleForUid(Math.multiplyExact(userId, 100000))
                destroyedToken = selected?.sessionToken
                    ?: nativeManager.get()?.let { access.controller(it)?.sessionToken }
                readGeneration++
                readJob?.cancel()
                actionJob?.cancel()
                state.clear()
                foregroundPackage = null
                clearSystemActivity()
                systemActivities?.resetForUserSwitch()
                clearChat()
                clearMedia()
                hide()
            }
        }
    }

    init {
        shell.onDismiss = { state.dismiss(); render() }
        shell.onOpen = { openPlayer() }
        shell.onControl = { control(it) }
        state.allowed = false
        try {
            ModuleHelper.observePreferenceChange(preferenceObserver, this)
            ScreenStateController.addListener(context, this)
            context.registerReceiver(userReceiver, IntentFilter(USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
            userReceiverRegistered = true
            context.registerComponentCallbacks(configuration)
            configurationRegistered = true
            refreshGeometry()
            if (options.enabled) systemActivities = IslandSystemActivities(context, scope, ioDispatcher, ::systemActivity)
                .also { it.start() }
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            dispose()
            throw error
        }
    }

    fun connectSafety(owner: Any) = onMain {
        if (safetyOwner === owner) return@onMain
        disconnectSafety()
        safetyOwner = owner
        safetyListener = access.listen(owner) { onMain { refreshSafety() } }
        refreshSafety()
    }

    private fun refreshSafety() {
        val allowed = !disposed && options.enabled && ScreenStateController.isScreenOn() &&
            safetyOwner?.let { access.isAllowed(it) } == true
        val shade = !disposed && options.enabled && ScreenStateController.isScreenOn() &&
            safetyOwner?.let { access.isShadeBlocked(it) } == true
        if (state.allowed == allowed && shadeBlocked == shade) return
        val wasShade = shadeBlocked
        if (!allowed && shade) pauseSystemFeedback()
        shadeBlocked = shade
        state.allowed = allowed
        presentation.refresh()
        if (!state.allowed) {
            clearChat()
            if (!shade) { clearMedia(); clearSystemActivity() }
            else {
                actionEpoch++; actionJob?.cancel()
                readGeneration++; readJob?.cancel(); handler.removeCallbacks(nativeRefresh)
            }
        } else {
            if (wasShade) shadeBaseline = true
            nativeManager.get()?.let { updateFromNative(it) }
            scheduleSystemFeedback()
        }
        render()
    }

    override fun onScreenStateChanged(isOn: Boolean) = onMain { refreshSafety() }

    fun foregroundChanged(packageName: String?) = onMain {
        if (foregroundPackage == packageName) return@onMain
        val previous = foregroundPackage
        foregroundPackage = packageName
        media?.let {
            if (packageName == it.packageName) {
                mediaRevealAt = null
                if (it.playbackState == PlaybackState.STATE_PAUSED) {
                    pauseDeadline = null
                    state.setMedia(it.token, null)
                }
            }
            else if (previous == it.packageName && it.playbackState != PlaybackState.STATE_PAUSED)
                mediaRevealAt = SystemClock.uptimeMillis() + IslandMotion.MEDIA_DELAY
        }
        render()
    }

    fun systemActivity(activity: IslandSystemActivity) = onMain {
        if (disposed || !options.enabled || (!state.allowed && !shadeBlocked)) return@onMain
        val now = SystemClock.uptimeMillis()
        systemFeedbackSlot.offer(activity, now)?.takeIf { state.allowed }
            ?.let { presentSystemEntry(it, now) }
        scheduleSystemFeedback()
    }

    fun independentSystemAction(kind: IslandSystemKind) = onMain {
        systemFeedbackSlot.independent(kind, SystemClock.uptimeMillis())
    }

    private fun clearSystemActivity() {
        handler.removeCallbacks(systemFeedback)
        systemFeedbackSlot.clear()
        systemPresentation?.let { state.release(it.lease) }
        systemPresentation = null
    }

    private fun pauseSystemFeedback() {
        handler.removeCallbacks(systemFeedback)
        systemPresentation?.let {
            state.release(it.lease)
        }
        systemPresentation = null
    }

    /** At most one pending feedback, with its original deadline even while the shade is open. */
    private fun scheduleSystemFeedback() {
        handler.removeCallbacks(systemFeedback)
        if (disposed || !options.enabled || !state.allowed) return
        val now = SystemClock.uptimeMillis()
        val ready = systemFeedbackSlot.readyAt(now) ?: return
        handler.postAtTime(systemFeedback, ready)
    }

    private fun presentSystemFeedback() {
        if (disposed || !options.enabled || !state.allowed) return
        val now = SystemClock.uptimeMillis()
        val ready = systemFeedbackSlot.readyAt(now) ?: return
        if (ready > now) {
            scheduleSystemFeedback()
            return
        }
        val entry = systemFeedbackSlot.poll(now) ?: return
        presentSystemEntry(entry, now)
    }

    private fun presentSystemEntry(entry: IslandSystemFeedback.Entry, now: Long) {
        val content = entry.activity.content(context)
        val animation = if (!options.simplified && ValueAnimator.areAnimatorsEnabled())
            IslandMotion.ENTER else 0L
        val remaining = entry.expiresAt - now
        if (remaining <= 0) return
        val lease = state.offer(content, now, minOf(remaining, animation + IslandSystemFeedback.DWELL))
        systemPresentation = SystemPresentation(lease, entry.activity)
        render()
    }

    fun showToast(content: IslandToastContent, duration: Long): IslandActivityState.Lease? {
        if (Looper.myLooper() != Looper.getMainLooper() || disposed || !options.enabled || !options.toast ||
            !state.allowed || content.packageName in options.excludedApps ||
            content.packageName == "com.android.systemui") return null
        if (systemPresentation?.let { state.owns(it.lease, SystemClock.uptimeMillis()) } == true) return null
        val text = IslandToastText.resolve(content.text, true) ?: return null
        val geometry = geometry(label = text) ?: return null
        if (!shell.fitsToast(text, geometry.width, geometry.gap, geometry.height)) return null
        val value = content.copy(text = text,
            icon = content.icon ?: context.getDrawable(android.R.drawable.ic_dialog_info))
        val lease = state.offer(value, SystemClock.uptimeMillis(), duration)
        // Only suppress the native Toast window after insertion of this shell actually succeeded.
        if (!render()) { state.release(lease); render(); return null }
        return lease
    }

    fun releaseToast(lease: IslandActivityState.Lease) = onMain { state.release(lease); render() }

    fun notificationPosted(sbn: StatusBarNotification) = onMain {
        // Posting never suppresses anything. Updates invalidate the old click before a new
        // native-approved bind, including updates that are no longer allowed to alert.
        if (chatKey == sbn.key && chatPostTime != sbn.postTime) { clearChat(); render() }
    }

    /** Synchronous transaction on the final native heads-up bind; false always retains native UI. */
    fun takeNotification(bound: IslandNotificationAccess.Bound): Boolean {
        val sbn = bound.notification
        if (Looper.myLooper() != Looper.getMainLooper() || disposed || !options.chat || !state.allowed ||
            sbn.packageName in options.excludedApps ||
            sbn.packageName in options.chatExcludedApps || sbn.packageName == foregroundPackage ||
            sbn.user != foregroundUser || sbn.notification.contentIntent == null) return false
        val now = SystemClock.uptimeMillis()
        if (chatNotification === sbn)
            return chatLease?.let { attached && !exiting && state.owns(it, now) } == true
        // Native system feedback and an already accepted ordinary Toast keep their lease.
        if (state.toast(now)?.let { it.notificationKey == null } == true) return false
        val rank = bound.ranking
        val prepared = IslandChatNotifications.prepare(context, sbn, rank.importance,
            rank.matchesInterruptionFilter(), rank.suppressedVisualEffects,
            nativeIcon = bound.icon) ?: return false
        clearChat()
        chatKey = sbn.key; chatPostTime = sbn.postTime; chatNotification = sbn; chatOpen = bound.open
        chatLease = state.offer(prepared, now, 4000)
        if (!render()) { clearChat(); return false }
        ModuleHelper.guarded { shell.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) }
        return true
    }

    fun notificationRemoved(key: String) = onMain {
        if (chatKey == key) { clearChat(); render() }
    }

    private fun clearChat() {
        chatLease?.let { state.release(it) }; chatLease = null
        chatKey = null; chatPostTime = 0
        chatNotification = null
        chatOpen = null
    }

    fun updateFromNative(owner: Any) = onMain {
        nativeManager = WeakReference(owner)
        if (!options.media || !state.allowed) return@onMain
        readGeneration++
        readJob?.cancel()
        handler.removeCallbacks(nativeRefresh)
        handler.post(nativeRefresh)
    }

    private fun refreshFromNative(owner: Any) {
        if (!options.media || !state.allowed) return
        val controller = access.controller(owner)
        if (controller == null || controller.sessionToken == destroyedToken) {
            clearMedia()
            render()
            return
        }
        if (selected?.sessionToken != controller.sessionToken) {
            clearMedia()
            selected = controller
        }
        val metadata = access.metadata(owner)
        val readOptions = options
        val currentIconEpoch = iconEpoch
        val generation = ++readGeneration
        readJob?.cancel()
        readJob = scope.launch {
            val prepared = withContext(ioDispatcher) {
                snapshotMutex.withLock { ensureActive(); prepare(controller, metadata, readOptions, currentIconEpoch) }
            }
            if (disposed || !state.allowed || generation != readGeneration ||
                selected?.sessionToken != controller.sessionToken) return@launch
            val now = SystemClock.uptimeMillis()
            if (prepared == null) {
                clearMedia()
            } else {
                if (sessionJob == null) watchSession(controller)
                observedPlaybackState = prepared.playbackState
                if (mediaRevealAt?.let { now >= it } == true) mediaRevealAt = null
                val paused = prepared.playbackState == PlaybackState.STATE_PAUSED
                // Repeated native snapshots are not new activity. A real pause/resume edge
                // of the selected session may re-open an explicitly dismissed island.
                val previous = media?.takeIf { it.token == prepared.token }?.playbackState
                val fromShade = shadeBaseline
                shadeBaseline = false
                if (!fromShade && IslandMediaPolicy.pauseResume(previous, prepared.playbackState)) state.allowMediaAgain()
                // Only a real callback while dragging can start a new pause lifetime. A
                // changed snapshot after a hidden period is insufficient to invent activity.
                if (fromShade && paused && (previous != PlaybackState.STATE_PAUSED ||
                        shadePlaybackReactivated && shadePlaybackState == PlaybackState.STATE_PAUSED)) {
                    val remaining = if (shadePlaybackReactivated && shadePlaybackState == PlaybackState.STATE_PAUSED)
                        shadePlaybackAt + IslandMotion.PAUSE_LIFETIME - now else 0L
                    if (remaining <= 0L) {
                        media = prepared; pauseDeadline = null; mediaRevealAt = null
                        state.setMedia(prepared.token, null); clearShadePlayback(); render(); return@launch
                    }
                    pauseDeadline = shadePlaybackAt + IslandMotion.PAUSE_LIFETIME -
                        if (!options.simplified && ValueAnimator.areAnimatorsEnabled()) IslandMotion.EXIT else 0L
                }
                // Privacy/owner rebinding clears the observed media, so an old paused
                // background snapshot still cannot start a new pause grace period.
                if (paused && media == null) {
                    media = prepared
                    state.setMedia(prepared.token, null)
                    pauseDeadline = null
                    render()
                    return@launch
                }
                // Pause grace belongs to music already on screen. A retained PLAYING
                // snapshot inside the player, a pending entrance or an unrelated Toast
                // is not visible music and must not create a new paused island on Home.
                val visibleMedia = attached && !exiting && prepared.packageName != foregroundPackage &&
                    (lastContent as? IslandMediaContent)?.token == prepared.token
                if (paused && !fromShade && previous != PlaybackState.STATE_PAUSED && !visibleMedia) {
                    mediaRevealAt = null; pauseDeadline = null; media = prepared
                    state.setMedia(prepared.token, null); render(); return@launch
                }
                if (!paused) {
                    pauseDeadline = null
                    if (!attached && mediaRevealAt == null &&
                        (media == null || previous == PlaybackState.STATE_PAUSED))
                        mediaRevealAt = now + IslandMotion.MEDIA_DELAY
                } else if (previous != PlaybackState.STATE_PAUSED && !fromShade) {
                    mediaRevealAt = null
                    val exitLead = if (!options.simplified && ValueAnimator.areAnimatorsEnabled()) IslandMotion.EXIT else 0L
                    pauseDeadline = now + IslandMotion.PAUSE_LIFETIME - exitLead
                }
                media = prepared
                state.setMedia(prepared.token, if (paused && pauseDeadline?.let { now < it } != true) null else prepared)
                if (fromShade) clearShadePlayback()
            }
            render()
        }
    }

    private fun watchSession(controller: MediaController) {
        val epoch = sessionEpoch
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) = ModuleHelper.guarded {
                onMain {
                    if (epoch == sessionEpoch && selected?.sessionToken == controller.sessionToken &&
                        (this@IslandActivityController.state.allowed || shadeBlocked)) {
                        // Observe the edge before coalescing the IO read: a quick pause/play
                        // pair can otherwise collapse into the same final PLAYING snapshot.
                        val observingShade = shadeBlocked || shadeBaseline
                        val previous = if (observingShade) shadePlaybackState ?: observedPlaybackState
                            ?: media?.playbackState else observedPlaybackState ?: media?.playbackState
                        if (state != null && IslandMediaPolicy.pauseResume(previous, state.state)) {
                            this@IslandActivityController.state.allowMediaAgain()
                            if (observingShade) shadePlaybackReactivated = true
                            if (state.state != PlaybackState.STATE_PAUSED && (!attached || exiting))
                                mediaRevealAt = SystemClock.uptimeMillis() + IslandMotion.MEDIA_DELAY
                        }
                        if (state != null) observedPlaybackState = state.state
                        if (observingShade && state != null) {
                            if (state.state != previous) shadePlaybackAt = SystemClock.uptimeMillis()
                            shadePlaybackState = state.state
                        }
                        if (!shadeBlocked) nativeManager.get()?.let { updateFromNative(it) }
                    }
                }
            }
            override fun onSessionDestroyed() = ModuleHelper.guarded {
                onMain {
                    if (epoch != sessionEpoch || selected?.sessionToken != controller.sessionToken) return@onMain
                    destroyedToken = controller.sessionToken
                    state.sessionDestroyed(controller.sessionToken)
                    clearMedia()
                    render()
                }
            }
        }
        // Watch only the native-selected session. Some apps publish transport state without
        // a native metadata change; callback reads coalesce through the existing snapshot job.
        sessionJob = scope.launch(ioDispatcher) {
            sessionMutex.withLock {
                try {
                    ensureActive()
                    controller.registerCallback(callback, handler)
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable + ioDispatcher) {
                        ModuleHelper.guarded { controller.unregisterCallback(callback) }
                    }
                }
            }
        }
    }

    private fun prepare(controller: MediaController, metadata: MediaMetadata?, options: IslandOptions,
        epoch: Long): IslandMediaContent? = try {
        val playback = controller.playbackState
        val pkg = controller.packageName
        if (playback == null || !IslandMediaPolicy.active(playback.state) || pkg in options.excludedApps) null
        else {
            // Capacity one, owned by this selected session. Configuration, selection, privacy and
            // disposal invalidate it; cancellation cannot publish an icon after invalidation.
            val cached = appIcon?.takeIf { it.token == controller.sessionToken && it.epoch == epoch }
            val icon = cached ?: run {
                val app = context.packageManager.getApplicationInfo(pkg, 0)
                AppIcon(controller.sessionToken, epoch,
                    context.packageManager.getApplicationLabel(app).toString().take(160),
                    context.packageManager.getApplicationIcon(app))
                    .also { candidate ->
                        synchronized(iconCacheLock) {
                            if (iconEpoch == epoch) appIcon = candidate
                        }
                    }
            }
            val title = metadata?.getText(MediaMetadata.METADATA_KEY_TITLE)?.toString()?.take(160)
                ?.takeUnless { it.isBlank() } ?: icon.label
            IslandMediaContent(controller.sessionToken, pkg, title,
                metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()?.take(160),
                prepareArtwork(metadata, epoch) ?: icon.drawable,
                IslandMediaPolicy.playing(playback.state), playback.actions, controller.sessionActivity, playback.state)
        }
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        if (error is CancellationException) throw error
        XposedHelpers.log("Dynamic Island media snapshot", error)
        null
    }

    private fun invalidateIcon() {
        synchronized(iconCacheLock) {
            iconEpoch++
            appIcon = null
            artwork = null
        }
    }

    private fun prepareArtwork(metadata: MediaMetadata?, epoch: Long): Drawable? {
        val source = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) ?: return null
        if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
        artwork?.takeIf { it.epoch == epoch && it.source.get() === source }?.let { return it.drawable }
        val scale = minOf(1f, 128f / source.width, 128f / source.height)
        val bitmap = if (scale == 1f) source else Bitmap.createScaledBitmap(source,
            (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
        val prepared = Artwork(WeakReference(source), epoch, BitmapDrawable(context.resources, bitmap))
        synchronized(iconCacheLock) { if (iconEpoch == epoch) artwork = prepared }
        return prepared.drawable
    }

    private fun clearMedia() {
        sessionEpoch++
        invalidateIcon()
        actionEpoch++
        readGeneration++
        readJob?.cancel(); readJob = null
        sessionJob?.cancel(); sessionJob = null
        actionJob?.cancel(); actionJob = null
        selected = null
        media = null
        pauseDeadline = null
        observedPlaybackState = null
        mediaRevealAt = null
        shadeBaseline = false
        clearShadePlayback()
        state.setMedia(null, null)
    }

    private fun clearShadePlayback() {
        shadePlaybackState = null; shadePlaybackAt = 0; shadePlaybackReactivated = false
    }

    private data class Geometry(val width: Int, val height: Int, val top: Int, val gap: Int, val maximumWidth: Int)
    private fun refreshGeometry() {
        val manager = window ?: return
        val generation = ++geometryGeneration
        geometryJob?.cancel()
        geometryJob = scope.launch {
            val measured = withContext(ioDispatcher) {
                geometryMutex.withLock {
                    ensureActive()
                    val metrics = manager.currentWindowMetrics
                    val hole = metrics.windowInsets.displayCutout?.boundingRectTop
                    Screen(metrics.bounds.width(), metrics.bounds.height(), hole?.width() ?: 0,
                        if (hole != null && !hole.isEmpty) hole.bottom - minOf(hole.width(), hole.height()) else -1,
                        if (hole != null && !hole.isEmpty) hole.bottom - minOf(hole.width(), hole.height()) * .5f else -1f)
                }
            }
            if (!disposed && generation == geometryGeneration) { screen = measured; render() }
        }
    }

    private fun geometry(label: String? = null): Geometry? {
        val bounds = screen ?: return null
        // Landscape cutouts are at a side; keep that layout entirely native.
        if (bounds.width >= bounds.height) return null
        val now = SystemClock.uptimeMillis()
        val feedback = state.toast(now)?.takeIf { label == null }
        val caption = label ?: feedback?.text
        val gap = maxOf(dp(40), bounds.holeWidth + dp(16))
        val clearance = DynamicIslandStatusBarFade.compactClearance(bounds.width, dp(IslandSizing.SHORT_DP))
        val maximumWidth = IslandSizing.maximumWidth(context.resources.displayMetrics.density, bounds.width,
            clearance, shortDp = options.shortWidth, longDp = options.longWidth, manual = !options.automaticWidth)
        val width = IslandSizing.collapsed(context.resources.displayMetrics.density, bounds.width,
            clearance, gap, dp(24).toFloat(), caption?.let {
                if (feedback?.status == true) shell.statusWidth(it) else shell.textWidth(it)
            } ?: 0f, shortDp = options.shortWidth, longDp = options.longWidth, manual = !options.automaticWidth)
        if (width - gap < dp(100)) return null
        val offset = dp(SystemUIStrongToastHooks.currentSnapshot()?.islandOffsetDp ?: 0)
        val anchor = if (bounds.holeTop >= 0) bounds.holeTop else dp(8)
        val height = if (feedback?.status == true)
            shell.statusHeight(feedback.text, width, gap, dp(options.height)) ?: return null else dp(options.height)
        return Geometry(width, height, (anchor + offset).coerceAtLeast(0), gap, maximumWidth)
    }

    private fun render(): Boolean {
        handler.removeCallbacks(expiry)
        val now = SystemClock.uptimeMillis()
        if (chatLease?.let { !state.owns(it, now) } == true) clearChat()
        if (systemPresentation?.let { state.owns(it.lease, now) } != true) systemPresentation = null
        scheduleSystemFeedback()
        if (mediaRevealAt?.let { now >= it } == true) mediaRevealAt = null
        if (pauseDeadline?.let { now >= it } == true) state.setMedia(null, null)
        if (shadeBlocked && !state.allowed) {
            val deadline = listOfNotNull(pauseDeadline?.takeIf { it > now }, mediaRevealAt?.takeIf { it > now }).minOrNull()
            if (!disposed && deadline != null) handler.postAtTime(expiry, deadline)
            hide(animate = true)
            return false
        }
        // A native activity may have hidden this slot when the source became foreground.
        // Recheck at selection time so ending native preemption cannot resurrect that notice.
        if (state.toast(now)?.let { it.notificationKey != null && it.packageName == foregroundPackage } == true) {
            clearChat(); state.clearToast()
        }
        val toast = state.toast(now)
        // Returning from a shade needs one current snapshot before the retained slot can
        // draw: it may still contain PLAYING although a real pause expired while hidden.
        val current = if (mediaRevealAt == null && !shadeBaseline) state.media(now)?.takeUnless {
            it.packageName == foregroundPackage } else null
        val geometry = if (!disposed && options.enabled && (toast != null || current != null)) geometry() else null
        val nextExpiry = listOfNotNull(state.deadline(), pauseDeadline?.takeIf { it > now },
            mediaRevealAt?.takeIf { it > now }).minOrNull()
        if (!disposed && nextExpiry != null) handler.postAtTime(expiry, nextExpiry)
        if (geometry == null) {
            hide(animate = !disposed && state.allowed && toast == null && current == null)
            return false
        }
        val resumingExit = exiting
        if (resumingExit) exitGeneration++
        exiting = false
        shell.simplified = options.simplified
        shell.entranceStyle = options.entranceStyle
        shell.windowTop = geometry.top
        val showingMedia = toast == null && current != null
        val secondary = attached && lastContent != null &&
            ((lastContent is IslandToastContent) != (toast != null) || toast != null && lastContent !== toast)
        shell.bind(toast?.text ?: current!!.title, if (showingMedia) current?.artist else toast?.detail,
            toast?.icon ?: current?.icon,
            showingMedia && current?.playing == true,
            showingMedia, options.waveform, if (showingMedia) current?.actions ?: 0L else 0L, showingMedia && options.mediaSwipe,
            current?.playbackState ?: PlaybackState.STATE_NONE, toast?.avatar == true, toast?.status == true)
        shell.geometry(geometry.width, geometry.height, geometry.gap, geometry.maximumWidth)
        screen?.let { bounds ->
            shell.cameraAnchor(if (bounds.holeCenter >= 0f) bounds.holeCenter - geometry.top else dp(20).toFloat(),
                if (bounds.holeWidth > 0) bounds.holeWidth.toFloat() else dp(16).toFloat())
        }
        if (resumingExit) shell.resumeEntry()
        // Maximum 280 -> 160 dp width travel plus bounded drag. Symmetric Surface
        // outsets preserve the A14 input frame; the logical touch window stays unchanged.
        val outset = dp(80)
        val outsetsChanged = windowOutsets.left != outset
        windowOutsets.set(outset, outset, outset, outset)
        params.width = geometry.width
        params.height = geometry.height
        params.y = geometry.top
        return try {
            val entering = !attached
            if (entering) {
                // Prepare alpha/scale BEFORE the first Surface can be drawn, including re-entry
                // after a native activity or a notification-panel transition.
                shell.prepareEntry()
                window!!.addView(shell, params); attached = true
            }
            else if (outsetsChanged || lastWidth != params.width || lastHeight != params.height || lastTop != params.y) window!!.updateViewLayout(shell, params)
            if (entering || resumingExit || secondary || lastWidth != params.width || lastHeight != params.height) {
                shell.transition(lastWidth, lastHeight, params.width, params.height, entering, secondary)
            }
            lastWidth = params.width; lastHeight = params.height
            lastTop = params.y
            lastContent = toast ?: current
            state.identity(now)?.let { presentation.prepare(it, geometry.width) }
            true
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dynamic Island window: retaining native feedback", error)
            hide()
            false
        }
    }

    private fun hide(animate: Boolean = false) {
        if (animate && attached) {
            if (!exiting) {
                exiting = true
                presentation.exiting()
                val generation = ++exitGeneration
                lastContent = null
                shell.exit {
                    if (exiting && exitGeneration == generation) {
                        hide()
                        scheduleSystemFeedback()
                    }
                }
            }
            return
        }
        exitGeneration++
        exiting = false
        try {
            if (attached) window?.removeViewImmediate(shell)
        } finally {
            attached = false
            lastContent = null
            presentation.cancel()
        }
    }

    private fun openPlayer() {
        val now = SystemClock.uptimeMillis()
        if (state.toast(now)?.notificationKey != null) {
            // The ROM's row click owns unlock, opening, auto-cancel and notification stats.
            // Never send a second PendingIntent or manually cancel the original notification.
            ModuleHelper.guarded { chatOpen?.invoke() }
            clearChat(); render()
            return
        }
        val launch = (state.toast(now)?.launch ?: state.media(now)?.launch) ?: return
        val epoch = actionEpoch
        actionJob?.cancel()
        actionJob = scope.launch(ioDispatcher) {
            actionMutex.withLock {
                    ensureActive()
                    if (epoch == actionEpoch) ModuleHelper.guarded { launch.send() }
                }
            }
        }

        private fun control(requested: Long) {
            if (!options.mediaSwipe || !state.allowed || state.media(SystemClock.uptimeMillis()) == null) return
            val controller = selected ?: return
            val epoch = actionEpoch
            actionJob?.cancel()
            actionJob = scope.launch(ioDispatcher) {
                actionMutex.withLock {
                ModuleHelper.guarded {
                    val playback = controller.playbackState ?: return@guarded
                    ensureActive()
                    if (epoch != actionEpoch) return@guarded
                    if (!IslandMediaPolicy.canSkip(requested, playback.actions)) return@guarded
                    val transport = controller.transportControls
                    when (requested) {
                        PlaybackState.ACTION_SKIP_TO_NEXT -> transport.skipToNext()
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS -> transport.skipToPrevious()
                    }
                }
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        handler.removeCallbacks(expiry)
        handler.removeCallbacks(nativeRefresh)
        clearSystemActivity()
        systemActivities?.dispose(); systemActivities = null
        geometryGeneration++
        geometryJob?.cancel()
        geometryJob = null
        screen = null
        ModuleHelper.unregisterPreferenceObserver(this)
        ScreenStateController.removeListener(this)
        if (userReceiverRegistered) {
            userReceiverRegistered = false
            ModuleHelper.guarded { context.unregisterReceiver(userReceiver) }
        }
        if (configurationRegistered) {
            configurationRegistered = false
            ModuleHelper.guarded { context.unregisterComponentCallbacks(configuration) }
        }
        disconnectSafety()
        clearMedia()
        clearChat()
        state.clear(preserveDismissal = true)
        hide()
        shell.onDismiss = {}; shell.onOpen = {}; shell.onControl = {}
        scope.cancel()
    }

    private fun disconnectSafety() {
        val owner = safetyOwner
        val listener = safetyListener
        safetyOwner = null; safetyListener = null
        if (owner != null && listener != null) ModuleHelper.guarded { access.unlisten(owner, listener) }
    }

    private fun onMain(block: () -> Unit) {
        if (disposed) return
        if (Looper.myLooper() == Looper.getMainLooper()) ModuleHelper.guarded { block() }
        else handler.post { ModuleHelper.guarded { if (!disposed) block() } }
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val USER_SWITCHED = "android.intent.action.USER_SWITCHED"
    }
}
