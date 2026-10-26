package tv.withaibuild.customiuizer.mods.island

import tv.withaibuild.customiuizer.mods.SystemUIStrongToastHooks
import tv.withaibuild.customiuizer.R

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
    val uid: Int? = null,
    val awaitingPlayback: Boolean = false,
    val artwork: Boolean = true,
) {
    val loading: Boolean get() = awaitingPlayback || IslandMediaPolicy.loading(playbackState)
}

internal object IslandMediaPolicy {
    fun active(state: Int): Boolean = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED ||
        state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING ||
        state == PlaybackState.STATE_FAST_FORWARDING || state == PlaybackState.STATE_REWINDING ||
        state == PlaybackState.STATE_SKIPPING_TO_NEXT || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
        state == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
    fun playing(state: Int) = state == PlaybackState.STATE_PLAYING
    fun loading(state: Int) = state == PlaybackState.STATE_BUFFERING || state == PlaybackState.STATE_CONNECTING ||
        state == PlaybackState.STATE_SKIPPING_TO_NEXT || state == PlaybackState.STATE_SKIPPING_TO_PREVIOUS ||
        state == PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM
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
    liveDismissal: IslandLiveRegistry.Dismissal = IslandLiveRegistry.Dismissal(),
) : ScreenStateController.ScreenStateListener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + ModuleHelper.coroutineFailureHandler)
    private val state = IslandActivityState<IslandToastContent, IslandMediaContent>(dismissal)
    private val live = IslandLiveRegistry(liveDismissal)
    private var liveSources: IslandLiveSources? = null
    private var liveContent: IslandToastContent? = null
    private var liveSourceOptions: Pair<Set<IslandLiveKind>, Set<String>>? = null
    private val privacyAccess = if (IslandLiveKind.PRIVACY in options.liveKinds && markerLoader != null)
        ModuleHelper.guarded<IslandPrivacyAccess?>(null) { IslandPrivacyAccess(markerLoader) } else null
    private val forms = IslandFormState()
    private val motionCoordinator = IslandMotionCoordinator()
    private val idleIdentity = Any()
    private val shell = IslandActivityView(context)
    private val touchWindow = IslandTouchWindow(shell)
    private var displayedMedia: MediaSession.Token? = null
    private val presentation = IslandWindowPresentation(touchWindow, target = { shell },
        eligible = { !disposed && attached && (state.allowed || lastContent === idleIdentity && idleShadeAllowed()) &&
            (lastContent != null || exiting) },
        shown = { event ->
            if (event === lowBatteryLease) batteryWarningMarker?.visible(true)
            (lastContent as? IslandMediaContent)?.takeIf { it.token == event && it.playing && !exiting }
                ?.let { displayedMedia = it.token }
            updateSpectrum(visible = true)
        }, hidden = { spectrum?.stop(); recorderWave.stop(); batteryWarningMarker?.visible(false) }, frameAllowed = ::frameAllowed,
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
    // Capacity one native-selected independent player, no artwork or extra callback retained.
    private var backgroundPlayer: MediaController? = null
    private var destroyedToken: MediaSession.Token? = null
    private var sessionJob: Job? = null
    private var sessionEpoch = 0L
    private var readJob: Job? = null
    private var actionJob: Job? = null
    private var chatLease: IslandActivityState.Lease? = null
    private var chatKey: String? = null
    private var chatNotification: StatusBarNotification? = null
    private var chatOpen: (() -> Boolean)? = null
    private var chatHidden: (() -> Boolean)? = null
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
    private val recorderWave by lazy { IslandRecorderMeter.Consumer(scope, ioDispatcher, shell::spectrum) }
    private val snapshotMutex = Mutex()
    private val sessionMutex = Mutex()
    private val actionMutex = Mutex()
    private val geometryMutex = Mutex()
    private var geometryJob: Job? = null
    private var geometryGeneration = 0L
    private data class Screen(val width: Int, val height: Int, val holeWidth: Int, val holeTop: Int,
        val holeCenter: Float, val safeLeft: Int, val safeRight: Int, val holeLeft: Int, val holeRight: Int,
        val holeBottom: Int, val holePhysicalTop: Int, val statusBarHeight: Int = 0)
    private var screen: Screen? = null
    @Volatile private var actionEpoch = 0L
    private var readGeneration = 0L
    private var media: IslandMediaContent? = null
    private data class RecentMedia(val token: MediaSession.Token, val packageName: String, val user: UserHandle, val uid: Int)
    // One identity, no retained Controller, metadata, artwork, callback or history.
    private var recentMedia: RecentMedia? = null
    private var foregroundPackage: String? = null
    private var previousForegroundPackage: String? = null
    private var foregroundChangedAt = 0L
    private var foregroundBaseline = false
    private data class AppIcon(val token: MediaSession.Token, val epoch: Long,
        val label: String, val drawable: Drawable?)
    private val iconCacheLock = Any()
    @Volatile private var iconEpoch = 0L
    @Volatile private var appIcon: AppIcon? = null
    private data class Artwork(val source: WeakReference<Bitmap>, val epoch: Long, val drawable: BitmapDrawable)
    @Volatile private var artwork: Artwork? = null
    private var pauseDeadline: Long? = null
    // One deadline for this session's loading episode; metadata churn cannot renew it.
    private var loadingDeadline: Long? = null
    private var observedPlaybackState: Int? = null
    private var mediaRevealAt: Long? = null
    private var pipSnapshot = IslandPipSnapshot(0, 0)
    private val pipHomeGate = pipCandidate?.let { IslandPipHomeGate(scope, ioDispatcher, it, ::pipHomeGateChanged) }
    // Installation-time option: changing the switch requires the same SystemUI restart as
    // installing its native event hooks. Other preference refreshes cannot change this snapshot.
    private val hideInFloatingWindow = options.hideInFloatingWindow
    private var freeformKnown = !hideInFloatingWindow || freeform == null
    private var freeformPresent = false
    private var freeformPackages: Set<String>? = null
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
    private var batterySignals: IslandBatterySignals? = null
    private var batteryWarningMarker: IslandBatteryWarningMarker? = null
    private var lowBatteryLease: IslandActivityState.Lease? = null
    private var powerSave = false
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
    private var compactObserver: android.view.ViewTreeObserver? = null
    private var lastCompactClearance = -1
    private val compactLayout = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        val bounds = screen
        if (!disposed && options.enabled && options.automaticWidth && state.allowed && bounds != null) {
            val center = if (bounds.holeWidth > 0) (bounds.holeLeft + bounds.holeRight) * .5f else bounds.width * .5f
            val clearance = DynamicIslandStatusBarFade.compactClearance(bounds.width, dp(IslandSizing.SHORT_DP), center)
            if (clearance != lastCompactClearance) { lastCompactClearance = clearance; render() }
        }
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
        windowAnimations = 0
        XposedHelpers.setIntField(this, "privateFlags", XposedHelpers.getIntField(this, "privateFlags") or
            XposedHelpers.getStaticIntField(WindowManager.LayoutParams::class.java, "PRIVATE_FLAG_NO_MOVE_ANIMATION"))
        // A translated root still gets cropped by its Surface. Keep bounded rendering outsets
        // for drag and collapse, without enlarging the logical window or its touchable frame.
        (XposedHelpers.getObjectField(this, "surfaceInsets") as Rect).set(dp(16), dp(16), dp(16), dp(16))
        XposedHelpers.setBooleanField(this, "hasManualSurfaceInsets", true)
        XposedHelpers.setBooleanField(this, "preservePreviousSurfaceInsets", false)
        access.trust(this)
    }
    private val expiry = Runnable { ModuleHelper.guarded { render() } }
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
                    key?.startsWith("system_island_music_") == true || key?.startsWith("system_island_idle_") == true) invalidateActions()
                if (!options.media) recentMedia = null
                if (previousOptions.position != options.position || previousOptions.horizontalOffset != options.horizontalOffset ||
                    previousOptions.height != options.height || previousOptions.shortWidth != options.shortWidth ||
                    previousOptions.longWidth != options.longWidth || previousOptions.automaticWidth != options.automaticWidth)
                    presentation.cancel()
                if (!options.enabled) dispose()
                else {
                    if (previousOptions.charging != options.charging || previousOptions.lowBattery != options.lowBattery)
                        configureBatterySignals()
                    if (previousOptions.liveKinds != options.liveKinds || previousOptions.excludedApps != options.excludedApps) configureLiveSources()
                    if (!options.toast && state.toast(SystemClock.uptimeMillis())?.let {
                            !it.status && it.notificationKey == null } == true) state.clearToast()
                    if (!options.media) { clearMedia(); state.allowMediaAgain() }
                    if (chatNotification?.let { !notificationSourceAllowed(it) } == true) clearChat()
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
                freeformPackages = null
                refreshFloatingWindows()
                destroyedToken = selected?.sessionToken
                    ?: nativeManager.get()?.let { access.controller(it)?.sessionToken }
                readGeneration++
                readJob?.cancel()
                actionJob?.cancel()
                recentMedia = null
                state.clear()
                live.clear(); liveContent = null
                liveSources?.close(); liveSources = null; liveSourceOptions = null
                configureLiveSources()
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
        shell.onDismiss = {
            if ((lastContent as? IslandMediaContent)?.token == backgroundPlayer?.sessionToken) backgroundPlayer = null
            if (lastContent === idleIdentity) forms.dismissIdle() else {
                (lastContent as? IslandToastContent)?.liveActivity?.let { live.dismiss(it.key) }
                state.dismiss()
            }
            forms.collapse()
            render()
        }
        shell.onExpand = {
            if (lastContent !== idleIdentity && forms.expand()) render()
        }
        shell.onOpen = {
            if (lastContent === idleIdentity) executeIdleAction("tap", options.idleActions.tap) else openPlayer()
        }
        shell.onGesture = { gesture ->
            if (lastContent === idleIdentity) executeIdleAction(gesture.suffix, options.idleActions.gestures.resolve(gesture, true))
            else executeGesture(gesture)
        }
        state.allowed = false
        try {
            ModuleHelper.observePreferenceChange(preferenceObserver, this)
            ScreenStateController.addListener(context, this)
            context.registerReceiver(userReceiver, IntentFilter(USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED)
            userReceiverRegistered = true
            context.registerComponentCallbacks(configuration)
            configurationRegistered = true
            geometryView.get()?.let { host ->
                host.addOnLayoutChangeListener(geometryLayout)
                host.viewTreeObserver.takeIf { it.isAlive }?.let { observer ->
                    compactObserver = observer
                    observer.addOnGlobalLayoutListener(compactLayout)
                }
            }
            refreshGeometry()
            if (options.enabled) systemActivities = createSystemActivities()
            if (options.enabled) configureBatterySignals()
            if (options.enabled) configureLiveSources()
            actionMarkers?.update(options.feedback)
            if (hideInFloatingWindow && trackApplicationOverlays) ModuleHelper.guarded {
                applicationOverlays = IslandApplicationOverlaySignal.Subscriber(context, handler,
                    send = { message -> scope.launch(ioDispatcher) {
                        ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(context, message) }
                    } }, changed = { onMain {
                        val wasPresented = lastContent != null
                        mediaReturnInvalidatedAt = SystemClock.uptimeMillis()
                        if (floatingWindowBlocked()) { cancelMediaReturn() }
                        if (backgroundPlayer != null) nativeManager.get()?.let { updateFromNative(it) }
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

    fun refreshLivePrivacy() = onMain { liveSources?.refresh() }

    private fun configureLiveSources() {
        val kinds = options.liveKinds.filterTo(linkedSetOf()) { it != IslandLiveKind.SMS && it != IslandLiveKind.CALENDAR }
        val configuration = Pair(kinds, options.excludedApps)
        if (configuration == liveSourceOptions) return
        liveSourceOptions = configuration
        liveSources?.close(); liveSources = null
        live.clear(preserveDismissal = true); liveContent = null; state.setActivity(null, null)
        if (kinds.isEmpty() || foregroundUser != contextUser) return
        liveSources = IslandLiveSources(context, handler, scope, ioDispatcher, context.applicationInfo.uid / 100000,
            kinds, privacyAccess, options.excludedApps) { kind, value ->
            if (!disposed) { live.update(kind, value); render() }
        }
    }

    private fun selectLive() {
        val user = if (foregroundUser == contextUser) context.applicationInfo.uid / 100000 else -1
        val value = live.select(options.liveKinds, options.excludedApps, user,
            privacyIsSecondary = state.uncoveredMedia()?.packageName?.let { it != foregroundPackage } == true,
            elapsed = SystemClock.elapsedRealtime())
        if (liveContent?.liveActivity != value) {
            liveContent = value?.let {
                val res = ModuleHelper.getModuleRes(context)
                val source = res.getString(it.kind.label)
                val text = when {
                    it.clock != null -> IslandLiveLabels.time(it.clock.millis(SystemClock.elapsedRealtime()))
                    it.kind == IslandLiveKind.CALL && it.alert -> res.getString(R.string.system_island_live_ringing)
                    it.alert && it.kind == IslandLiveKind.TIMER -> res.getString(R.string.system_island_live_finished)
                    it.kind == IslandLiveKind.PRIVACY -> res.getString(if (it.privacy == IslandPrivacyAccess.CAMERA)
                        R.string.system_island_live_camera else if (it.privacy == IslandPrivacyAccess.MICROPHONE)
                        R.string.system_island_live_microphone else R.string.system_island_live_privacy)
                    else -> source
                }
                val detail = if (it.clock?.pausedMillis != null) "$source · ${res.getString(R.string.system_island_paused)}" else source
                val icon = ModuleHelper.guarded<Drawable?>(null) {
                    context.packageManager.getApplicationIcon(it.packageName)
                }
                IslandToastContent(text, it.packageName, icon, launch = it.open, detail = detail, liveActivity = it)
            }
        }
        state.setActivity(value?.key, liveContent)
        shell.privacy = if (state.allowed && IslandLiveKind.PRIVACY in options.liveKinds) live.privacy(user) else 0
    }

    private fun notificationSourceAllowed(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName in options.excludedApps || sbn.packageName in options.chatExcludedApps) return false
        val selectedNative = IslandLiveLabels.notificationKind(sbn.packageName, sbn.notification.category,
            sbn.notification.channelId, sbn.notification.extras.getString(android.app.Notification.EXTRA_TEMPLATE)) in options.liveKinds
        return selectedNative || options.chat && sbn.packageName in options.notificationApps
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

    private fun idleShadeAllowed(): Boolean = !disposed && options.enabled && options.idle && !forms.idleSuppressed &&
        shadeBlocked && portraitDisplay && ScreenStateController.isScreenOn() &&
        safetyOwner?.let { ModuleHelper.guarded(false) { access.isShadeBlocked(it) } } == true

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

    override fun onScreenStateChanged(isOn: Boolean) = onMain {
        val restoreIdle = isOn && forms.idleSuppressed
        if (isOn) forms.wake()
        refreshSafety()
        if (restoreIdle && state.allowed) render()
    }

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
        if (backgroundPlayer != null) nativeManager.get()?.let { updateFromNative(it) }
        return true
    }

    private fun floatingWindowBlocked(pip: IslandPipSnapshot = pipSnapshot, notification: Boolean = false,
        allowMedia: Boolean = true): Boolean = IslandFloatingPolicy.blocked(hideInFloatingWindow, notification,
        media?.takeIf { allowMedia }, foregroundUser,
        freeformKnown, freeformPresent, freeformPackages, pip,
        applicationOverlays?.owns(media?.uid) == true)

    private fun sourceExcluded(packageName: String, user: UserHandle?, uid: Int?) = IslandFloatingPolicy.excludes(
        hideInFloatingWindow, packageName, user, foregroundUser, freeformKnown, freeformPresent, freeformPackages,
        pipSnapshot, applicationOverlays?.owns(uid) == true)

    private fun pipHomeBlocked(notification: Boolean = false, current: IslandMediaContent? = media): Boolean {
        val gate = pipHomeGate ?: return false
        if (gate.source == null) return false
        if (notification) return false
        return gate.matches(current)
    }

    /** Read only the already-published Shell snapshot; no native query or frame work queue. */
    private fun frameAllowed(): Boolean {
        if (disposed || !options.enabled) return false
        // Safety/panel removal is owned by refreshSafety/render. This additional
        // fence must not stall their existing exit animation on an old Surface buffer.
        if (exiting || !state.allowed) return true
        val pip = pipStateReader?.invoke()?.takeIf { it.generation >= pipSnapshot.generation } ?: pipSnapshot
        val notification = (lastContent as? IslandToastContent)?.let { it.notificationKey != null || it.liveActivity != null } == true
        return !floatingWindowBlocked(pip, notification, lastContent is IslandMediaContent) &&
            !pipHomeBlocked(notification, lastContent as? IslandMediaContent)
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
        if (update.confirmed && (pipHomeBlocked() || sameMedia)) {
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
                val snapshot = withContext(ioDispatcher) {
                    try { reader.snapshot(user) }
                    catch (error: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(error)
                        if (error is CancellationException) throw error
                        diagnostics.report("floating-window-state-unavailable")
                        IslandFreeformPort.Snapshot(null)
                    }
                }
                if (disposed || generation != freeformGeneration || user != foregroundUser) return@withLock
                val wasHidden = floatingWindowBlocked()
                freeformKnown = snapshot.present != null
                snapshot.present?.let { freeformPresent = it }
                freeformPackages = snapshot.packages
                if (wasHidden || floatingWindowBlocked()) mediaReturnInvalidatedAt = SystemClock.uptimeMillis()
                if (wasHidden && !floatingWindowBlocked() && media?.packageName != foregroundPackage) {
                    mediaRevealAt = SystemClock.uptimeMillis() + IslandMotion.MEDIA_DELAY
                    nativeManager.get()?.let { updateFromNative(it) }
                }
                if (backgroundPlayer != null) nativeManager.get()?.let { updateFromNative(it) }
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
                    !reducedMotion() && ValueAnimator.areAnimatorsEnabled()
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
        if (sourceChanged && (media?.packageName in options.mediaReturnApps || options.liveReturn && liveContent != null))
            musicReturnPublisher?.publish(force = true)
    }

    internal fun nativeMusicReturn(frame: IslandMusicReturnSignal.Frame, committedAt: Long = SystemClock.uptimeMillis()) = onMain {
        pipStateReader?.invoke()?.let(::applyPipSnapshot)
        val activity = state.activity(SystemClock.uptimeMillis())?.liveActivity
        if (activity != null && options.liveReturn && frame.packageName == activity.packageName &&
            IslandLiveKind.canReturn(activity.kind, activity.packageName) &&
            frame.user == foregroundUser && frame.user == UserHandle.getUserHandleForUid(activity.uid) && frame.valid() &&
            !disposed && state.allowed && !reducedMotion() && !floatingWindowBlocked(allowMedia = false) &&
            ValueAnimator.areAnimatorsEnabled() &&
            (foregroundPackage == activity.packageName || foregroundPackage == IslandMusicReturnSignal.LAUNCHER) &&
            committedAt >= foregroundChangedAt) {
            render()
            return@onMain
        }
        val current = media ?: return@onMain
        if (pipSnapshot.blocks(current) || floatingWindowBlocked() ||
            pipHomeGate?.let { it.confirmed && it.matches(current) } == true || committedAt <= mediaReturnInvalidatedAt) return@onMain
        if (disposed || !state.allowed || !options.mediaReturn || reducedMotion() || !frame.valid() ||
            !ValueAnimator.areAnimatorsEnabled() || !current.playing || frame.packageName != current.packageName ||
            frame.user != foregroundUser || frame.user != current.user || current.packageName !in options.mediaReturnApps ||
            foregroundPackage != current.packageName && foregroundPackage != IslandMusicReturnSignal.LAUNCHER ||
            foregroundPackage == IslandMusicReturnSignal.LAUNCHER && foregroundChangedAt > committedAt &&
                previousForegroundPackage != current.packageName ||
            foregroundPackage == current.packageName && foregroundChangedAt > committedAt) return@onMain
        val now = SystemClock.uptimeMillis()
        if (foregroundPackage == IslandMusicReturnSignal.LAUNCHER && attached && !exiting &&
            presentation.phase == IslandWindowPresentation.Phase.VISIBLE &&
            (lastContent as? IslandMediaContent)?.token == current.token) {
            // A canceled opening may never have displaced the existing island. The
            // native window can land on that same object without hiding it for 80 ms.
            mediaReturn = null
            mediaRevealAt = null
            render()
            return@onMain
        }
        mediaReturn = MediaReturn(current.token, now + IslandMotion.MEDIA_DELAY)
        mediaRevealAt = now + IslandMotion.MEDIA_RETURN_DELAY
        foregroundBaseline = true
        nativeManager.get()?.let { updateFromNative(it) }
        render()
    }

    fun systemActivity(activity: IslandSystemActivity) = onMain {
        if (disposed || !options.enabled || floatingWindowBlocked(allowMedia = false) || activity.kind !in options.feedback || (!state.allowed && !shadeBlocked)) return@onMain
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
        if (disposed || !options.enabled || !state.allowed || floatingWindowBlocked(allowMedia = false)) return
        val now = SystemClock.uptimeMillis()
        val ready = systemFeedbackSlot.readyAt(now) ?: return
        handler.postAtTime(systemFeedback, ready)
    }

    private fun presentSystemFeedback() {
        if (disposed || !options.enabled || !state.allowed || floatingWindowBlocked(allowMedia = false)) return
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
        val animation = if (!reducedMotion() && ValueAnimator.areAnimatorsEnabled())
            IslandMotion.ENTER else 0L
        val remaining = entry.expiresAt - now
        if (remaining <= 0) return
        val lease = state.offer(content, now, minOf(remaining, animation + IslandSystemFeedback.DWELL))
        systemPresentation = SystemPresentation(lease, entry.activity)
        render()
    }

    fun showToast(content: IslandToastContent, duration: Long): IslandActivityState.Lease? {
        if (Looper.myLooper() != Looper.getMainLooper() || disposed || !options.enabled || !options.toast ||
            !state.allowed || floatingWindowBlocked(notification = content.notificationKey != null, allowMedia = false) || content.packageName in options.excludedApps ||
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
        if (Looper.myLooper() != Looper.getMainLooper() || disposed || !options.enabled || !notificationSourceAllowed(sbn) || floatingWindowBlocked(notification = true)) return false
        // The native keyguard can lead the status-bar callback. Read it at the
        // takeover boundary, before any notification lease or native mutation.
        refreshSafety()
        if (!state.allowed ||
            sbn.packageName in options.excludedApps ||
            sbn.packageName in options.chatExcludedApps || sbn.packageName == foregroundPackage ||
            sbn.user != foregroundUser || sbn.notification.contentIntent == null) {
            diagnostics.report("notification-source-or-safety"); return false
        }
        val now = SystemClock.uptimeMillis()
        if (chatNotification === sbn) {
            val current = chatLease?.let { attached && !exiting && state.owns(it, now) } == true
            if (current) {
                chatOpen = bound.open // A new native Row may keep the same Sbn and lease.
                chatHidden = bound.hidden
            }
            return current
        }
        // Native system feedback and an already accepted ordinary Toast keep their lease.
        if (state.toast(now)?.let { it.notificationKey == null } == true) {
            diagnostics.report("notification-feedback-active"); return false
        }
        val rank = bound.ranking
        val prepared = IslandChatNotifications.prepare(context, sbn, rank.importance,
            rank.matchesInterruptionFilter(), rank.suppressedVisualEffects,
            nativeIcon = bound.icon, calendar = IslandLiveKind.CALENDAR in options.liveKinds &&
                IslandLiveLabels.notificationKind(sbn.packageName, sbn.notification.category,
                    sbn.notification.channelId) == IslandLiveKind.CALENDAR)
            ?: run { diagnostics.report("notification-native-content"); return false }
        clearChat()
        chatKey = sbn.key; chatNotification = sbn; chatOpen = bound.open; chatHidden = bound.hidden
        chatLease = state.offer(prepared, now, 4000)
        if (!render()) { clearChat(); return false }
        if (chatNotification !== sbn || chatOpen !== bound.open ||
            chatLease?.let { state.owns(it, SystemClock.uptimeMillis()) } != true) {
            // Window insertion can re-enter a native privacy update. Reconcile any
            // presentation prepared by the old call before retaining its native alert.
            render()
            return false
        }
        if (!presentationAllowed()) { refreshSafety(); return false }
        ModuleHelper.guarded { shell.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) }
        return true
    }

    fun notificationRemoved(key: String) = onMain {
        if (chatKey == key) { clearChat(); render() }
    }

    /** Native sensitivity render passes are events, not a frame/polling subscription. */
    fun notificationPrivacyChanged() = onMain {
        if (chatNotification != null && ModuleHelper.guarded(true) { chatHidden?.invoke() == true }) {
            clearChat(); render()
        }
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
        chatHidden = null
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
        val nativeSelection = access.controller(owner)
        // Some players temporarily remove the native card between tracks. Keep only
        // the already selected, loading session; its existing callback owns recovery.
        val nativePlayer = nativeSelection ?: selected?.takeIf { media?.loading == true &&
            loadingDeadline?.let { it > SystemClock.uptimeMillis() } == true }
        if (nativePlayer == null || nativePlayer.sessionToken == destroyedToken) {
            clearMedia()
            render()
            return
        }
        val previous = media
        if (selected != null && selected?.packageName != nativePlayer.packageName && previous?.playing == true &&
            previous.user == foregroundUser && !state.isMediaDismissed(previous.token) &&
            !sourceExcluded(previous.packageName, previous.user, previous.uid)) backgroundPlayer = selected
        val nativeUid = media?.takeIf { it.token == nativePlayer.sessionToken }?.uid ?: IslandMediaIdentity.uid(nativePlayer)
        val nativeUser = nativeUid?.let { UserHandle.getUserHandleForUid(it) }
        val independent = backgroundPlayer?.takeIf { candidate ->
            if (!sourceExcluded(nativePlayer.packageName, nativeUser, nativeUid) || candidate.packageName == nativePlayer.packageName ||
                candidate.packageName in options.excludedApps || state.isMediaDismissed(candidate.sessionToken)) false
            else {
                val uid = media?.takeIf { it.token == candidate.sessionToken }?.uid ?: IslandMediaIdentity.uid(candidate)
                val user = uid?.let { UserHandle.getUserHandleForUid(it) }
                user == foregroundUser && !sourceExcluded(candidate.packageName, user, uid)
            }
        }
        val proposed = independent ?: nativePlayer
        val controller = selected?.takeIf { it.sessionToken == proposed.sessionToken } ?: proposed
        if (selected !== controller) {
            clearMedia(keepBackground = true)
            selected = controller
        }
        val metadata = if (independent == null && nativeSelection != null) access.metadata(owner) else controller.metadata
        val readOptions = options
        val currentIconEpoch = iconEpoch
        val retained = media?.takeIf { it.token == controller.sessionToken }
        val generation = ++readGeneration
        readJob?.cancel()
        readJob = scope.launch {
            val prepared = withContext(ioDispatcher) {
                snapshotMutex.withLock {
                    ensureActive()
                    val playback = controller.playbackState?.state
                    if (independent != null && playback != PlaybackState.STATE_PLAYING &&
                        !(retained != null && playback != null && IslandMediaPolicy.loading(playback))) null
                    else prepare(controller, if (independent != null) controller.metadata else metadata, readOptions, currentIconEpoch, retained)
                }
            }
            if (disposed || !state.allowed || generation != readGeneration ||
                selected?.sessionToken != controller.sessionToken) return@launch
            val now = SystemClock.uptimeMillis()
            foregroundBaseline = false
            if (prepared == null) {
                clearMedia()
            } else {
                if (sessionJob == null) watchSession(controller)
                if (prepared.playing && prepared.user == foregroundUser && prepared.uid != null && prepared.user != null &&
                    !sourceExcluded(prepared.packageName, prepared.user, prepared.uid))
                    recentMedia = RecentMedia(prepared.token, prepared.packageName, prepared.user, prepared.uid)
                observedPlaybackState = prepared.playbackState
                if (mediaRevealAt?.let { now >= it } == true) mediaRevealAt = null
                if (prepared.loading) {
                    cancelMediaReturn()
                    pauseDeadline = null
                    if (loadingDeadline == null) loadingDeadline = if (retained != null &&
                        displayedMedia == prepared.token) Long.MAX_VALUE else 0L
                    // Explicit buffering has a live owner and needs no polling or
                    // timeout. A following ambiguous PAUSED signal gets bounded grace.
                    if (!IslandMediaPolicy.loading(prepared.playbackState) && loadingDeadline == Long.MAX_VALUE)
                        loadingDeadline = now + IslandMotion.LOADING_LIFETIME
                    media = prepared
                    state.setMedia(prepared.token, prepared.takeIf { loadingDeadline?.let { now < it } == true })
                    if (shadeBaseline) { shadeBaseline = false; clearShadePlayback() }
                    render()
                    return@launch
                }
                loadingDeadline = null
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
                        if (!reducedMotion() && ValueAnimator.areAnimatorsEnabled()) IslandMotion.EXIT else 0L
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
                    val exitLead = if (!reducedMotion() && ValueAnimator.areAnimatorsEnabled()) IslandMotion.EXIT else 0L
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
            override fun onMetadataChanged(metadata: MediaMetadata?) = ModuleHelper.guarded {
                onMain {
                    if (epoch == sessionEpoch && selected?.sessionToken == controller.sessionToken && state.allowed)
                        nativeManager.get()?.let { updateFromNative(it) }
                }
            }
            override fun onSessionDestroyed() = ModuleHelper.guarded {
                onMain {
                    if (epoch != sessionEpoch || selected?.sessionToken != controller.sessionToken) return@onMain
                    destroyedToken = controller.sessionToken
                    if (recentMedia?.token == controller.sessionToken) recentMedia = null
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
        epoch: Long, retained: IslandMediaContent?): IslandMediaContent? = try {
        val playback = controller.playbackState
        val pkg = controller.packageName
        if (playback == null || !IslandMediaPolicy.active(playback.state) || pkg in options.excludedApps) null
        else if (retained != null && metadata?.getText(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() &&
            retained.title != appIcon?.label)
            retained.copy(playing = false, actions = playback.actions, playbackState = playback.state, awaitingPlayback = true)
        else if (retained != null && (IslandMediaPolicy.loading(playback.state) ||
                retained.loading && playback.state == PlaybackState.STATE_PAUSED))
            retained.copy(playing = false, actions = playback.actions, playbackState = playback.state,
                awaitingPlayback = true)
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
            val uid = IslandMediaIdentity.uid(controller)
            val cover = prepareArtwork(metadata, epoch)
            IslandMediaContent(controller.sessionToken, pkg, title,
                metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST)?.toString()?.take(160),
                cover ?: icon.drawable,
                IslandMediaPolicy.playing(playback.state), playback.actions, playback.state,
                uid?.let { UserHandle.getUserHandleForUid(it) }, uid, artwork = cover != null)
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
        val previous = artwork?.takeIf { it.epoch == epoch && !it.drawable.bitmap.isRecycled }
        if (previous != null && previous.drawable.bitmap.width == bitmap.width && previous.drawable.bitmap.height == bitmap.height &&
            previous.drawable.bitmap.sameAs(bitmap)) {
            // Binder may parcel the same cover on several mirrored callbacks. Compare
            // one bounded (<=128px) current cover on this worker, never in drawing.
            if (bitmap !== source && bitmap !== previous.drawable.bitmap) bitmap.recycle()
            synchronized(iconCacheLock) {
                if (iconEpoch == epoch) artwork = previous.copy(source = WeakReference(source))
            }
            return previous.drawable
        }
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

    private fun clearMedia(keepBackground: Boolean = false) {
        if (!keepBackground) backgroundPlayer = null
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
        loadingDeadline = null
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
                        hole?.left ?: 0, hole?.right ?: 0, hole?.bottom ?: 0, hole?.top ?: 0, safe.top)
                }
            }
            if (!disposed && generation == geometryGeneration) { screen = measured; render() }
        }
    }

    private fun geometry(label: String? = null, mediaOnly: Boolean = false, idle: Boolean = false): Geometry? {
        if (!touchWindow.inputRegionAvailable) return null
        val bounds = screen ?: return null
        // Landscape cutouts are at a side; keep that layout entirely native.
        if (bounds.width >= bounds.height) return null
        val now = SystemClock.uptimeMillis()
        val feedback = (state.toast(now) ?: state.activity(now))?.takeIf { label == null && !mediaOnly }
        val heightDp = if (options.automaticHeight) IslandSizing.automaticHeight(
            (bounds.holeWidth / context.resources.displayMetrics.density).toInt(),
            (bounds.statusBarHeight / context.resources.displayMetrics.density).toInt()) else options.height
        shell.contentHeight(heightDp)
        val caption = label ?: feedback?.text
        val centered = options.position == 1
        val gap = if (centered) maxOf(dp(40), bounds.holeWidth + dp(16)) else 0
        val center = if (bounds.holeWidth > 0) (bounds.holeLeft + bounds.holeRight) * .5f else bounds.width * .5f
        val nativeClearance = if (centered && options.automaticWidth)
            DynamicIslandStatusBarFade.measuredCompactClearance(bounds.width, center) else null
        val clearance = if (centered && options.automaticWidth) nativeClearance ?: dp(IslandSizing.SHORT_DP)
            else bounds.safeRight - bounds.safeLeft
        val density = context.resources.displayMetrics.density
        val expanded = !idle && !mediaOnly && label == null && forms.expanded
        val compactMaximumWidth = IslandSizing.maximumWidth(density, bounds.width, clearance,
            shortDp = options.shortWidth, longDp = options.longWidth, manual = !options.automaticWidth,
            heightDp = heightDp, centered = centered)
        val maximumWidth = if (expanded && options.automaticWidth) {
            val content = maxOf(shell.textWidth(feedback?.text ?: media?.title.orEmpty()),
                shell.textWidth(feedback?.detail ?: media?.artist.orEmpty())) * .94f
            IslandSizing.expanded(density, bounds.width, bounds.safeRight - bounds.safeLeft, gap,
                content, heightDp, centered)
        } else compactMaximumWidth
        val naturalWidth = IslandSizing.collapsed(density, bounds.width, clearance, gap, dp(24).toFloat(),
            caption?.let { if (feedback?.status == true) shell.statusWidth(it) else shell.textWidth(it) }
                ?: dp(24).toFloat(), shortDp = options.shortWidth, longDp = options.longWidth,
            manual = !options.automaticWidth, heightDp = heightDp, centered = centered)
        val shortWidth = IslandSizing.collapsed(density, bounds.width, clearance, gap, dp(24).toFloat(),
            dp(24).toFloat(), shortDp = options.shortWidth, longDp = options.longWidth,
            manual = !options.automaticWidth, heightDp = heightDp, centered = centered)
        val width = when {
            idle -> IslandSizing.idleWidth(density, dp(heightDp), bounds.holeWidth, shortWidth, options.idleWidth)
            expanded -> maximumWidth
            feedback?.notificationKey != null -> shortWidth
            else -> naturalWidth
        }
        val offset = dp(SystemUIStrongToastHooks.currentSnapshot()?.islandOffsetDp ?: 0)
        val anchor = if (bounds.holeTop >= 0) bounds.holeTop else dp(8)
        val requiredCaption = caption?.let { text ->
            val measured = if (feedback?.status == true) shell.statusWidth(text) *
                (11f / (13f + (heightDp - 40) * .1f)) else shell.textWidth(text)
            (measured + dp(64) + 1).toInt()
        } ?: dp(88)
        val minimumWidth = if (idle) minOf(width, maxOf(dp(heightDp), bounds.holeWidth + dp(16)))
            else if (centered) dp(100) + gap else minOf(width, maxOf(dp(88), requiredCaption))
        val smallest = if (options.idle) IslandSizing.idleWidth(density, dp(heightDp), bounds.holeWidth, shortWidth, options.idleWidth) else shortWidth
        val cameraLimit = IslandSizing.horizontalOffsetLimit(shortWidth, smallest, gap, dp(IslandSizing.EDGE_DP + 24))
        val offsetLimit = if (nativeClearance != null) minOf(cameraLimit,
            ((nativeClearance - compactMaximumWidth) / 2).coerceAtLeast(0)) else cameraLimit
        val horizontalOffset = if (centered && bounds.holeWidth > 0) dp(options.horizontalOffset).coerceIn(-offsetLimit, offsetLimit)
            else dp(options.horizontalOffset)
        val placement = IslandPosition.layout(width, dp(heightDp), minimumWidth,
            bounds.safeLeft, bounds.safeRight, options.position, horizontalOffset,
            (anchor + offset).coerceAtLeast(0), gap, bounds.holeLeft, bounds.holeRight,
            bounds.holePhysicalTop, bounds.holeBottom, dp(8)) ?: return null
        val height = if (feedback?.status == true)
            shell.statusHeight(feedback.text, placement.width, placement.gap, dp(heightDp)) ?: return null
            else dp(heightDp)
        return Geometry(placement.width, height, placement.top, placement.gap,
            if (centered) maximumWidth else placement.width, placement.left)
    }

    private fun liveReturnFrame(now: Long): IslandMusicReturnSignal.Frame? {
        val current = state.activity(now)?.liveActivity ?: return null
        val bounds = screen ?: return null
        if (!options.liveReturn || current.packageName !in options.returnPackages() ||
            !IslandLiveKind.canReturn(current.kind, current.packageName) ||
            foregroundUser != UserHandle.getUserHandleForUid(current.uid) || state.toast(now) != null) return null
        val geometry = geometry() ?: return null
        return IslandMusicReturnSignal.Frame(current.packageName, UserHandle.getUserHandleForUid(current.uid),
            geometry.left.toFloat(), geometry.top.toFloat(), (geometry.left + geometry.width).toFloat(),
            (geometry.top + geometry.height).toFloat(), bounds.width, bounds.height)
    }

    private fun publishMusicReturn() {
        val enabled = !disposed && options.enabled && options.returnPackages().isNotEmpty() &&
            !reducedMotion() && !floatingWindowBlocked() && ValueAnimator.areAnimatorsEnabled()
        if (!enabled) {
            val old = musicReturnPublisher
            musicReturnPublisher = null
            old?.let { ModuleHelper.guarded { it.close() } }
            return
        }
        val publisher = musicReturnPublisher ?: IslandMusicReturnSignal.Publisher(context.applicationContext,
            handler, scope, ioDispatcher, onCommit = ::nativeMusicReturn,
            onImpulse = { shell.returnStrength = it }) {
            pipStateReader?.invoke()?.let(::applyPipSnapshot)
            val now = SystemClock.uptimeMillis()
            val liveFrame = if (state.allowed) liveReturnFrame(now) else null
            val current = state.media(now)
            val bounds = screen
            if (liveFrame != null) liveFrame
            else if (!state.allowed || current?.playing != true || current.user == null || pipSnapshot.blocks(current) ||
                pipHomeGate?.let { it.confirmed && pipHomeBlocked(current = current) } == true ||
                current.user != foregroundUser || current.packageName !in options.mediaReturnApps ||
                state.toast(SystemClock.uptimeMillis()) != null || state.activity(SystemClock.uptimeMillis()) != null || bounds == null) null
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
        val now = SystemClock.uptimeMillis()
        if (pauseDeadline?.let { now >= it } == true) state.setMedia(null, null)
        if (loadingDeadline?.let { it > 0L && now >= it } == true) {
            loadingDeadline = 0L
            state.setMedia(null, null)
        }
        selectLive()
        val selectedToast = state.toast(now)
        val notification = selectedToast?.notificationKey != null || state.activity(now) != null
        if (floatingWindowBlocked(notification = notification, allowMedia = selectedToast == null)) {
            cancelMediaReturn()
            publishMusicReturn()
            clearChat(); state.clearToast(); clearSystemActivity()
            mediaRevealAt = null
            hide(animate = state.allowed)
            return false
        }
        if (pipHomeBlocked(notification, media.takeIf { selectedToast == null })) {
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
        if (mediaReturn?.let { now >= it.expiresAt } == true || !state.allowed ||
            !options.mediaReturn || reducedMotion() || !ValueAnimator.areAnimatorsEnabled()) {
            cancelMediaReturn()
        }
        if (chatLease?.let { !state.owns(it, now) } == true) clearChat()
        if (systemPresentation?.let { state.owns(it.lease, now) } != true) systemPresentation = null
        scheduleSystemFeedback()
        if (mediaRevealAt?.let { now >= it } == true) mediaRevealAt = null
        val shadeIdle = !state.allowed && idleShadeAllowed()
        if (shadeBlocked && !state.allowed && !shadeIdle) {
            val deadline = listOfNotNull(pauseDeadline?.takeIf { it > now }, loadingDeadline?.takeIf { it > now && it < Long.MAX_VALUE }, mediaRevealAt?.takeIf { it > now },
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
        val toast = (state.toast(now) ?: state.activity(now)).takeIf { state.allowed }
        if (toast != null) {
            val sameActivity = toast.liveActivity?.key?.let {
                it == (lastContent as? IslandToastContent)?.liveActivity?.key
            } == true
            if (sameActivity) mediaReturn = null else cancelMediaReturn()
        }
        // Returning from a shade needs one current snapshot before the retained slot can
        // draw: it may still contain PLAYING although a real pause expired while hidden.
        val current = if (state.allowed && mediaRevealAt == null && !shadeBaseline && !foregroundBaseline) state.media(now)?.takeUnless {
            it.packageName == foregroundPackage } else null
        forms.select(if (toast != null || current != null) state.identity(now) else null,
            options.automaticWidth && IslandMotionCoordinator.prefersLong(toast))
        val idle = options.idle && !forms.idleSuppressed && (state.allowed || shadeIdle) && toast == null && current == null
        val geometry = if (!disposed && options.enabled && (toast != null || current != null || idle)) geometry(idle = idle) else null
        val liveExpiry = toast?.liveActivity?.clock?.takeIf { it.countdown && it.pausedMillis == null }
            ?.let { now + (it.base + 3000 - SystemClock.elapsedRealtime()).coerceAtLeast(1) }
        val nextExpiry = listOfNotNull(liveExpiry, state.deadline(), pauseDeadline?.takeIf { it > now }, loadingDeadline?.takeIf { it > now && it < Long.MAX_VALUE },
            mediaRevealAt?.takeIf { it > now }, mediaReturn?.expiresAt?.takeIf { it > now }).minOrNull()
        if (!disposed && nextExpiry != null) handler.postAtTime(expiry, nextExpiry)
        if (geometry == null) {
            hide(animate = !disposed && state.allowed && toast == null && current == null)
            return false
        }
        val resumingExit = exiting
        if (resumingExit) exitGeneration++
        exiting = false
        shell.simplified = reducedMotion()
        shell.allowTextBlur = !reducedMotion()
        shell.entranceStyle = options.entranceStyle
        shell.windowTop = geometry.top
        shell.idle = idle
        shell.idleTapAction = options.idleActions.tap
        shell.expandOnDown = options.expandOnDown
        shell.expanded = forms.expanded
        shell.shortWidth = IslandSizing.collapsed(context.resources.displayMetrics.density,
            screen?.width ?: geometry.width, geometry.maximumWidth, geometry.gap,
            dp(24).toFloat(), dp(24).toFloat(), shortDp = options.shortWidth, longDp = options.longWidth,
            manual = !options.automaticWidth, heightDp = kotlin.math.round(geometry.height / context.resources.displayMetrics.density).toInt(),
            centered = options.position == 1)
        val showingMedia = toast == null && current != null
        if (showingMedia != (lastContent is IslandMediaContent) ||
            showingMedia && (lastContent as? IslandMediaContent)?.token != current?.token ||
            !showingMedia && lastContent !== toast) invalidateActions()
        val motionIdentity = if (idle) idleIdentity else state.identity(now)
        val motionKind = when {
            idle -> IslandMotionCoordinator.Kind.IDLE
            showingMedia -> IslandMotionCoordinator.Kind.MEDIA
            toast?.liveActivity != null -> IslandMotionCoordinator.Kind.ACTIVITY
            else -> IslandMotionCoordinator.Kind.NOTICE
        }
        val secondary = attached && motionCoordinator.replacement(motionIdentity)
        val visualLeft = shell.pivotX * (1f - shell.scaleX) + shell.translationX
        val visualTop = shell.pivotY * (1f - shell.scaleY) + shell.translationY
        val contentChanged = shell.bind(toast?.text ?: current?.title.orEmpty(), if (showingMedia) current?.artist else toast?.detail,
            toast?.icon ?: current?.icon,
            showingMedia && current?.playing == true,
            showingMedia, options.waveform, if (showingMedia) current?.actions ?: 0L else 0L, showingMedia && options.mediaSwipe,
            current?.playbackState ?: PlaybackState.STATE_NONE, toast?.avatar == true, toast?.status == true,
            expanded = forms.expanded, loading = showingMedia && current?.loading == true,
            photo = if (showingMedia) current?.artwork == true else toast?.avatar == true,
            clock = toast?.liveActivity?.clock,
            recording = options.waveformMode == 2 && toast?.liveActivity?.let { it.kind == IslandLiveKind.RECORDER && it.meter != null } == true)
        shell.configureGestures(if (idle) idleIdentity else if (showingMedia) selected else state.identity(now),
            if (idle) options.idleActions.gestures else options.musicActions, idle || showingMedia && options.mediaSwipe)
        val cameraOffset = screen?.takeIf { options.position == 1 && it.holeWidth > 0 }?.let {
            (it.holeLeft + it.holeRight) * .5f - geometry.left - geometry.width * .5f } ?: 0f
        shell.geometry(geometry.width, geometry.height, geometry.gap, geometry.maximumWidth, options.position, cameraOffset)
        screen?.let { bounds ->
            shell.cameraAnchor(if (options.position != 1) geometry.height * .5f else if (bounds.holeCenter >= 0f) bounds.holeCenter - geometry.top else dp(20).toFloat(),
                if (options.position != 1) 0f else if (bounds.holeWidth > 0) bounds.holeWidth.toFloat() else dp(16).toFloat())
        }
        if (resumingExit) shell.resumeEntry()
        // One stable, transparent status-band canvas. Form changes happen together in
        // one RenderNode transaction instead of racing a Surface move/resize against the
        // shell's spring. Only the capsule's bounded input region accepts touches.
        touchWindow.inputEnabled = !shadeIdle
        touchWindow.geometry(geometry.width, geometry.height, geometry.left + touchWindow.margin)
        val canvasWidth = (screen?.width ?: geometry.width) + 2 * touchWindow.margin
        val canvasHeight = maxOf(dp(IslandSizing.HEIGHT_MAX_DP), geometry.height) + 2 * touchWindow.margin
        val windowGeometryChanged = params.width != canvasWidth || params.height != canvasHeight ||
            params.y != geometry.top - touchWindow.margin || params.x != 0
        params.width = canvasWidth
        params.height = canvasHeight
        params.y = geometry.top - touchWindow.margin
        params.x = 0
        return try {
            val entering = !attached
            val fromIdle = attached && !resumingExit && lastContent === idleIdentity
            val returning = (entering || resumingExit || fromIdle) && showingMedia && current?.playing == true &&
                current.packageName in options.mediaReturnApps && mediaReturn?.token == current.token
            val arrival = motionCoordinator.arrival(motionIdentity, motionKind, entering, resumingExit, returning)
            if (showingMedia) mediaReturn = null // One foreground edge can animate only once.
            if (returning) {
                if (fromIdle) shell.prepareReturnFromCurrent() else shell.prepareEntry(mediaReturn = true)
            } else if (arrival == IslandMotionCoordinator.Arrival.CONTENT) shell.prepareContentArrivalFromCurrent()
            if (entering) {
                // Prepare alpha/scale BEFORE the first Surface can be drawn, including re-entry
                // after a native activity or a notification-panel transition.
                if (!returning) shell.prepareEntry()
                window!!.addView(touchWindow, params); attached = true
                if (!touchWindow.inputRegionAvailable) { hide(); return false }
            }
            else if (windowGeometryChanged) window!!.updateViewLayout(touchWindow, params)
            if (arrival != IslandMotionCoordinator.Arrival.NONE || resumingExit || secondary || contentChanged || lastWidth != geometry.width ||
                lastHeight != geometry.height || lastLeft != geometry.left || lastTop != geometry.top) {
                shell.transition(lastWidth, lastHeight, geometry.width, geometry.height, entering, secondary,
                    if (entering || returning && !fromIdle) null else lastLeft - geometry.left + visualLeft,
                    if (entering || returning && !fromIdle) null else lastTop - geometry.top + visualTop)
            }
            lastWidth = geometry.width; lastHeight = geometry.height
            lastTop = geometry.top
            lastLeft = geometry.left
            lastContent = toast ?: current ?: idleIdentity
            motionCoordinator.commit(motionIdentity, motionKind)
            updateSpectrum()
            (if (idle) idleIdentity else state.identity(now))?.let { presentation.prepare(it, geometry.width) }
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
        recorderWave.stop()
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
        val recording = (lastContent as? IslandToastContent)?.liveActivity?.takeIf { it.kind == IslandLiveKind.RECORDER }
        if (visible && !disposed && !exiting && attached && state.allowed && options.waveformMode == 2 &&
            !reducedMotion() && ValueAnimator.areAnimatorsEnabled() && recording?.meter != null &&
            recording.clock?.pausedMillis == null && recording.packageName != foregroundPackage) {
            spectrum?.stop(); recorderWave.start(recording.meter)
            return
        }
        recorderWave.stop()
        val content = lastContent as? IslandMediaContent
        val player = selected
        if (!visible || disposed || exiting || !attached || !state.allowed || options.waveformMode != 2 ||
            reducedMotion() || !ValueAnimator.areAnimatorsEnabled() || content?.playing != true ||
            player == null || player.sessionToken != content.token) {
            if (visible && !disposed && !exiting && attached && state.allowed && content?.loading == true &&
                player?.sessionToken == content.token) spectrum?.pauseKeepingFrame() else spectrum?.stop()
            return
        }
        val source = spectrum ?: IslandAudioSpectrum(scope, ioDispatcher, handler,
            spectrumBackend ?: IslandAudioSpectrum.NativeBackend(context, handler), diagnostics, shell::spectrum).also { spectrum = it }
        source.start(player, foregroundUser)
    }

    private fun openPlayer() {
        val now = SystemClock.uptimeMillis()
        if (disposed || exiting || !attached || !state.allowed) return
        val toast = state.toast(now) ?: state.activity(now)
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
                    if (valid()) ModuleHelper.guarded {
                        when (toast.liveActivity?.kind) {
                            IslandLiveKind.CALL -> IslandLiveCalls.open(context)
                            IslandLiveKind.PRIVACY -> context.startActivity(Intent(android.provider.Settings.ACTION_PRIVACY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            else -> toast.launch?.send()
                        }
                    }
                    return@withLock
                }
                val player = controller ?: return@withLock
                val uid = IslandMediaIdentity.uid(player)
                if (uid == null || !IslandMediaIdentity.sameUser(uid, foregroundUser)) {
                    diagnostics.report("player-user-unavailable")
                    return@withLock
                }
                val result = launchVerifiedPlayer(player, uid, ::valid)
                if (result != IslandPlayerLaunch.Result.STARTED && result != IslandPlayerLaunch.Result.STALE)
                    diagnostics.report("player-${result.name.lowercase()}")
            }
        }
    }

    private fun launchVerifiedPlayer(player: MediaController, uid: Int, valid: () -> Boolean): IslandPlayerLaunch.Result {
        return IslandPlayerLaunch.open(valid,
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
    }

    private fun createSystemActivities(): IslandSystemActivities? = options.feedback.takeIf { it.isNotEmpty() }?.let {
        IslandSystemActivities(context, scope, ioDispatcher, it, ::systemActivity).also { source -> source.start() }
    }

    private fun reducedMotion() = options.simplified || powerSave

    private fun configureBatterySignals() {
        batterySignals?.close()
        batteryWarningMarker?.close(); batteryWarningMarker = null
        if (options.lowBattery) batteryWarningMarker = IslandBatteryWarningMarker(context.contentResolver, scope, ioDispatcher)
        batterySignals = IslandBatterySignals(context, scope, ioDispatcher, handler, options.charging, options.lowBattery,
            power = { enabled ->
                if (!disposed && powerSave != enabled) {
                    powerSave = enabled
                    if (enabled) cancelMediaReturn()
                    render()
                }
            }, event = { event ->
                if (!disposed && state.allowed && !floatingWindowBlocked(allowMedia = false) && options.enabled &&
                    (if (event.low) options.lowBattery else options.charging)) {
                    val res = ModuleHelper.getModuleRes(context)
                    val icon = context.getDrawable(if (event.low) android.R.drawable.ic_lock_idle_low_battery
                        else android.R.drawable.ic_lock_idle_charging)?.mutate()
                    val label = res.getString(if (event.low) tv.withaibuild.customiuizer.R.string.system_island_battery_low
                        else tv.withaibuild.customiuizer.R.string.system_island_battery_charging)
                    val content = IslandToastContent("${event.level}%", "com.android.systemui", icon, detail = label)
                    val lease = state.offer(content, SystemClock.uptimeMillis(), if (event.low) 2600 else 1800)
                    lowBatteryLease = lease.takeIf { event.low }
                    render()
                }
            })
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

    private fun executeIdleAction(suffix: String, action: Int) {
        if (action == 1 || !IslandMusicActions.supported(action) || disposed || exiting || !attached ||
            !state.allowed || lastContent !== idleIdentity || presentation.phase != IslandWindowPresentation.Phase.VISIBLE) return
        val recent = recentMedia
        val mediaAction = action == 85 || action in 87..88 || action in 18..19 ||
            action in IslandMusicActions.PLAY..IslandMusicActions.OPEN_PLAYER
        if (mediaAction && (!options.media || recent == null || recent.user != foregroundUser ||
                recent.packageName in options.excludedApps || sourceExcluded(recent.packageName, recent.user, recent.uid))) return
        val epoch = actionEpoch
        actionJob?.cancel()
        actionJob = scope.launch(ioDispatcher) {
            actionMutex.withLock { ModuleHelper.guarded {
                ensureActive()
                val keyguard = context.getSystemService(android.app.KeyguardManager::class.java)
                if (epoch != actionEpoch || keyguard?.isKeyguardLocked != false) return@guarded
                if (!mediaAction) {
                    GlobalActions.handleResolvedAction(context, "system_island_idle_$suffix", action)
                    return@guarded
                }
                val identity = recent ?: return@guarded
                val player = context.getSystemService(android.media.session.MediaSessionManager::class.java)
                    ?.getActiveSessions(null)?.firstOrNull { it.sessionToken == identity.token && it.packageName == identity.packageName }
                    ?: return@guarded
                if (IslandMediaIdentity.uid(player) != identity.uid || !IslandMediaIdentity.sameUser(identity.uid, identity.user)) return@guarded
                val playback = player.playbackState ?: return@guarded
                if (!IslandMediaPolicy.active(playback.state)) return@guarded
                ensureActive()
                if (epoch != actionEpoch) return@guarded
                when (action) {
                    IslandMusicActions.OPEN_PLAYER -> launchVerifiedPlayer(player, identity.uid) {
                        epoch == actionEpoch && !disposed && state.allowed && !exiting &&
                            lastContent === idleIdentity && recentMedia == identity && !keyguard.isKeyguardLocked
                    }
                    18, 19 -> {
                        val info = player.playbackInfo
                        val fixed = context.getSystemService(AudioManager::class.java)?.isVolumeFixed != false
                        if (IslandVolumePolicy.adjustable(info.playbackType, info.volumeControl, info.audioAttributes.usage, fixed))
                            player.adjustVolume(if (action == 18) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
                    }
                    else -> when (IslandTransportAction.resolve(action, playback.state, playback.actions)) {
                        PlaybackState.ACTION_PLAY -> player.transportControls.play()
                        PlaybackState.ACTION_PAUSE -> player.transportControls.pause()
                        PlaybackState.ACTION_SKIP_TO_NEXT -> player.transportControls.skipToNext()
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS -> player.transportControls.skipToPrevious()
                        else -> Unit
                    }
                }
            } }
        }
    }

    fun dispose() {
        liveSources?.close(); liveSources = null
        live.clear(preserveDismissal = true); liveContent = null
        recentMedia = null
        if (disposed) return
        disposed = true
        batterySignals?.close(); batterySignals = null
        batteryWarningMarker?.close(); batteryWarningMarker = null
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
        compactObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(compactLayout)
        compactObserver = null
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
        shell.onDismiss = {}; shell.onOpen = {}; shell.onGesture = {}; shell.onExpand = {}
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
