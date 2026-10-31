package tv.withaibuild.customiuizer.mods

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.AbstractInputMethodService
import android.view.inputmethod.InputMethodSession
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import java.lang.reflect.Field
import tv.withaibuild.customiuizer.utils.HookUtils

@SuppressLint("StaticFieldLeak")
object Controls {

    private const val NAV_BAR_DARK_STATE_FIELD = "custo_nav_bar_buttons_dark"

    @Volatile private var isPowerPressed = false
    private var isPowerLongPressed = false
    @Volatile private var isVolumePressed = false
    @Volatile private var isVolumeLongPressed = false
    private var isWaitingForPowerLongPressed = false
    @Volatile private var isWaitingForVolumeLongPressed = false
    private var wasRaise2WakeEnabled = false
    private var mHandler: Handler? = null

    internal data class ControlsSnapshot(
        val powerFlashDelay: Boolean = false,
        val volumeMediaUp: Int = 0,
        val volumeMediaDown: Int = 0,
        val volumeCursorApps: Set<String> = emptySet(),
        val volumeCursorEnabled: Boolean = false,
        val volumeCursorReverse: Boolean = false,
        val navbarMargin: Int = 0,
        val navbarLeftAction: Int = 1,
        val navbarLeftLongAction: Int = 1,
        val navbarRightAction: Int = 1,
        val navbarRightLongAction: Int = 1,
        val backLongAction: Int = 1,
        val homeLongAction: Int = 1,
        val menuLongAction: Int = 1,
        val fingerprintSuccessIgnore: Boolean = false,
        val fingerprintSuccess: Int = 1,
        val fsgCoverage: Int = 60,
        val fsgWidth: Int = 100,
        val volumeDownDtTorch: Boolean = false,
        val powerDtAction: Int = 1,
    )

    @Volatile
    internal var controlsConfig = ControlsSnapshot()

    private var controlsObserverRegistered = false

    private val CONTROLS_PREF_KEYS = setOf(
        "controls_powerflash_delay",
        "controls_volumemedia_up",
        "controls_volumemedia_down",
        "controls_volumecursor_apps",
        "controls_volumecursor",
        "controls_volumecursor_reverse",
        "controls_navbarmargin",
        "controls_navbarleft_action",
        "controls_navbarleftlong_action",
        "controls_navbarright_action",
        "controls_navbarrightlong_action",
        "controls_backlong_action",
        "controls_homelong_action",
        "controls_menulong_action",
        "controls_fingerprintsuccess_ignore",
        "controls_fingerprintsuccess",
        "controls_fsg_coverage",
        "controls_fsg_width",
        "controls_volumedowndt_torch",
        "controls_powerdt_action",
    )

    internal fun refreshControlsSnapshot() {
        val prefs = MainModule.mPrefs
        controlsConfig = ControlsSnapshot(
            powerFlashDelay = prefs.getBoolean("controls_powerflash_delay"),
            volumeMediaUp = prefs.getStringAsInt("controls_volumemedia_up", 0),
            volumeMediaDown = prefs.getStringAsInt("controls_volumemedia_down", 0),
            volumeCursorApps = HashSet(prefs.getStringSet("controls_volumecursor_apps")),
            volumeCursorEnabled = prefs.getBoolean("controls_volumecursor"),
            volumeCursorReverse = prefs.getBoolean("controls_volumecursor_reverse"),
            navbarMargin = prefs.getInt("controls_navbarmargin", 0),
            navbarLeftAction = prefs.getInt("controls_navbarleft_action", 1),
            navbarLeftLongAction = prefs.getInt("controls_navbarleftlong_action", 1),
            navbarRightAction = prefs.getInt("controls_navbarright_action", 1),
            navbarRightLongAction = prefs.getInt("controls_navbarrightlong_action", 1),
            backLongAction = prefs.getInt("controls_backlong_action", 1),
            homeLongAction = prefs.getInt("controls_homelong_action", 1),
            menuLongAction = prefs.getInt("controls_menulong_action", 1),
            fingerprintSuccessIgnore = prefs.getBoolean("controls_fingerprintsuccess_ignore"),
            fingerprintSuccess = prefs.getString("controls_fingerprintsuccess", "1").toIntOrNull() ?: 1,
            fsgCoverage = prefs.getInt("controls_fsg_coverage", 60).coerceIn(20, 95),
            fsgWidth = prefs.getInt("controls_fsg_width", 100).coerceIn(100, 400),
            volumeDownDtTorch = prefs.getBoolean("controls_volumedowndt_torch"),
            powerDtAction = prefs.getInt("controls_powerdt_action", 1),
        )
    }

    @JvmStatic
    internal fun installControlsSnapshot() {
        refreshControlsSnapshot()
        if (controlsObserverRegistered) return
        controlsObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in CONTROLS_PREF_KEYS) refreshControlsSnapshot()
            }
        })
    }

    private fun navbarActionFor(key: String): Int {
        val cfg = controlsConfig
        return when (key) {
            "controls_navbarleft" -> cfg.navbarLeftAction
            "controls_navbarleftlong" -> cfg.navbarLeftLongAction
            "controls_navbarright" -> cfg.navbarRightAction
            "controls_navbarrightlong" -> cfg.navbarRightLongAction
            else -> 1
        }
    }

    private fun isTorchEnabled(mContext: Context): Boolean {
        return Settings.Global.getInt(mContext.contentResolver, "torch_state", 0) != 0
    }

    private fun setTorch(context: Context, state: Boolean) {
        if (state) {
            val wakeup = Settings.System.getInt(context.contentResolver, "pick_up_gesture_wakeup_mode", 0)
            wasRaise2WakeEnabled = wakeup == 1
            if (wasRaise2WakeEnabled) Settings.System.putInt(context.contentResolver, "pick_up_gesture_wakeup_mode", 0)
        }
        val intent = Intent("miui.intent.action.TOGGLE_TORCH")
        intent.putExtra("miui.intent.extra.IS_ENABLE", state)
        context.sendBroadcast(intent)
    }

    private val mScreenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
            if (isTorchEnabled(context)) setTorch(context, false)
            if (HookUtils.mWakeLock != null && HookUtils.mWakeLock!!.isHeld) HookUtils.mWakeLock!!.release()
            if (wasRaise2WakeEnabled) {
                wasRaise2WakeEnabled = false
                Settings.System.putInt(context.contentResolver, "pick_up_gesture_wakeup_mode", 1)
            }
        }
    }

    @JvmStatic
    fun PowerKeyHook(lpparam: SystemServerStartingParam) {
        installControlsSnapshot()
        ModuleHelper.hookAllMethods("com.android.server.policy.PhoneWindowManager", lpparam.classLoader, "init", object : MethodHook() {
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
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    ModuleHelper.registerModuleReceiver(mContext, "torchScreenOnReceiver", mScreenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader, "interceptKeyBeforeQueueing", KeyEvent::class.java, Int::class.javaPrimitiveType, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    // Power and volkeys are pressed at the same time
                    if (isVolumePressed) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                    val keyEvent = chain.getArg(0) as KeyEvent

                    val keycode = keyEvent.keyCode
                    val action = keyEvent.action
                    val flags = keyEvent.flags

                    // Ignore repeated KeyEvents simulated on Power Key Up
                    if ((flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY) == KeyEvent.FLAG_VIRTUAL_HARD_KEY) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                    if ((flags and KeyEvent.FLAG_FROM_SYSTEM) != KeyEvent.FLAG_FROM_SYSTEM || keycode != KeyEvent.KEYCODE_POWER) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }

                    // Power long press
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mPowerManager = XposedHelpers.getObjectField(thisObject, "mPowerManager") as PowerManager
                    if (mPowerManager.isInteractive) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (action == KeyEvent.ACTION_DOWN) {
                        isPowerPressed = true
                        isPowerLongPressed = false

                        mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler

                        val longPressDelay = (if (controlsConfig.powerFlashDelay) ViewConfiguration.getLongPressTimeout() * 3 else ViewConfiguration.getLongPressTimeout()) + 500
                        // Post only one delayed runnable that waits for long press timeout
                        if (!isWaitingForPowerLongPressed) {
                            // Runs on the PhoneWindowManager handler inside system_server, outside
                            // this hook's try/catch. An unguarded throw here reboots the device.
                            mHandler!!.postDelayed(Runnable {
                                ModuleHelper.guarded {
                                    if (isPowerPressed) {
                                        isPowerLongPressed = true

                                        val wakeLock = HookUtils.mWakeLock
                                            ?: mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "miuizer:flashlight")
                                                .also { HookUtils.mWakeLock = it }

                                        if (!isTorchEnabled(mContext) || !wakeLock.isHeld) {
                                            setTorch(mContext, true)
                                            if (!wakeLock.isHeld) wakeLock.acquire(600000L)
                                        } else {
                                            setTorch(mContext, true)
                                            if (wakeLock.isHeld) wakeLock.release()
                                        }
                                    }
                                }
                                isPowerPressed = false
                                isWaitingForPowerLongPressed = false
                            }, longPressDelay.toLong())
                        }

                        isWaitingForPowerLongPressed = true
                        skipped = true; result = 0; throwable = null
                    }

                    if (action == KeyEvent.ACTION_UP) {
                        if (isPowerPressed && !isPowerLongPressed) {
                            try {
                                if (isTorchEnabled(mContext)) setTorch(mContext, false)
                                if (HookUtils.mWakeLock != null && HookUtils.mWakeLock!!.isHeld) HookUtils.mWakeLock!!.release()
                                XposedHelpers.callMethod(mPowerManager, "wakeUp", SystemClock.uptimeMillis())
                                skipped = true; result = 0; throwable = null
                            } catch (t: Throwable) {
                                XposedHelpers.log(t)
                            }
                        } else if (wasRaise2WakeEnabled && !isTorchEnabled(mContext)) {
                            wasRaise2WakeEnabled = false
                            Settings.System.putInt(mContext.contentResolver, "pick_up_gesture_wakeup_mode", 1)
                        }
                        isPowerPressed = false
                        isWaitingForPowerLongPressed = false
                    }

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
    fun VolumeMediaButtonsHook(lpparam: SystemServerStartingParam) {
        installControlsSnapshot()
        val owner = XposedHelpers.findClassIfExists("com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader)
            ?: return unsupportedControlsContract("VolumeMediaButtonsHook")
        val access = VolumePolicyAccess.resolve(owner) ?: return unsupportedControlsContract("VolumeMediaButtonsHook")
        ModuleHelper.hookMethod(access.intercept, volumeMediaButtonsHook(access))
    }

    private data class VolumePressToken(val code: Int, val device: Int, val downTime: Long)

    private fun unsupportedControlsContract(name: String) {
        XposedHelpers.log("$name: complete native contract unavailable; keeping native behavior")
    }

    // The callback runs only in PhoneWindowManager, using the system process permissions.
    @SuppressLint("MissingPermission")
    internal fun volumeMediaButtonsHook(access: VolumePolicyAccess) = object : MethodHook() {
        private val longPress = tv.withaibuild.customiuizer.mods.utils.OwnedHandlerCallback()
        @Volatile private var owned: VolumePressToken? = null
        private fun owns(event: KeyEvent) = owned?.let {
            it.code == event.keyCode && it.device == event.deviceId && it.downTime == event.downTime
        } == true
        override fun intercept(chain: XposedInterface.Chain): Any? {
            var skipped = false
            var result: Any? = null
            var throwable: Throwable? = null
            try {
                // Power and volkeys are pressed at the same time
                if (isPowerPressed) {
                    longPress.cancel(); isVolumePressed = false; isWaitingForVolumeLongPressed = false
                    val event = chain.getArg(0) as KeyEvent
                    if (owns(event)) {
                        if (event.action == KeyEvent.ACTION_UP) owned = null
                        return 0
                    }
                    return chain.proceed()
                }
                val keyEvent = chain.getArg(0) as KeyEvent

                val keycode = keyEvent.keyCode
                val action = keyEvent.action
                val flags = keyEvent.flags

                // Ignore repeated KeyEvents simulated on volume Key Up
                if ((flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY) == KeyEvent.FLAG_VIRTUAL_HARD_KEY) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                if ((flags and KeyEvent.FLAG_FROM_SYSTEM) != KeyEvent.FLAG_FROM_SYSTEM || (keycode != KeyEvent.KEYCODE_VOLUME_UP && keycode != KeyEvent.KEYCODE_VOLUME_DOWN)) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }

                val configured = if (keycode == KeyEvent.KEYCODE_VOLUME_UP) controlsConfig.volumeMediaUp else controlsConfig.volumeMediaDown
                if (action == KeyEvent.ACTION_DOWN && owned != null && !owns(keyEvent)) {
                    longPress.cancel(); isVolumePressed = false; isWaitingForVolumeLongPressed = false
                    if (keycode == owned?.code && keyEvent.deviceId == owned?.device && keyEvent.repeatCount == 0)
                    owned = null // A new stroke repairs a missing old UP.
                    else { isVolumeLongPressed = true; return chain.proceed() }
                }
                if (!owns(keyEvent) && configured == 0 || action == KeyEvent.ACTION_UP && !owns(keyEvent)) return chain.proceed()
                if (action == KeyEvent.ACTION_DOWN && owns(keyEvent)) return 0
                if (action == KeyEvent.ACTION_DOWN && (keyEvent.repeatCount > 0 || keyEvent.isCanceled)) return chain.proceed()

                // Volume long press
                val thisObject = chain.thisObject
                if (!access.owner.isInstance(thisObject)) return chain.proceed()
                val mContext = access.context.get(thisObject) as? Context ?: return chain.proceed()
                val mPowerManager = access.power.get(thisObject) as? PowerManager ?: return chain.proceed()
                if (mPowerManager.isInteractive) {
                    longPress.cancel(); isVolumePressed = false; isWaitingForVolumeLongPressed = false
                    if (owns(keyEvent)) {
                        if (action == KeyEvent.ACTION_UP) owned = null
                        return 0
                    }
                    return chain.proceed()
                }
                if (action == KeyEvent.ACTION_DOWN) {
                    val volumeHandler = access.handler.get(thisObject) as? Handler ?: return chain.proceed()
                    if (access.wakeLock.get(thisObject) !is WakeLock) return chain.proceed()
                    val press = VolumePressToken(keycode, keyEvent.deviceId, keyEvent.downTime)
                    owned = press
                    isVolumePressed = true
                    isVolumeLongPressed = false

                    // Post only one delayed runnable that waits for long press timeout
                    if (!isWaitingForVolumeLongPressed) {
                        // Runs on the PhoneWindowManager handler inside system_server, outside
                        // this hook's try/catch. An unguarded throw here reboots the device.
                        val task = object : Runnable {
                            override fun run() {
                                if (!longPress.take(this)) return
                                ModuleHelper.guarded {
                                    if (owned === press && isVolumePressed && !isPowerPressed && !mPowerManager.isInteractive && GlobalActions.isMediaActionsAllowed(mContext)) {
                                        when (press.code) {
                                            KeyEvent.KEYCODE_VOLUME_UP -> {
                                                val pref_mediaUp = controlsConfig.volumeMediaUp
                                                if (pref_mediaUp != 0) {
                                                    isVolumeLongPressed = true
                                                    GlobalActions.sendDownUpKeyEvent(mContext, pref_mediaUp, true)
                                                }
                                            }
                                            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                                val pref_mediaDown = controlsConfig.volumeMediaDown
                                                if (pref_mediaDown != 0) {
                                                    isVolumeLongPressed = true
                                                    GlobalActions.sendDownUpKeyEvent(mContext, pref_mediaDown, true)
                                                }
                                            }
                                        }
                                    }
                                }
                                if (owned === press) {
                                    isVolumePressed = false
                                    isWaitingForVolumeLongPressed = false
                                }
                            }
                        }
                        // Publish before enqueue: the policy handler can run before post returns.
                        isWaitingForVolumeLongPressed = true
                        if (!longPress.post(volumeHandler, task, ViewConfiguration.getLongPressTimeout().toLong())) {
                            owned = null
                            isVolumePressed = false
                            isWaitingForVolumeLongPressed = false
                            return chain.proceed()
                        }
                    }
                    skipped = true; result = 0; throwable = null
                }

                if (action == KeyEvent.ACTION_UP) {
                    isVolumePressed = false
                    // This is the PhoneWindowManager's shared Handler. Remove only our lease.
                    longPress.cancel()
                    isWaitingForVolumeLongPressed = false
                    owned = null
                    if (!isVolumeLongPressed && !keyEvent.isCanceled) {
                        val am = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val tm = mContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                        val mBroadcastWakeLock = access.wakeLock.get(thisObject) as? WakeLock ?: return 0
                        var k = AudioManager.ADJUST_RAISE
                        if (keycode != KeyEvent.KEYCODE_VOLUME_UP) k = AudioManager.ADJUST_LOWER
                        mBroadcastWakeLock.acquire(5000L)
                        try {
                            // If music stream is playing, adjust its volume
                            if (am.isMusicActive) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, k, 0)
                            // If voice call is active while screen off by proximity sensor, adjust its volume
                            else if (tm.isInCall) am.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, k, 0)
                            // If volume keys to wake option active, wake the device
                            else if (Settings.System.getInt(mContext.contentResolver, "volumekey_wake_screen", 0) == 1) XposedHelpers.callMethod(mPowerManager, "wakeUp", SystemClock.uptimeMillis())
                        } finally {
                            if (mBroadcastWakeLock.isHeld) mBroadcastWakeLock.release()
                        }
                    }
                    skipped = true; result = 0; throwable = null
                    isWaitingForVolumeLongPressed = false
                    owned = null
                }

                if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                result = chain.proceed()
            } catch (t: Throwable) {
                throwable = t
                result = null
            }
            return XposedHelpers.throwOrReturn(throwable, result)
        }
    }


    @JvmStatic
    fun VolumeMediaPlayerHook(lpparam: PackageReadyParam) {
        val player = XposedHelpers.findClassIfExists("android.media.MediaPlayer", lpparam.classLoader)
            ?: return unsupportedControlsContract("VolumeMediaPlayerHook")
        val stream = XposedHelpers.findMethodExactIfExists(player, "getAudioStreamType")
            ?: return unsupportedControlsContract("VolumeMediaPlayerHook")
        if (stream.returnType != Int::class.javaPrimitiveType) return unsupportedControlsContract("VolumeMediaPlayerHook")
        stream.isAccessible = true
        ModuleHelper.findAndHookMethod(player, "pause", mediaPauseHook { owner ->
            val context = ModuleHelper.findContext(lpparam) ?: return@mediaPauseHook
            val type = stream.invoke(owner) as? Int ?: return@mediaPauseHook
            if (type == AudioManager.STREAM_MUSIC || type == 0x80000000.toInt()) {
                val intent = Intent(GlobalActions.ACTION_PREFIX + "SaveLastMusicPausedTime")
                intent.setPackage("com.android.systemui")
                context.sendBroadcast(intent)
            }
        })
    }

    internal fun mediaPauseHook(afterPause: (Any) -> Unit) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val original = chain.proceed()
            ModuleHelper.guarded { afterPause(chain.thisObject) }
            return original
        }
    }

    @JvmStatic
    fun VolumeCursorHook(lpparam: PackageReadyParam) {
        installControlsSnapshot()
        val session = AbstractInputMethodService.AbstractInputMethodSessionImpl::class.java
        val owner = XposedHelpers.findField(session, "this\$0")
        ModuleHelper.findAndHookMethod(session, "dispatchKeyEvent", Int::class.javaPrimitiveType,
            KeyEvent::class.java, InputMethodSession.EventCallback::class.java, volumeCursorSessionHook(owner))
    }

    /** The framework session precedes IME overrides that may never call super.onKeyDown.
     * An accepted key completes its original callback once; all other events proceed unchanged. */
    internal fun volumeCursorSessionHook(owner: java.lang.reflect.Field) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val event = chain.getArg(1) as? KeyEvent ?: return chain.proceed()
            val code = event.keyCode
            if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN ||
                event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return chain.proceed()
            val session = chain.thisObject as? AbstractInputMethodService.AbstractInputMethodSessionImpl ?: return chain.proceed()
            if (!session.isEnabled) return chain.proceed()
            val callback = chain.getArg(2) as? InputMethodSession.EventCallback ?: return chain.proceed()
            val sequence = chain.getArg(0) as? Int ?: return chain.proceed()
            val service = owner.get(session) as? InputMethodService ?: return chain.proceed()
            if (!dispatchVolumeCursor(service, code, event, event.action == KeyEvent.ACTION_DOWN)) return chain.proceed()
            callback.finishedEvent(sequence, true)
            return null
        }
    }

    private fun dispatchVolumeCursor(service: InputMethodService, code: Int, event: KeyEvent, down: Boolean): Boolean =
        ModuleHelper.guarded(false) {
            val key = "customiuizer_volume_cursor_keys"
            var state = XposedHelpers.getAdditionalInstanceField(service, key) as? VolumeCursorKeys
            if (state == null) {
                if (!down || !controlsConfig.volumeCursorEnabled) return@guarded false
                state = VolumeCursorKeys(VolumeCursorKeys.Native(service))
                XposedHelpers.setAdditionalInstanceField(service, key, state)
            }
            if (down) state.onDown(code, event, controlsConfig) else state.onUp(code, event)
        }

    private fun handleNavBarAction(context: Context, key: String): Boolean {
        val action = navbarActionFor(key)
        if (action in 85..88) {
            if (GlobalActions.isMediaActionsAllowed(context)) {
                GlobalActions.sendDownUpKeyEvent(context, action, false)
            }
            return true
        } else if (action == 1) {
            try {
                Toast.makeText(ModuleHelper.getModuleContext(context), R.string.controls_navbar_noaction, Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
            return false
        } else {
            return GlobalActions.handleAction(context, key)
        }
    }

    private fun reposNavBarButtons(navbar: FrameLayout) {
        val mContext = navbar.context
        val displayRotation = navbar.context.display.rotation
        val density = mContext.resources.displayMetrics.density
        val margin = Math.round(controlsConfig.navbarMargin * density)
        if (displayRotation == Surface.ROTATION_0) {
            val hleft = navbar.findViewWithTag<ImageView>("custom_left_horiz")
            if (hleft != null) {
                val leftbtn = hleft.parent as LinearLayout
                val lpl = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)
                lpl.leftMargin += margin
                lpl.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                leftbtn.layoutParams = lpl
            }

            val hright = navbar.findViewWithTag<ImageView>("custom_right_horiz")
            if (hright != null) {
                val rightbtn = hright.parent as LinearLayout
                val lpr = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT)
                lpr.rightMargin += margin
                lpr.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightbtn.layoutParams = lpr
            }
        } else {
            val vleft = navbar.findViewWithTag<ImageView>("custom_left_vert")
            val vright = navbar.findViewWithTag<ImageView>("custom_right_vert")

            var leftbtn: LinearLayout? = null
            if (vleft != null) {
                leftbtn = vleft.parent as LinearLayout
            }
            val lpl = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)

            var rightbtn: LinearLayout? = null
            if (vright != null) {
                rightbtn = vright.parent as LinearLayout
            }
            val lpr = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            if (displayRotation == Surface.ROTATION_270) {
                lpl.topMargin += margin
                lpl.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

                lpr.bottomMargin += margin
                lpr.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            } else if (displayRotation == Surface.ROTATION_90) {
                lpr.topMargin += margin
                lpr.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

                lpl.bottomMargin += margin
                lpl.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            if (leftbtn != null) leftbtn.layoutParams = lpl
            if (rightbtn != null) rightbtn.layoutParams = lpr
        }
    }

    private fun addCustomNavBarKeys(isVertical: Boolean, mContext: Context, navButtons: FrameLayout, kbrCls: Class<*>?) {
        val dot1: Drawable
        val dot2: Drawable
        try {
            val modCtx = ModuleHelper.getModuleContext(mContext)
            val modRes = ModuleHelper.getModuleRes(mContext)
            dot1 = modRes.getDrawable(R.drawable.ic_sysbar_dot_bottomleft, modCtx.theme)!!
            dot2 = modRes.getDrawable(R.drawable.ic_sysbar_dot_topright, modCtx.theme)!!
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            return
        }

        val leftbtn = LinearLayout(mContext)
        val left = ImageView(mContext)

        val lplc: LinearLayout.LayoutParams = if (isVertical)
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        else
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        left.layoutParams = lplc
        left.setImageDrawable(dot1)
        left.alpha = 0.9f
        left.tag = "custom_left" + if (isVertical) "_vert" else "_horiz"
        if (kbrCls != null) {
            try {
                val lripple = kbrCls.getConstructor(Context::class.java, View::class.java).newInstance(mContext, leftbtn) as Drawable
                leftbtn.background = lripple
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
        }
        leftbtn.isClickable = true
        leftbtn.isHapticFeedbackEnabled = true
        leftbtn.setOnClickListener { view -> ModuleHelper.guarded { handleNavBarAction(view.context, "controls_navbarleft") } }
        // handleNavBarAction returns false when no action is configured, which lets the
        // ROM keep its own long-press behavior. Propagate it instead of consuming.
        leftbtn.setOnLongClickListener { view ->
            ModuleHelper.guarded(false) { handleNavBarAction(view.context, "controls_navbarleftlong") }
        }
        leftbtn.addView(left)

        val rightbtn = LinearLayout(mContext)
        val right = ImageView(mContext)
        val lprc: LinearLayout.LayoutParams = if (isVertical)
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        else
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        right.layoutParams = lprc
        right.setImageDrawable(dot2)
        right.alpha = 0.9f
        right.tag = "custom_right" + if (isVertical) "_vert" else "_horiz"
        if (kbrCls != null) {
            try {
                val rripple = kbrCls.getConstructor(Context::class.java, View::class.java).newInstance(mContext, rightbtn) as Drawable
                rightbtn.background = rripple
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
        }
        rightbtn.isClickable = true
        rightbtn.isHapticFeedbackEnabled = true
        rightbtn.setOnClickListener { view -> ModuleHelper.guarded { handleNavBarAction(view.context, "controls_navbarright") } }
        // handleNavBarAction returns false when no action is configured, which lets the
        // ROM keep its own long-press behavior. Propagate it instead of consuming.
        rightbtn.setOnLongClickListener { view ->
            ModuleHelper.guarded(false) { handleNavBarAction(view.context, "controls_navbarrightlong") }
        }
        rightbtn.addView(right)

        val hasLeftAction = controlsConfig.navbarLeftAction > 1 || controlsConfig.navbarLeftLongAction > 1
        val hasRightAction = controlsConfig.navbarRightAction > 1 || controlsConfig.navbarRightLongAction > 1

//		float part = 0.55f;
        if (isVertical) {
            if (hasRightAction) {
                navButtons.addView(rightbtn, 0)
//				lp2.weight = Math.round(lp2.weight * part);
            }
            if (hasLeftAction) {
                navButtons.addView(leftbtn, navButtons.childCount)
//				lp1.weight = Math.round(lp1.weight * part);
            }
        } else {
            if (hasLeftAction) {
                navButtons.addView(leftbtn, 0)
//				lp1.weight = Math.round(lp1.weight * part);
            }
            if (hasRightAction) {
                navButtons.addView(rightbtn, navButtons.childCount)
//				lp2.weight = Math.round(lp2.weight * part);
            }
        }
    }

    @JvmStatic
    fun NavBarButtonsHook(lpparam: PackageReadyParam) {
        installControlsSnapshot()
        val config = controlsConfig
        if (!hasExtraNavBarButtons(config)) return
        ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBarView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
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
                    val navBar = thisObject as FrameLayout
                    val mContext = navBar.context
                    val mHorizontal = XposedHelpers.getObjectField(thisObject, "mHorizontal") as ViewGroup
                    val mVertical = XposedHelpers.getObjectField(thisObject, "mVertical") as ViewGroup
                    val navButtonsId = navBar.resources.getIdentifier("nav_buttons", "id", lpparam.packageName)
                    val navButtons0 = mHorizontal.findViewById<FrameLayout>(navButtonsId)
                    val navButtons90 = mVertical.findViewById<FrameLayout>(navButtonsId)

                    val kbrCls = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.MiuiKeyButtonRipple", lpparam.classLoader)
                    addCustomNavBarKeys(false, mContext, navButtons0, kbrCls)
                    addCustomNavBarKeys(true, mContext, navButtons90, kbrCls)
                    reposNavBarButtons(navBar)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBarTransitions", lpparam.classLoader, "applyDarkIntensity", Float::class.javaPrimitiveType, object : MethodHook() {
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

                    val navbar = XposedHelpers.getObjectField(thisObject, "mView") as FrameLayout
                    val isDark = (chain.getArg(0) as Float) > 0.5f
                    val hleft = navbar.findViewWithTag<ImageView>("custom_left_horiz")
                    val vleft = navbar.findViewWithTag<ImageView>("custom_left_vert")
                    val hright = navbar.findViewWithTag<ImageView>("custom_right_horiz")
                    val vright = navbar.findViewWithTag<ImageView>("custom_right_vert")
                    if (hleft == null && vleft == null && hright == null && vright == null) {
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    val previousDark = XposedHelpers.getAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD) as? Boolean
                    if (previousDark == isDark) {
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }

                    val modCtx = ModuleHelper.getModuleContext(navbar.context)
                    val modRes = ModuleHelper.getModuleRes(navbar.context)
                    if (isDark) {
                        val darkImg1 = modRes.getDrawable(R.drawable.ic_sysbar_dot_bottomleft_dark, modCtx.theme)!!
                        val darkImg2 = modRes.getDrawable(R.drawable.ic_sysbar_dot_topright_dark, modCtx.theme)!!
                        if (hleft != null) hleft.setImageDrawable(darkImg1)
                        if (vleft != null) vleft.setImageDrawable(darkImg1)
                        if (hright != null) hright.setImageDrawable(darkImg2)
                        if (vright != null) vright.setImageDrawable(darkImg2)
                    } else {
                        val lightImg1 = modRes.getDrawable(R.drawable.ic_sysbar_dot_bottomleft, modCtx.theme)!!
                        val lightImg2 = modRes.getDrawable(R.drawable.ic_sysbar_dot_topright, modCtx.theme)!!
                        if (hleft != null) hleft.setImageDrawable(lightImg1)
                        if (vleft != null) vleft.setImageDrawable(lightImg1)
                        if (hright != null) hright.setImageDrawable(lightImg2)
                        if (vright != null) vright.setImageDrawable(lightImg2)
                    }
                    XposedHelpers.setAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD, isDark)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.navigationbar.NavigationBarView", lpparam.classLoader, "onConfigurationChanged", Configuration::class.java,
            object : MethodHook() {
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

                        val navbar = thisObject as FrameLayout
                        XposedHelpers.removeAdditionalInstanceField(navbar, NAV_BAR_DARK_STATE_FIELD)
                        reposNavBarButtons(navbar)
                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
    }

    internal fun hasExtraNavBarButtons(config: ControlsSnapshot): Boolean =
        config.navbarLeftAction > 1 || config.navbarLeftLongAction > 1 ||
            config.navbarRightAction > 1 || config.navbarRightLongAction > 1

    internal fun navBarLongPressAction(keyCode: Int, config: ControlsSnapshot): Int = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> config.backLongAction
        KeyEvent.KEYCODE_HOME -> config.homeLongAction
        // HyperOS 1's navigation rules use MENU (82), not APP_SWITCH (187).
        KeyEvent.KEYCODE_MENU -> config.menuLongAction
        else -> 1
    }

    internal fun interceptNavBarSupportLongPress(
        chain: XposedInterface.Chain,
        primaryKeyField: Field,
    ): Any? {
        val config = controlsConfig
        // Native support also refreshes mLongPressFunction for a later failure fallback.
        val nativeSupport = chain.proceed()
        if (config.backLongAction <= 1 && config.homeLongAction <= 1 && config.menuLongAction <= 1) {
            return nativeSupport
        }
        val enabled = ModuleHelper.guarded(false) {
            navBarLongPressAction(primaryKeyField.getInt(chain.thisObject), config) > 1
        }
        return if (enabled) true else nativeSupport
    }

    internal fun interceptNavBarLongPress(
        chain: XposedInterface.Chain,
        primaryKeyField: Field,
        contextField: Field,
        executeAction: (Context, String, Int) -> Boolean,
        vibrate: (Context) -> Unit,
        consumption: NavBarLongPressConsumption? = null,
    ): Any? {
        val config = controlsConfig
        if (config.backLongAction <= 1 && config.homeLongAction <= 1 && config.menuLongAction <= 1) {
            return chain.proceed()
        }
        var handled = false
        ModuleHelper.guarded {
            val owner = chain.thisObject ?: return@guarded
            val keyCode = primaryKeyField.getInt(owner)
            val action = navBarLongPressAction(keyCode, config)
            if (action <= 1) return@guarded
            val actionKey = when (keyCode) {
                KeyEvent.KEYCODE_BACK -> "controls_backlong"
                KeyEvent.KEYCODE_HOME -> "controls_homelong"
                KeyEvent.KEYCODE_MENU -> "controls_menulong"
                else -> return@guarded
            }
            val context = contextField.get(owner) as? Context ?: return@guarded
            val downTime = if (consumption == null) 0L else chain.getArg(0) as? Long ?: return@guarded
            if (consumption != null && !consumption.canTrack(keyCode, downTime)) return@guarded
            try {
                handled = executeAction(context, actionKey, action)
                // A vibration failure must not execute the native action after a successful custom action.
                if (handled) {
                    consumption?.markHandled(keyCode, downTime)
                    vibrate(context)
                }
            } catch (failure: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(failure)
                throw failure
            }
        }
        return if (handled) null else chain.proceed()
    }

    internal fun interceptNavBarKeyDispatch(
        chain: XposedInterface.Chain,
        consumption: NavBarLongPressConsumption,
    ): Any? {
        if (!consumption.hasHandledPress()) return chain.proceed()
        val event = ModuleHelper.guarded<KeyEvent?>(null) {
            try {
                chain.getArg(1) as? KeyEvent
            } catch (failure: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(failure)
                throw failure
            }
        }
            ?: return chain.proceed()
        val key = event.keyCode
        if (key != KeyEvent.KEYCODE_HOME && key != KeyEvent.KEYCODE_BACK && key != KeyEvent.KEYCODE_MENU) {
            return chain.proceed()
        }
        return consumption.dispatch(chain, key, event.downTime, event.action)
    }

    @JvmStatic
    fun NavBarActionsHook(lpparam: SystemServerStartingParam) {
        val ruleClass = XposedHelpers.findClass(
            "com.android.server.policy.MiuiSingleKeyRule",
            lpparam.classLoader,
        )
        // Resolve the exact ROM members once, before registering either callback.
        XposedHelpers.findMethodExact(ruleClass, "supportLongPress", *emptyArray<Class<*>>())
        XposedHelpers.findMethodExact(ruleClass, "onLongPress", Long::class.javaPrimitiveType)
        val primaryKeyField = XposedHelpers.findField(ruleClass, "mKeyCode")
        val contextField = XposedHelpers.findField(ruleClass, "mContext")
        val policyClass = XposedHelpers.findClass("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader)
        XposedHelpers.findMethodExact(
            policyClass, "interceptKeyBeforeDispatching", IBinder::class.java, KeyEvent::class.java, Int::class.javaPrimitiveType,
        )
        val cancelMenuPreloadMethod = XposedHelpers.findMethodExact(
            "com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader, "cancelPreloadRecentAppsInternal",
        )
        val consumption = NavBarLongPressConsumption { chain -> cancelMenuPreloadMethod.invoke(chain.thisObject) }
        installControlsSnapshot()
        val executeAction: (Context, String, Int) -> Boolean = { context, key, action ->
            GlobalActions.handleResolvedAction(context, key, action)
        }
        val vibrate: (Context) -> Unit = { HookUtils.performStrongVibration(it) }
        // Real HOME enters this parent method and bypasses the Default hooks on the target ROM.
        ModuleHelper.findAndHookMethod(ruleClass, "supportLongPress", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? =
                interceptNavBarSupportLongPress(chain, primaryKeyField)
        })
        ModuleHelper.findAndHookMethod(ruleClass, "onLongPress", Long::class.javaPrimitiveType, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? =
                interceptNavBarLongPress(chain, primaryKeyField, contextField, executeAction, vibrate, consumption)
        })
        ModuleHelper.findAndHookMethod(
            policyClass, "interceptKeyBeforeDispatching", IBinder::class.java, KeyEvent::class.java,
            Int::class.javaPrimitiveType, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? = interceptNavBarKeyDispatch(chain, consumption)
            },
        )
    }

    @JvmStatic
    fun FingerprintHapticSuccessHook(lpparam: SystemServerStartingParam) {
        installControlsSnapshot()
        ModuleHelper.hookAllMethods("com.android.server.biometrics.sensors.AuthenticationClient", lpparam.classLoader, "onAuthenticated", object : MethodHook() {
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

                    val mAuthSuccess = XposedHelpers.getBooleanField(thisObject, "mAuthSuccess")
                    if (!mAuthSuccess) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context

                    val ignoreSystem = controlsConfig.fingerprintSuccessIgnore
                    val opt = controlsConfig.fingerprintSuccess
                    if (opt == 2)
                        HookUtils.performLightVibration(mContext, ignoreSystem)
                    else if (opt == 3)
                        HookUtils.performStrongVibration(mContext, ignoreSystem)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun FingerprintHapticFailureHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.biometrics.sensors.AcquisitionClient", lpparam.classLoader, "vibrateError", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    skipped = true; result = null; throwable = null

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
    fun FingerprintScreenOnHook(lpparam: SystemServerStartingParam) {
        val authClient = "com.android.server.biometrics.sensors.AuthenticationClient"
        ModuleHelper.hookAllMethods(authClient, lpparam.classLoader, "onAuthenticated", object : MethodHook() {
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

                    val mAuthSuccess = XposedHelpers.getBooleanField(thisObject, "mAuthSuccess")
                    if (mAuthSuccess) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mPowerManager = mContext.getSystemService(Context.POWER_SERVICE) as PowerManager
                    if (mPowerManager.isInteractive) { return XposedHelpers.throwOrReturn(throwable, result) }
                    if (!GlobalActions.commonSendAction(mContext, "WakeUp")) XposedHelpers.log("FingerprintScreenOnHook", "Failed to wake up device")
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun BackGestureAreaHeightHook(lpparam: PackageReadyParam) {
        installControlsSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "getGestureStubWindowParam", backGestureHeightHook())
    }

    internal fun backGestureHeightHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            ModuleHelper.guarded {
                val pct = controlsConfig.fsgCoverage
                if (pct == 60) return@guarded
                val lp = result as? WindowManager.LayoutParams ?: return@guarded
                val owner = chain.thisObject
                if (XposedHelpers.getIntField(owner, "mGestureStubPos") == 2) return@guarded
                val rotation = XposedHelpers.getIntField(owner, "mRotation")
                // Native portrait split screen uses 100%; other side windows use 60%.
                val fullHeight = (rotation == 0 || rotation == 2) &&
                    XposedHelpers.getBooleanField(owner, "mIsInMultiWindowMode") &&
                    !XposedHelpers.getBooleanField(owner, "mIsInMinimizedMultiWindowMode")
                val nativePercent = if (fullHeight) 100f else 60f
                lp.height = Math.round(lp.height / nativePercent * pct)
            }
            return result
        }
    }

    @JvmStatic
    fun BackGestureAreaWidthHook(lpparam: PackageReadyParam) {
        installControlsSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "initScreenSizeAndDensity", backGestureWidthInitHook())

        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "setSize", Int::class.javaPrimitiveType, backGestureSetSizeHook())
    }

    internal fun backGestureWidthInitHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            ModuleHelper.guarded {
                val pct = controlsConfig.fsgWidth
                if (pct == 100) return@guarded
                val owner = chain.thisObject
                val defaultSize = XposedHelpers.getIntField(owner, "mGestureStubDefaultSize")
                val size = XposedHelpers.getIntField(owner, "mGestureStubSize")
                XposedHelpers.setIntField(owner, "mGestureStubDefaultSize", Math.round(defaultSize * pct / 100f))
                XposedHelpers.setIntField(owner, "mGestureStubSize", Math.round(size * pct / 100f))
            }
            return result
        }
    }

    internal fun backGestureSetSizeHook() = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val pct = controlsConfig.fsgWidth
                    if (pct == 100) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val mGestureStubDefaultSize = XposedHelpers.getIntField(thisObject, "mGestureStubDefaultSize")
                    val requestedSize = chain.getArg(0) as Int
                    if (requestedSize == mGestureStubDefaultSize) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val args = XposedHelpers.getArgsArray(chain)
                    args[0] = Math.round(requestedSize * pct / 100f)

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

    @JvmStatic
    fun HideNavBarHook(lpparam: PackageReadyParam) {
        val loader = lpparam.classLoader
        val callback = XposedHelpers.findClassIfExists("com.android.systemui.recents.OverviewProxyService\$6", loader) ?: return unsupportedControlsContract("HideNavBarHook")
        val controller = XposedHelpers.findClassIfExists("com.android.systemui.navigationbar.NavigationBarController", loader) ?: return unsupportedControlsContract("HideNavBarHook")
        val result = XposedHelpers.findClassIfExists("com.android.internal.statusbar.RegisterStatusBarResult", loader) ?: return unsupportedControlsContract("HideNavBarHook")
        val observer = XposedHelpers.findClassIfExists("com.miui.systemui.controller.GestureObserver", loader) ?: return unsupportedControlsContract("HideNavBarHook")
        val gestureLine = XposedHelpers.findFieldIfExists(observer, "mGestureLineEnable") ?: return unsupportedControlsContract("HideNavBarHook")
        val state = XposedHelpers.findMethodExactIfExists(callback, "setWindowState",
            Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType) ?: return unsupportedControlsContract("HideNavBarHook")
        val create = XposedHelpers.findMethodExactIfExists(controller, "createNavigationBar",
            android.view.Display::class.java, Bundle::class.java, result) ?: return unsupportedControlsContract("HideNavBarHook")
        if (state.returnType != Void.TYPE || create.returnType != Void.TYPE || gestureLine.type != Boolean::class.javaPrimitiveType ||
            java.lang.reflect.Modifier.isStatic(gestureLine.modifiers) || java.lang.reflect.Modifier.isFinal(gestureLine.modifiers) ||
            listOf(state, create).any { java.lang.reflect.Modifier.isStatic(it.modifiers) || java.lang.reflect.Modifier.isAbstract(it.modifiers) })
            return unsupportedControlsContract("HideNavBarHook")
        gestureLine.isAccessible = true
        val stateHandle = ModuleHelper.hookMethod(state, hiddenNavWindowStateHook {
            val current = ModuleHelper.getDepInstance(loader, "com.miui.systemui.controller.GestureObserver")
            if (observer.isInstance(current)) gestureLine.setBoolean(current, true)
        }) ?: return unsupportedControlsContract("HideNavBarHook")
        if (ModuleHelper.hookMethod(create, hiddenNavCreationHook()) == null) stateHandle.unhook()
    }

    internal fun hiddenNavWindowStateHook(enableGestureLine: () -> Unit) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            // Both sampled native bodies consume this flag before updating the default
            // display's navigation-window state. Status-bar and secondary-display events
            // must not change that shared native property.
            if (chain.getArg(0) == android.view.Display.DEFAULT_DISPLAY && chain.getArg(1) == 2)
                ModuleHelper.guarded { enableGestureLine() }
            return chain.proceed()
        }
    }

    internal fun hiddenNavCreationHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val display = chain.getArg(0) as? android.view.Display
            return if (display?.displayId == android.view.Display.DEFAULT_DISPLAY) null else chain.proceed()
        }
    }

    @JvmStatic
    fun PowerDoubleTapActionHook(lpparam: SystemServerStartingParam) {
        installControlsSnapshot()
        val doubleTapResons = arrayOf("double_click_power", "power_double_tap", "double_click_power_key")
        ModuleHelper.findAndHookMethod("com.miui.server.input.util.ShortCutActionsUtils", lpparam.classLoader, "triggerFunction", String::class.java, String::class.java, Bundle::class.java, Boolean::class.javaPrimitiveType, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {
                    val thisObject = chain.thisObject

                    val dtFromVolumeDownNow = controlsConfig.volumeDownDtTorch
                    if (dtFromVolumeDownNow && args[1] as String == "double_click_volume_down") {
                        args[0] = "turn_on_torch"
                    } else if (controlsConfig.powerDtAction > 1 && doubleTapResons.contains(args[1] as String)) {
                        val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                        GlobalActions.handleAction(mContext, "controls_powerdt", true)
                        skipped = true; result = true; throwable = null
                    }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiShortcutTriggerHelper", lpparam.classLoader, "getDoubleVolumeDownKeyFunction", String::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return if (controlsConfig.volumeDownDtTorch) "launch_camera" else chain.proceed()
            }
        })
        ModuleHelper.findAndHookMethod("com.android.server.input.shortcut.singlekeyrule.VolumeDownKeyRule", lpparam.classLoader, "isEnableLaunchCamera", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return if (controlsConfig.volumeDownDtTorch) true else chain.proceed()
            }
        })
    }

    @JvmStatic
    fun NoFingerprintWakeHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader, "processBackFingerprintDpcenterEvent", KeyEvent::class.java, Boolean::class.javaPrimitiveType, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val isScreenOn = chain.getArg(1) as Boolean
                    if (!isScreenOn) { skipped = true; result = null; throwable = null }

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
    fun AssistGestureActionHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.assist.AssistManager", lpparam.classLoader, "startAssist", Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val bundle = chain.getArg(0) as Bundle?
                    val pos = assistGestureSide(bundle) ?: return chain.proceed()
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    if (GlobalActions.handleAction(mContext, "controls_fsg_assist_$pos", false, bundle)) {
                        HookUtils.performLightVibration(mContext)
                        skipped = true; result = null; throwable = null
                    }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.assist.ui.DefaultUiController", lpparam.classLoader, "logInvocationProgressMetrics", Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun HideImeDismissButtonHook(lpparam: PackageReadyParam) {
        val access = ImeDismissButtonAccess.resolve(lpparam.classLoader) ?: run {
            XposedHelpers.log("HideImeDismissButtonHook: complete native button contract unavailable")
            return
        }
        ModuleHelper.hookMethod(access.update, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? = access.dispatch(chain)
        })
    }

    internal fun assistGestureSide(bundle: Bundle?): String? {
        if (bundle == null || bundle.getInt("triggered_by", 0) != 83 || bundle.getInt("invocation_type", 0) != 1) return null
        return when (bundle.getInt("inDirection", -1)) { 0 -> "left"; 1 -> "right"; else -> null }
    }

    /**
     * Returns true when the back button is in IME-alternate mode and the nav bar is in gestural mode.
     *
     * - bit 0 of navigationIconHints is the BACK_ALT/IME alternate hint.
     * - navBarMode 2 is the fully gestural navigation mode (QuickStepContract.isGesturalMode).
     */
    internal fun shouldHideImeDismissButton(navigationIconHints: Int, navBarMode: Int): Boolean {
        return (navigationIconHints and 0x1) != 0 && navBarMode == 2
    }
}
