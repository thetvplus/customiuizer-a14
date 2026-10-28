package tv.withaibuild.customiuizer.mods.island

import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.app.PendingIntent
import android.service.notification.StatusBarNotification
import android.service.notification.NotificationListenerService.RankingMap
import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ReflectionCache
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.SystemPipActions
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/** Install the notification policy observer and its dependent heads-up takeover together. */
internal fun installIslandNotificationTakeover(
    privacyUpdated: Method?,
    onBindFinished: Method,
    privacyHook: MethodHook,
    bindHook: MethodHook,
): Boolean {
    val method = privacyUpdated ?: return false
    val observer = ModuleHelper.hookMethod(method, privacyHook) ?: return false
    var installed = false
    var failure: Throwable? = null
    try {
        installed = ModuleHelper.hookMethod(onBindFinished, bindHook) != null
        return installed
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        if (!installed) {
            try {
                ModuleHelper.guarded { observer.unhook() }
            } catch (cleanup: Throwable) {
                val original = failure
                if (original == null) throw cleanup
                if (cleanup !== original) original.addSuppressed(cleanup)
            }
        }
    }
}

internal data class IslandOptions(
    val enabled: Boolean, val toast: Boolean, val media: Boolean,
    val waveform: Boolean = true,
    val simplified: Boolean = false,
    val excludedApps: Set<String> = emptySet(),
    val chat: Boolean = false,
    val height: Int = 40,
    val automaticWidth: Boolean = true,
    val shortWidth: Int = 184,
    val longWidth: Int = 240,
    val entranceStyle: IslandEntranceStyle = IslandEntranceStyle.TOP,
    val chatExcludedApps: Set<String> = emptySet(),
    val mediaSwipe: Boolean = true,
    val musicActions: IslandMusicActions = IslandMusicActions(),
    val position: Int = 1,
    val horizontalOffset: Int = 0,
    val feedback: Set<IslandSystemKind> = emptySet(),
    val notificationApps: Set<String> = emptySet(),
    val waveformMode: Int = if (waveform) 1 else 0,
    val mediaReturn: Boolean = false,
    val mediaReturnApps: Set<String> = emptySet(),
    val hideInFloatingWindow: Boolean = false,
    val idle: Boolean = false,
    val idleWidth: Int = 0,
    val automaticHeight: Boolean = height == 40,
    val charging: Boolean = false,
    val lowBattery: Boolean = false,
    val idleActions: IslandIdleActions = IslandIdleActions(),
    val liveKinds: Set<IslandLiveKind> = emptySet(),
    val liveReturn: Boolean = true,
    val waveformGain: Int = 200,
    val absorption: Boolean = false,
) {
    private val mediaReturningPackages: Set<String> = if (enabled && media && mediaReturn)
        mediaReturnApps.filterTo(linkedSetOf()) { it !in excludedApps } else emptySet()
    private val returningPackages: Set<String> = buildSet {
        addAll(mediaReturningPackages)
        if (enabled && liveReturn) IslandLiveKind.windowPackages(liveKinds).filterTo(this) { it !in excludedApps }
    }
    fun returnPackages(): Set<String> = returningPackages
    fun returnsMedia(pkg: String): Boolean = pkg in mediaReturningPackages
    fun returnsLive(kind: IslandLiveKind, pkg: String): Boolean = enabled && liveReturn && kind in liveKinds &&
        IslandLiveKind.canReturn(kind, pkg) && pkg in returningPackages
    fun acceptsNotification(pkg: String, category: String?, channel: String? = null, template: String? = null): Boolean {
        return enabled && chat && pkg in notificationApps && pkg !in excludedApps && pkg !in chatExcludedApps
    }
    companion object {
        fun read(prefs: PrefMap): IslandOptions {
            val mode = StrongToastPresentationMode.fromPreference(prefs.getStringAsInt("system_strong_toast_mode", 0))
            val waveformMode = if (prefs.containsKey("system_island_waveform_mode"))
                prefs.getStringAsInt("system_island_waveform_mode", 1).let { if (it in 0..2) it else 1 }
                else if (prefs.getBoolean("system_island_waveform", true)) 1 else 0
            val shortWidth = prefs.getInt("system_island_width_short", 184).coerceIn(IslandSizing.SHORT_MIN_DP, IslandSizing.SHORT_MAX_DP)
            val legacyFeedback = mode == StrongToastPresentationMode.DYNAMIC_ISLAND &&
                IslandSystemKind.entries.none { prefs.containsKey(it.preferenceKey) }
            return IslandOptions(mode == StrongToastPresentationMode.DYNAMIC_ISLAND,
                prefs.getBoolean("system_toast_capsule"), prefs.getBoolean("system_island_media"),
                waveformMode != 0,
                prefs.getBoolean("system_island_simplified"),
                prefs.getStringSet("system_island_excluded_apps").toSet(),
                prefs.getBoolean("system_island_chat"),
                prefs.getInt("system_island_height", 40).coerceIn(IslandSizing.HEIGHT_MIN_DP, IslandSizing.HEIGHT_MAX_DP),
                prefs.getBoolean("system_island_width_auto", true),
                shortWidth,
                prefs.getInt("system_island_width_medium", 240).coerceIn(IslandSizing.LONG_MIN_DP, IslandSizing.LONG_MAX_MANUAL_DP).coerceAtLeast(shortWidth),
                IslandEntranceStyle.fromPreference(prefs.getStringAsInt("system_island_entrance_style", 1)),
                prefs.getStringSet("system_island_chat_excluded_apps").toSet(),
                prefs.getBoolean("system_island_media_swipe", true),
                IslandMusicActions.read(prefs),
                prefs.getStringAsInt("system_island_position", 1).coerceIn(0, 2),
                prefs.getInt("system_island_horizontal_offset", 80).coerceIn(0, 160) - 80,
                IslandSystemKind.entries.filterTo(linkedSetOf()) {
                    prefs.getBoolean(it.preferenceKey, legacyFeedback)
                },
                prefs.getStringSet("system_island_notification_apps").toSet(), waveformMode,
                prefs.getBoolean("system_island_media_return"),
                prefs.getStringSet("system_island_media_return_apps").toSet(),
                prefs.getBoolean("system_island_hide_in_floating_window"),
                prefs.getBoolean("system_island_idle"),
                if (prefs.getBoolean("system_island_idle_width_auto", true)) 0 else prefs.getInt("system_island_idle_width", 100).coerceIn(48, 120),
                prefs.getBoolean("system_island_height_auto", prefs.getInt("system_island_height", 40) == 40),
                prefs.getBoolean("system_island_feedback_charging"),
                prefs.getBoolean("system_island_feedback_low_battery"), IslandIdleActions.read(prefs), IslandLiveKind.read(prefs),
                prefs.getBoolean("system_island_live_return", true),
                prefs.getInt("system_island_waveform_gain", 200).coerceIn(100, 800),
                prefs.getStringAsInt("system_island_return_effect", 0) == 1)
        }
    }
}

internal data class IslandToastContent(val text: String, val packageName: String?, val icon: Drawable?,
    val launch: PendingIntent? = null, val notificationKey: String? = null,
    val detail: String? = null, val avatar: Boolean = false, val status: Boolean = false,
    val liveActivity: IslandLiveActivity? = null)

/** Only exact, whole, already-successful status messages may be shortened. Never guess from a substring. */
internal object IslandToastText {
    private val concise = mapOf("保存成功" to "已保存", "复制成功" to "已复制", "删除成功" to "已删除",
        "移动成功" to "已移动", "儲存成功" to "已儲存", "複製成功" to "已複製", "刪除成功" to "已刪除", "移動成功" to "已移動")
    fun resolve(text: CharSequence, shorten: Boolean): String? {
        if (text is android.text.Spanned && text.getSpans(0, text.length, Any::class.java).isNotEmpty()) return null
        if (text.isBlank() || text.length > 4096 || text.any { it == '\n' || it == '\r' || it.isISOControl() }) return null
        val original = text.toString()
        // A path makes a successful save arbitrarily long. Recognize only the complete
        // status prefix followed immediately by an absolute destination, never a substring.
        if (shorten) savedDestination(original)?.let { return it }
        if (text.length > 160) return null
        return if (shorten) concise[original] ?: original else original
    }

    private val destinations = listOf(
        "图片已保存至" to "图片已保存", "图片已保存到" to "图片已保存",
        "照片已保存至" to "照片已保存", "照片已保存到" to "照片已保存",
        "视频已保存至" to "视频已保存", "视频已保存到" to "视频已保存",
        "文件已保存至" to "文件已保存", "文件已保存到" to "文件已保存",
        "已保存至" to "已保存", "已保存到" to "已保存",
        "圖片已儲存至" to "圖片已儲存", "圖片已儲存到" to "圖片已儲存",
        "照片已儲存至" to "照片已儲存", "影片已儲存至" to "影片已儲存",
        "檔案已儲存至" to "檔案已儲存", "已儲存至" to "已儲存",
        "Image saved to" to "Image saved", "Photo saved to" to "Photo saved",
        "Video saved to" to "Video saved", "File saved to" to "File saved", "Saved to" to "Saved",
    )

    private fun savedDestination(text: String): String? {
        for ((prefix, summary) in destinations) {
            if (!text.startsWith(prefix)) continue
            val path = text.substring(prefix.length).trimStart(' ', ':', '：')
            // Android shared-storage locations only. The suffix remains opaque; none of
            // its filename words can turn a failure sentence into a successful activity.
            if (path.any { it in "，。；！？" } || path.contains(" but ", ignoreCase = true)) continue
            if (path.startsWith("/storage/") || path.startsWith("/sdcard/") || path.startsWith("file:///storage/"))
                return summary
        }
        return null
    }
}

/** Frozen ROM metadata. No static strong references to a View, media manager or controller. */
internal object DynamicIslandActivities {
    private const val OWNER = "customiuizer_island_activity_owner"
    private var access: IslandNativeAccess? = null
    private var prefs: PrefMap? = null
    private var active = WeakReference<IslandActivityController>(null)
    private var manager = WeakReference<Any>(null)
    private var safety = WeakReference<Any>(null)
    private val mediaDismissal = IslandActivityState.MediaDismissal()
    private val liveDismissal = IslandLiveRegistry.Dismissal()
    @Volatile private var foregroundPackage: String? = null
    private var pipObserver: IslandPipObserver? = null
    private var freeformAccess: IslandFreeformAccess? = null
    private var installedFloatingWindowPolicy = false

    fun install(param: PackageReadyParam, source: PrefMap) {
        if (access != null) return
        val options = IslandOptions.read(source)
        if (!options.enabled) return
        installedFloatingWindowPolicy = options.hideInFloatingWindow
        val resolved = try { IslandNativeAccess(param.classLoader) } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dynamic Island: native activity contract unavailable", error)
            return
        }
        access = resolved
        prefs = source
        val foreground = try { IslandForegroundAccess(param.classLoader) } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Dynamic Island: native foreground contract unavailable", error)
            null
        }
        if (foreground != null) {
            val capture = object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    if (callback.getThrowable() != null) return@guarded
                    val packageName = foreground.packageName(callback.getThisObject())
                    if (foregroundPackage != packageName) {
                        foregroundPackage = packageName
                        active.get()?.foregroundChanged(packageName)
                    }
                }
            }
            ModuleHelper.hookAllConstructors(foreground.observerClass, capture)
            for (method in foreground.updates) ModuleHelper.hookMethod(method, capture)
        }
        ModuleHelper.hookAllConstructors(resolved.safetyClass, object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val owner = callback.getThisObject() ?: return
                safety = WeakReference(owner)
                active.get()?.connectSafety(owner)
            }
        })
        if (options.media || options.hideInFloatingWindow) {
            ModuleHelper.guarded {
                val observer = IslandPipObserver(IslandPipAccess(param.classLoader)) { snapshot ->
                    active.get()?.pipChanged(snapshot)
                }
                if (observer.install()) {
                    pipObserver = observer
                    SystemPipActions.visibilityObserver = observer::visibilityChanged
                }
            }
        }
        if (options.hideInFloatingWindow) {
            ModuleHelper.guarded {
                val floating = IslandFreeformAccess(param.classLoader)
                val installed = ModuleHelper.hookMethod(floating.changed, object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        if (callback.getThrowable() == null) active.get()?.refreshFloatingWindows()
                    }
                })
                if (installed != null) freeformAccess = floating
            }
        }
        if (options.media) {
            ModuleHelper.hookAllConstructors(resolved.mediaClass, object : MethodHook() {
                override fun after(callback: AfterHookCallback) { captureMedia(callback.getThisObject(), replacing = true) }
            })
            for (method in resolved.mediaUpdates) ModuleHelper.hookMethod(method, object : MethodHook() {
                override fun after(callback: AfterHookCallback) { captureMedia(callback.getThisObject()) }
            })
        }
        if (IslandLiveKind.PRIVACY in options.liveKinds) ModuleHelper.guarded {
            ModuleHelper.findAndHookMethod("com.android.systemui.appops.AppOpsControllerImpl", param.classLoader,
                "notifySuscribersWorker", String::class.java, Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        if (callback.getThrowable() == null) active.get()?.refreshLivePrivacy()
                    }
                })
        }
        if (options.chat) {
            // Take over only the final, native-approved heads-up bind. Never block posting,
            // ranking or the notification-center entry. A missing contract stays native.
            ModuleHelper.guarded {
                val notifications = IslandNotificationAccess(param.classLoader)
                installIslandNotificationTakeover(
                    notifications.onPrivacyUpdated, notifications.onBindFinished,
                    object : MethodHook() {
                        override fun after(callback: AfterHookCallback) {
                            if (callback.getThrowable() == null) active.get()?.notificationPrivacyChanged()
                        }
                    }, object : MethodHook() {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val entry = chain.getArg(0) ?: return chain.proceed()
                            val controller = active.get() ?: return chain.proceed()
                            val accepted = try {
                                notifications.take(chain.thisObject!!, entry, controller::takeNotification, controller::cancelNotificationTakeover)
                            } catch (error: Throwable) {
                                FatalErrors.unwrapAndRethrowIfFatal(error)
                                XposedHelpers.log("Dynamic Island: retaining native heads-up", error)
                                false
                            }
                            return if (accepted) null else chain.proceed()
                        }
                    },
                )
            }
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.collection.NotifCollection\$1",
                param.classLoader, "onNotificationPosted", StatusBarNotification::class.java, RankingMap::class.java,
                object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        if (callback.getThrowable() == null) active.get()?.notificationPosted(
                            callback.getArg(0) as StatusBarNotification)
                    }
                })
            ModuleHelper.hookAllMethods(XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.collection.NotifCollection\$1", param.classLoader),
                "onNotificationRemoved", object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        (callback.getArg(0) as? StatusBarNotification)?.let { active.get()?.notificationRemoved(it.key) }
                    }
                })
        }
        // Causal command markers are leased by the attached owner for selected categories.
    }

    private fun captureMedia(owner: Any?, replacing: Boolean = false) {
        if (owner == null) return
        if (!replacing && manager.get()?.let { it !== owner } == true) return
        manager = WeakReference(owner)
        active.get()?.updateFromNative(owner)
    }

    fun bind(statusBar: View) {
        val resolved = access ?: return
        val source = prefs ?: return
        if (!statusBar.isAttachedToWindow) return
        if (XposedHelpers.getAdditionalInstanceField(statusBar, OWNER) != null) return
        val options = IslandOptions.read(source).copy(hideInFloatingWindow = installedFloatingWindowPolicy)
        if (!options.enabled || statusBar.display?.displayId != 0) return
        active.get()?.dispose()
        val controller = IslandActivityController(statusBar.context, source, options, resolved,
            dismissal = mediaDismissal, markerLoader = resolved.mediaClass.classLoader,
            liveDismissal = liveDismissal,
            freeform = freeformAccess, pipStateReader = { pipObserver?.snapshot }, geometryHost = statusBar.rootView,
            trackApplicationOverlays = true,
            pipCandidate = if (options.media || options.hideInFloatingWindow) IslandPipCandidateAccess(statusBar.context) else null)
        XposedHelpers.setAdditionalInstanceField(statusBar, OWNER, controller)
        active = WeakReference(controller)
        pipObserver?.snapshot?.let(controller::pipChanged)
        controller.foregroundChanged(foregroundPackage)
        statusBar.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) = ModuleHelper.guarded {
                controller.dispose()
                XposedHelpers.removeAdditionalInstanceField(v, OWNER)
                v.removeOnAttachStateChangeListener(this)
            }
        })
        safety.get()?.let { controller.connectSafety(it) }
        manager.get()?.let { controller.updateFromNative(it) }
        controller.refreshFloatingWindows()
    }

    fun showToast(content: IslandToastContent, duration: Long, displayId: Int): IslandActivityState.Lease? =
        if (displayId == android.view.Display.DEFAULT_DISPLAY) active.get()?.showToast(content, duration) else null

    fun releaseToast(lease: IslandActivityState.Lease) { active.get()?.releaseToast(lease) }

}

internal interface IslandNativePort {
    fun controller(owner: Any): MediaController?
    fun metadata(owner: Any): MediaMetadata?
    fun isAllowed(owner: Any): Boolean
    /** Only an unlocked shade may use a short visual exit; privacy blocks stay immediate. */
    fun isShadeBlocked(owner: Any): Boolean = false
    fun trust(params: android.view.WindowManager.LayoutParams)
    fun listen(owner: Any, changed: () -> Unit): Any
    fun unlisten(owner: Any, listener: Any)
}

internal class IslandNativeAccess(loader: ClassLoader) : IslandNativePort {
    val mediaClass = XposedHelpers.findClass("com.android.systemui.statusbar.NotificationMediaManager", loader)
    val safetyClass = XposedHelpers.findClass("com.android.systemui.statusbar.StatusBarStateControllerImpl", loader)
    private val stateListener = XposedHelpers.findClass("com.android.systemui.plugins.statusbar.StatusBarStateController\$StateListener", loader)
    val mediaUpdates = arrayOf(mediaClass.getDeclaredMethod("findAndUpdateMediaNotifications"),
        mediaClass.getDeclaredMethod("dispatchUpdateMediaMetaData", Boolean::class.javaPrimitiveType)).onEach { it.isAccessible = true }
    private val controllerField = field(mediaClass, "mMediaController")
    private val metadataField = field(mediaClass, "mMediaMetadata")
    private val stateField = field(safetyClass, "mState")
    private val dozingField = field(safetyClass, "mIsDozing")
    private val expandedField = field(safetyClass, "mIsExpanded")
    private val dreamingField = field(safetyClass, "mIsDreaming")
    private val requestedField = field(safetyClass, "mKeyguardRequested")
    private val upcomingField = field(safetyClass, "mUpcomingState")
    private val keyguard = IslandKeyguardAccess(loader)
    private var keyguardOwner = WeakReference<Any>(null)
    private data class SafetyListeners(val statusBar: Any, val keyguardOwner: Any?, val keyguardListener: Any?)
    private val addListener = safetyClass.getMethod("addCallback", stateListener)
    private val removeListener = safetyClass.getMethod("removeCallback", stateListener)
    private val trustedOverlay = android.view.WindowManager.LayoutParams::class.java.getMethod("setTrustedOverlay")

    override fun controller(owner: Any) = controllerField.get(owner) as? MediaController
    override fun metadata(owner: Any) = metadataField.get(owner) as? MediaMetadata
    private fun unlocked(owner: Any): Boolean = stateField.getInt(owner) == 0 && upcomingField.getInt(owner) == 0 &&
        !requestedField.getBoolean(owner) && !dozingField.getBoolean(owner) && !dreamingField.getBoolean(owner) &&
        keyguard.isUnlocked(keyguardOwner.get())
    override fun isAllowed(owner: Any): Boolean = unlocked(owner) && !expandedField.getBoolean(owner)
    // Retain shade playback only across an unlocked panel drag.
    override fun isShadeBlocked(owner: Any): Boolean = unlocked(owner) && expandedField.getBoolean(owner)
    override fun trust(params: android.view.WindowManager.LayoutParams) { trustedOverlay.invoke(params) }

    override fun listen(owner: Any, changed: () -> Unit): Any {
        val nativeKeyguard = ReflectionCache.getDepInstance(safetyClass.classLoader,
            "com.android.systemui.statusbar.policy.KeyguardStateController")
        val listener = Proxy.newProxyInstance(stateListener.classLoader, arrayOf(stateListener)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> java.lang.System.identityHashCode(proxy)
                "toString" -> "A14IslandStateListener"
                "onStateChanged", "onUpcomingStateChanged", "onDozingChanged", "onExpandedChanged", "onDreamingChanged" -> {
                    ModuleHelper.guarded { changed() }; null
                }
                else -> null
            }
        }
        val keyguardListener = nativeKeyguard?.let { keyguard.listen(it, changed) }
        try { addListener.invoke(owner, listener) }
        catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            ModuleHelper.guarded { removeListener.invoke(owner, listener) }
            if (nativeKeyguard != null && keyguardListener != null)
                ModuleHelper.guarded { keyguard.unlisten(nativeKeyguard, keyguardListener) }
            keyguardOwner.clear()
            throw error
        }
        keyguardOwner = WeakReference(nativeKeyguard)
        return SafetyListeners(listener, nativeKeyguard, keyguardListener)
    }
    override fun unlisten(owner: Any, listener: Any) {
        val listeners = listener as SafetyListeners
        try { removeListener.invoke(owner, listeners.statusBar) }
        finally {
            if (keyguardOwner.get() === listeners.keyguardOwner) keyguardOwner.clear()
            if (listeners.keyguardOwner != null && listeners.keyguardListener != null)
                keyguard.unlisten(listeners.keyguardOwner, listeners.keyguardListener)
        }
    }
    private fun field(owner: Class<*>, name: String): Field = XposedHelpers.findField(owner, name)
}
