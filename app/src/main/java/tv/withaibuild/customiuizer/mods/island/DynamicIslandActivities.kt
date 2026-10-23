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
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Proxy

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
) {
    companion object {
        fun read(prefs: PrefMap): IslandOptions {
            val mode = StrongToastPresentationMode.fromPreference(prefs.getStringAsInt("system_strong_toast_mode", 0))
            val shortWidth = prefs.getInt("system_island_width_short", 184).coerceIn(160, 224)
            return IslandOptions(mode == StrongToastPresentationMode.DYNAMIC_ISLAND,
                prefs.getBoolean("system_toast_capsule"), prefs.getBoolean("system_island_media"),
                prefs.getBoolean("system_island_waveform", true),
                prefs.getBoolean("system_island_simplified"),
                prefs.getStringSet("system_island_excluded_apps"),
                prefs.getBoolean("system_island_chat"),
                prefs.getInt("system_island_height", 40).coerceIn(38, 44),
                prefs.getBoolean("system_island_width_auto", true),
                shortWidth,
                prefs.getInt("system_island_width_medium", 240).coerceIn(184, 280).coerceAtLeast(shortWidth),
                IslandEntranceStyle.fromPreference(prefs.getStringAsInt("system_island_entrance_style", 1)),
                prefs.getStringSet("system_island_chat_excluded_apps"),
                prefs.getBoolean("system_island_media_swipe", true))
        }
    }
}

internal data class IslandToastContent(val text: String, val packageName: String?, val icon: Drawable?,
    val launch: PendingIntent? = null, val notificationKey: String? = null,
    val detail: String? = null, val avatar: Boolean = false, val status: Boolean = false)

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
    @Volatile private var foregroundPackage: String? = null

    fun install(param: PackageReadyParam, source: PrefMap) {
        if (access != null) return
        val options = IslandOptions.read(source)
        if (!options.enabled) return
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
        if (options.media) {
            ModuleHelper.hookAllConstructors(resolved.mediaClass, object : MethodHook() {
                override fun after(callback: AfterHookCallback) { captureMedia(callback.getThisObject(), replacing = true) }
            })
            for (method in resolved.mediaUpdates) ModuleHelper.hookMethod(method, object : MethodHook() {
                override fun after(callback: AfterHookCallback) { captureMedia(callback.getThisObject()) }
            })
        }
        if (options.chat) {
            // Take over only the final, native-approved heads-up bind. Never block posting,
            // ranking or the notification-center entry. A missing contract stays native.
            ModuleHelper.guarded {
                val notifications = IslandNotificationAccess(param.classLoader)
                ModuleHelper.hookMethod(notifications.onBindFinished, object : MethodHook() {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val entry = chain.getArg(0) ?: return chain.proceed()
                        var acceptedKey: String? = null
                        val accepted = try {
                            val bound = notifications.read(entry)
                            val map = notifications.bindingMap(chain.thisObject!!)
                            if (bound == null || active.get()?.takeNotification(bound) != true) false
                            else {
                                acceptedKey = bound.notification.key
                                notifications.complete(chain.thisObject!!, entry, map, bound.notification.key)
                                true
                            }
                        } catch (error: Throwable) {
                            FatalErrors.unwrapAndRethrowIfFatal(error)
                            acceptedKey?.let { active.get()?.notificationRemoved(it) }
                            XposedHelpers.log("Dynamic Island: retaining native heads-up", error)
                            false
                        }
                        return if (accepted) null else chain.proceed()
                    }
                })
            }
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.collection.NotifCollection\$1",
                param.classLoader, "onNotificationPosted", StatusBarNotification::class.java, RankingMap::class.java,
                object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        if (callback.getThrowable() == null) active.get()?.notificationPosted(
                            callback.getArgs()[0] as StatusBarNotification)
                    }
                })
            ModuleHelper.hookAllMethods(XposedHelpers.findClass(
                "com.android.systemui.statusbar.notification.collection.NotifCollection\$1", param.classLoader),
                "onNotificationRemoved", object : MethodHook() {
                    override fun after(callback: AfterHookCallback) {
                        (callback.getArgs().firstOrNull() as? StatusBarNotification)?.let { active.get()?.notificationRemoved(it.key) }
                    }
                })
        }
        // A local AudioManager command is an explicit operation; system_server's DND
        // consequences arrive only as state broadcasts. Do not swallow the former.
        for (name in arrayOf("setRingerMode", "setRingerModeInternal")) ModuleHelper.guarded {
            ModuleHelper.findAndHookMethod(android.media.AudioManager::class.java, name,
                Int::class.javaPrimitiveType, object : MethodHook() {
                    override fun before(callback: BeforeHookCallback) {
                        active.get()?.independentSystemAction(IslandSystemKind.RINGER)
                    }
                })
        }
        // Native tiles mark explicit radio changes too; an intentional OFF immediately
        // after airplane mode must not be mistaken for the airplane broadcast cascade.
        for ((tile, kind) in arrayOf("MuteTile" to IslandSystemKind.RINGER,
            "MiuiWifiTile" to IslandSystemKind.WIFI, "BluetoothTile" to IslandSystemKind.BLUETOOTH,
            "MiuiCellularTile" to IslandSystemKind.DATA)) ModuleHelper.guarded {
            ModuleHelper.findAndHookMethod("com.android.systemui.qs.tiles.$tile", param.classLoader,
                "handleClick", View::class.java, object : MethodHook() {
                    override fun before(callback: BeforeHookCallback) { active.get()?.independentSystemAction(kind) }
                })
        }
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
        val options = IslandOptions.read(source)
        if (!options.enabled || statusBar.display?.displayId != 0) return
        active.get()?.dispose()
        val controller = IslandActivityController(statusBar.context, source, options, resolved,
            dismissal = mediaDismissal)
        XposedHelpers.setAdditionalInstanceField(statusBar, OWNER, controller)
        active = WeakReference(controller)
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
    private val addListener = safetyClass.getMethod("addCallback", stateListener)
    private val removeListener = safetyClass.getMethod("removeCallback", stateListener)
    private val trustedOverlay = android.view.WindowManager.LayoutParams::class.java.getMethod("setTrustedOverlay")

    override fun controller(owner: Any) = controllerField.get(owner) as? MediaController
    override fun metadata(owner: Any) = metadataField.get(owner) as? MediaMetadata
    override fun isAllowed(owner: Any): Boolean = stateField.getInt(owner) == 0 &&
        !dozingField.getBoolean(owner) && !expandedField.getBoolean(owner)
    // Matches this ROM's MediaHierarchyManager.isHomeScreenShadeVisibleToUser().
    override fun isShadeBlocked(owner: Any): Boolean = stateField.getInt(owner) == 0 &&
        !dozingField.getBoolean(owner) && expandedField.getBoolean(owner)
    override fun trust(params: android.view.WindowManager.LayoutParams) { trustedOverlay.invoke(params) }

    override fun listen(owner: Any, changed: () -> Unit): Any {
        val listener = Proxy.newProxyInstance(stateListener.classLoader, arrayOf(stateListener)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> java.lang.System.identityHashCode(proxy)
                "toString" -> "A14IslandStateListener"
                "onStateChanged", "onDozingChanged", "onExpandedChanged", "onDreamingChanged" -> {
                    ModuleHelper.guarded { changed() }; null
                }
                else -> null
            }
        }
        addListener.invoke(owner, listener)
        return listener
    }
    override fun unlisten(owner: Any, listener: Any) { removeListener.invoke(owner, listener) }
    private fun field(owner: Class<*>, name: String): Field = XposedHelpers.findField(owner, name)
}
