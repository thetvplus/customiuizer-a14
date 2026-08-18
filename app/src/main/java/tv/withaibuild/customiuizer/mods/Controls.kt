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
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
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
import java.lang.reflect.Method
import tv.withaibuild.customiuizer.utils.HookUtils

// basePWMContext is the system_server PhoneWindowManager mContext, a per-process
// singleton, not an Activity Context. It is captured once per PhoneWindowManager
// and used by the navigation-key long-press Runnables. There is no owner hand-off
// to manage because the system_server process recreates it only on crash/restart.
@SuppressLint("StaticFieldLeak")
object Controls {

    private const val NAV_BAR_DARK_STATE_FIELD = "custo_nav_bar_buttons_dark"

    private var isPowerPressed = false
    private var isPowerLongPressed = false
    private var isVolumePressed = false
    private var isVolumeLongPressed = false
    private var isWaitingForPowerLongPressed = false
    private var isWaitingForVolumeLongPressed = false
    private var wasRaise2WakeEnabled = false
    private var mHandler: Handler? = null

    internal data class ControlsSnapshot(
        val powerFlashDelay: Boolean = false,
        val volumeMediaUp: Int = 0,
        val volumeMediaDown: Int = 0,
        val volumeCursorApps: Set<String> = emptySet(),
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
            fsgCoverage = prefs.getInt("controls_fsg_coverage", 60),
            fsgWidth = prefs.getInt("controls_fsg_width", 100),
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
    @SuppressLint("MissingPermission")
    fun VolumeMediaButtonsHook(lpparam: SystemServerStartingParam) {
        installControlsSnapshot()
        ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiPhoneWindowManager", lpparam.classLoader, "interceptKeyBeforeQueueing", KeyEvent::class.java, Int::class.javaPrimitiveType, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    // Power and volkeys are pressed at the same time
                    if (isPowerPressed) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                    val keyEvent = chain.getArg(0) as KeyEvent

                    val keycode = keyEvent.keyCode
                    val action = keyEvent.action
                    val flags = keyEvent.flags

                    // Ignore repeated KeyEvents simulated on volume Key Up
                    if ((flags and KeyEvent.FLAG_VIRTUAL_HARD_KEY) == KeyEvent.FLAG_VIRTUAL_HARD_KEY) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                    if ((flags and KeyEvent.FLAG_FROM_SYSTEM) != KeyEvent.FLAG_FROM_SYSTEM || (keycode != KeyEvent.KEYCODE_VOLUME_UP && keycode != KeyEvent.KEYCODE_VOLUME_DOWN)) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }

                    // Volume long press
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mPowerManager = XposedHelpers.getObjectField(thisObject, "mPowerManager") as PowerManager
                    if (mPowerManager.isInteractive) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (action == KeyEvent.ACTION_DOWN) {
                        isVolumePressed = true
                        isVolumeLongPressed = false

                        mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler

                        // Post only one delayed runnable that waits for long press timeout
                        if (mHandler != null && !isWaitingForVolumeLongPressed) {
                            // Runs on the PhoneWindowManager handler inside system_server, outside
                            // this hook's try/catch. An unguarded throw here reboots the device.
                            mHandler!!.postDelayed(Runnable {
                                ModuleHelper.guarded {
                                    if (isVolumePressed && GlobalActions.isMediaActionsAllowed(mContext)) {
                                        isVolumeLongPressed = true
                                        when (keyEvent.keyCode) {
                                            KeyEvent.KEYCODE_VOLUME_UP -> {
                                                val pref_mediaUp = controlsConfig.volumeMediaUp
                                                if (pref_mediaUp != 0) GlobalActions.sendDownUpKeyEvent(mContext, pref_mediaUp, true)
                                            }
                                            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                                                val pref_mediaDown = controlsConfig.volumeMediaDown
                                                if (pref_mediaDown != 0) GlobalActions.sendDownUpKeyEvent(mContext, pref_mediaDown, true)
                                            }
                                        }
                                    }
                                }
                                isVolumePressed = false
                                isWaitingForVolumeLongPressed = false
                            }, ViewConfiguration.getLongPressTimeout().toLong())
                        }

                        isWaitingForVolumeLongPressed = true
                        skipped = true; result = 0; throwable = null
                    }

                    if (action == KeyEvent.ACTION_UP) {
                        isVolumePressed = false
                        // Kill all callbacks (removing only posted Runnable is not working... no idea)
                        mHandler?.removeCallbacksAndMessages(null)
                        if (!isVolumeLongPressed) {
                            val am = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            val tm = mContext.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                            val mBroadcastWakeLock = XposedHelpers.getObjectField(thisObject, "mBroadcastWakeLock") as WakeLock
                            var k = AudioManager.ADJUST_RAISE
                            if (keycode != KeyEvent.KEYCODE_VOLUME_UP) k = AudioManager.ADJUST_LOWER
                            mBroadcastWakeLock.acquire(5000L)
                            // If music stream is playing, adjust its volume
                            if (am.isMusicActive) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, k, 0)
                            // If voice call is active while screen off by proximity sensor, adjust its volume
                            else if (tm.isInCall) am.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, k, 0)
                            // If volume keys to wake option active, wake the device
                            else if (Settings.System.getInt(mContext.contentResolver, "volumekey_wake_screen", 0) == 1)
                                XposedHelpers.callMethod(mPowerManager, "wakeUp", SystemClock.uptimeMillis())
                            if (mBroadcastWakeLock.isHeld) mBroadcastWakeLock.release()
                        }
                        skipped = true; result = 0; throwable = null
                        isWaitingForVolumeLongPressed = false
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
    fun VolumeMediaPlayerHook(lpparam: PackageReadyParam) {
        val MediaPlayerCls = XposedHelpers.findClass("android.media.MediaPlayer", lpparam.classLoader)
        ModuleHelper.findAndHookMethod(MediaPlayerCls, "pause", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val mContext = ModuleHelper.findContext(lpparam)
                    val mStreamType = XposedHelpers.findMethodExact(MediaPlayerCls, "getAudioStreamType", *emptyArray<Any?>()).invoke(thisObject) as Int
                    if (mContext != null && (mStreamType == AudioManager.STREAM_MUSIC || mStreamType == 0x80000000.toInt())) {
                        val intent = Intent(GlobalActions.ACTION_PREFIX + "SaveLastMusicPausedTime")
                        intent.setPackage("com.android.systemui")
                        mContext.sendBroadcast(intent)
                    }

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
    fun VolumeCursorHook(lpparam: PackageReadyParam) {
        installControlsSnapshot()
        ModuleHelper.findAndHookMethod("android.inputmethodservice.InputMethodService", lpparam.classLoader, "onKeyDown", Int::class.javaPrimitiveType, KeyEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val ims = thisObject as InputMethodService
                    val code = chain.getArg(0) as Int
                    if ((code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) && ims.isInputViewShown) {
                        val pkgName = Settings.Global.getString(ims.contentResolver, Helpers.modulePkg + ".foreground.package")
                        if (pkgName != null && pkgName in controlsConfig.volumeCursorApps) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                        val swapDir = controlsConfig.volumeCursorReverse
                        ims.sendDownUpKeyEvents(if (code == (if (swapDir) KeyEvent.KEYCODE_VOLUME_DOWN else KeyEvent.KEYCODE_VOLUME_UP)) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT)
                        skipped = true; result = true; throwable = null
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

        ModuleHelper.findAndHookMethod("android.inputmethodservice.InputMethodService", lpparam.classLoader, "onKeyUp", Int::class.javaPrimitiveType, KeyEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val ims = thisObject as InputMethodService
                    val code = chain.getArg(0) as Int
                    if ((code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) && ims.isInputViewShown) {
                        val pkgName = Settings.Global.getString(ims.contentResolver, Helpers.modulePkg + ".foreground.package")
                        if (pkgName == null || pkgName !in controlsConfig.volumeCursorApps) {
                            skipped = true; result = true; throwable = null
                        }
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
            } catch (ignore: Throwable) {
            }
            return false
        } else {
            return GlobalActions.handleAction(context, key)
        }
    }

    private fun reposNavBarButtons(navbar: FrameLayout) {
        val mContext = navbar.context
        val displayRotation = navbar.context.display!!.rotation
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
            val modCtx = ModuleHelper.getModuleContext(mContext)!!
            val modRes = ModuleHelper.getModuleRes(mContext)!!
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
            } catch (ignore: Throwable) {
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
            } catch (ignore: Throwable) {
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

                    val modCtx = ModuleHelper.getModuleContext(navbar.context)!!
                    val modRes = ModuleHelper.getModuleRes(navbar.context)!!
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

    @SuppressLint("StaticFieldLeak")
    private var basePWMContext: Context? = null
    private var basePWMObject: Any? = null
    private var markShortcutTriggered: Method? = null

    private val mBackLongPressAction = Runnable {
        ModuleHelper.guarded {
            if (basePWMContext == null || basePWMObject == null) return@guarded
            if (GlobalActions.handleAction(basePWMContext!!, "controls_backlong")) HookUtils.performStrongVibration(basePWMContext!!)
            if (controlsConfig.backLongAction != 1) markShortcutTriggered?.invoke(basePWMObject)
        }
    }
    private val mHomeLongPressAction = Runnable {
        ModuleHelper.guarded {
            if (basePWMContext == null || basePWMObject == null) return@guarded
            if (GlobalActions.handleAction(basePWMContext!!, "controls_homelong")) HookUtils.performStrongVibration(basePWMContext!!)
            if (controlsConfig.homeLongAction != 1) markShortcutTriggered?.invoke(basePWMObject)
        }
    }
    private val mMenuLongPressAction = Runnable {
        ModuleHelper.guarded {
            if (basePWMContext == null || basePWMObject == null) return@guarded
            if (GlobalActions.handleAction(basePWMContext!!, "controls_menulong")) HookUtils.performStrongVibration(basePWMContext!!)
            if (controlsConfig.menuLongAction != 1) markShortcutTriggered?.invoke(basePWMObject)
        }
    }

    @JvmStatic
    fun NavBarActionsHook(lpparam: SystemServerStartingParam) {
        installControlsSnapshot()
        ModuleHelper.hookAllMethods("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, "postKeyLongPress", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    if (basePWMObject == null) basePWMObject = thisObject
                    if (basePWMContext == null) basePWMContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    if (markShortcutTriggered == null) markShortcutTriggered = XposedHelpers.findMethodExact("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, "markShortcutTriggered")

                    val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler

                    val key = chain.getArg(0) as Int
                    if (key == KeyEvent.KEYCODE_BACK && controlsConfig.backLongAction > 1) {
                        mHandler.removeCallbacks(mBackLongPressAction)
                        mHandler.postDelayed(mBackLongPressAction, ViewConfiguration.getLongPressTimeout().toLong())
                        skipped = true; result = null; throwable = null
                    } else if (key == KeyEvent.KEYCODE_HOME && controlsConfig.homeLongAction > 1) {
                        mHandler.removeCallbacks(mHomeLongPressAction)
                        mHandler.postDelayed(mHomeLongPressAction, ViewConfiguration.getLongPressTimeout().toLong())
                        skipped = true; result = null; throwable = null
                    } else if (key == KeyEvent.KEYCODE_APP_SWITCH && controlsConfig.menuLongAction > 1) {
                        mHandler.removeCallbacks(mMenuLongPressAction)
                        mHandler.postDelayed(mMenuLongPressAction, ViewConfiguration.getLongPressTimeout().toLong())
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

        ModuleHelper.hookAllMethods("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, "removeKeyLongPress", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val key = chain.getArg(0) as Int
                    val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler
                    if (key == KeyEvent.KEYCODE_BACK)
                        mHandler.removeCallbacks(mBackLongPressAction)
                    else if (key == KeyEvent.KEYCODE_HOME)
                        mHandler.removeCallbacks(mHomeLongPressAction)
                    else if (key == KeyEvent.KEYCODE_APP_SWITCH)
                        mHandler.removeCallbacks(mMenuLongPressAction)

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
        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "getGestureStubWindowParam", object : MethodHook() {
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
                    val lp = result as WindowManager.LayoutParams
                    val pct = controlsConfig.fsgCoverage
                    lp.height = Math.round(lp.height / 60.0f * pct)
                    result = lp; throwable = null
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun BackGestureAreaWidthHook(lpparam: PackageReadyParam) {
        installControlsSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "initScreenSizeAndDensity", Int::class.javaPrimitiveType, object : MethodHook() {
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

                    val pct = controlsConfig.fsgWidth
                    if (pct == 100) { return XposedHelpers.throwOrReturn(throwable, result) }
                    var mGestureStubDefaultSize = XposedHelpers.getIntField(thisObject, "mGestureStubDefaultSize")
                    var mGestureStubSize = XposedHelpers.getIntField(thisObject, "mGestureStubSize")
                    mGestureStubDefaultSize = Math.round(mGestureStubDefaultSize * pct / 100f)
                    mGestureStubSize = Math.round(mGestureStubSize * pct / 100f)
                    XposedHelpers.setIntField(thisObject, "mGestureStubDefaultSize", mGestureStubDefaultSize)
                    XposedHelpers.setIntField(thisObject, "mGestureStubSize", mGestureStubSize)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "setSize", Int::class.javaPrimitiveType, object : MethodHook() {
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
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun HideNavBarHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors("com.android.systemui.recents.OverviewProxyService", lpparam.classLoader, object : MethodHook() {
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

                    val mCallbacks = ModuleHelper.getObjectFieldByPath(thisObject, "mCommandQueue.mCallbacks") as ArrayList<*>
                    val callback = mCallbacks[mCallbacks.size - 1]
                    ModuleHelper.findAndHookMethod(callback.javaClass, "setWindowState", Integer::class.javaPrimitiveType!!, Integer::class.javaPrimitiveType!!, Integer::class.javaPrimitiveType!!, object : MethodHook() {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            var result: Any? = null
                            var throwable: Throwable? = null
                            try {
                                val GestureObserver = ModuleHelper.getDepInstance(lpparam.classLoader, "com.miui.systemui.controller.GestureObserver")
                                XposedHelpers.setObjectField(GestureObserver, "mGestureLineEnable", true)

                                result = chain.proceed()
                            } catch (t: Throwable) {
                                throwable = t
                                result = null
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
        ModuleHelper.hookAllMethods("com.android.systemui.navigationbar.NavigationBarController", lpparam.classLoader, "createNavigationBar", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {
                    if (args.size >= 3) {
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
    }

    @JvmStatic
    fun PowerDoubleTapActionHook(lpparam: SystemServerStartingParam) {
        installControlsSnapshot()
        val dtFromVolumeDown = controlsConfig.volumeDownDtTorch
        val doubleTapResons = arrayListOf("double_click_power", "power_double_tap", "double_click_power_key")
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

        if (dtFromVolumeDown) {
            ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiShortcutTriggerHelper", lpparam.classLoader, "getDoubleVolumeDownKeyFunction", String::class.java, tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.returnConstant("launch_camera"))
            ModuleHelper.findAndHookMethod("com.android.server.input.shortcut.singlekeyrule.VolumeDownKeyRule", lpparam.classLoader, "isEnableLaunchCamera", tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.returnConstant(true))
        }
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
                    if (bundle == null || bundle.getInt("triggered_by", 0) != 83 || bundle.getInt("invocation_type", 0) != 1) { return if (skipped) XposedHelpers.throwOrReturn(throwable, result) else XposedHelpers.proceedOrThrow(chain, throwable) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val pos = if (bundle.getInt("inDirection", 0) == 1) "right" else "left"
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
        val navBarViewClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.navigationbar.NavigationBarView",
            lpparam.classLoader
        ) ?: run {
            XposedHelpers.log("HideImeDismissButtonHook: NavigationBarView not found")
            return
        }

        val backButtonDispatcherClass = XposedHelpers.findClassIfExists(
            "com.android.systemui.navigationbar.buttons.ButtonDispatcher",
            lpparam.classLoader
        ) ?: run {
            XposedHelpers.log("HideImeDismissButtonHook: ButtonDispatcher not found")
            return
        }

        val navigationIconHintsField = XposedHelpers.findFieldIfExists(navBarViewClass, "mNavigationIconHints")
            ?: run {
                XposedHelpers.log("HideImeDismissButtonHook: mNavigationIconHints not found")
                return
            }

        val navBarModeField = XposedHelpers.findFieldIfExists(navBarViewClass, "mNavBarMode")
            ?: run {
                XposedHelpers.log("HideImeDismissButtonHook: mNavBarMode not found")
                return
            }

        val getBackButtonMethod = XposedHelpers.findMethodExactIfExists(
            navBarViewClass,
            "getBackButton"
        ) ?: run {
            XposedHelpers.log("HideImeDismissButtonHook: getBackButton not found")
            return
        }

        val setVisibilityMethod = XposedHelpers.findMethodExactIfExists(
            backButtonDispatcherClass,
            "setVisibility",
            Int::class.javaPrimitiveType
        ) ?: run {
            XposedHelpers.log("HideImeDismissButtonHook: ButtonDispatcher.setVisibility not found")
            return
        }

        ModuleHelper.findAndHookMethod(navBarViewClass, "updateNavButtonIcons", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    throwable = t
                    result = null
                }

                try {
                    val thisObject = chain.thisObject
                    val navigationIconHints = navigationIconHintsField.getInt(thisObject)
                    val navBarMode = navBarModeField.getInt(thisObject)

                    if (shouldHideImeDismissButton(navigationIconHints, navBarMode)) {
                        val backButton = getBackButtonMethod.invoke(thisObject)
                        if (backButton != null) {
                            setVisibilityMethod.invoke(backButton, View.INVISIBLE)
                        }
                    }
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }

                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
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
