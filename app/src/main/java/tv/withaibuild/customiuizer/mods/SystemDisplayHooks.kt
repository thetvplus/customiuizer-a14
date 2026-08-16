package tv.withaibuild.customiuizer.mods

import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Resources
import android.os.Handler
import android.provider.Settings
import android.view.View
import io.github.libxposed.api.XposedInterface
import miui.os.Build
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.HookUtils
import java.lang.ref.WeakReference

/**
 * Screen, brightness and wallpaper hooks.
 * Screen-off animation timing, the auto-brightness range and its reset on screen
 * off, dim timeout, drawer blur, forced dark mode and wallpaper scale.
 */
object SystemDisplayHooks {

    private const val PREF_DISABLE_WINDOW_BLURS = "system_disable_window_blurs"
    private const val DISABLE_WINDOW_BLURS_SETTING = "disable_window_blurs"

    @Volatile
    private var forceWindowBlursDisabled = false

    @JvmStatic
    internal fun resolveWindowBlursDisabled(moduleDisabled: Boolean, systemDisabled: Boolean): Boolean =
        moduleDisabled || systemDisabled

    private fun applyWindowBlurPolicy(controller: Any) {
        val context = XposedHelpers.getObjectField(controller, "mContext") as? Context ?: return
        val systemDisabled = Settings.Global.getInt(
            context.contentResolver,
            DISABLE_WINDOW_BLURS_SETTING,
            0
        ) == 1
        XposedHelpers.setBooleanField(
            controller,
            "mBlurDisabledSetting",
            resolveWindowBlursDisabled(forceWindowBlursDisabled, systemDisabled)
        )
        XposedHelpers.callMethod(controller, "updateBlurEnabled")
    }

    /**
     * Adds a module policy layer to Android 14's existing BlurController.
     *
     * The platform keeps ownership of power-save, thermal, tunnel-mode and developer-setting
     * state. This hook only ORs the module switch into the native disabled state, so turning the
     * module switch off returns control to Android after the preference reaches system_server.
     * Preference changes reuse the project's existing remote-preference channel; no thread,
     * poller or ContentObserver is added.
     */
    @JvmStatic
    fun DisableWindowBlursHook(lpparam: SystemServerStartingParam) {
        forceWindowBlursDisabled = MainModule.mPrefs.getBoolean(PREF_DISABLE_WINDOW_BLURS)

        ModuleHelper.findAndHookMethod(
            "com.android.server.wm.BlurController",
            lpparam.classLoader,
            "getBlurDisabledSetting",
            object : MethodHook() {
                override fun after(callback: HookerClassHelper.AfterHookCallback) {
                    val systemDisabled = callback.getResult() as? Boolean ?: return
                    callback.setResult(resolveWindowBlursDisabled(forceWindowBlursDisabled, systemDisabled))
                }
            }
        )

        ModuleHelper.hookAllConstructors(
            "com.android.server.wm.BlurController",
            lpparam.classLoader,
            object : MethodHook() {
                override fun after(callback: HookerClassHelper.AfterHookCallback) {
                    val controller = callback.getThisObject() ?: return
                    try {
                        applyWindowBlurPolicy(controller)
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log("DisableWindowBlurs", t)
                    }
                    val controllerRef = WeakReference(controller)
                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) = ModuleHelper.guarded {
                            if (key != null && key != PREF_DISABLE_WINDOW_BLURS) return@guarded
                            forceWindowBlursDisabled = MainModule.mPrefs.getBoolean(PREF_DISABLE_WINDOW_BLURS)
                            controllerRef.get()?.let(::applyWindowBlurPolicy)
                        }
                    }, controller)
                }
            }
        )
    }

    @JvmStatic
    fun ScreenAnimHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "initialize", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    try {
                        XposedHelpers.setObjectField(thisObject, "mColorFadeEnabled", true)
                        XposedHelpers.setObjectField(thisObject, "mColorFadeFadesConfig", true)
                    } catch (ignore: Throwable) { FatalErrors.rethrowIfFatal(ignore) }

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                try {

                    val mColorFadeOffAnimator = XposedHelpers.getObjectField(thisObject, "mColorFadeOffAnimator") as ObjectAnimator?
                    if (mColorFadeOffAnimator != null) {
                        var value = MainModule.mPrefs.getInt("system_screenanim_duration", 0)
                        if (value == 0) value = 250
                        mColorFadeOffAnimator.duration = value.toLong()
                    }
                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) = ModuleHelper.guarded {
                            if (key == "system_screenanim_duration") {
                                if (mColorFadeOffAnimator == null) return
                                var value2 = MainModule.mPrefs.getInt("system_screenanim_duration", 0)
                                if (value2 == 0) value2 = 250
                                mColorFadeOffAnimator.duration = value2.toLong()
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun NoLightUpOnChargeHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.power.PowerManagerService", lpparam.classLoader, "wakePowerGroupLocked", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val reason = chain.getArg(3) as String?
                    if (reason == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (
                        reason.startsWith("android.server.power:PLUGGED")
                        || reason == "com.android.systemui:RAPID_CHARGE"
                        || reason == "com.android.systemui:WIRELESS_CHARGE"
                        || reason == "com.android.systemui:WIRELESS_RAPID_CHARGE"
                    ) { skipped = true; result = null; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    /**
     * Per-thread bounded scope for the drawer blur ratio adjustment.
     *
     * [doFrame] enters the scope with the current modifier percentage and the cached
     * [WeakReference] of the target [BlurUtilsExt]; [applyBlur] only adjusts the ratio when
     * it is called on exactly that target instance.  Nested and cross-thread calls are
     * isolated, and [exit] is always called in a `finally` block so the scope (including the
     * target reference) cannot leak after an exception.
     *
     * The State is reused per-thread: exit only resets fields, it does not remove the
     * ThreadLocal entry.  This keeps steady-state doFrame calls allocation-free.
     */
    internal object DrawerBlurScope {
        internal class State(
            var depth: Int = 0,
            var modifier: Int = 100,
            var targetRef: WeakReference<Any>? = null
        )

        private val scope = ThreadLocal.withInitial { State() }

        private inline val current: State
            get() = scope.get()!!

        fun enter(modifierPct: Int, targetRef: WeakReference<Any>?) {
            val s = current
            if (s.depth == 0) {
                s.modifier = modifierPct
                s.targetRef = targetRef
            }
            s.depth++
        }

        fun exit() {
            val s = current
            s.depth--
            if (s.depth <= 0) {
                s.depth = 0
                s.modifier = 100
                s.targetRef = null
            }
        }

        fun isActive(): Boolean = current.depth > 0
        fun getModifier(): Int = current.modifier
        fun getTargetRef(): WeakReference<Any>? = current.targetRef
    }

    internal const val DRAWER_BLUR_TARGET_KEY = "customiuizer_drawer_blur_target"

    private const val PREF_SYSTEM_DRAWER_BLUR = "system_drawer_blur"

    @Volatile
    private var drawerBlurModifierPct = 100

    private var drawerBlurSnapshotInstalled = false

    internal fun refreshDrawerBlurSnapshot() {
        drawerBlurModifierPct = MainModule.mPrefs.getInt(PREF_SYSTEM_DRAWER_BLUR, 100)
    }

    internal fun onDrawerBlurPreferenceChanged(key: String?) {
        if (key == null || key == PREF_SYSTEM_DRAWER_BLUR) {
            refreshDrawerBlurSnapshot()
        }
    }

    private fun installDrawerBlurSnapshot() {
        if (drawerBlurSnapshotInstalled) return
        drawerBlurSnapshotInstalled = true
        refreshDrawerBlurSnapshot()
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                onDrawerBlurPreferenceChanged(key)
            }
        })
    }

    internal fun onDoFrame(chain: XposedInterface.Chain): Any? {
        val targetRef = resolveDrawerBlurTargetRef(chain.getThisObject())
        DrawerBlurScope.enter(drawerBlurModifierPct, targetRef)
        return try {
            chain.proceed()
        } finally {
            DrawerBlurScope.exit()
        }
    }

    internal fun onApplyBlur(chain: XposedInterface.Chain): Any? {
        val args = XposedHelpers.getArgsArray(chain)
        if (DrawerBlurScope.isActive() && DrawerBlurScope.getTargetRef()?.get() === chain.getThisObject()) {
            val ratio = args[1] as Float
            args[1] = ratio * DrawerBlurScope.getModifier() / 100f
        }
        return chain.proceed(args)
    }

    private fun resolveDrawerBlurTargetRef(callback: Any?): WeakReference<Any>? {
        if (callback == null) return null

        val cached = XposedHelpers.getAdditionalInstanceField(callback, DRAWER_BLUR_TARGET_KEY) as? WeakReference<*>
        if (cached != null) {
            @Suppress("UNCHECKED_CAST")
            return if (cached.get() != null) cached as WeakReference<Any> else null
        }

        return try {
            val controller = XposedHelpers.getSurroundingThis(callback)
            val target = findBlurUtilsExt(controller)
            if (target != null) {
                val ref = WeakReference<Any>(target)
                XposedHelpers.setAdditionalInstanceField(callback, DRAWER_BLUR_TARGET_KEY, ref)
                ref
            } else {
                null
            }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    private fun findBlurUtilsExt(controller: Any?): Any? {
        if (controller == null) return null

        // Hot path: common field names used by HyperOS SystemUI.
        for (name in arrayOf("mBlurUtilsExt", "blurUtilsExt", "mBlurUtils", "blurUtils", "mBlur", "blur")) {
            try {
                val value = XposedHelpers.getObjectField(controller, name)
                if (value != null && isBlurUtilsExt(value)) return value
            } catch (ignored: Throwable) {
                FatalErrors.rethrowIfFatal(ignored)
            }
        }

        // Cold path: search for a field whose declared type is BlurUtilsExt.
        val controllerClass = controller.javaClass
        for (field in controllerClass.declaredFields) {
            if (field.type.name == "com.android.systemui.statusbar.policy.BlurUtilsExt") {
                try {
                    field.isAccessible = true
                    val value = field.get(controller)
                    if (value != null) return value
                } catch (ignored: Throwable) {
                FatalErrors.rethrowIfFatal(ignored)
            }
            }
        }

        // Final fallback: search assignable subclasses in the whole hierarchy.
        var searchClass: Class<*>? = controllerClass
        while (searchClass != null) {
            for (field in searchClass.declaredFields) {
                val value = try {
                    field.isAccessible = true
                    field.get(controller)
                } catch (ignored: Throwable) {
                    FatalErrors.rethrowIfFatal(ignored)
                    null
                } ?: continue
                if (isBlurUtilsExt(value)) return value
            }
            searchClass = searchClass.superclass
        }
        return null
    }

    private fun isBlurUtilsExt(value: Any): Boolean {
        return value.javaClass.name == "com.android.systemui.statusbar.policy.BlurUtilsExt" ||
            value.javaClass.name.startsWith("com.android.systemui.statusbar.policy.BlurUtils")
    }

    internal fun onControlPanelSetBlurRatio(chain: XposedInterface.Chain): Any? {
        val args = XposedHelpers.getArgsArray(chain)
        val ratio = args[0] as Float
        args[0] = ratio * drawerBlurModifierPct / 100f
        return chain.proceed(args)
    }

    @JvmStatic
    fun DrawerBlurRatioHook(lpparam: PackageReadyParam) {
        installDrawerBlurSnapshot()

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.NotificationShadeDepthController\$updateBlurCallback\$1", lpparam.classLoader, "doFrame", Long::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onDoFrame(chain)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BlurUtilsExt", lpparam.classLoader, "applyBlur", View::class.java, Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onApplyBlur(chain)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.controlcenter.phone.ControlPanelWindowManager", lpparam.classLoader, "setBlurRatio", Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onControlPanelSetBlurRatio(chain)
            }
        })
    }

    @JvmStatic
    fun ChargeAnimationHook(lpparam: PackageReadyParam) {
        val timeout = MainModule.mPrefs.getInt("system_chargeanimtime", 20) * 1000
        ModuleHelper.findAndHookMethod("com.miui.charge.container.MiuiChargeAnimationView", lpparam.classLoader, "getAnimationDuration", HookerClassHelper.returnConstant(timeout))
    }

    internal var mMaximumBacklight = 0f

    internal var mMinimumBacklight = 0f

    internal var backlightMaxLevel = 0

    private data class AutoBrightnessRangeSnapshot(
        val limitMin: Boolean = false,
        val limitMax: Boolean = false,
        val minValue: Float = 0f,
        val maxValue: Float = 1f,
        val initialized: Boolean = false
    )

    @Volatile
    private var autoBrightnessRangeSnapshot = AutoBrightnessRangeSnapshot()

    private var autoBrightnessRangeObserverInstalled = false

    internal fun refreshAutoBrightnessRangeSnapshot() {
        val limitMin = MainModule.mPrefs.getBoolean("system_autobrightness_limitmin")
        val limitMax = MainModule.mPrefs.getBoolean("system_autobrightness_limitmax")
        val minPct = MainModule.mPrefs.getInt("system_autobrightness_min", 25).coerceIn(0, 100)
        val maxPct = MainModule.mPrefs.getInt("system_autobrightness_max", 75).coerceIn(0, 100)

        if (backlightMaxLevel <= 0 || mMaximumBacklight <= mMinimumBacklight || (limitMin && limitMax && minPct >= maxPct)) {
            autoBrightnessRangeSnapshot = AutoBrightnessRangeSnapshot(
                limitMin = limitMin,
                limitMax = limitMax,
                initialized = false
            )
            return
        }

        val min = HookUtils.convertGammaToLinearFloat(minPct / 100f * backlightMaxLevel, backlightMaxLevel, mMinimumBacklight, mMaximumBacklight)
        val max = HookUtils.convertGammaToLinearFloat(maxPct / 100f * backlightMaxLevel, backlightMaxLevel, mMinimumBacklight, mMaximumBacklight)

        if (!min.isFinite() || !max.isFinite() || (limitMin && limitMax && min >= max)) {
            autoBrightnessRangeSnapshot = AutoBrightnessRangeSnapshot(
                limitMin = limitMin,
                limitMax = limitMax,
                initialized = false
            )
            return
        }

        autoBrightnessRangeSnapshot = AutoBrightnessRangeSnapshot(
            limitMin = limitMin,
            limitMax = limitMax,
            minValue = min,
            maxValue = max,
            initialized = true
        )
    }

    internal fun onAutoBrightnessRangePreferenceChanged(key: String?) {
        if (key == null ||
            key == "system_autobrightness_limitmin" ||
            key == "system_autobrightness_limitmax" ||
            key == "system_autobrightness_min" ||
            key == "system_autobrightness_max") {
            refreshAutoBrightnessRangeSnapshot()
        }
    }

    private fun installAutoBrightnessRangeSnapshot() {
        if (autoBrightnessRangeObserverInstalled) return
        autoBrightnessRangeObserverInstalled = true
        refreshAutoBrightnessRangeSnapshot()
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                onAutoBrightnessRangePreferenceChanged(key)
            }
        })
    }

    internal fun constrainValue(value: Float): Float {
        val snap = autoBrightnessRangeSnapshot
        if (!snap.initialized || value < 0) return value

        var newVal = value
        if (newVal < 0) newVal = 0f
        if (newVal > 1) newVal = 1f

        if (snap.limitMin && newVal < snap.minValue) newVal = snap.minValue
        if (snap.limitMax && newVal > snap.maxValue) newVal = snap.maxValue
        return newVal
    }

    internal fun interceptClampScreenBrightness(chain: XposedInterface.Chain): Any? {
        var result: Any? = null
        var throwable: Throwable? = null
        try {
            result = chain.proceed()
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            throwable = t
            result = null
        }
        try {
            val value = result as Float
            if (value >= 0) {
                result = constrainValue(value)
                throwable = null
            }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
        }
        return XposedHelpers.throwOrReturn(throwable, result)
    }

    internal fun interceptGetDarkModeAppList(chain: XposedInterface.Chain): Any? {
        return withTemporaryStaticBoolean(Build::class.java, "IS_INTERNATIONAL_BUILD", true, chain)
    }

    internal fun withTemporaryStaticBoolean(
        clazz: Class<*>,
        fieldName: String,
        temporaryValue: Boolean,
        chain: XposedInterface.Chain
    ): Any? {
        var result: Any? = null
        var throwable: Throwable? = null

        try {
            XposedHelpers.setStaticBooleanField(clazz, fieldName, temporaryValue)
        } catch (t: Throwable) {
            throwable = t
        }

        if (throwable == null) {
            try {
                result = chain.proceed()
            } catch (t: Throwable) {
                throwable = t
                result = null
            }
        }

        try {
            XposedHelpers.setStaticBooleanField(clazz, fieldName, !temporaryValue)
        } catch (t: Throwable) {
            if (throwable == null) {
                throwable = t
                result = null
            } else {
                throwable.addSuppressed(t)
            }
        }

        return XposedHelpers.throwOrReturn(throwable, result)
    }

    @JvmStatic
    fun AutoBrightnessRangeHook(lpparam: SystemServerStartingParam) {
        installAutoBrightnessRangeSnapshot()

        ModuleHelper.findAndHookMethod("com.android.server.display.AutomaticBrightnessController", lpparam.classLoader, "clampScreenBrightness", Float::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? = interceptClampScreenBrightness(chain)
        })

        ModuleHelper.hookAllConstructors("com.android.server.display.AutomaticBrightnessController", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    XposedHelpers.setLongField(thisObject, "mBrighteningLightDebounceConfig", 1000L)
                    XposedHelpers.setLongField(thisObject, "mDarkeningLightDebounceConfig", 1200L)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "clampScreenBrightness", Float::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? = interceptClampScreenBrightness(chain)
        })

        ModuleHelper.hookAllConstructors("com.android.server.display.DisplayPowerController", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val res = Resources.getSystem()
                    val minBrightnessLevel = res.getInteger(HookUtils.getResId(res, "config_screenBrightnessSettingMinimum", "integer", "android"))
                    val maxBrightnessLevel = res.getInteger(HookUtils.getResId(res, "config_screenBrightnessSettingMaximum", "integer", "android"))
                    val backlightBit = res.getInteger(HookUtils.getResId(res, "config_backlightBit", "integer", "android.miui"))
                    backlightMaxLevel = (1 shl backlightBit) - 1
                    mMinimumBacklight = (minBrightnessLevel - 1) * 1.0f / (backlightMaxLevel - 1)
                    mMaximumBacklight = (maxBrightnessLevel - 1) * 1.0f / (backlightMaxLevel - 1)
                    refreshAutoBrightnessRangeSnapshot()

                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun AutoBrightnessAfterScreenOffHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.display.DisplayPowerController", lpparam.classLoader, "setScreenState", Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            var stateChanged = false
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.thisObject
                try {

                    val state = args[0] as Int
                    val reportOnly = args[1] as Boolean
                    val mUseAutoBrightness = XposedHelpers.getBooleanField(thisObject, "mUseAutoBrightness")
                    if (state == 1 && mUseAutoBrightness && !reportOnly) {
                        val mPowerState = XposedHelpers.getObjectField(thisObject, "mPowerState")
                        val mScreenState = XposedHelpers.getIntField(mPowerState, "mScreenState")
                        stateChanged = state != mScreenState
                    } else {
                        stateChanged = false
                    }

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                try {

                    if (stateChanged) {
                        val readyToUpdateDisplayState = XposedHelpers.callMethod(thisObject, "readyToUpdateDisplayState") as Boolean
                        if (readyToUpdateDisplayState) {
                            val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler
                            val msg = mHandler.obtainMessage(255)
                            mHandler.sendMessage(msg)
                        }
                    }

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ScreenDimTimeHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.power.PowerManagerService", lpparam.classLoader, "readConfigurationLocked", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val opt = MainModule.mPrefs.getInt("system_dimtime", 0) / 100f
                    XposedHelpers.setIntField(thisObject, "mMaximumScreenDimDurationConfig", 600000)
                    XposedHelpers.setFloatField(thisObject, "mMaximumScreenDimRatioConfig", opt)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ForceDarkAllAppsHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllMethods("com.android.server.ForceDarkAppListProvider", lpparam.classLoader, "fillDarkModeAppSettingsInfo", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                try {

                    XposedHelpers.callMethod(chain.getArg(0), "setShowInSettings", true)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        if (!Build.IS_INTERNATIONAL_BUILD) {
            ModuleHelper.findAndHookMethod("com.android.server.ForceDarkAppListManager", lpparam.classLoader, "getDarkModeAppList", Long::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? = interceptGetDarkModeAppList(chain)
            })
        }
        ModuleHelper.findAndHookMethod("com.android.server.ForceDarkAppListManager", lpparam.classLoader, "shouldShowInSettings", ApplicationInfo::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    if (chain.getArg(0) == null) {
                        return XposedHelpers.throwOrReturn(null, false)
                    }
                    val applicationInfo = chain.getArg(0) as ApplicationInfo
                    val flags = applicationInfo.flags
                    val systemApp = (flags and 1) != 0 || (flags and 128) != 0 || applicationInfo.uid < 10000
                    skipped = true; result = !systemApp; throwable = null

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun WallpaperScaleLevelHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.hookAllConstructors("com.android.server.wm.WallpaperController", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val scale = MainModule.mPrefs.getInt("system_other_wallpaper_scale", 6) / 10.0f
                    XposedHelpers.setObjectField(thisObject, "mMaxWallpaperScale", scale)
                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) = ModuleHelper.guarded {
                            if (key == "system_other_wallpaper_scale") {
                                val value = MainModule.mPrefs.getInt("system_other_wallpaper_scale", 6)
                                XposedHelpers.setObjectField(thisObject, "mMaxWallpaperScale", value / 10.0f)
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

}
