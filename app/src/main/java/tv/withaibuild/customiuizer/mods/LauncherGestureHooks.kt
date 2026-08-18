package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ShakeManager
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Home screen gesture hooks.
 * Swipes on the workspace and hotseat, shake, full-screen gestures, double tap,
 * pinch, and the assistant gesture.
 */
object LauncherGestureHooks {

    private const val HOTSEAT_HORIZONTAL_DETECTOR_KEY = "customiuizer_hotseat_horizontal_detector"

    /**
     * Factory for the HotSeats horizontal swipe detector.
     *
     * In production this builds a [GestureDetector] bound to a specific HotSeats owner. Tests
     * can replace the factory to avoid real Android object construction.
     */
    @JvmField
    internal var hotSeatDetectorFactory: (Any, Context?) -> Any = { owner, context ->
        GestureDetector(context, SwipeListenerHorizontal(owner))
    }

    internal fun obtainHotSeatDetector(hotSeat: Any): Any {
        val existing = XposedHelpers.getAdditionalInstanceField(hotSeat, HOTSEAT_HORIZONTAL_DETECTOR_KEY)
        if (existing != null) return existing

        val context = (hotSeat as? View)?.context?.applicationContext
        val detector = hotSeatDetectorFactory(hotSeat, context)
        XposedHelpers.setAdditionalInstanceField(hotSeat, HOTSEAT_HORIZONTAL_DETECTOR_KEY, detector)
        return detector
    }

    private const val PREF_LAUNCHER_SWIPE_DOWN_ACTION = "launcher_swipedown_action"
    private const val PREF_LAUNCHER_SWIPE_UP_ACTION = "launcher_swipeup_action"
    private const val PREF_LAUNCHER_PINCH_ACTION = "launcher_pinch_action"
    private const val PREF_FSG_HORIZ_APPS = "controls_fsg_horiz_apps"
    private const val PREF_FSG_SWIPE_STOP_DISABLE_VIBRATE = "controls_fsg_swipeandstop_disablevibrate"

    @Volatile
    private var swipeDownCustom = false

    @Volatile
    private var swipeUpCustom = false

    @Volatile
    private var pinchCustom = false

    @Volatile
    private var fsgHorizApps: Set<String> = emptySet()

    @Volatile
    private var disableSwipeAndStopVibrate = false

    private var homescreenSwipeSnapshotInstalled = false

    internal fun refreshHomescreenSwipeSnapshots() {
        val prefs = MainModule.mPrefs
        swipeDownCustom = prefs.getInt(PREF_LAUNCHER_SWIPE_DOWN_ACTION, 1) > 1
        swipeUpCustom = prefs.getInt(PREF_LAUNCHER_SWIPE_UP_ACTION, 1) > 1
        pinchCustom = prefs.getInt(PREF_LAUNCHER_PINCH_ACTION, 1) > 1
        fsgHorizApps = HashSet(prefs.getStringSet(PREF_FSG_HORIZ_APPS))
        disableSwipeAndStopVibrate = prefs.getBoolean(PREF_FSG_SWIPE_STOP_DISABLE_VIBRATE)
    }

    internal fun onHomescreenSwipePreferenceChanged(key: String?) {
        if (key == null ||
            key == PREF_LAUNCHER_SWIPE_DOWN_ACTION ||
            key == PREF_LAUNCHER_SWIPE_UP_ACTION ||
            key == PREF_LAUNCHER_PINCH_ACTION ||
            key == PREF_FSG_HORIZ_APPS ||
            key == PREF_FSG_SWIPE_STOP_DISABLE_VIBRATE
        ) {
            refreshHomescreenSwipeSnapshots()
        }
    }

    private fun installHomescreenSwipeSnapshot() {
        if (homescreenSwipeSnapshotInstalled) return
        homescreenSwipeSnapshotInstalled = true
        refreshHomescreenSwipeSnapshots()
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                onHomescreenSwipePreferenceChanged(key)
            }
        })
    }

    internal fun onStatusBarSwipeCanInterceptTouch(chain: XposedInterface.Chain): Any? {
        return if (swipeDownCustom) false else chain.proceed()
    }

    internal fun onAllAppsSwipeCanInterceptTouch(chain: XposedInterface.Chain): Any? {
        return if (swipeUpCustom) false else chain.proceed()
    }

    internal fun shouldBlockPinchScale(): Boolean = pinchCustom

    internal fun isFsgHorizApp(pkg: String): Boolean = pkg in fsgHorizApps

    internal fun isSwipeAndStopVibrateDisabled(): Boolean = disableSwipeAndStopVibrate

    @JvmStatic
    fun HomescreenSwipesHook(lpparam: PackageReadyParam) {
        installHomescreenSwipeSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "onVerticalGesture", Int::class.javaPrimitiveType!!, MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.getThisObject()
                try {
                    if (XposedHelpers.callMethod(thisObject, "isInNormalEditingMode") as Boolean) {
                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        return XposedHelpers.proceedOrThrow(chain, throwable)
                    }
                    val helperContext = (thisObject as ViewGroup).context
                    var numOfFingers = 1
                    if (args[1] != null) numOfFingers = (args[1] as MotionEvent).pointerCount
                    val key = LauncherVerticalGesture.resolveKey(args[0] as Int, numOfFingers)
                    if (key != null && GlobalActions.handleAction(helperContext, key)) {
                        skipped = true
                        result = true
                        throwable = null
                    }
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

        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.uioverrides.StatusBarSwipeController", lpparam.classLoader, "canInterceptTouch", MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onStatusBarSwipeCanInterceptTouch(chain)
            }
        })

        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.uioverrides.AllAppsSwipeController", lpparam.classLoader, "canInterceptTouch", MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return onAllAppsSwipeCanInterceptTouch(chain)
            }
        })

        // content_center, global_search, notification_bar
        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.allapps.LauncherMode", lpparam.classLoader, "getPullDownGesture", Context::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    if (swipeDownCustom) { result = "no_action"; throwable = null }
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        // content_center, global_search
        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.allapps.LauncherMode", lpparam.classLoader, "getSlideUpGesture", Context::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    if (swipeUpCustom) { skipped = true; result = "no_action"; throwable = null }
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

        if (ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "isGlobalSearchEnable", Context::class.java, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var skipped = false
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        if (swipeUpCustom) { skipped = true; result = false; throwable = null }
                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })) {
            ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.search.SearchEdgeLayout", lpparam.classLoader, "isTopSearchEnable", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var skipped = false
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        if (swipeDownCustom) { skipped = true; result = false; throwable = null }
                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
            ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.search.SearchEdgeLayout", lpparam.classLoader, "isBottomGlobalSearchEnable", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var skipped = false
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        if (swipeUpCustom) { skipped = true; result = false; throwable = null }
                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
            ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "isGlobalSearchBottomEffectEnable", Context::class.java, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var skipped = false
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        if (swipeUpCustom) { skipped = true; result = false; throwable = null }
                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        } else if (!ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "allowedSlidingUpToStartGolbalSearch", Context::class.java, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var skipped = false
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        if (swipeUpCustom) { skipped = true; result = false; throwable = null }
                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })) {
            if (lpparam.packageName == "com.miui.home") XposedHelpers.log("HomescreenSwipesHook", "Cannot disable swipe up search")
        }
    }

    @JvmStatic
    fun HotSeatSwipesHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.overlay.assistant.AssistantOverlaySwipeController", lpparam.classLoader, "canInterceptTouch", MotionEvent::class.java, object : MethodHook() {
            private var mHotHeatTouchRect: Rect? = null
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    val thisObject = chain.getThisObject()

                    val canInterceptTouch = result as Boolean
                    if (canInterceptTouch) {
                        val rect = mHotHeatTouchRect ?: Rect().also { mHotHeatTouchRect = it }
                        val mLauncher = XposedHelpers.getObjectField(thisObject, "mLauncher")
                        val mHotSeats = XposedHelpers.callMethod(mLauncher, "getHotSeats") as FrameLayout
                        mHotSeats.getHitRect(rect)
                        val motionEvent = chain.getArg(0) as MotionEvent
                        if (rect.contains(motionEvent.x.toInt(), motionEvent.y.toInt())) {
                            result = false
                            throwable = null
                        }
                    }

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.hotseats.HotSeats", lpparam.classLoader, "dispatchTouchEvent", MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    val ev = chain.getArg(0) as MotionEvent?
                    if (ev == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }

                    val detector = obtainHotSeatDetector(thisObject) as GestureDetector
                    detector.onTouchEvent(ev)

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

    private class SwipeListenerHorizontal(cellLayout: Any) : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_MIN_DISTANCE_HORIZ: Int
        private val SWIPE_THRESHOLD_VELOCITY: Int
        private val ownerRef: WeakReference<ViewGroup> = WeakReference(cellLayout as? ViewGroup)

        init {
            val context = ownerRef.get()?.context
            val density = context?.resources?.displayMetrics?.density ?: 1.0f
            SWIPE_MIN_DISTANCE_HORIZ = Math.round(75 * density)
            SWIPE_THRESHOLD_VELOCITY = Math.round(33 * density)
        }

        override fun onDown(e: MotionEvent): Boolean {
            return false
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            val owner = ownerRef.get() ?: return false
            val context = owner.context

            if (e2.x - e1.x > SWIPE_MIN_DISTANCE_HORIZ && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY)
                return GlobalActions.handleAction(context, "launcher_swiperight")

            if (e1.x - e2.x > SWIPE_MIN_DISTANCE_HORIZ && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY)
                return GlobalActions.handleAction(context, "launcher_swipeleft")

            return false
        }
    }

    @JvmStatic
    fun ShakeHook(lpparam: PackageReadyParam) {
        val shakeMgrKey = "MIUIZER_SHAKE_MGR"

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onResume", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    val thisObject = chain.getThisObject()

                    var shakeMgr = XposedHelpers.getAdditionalInstanceField(thisObject, shakeMgrKey) as ShakeManager?
                    if (shakeMgr == null) {
                        shakeMgr = ShakeManager(thisObject as Context)
                        XposedHelpers.setAdditionalInstanceField(thisObject, shakeMgrKey, shakeMgr)
                    }
                    val launcherActivity = thisObject as Activity
                    val sensorMgr = launcherActivity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    shakeMgr.reset()
                    sensorMgr.registerListener(shakeMgr, sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onPause", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    val thisObject = chain.getThisObject()

                    if (XposedHelpers.getAdditionalInstanceField(thisObject, shakeMgrKey) == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val launcherActivity = thisObject as Activity
                    val sensorMgr = launcherActivity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    sensorMgr.unregisterListener(XposedHelpers.getAdditionalInstanceField(thisObject, shakeMgrKey) as ShakeManager)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun FSGesturesHook(lpparam: PackageReadyParam) {
        installHomescreenSwipeSnapshot()
        val baseRecentsClass = installForceFsgNavBarCallerScope(lpparam.classLoader)
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.DeviceConfig", lpparam.classLoader, "usingFsGesture", HookerClassHelper.returnConstant(true))
        ModuleHelper.findAndHookMethodSilently(baseRecentsClass, "createAndAddNavStubView", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val fsg = XposedHelpers.getAdditionalStaticField(
                        baseRecentsClass,
                        "REAL_FORCE_FSG_NAV_BAR"
                    ) as Boolean
                    if (!fsg) { skipped = true; result = null; throwable = null }
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

        ModuleHelper.findAndHookMethodSilently(baseRecentsClass, "updateFsgWindowState", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    val thisObject = chain.getThisObject()

                    val fsg = XposedHelpers.getAdditionalStaticField(
                        baseRecentsClass,
                        "REAL_FORCE_FSG_NAV_BAR"
                    ) as Boolean
                    if (fsg) { return XposedHelpers.throwOrReturn(throwable, result) }

                    val mNavStubView = XposedHelpers.getObjectField(thisObject, "mNavStubView")
                    val mWindowManager = XposedHelpers.getObjectField(thisObject, "mWindowManager")
                    if (mWindowManager != null && mNavStubView != null) {
                        XposedHelpers.callMethod(mWindowManager, "removeView", mNavStubView)
                        XposedHelpers.setObjectField(thisObject, "mNavStubView", null)
                    }

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethodSilently("com.miui.launcher.utils.MiuiSettingsUtils", lpparam.classLoader, "getGlobalBoolean", ContentResolver::class.java, String::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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

                    if (chain.getArg(1) != "force_fsg_nav_bar") { return XposedHelpers.throwOrReturn(throwable, result) }

                    if (forceFsgNavBarCallerScope.isActive()) {
                        XposedHelpers.setAdditionalStaticField(
                            baseRecentsClass,
                            "REAL_FORCE_FSG_NAV_BAR",
                            result
                        )
                        result = true
                        throwable = null
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    // The caller wrappers restore depth in finally, including nested failures.
                    // Non-caller queries keep the ROM result and throwable unchanged.

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "onTouchEvent", MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val event = chain.getArg(0) as MotionEvent
                    if (event.action != MotionEvent.ACTION_DOWN) {
                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        return XposedHelpers.proceedOrThrow(chain, throwable)
                    }
                    val foregroundInfo = ProcessManager.getForegroundInfo()
                    if (foregroundInfo != null) {
                        val pkgName = foregroundInfo.mForegroundPackageName
                        if (pkgName != null && pkgName in fsgHorizApps) { skipped = true; result = false; throwable = null }
                    }

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

    class DoubleTapController(context: Context, actionKey: String) {
        private val MAX_DURATION = 500L
        private var mActionDownRawX: Float = 0f
        private var mActionDownRawY: Float = 0f
        private var mClickCount: Int = 0
        val mContext: Context = context
        private val mActionKey: String = actionKey
        private var mFirstClickRawX: Float = 0f
        private var mFirstClickRawY: Float = 0f
        private var mLastClickTime: Long = 0L
        private var mTouchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop * 2

        fun isDoubleTapEvent(motionEvent: MotionEvent): Boolean {
            val action = motionEvent.actionMasked
            if (action == MotionEvent.ACTION_DOWN) {
                mActionDownRawX = motionEvent.rawX
                mActionDownRawY = motionEvent.rawY
                return false
            } else if (action != MotionEvent.ACTION_UP) {
                return false
            } else {
                val rawX = motionEvent.rawX
                val rawY = motionEvent.rawY
                if (Math.abs(rawX - mActionDownRawX) <= mTouchSlop.toFloat() && Math.abs(rawY - mActionDownRawY) <= mTouchSlop.toFloat()) {
                    if (SystemClock.elapsedRealtime() - mLastClickTime > MAX_DURATION || rawY - mFirstClickRawY > mTouchSlop.toFloat() || rawX - mFirstClickRawX > mTouchSlop.toFloat()) {
                        mClickCount = 0
                    }
                    mClickCount++
                    if (mClickCount == 1) {
                        mFirstClickRawX = rawX
                        mFirstClickRawY = rawY
                        mLastClickTime = SystemClock.elapsedRealtime()
                        return false
                    } else if (Math.abs(rawY - mFirstClickRawY) <= mTouchSlop.toFloat() && Math.abs(rawX - mFirstClickRawX) <= mTouchSlop.toFloat() && SystemClock.elapsedRealtime() - mLastClickTime <= MAX_DURATION) {
                        mClickCount = 0
                        return true
                    }
                }
                mClickCount = 0
                return false
            }
        }

        fun onDoubleTapEvent() {
            GlobalActions.handleAction(mContext, mActionKey)
        }
    }

    @JvmStatic
    fun LauncherDoubleTapHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors("com.miui.home.launcher.Workspace", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    val thisObject = chain.getThisObject()
                    val args = chain.args

                    if (args.size != 3) { return XposedHelpers.throwOrReturn(throwable, result) }
                    var mDoubleTapControllerEx = XposedHelpers.getAdditionalInstanceField(thisObject, "mDoubleTapControllerEx")
                    if (mDoubleTapControllerEx != null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    mDoubleTapControllerEx = DoubleTapController(args[0] as Context, "launcher_doubletap")
                    XposedHelpers.setAdditionalInstanceField(thisObject, "mDoubleTapControllerEx", mDoubleTapControllerEx)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "dispatchTouchEvent", MotionEvent::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    val mDoubleTapControllerEx = XposedHelpers.getAdditionalInstanceField(thisObject, "mDoubleTapControllerEx") as? DoubleTapController
                    if (mDoubleTapControllerEx == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (!mDoubleTapControllerEx.isDoubleTapEvent(chain.getArg(0) as MotionEvent)) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val mCurrentScreenIndex = XposedHelpers.getIntField(thisObject, if (lpparam.packageName == "com.miui.home") "mCurrentScreenIndex" else "mCurrentScreen")
                    val cellLayout = XposedHelpers.callMethod(thisObject, "getCellLayout", mCurrentScreenIndex)
                    if (XposedHelpers.callMethod(cellLayout, "lastDownOnOccupiedCell") as Boolean) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (XposedHelpers.callMethod(thisObject, "isInNormalEditingMode") as Boolean) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    mDoubleTapControllerEx.onDoubleTapEvent()

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
    fun AssistGestureActionHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.shared.recents.system.AssistManager", lpparam.classLoader, "isSupportGoogleAssist", Int::class.javaPrimitiveType!!, HookerClassHelper.returnConstant(true))
        val FsGestureHelper = XposedHelpers.findClassIfExists("com.miui.home.recents.FsGestureAssistHelper", lpparam.classLoader)
        ModuleHelper.findAndHookMethod(FsGestureHelper!!, "canTriggerAssistantAction", Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.getThisObject()
                try {

                    val isDisabled = XposedHelpers.callStaticMethod(FsGestureHelper, "isAssistantGestureDisabled", args[2]) as Boolean
                    if (!isDisabled) {
                        val mAssistantWidth = XposedHelpers.getIntField(thisObject, "mAssistantWidth")
                        val f = args[0] as Float
                        val f2 = args[1] as Float
                        if (f < mAssistantWidth || f > f2 - mAssistantWidth) {
                            skipped = true
                            result = true
                            throwable = null
                            return XposedHelpers.throwOrReturn(throwable, result)
                        }
                    }
                    skipped = true
                    result = false
                    throwable = null

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

        val inDirection = intArrayOf(0)

        ModuleHelper.hookAllMethods(FsGestureHelper!!, "handleTouchEvent", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    val thisObject = chain.getThisObject()

                    val motionEvent = chain.getArg(0) as MotionEvent
                    if (motionEvent.action == 0) {
                        val mDownX = XposedHelpers.getFloatField(thisObject, "mDownX")
                        val mAssistantWidth = XposedHelpers.getIntField(thisObject, "mAssistantWidth")
                        inDirection[0] = if (mDownX < mAssistantWidth) 0 else 1
                    }

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.recents.SystemUiProxyWrapper", lpparam.classLoader, "startAssistant", Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val bundle = chain.getArg(0) as Bundle
                    bundle.putInt("inDirection", inDirection[0])

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
    fun SwipeAndStopActionHook(lpparam: PackageReadyParam) {
        installHomescreenSwipeSnapshot()
        val ReadyStateEnum = XposedHelpers.findClassIfExists("com.miui.home.recents.GestureBackArrowView\$ReadyState", lpparam.classLoader)
        if (ReadyStateEnum == null) return
        val states = ReadyStateEnum.enumConstants ?: return
        var recentState: Any? = null
        var backState: Any? = null
        for (o in states) {
            val enumStr = o.toString()
            if ("READY_STATE_RECENT" == enumStr) recentState = o
            else if ("READY_STATE_BACK" == enumStr) backState = o
        }
        val finalBackState = backState
        val finalRecentState = recentState
        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureBackArrowView", lpparam.classLoader, "setReadyFinish", ReadyStateEnum, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    val mReadyState = XposedHelpers.getObjectField(thisObject, "mReadyState")
                    val readyState = chain.getArg(0)
                    if (readyState != mReadyState) {
                        val disableVibrate = disableSwipeAndStopVibrate
                        val view = thisObject as View
                        XposedHelpers.setObjectField(view, "mRecentTaskIcon", null)
                        if (mReadyState == finalBackState && readyState == finalRecentState) {
                            val mScale = XposedHelpers.getFloatField(view, "mScale")
                            XposedHelpers.callMethod(view, "changeScale", mScale, 1.17f, 200, false)
                            if (!disableVibrate) {
                                HookUtils.performStrongVibration(view.context, true)
                            }
                        } else if (mReadyState == finalRecentState) {
                            val mScale = XposedHelpers.getFloatField(view, "mScale")
                            XposedHelpers.callMethod(view, "changeScale", mScale, 1.0f, 200, true)
                        }
                        XposedHelpers.setObjectField(view, "mReadyState", readyState)
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        val GestureStubViewClass = XposedHelpers.findClass("com.miui.home.recents.GestureStubView", lpparam.classLoader)
        ModuleHelper.findAndHookMethod(GestureStubViewClass, "disableQuickSwitch", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    args[0] = false

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod(GestureStubViewClass, "isDisableQuickSwitch", HookerClassHelper.returnConstant(false))
        val gestureStubViews = arrayOfNulls<Any>(1)
        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView\$3", lpparam.classLoader, "onSwipeStop", Boolean::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    val isFinished = chain.getArg(0) as Boolean
                    if (isFinished) {
                        val outerThis = XposedHelpers.getSurroundingThis(thisObject)
                        gestureStubViews[0] = outerThis
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

                    val isFinished = chain.getArg(0) as Boolean
                    if (isFinished) {
                        gestureStubViews[0] = null
                    }

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.home.recents.GestureStubView", lpparam.classLoader, "getNextTask", Context::class.java, Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                    val args = chain.args

                    val nextTaskInfo = args[1] as Boolean
                    if (!nextTaskInfo || gestureStubViews[0] == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val outerThis = gestureStubViews[0]
                    ModuleHelper.callMethodSilently(outerThis, "onBackCancelled")
                    val mContext = XposedHelpers.getObjectField(outerThis, "mContext") as Context
                    val mGestureStubPos = args[2] as Int
                    val bundle = Bundle()
                    bundle.putInt("inDirection", mGestureStubPos)
                    GlobalActions.handleAction(mContext, "controls_fsg_swipeandstop", false, bundle)

                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun LauncherPinchHook(lpparam: PackageReadyParam) {
        installHomescreenSwipeSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "onPinching", Float::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    val dampingScale = XposedHelpers.callMethod(thisObject, "getDampingScale", chain.getArg(0)) as Float
                    val screenScaleRatio = XposedHelpers.callMethod(thisObject, "getScreenScaleRatio") as Float
                    if (dampingScale < screenScaleRatio)
                        if (pinchCustom) { skipped = true; result = false; throwable = null }

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

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Workspace", lpparam.classLoader, "onPinchingEnd", Float::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    val dampingScale = XposedHelpers.callMethod(thisObject, "getDampingScale", chain.getArg(0)) as Float
                    val screenScaleRatio = XposedHelpers.callMethod(thisObject, "getScreenScaleRatio") as Float
                    if (dampingScale < screenScaleRatio)
                        if (GlobalActions.handleAction((thisObject as View).context, "launcher_pinch")) {
                            XposedHelpers.callMethod(thisObject, "finishCurrentGesture")

                            val pinchingStateEnum = XposedHelpers.findClass("com.miui.home.launcher.Workspace\$PinchingState", lpparam.classLoader)
                            val stateFollow = XposedHelpers.getStaticObjectField(pinchingStateEnum, "FOLLOW")
                            val stateReadyToEdit = XposedHelpers.getStaticObjectField(pinchingStateEnum, "READY_TO_EDIT")

                            val mState = XposedHelpers.getObjectField(thisObject, "mState")
                            XposedHelpers.setObjectField(thisObject, "mState", stateFollow)
                            if (mState == stateReadyToEdit)
                                XposedHelpers.callMethod(XposedHelpers.getObjectField(thisObject, "mLauncher"), "changeEditingEntryViewToHotseats")
                            XposedHelpers.callMethod(thisObject, "resetCellScreenScale", chain.getArg(0))

                            skipped = true
                            result = null
                            throwable = null
                        }

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

}
