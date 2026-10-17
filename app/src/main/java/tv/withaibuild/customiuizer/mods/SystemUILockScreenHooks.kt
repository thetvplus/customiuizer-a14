package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.os.Bundle
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.view.VelocityTracker
import android.view.View
import android.widget.ImageView
import androidx.core.content.res.ResourcesCompat
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.LockScreenAlbumArtController
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefPair
import java.lang.ref.WeakReference
import java.util.ArrayList

/**
 * Lock screen surface hooks.
 * Album art background, the bottom shortcuts and their launch path, top margin,
 * and the keyguard editor and zen mode entry points.
 */
object SystemUILockScreenHooks {

    private const val PREF_SWIPE_RIGHT_OFF = "system_lockscreenshortcuts_right_off"
    private const val PREF_SWIPE_LEFT_OFF = "system_lockscreenshortcuts_left_off"

    /**
     * Swipe suppression flags for `KeyguardMoveHelper.setTranslation`.
     *
     * setTranslation runs for every touch sample of a keyguard swipe, so it must not reach the
     * preference map. These flags are published on the cold install path and refreshed only when
     * the preference actually changes.
     */
    @Volatile
    internal var swipeRightOff = false

    @Volatile
    internal var swipeLeftOff = false

    @Volatile
    private var swipeSuppressionObserverRegistered = false

    internal data class LockScreenConfigSnapshot(
        val albumArtBlur: Int = 0,
        val albumArtScale: Int = 1,
        val albumArtGray: Boolean = false,
        val leftTapAction: Boolean = false,
        val leftOff: Boolean = false,
        val rightAction: Int = 1,
        val rightOff: Boolean = false,
        val calendarAppUser: Int = 0,
        val calendarApp: String = "",
        val clockAppUser: Int = 0,
        val clockApp: String = "",
        val shortcutAppUser: Int = 0,
        val shortcutApp: String = "",
    )

    @Volatile
    internal var lockScreenConfig = LockScreenConfigSnapshot()

    private var lockScreenConfigObserverRegistered = false

    private val LOCK_SCREEN_CONFIG_KEYS = setOf(
        "system_albumartonlock_blur",
        "system_albumartonlock_scale",
        "system_albumartonlock_gray",
        "system_lockscreenshortcuts_left_tapaction",
        "system_lockscreenshortcuts_left_off",
        "system_lockscreenshortcuts_right_action",
        "system_lockscreenshortcuts_right_off",
        "system_calendar_app_user",
        "system_calendar_app",
        "system_clock_app_user",
        "system_clock_app",
        "system_shortcut_app_user",
        "system_shortcut_app",
    )

    internal fun refreshLockScreenConfig() {
        val prefs = MainModule.mPrefs
        lockScreenConfig = LockScreenConfigSnapshot(
            albumArtBlur = prefs.getInt("system_albumartonlock_blur", 0),
            albumArtScale = prefs.getStringAsInt("system_albumartonlock_scale", 1),
            albumArtGray = prefs.getBoolean("system_albumartonlock_gray"),
            leftTapAction = prefs.getBoolean("system_lockscreenshortcuts_left_tapaction"),
            leftOff = prefs.getBoolean("system_lockscreenshortcuts_left_off"),
            rightAction = prefs.getInt("system_lockscreenshortcuts_right_action", 1),
            rightOff = prefs.getBoolean("system_lockscreenshortcuts_right_off"),
            calendarAppUser = prefs.getInt("system_calendar_app_user", 0),
            calendarApp = prefs.getString("system_calendar_app", ""),
            clockAppUser = prefs.getInt("system_clock_app_user", 0),
            clockApp = prefs.getString("system_clock_app", ""),
            shortcutAppUser = prefs.getInt("system_shortcut_app_user", 0),
            shortcutApp = prefs.getString("system_shortcut_app", ""),
        )
    }

    @JvmStatic
    internal fun installLockScreenConfigSnapshot() {
        refreshLockScreenConfig()
        if (lockScreenConfigObserverRegistered) return
        lockScreenConfigObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in LOCK_SCREEN_CONFIG_KEYS) refreshLockScreenConfig()
            }
        })
    }

    internal fun refreshSwipeSuppression() {
        swipeRightOff = MainModule.mPrefs.getBoolean(PREF_SWIPE_RIGHT_OFF)
        swipeLeftOff = MainModule.mPrefs.getBoolean(PREF_SWIPE_LEFT_OFF)
    }

    internal fun onSwipeSuppressionPreferenceChanged(key: String?) {
        if (key == null || key == PREF_SWIPE_RIGHT_OFF || key == PREF_SWIPE_LEFT_OFF) {
            refreshSwipeSuppression()
        }
    }

    @JvmStatic
    internal fun installSwipeSuppressionSnapshot() {
        refreshSwipeSuppression()
        if (swipeSuppressionObserverRegistered) return
        swipeSuppressionObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                onSwipeSuppressionPreferenceChanged(key)
            }
        })
    }

    @JvmStatic
    fun LockScreenTopMarginHook(lpparam: PackageReadyParam) {
        val statusBarPaddingTop = IntArray(1)
        ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIApplication", lpparam.classLoader, "onCreate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mContext = XposedHelpers.callMethod(param.getThisObject(), "getApplicationContext") as Context
                val dimenResId = mContext.resources.getIdentifier("status_bar_padding_top", "dimen", lpparam.packageName)
                statusBarPaddingTop[0] = mContext.resources.getDimensionPixelSize(dimenResId)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "updateViewStatusBarPaddingTop", View::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val view = param.getArg(0) as View?
                if (view != null) {
                    view.setPadding(view.paddingLeft, statusBarPaddingTop[0], view.paddingRight, view.paddingBottom)
                    param.returnAndSkip(null)
                }
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                XposedHelpers.callMethod(param.getThisObject(), "onDensityOrFontScaleChanged")
            }
        })
    }

    private var lockScreenAlbumArtController: WeakReference<Any>? = null

    private var lockScreenAlbumArtReceiverRegistered = false

    private val lockScreenAlbumArtReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
            if (intent.action != GlobalActions.EVENT_PREFIX + "UPDATE_LS_ALBUM_ART") return@guarded
            val controller = lockScreenAlbumArtController?.get() ?: return@guarded
            XposedHelpers.callMethod(controller, "updateThemeBackgroundVisibility")
        }
    }

    private fun registerLockScreenAlbumArtReceiver(context: Context, controller: Any) {
        lockScreenAlbumArtController = WeakReference(controller)
        if (lockScreenAlbumArtReceiverRegistered) return
        ModuleHelper.registerModuleReceiver(
            context.applicationContext,
            "lockScreenAlbumArtReceiver",
            lockScreenAlbumArtReceiver,
            IntentFilter(GlobalActions.EVENT_PREFIX + "UPDATE_LS_ALBUM_ART"),
            Context.RECEIVER_NOT_EXPORTED
        )
        lockScreenAlbumArtReceiverRegistered = true
    }

    @JvmStatic
    fun LockScreenAlbumArtHook(lpparam: PackageReadyParam) {
        installLockScreenConfigSnapshot()
        val MiuiThemeUtilsClass = XposedHelpers.findClassIfExists("com.android.keyguard.utils.MiuiKeyguardUtils", lpparam.classLoader)
        LockScreenAlbumArtController.setMiuiThemeUtilsClass(MiuiThemeUtilsClass)

        val panelClassNames = arrayOf(
            "com.android.systemui.statusbar.phone.MiuiNotificationPanelViewController",
            "com.android.systemui.shade.MiuiNotificationPanelViewController"
        )

        for (panelClassName in panelClassNames) {
            val panelClass = XposedHelpers.findClassIfExists(panelClassName, lpparam.classLoader) ?: continue
            val screenStates = booleanArrayOf(false) // isAod

            ModuleHelper.hookAllConstructors(panelClass, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    try {
                        if (!isDefaultLockScreenTheme(MiuiThemeUtilsClass)) return
                        val mBlurRatioChangedListener = XposedHelpers.getObjectField(param.getThisObject(), "mBlurRatioChangedListener")
                        val notificationShadeDepthController = XposedHelpers.getObjectField(param.getThisObject(), "mDepthController")
                        val listeners = XposedHelpers.getObjectField(notificationShadeDepthController, "listeners") as ArrayList<Any>
                        listeners.remove(mBlurRatioChangedListener)
                        val view = XposedHelpers.getObjectField(param.getThisObject(), "mThemeBackgroundView") as View
                        view.alpha = 1.0f
                        registerLockScreenAlbumArtReceiver(view.context, param.getThisObject() ?: return)
                    } catch (t: Throwable) {
                        FatalErrors.rethrowIfFatal(t)
                        XposedHelpers.log(t)
                    }
                }
            })

            val updateLockscreenHook = object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    try {
                        if (!isDefaultLockScreenTheme(MiuiThemeUtilsClass)) return
                        val view = XposedHelpers.getObjectField(param.getThisObject(), "mThemeBackgroundView") as View
                        val isOnShade = XposedHelpers.callMethod(param.getThisObject(), "isOnShade") as Boolean
                        if (isOnShade || screenStates[0]) {
                            view.visibility = View.GONE
                        } else {
                            val applied = LockScreenAlbumArtController.applyTo(view)
                            view.visibility = if (applied) View.VISIBLE else View.GONE
                        }
                        param.returnAndSkip(null)
                    } catch (t: Throwable) {
                        FatalErrors.rethrowIfFatal(t)
                        XposedHelpers.log(t)
                    }
                }
            }
            ModuleHelper.findAndHookMethodSilently(panelClass, "updateThemeBackground", updateLockscreenHook)
            ModuleHelper.findAndHookMethodSilently(panelClass, "updateThemeBackgroundVisibility", updateLockscreenHook)

            ModuleHelper.findAndHookMethodSilently(panelClass, "linkageViewAnim", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    try {
                        val screenOn = param.getArgs()[0] as Boolean
                        screenStates[0] = !screenOn
                        LockScreenAlbumArtController.setAod(!screenOn)
                        XposedHelpers.callMethod(param.getThisObject(), "updateThemeBackgroundVisibility")
                    } catch (t: Throwable) {
                        FatalErrors.rethrowIfFatal(t)
                        XposedHelpers.log(t)
                    }
                }
            })
        }

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.NotificationMediaManager", lpparam.classLoader, "updateMediaMetaData", Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                try {
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    if (!isDefaultLockScreenTheme(MiuiThemeUtilsClass)) {
                        XposedHelpers.setAdditionalStaticField(MiuiThemeUtilsClass, "mAlbumArtSource", null)
                        XposedHelpers.setAdditionalStaticField(MiuiThemeUtilsClass, "mAlbumArt", null)
                        // The two fields above are only half of what is held: the controller
                        // still has the source and a cache of full-screen frames it can no
                        // longer draw on this theme.
                        LockScreenAlbumArtController.clear()
                        return
                    }
                    val mMediaMetadata = XposedHelpers.getObjectField(param.getThisObject(), "mMediaMetadata") as MediaMetadata?
                    var art: Bitmap? = null
                    if (mMediaMetadata != null) {
                        art = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                        if (art == null) art = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        if (art == null) art = mMediaMetadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    }
                    val config = lockScreenConfig
                    LockScreenAlbumArtController.updateMediaMetaData(
                        mContext,
                        art,
                        config.albumArtBlur,
                        config.albumArtScale,
                        config.albumArtGray,
                    )
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
            }
        })

        ModuleHelper.findAndHookMethodSilently("com.android.systemui.statusbar.NotificationMediaManager", lpparam.classLoader, "clearCurrentMediaNotification", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                try {
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    if (isDefaultLockScreenTheme(MiuiThemeUtilsClass)) {
                        LockScreenAlbumArtController.updateMediaMetaData(mContext, null, 0, 1, false)
                    }
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
            }
        })
    }

    private fun isDefaultLockScreenTheme(cls: Class<*>?): Boolean {
        if (cls == null) return false
        return try {
            XposedHelpers.callStaticMethod(cls, "isDefaultLockScreenTheme") as Boolean
        } catch (ignored: Throwable) {
            FatalErrors.rethrowIfFatal(ignored)
            try {
                XposedHelpers.callStaticMethod(cls, "isDefaultKeyguardNotTheme") as Boolean
            } catch (ignored2: Throwable) {
                FatalErrors.rethrowIfFatal(ignored2)
                false
            }
        }
    }

    @JvmStatic
    fun LockScreenShortcutHook(lpparam: PackageReadyParam) {
        installLockScreenConfigSnapshot()
        ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateLeftIcon", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisObject = param.getThisObject()
                val mLeftButton = XposedHelpers.getObjectField(thisObject, "mLeftButton") as ImageView?
                if (mLeftButton == null) return
                val config = lockScreenConfig
                if (config.leftTapAction) {
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mDarkMode = XposedHelpers.getBooleanField(thisObject, "mBottomIconRectIsDeep")
                    val iconImg = if (mDarkMode) R.drawable.keyguard_bottom_flashlight_img_light else R.drawable.keyguard_bottom_flashlight_img_dark
                    val iconDrawable = ResourcesCompat.getDrawable(ModuleHelper.getModuleRes(mContext), iconImg, mContext.theme)
                    XposedHelpers.callMethod(mLeftButton, "setImageDrawable", iconDrawable, false)
                    val mFlashlightController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.policy.FlashlightController")
                    val isOn = XposedHelpers.callMethod(mFlashlightController, "isEnabled") as Boolean
                    XposedHelpers.callMethod(mLeftButton, "setCircleRadiusWithoutAnimation", if (isOn) 66f else 0f)
                } else if (config.leftOff) {
                    mLeftButton.visibility = View.GONE
                }
            }
        })
        if (lockScreenConfig.leftTapAction) {
            ModuleHelper.hookAllConstructors("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val resolver = mContext.contentResolver
                    // Handler() without a Looper binds to whichever thread ran the constructor
                    // hook and throws outright when that thread has no Looper.
                    val torchObserver = object : ContentObserver(Handler(mContext.mainLooper)) {
                        override fun onChange(selfChange: Boolean) = ModuleHelper.guarded {
                            if (selfChange) return@guarded
                            XposedHelpers.callMethod(param.getThisObject(), "updateLeftIcon")
                        }
                    }
                    resolver.registerContentObserver(Settings.Global.getUriFor("torch_state"), false, torchObserver)
                    ModuleHelper.replaceModuleRegistration("keyguardTorchObserver") {
                        resolver.unregisterContentObserver(torchObserver)
                    }
                }
            })
        }

        val updateRightButtonHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisObject = param.getThisObject()
                val mRightButton = XposedHelpers.getObjectField(thisObject, "mRightButton") as ImageView?
                if (mRightButton == null) return
                val config = lockScreenConfig
                if (config.rightAction > 1) {
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    val mDarkMode = XposedHelpers.getBooleanField(thisObject, "mBottomIconRectIsDeep")
                    val iconImg = if (mDarkMode) R.drawable.keyguard_bottom_miuizer_img_dark else R.drawable.keyguard_bottom_miuizer_img_light
                    val iconDrawable = ResourcesCompat.getDrawable(ModuleHelper.getModuleRes(mContext), iconImg, mContext.theme)
                    mRightButton.setImageDrawable(iconDrawable)
                } else if (config.rightOff) {
                    mRightButton.visibility = View.GONE
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateRightIcon", updateRightButtonHook)
        ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateRightAffordanceViewLayoutVisibility", updateRightButtonHook)

        val leftAction = lockScreenConfig.leftTapAction
        val rightAction = lockScreenConfig.rightAction > 1

        if (leftAction || rightAction) {
            ModuleHelper.findAndHookMethod("com.android.keyguard.injector.KeyguardBottomAreaInjector", lpparam.classLoader, "updateIcons", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mLeftButton = XposedHelpers.getObjectField(param.getThisObject(), "mLeftButton") as View?
                    if (mLeftButton == null) {
                        return
                    }
                    if (leftAction) {
                        mLeftButton.setOnLongClickListener { _: View ->
                            ModuleHelper.guarded {
                                val mFlashlightController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.policy.FlashlightController")
                                val z = !(XposedHelpers.callMethod(mFlashlightController, "isEnabled") as Boolean)
                                XposedHelpers.callMethod(mFlashlightController, "setFlashlight", z)
                            }
                            true
                        }
                        mLeftButton.setOnClickListener(null)
                    }
                    if (rightAction) {
                        val mRightButton = XposedHelpers.getObjectField(param.getThisObject(), "mRightButton") as View
                        mRightButton.setOnLongClickListener { v: View ->
                            ModuleHelper.guarded {
                                GlobalActions.handleAction(v.context, "system_lockscreenshortcuts_right", true)
                            }
                            true
                        }
                        mRightButton.setOnClickListener(null)
                    }
                }
            })
        }

        installSwipeSuppressionSnapshot()

        ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardMoveHelper", lpparam.classLoader, "setTranslation", Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val rightOff = swipeRightOff
                val leftOff = swipeLeftOff
                onKeyguardMoveHelperSetTranslationBefore(param, rightOff, leftOff)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.keyguard.KeyguardMoveHelper", lpparam.classLoader, "endMotion", Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                onKeyguardMoveHelperEndMotionBefore(param)
            }
        })

        ModuleHelper.hookAllMethods("com.android.keyguard.KeyguardMoveRightController", lpparam.classLoader, "onTouchDown", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                onKeyguardMoveRightControllerOnTouchDownBefore(param)
            }
        })

        ModuleHelper.hookAllMethods("com.android.keyguard.KeyguardMoveRightController", lpparam.classLoader, "onTouchMove", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                onKeyguardMoveRightControllerOnTouchMoveBefore(param)
            }
        })
    }

    /**
     * Swipe suppression callbacks. These are installed once and remain permanent;
     * live preference changes are gated by the volatile [swipeRightOff]/[swipeLeftOff]
     * snapshots instead of dynamic hook install/uninstall.
     */
    internal fun onKeyguardMoveHelperSetTranslationBefore(
        param: BeforeHookCallback,
        rightOff: Boolean = swipeRightOff,
        leftOff: Boolean = swipeLeftOff
    ) {
        if (!rightOff && !leftOff) return
        val mCurrentScreen = XposedHelpers.getIntField(param.getThisObject(), "mCurrentScreen")
        if (mCurrentScreen != 1) return
        val translation = param.getArgs()[0] as Float
        if ((translation < 0 && rightOff) || (translation > 0 && leftOff)) {
            param.getArgs()[0] = 0.0f
        }
    }

    internal fun onKeyguardMoveHelperEndMotionBefore(param: BeforeHookCallback) {
        if (!swipeRightOff) return
        val mCurrentScreen = XposedHelpers.getIntField(param.getThisObject(), "mCurrentScreen")
        if (mCurrentScreen != 1) return
        val mTranslation = XposedHelpers.getFloatField(param.getThisObject(), "mTranslation")
        val velocityTracker = XposedHelpers.getObjectField(param.getThisObject(), "mVelocityTracker") as VelocityTracker?
        val xVelocity: Float = if (velocityTracker == null) {
            0.0f
        } else {
            velocityTracker.computeCurrentVelocity(1000)
            velocityTracker.xVelocity
        }
        if (xVelocity * mTranslation < 0.01f) {
            param.returnAndSkip(null)
        }
    }

    internal fun onKeyguardMoveRightControllerOnTouchDownBefore(param: BeforeHookCallback) {
        if (!swipeRightOff) return
        param.returnAndSkip(null)
    }

    internal fun onKeyguardMoveRightControllerOnTouchMoveBefore(param: BeforeHookCallback) {
        if (!swipeRightOff) return
        param.returnAndSkip(true)
    }

    @JvmStatic
    fun LockScreenSecureLaunchHook() {
        ModuleHelper.findAndHookMethod(Activity::class.java, "onCreate", Bundle::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val act = param.getThisObject() as Activity? ?: return
                val intent = act.intent
                if (intent == null) return
                val mFromSecureKeyguard = intent.getBooleanExtra("StartActivityWhenLocked", false)
                var mStartedFromLockScreen = false
                try {
                    mStartedFromLockScreen = XposedHelpers.getAdditionalInstanceField(act.application, "wasStartedFromLockScreen") as Boolean
                } catch (ignore: Throwable) {
                    FatalErrors.rethrowIfFatal(ignore)
                }
                if (mFromSecureKeyguard || mStartedFromLockScreen) {
                    XposedHelpers.setAdditionalInstanceField(act.application, "wasStartedFromLockScreen", true)
                    act.setShowWhenLocked(true)
                    act.setInheritShowWhenLocked(true)
                }
            }
        })
    }

    @JvmStatic
    fun ReplaceShortcutAppHook(lpparam: PackageReadyParam) {
        installLockScreenConfigSnapshot()
        val openAppHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mContext = ModuleHelper.findContext(lpparam)
                var user = 0
                var pkgAppName = ""
                val config = lockScreenConfig
                when (param.getMember().name) {
                    "startCalendarApp" -> {
                        user = config.calendarAppUser
                        pkgAppName = config.calendarApp
                    }
                    "startClockApp" -> {
                        user = config.clockAppUser
                        pkgAppName = config.clockApp
                    }
                    "startSettingsApp" -> {
                        user = config.shortcutAppUser
                        pkgAppName = config.shortcutApp
                    }
                }
                if (pkgAppName.isNotEmpty()) {
                    val pkgAppArray = pkgAppName.split(PrefPair.DELIMITER)
                    if (pkgAppArray.size < 2) return

                    val name = ComponentName(pkgAppArray[0], pkgAppArray[1])
                    val intent = Intent(Intent.ACTION_MAIN)
                    intent.addCategory(Intent.CATEGORY_LAUNCHER)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    intent.component = name
                    if (user != 0) {
                        try {
                            val mStatusBar = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.CentralSurfaces")
                            XposedHelpers.callMethod(mStatusBar, "collapsePanels")
                            XposedHelpers.callMethod(mContext, "startActivityAsUser", intent, XposedHelpers.newInstance(UserHandle::class.java, user))
                        } catch (t: Throwable) {
                            FatalErrors.rethrowIfFatal(t)
                            XposedHelpers.log(t)
                        }
                    } else {
                        val activityStarter = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.plugins.ActivityStarter")
                        XposedHelpers.callMethod(activityStarter, "startActivity", intent, true)
                    }
                    param.returnAndSkip(null)
                }
            }
        }
        val config = lockScreenConfig
        if (config.shortcutApp.isNotEmpty()) {
            ModuleHelper.findAndHookMethod("com.miui.systemui.util.CommonUtil", lpparam.classLoader, "startSettingsApp", openAppHook)
        }
        if (config.calendarApp.isNotEmpty()) {
            ModuleHelper.findAndHookMethod("com.miui.systemui.util.CommonUtil", lpparam.classLoader, "startCalendarApp", Context::class.java, openAppHook)
        }
        if (config.clockApp.isNotEmpty()) {
            ModuleHelper.findAndHookMethod("com.miui.systemui.util.CommonUtil", lpparam.classLoader, "startClockApp", openAppHook)
        }
    }

    @JvmStatic
    fun DisableKeyguardEditorHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors("com.android.keyguard.KeyguardEditorHelper", lpparam.classLoader, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mMiuiKeyguardUpdateMonitorCallback = XposedHelpers.getObjectField(param.getThisObject(), "mMiuiKeyguardUpdateMonitorCallback")
                val keyguardUpdateMonitorInjector = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.keyguard.injector.KeyguardUpdateMonitorInjector")
                XposedHelpers.callMethod(keyguardUpdateMonitorInjector, "removeCallback", mMiuiKeyguardUpdateMonitorCallback)
                XposedHelpers.setObjectField(param.getThisObject(), "mIsMagazinePreViewVisibility", true)
            }
        })
    }

    @JvmStatic
    fun HideLockscreenZenModeHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.notification.zen.ZenModeViewController", lpparam.classLoader, "updateVisibility", object : MethodHook() {
            private var manuallyDismissed = false
            override fun before(param: BeforeHookCallback) {
                manuallyDismissed = XposedHelpers.getBooleanField(param.getThisObject(), "manuallyDismissed")
                XposedHelpers.setObjectField(param.getThisObject(), "manuallyDismissed", true)
            }
            override fun after(param: AfterHookCallback) {
                XposedHelpers.setObjectField(param.getThisObject(), "manuallyDismissed", manuallyDismissed)
            }
        })
    }

}
