package tv.withaibuild.customiuizer.mods

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.VibrationAttributes
import java.lang.reflect.Method
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import java.util.concurrent.ConcurrentHashMap
import tv.withaibuild.customiuizer.mods.utils.HookInstallStateMachine
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.AudioVisualizer
import java.util.ArrayList
import java.util.Calendar
import java.util.Stack
import tv.withaibuild.customiuizer.utils.HookUtils
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/**
 * Audio, media and haptics hooks.
 * The lock screen audio visualiser and its media-session plumbing, call
 * interruption, ducking, first volume press, and per-app vibration strength.
 */
object SystemAudioHooks {

    internal data class AudioHapticsSnapshot(
        val qsHapticsIgnore: Boolean = false,
        val qsHaptics: Int = 1,
        val vibrationMode: Int = 1,
        val vibrationApps: Set<String> = emptySet(),
        val ignoreCallsApps: Set<String> = emptySet(),
        val ampRinger: Float = 1f,
        val ampNotif: Float = 1f,
        val ampOther: Float = 1f,
        val periodStartMinutes: Int = 0,
        val periodEndMinutes: Int = 0,
    )

    @Volatile
    internal var audioHapticsConfig = AudioHapticsSnapshot()

    private var audioHapticsObserverRegistered = false

    private val AUDIO_HAPTICS_KEYS = setOf(
        "system_qshaptics_ignore",
        "system_qshaptics",
        "system_vibration",
        "system_vibration_apps",
        "system_ignorecalls_apps",
        "system_vibration_amp_ringer",
        "system_vibration_amp_notif",
        "system_vibration_amp_other",
        "system_vibration_amp_period_start_hour",
        "system_vibration_amp_period_start_minute",
        "system_vibration_amp_period_end_hour",
        "system_vibration_amp_period_end_minute",
    )

    internal fun refreshAudioHapticsSnapshot() {
        val prefs = MainModule.mPrefs
        audioHapticsConfig = AudioHapticsSnapshot(
            qsHapticsIgnore = prefs.getBoolean("system_qshaptics_ignore"),
            qsHaptics = prefs.getStringAsInt("system_qshaptics", 1),
            vibrationMode = prefs.getStringAsInt("system_vibration", 1),
            vibrationApps = HashSet(prefs.getStringSet("system_vibration_apps")),
            ignoreCallsApps = HashSet(prefs.getStringSet("system_ignorecalls_apps")),
            ampRinger = prefs.getInt("system_vibration_amp_ringer", 100) / 100f,
            ampNotif = prefs.getInt("system_vibration_amp_notif", 100) / 100f,
            ampOther = prefs.getInt("system_vibration_amp_other", 100) / 100f,
            periodStartMinutes = prefs.getInt("system_vibration_amp_period_start_hour", 0) * 60 +
                prefs.getInt("system_vibration_amp_period_start_minute", 0),
            periodEndMinutes = prefs.getInt("system_vibration_amp_period_end_hour", 0) * 60 +
                prefs.getInt("system_vibration_amp_period_end_minute", 0),
        )
    }

    @JvmStatic
    internal fun installAudioHapticsSnapshot() {
        refreshAudioHapticsSnapshot()
        if (audioHapticsObserverRegistered) return
        audioHapticsObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in AUDIO_HAPTICS_KEYS) refreshAudioHapticsSnapshot()
            }
        })
    }

    @JvmStatic
    fun QSHapticHook(lpparam: PackageReadyParam) {
        installAudioHapticsSnapshot()
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mState = XposedHelpers.callMethod(thisObject, "getState")
                    val state = XposedHelpers.getIntField(mState, "state")
                    if (state != 0) {
                        val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                        val ignoreSystem = audioHapticsConfig.qsHapticsIgnore
                        val opt = audioHapticsConfig.qsHaptics
                        if (opt == 2)
                            HookUtils.performLightVibration(mContext, ignoreSystem)
                        else if (opt == 3)
                            HookUtils.performStrongVibration(mContext, ignoreSystem)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    private fun checkVibration(pkgName: String): Boolean {
        val cfg = audioHapticsConfig
        val isSelected = pkgName in cfg.vibrationApps
        return (cfg.vibrationMode == 2 && !isSelected) || (cfg.vibrationMode == 3 && isSelected)
    }

    @JvmStatic
    fun SelectiveVibrationHook(lpparam: SystemServerStartingParam) {
        installAudioHapticsSnapshot()
        ModuleHelper.findAndHookMethod("com.android.server.vibrator.VibratorManagerService", lpparam.classLoader,
            "vibrate", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java,
            "android.os.CombinedVibration", "android.os.VibrationAttributes", String::class.java,
            "android.os.IBinder", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return interceptSelectiveVibration(chain)
            }
        })
    }

    internal fun interceptSelectiveVibration(chain: XposedInterface.Chain): Any? {
        val blocked = ModuleHelper.guarded(false) {
            // A14: uid, deviceId, opPkg, effect, attributes, reason, token.
            // Do not replace the native device, vibration attributes or original exception.
            val packageName = chain.getArg(2) as? String ?: return@guarded false
            checkVibration(packageName)
        }
        return if (blocked) null else chain.proceed()
    }

    @JvmStatic
    fun NoDuckingHook(lpparam: SystemServerStartingParam) {
        val requesterClass = XposedHelpers.findClass("com.android.server.audio.FocusRequester", lpparam.classLoader)
        ModuleHelper.findAndHookMethod(
            requesterClass, "frameworkHandleFocusLoss", Int::class.javaPrimitiveType!!,
            requesterClass, Boolean::class.javaPrimitiveType!!,
            object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? = interceptDucking(chain)
            }
        )
    }

    internal fun interceptDucking(chain: XposedInterface.Chain): Any? {
        // handleFocusLoss must still update loss/notified state and inform the focus policy.
        val handled = ModuleHelper.guarded(false) {
            chain.getArg(0) == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK &&
                chain.getArg(1) != null
        }
        return if (handled) true else chain.proceed()
    }

    private var audioViz: AudioVisualizer? = null

    private const val AUDIO_VISUALIZER_TAG = "customiuizer_audio_visualizer"

    private var isKeyguardShowing = false

    private var isNotificationPanelExpanded = false

    private var mMediaController: MediaController? = null

    private fun updateAudioVisualizerState(context: Context?) {
        if (audioViz == null || context == null) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager?
        val isMusicPlaying = am != null && am.isMusicActive
        var isPlaying = false
        if (mMediaController == null || mMediaController?.playbackState == null || mMediaController?.playbackState?.state != PlaybackState.STATE_PLAYING) {
            if (audioViz?.showWithControllerOnly == false) isPlaying = isMusicPlaying
        } else {
            isPlaying = isMusicPlaying && mMediaController?.playbackState?.state == PlaybackState.STATE_PLAYING
        }
        audioViz?.updateViewState(isPlaying, isKeyguardShowing, isNotificationPanelExpanded)
    }

    @JvmStatic
    fun AudioVisualizerHook(lpparam: PackageReadyParam) {
        val screenOffHooks = ConcurrentHashMap<Class<*>, HookInstallStateMachine>()
        val screenOnHooks = ConcurrentHashMap<Class<*>, HookInstallStateMachine>()

        val screenAndDoze = booleanArrayOf(false, false)
        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "onViewAttachedToWindow", View::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                var createdVisualizer: AudioVisualizer? = null
                try {
                    val thisObject = chain.thisObject

                    val mNotificationPanel = XposedHelpers.getObjectField(thisObject, "panelView") as FrameLayout?
                    if (mNotificationPanel == null) {
                        XposedHelpers.log("AudioVisualizerHook", "Cannot find mNotificationPanel")
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }

                    val mContext = mNotificationPanel.context
                    val existingVisualizer =
                        mNotificationPanel.findViewWithTag<AudioVisualizer>(AUDIO_VISUALIZER_TAG)
                    if (existingVisualizer != null) {
                        if (!existingVisualizer.isDisposed) {
                            audioViz = existingVisualizer
                            return XposedHelpers.throwOrReturn(throwable, result)
                        }
                        val oldParent = existingVisualizer.parent as? ViewGroup
                        if (oldParent?.parent === mNotificationPanel) {
                            mNotificationPanel.removeView(oldParent)
                        } else {
                            oldParent?.removeView(existingVisualizer)
                        }
                    }
                    val visFrame = FrameLayout(mContext)
                    visFrame.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    val audioVizLocal = AudioVisualizer(mContext)
                    createdVisualizer = audioVizLocal
                    audioVizLocal.tag = AUDIO_VISUALIZER_TAG
                    audioVizLocal.onDisposed = { disposed ->
                        if (audioViz === disposed) audioViz = null
                    }
                    audioVizLocal.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM)
                    audioVizLocal.isClickable = false
                    visFrame.addView(audioVizLocal)
                    visFrame.isClickable = false
                    val themebkg = mNotificationPanel.findViewById<View>(HookUtils.getResId(mContext.resources, "keyguard_background_layer", "id", lpparam.packageName))

                    var order = 0
                    if (themebkg != null) order = Math.max(order, mNotificationPanel.indexOfChild(themebkg))
                    mNotificationPanel.addView(visFrame, order + 1)
                    audioViz = audioVizLocal
                    createdVisualizer = null

                } catch (t: Throwable) {
                    createdVisualizer?.dispose()
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.CentralSurfacesImpl", lpparam.classLoader, "start", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mScreenObserver = XposedHelpers.getObjectField(thisObject, "mScreenObserver")
                    val ScreenObserverCls = mScreenObserver.javaClass
                    screenOffHooks.getOrPut(ScreenObserverCls) { HookInstallStateMachine() }.install {
                        ModuleHelper.findAndHookMethod(ScreenObserverCls, "onScreenTurnedOff", object : MethodHook() {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            var result: Any?
                            var throwable: Throwable? = null
                            try {
                                result = chain.proceed()
                            } catch (t: Throwable) {
                                throwable = t
                                result = null
                            }
                            try {

                                screenAndDoze[0] = false
                                if (audioViz != null) audioViz?.updateScreenOn(false)

                            } catch (t: Throwable) {
                                XposedHelpers.log(t)
                            }
                            return XposedHelpers.throwOrReturn(throwable, result)
                        }
                    }) != null
                    }

                    screenOnHooks.getOrPut(ScreenObserverCls) { HookInstallStateMachine() }.install {
                        ModuleHelper.findAndHookMethod(ScreenObserverCls, "onScreenTurnedOn", object : MethodHook() {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            var result: Any?
                            var throwable: Throwable? = null
                            try {
                                result = chain.proceed()
                            } catch (t: Throwable) {
                                throwable = t
                                result = null
                            }
                            try {

                                screenAndDoze[0] = true
                                if (audioViz != null) audioViz?.updateScreenOn(!screenAndDoze[1])

                            } catch (t: Throwable) {
                                XposedHelpers.log(t)
                            }
                            return XposedHelpers.throwOrReturn(throwable, result)
                        }
                    }) != null
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.CentralSurfacesImpl", lpparam.classLoader, "updateDozingState", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mDozing = XposedHelpers.getBooleanField(thisObject, "mDozing")
                    screenAndDoze[1] = mDozing
                    if (audioViz != null) audioViz?.updateScreenOn(!mDozing && screenAndDoze[0])

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", lpparam.classLoader, "notifyKeyguardState", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val isKeyguardShowingNew = chain.getArg(0) as Boolean
                    if (isKeyguardShowing != isKeyguardShowingNew) {
                        isKeyguardShowing = isKeyguardShowingNew
                        isNotificationPanelExpanded = false
                        updateAudioVisualizerState(XposedHelpers.getObjectField(thisObject, "mContext") as Context)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "updatePanelExpanded", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val isNotificationPanelExpandedNew = XposedHelpers.getBooleanField(thisObject, "mPanelExpanded")
                    if (isNotificationPanelExpanded != isNotificationPanelExpandedNew) {
                        isNotificationPanelExpanded = isNotificationPanelExpandedNew
                        val mNotificationPanel = XposedHelpers.getObjectField(thisObject, "panelView") as FrameLayout
                        updateAudioVisualizerState(mNotificationPanel.context)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.NotificationMediaManager", lpparam.classLoader, "updateMediaMetaData", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    if (audioViz == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    if (!screenAndDoze[0] || screenAndDoze[1]) {
                        audioViz?.updateScreenOn(false)
                        return XposedHelpers.proceedOrThrow(chain, throwable)
                    } else audioViz?.isScreenOn = true

                    val mMediaMetadata = XposedHelpers.getObjectField(thisObject, "mMediaMetadata") as MediaMetadata?
                    var art: Bitmap? = null
                    if (mMediaMetadata != null) {
                        art = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        if (art == null) art = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        if (art == null) art = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    }
                    if (art == null) {
                        val wallpaperMgr = WallpaperManager.getInstance(mContext)
                        @SuppressLint("MissingPermission")
                        val wallpaperDrawable = wallpaperMgr.drawable
                        if (wallpaperDrawable is BitmapDrawable) {
                            art = wallpaperDrawable.bitmap
                        }
                    }

                    mMediaController = XposedHelpers.getObjectField(thisObject, "mMediaController") as MediaController?
                    updateAudioVisualizerState(mContext)
                    audioViz?.updateMusicArt(art)

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    private const val PHONE_FOCUS_CLIENT = "AudioFocus_For_Phone_Ring_And_Calls"

    /** Field handles belong to this installation's ClassLoader; no service/owner is retained. */
    internal class CallAudioFocusAccess(serviceClass: Class<*>, controlClass: Class<*>, requesterClass: Class<*>) {
        private val controlField = XposedHelpers.findField(serviceClass, "mMediaFocusControl")
        private val stackField = XposedHelpers.findField(controlClass, "mFocusStack")
        private val policyField = XposedHelpers.findField(controlClass, "mFocusPolicy")
        private val multiFocusField = XposedHelpers.findField(controlClass, "mMultiAudioFocusEnabled")
        private val callActiveField = XposedHelpers.findField(controlClass, "mRingOrCallActive")
        private val packageField = XposedHelpers.findField(requesterClass, "mPackageName")
        private val clientField = XposedHelpers.findField(requesterClass, "mClientId")
        private val lossField = XposedHelpers.findField(requesterClass, "mFocusLossReceived")
        private val focusLockField = XposedHelpers.findField(controlClass, "mAudioFocusLock")

        fun shouldIgnoreCall(service: Any?, selectedApps: Set<String>): Boolean {
            if (service == null || selectedApps.isEmpty()) return false
            val control = controlField.get(service) ?: return false
            val focusLock = focusLockField.get(null) ?: return false
            synchronized(focusLock) {
                // A single stack owner cannot describe an external policy or multiple owners.
                if (policyField.get(control) != null || multiFocusField.getBoolean(control) ||
                    callActiveField.getBoolean(control)
                ) return false
                val stack = stackField.get(control) as? Stack<*> ?: return false
                val owner = if (stack.empty()) null else stack.peek()
                if (owner == null || lossField.getInt(owner) != 0 ||
                    clientField.get(owner) == PHONE_FOCUS_CLIENT
                ) return false
                val packageName = packageField.get(owner) as? String ?: return false
                return packageName in selectedApps
            }
        }
    }

    internal fun interceptCallAudioFocus(chain: XposedInterface.Chain, access: CallAudioFocusAccess): Any? {
        val ignored = ModuleHelper.guarded(false) {
            chain.getArg(4) == PHONE_FOCUS_CLIENT &&
                access.shouldIgnoreCall(chain.thisObject, audioHapticsConfig.ignoreCallsApps)
        }
        return if (ignored) AudioManager.AUDIOFOCUS_REQUEST_GRANTED else chain.proceed()
    }

    internal fun filterCallStateEvents(callingPackage: String?, events: Set<Int>): Set<Int> {
        // A14 TelephonyRegistry uses callback event IDs, not PhoneStateListener bit masks.
        // Both fuxi ROMs dispatch call state as 6 (modern) and 36 (legacy).
        if (callingPackage !in audioHapticsConfig.ignoreCallsApps ||
            (6 !in events && 36 !in events)
        ) return events
        return HashSet(events).apply {
            remove(6)
            remove(36)
        }
    }

    @JvmStatic
    fun NoCallInterruptionHook(lpparam: SystemServerStartingParam) {
        installAudioHapticsSnapshot()
        // Audio-focus adaptation and telephony registration are independent failure domains.
        ModuleHelper.guarded {
            val serviceClass = XposedHelpers.findClass("com.android.server.audio.AudioService", lpparam.classLoader)
            val access = CallAudioFocusAccess(
                serviceClass,
                XposedHelpers.findClass("com.android.server.audio.MediaFocusControl", lpparam.classLoader),
                XposedHelpers.findClass("com.android.server.audio.FocusRequester", lpparam.classLoader),
            )
            ModuleHelper.findAndHookMethod(
                serviceClass, "requestAudioFocus", "android.media.AudioAttributes", Int::class.javaPrimitiveType!!,
                "android.os.IBinder", "android.media.IAudioFocusDispatcher", String::class.java,
                String::class.java, String::class.java, Int::class.javaPrimitiveType!!,
                "android.media.audiopolicy.IAudioPolicyCallback", Int::class.javaPrimitiveType!!,
                object : MethodHook() {
                    override fun intercept(chain: XposedInterface.Chain): Any? = interceptCallAudioFocus(chain, access)
                }
            )
        }

        ModuleHelper.findAndHookMethod(
            "com.android.server.TelephonyRegistry", lpparam.classLoader, "listen",
            Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
            String::class.java, String::class.java, "com.android.internal.telephony.IPhoneStateListener",
            Set::class.java, Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val args = XposedHelpers.getArgsArray(chain)
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val events = args[5] as Set<Int>
                        args[5] = filterCallStateEvents(args[2] as? String, events)
                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    // Filter only this registration. Never mutate shared records or the caller's Set.
                    return chain.proceed(args)
                }
            }
        )
    }

    @JvmStatic
    fun FirstVolumePressHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.audio.AudioService\$VolumeController", lpparam.classLoader, "suppressAdjustment", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? = interceptFirstVolumePress(chain)
        })
    }

    internal fun interceptFirstVolumePress(chain: XposedInterface.Chain): Any? {
        // Preserve native long-press bookkeeping and propagate its exceptions exactly once.
        val result = chain.proceed()
        return ModuleHelper.guarded(result) {
            if (chain.getArg(0) != AudioManager.STREAM_MUSIC || chain.getArg(2) != false ||
                XposedHelpers.getObjectField(chain.thisObject, "mController") == null
            ) result else false
        }
    }

    @JvmStatic
    fun MuffledVibrationHook(lpparam: SystemServerStartingParam) {
        val effect = XposedHelpers.findClass("android.os.VibrationEffect", lpparam.classLoader)
        val scale = effect.getMethod("scale", Float::class.javaPrimitiveType)
        installAudioHapticsSnapshot()
        ModuleHelper.findAndHookMethod("com.android.server.vibrator.VibrationScaler", lpparam.classLoader,
            "scale", effect, Int::class.javaPrimitiveType, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? = interceptVibrationScale(chain, scale)
            })
    }

    internal fun interceptVibrationScale(chain: XposedInterface.Chain, scale: Method): Any? {
        // Preserve native strength, amplitude resolution, permission checks and exceptions first.
        val nativeEffect = chain.proceed() ?: return null
        return ModuleHelper.guarded(nativeEffect) {
            val cfg = audioHapticsConfig
            val ratio = when (chain.getArg(1) as? Int) {
                VibrationAttributes.USAGE_RINGTONE -> cfg.ampRinger
                VibrationAttributes.USAGE_NOTIFICATION -> cfg.ampNotif
                null -> return@guarded nativeEffect
                else -> cfg.ampOther
            }.coerceIn(0.01f, 1f)
            if (ratio == 1f) return@guarded nativeEffect
            val start = cfg.periodStartMinutes.coerceIn(0, 1439)
            val end = cfg.periodEndMinutes.coerceIn(0, 1439)
            // Equal endpoints mean all day and need no clock allocation.
            if (start != end) {
                val now = Calendar.getInstance()
                val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                if (!vibrationPeriodContains(start, end, minutes)) return@guarded nativeEffect
            }
            // Native scale preserves duration, frequency, repeat index and unsupported prebaked effects.
            scale.invoke(nativeEffect, ratio) ?: nativeEffect
        }
    }

    internal fun vibrationPeriodContains(start: Int, end: Int, minute: Int): Boolean = when {
        start == end -> true
        start < end -> minute >= start && minute < end
        else -> minute >= start || minute < end
    }

}
