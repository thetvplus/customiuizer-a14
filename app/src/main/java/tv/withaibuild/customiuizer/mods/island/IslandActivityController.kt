package tv.withaibuild.customiuizer.mods.island

import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks

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
import android.media.AudioManager
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
import android.view.Surface
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference

internal data class IslandMediaContent(
    val token: MediaSession.Token, val packageName: String, val title: String, val artist: String?,
    val icon: Drawable?, val playing: Boolean, val actions: Long,
    val playbackState: Int,
    val user: UserHandle? = null,
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
    markerLoader: ClassLoader? = null,
    private val spectrumBackend: IslandAudioSpectrum.Backend? = null,
    private val freeform: IslandFreeformPort? = null,
    private val pipStateReader: (() -> IslandPipSnapshot?)? = null,
    geometryHost: View? = null,
    trackApplicationOverlays: Boolean = false,
    pipCandidate: IslandPipCandidatePort? = null,
) : ScreenStateController.ScreenStateListener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + ModuleHelper.coroutineFailureHandler)
    private val state = IslandActivityState<IslandToastContent, IslandMediaContent>(dismissal)
    private val shell = IslandActivityView(context)
    private val touchWindow = IslandTouchWindow(shell)
    private var displayedMedia: MediaSession.Token? = null
    private val presentation = IslandWindowPresentation(touchWindow, target = { shell },
        eligible = { !disposed && attached && state.allowed &&
            (lastContent != null || exiting) },
        shown = { event ->
            (lastContent as? IslandMediaContent)?.takeIf { it.token == event && it.playing && !exiting }
                ?.let { displayedMedia = it.token; updateSpectrum(visible = true) }
        }, hidden = { spectrum?.stop() }, frameAllowed = ::frameAllowed,
        rejected = ::queueFrameRecheck)
    private val window = context.getSystemService(WindowManager::class.java)
    private var attached = false
    private var exiting = false
    private var exitGeneration = 0L
    private var disposed = false
    private var applicationOverlays: IslandApplicationOverlaySignal.Subscriber? = null
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
    private var chatNotification: StatusBarNotification? = null
    private var chatOpen: (() -> Boolean)? = null
    private var foregroundUser: UserHandle? = ModuleHelper.guarded(null) {
        val id = XposedHelpers.callStaticMethod(ActivityManager::class.java, "getCurrentUser") as Int
        // UserHandle.getIdentifier/constructors are hidden in the SDK; cache the public UID
        // conversion once per foreground-user change (A14 PER_USER_RANGE = 100000).
        if (id < 0) null else UserHandle.getUserHandleForUid(Math.multiplyExact(id, 100000))
    }
    private val contextUser: UserHandle? = ModuleHelper.guarded(null) {
        val id = XposedHelpers.callMethod(context, "getUserId") as Int
        if (id < 0) null else UserHandle.getUserHandleForUid(Math.multiplyExact(id, 100000))
    }
    private val diagnostics = IslandDiagnostics()
    private var spectrum: IslandAudioSpectrum? = null
    private val snapshotMutex = Mutex()
    private val sessionMutex = Mutex()
    private val actionMutex = Mutex()
    private val geometryMutex = Mutex()
    private var geometryJob: Job? = null
    private var geometryGeneration = 0L
    private data class Screen(val width: Int, val height: Int, val holeWidth: Int, val holeTop: Int,
        val holeCenter: Float, val safeLeft: Int, val safeRight: Int, val holeLeft: Int, val holeRight: Int,
        val holeBottom: Int, val holePhysicalTop: Int)
    private var screen: Screen? = null
    @Volatile private var actionEpoch = 0L
    private var readGeneration = 0L
    private var media: IslandMediaContent? = null
    private var foregroundPackage: String? = null
    private var previousForegroundPackage: String? = null
    private var foregroundChangedAt = 0L
    private var foregroundBaseline = false
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
    private var pipSnapshot = IslandPipSnapshot(0, 0)
    private val pipHomeGate = pipCandidate?.let { IslandPipHomeGate(scope, ioDispatcher, it, ::pipHomeGateChanged) }
    // Installation-time option: changing the switch requires the same SystemUI restart as
    // installing its native event hooks. Other preference refreshes cannot change this snapshot.
    private val hideInFloatingWindow = options.hideInFloatingWindow
    private var freeformKnown = !hideInFloatingWindow || freeform == null
    private var freeformPresent = false
    private val freeformMutex = Mutex()
    private var freeformJob: Job? = null
    private var freeformGeneration = 0L
    private data class MediaReturn(val token: MediaSession.Token, val expiresAt: Long)
    private var mediaReturn: MediaReturn? = null
    private var mediaReturnInvalidatedAt = -1L
    private var musicReturnPublisher: IslandMusicReturnSignal.Publisher? = null
    private var shadeBlocked = false
    private var shadeBaseline = false
    private var shadePlaybackState: Int? = null
    private var shadePlaybackAt = 0L
    private var shadePlaybackReactivated = false
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastTop = 0
    private var lastLeft = 0
    private var lastContent: Any? = null
    private var systemActivities: IslandSystemActivities? = null
    private val actionMarkers = markerLoader?.let { IslandSystemActionMarkers.native(it, WeakReference(this)) }
    private val systemFeedbackSlot = IslandSystemFeedback()
    private data class SystemPresentation(val lease: IslandActivityState.Lease, val activity: IslandSystemActivity)
    private var systemPresentation: SystemPresentation? = null
    private val systemFeedback = Runnable { ModuleHelper.guarded { presentSystemFeedback() } }
    private var userReceiverRegistered = false
    private var configurationRegistered = false
    private val geometryView = WeakReference(geometryHost)
    private val hasGeometryHost = geometryHost != null
    private var portraitDisplay = !hasGeometryHost || ModuleHelper.guarded(false) {
        geometryHost?.display?.rotation.let { it == Surface.ROTATION_0 || it == Surface.ROTATION_180 }
    }
    private val geometryLayout = View.OnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
        // StatusBar's actual window can rotate without a resource Configuration callback.
        // Only width changes do work; ordinary layout/animation frames do not query native state.
        if (right - left != oldRight - oldLeft) onMain { refreshWindowGeometry() }
    }
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
                options = IslandOptions.read(prefs).copy(hideInFloatingWindow = hideInFloatingWindow)
                if (previousOptions.mediaReturn != options.mediaReturn ||
                    previousOptions.mediaReturnApps != options.mediaReturnApps) {
                    cancelMediaReturn()
                }
                if (previousOptions.musicActions != options.musicActions || previousOptions.mediaSwipe != options.mediaSwipe ||
                    previousOptions.media != options.media || previousOptions.enabled != options.enabled ||
                    previousOptions.excludedApps != options.excludedApps || key == null ||
                    key?.startsWith("system_island_music_") == true) invalidateActions()
                if (previousOptions.position != options.position || previousOptions.horizontalOffset != options.horizontalOffset ||
                    previousOptions.height != options.height || previousOptions.shortWidth != options.shortWidth ||
                    previousOptions.longWidth != options.longWidth || previousOptions.automaticWidth != options.automaticWidth)
                    presentation.cancel()
                if (!options.enabled) dispose()
                else {
                    if (!options.toast && state.toast(SystemClock.uptimeMillis())?.let {
                            !it.status && it.notificationKey == null } == true) state.clearToast()
                    if (!options.media) { clearMedia(); state.allowMediaAgain() }
                    if (!options.chat || state.toast(SystemClock.uptimeMillis())?.packageName.let {
                            it in options.chatExcludedApps || it !in options.notificationApps }) clearChat()
                    if (previousOptions.feedback != options.feedback) {
                        clearSystemActivity()
                        systemActivities?.dispose()
                        systemActivities = createSystemActivities()
                        actionMarkers?.update(options.feedback)
                    }
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
                refreshWindowGeometry()
            }
        }
        override fun onLowMemory() = Unit
    }
    private val userReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = ModuleHelper.guarded {
            // System-protected user-switch broadcasts only; discard the old user's feedback and session.
            if (intent.action == USER_SWITCHED) {
                pipHomeGate?.cancel()
                val userId = intent.getIntExtra("android.intent.extra.user_handle", -1)
                foregroundUser = if (userId < 0) null else UserHandle.getUserHandleForUid(Math.multiplyExact(userId, 100000))
                freeformKnown = !hideInFloatingWindow || freeform == null
                freeformPresent = false
                refreshFloatingWindows()
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
        touchWindow.onUnavailable = { handler.post { ModuleHelper.guarded { if (!disposed) render() } } }
        shell.onDismiss = { state.dismiss(); render() }
        shell.onOpen = { openPlayer() }
        shell.onGesture = { executeGesture(it) }
        state.allowed = false
        try {
            ModuleHelper.observePreferenceChange(preferenceObserver, this)
            ScreenStateController.addListener(context, this)
            context.registerReceiver(userReceiver, IntentFilter(USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
            userReceiverRegistered = true
            context.registerComponentCallbacks(configuration)
            configurationRegistered = true
            geometryView.get()?.addOnLayoutChangeListener(geometryLayout)
            refreshGeometry()
            if (options.enabled) systemActivities = createSystemActivities()
            actionMarkers?.update(options.feedback)
            if (hideInFloatingWindow && trackApplicationOverlays) ModuleHelper.guarded {
                applicationOverlays = IslandApplicationOverlaySignal.Subscriber(context, handler,
                    send = { message -> scope.launch(ioDispatcher) {
                        ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(context, message) }
                    } }, changed = { onMain {
                        val wasPresented = lastContent != null
                        mediaReturnInvalidatedAt = SystemClock.uptimeMillis()
                        if (floatingWindowBlocked()) { cancelMediaReturn() }
                        else if (!wasPresented && media?.packageName != foregroundPackage) {
                            mediaRevealAt = SystemClock.uptimeMillis() + IslandMotion.MEDIA_DELAY
                            nativeManager.get()?.let { updateFromNative(it) }
                        }
                        render()
                    } })
            }
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
        val allowed = presentationAllowed()
        val shade = !disposed && options.enabled && ScreenStateController.isScreenOn() &&
            safetyOwner?.let { ModuleHelper.guarded(false) { access.isShadeBlocked(it) } } == true
        if (state.allowed == allowed && shadeBlocked == shade) return
        val wasShade = shadeBlocked
        if (!allowed && shade) pauseSystemFeedback()
        shadeBlocked = shade
        state.allowed = allowed
        presentation.refresh()
        if (!state.allowed) {
            pipHomeGate?.cancel()
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

    private fun presentationAllowed(): Boolean = !disposed && options.enabled && ScreenStateController.isScreenOn() &&
        portraitDisplay &&
        safetyOwner?.let { ModuleHelper.guarded(false) { access.isAllowed(it) } } == true

    private fun refreshWindowGeometry() {
        if (hasGeometryHost) portraitDisplay = ModuleHelper.guarded(false) {
            geometryView.get()?.display?.rotation.let { it == Surface.ROTATION_0 || it == Surface.ROTATION_180 }
        }
        readGeneration++
        invalidateIcon()
        media = null
        state.setMedia(null, null)
        hide()
        shell.refreshConfiguration()
        touchWindow.refreshConfiguration()
        screen = null
        refreshSafety()
        refreshGeometry()
        nativeManager.get()?.let { updateFromNative(it) }
        render()
    }

    override fun onScreenStateChanged(isOn: Boolean) = onMain { refreshSafety() }

    internal fun pipChanged(snapshot: IslandPipSnapshot) = onMain {
        if (applyPipSnapshot(snapshot)) render()
    }

    private fun applyPipSnapshot(snapshot: IslandPipSnapshot): Boolean {
        if (snapshot.generation <= pipSnapshot.generation) return false
        val wasHidden = floatingWindowBlocked()
        val wasBlocked = media?.let(pipSnapshot::blocks) == true
        pipSnapshot = snapshot
        pipHomeGate?.nativeChanged(snapshot, renderNow = false)
        val blocked = media?.let(snapshot::blocks) == true
        if (blocked || wasBlocked || wasHidden || floatingWindowBlocked())
            mediaReturnInvalidatedAt = SystemClock.uptimeMillis()
        if (blocked) {
            cancelMediaReturn()
        }
        if (floatingWindowBlocked()) {
            mediaRevealAt = null
            // A prepared/visible media window must not finish a now-invalid entrance.
            if (lastContent is IslandMediaContent) hide(animate = state.allowed)
        } else if (wasHidden && media?.packageName != foregroundPackage) {
            mediaRevealAt = SystemClock.uptimeMillis() + IslandMotion.MEDIA_DELAY
            nativeManager.get()?.let { updateFromNative(it) }
        }
        return true
    }

    private fun floatingWindowBlocked(pip: IslandPipSnapshot = pipSnapshot): Boolean = hideInFloatingWindow &&
        (!freeformKnown || freeformPresent || (pip.pendingEntry || pip.state in 1..5) && pip.displayId == 0 ||
            foregroundUser?.let { applicationOverlays?.hasWindow(it) } == true)

    /** Read only the already-published Shell snapshot; no native query or frame work queue. */
    private fun frameAllowed(): Boolean {
        if (disposed || !options.enabled) return false
        // Safety/panel removal is owned by refreshSafety/render. This additional
        // fence must not stall their existing exit animation on an old Surface buffer.
        if (exiting || !state.allowed) return true
        val pip = pipStateReader?.invoke()?.takeIf { it.generation >= pipSnapshot.generation } ?: pipSnapshot
        return !floatingWindowBlocked(pip) && !(pipHomeGate?.source != null &&
            (hideInFloatingWindow || pipHomeGate.matches(lastContent as? IslandMediaContent)))
    }

    private var frameRecheckQueued = false
    private val frameRecheck = Runnable {
        frameRecheckQueued = false
        ModuleHelper.guarded { if (!disposed) render() }
    }
    private fun queueFrameRecheck() {
        if (!disposed && !frameRecheckQueued) {
            frameRecheckQueued = true
            handler.post(frameRecheck)
        }
    }

    private fun pipHomeGateChanged(update: IslandPipHomeGate.Update) {
        if (disposed) return
        val sameMedia = media?.let { it.packageName == update.source.packageName && it.user == update.source.user } == true
        if (update.confirmed && (hideInFloatingWindow || sameMedia)) {
            mediaReturnInvalidatedAt = SystemClock.uptimeMillis()
            cancelMediaReturn()
        }
        if (update.refreshMedia && options.media && state.allowed && foregroundPackage == IslandMusicReturnSignal.LAUNCHER &&
            (media == null || sameMedia)) nativeManager.get()?.let {
            // PiP can briefly replace native media selection; read once after it settles.
            foregroundBaseline = true
            updateFromNative(it)
        }
        if (update.renderNow) render()
    }

    internal fun refreshFloatingWindows() = onMain {
        val reader = freeform ?: return@onMain
        if (!hideInFloatingWindow) return@onMain
        val user = foregroundUser ?: run { freeformKnown = true; render(); return@onMain }
        val generation = ++freeformGeneration
        freeformJob?.cancel()
        freeformJob = scope.launch {
            freeformMutex.withLock {
                val present = withContext(ioDispatcher) {
                    try { reader.hasWindow(user) }
                    catch (error: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(error)
                        if (error is CancellationException) throw error
                        diagnostics.report("floating-window-state-unavailable")
                        null
                    }
                }
                if (disposed || generation != freeformGeneration || user != foregroundUser) return@withLock
                val wasHidden = floatingWindowBlocked()
                freeformKnown = true
                if (present != null) freeformPresent = present
                if (wasHidden || floatingWindowBlocked()) mediaReturnInvalidatedAt = SystemClock.uptimeMillis()
                if (wasHidden && !floatingWindowBlocked() && media?.packageName != foregroundPackage) {
                    mediaRevealAt = SystemClock.uptimeMillis() + IslandMotion.MEDIA_DELAY
                    nativeManager.get()?.let { updateFromNative(it) }
                }
                render()
            }
        }
    }

    fun foregroundChanged(packageName: String?) = onMain {
        if (foregroundPackage == packageName) return@onMain
        val previous = foregroundPackage
        pipHomeGate?.cancel()
        previousForegroundPackage = previous
        foregroundPackage = packageName
        foregroundChangedAt = SystemClock.uptimeMillis()
        val committed = mediaReturn?.takeIf { pending ->
            packageName != null && previous == media?.packageName && packageName != previous &&
                pending.token == media?.token && SystemClock.uptimeMillis() < pending.expiresAt
        }
        if (committed == null) cancelMediaReturn() else shell.cancelMediaReturn()
        invalidateActions()
        // A snapshot can already be on its way back to Main before the first media
        // value exists. Direct App/Home launches must invalidate that result too.
        val pendingRead = readJob?.isActive == true
        val sourceChanged = media?.let { packageName == it.packageName || previous == it.packageName } == true
        if (!disposed && options.enabled && state.allowed && packageName == IslandMusicReturnSignal.LAUNCHER &&
            previous != null && previous != IslandMusicReturnSignal.LAUNCHER &&
            (hideInFloatingWindow || options.media && (media == null || media?.packageName == previous)))
            foregroundUser?.let { pipHomeGate?.begin(previous, it) }
        media?.let {
            if (packageName == it.packageName) {
                displayedMedia = null
                mediaRevealAt = null
                if (it.playbackState == PlaybackState.STATE_PAUSED) {
                    pauseDeadline = null
                    state.setMedia(it.token, null)
                }
            }
            else if (previous == it.packageName && it.playbackState != PlaybackState.STATE_PAUSED)
            {
                val now = SystemClock.uptimeMillis()
                val returning = committed != null && state.allowed && options.mediaReturn &&
                    it.packageName in options.mediaReturnApps && it.playing &&
                    !options.simplified && ValueAnimator.areAnimatorsEnabled()
                if (returning) mediaReturn = MediaReturn(it.token, now + IslandMotion.MEDIA_DELAY)
                mediaRevealAt = now + if (returning) IslandMotion.MEDIA_RETURN_DELAY else IslandMotion.MEDIA_DELAY
            }
        }
        if (pendingRead || sourceChanged) {
            val manager = nativeManager.get()
            if (manager == null) clearMedia()
            else if (options.media && state.allowed) {
                foregroundBaseline = true
                updateFromNative(manager)
            }
        }
        render()
        // An unchanged playing session otherwise leaves Launcher's five-second packet
        // expired until a gesture-down round trip completes. Renew on this real edge,
        // before a rapid opening/closing gesture; no timer or stale lifetime extension.
        if (sourceChanged && media?.packageName in options.mediaReturnApps)
            musicReturnPublisher?.publish(force = true)
    }

    internal fun nativeMusicReturn(frame: IslandMusicReturnSignal.Frame, committedAt: Long = SystemClock.uptimeMillis()) = onMain {
        pipStateReader?.invoke()?.let(::applyPipSnapshot)
        val current = media ?: return@onMain
        if (pipSnapshot.blocks(current) || floatingWindowBlocked() ||
            pipHomeGate?.let { it.confirmed && it.matches(current) } == true || committedAt <= mediaReturnInvalidatedAt) return@onMain
        if (disposed || !state.allowed || !options.mediaReturn || options.simplified || !frame.valid() ||
            !ValueAnimator.areAnimatorsEnabled() || !current.playing || frame.packageName != current.packageName ||
            frame.user != foregroundUser || frame.user != current.user || current.packageName !in options.mediaReturnApps ||
            foregroundPackage != current.packageName &&
                (foregroundPackage != IslandMusicReturnSignal.LAUNCHER || previousForegroundPackage != current.packageName) ||
            foregroundPackage == current.packageName && foregroundChangedAt > committedAt) return@onMain
        val now = SystemClock.uptimeMillis()
        mediaReturn = MediaReturn(current.token, now + IslandMotion.MEDIA_DELAY)
        mediaRevealAt = now + IslandMotion.MEDIA_RETURN_DELAY
        foregroundBaseline = true
        nativeManager.get()?.let { updateFromNative(it) }
        render()
    }

    fun systemActivity(activity: IslandSystemActivity) = onMain {
        if (disposed || !options.enabled || floatingWindowBlocked() || activity.kind !in options.feedback || (!state.allowed && !shadeBlocked)) return@onMain
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
        if (disposed || !options.enabled || !state.allowed || floatingWindowBlocked()) return
        val now = SystemClock.uptimeMillis()
        val ready = systemFeedbackSlot.readyAt(now) ?: return
        handler.postAtTime(systemFeedback, ready)
    }

    private fun presentSystemFeedback() {
        if (disposed || !options.enabled || !state.allowed || floatingWindowBlocked()) return
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
            !state.allowed || floatingWindowBlocked() || content.packageName in options.excludedApps ||
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
        if (chatKey == sbn.key && chatNotification !== sbn) { clearChat(); render() }
    }

    /** Synchronous transaction on the final native heads-up bind; false always retains native UI. */
    fun takeNotification(bound: IslandNotificationAccess.Bound): Boolean {
        val sbn = bound.notification
        if (Looper.myLooper() != Looper.getMainLooper() || disposed || !options.enabled || !options.chat || floatingWindowBlocked()) return false
        // The native keyguard can lead the status-bar callback. Read it at the
        // takeover boundary, before any notification lease or native mutation.
        refreshSafety()
        if (!state.allowed ||
            sbn.packageName !in options.notificationApps ||
            sbn.packageName in options.excludedApps ||
            sbn.packageName in options.chatExcludedApps || sbn.packageName == foregroundPackage ||
            sbn.user != foregroundUser || sbn.notification.contentIntent == null) return false
        val now = SystemClock.uptimeMillis()
        if (chatNotification === sbn) {
            val current = chatLease?.let { attached && !exiting && state.owns(it, now) } == true
            if (current) chatOpen = bound.open // A new native Row may keep the same Sbn and lease.
            return current
        }
        // Native system feedback and an already accepted ordinary Toast keep their lease.
        if (state.toast(now)?.let { it.notificationKey == null } == true) return false
        val rank = bound.ranking
        val prepared = IslandChatNotifications.prepare(context, sbn, rank.importance,
            rank.matchesInterruptionFilter(), rank.suppressedVisualEffects,
            nativeIcon = bound.icon) ?: return false
        clearChat()
        chatKey = sbn.key; chatNotification = sbn; chatOpen = bound.open
        chatLease = state.offer(prepared, now, 4000)
        if (!render()) { clearChat(); return false }
        if (!presentationAllowed()) { refreshSafety(); return false }
        ModuleHelper.guarded { shell.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) }
        return true
    }

    fun notificationRemoved(key: String) = onMain {
        if (chatKey == key) { clearChat(); render() }
    }

    fun cancelNotificationTakeover(bound: IslandNotificationAccess.Bound) = onMain {
        // A failed old bind must not clear a newer update with the same key.
        if (chatNotification === bound.notification && chatOpen === bound.open) { clearChat(); render() }
    }

    private fun clearChat() {
        chatLease?.let { state.release(it) }; chatLease = null
        chatKey = null
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
        if (selected !== controller) {
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
            foregroundBaseline = false
            if (prepared == null) {
                clearMedia()
            } else {
                if (sessionJob == null) watchSession(controller)
                observedPlaybackState = prepared.playbackState
                if (mediaRevealAt?.let { now >= it } == true) mediaRevealAt = null
                val paused = prepared.playbackState == PlaybackState.STATE_PAUSED
                if (!prepared.playing || mediaReturn?.token?.let { it != prepared.token } == true) {
                    cancelMediaReturn()
                }
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
                    val remaining = if (displayedMedia == prepared.token && shadePlaybackReactivated && shadePlaybackState == PlaybackState.STATE_PAUSED)
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
                val visibleMedia = presentation.phase == IslandWindowPresentation.Phase.VISIBLE &&
                    displayedMedia == prepared.token && attached && !exiting && prepared.packageName != foregroundPackage &&
                    (lastContent as? IslandMediaContent)?.token == prepared.token
                if (paused && !fromShade && previous != PlaybackState.STATE_PAUSED && !visibleMedia) {
                    mediaRevealAt = null; pauseDeadline = null; media = prepared
                    state.setMedia(prepared.token, null); render(); return@launch
                }
                if (!paused) {
                    if (previous == PlaybackState.STATE_PAUSED && !visibleMedia) displayedMedia = null
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
                            if (state.state != PlaybackState.STATE_PAUSED &&
                                (!this@IslandActivityController.state.allowed || exiting ||
                                    presentation.phase != IslandWindowPresentation.Phase.VISIBLE ||
                                    (lastContent as? IslandMediaContent)?.token != controller.sessionToken)) displayedMedia = null
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
                IslandMediaPolicy.playing(playback.state), playback.actions, playback.state,
                IslandMediaIdentity.uid(controller)?.let { UserHandle.getUserHandleForUid(it) })
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

    private fun cancelMediaReturn() {
        mediaReturn?.let { pending ->
            // Revoking the opt-in before entry restores the ordinary entrance deadline.
            mediaRevealAt?.let { mediaRevealAt = maxOf(it, pending.expiresAt) }
        }
        mediaReturn = null
        shell.cancelMediaReturn()
    }

    private fun clearMedia() {
        spectrum?.stop()
        sessionEpoch++
        invalidateIcon()
        actionEpoch++
        readGeneration++
        readJob?.cancel(); readJob = null
        sessionJob?.cancel(); sessionJob = null
        actionJob?.cancel(); actionJob = null
        selected = null
        media = null
        displayedMedia = null
        foregroundBaseline = false
        pauseDeadline = null
        observedPlaybackState = null
        mediaRevealAt = null
        cancelMediaReturn()
        shadeBaseline = false
        clearShadePlayback()
        state.setMedia(null, null)
    }

    private fun clearShadePlayback() {
        shadePlaybackState = null; shadePlaybackAt = 0; shadePlaybackReactivated = false
    }

    private data class Geometry(val width: Int, val height: Int, val top: Int, val gap: Int, val maximumWidth: Int, val left: Int)
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
                    val safe = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or
                        android.view.WindowInsets.Type.displayCutout())
                    Screen(metrics.bounds.width(), metrics.bounds.height(), hole?.width() ?: 0,
                        if (hole != null && !hole.isEmpty) hole.bottom - minOf(hole.width(), hole.height()) else -1,
                        if (hole != null && !hole.isEmpty) hole.bottom - minOf(hole.width(), hole.height()) * .5f else -1f,
                        safe.left, metrics.bounds.width() - safe.right,
                        hole?.left ?: 0, hole?.right ?: 0, hole?.bottom ?: 0, hole?.top ?: 0)
                }
            }
            if (!disposed && generation == geometryGeneration) { screen = measured; render() }
        }
    }

    private fun geometry(label: String? = null, mediaOnly: Boolean = false): Geometry? {
        if (!touchWindow.inputRegionAvailable) return null
        val bounds = screen ?: return null
        // Landscape cutouts are at a side; keep that layout entirely native.
        if (bounds.width >= bounds.height) return null
        val now = SystemClock.uptimeMillis()
        val feedback = state.toast(now)?.takeIf { label == null && !mediaOnly }
        shell.contentHeight(options.height)
        val caption = label ?: feedback?.text
        val centered = options.position == 1
        val gap = if (centered) maxOf(dp(40), bounds.holeWidth + dp(16)) else 0
        val clearance = if (centered && options.automaticWidth) DynamicIslandStatusBarFade.compactClearance(bounds.width, dp(IslandSizing.SHORT_DP))
            else bounds.safeRight - bounds.safeLeft
        val density = context.resources.displayMetrics.density
        val maximumWidth = IslandSizing.maximumWidth(density, bounds.width, clearance,
            shortDp = options.shortWidth, longDp = options.longWidth, manual = !options.automaticWidth,
            heightDp = options.height, centered = centered)
        val width = IslandSizing.collapsed(density, bounds.width, clearance, gap, dp(24).toFloat(),
            caption?.let { if (feedback?.status == true) shell.statusWidth(it) else shell.textWidth(it) }
                ?: dp(24).toFloat(), shortDp = options.shortWidth, longDp = options.longWidth,
            manual = !options.automaticWidth, heightDp = options.height, centered = centered)
        val offset = dp(SystemUIStrongToastHooks.currentSnapshot()?.islandOffsetDp ?: 0)
        val anchor = if (bounds.holeTop >= 0) bounds.holeTop else dp(8)
        val requiredCaption = caption?.let { text ->
            val measured = if (feedback?.status == true) shell.statusWidth(text) *
                (11f / (13f + (options.height - 40) * .1f)) else shell.textWidth(text)
            (measured + dp(64) + 1).toInt()
        } ?: dp(88)
        val minimumWidth = if (centered) dp(100) + gap else minOf(width, maxOf(dp(88), requiredCaption))
        val placement = IslandPosition.layout(width, dp(options.height), minimumWidth,
            bounds.safeLeft, bounds.safeRight, options.position, dp(options.horizontalOffset),
            (anchor + offset).coerceAtLeast(0), gap, bounds.holeLeft, bounds.holeRight,
            bounds.holePhysicalTop, bounds.holeBottom, dp(8)) ?: return null
        val height = if (feedback?.status == true)
            shell.statusHeight(feedback.text, placement.width, placement.gap, dp(options.height)) ?: return null
            else dp(options.height)
        return Geometry(placement.width, height, placement.top, placement.gap,
            if (centered) maximumWidth else placement.width, placement.left)
    }

    private fun publishMusicReturn() {
        val enabled = !disposed && options.enabled && options.media && options.mediaReturn &&
            options.mediaReturnApps.isNotEmpty() && !options.simplified && !floatingWindowBlocked() && ValueAnimator.areAnimatorsEnabled()
        if (!enabled) {
            val old = musicReturnPublisher
            musicReturnPublisher = null
            old?.let { ModuleHelper.guarded { it.close() } }
            return
        }
        val publisher = musicReturnPublisher ?: IslandMusicReturnSignal.Publisher(context.applicationContext,
            handler, scope, ioDispatcher, onCommit = ::nativeMusicReturn) {
            pipStateReader?.invoke()?.let(::applyPipSnapshot)
            val current = state.media(SystemClock.uptimeMillis())
            val bounds = screen
            if (!state.allowed || current?.playing != true || current.user == null || pipSnapshot.blocks(current) ||
                pipHomeGate?.let { it.confirmed && (hideInFloatingWindow || it.matches(current)) } == true ||
                current.user != foregroundUser || current.packageName !in options.mediaReturnApps ||
                state.toast(SystemClock.uptimeMillis()) != null || bounds == null) null
            else geometry(mediaOnly = true)?.let { geometry ->
                IslandMusicReturnSignal.Frame(current.packageName, current.user,
                    geometry.left.toFloat(), geometry.top.toFloat(), (geometry.left + geometry.width).toFloat(),
                    (geometry.top + geometry.height).toFloat(), bounds.width, bounds.height)
            }
        }.also { musicReturnPublisher = it }
        publisher.publish()
    }

    private fun render(): Boolean {
        // A Shell-thread event may precede its queued Main delivery. Read its already-published
        // scalar snapshot at the commit boundary; this performs no native query or polling.
        pipStateReader?.invoke()?.let(::applyPipSnapshot)
        handler.removeCallbacks(expiry)
        if (floatingWindowBlocked()) {
            cancelMediaReturn()
            publishMusicReturn()
            clearChat(); state.clearToast(); clearSystemActivity()
            mediaRevealAt = null
            hide(animate = state.allowed)
            return false
        }
        if (pipHomeGate?.source != null && (hideInFloatingWindow ||
                state.toast(SystemClock.uptimeMillis()) == null && pipHomeGate.matches(media))) {
            // Hold admission, not the ordinary music-return transaction, during the one query.
            publishMusicReturn()
            hide(animate = state.allowed)
            return false
        }
        if (media?.let(pipSnapshot::blocks) == true) {
            // PiP owns its video window even when a parallel media island is permitted.
            cancelMediaReturn()
        }
        publishMusicReturn()
        val now = SystemClock.uptimeMillis()
        if (mediaReturn?.let { now >= it.expiresAt } == true || !state.allowed ||
            !options.mediaReturn || options.simplified || !ValueAnimator.areAnimatorsEnabled()) {
            cancelMediaReturn()
        }
        if (chatLease?.let { !state.owns(it, now) } == true) clearChat()
        if (systemPresentation?.let { state.owns(it.lease, now) } != true) systemPresentation = null
        scheduleSystemFeedback()
        if (mediaRevealAt?.let { now >= it } == true) mediaRevealAt = null
        if (pauseDeadline?.let { now >= it } == true) state.setMedia(null, null)
        if (shadeBlocked && !state.allowed) {
            val deadline = listOfNotNull(pauseDeadline?.takeIf { it > now }, mediaRevealAt?.takeIf { it > now },
                mediaReturn?.expiresAt?.takeIf { it > now }).minOrNull()
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
        if (toast != null) cancelMediaReturn()
        // Returning from a shade needs one current snapshot before the retained slot can
        // draw: it may still contain PLAYING although a real pause expired while hidden.
        val current = if (mediaRevealAt == null && !shadeBaseline && !foregroundBaseline) state.media(now)?.takeUnless {
            it.packageName == foregroundPackage } else null
        val geometry = if (!disposed && options.enabled && (toast != null || current != null)) geometry() else null
        val nextExpiry = listOfNotNull(state.deadline(), pauseDeadline?.takeIf { it > now },
            mediaRevealAt?.takeIf { it > now }, mediaReturn?.expiresAt?.takeIf { it > now }).minOrNull()
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
        if (showingMedia != (lastContent is IslandMediaContent) ||
            showingMedia && (lastContent as? IslandMediaContent)?.token != current?.token ||
            !showingMedia && lastContent !== toast) invalidateActions()
        val secondary = attached && lastContent != null &&
            ((lastContent is IslandToastContent) != (toast != null) || toast != null && lastContent !== toast)
        val visualLeft = shell.pivotX * (1f - shell.scaleX) + shell.translationX
        val visualTop = shell.pivotY * (1f - shell.scaleY) + shell.translationY
        val contentChanged = shell.bind(toast?.text ?: current!!.title, if (showingMedia) current?.artist else toast?.detail,
            toast?.icon ?: current?.icon,
            showingMedia && current?.playing == true,
            showingMedia, options.waveform, if (showingMedia) current?.actions ?: 0L else 0L, showingMedia && options.mediaSwipe,
            current?.playbackState ?: PlaybackState.STATE_NONE, toast?.avatar == true, toast?.status == true)
        shell.configureGestures(if (showingMedia) selected else state.identity(now), options.musicActions, showingMedia && options.mediaSwipe)
        shell.geometry(geometry.width, geometry.height, geometry.gap, geometry.maximumWidth, options.position)
        screen?.let { bounds ->
            shell.cameraAnchor(if (options.position != 1) geometry.height * .5f else if (bounds.holeCenter >= 0f) bounds.holeCenter - geometry.top else dp(20).toFloat(),
                if (options.position != 1) 0f else if (bounds.holeWidth > 0) bounds.holeWidth.toFloat() else dp(16).toFloat())
        }
        if (resumingExit) shell.resumeEntry()
        // Retain the current shell inside symmetric Surface outsets when a manual
        // long form shrinks to a short one. No per-frame window/layout update is needed.
        val previousLeft = lastLeft - geometry.left + visualLeft
        val previousWidth = lastWidth * shell.scaleX
        val horizontalOutset = if (!attached) dp(80) else maxOf(dp(80), kotlin.math.ceil(maxOf(-previousLeft,
            previousLeft + previousWidth - geometry.width) - touchWindow.margin).toInt())
        val previousTop = lastTop - geometry.top + visualTop
        val verticalOutset = if (!attached) dp(80) else maxOf(dp(80), kotlin.math.ceil(maxOf(-previousTop,
            previousTop + lastHeight * shell.scaleY - geometry.height) - touchWindow.margin).toInt())
        val outsetsChanged = windowOutsets.left != horizontalOutset || windowOutsets.top != verticalOutset
        windowOutsets.set(horizontalOutset, verticalOutset, horizontalOutset, verticalOutset)
        touchWindow.geometry(geometry.width, geometry.height)
        val windowGeometryChanged = params.width != geometry.width + 2 * touchWindow.margin ||
            params.height != geometry.height + 2 * touchWindow.margin ||
            params.y != geometry.top - touchWindow.margin ||
            params.x != geometry.left - touchWindow.margin - ((screen?.width ?: geometry.width) - (geometry.width + 2 * touchWindow.margin)) / 2
        params.width = geometry.width + 2 * touchWindow.margin
        params.height = geometry.height + 2 * touchWindow.margin
        params.y = geometry.top - touchWindow.margin
        params.x = geometry.left - touchWindow.margin - ((screen?.width ?: geometry.width) - params.width) / 2
        return try {
            val entering = !attached
            val returning = (entering || resumingExit) && showingMedia && current?.playing == true &&
                current.packageName in options.mediaReturnApps && mediaReturn?.token == current.token
            if (showingMedia) mediaReturn = null // One foreground edge can animate only once.
            if (returning) shell.prepareEntry(mediaReturn = true)
            if (entering) {
                // Prepare alpha/scale BEFORE the first Surface can be drawn, including re-entry
                // after a native activity or a notification-panel transition.
                if (!returning) shell.prepareEntry()
                window!!.addView(touchWindow, params); attached = true
                if (!touchWindow.inputRegionAvailable) { hide(); return false }
            }
            else if (outsetsChanged || windowGeometryChanged) window!!.updateViewLayout(touchWindow, params)
            if (entering || resumingExit || secondary || contentChanged || lastWidth != geometry.width ||
                lastHeight != geometry.height || lastLeft != geometry.left || lastTop != geometry.top) {
                shell.transition(lastWidth, lastHeight, geometry.width, geometry.height, entering, secondary,
                    if (entering || returning) null else lastLeft - geometry.left + visualLeft,
                    if (entering || returning) null else lastTop - geometry.top + visualTop)
            }
            lastWidth = geometry.width; lastHeight = geometry.height
            lastTop = geometry.top
            lastLeft = geometry.left
            lastContent = toast ?: current
            updateSpectrum()
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
        spectrum?.stop()
        invalidateActions()
        if (animate && attached && (presentation.phase == IslandWindowPresentation.Phase.VISIBLE ||
                presentation.phase == IslandWindowPresentation.Phase.EXITING)) {
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
            if (attached) window?.removeViewImmediate(touchWindow)
        } finally {
            attached = false
            lastContent = null
            presentation.cancel()
        }
    }

    private fun updateSpectrum(visible: Boolean = presentation.phase == IslandWindowPresentation.Phase.VISIBLE) {
        val content = lastContent as? IslandMediaContent
        val player = selected
        if (!visible || disposed || exiting || !attached || !state.allowed || options.waveformMode != 2 ||
            options.simplified || !ValueAnimator.areAnimatorsEnabled() || content?.playing != true ||
            player == null || player.sessionToken != content.token) {
            spectrum?.stop()
            return
        }
        val source = spectrum ?: IslandAudioSpectrum(scope, ioDispatcher, handler,
            spectrumBackend ?: IslandAudioSpectrum.NativeBackend(context, handler), diagnostics, shell::spectrum).also { spectrum = it }
        source.start(player, foregroundUser)
    }

    private fun openPlayer() {
        val now = SystemClock.uptimeMillis()
        if (disposed || exiting || !attached || !state.allowed) return
        val toast = state.toast(now)
        if (toast?.notificationKey != null) {
            ModuleHelper.guarded { chatOpen?.invoke() }
            clearChat(); render()
            return
        }
        val current = state.media(now)
        val controller = selected
        if (toast == null && (current == null || controller == null || controller.sessionToken != current.token ||
                (lastContent as? IslandMediaContent)?.token != current.token ||
                presentation.phase != IslandWindowPresentation.Phase.VISIBLE || current.packageName == foregroundPackage)) return
        val epoch = actionEpoch
        actionJob?.cancel()
        actionJob = scope.launch(ioDispatcher) {
            actionMutex.withLock {
                ensureActive()
                val actionContext = coroutineContext
                val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
                fun valid() = actionContext.isActive && epoch == actionEpoch && !disposed && state.allowed && !exiting &&
                    keyguard?.isKeyguardLocked != true && (toast != null || selected === controller)
                if (toast != null) {
                    if (valid()) ModuleHelper.guarded { toast.launch?.send() }
                    return@withLock
                }
                val player = controller ?: return@withLock
                val uid = IslandMediaIdentity.uid(player)
                if (uid == null || !IslandMediaIdentity.sameUser(uid, foregroundUser)) {
                    diagnostics.report("player-user-unavailable")
                    return@withLock
                }
                val result = IslandPlayerLaunch.open(::valid,
                    primary = {
                        player.sessionActivity?.takeIf { it.isActivity && it.creatorUid == uid &&
                            it.creatorPackage == player.packageName }?.let { launch -> IslandPlayerLaunch.Entry { launch.send() } }
                    },
                    fallback = {
                        // This public API resolves within the Context's user. Refuse cross-user
                        // fallbacks rather than opening the owner's copy of a work/secondary app.
                        if (foregroundUser != contextUser ||
                            context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true)
                            null
                        else {
                            val info = context.packageManager.getApplicationInfo(player.packageName, 0)
                            if (info.uid != uid || !info.enabled) null else {
                                val sender = context.packageManager.getLaunchIntentSenderForPackage(player.packageName)
                                IslandPlayerLaunch.Entry { sender.sendIntent(context, 0, null, null, null) }
                            }
                        }
                    })
                if (result != IslandPlayerLaunch.Result.STARTED && result != IslandPlayerLaunch.Result.STALE)
                    diagnostics.report("player-${result.name.lowercase()}")
            }
        }
    }

    private fun createSystemActivities(): IslandSystemActivities? = options.feedback.takeIf { it.isNotEmpty() }?.let {
        IslandSystemActivities(context, scope, ioDispatcher, it, ::systemActivity).also { source -> source.start() }
    }

    private fun invalidateActions() {
        actionEpoch++
        actionJob?.cancel(); actionJob = null
        shell.cancelInputs()
    }

    private fun executeGesture(gesture: IslandMusicGesture) {
            val action = options.musicActions.resolve(gesture, options.mediaSwipe)
            val current = state.media(SystemClock.uptimeMillis()) ?: return
            if (action == 1 || disposed || exiting || !attached || !state.allowed || !options.media ||
                current.packageName == foregroundPackage || current.token != (lastContent as? IslandMediaContent)?.token ||
                presentation.phase != IslandWindowPresentation.Phase.VISIBLE) return
            if (action == IslandMusicActions.OPEN_PLAYER) { openPlayer(); return }
            val controller = selected ?: return
            if (controller.sessionToken != current.token) return
            val epoch = actionEpoch
            actionJob?.cancel()
            actionJob = scope.launch(ioDispatcher) {
                actionMutex.withLock {
                ModuleHelper.guarded {
                    val playback = controller.playbackState ?: return@guarded
                    ensureActive()
                    if (epoch != actionEpoch) return@guarded
                    if (!IslandMediaPolicy.active(playback.state)) return@guarded
                    when (action) {
                        85, 87, 88, IslandMusicActions.PLAY, IslandMusicActions.PAUSE -> {
                            val request = IslandTransportAction.resolve(action, playback.state, playback.actions) ?: return@guarded
                            val transport = controller.transportControls
                            ensureActive()
                            if (epoch != actionEpoch) return@guarded
                            when (request) {
                                PlaybackState.ACTION_PLAY -> transport.play()
                                PlaybackState.ACTION_PAUSE -> transport.pause()
                                PlaybackState.ACTION_SKIP_TO_NEXT -> transport.skipToNext()
                                PlaybackState.ACTION_SKIP_TO_PREVIOUS -> transport.skipToPrevious()
                            }
                        }
                        18, 19 -> {
                            val info = controller.playbackInfo
                            val fixed = info.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL &&
                                context.getSystemService(AudioManager::class.java)?.isVolumeFixed != false
                            if (!IslandVolumePolicy.adjustable(info.playbackType,info.volumeControl,info.audioAttributes.usage,fixed)) return@guarded
                            ensureActive()
                            if (epoch == actionEpoch) controller.adjustVolume(
                                if (action == 18) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
                        }
                        else -> if (epoch == actionEpoch) GlobalActions.handleResolvedAction(context, gesture.key, action)
                    }
                }
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        publishMusicReturn()
        handler.removeCallbacks(expiry)
        handler.removeCallbacks(nativeRefresh)
        handler.removeCallbacks(frameRecheck)
        frameRecheckQueued = false
        pipHomeGate?.close()
        freeformGeneration++
        freeformJob?.cancel(); freeformJob = null
        applicationOverlays?.close(); applicationOverlays = null
        clearSystemActivity()
        systemActivities?.dispose(); systemActivities = null
        actionMarkers?.close()
        geometryGeneration++
        geometryJob?.cancel()
        geometryJob = null
        screen = null
        geometryView.get()?.removeOnLayoutChangeListener(geometryLayout)
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
        shell.onDismiss = {}; shell.onOpen = {}; shell.onGesture = {}
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
