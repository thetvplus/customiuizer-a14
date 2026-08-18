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
import android.telephony.PhoneStateListener
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.AudioVisualizer
import java.util.ArrayList
import java.util.Calendar
import tv.withaibuild.customiuizer.utils.HookUtils

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
        ModuleHelper.hookAllMethods("com.android.server.vibrator.VibratorManagerService", lpparam.classLoader, "vibrate", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val pkgName = chain.getArg(1) as String?
                    if (pkgName == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (checkVibration(pkgName)) { skipped = true; result = null; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NoDuckingHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.audio.FocusRequester", lpparam.classLoader, "handleFocusLoss", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    if ((chain.getArg(0) as Int) == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) { skipped = true; result = null; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
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
                    })

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
                    })

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

    private var audioFocusPkg: String? = null

    private fun removeListener(thisObject: Any) {
        val mRecords = XposedHelpers.getObjectField(thisObject, "mRecords") as ArrayList<Any>?
        if (mRecords == null) return
        for (record in mRecords) {
            val callingPackage = XposedHelpers.getObjectField(record, "callingPackage") as String?
            val events = XposedHelpers.getIntField(record, "events")
            val selectedApps = audioHapticsConfig.ignoreCallsApps
            if ((events and PhoneStateListener.LISTEN_CALL_STATE) == PhoneStateListener.LISTEN_CALL_STATE && callingPackage != null && selectedApps.contains(callingPackage)) {
                val newEvents = events and PhoneStateListener.LISTEN_CALL_STATE.inv()
                XposedHelpers.setIntField(record, "events", newEvents)
            }
        }
    }

    @JvmStatic
    fun NoCallInterruptionHook(lpparam: SystemServerStartingParam) {
        installAudioHapticsSnapshot()
        ModuleHelper.hookAllMethods("com.android.server.audio.AudioService", lpparam.classLoader, "requestAudioFocus", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {

                    if (args[4] == "AudioFocus_For_Phone_Ring_And_Calls" && audioFocusPkg != null && audioFocusPkg in audioHapticsConfig.ignoreCallsApps)
                        { skipped = true; result = 1; throwable = null }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val res = result as Int
                    if (res != AudioManager.AUDIOFOCUS_REQUEST_FAILED && args[4] != "AudioFocus_For_Phone_Ring_And_Calls")
                        audioFocusPkg = args[5] as String?

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.server.TelephonyRegistry", lpparam.classLoader, "notifyCallState", Int::class.javaPrimitiveType!!, String::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    removeListener(thisObject)

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.server.TelephonyRegistry", lpparam.classLoader, "notifyCallStateForPhoneId", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, String::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    removeListener(thisObject)

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun FirstVolumePressHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.audio.AudioService\$VolumeController", lpparam.classLoader, "suppressAdjustment", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
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
                    val args = chain.args

                    val streamType = args[0] as Int
                    if (streamType != AudioManager.STREAM_MUSIC) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val isMuteAdjust = args[2] as Boolean
                    if (isMuteAdjust) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mController = XposedHelpers.getObjectField(thisObject, "mController")
                    if (mController == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = false; throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun MuffledVibrationHook(lpparam: SystemServerStartingParam) {
        installAudioHapticsSnapshot()
        ModuleHelper.hookAllMethods("com.android.server.VibratorService", lpparam.classLoader, "doVibratorOn", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val cfg = audioHapticsConfig
                    val ratio_ringer = cfg.ampRinger
                    val ratio_notif = cfg.ampNotif
                    val ratio_other = cfg.ampOther

                    var isRingtone = false
                    var isNotification = false
                    val mCurrentVibration = XposedHelpers.getObjectField(thisObject, "mCurrentVibration")
                    if (mCurrentVibration != null) try {
                        isRingtone = XposedHelpers.callMethod(mCurrentVibration, "isRingtone") as Boolean
                        isNotification = XposedHelpers.callMethod(mCurrentVibration, "isNotification") as Boolean
                    } catch (t: Throwable) {
                        val mUsageHint = XposedHelpers.getIntField(mCurrentVibration, "mUsageHint")
                        isRingtone = mUsageHint == 6
                        isNotification = mUsageHint == 5 || mUsageHint == 7 || mUsageHint == 8 || mUsageHint == 9
                    }

                    val ratio = when {
                        isRingtone -> ratio_ringer
                        isNotification -> ratio_notif
                        else -> ratio_other
                    }
                    if (ratio == 1.0f) {
                        return XposedHelpers.proceedOrThrow(chain, throwable)
                    }

                    val startMinutes = cfg.periodStartMinutes
                    val endMinutes = cfg.periodEndMinutes
                    val now = Calendar.getInstance()
                    val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                    val insidePeriod = if (startMinutes < endMinutes) {
                        nowMinutes > startMinutes && nowMinutes < endMinutes
                    } else {
                        nowMinutes < endMinutes || nowMinutes > startMinutes
                    }
                    if (!insidePeriod) { return XposedHelpers.proceedOrThrow(chain, throwable) }

                    var mSupportsAmplitudeControl = false
                    try {
                        mSupportsAmplitudeControl = XposedHelpers.getBooleanField(thisObject, "mSupportsAmplitudeControl")
                    } catch (ignore: Throwable) {}

                    val args = XposedHelpers.getArgsArray(chain)
                    if (mSupportsAmplitudeControl)
                        args[1] = Math.round((if (args[1] as Int == -1) XposedHelpers.getIntField(thisObject, "mDefaultVibrationAmplitude") else args[1] as Int) * ratio)
                    else
                        args[0] = Math.max(3L, Math.round((args[0] as Long) * ratio.toDouble()))

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

}
