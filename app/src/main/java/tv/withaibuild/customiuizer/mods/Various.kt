package tv.withaibuild.customiuizer.mods

import android.app.Activity
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Rect
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.os.UserHandle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import miui.os.Build
import miui.process.ForegroundInfo
import miui.process.ProcessManager
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.PreparedHookBatch
import tv.withaibuild.customiuizer.mods.utils.HookInstallStateMachine
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.HttpURLConnection
import java.net.URLConnection
import java.util.ArrayList
import java.util.Calendar
import java.util.HashMap
import tv.withaibuild.customiuizer.utils.HookUtils

object Various {

    internal data class VariousSnapshot(
        val alarmCompatApps: Set<String> = emptySet(),
        val showCallUi: Int = 0,
        val callUiBrightType: Int = 0,
        val callUiBrightNight: Boolean = false,
        val callUiBrightNightStartHour: Int = 0,
        val callUiBrightNightStartMinute: Int = 0,
        val callUiBrightNightEndHour: Int = 0,
        val callUiBrightNightEndMinute: Int = 0,
        val callUiBrightVal: Int = 0,
        val gboardPaddingPort: Int = 0,
        val gboardPaddingLand: Int = 0,
    )

    @Volatile
    internal var variousConfig = VariousSnapshot()

    private var variousObserverRegistered = false

    private val VARIOUS_PREF_KEYS = setOf(
        "various_alarmcompat_apps",
        "various_showcallui",
        "various_calluibright_type",
        "various_calluibright_night",
        "various_calluibright_night_start_hour",
        "various_calluibright_night_start_minute",
        "various_calluibright_night_end_hour",
        "various_calluibright_night_end_minute",
        "various_calluibright_val",
        "various_gboardpadding_port",
        "various_gboardpadding_land",
    )

    internal fun refreshVariousSnapshot() {
        val prefs = MainModule.mPrefs
        val nightKey = "various_calluibright_night"
        variousConfig = VariousSnapshot(
            alarmCompatApps = HashSet(prefs.getStringSet("various_alarmcompat_apps")),
            showCallUi = prefs.getStringAsInt("various_showcallui", 0),
            callUiBrightType = prefs.getStringAsInt("various_calluibright_type", 0),
            callUiBrightNight = prefs.getBoolean(nightKey),
            callUiBrightNightStartHour = prefs.getInt(nightKey + "_start_hour", 0),
            callUiBrightNightStartMinute = prefs.getInt(nightKey + "_start_minute", 0),
            callUiBrightNightEndHour = prefs.getInt(nightKey + "_end_hour", 0),
            callUiBrightNightEndMinute = prefs.getInt(nightKey + "_end_minute", 0),
            callUiBrightVal = prefs.getInt("various_calluibright_val", 0),
            gboardPaddingPort = prefs.getInt("various_gboardpadding_port", 0),
            gboardPaddingLand = prefs.getInt("various_gboardpadding_land", 0),
        )
    }

    @JvmStatic
    internal fun installVariousSnapshot() {
        refreshVariousSnapshot()
        if (variousObserverRegistered) return
        variousObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in VARIOUS_PREF_KEYS) refreshVariousSnapshot()
            }
        })
    }

    @JvmField
    val MIUI_CORE_APPS = setOf(
        "com.lbe.security.miui", "com.miui.securitycenter", "com.miui.packageinstaller", "com.miui.home"
    )

    @JvmStatic
    fun AppInfoHook(lpparam: PackageReadyParam) = AppInformationHooks.install(lpparam.classLoader)

    /**
     * Resolves the user id for the AppInfo "launch_app" action.
     *
     * A UID below 0 means the extra is missing or invalid and the launch action
     * must fail closed. A UID >= 0 is passed to [resolver]; non-fatal failures are
     * logged and returned as `null`. Fatal errors are propagated.
     */
    internal fun resolveAppInfoLaunchUserId(uid: Int, resolver: (Int) -> Int): Int? {
        if (uid < 0) return null
        return try {
            resolver(uid)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            null
        }
    }

    internal fun initialAppSort(bundle: Bundle?, option: Int): Bundle? {
        if (option !in 2..4 || bundle?.let { it.containsKey("current_sory_type") || it.containsKey("current_sort_type") } == true)
            return bundle
        return (bundle?.let(::Bundle) ?: Bundle()).apply {
            putInt("current_sory_type", option - 1)
            putInt("current_sort_type", option - 1)
        }
    }

    @JvmStatic
    fun AppsDefaultSortHook(lpparam: PackageReadyParam) {
        appSortObserver.onChange(null)
        ModuleHelper.observePreferenceChange(appSortObserver, appSortObserver)
        // A14's ManageFragment consumes the sort key; AppFragment is a separate landing page.
        val type = XposedHelpers.findClass("com.miui.appmanager.fragment.ManageFragment", lpparam.classLoader)
        ModuleHelper.findAndHookMethod(type, "onActivityCreated", Bundle::class.java, object : MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                    val original = callback.getArg(0) as? Bundle
                    val updated = initialAppSort(original, appSortOption)
                    if (updated !== original) callback.getArgs()[0] = updated
                }
        })
    }

    @Volatile private var appSortOption = 1
    private val appSortObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key == null || key == "various_appsort") appSortOption = MainModule.mPrefs.getStringAsInt("various_appsort", 1)
        }
    }

    internal data class AppStateTarget(val packageName: String, val uid: Int)
    private const val APP_STATE_TARGET = "customiuizer_app_state_target"

    internal fun resolveAppStateTarget(info: PackageInfo?, contextUser: Int): AppStateTarget? {
        val application = info?.applicationInfo ?: return null
        // A default or parcelled PackageInfo can still contain a null package name.
        val name = info.packageName ?: return null
        if (name.isBlank() || application.packageName != name || application.uid < 0 || contextUser < 0 ||
            application.uid / 100000 != contextUser) return null
        return AppStateTarget(name, application.uid)
    }

    internal fun appStateTarget(act: Activity, info: PackageInfo): AppStateTarget? = ModuleHelper.guarded<AppStateTarget?>(null) {
        resolveAppStateTarget(info, XposedHelpers.callMethod(act, "getUserId") as Int)
    }

    internal fun changeApplicationState(pm: PackageManager, target: AppStateTarget, enable: Boolean): Boolean {
        if (target.packageName in MIUI_CORE_APPS || pm.getApplicationInfo(target.packageName, PackageManager.MATCH_DISABLED_COMPONENTS).uid != target.uid) return false
        pm.setApplicationEnabledSetting(target.packageName,
            if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
        val actual = pm.getApplicationInfo(target.packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        return actual.uid == target.uid && actual.enabled == enable
    }

    private fun setAppState(act: Activity, target: AppStateTarget, item: MenuItem, enable: Boolean) {
        ModuleHelper.guarded {
            if (act.isFinishing || act.isDestroyed) return@guarded
            val current = XposedHelpers.findFirstFieldByExactType(act.javaClass, PackageInfo::class.java).get(act) as? PackageInfo
            if (current == null || appStateTarget(act, current) != target || target.packageName in MIUI_CORE_APPS) return@guarded
            val pkgName = target.packageName
            val pm = act.packageManager
            if (changeApplicationState(pm, target, enable)) {
                item.setTitle(act.resources.getIdentifier(if (enable) "app_manager_disable_text" else "app_manager_enable_text", "string", "com.miui.securitycenter"))
                Toast.makeText(act, act.resources.getIdentifier(if (enable) "app_manager_enabled" else "app_manager_disabled", "string", "com.miui.securitycenter"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(act, ModuleHelper.getModuleRes(act).getString(R.string.disable_app_fail), Toast.LENGTH_LONG).show()
            }
            Handler(Looper.getMainLooper()).postDelayed({ ModuleHelper.guarded {
                if (!act.isFinishing && !act.isDestroyed) act.invalidateOptionsMenu()
            } }, 500)
        }
    }

    @JvmStatic
    fun AppsDisableServiceHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.pm.PackageManagerServiceImpl", lpparam.classLoader, "canBeDisabled", String::class.java, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    if (!MIUI_CORE_APPS.contains(chain.getArg(0) as String)) {
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
    fun AppsDisableHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "onCreateOptionsMenu", Menu::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result = chain.proceed()
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val act = thisObject as Activity
                    val menu = chain.getArg(0) as Menu
                    val dis = menu.add(0, 666, 1, act.resources.getIdentifier("app_manager_disable_text", "string", lpparam.packageName))
                    dis.setIcon(act.resources.getIdentifier("action_button_stop", "drawable", lpparam.packageName))
                    dis.isEnabled = true
                    dis.setShowAsAction(1)

                    val pm = act.packageManager
                    val piField = XposedHelpers.findFirstFieldByExactType(act.javaClass, PackageInfo::class.java)
                    val mPackageInfo = piField.get(act) as PackageInfo
                    val target = appStateTarget(act, mPackageInfo)
                    if (target == null || target.packageName in MIUI_CORE_APPS) { dis.isEnabled = false; return result }
                    XposedHelpers.setAdditionalInstanceField(dis, APP_STATE_TARGET, target)
                    val appInfo = pm.getApplicationInfo(mPackageInfo.packageName, PackageManager.GET_META_DATA)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    dis.setTitle(act.resources.getIdentifier(if (appInfo.enabled) "app_manager_disable_text" else "app_manager_enable_text", "string", lpparam.packageName))

                    if (!appInfo.enabled || (isSystem && !isUpdatedSystem)) {
                        val item = menu.findItem(2)
                        item?.isVisible = false
                    }
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "onOptionsItemSelected", MenuItem::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result = chain.proceed()
                var throwable: Throwable? = null
                try {
                    val thisObject = chain.thisObject

                    val item = chain.getArg(0) as MenuItem
                    if (item.itemId != 666) return result
                    val target = XposedHelpers.getAdditionalInstanceField(item, APP_STATE_TARGET) as? AppStateTarget ?: return result

                    val act = thisObject as Activity
                    val modRes = ModuleHelper.getModuleRes(act)
                    val piField = XposedHelpers.findFirstFieldByExactType(act.javaClass, PackageInfo::class.java)
                    val mPackageInfo = piField.get(act) as PackageInfo
                    if (appStateTarget(act, mPackageInfo) != target) return result
                    if (MIUI_CORE_APPS.contains(mPackageInfo.packageName)) {
                        Toast.makeText(act, modRes.getString(R.string.disable_app_settings), Toast.LENGTH_SHORT).show()
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }

                    val pm = act.packageManager
                    val appInfo = pm.getApplicationInfo(mPackageInfo.packageName, PackageManager.GET_META_DATA)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    if (appInfo.enabled) {
                        if (isSystem) {
                            val title = modRes.getString(R.string.disable_app_title)
                            val text = modRes.getString(R.string.disable_app_text)
                            AlertDialog.Builder(act).setTitle(title).setMessage(text).setPositiveButton(android.R.string.ok) { _, _ ->
                                ModuleHelper.guarded { setAppState(act, target, item, false) }
                            }.setNegativeButton(android.R.string.cancel, null).show()
                        } else setAppState(act, target, item, false)
                    } else setAppState(act, target, item, true)
                    result = true; throwable = null
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun HideReportButtonHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "onCreateOptionsMenu", Menu::class.java, object : MethodHook() {
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
                    val menu = chain.args[0] as Menu
                    val reportMenu = menu.findItem(4)
                    reportMenu?.isVisible = false
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun AppsRestrictHook(lpparam: PackageReadyParam) {
        val mGetAppInfo = XposedHelpers.findMethodsByExactParameters(
            XposedHelpers.findClass("com.miui.appmanager.AppManageUtils", lpparam.classLoader),
            ApplicationInfo::class.java, Any::class.java, PackageManager::class.java, String::class.java,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!
        )
        if (mGetAppInfo.isEmpty())
            XposedHelpers.log("AppsRestrictHook", "Cannot find getAppInfo method!")
        else
            ModuleHelper.hookMethod(mGetAppInfo[0], appRestrictionInfoHook())

        ModuleHelper.findAndHookMethod("com.miui.networkassistant.ui.fragment.ShowAppDetailFragment", lpparam.classLoader, "initFirewallData", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {
                    val mAppInfo = XposedHelpers.getObjectField(thisObject, "mAppInfo")
                    if (mAppInfo != null) XposedHelpers.setBooleanField(mAppInfo, "isSystemApp", false)

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods("com.miui.networkassistant.service.FirewallService", lpparam.classLoader, "setSystemAppWifiRuleAllow", HookerClassHelper.DO_NOTHING)
    }

    internal fun appRestrictionInfoHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val original = chain.proceed()
            if (original !is ApplicationInfo || original.javaClass != ApplicationInfo::class.java ||
                original.flags and ApplicationInfo.FLAG_SYSTEM == 0) return original
            return ModuleHelper.guarded(original) {
                if (chain.getArg(3) == 128 && chain.getArg(4) == 0)
                    ApplicationInfo(original).apply { flags = flags and ApplicationInfo.FLAG_SYSTEM.inv() }
                else original
            }
        }
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun AppsRestrictPowerHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.powerkeeper.provider.PowerKeeperConfigureManager", lpparam.classLoader, "pkgHasIcon", String::class.java, HookerClassHelper.returnConstant(true))

        ModuleHelper.findAndHookMethod("com.miui.powerkeeper.provider.PreSetGroup", lpparam.classLoader, "initGroup", object : MethodHook() {
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
                    val mGroupHeadUidMap = XposedHelpers.getStaticObjectField(
                        XposedHelpers.findClass("com.miui.powerkeeper.provider.PreSetGroup", lpparam.classLoader),
                        "mGroupHeadUidMap"
                    ) as HashMap<String, Int>
                    mGroupHeadUidMap.clear()
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.powerkeeper.provider.PreSetApp", lpparam.classLoader, "isPreSetApp", String::class.java, HookerClassHelper.returnConstant(false))
        ModuleHelper.hookAllMethods("com.miui.powerkeeper.utils.Utils", lpparam.classLoader, "pkgHasIcon", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun PersistBatteryOptimizationHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.miui.powerkeeper.utils.CommonAdapter", lpparam.classLoader, "addPowerSaveWhitelistApps", HookerClassHelper.DO_NOTHING)
        ModuleHelper.hookAllMethods("com.miui.powerkeeper.millet.MilletPolicy", lpparam.classLoader, "dealSleepModeWhiteList", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val addWhiteList = chain.getArg(1) as Boolean
                    if (addWhiteList) {
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
        ModuleHelper.findAndHookMethod("com.miui.powerkeeper.statemachine.ForceDozeController", lpparam.classLoader, "restoreWhiteListAppsIfQuitForceIdle", HookerClassHelper.DO_NOTHING)
    }

    private fun showSideBar(view: View, dockLocation: Int) {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val y = location[1].toFloat()
        val uptimeMillis = SystemClock.uptimeMillis()
        val downEvent: MotionEvent
        val moveEvent: MotionEvent
        val upEvent: MotionEvent
        if (dockLocation == 0) {
            downEvent = MotionEvent.obtain(uptimeMillis, uptimeMillis, MotionEvent.ACTION_DOWN, 4f, y + 15f, 0)
            moveEvent = MotionEvent.obtain(uptimeMillis, uptimeMillis + 20, MotionEvent.ACTION_MOVE, 160f, y + 15f, 0)
            upEvent = MotionEvent.obtain(uptimeMillis, uptimeMillis + 21, MotionEvent.ACTION_UP, 160f, y + 15f, 0)
        } else {
            val x = location[0].toFloat()
            downEvent = MotionEvent.obtain(uptimeMillis, uptimeMillis, MotionEvent.ACTION_DOWN, x - 4f, y + 15f, 0)
            moveEvent = MotionEvent.obtain(uptimeMillis, uptimeMillis + 20, MotionEvent.ACTION_MOVE, x - 160f, y + 15f, 0)
            upEvent = MotionEvent.obtain(uptimeMillis, uptimeMillis + 21, MotionEvent.ACTION_UP, x - 160f, y + 15f, 0)
        }
        downEvent.setSource(9999)
        moveEvent.setSource(9999)
        upEvent.setSource(9999)
        view.dispatchTouchEvent(downEvent)
        view.dispatchTouchEvent(moveEvent)
        view.dispatchTouchEvent(upEvent)
        downEvent.recycle()
        moveEvent.recycle()
        upEvent.recycle()
    }

    internal const val SIDE_BAR_EXPAND_RECEIVER_KEY = "showSideBarReceiver"
    private const val SIDE_BAR_HIDE_TASK = "customiuizer_sidebar_hide_task"

    internal fun scheduleSideBarHandleHide(view: View) {
        if (XposedHelpers.getAdditionalInstanceField(view, SIDE_BAR_HIDE_TASK) != null) return
        val owner = WeakReference(view)
        val task = Runnable {
            val target = owner.get() ?: return@Runnable
            XposedHelpers.removeAdditionalInstanceField(target, SIDE_BAR_HIDE_TASK) ?: return@Runnable
            ModuleHelper.guarded {
                val listenerInfo = XposedHelpers.getObjectField(target, "mListenerInfo")
                val listener = listenerInfo?.let {
                    XposedHelpers.getObjectField(it, "mOnTouchListener") as? View.OnTouchListener
                }
                if (listener != null) {
                    target.setOnTouchListener { ownerView, event ->
                        ModuleHelper.guarded(false) {
                            event.source == 9999 && listener.onTouch(ownerView, event)
                        }
                    }
                }
                target.background = null
            }
        }
        XposedHelpers.setAdditionalInstanceField(view, SIDE_BAR_HIDE_TASK, task)
        view.postDelayed(task, 150)
    }

    internal fun cancelSideBarHandleHide(view: View?) {
        if (view == null) return
        val task = XposedHelpers.removeAdditionalInstanceField(view, SIDE_BAR_HIDE_TASK) as? Runnable ?: return
        view.removeCallbacks(task)
    }

    /**
     * Process-scoped ShowSideBar receiver. The [View] is held only through a
     * [WeakReference] so [ReceiverRegistry] cannot pin the sidebar view after detach.
     */
    internal fun createSideBarExpandReceiver(
        view: View,
        originDockLocation: Int,
    ): BroadcastReceiver {
        val viewRef = WeakReference(view)
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                val target = viewRef.get() ?: return@guarded
                val fromPackage = getSentFromPackage()
                if (fromPackage != Helpers.modulePkg &&
                    fromPackage != "android" &&
                    fromPackage != "com.android.systemui" &&
                    fromPackage != "com.miui.home"
                ) {
                    if (isOrderedBroadcast) setResultCode(GlobalActions.ACTION_FAILED)
                    return@guarded
                }
                val bundle = intent.getBundleExtra("actionInfo")
                var pos = originDockLocation
                if (bundle != null) {
                    pos = bundle.getInt("inDirection", 0)
                    target.context.getSharedPreferences("sp_video_box", 0).edit().putInt("dock_line_location", pos).apply()
                }
                showSideBar(target, pos)
                if (isOrderedBroadcast) setResultCode(GlobalActions.ACTION_HANDLED)
            }
        }
    }

    internal fun releaseSideBarExpandReceiver(receiver: BroadcastReceiver?) {
        if (receiver == null) return
        ModuleHelper.unregisterModuleReceiver(SIDE_BAR_EXPAND_RECEIVER_KEY, receiver)
    }

    /**
     * Releases the process-scoped sidebar receiver only when [detachedView] is the
     * current owner. A detach from any other [RegionSamplingHelper] instance, or a
     * collected owner WeakReference, leaves the slot unchanged.
     */
    internal fun takeSideBarExpandReceiverIfOwner(
        registeredReceiver: Array<BroadcastReceiver?>,
        registeredOwnerView: Array<WeakReference<View>?>,
        isHooked: BooleanArray,
        detachedView: View?,
    ): BroadcastReceiver? {
        val currentOwner = registeredOwnerView[0]?.get() ?: return null
        if (currentOwner !== detachedView) return null
        val receiverToRelease = registeredReceiver[0]
        registeredReceiver[0] = null
        registeredOwnerView[0] = null
        isHooked[0] = false
        return receiverToRelease
    }

    @JvmStatic
    fun AddSideBarExpandReceiverHook(lpparam: PackageReadyParam) {
        val isHooked = booleanArrayOf(false)
        val registeredReceiver = arrayOf<BroadcastReceiver?>(null)
        val registeredOwnerView = arrayOf<WeakReference<View>?>(null)
        val enableSideBar = MainModule.mPrefs.getBoolean("various_swipe_expand_sidebar")
        if (!enableSideBar) {
            MainModule.resHooks.setThemeValueReplacement("com.miui.securitycenter", "dimen", "sidebar_height_default", 8)
            MainModule.resHooks.setThemeValueReplacement("com.miui.securitycenter", "dimen", "sidebar_height_vertical", 8)
        }
        val RegionSamplingHelper = XposedHelpers.findClassIfExists("com.android.systemui.navigationbar.gestural.RegionSamplingHelper", lpparam.classLoader)
        if (RegionSamplingHelper == null) {
            XposedHelpers.log("AddSideBarExpandReceiverHook", "failed to find RegionSamplingHelper")
            return
        }

        ModuleHelper.hookAllConstructors(RegionSamplingHelper, object : MethodHook() {
            private var originDockLocation = -1
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
                if (throwable != null) return XposedHelpers.throwOrReturn(throwable, result)
                try {
                    if (!isHooked[0]) {
                        isHooked[0] = true
                        val view = chain.getArg(0) as View
                        if (originDockLocation == -1) {
                            originDockLocation = view.context.getSharedPreferences("sp_video_box", 0).getInt("dock_line_location", 0)
                        }
                        val showReceiver = createSideBarExpandReceiver(view, originDockLocation)
                        if (ModuleHelper.registerModuleReceiver(
                                view.context,
                                SIDE_BAR_EXPAND_RECEIVER_KEY,
                                showReceiver,
                                IntentFilter(GlobalActions.ACTION_PREFIX + "ShowSideBar"),
                                Context.RECEIVER_EXPORTED,
                            )
                        ) {
                            registeredReceiver[0] = showReceiver
                            registeredOwnerView[0] = WeakReference(view)
                            if (!enableSideBar) scheduleSideBarHandleHide(view)
                        } else {
                            registeredReceiver[0] = null
                            registeredOwnerView[0] = null
                            isHooked[0] = false
                        }
                    }
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    if (registeredReceiver[0] == null) {
                        registeredOwnerView[0] = null
                        isHooked[0] = false
                    }
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod(RegionSamplingHelper, "onViewDetachedFromWindow", View::class.java, object : MethodHook() {
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
                cancelSideBarHandleHide(chain.getArg(0) as? View)
                val receiverToRelease = takeSideBarExpandReceiverIfOwner(
                    registeredReceiver,
                    registeredOwnerView,
                    isHooked,
                    chain.getArg(0) as? View,
                )
                try {
                    releaseSideBarExpandReceiver(receiverToRelease)
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        val methods = XposedHelpers.findMethodsByExactParameters(RegionSamplingHelper, Void.TYPE, Rect::class.java)
        if (methods.isEmpty()) {
            XposedHelpers.log("AddSideBarExpandReceiverHook", "Cannot find appropriate start method")
            return
        }
        ModuleHelper.hookMethod(methods[0], object : MethodHook() {
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
    fun InterceptPermHook(lpparam: PackageReadyParam) {
        val InterceptBaseFragmentClass = XposedHelpers.findClass("com.miui.permcenter.privacymanager.InterceptBaseFragment", lpparam.classLoader)
        val innerClasses = InterceptBaseFragmentClass.declaredClasses
        var HandlerClass: Class<*>? = null
        for (innerClass in innerClasses) {
            if (Handler::class.java.isAssignableFrom(innerClass)) {
                HandlerClass = innerClass
                break
            }
        }
        if (HandlerClass != null) {
            ModuleHelper.hookAllConstructors(HandlerClass, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    val args = XposedHelpers.getArgsArray(chain)
                    try {
                        if (args.size == 2) {
                            args[1] = 0
                        }

                        result = chain.proceed(args)
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
            val methods = XposedHelpers.findMethodsByExactParameters(HandlerClass, Void.TYPE, Int::class.javaPrimitiveType!!)
            if (methods.isNotEmpty()) {
                ModuleHelper.hookMethod(methods[0], object : MethodHook() {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        var result: Any? = null
                        var throwable: Throwable? = null
                        val args = XposedHelpers.getArgsArray(chain)
                        try {
                            args[0] = 0

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
    }

    @JvmStatic
    fun PrivacyAppsLayoutHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.privacyapps.ui.PrivacyAppsActivity", lpparam.classLoader, "onCreate", Bundle::class.java, object : MethodHook() {
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

                    val act = thisObject as Activity
                    val gridViewId = act.resources.getIdentifier("privacy_apps_gridview", "id", "com.miui.securitycenter")
                    val gridView = act.findViewById<GridView>(gridViewId)
                    gridView.numColumns = 4
                    val params = gridView.layoutParams as LinearLayout.LayoutParams
                    params.rightMargin = HookUtils.dp2px(16f).toInt()
                    params.leftMargin = params.rightMargin
                    gridView.layoutParams = params
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun PersistPrivacyThumbnailBlur(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(ContentResolver::class.java, "call", Uri::class.java, String::class.java, String::class.java, Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {
                    if ((args[1] as? String) == "callPreference" && (args[2] as? String) == "GET") {
                        val extras = args[3] as Bundle?
                        if (extras != null && extras.getString("key") == "pref_key_last_upload_privacy_thumbnail_blur_time") {
                            val res = Bundle()
                            res.putLong("pref_key_last_upload_privacy_thumbnail_blur_time", java.lang.System.currentTimeMillis() - 10000)
                            skipped = true; result = res; throwable = null
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
        if (!Build.IS_INTERNATIONAL_BUILD) {
            ModuleHelper.findAndHookMethod(URLConnection::class.java, "setUseCaches", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    val thisObject = chain.thisObject
                    try {
                        val httpURLConnection = thisObject as URLConnection
                        if ("/user/cat" == httpURLConnection.url.path) {
                            (httpURLConnection as HttpURLConnection).requestMethod = "HEAD"
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
    }

    @JvmStatic
    fun NoLowBatteryWarningHook() {
        val policy = tv.withaibuild.customiuizer.mods.island.IslandBatteryWarningPolicy(MainModule.mPrefs)
        val settingHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val key = chain.getArg(1) as String
                    if (key != "low_battery_dialog_disabled" && key != "low_battery_sound") return chain.proceed()
                    if (!policy.suppress(chain.getArg(0) as? android.content.ContentResolver)) return chain.proceed()
                    if ("low_battery_dialog_disabled" == key) { skipped = true; result = 1; throwable = null }
                    else if ("low_battery_sound" == key) { skipped = true; result = null; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.hookAllMethods(Settings.Global::class.java, "getInt", settingHook)
        ModuleHelper.hookAllMethods(Settings.System::class.java, "getInt", settingHook)
        ModuleHelper.hookAllMethods(Settings.Global::class.java, "getString", settingHook)
    }

    @JvmStatic
    fun OpenByDefaultHook(lpparam: PackageReadyParam) {
        val defaultOpenTitleId = MainModule.resHooks.addFakeResource("various_open_by_default_title", R.string.various_open_by_default_title, "string")
        ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "initView", object : MethodHook() {
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

                    val act = thisObject as Activity
                    val anchorViewId = act.resources.getIdentifier("am_storage_view", "id", "com.miui.securitycenter")
                    val anchorView = act.findViewById<View>(anchorViewId)
                    val actionContainer = anchorView.parent as LinearLayout
                    val BannerItem = XposedHelpers.findClass("com.miui.appmanager.widget.AppDetailBannerItemView", lpparam.classLoader)
                    if (BannerItem != null) {
                        val childCount = actionContainer.childCount
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        val defaultView = XposedHelpers.newInstance(BannerItem, actionContainer.context, null) as LinearLayout
                        XposedHelpers.callMethod(defaultView, "setTitle", defaultOpenTitleId)
                        actionContainer.addView(defaultView, childCount - 2, lp)
                        val paddingStart = anchorView.paddingStart
                        val paddingEnd = anchorView.paddingEnd
                        defaultView.setPaddingRelative(paddingStart, 0, paddingEnd, 0)
                        defaultView.minimumHeight = anchorView.minimumHeight
                        val itemBackgroundId = act.resources.getIdentifier("am_card_bg_selector", "drawable", "com.miui.securitycenter")
                        defaultView.setBackgroundResource(itemBackgroundId)
                        defaultView.gravity = Gravity.CENTER_VERTICAL

                        defaultView.setOnClickListener {
                            ModuleHelper.guarded {
                                val intent = Intent("android.settings.APP_OPEN_BY_DEFAULT_SETTINGS")
                                val pkgName = act.intent.getStringExtra("package_name")
                                intent.data = Uri.parse("package:$pkgName")
                                act.startActivity(intent)
                            }
                        }
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun SkipSecurityScanHook(lpparam: PackageReadyParam) {
        val skipScan = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    skipped = true; result = ArrayList<Any?>(); throwable = null

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.findAndHookMethod("com.miui.securityscan.model.ModelFactory", lpparam.classLoader, "produceSystemGroupModel", Context::class.java, skipScan)
        ModuleHelper.findAndHookMethod("com.miui.securityscan.model.ModelFactory", lpparam.classLoader, "produceManualGroupModel", Context::class.java, skipScan)
        ModuleHelper.findAndHookMethod("com.miui.common.customview.ScoreTextView", lpparam.classLoader, "setScore", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {
                    args[0] = 100

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod(ContentResolver::class.java, "call", Uri::class.java, String::class.java, String::class.java, Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {
                    if ((args[1] as? String) == "callPreference" && (args[2] as? String) == "GET") {
                        val extras = args[3] as Bundle?
                        if (extras != null && extras.getString("key") == "latest_optimize_date") {
                            val res = Bundle()
                            res.putLong("latest_optimize_date", java.lang.System.currentTimeMillis() - 10000)
                            skipped = true; result = res; throwable = null
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
        ModuleHelper.findAndHookMethod("com.miui.securityscan.ui.main.MainContentFrame", lpparam.classLoader, "onClick", View::class.java, HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun DisableDefraudAppsCheck(lpparam: PackageReadyParam): FeatureInstallResult {
        val bridge = XposedHelpers.bridge
        if (bridge == null) {
            HookDiagnostics.recordDexKit("com.miui.guardprovider", "AntiDefraudAppManager", exceptionType = "bridge-null")
            return FeatureInstallResult.FAILED_TRANSIENT
        }
        return installDefraudAppsHook(
            resolve = {
                bridge.findMethod(
                    FindMethod.create()
                        .excludePackages("tmsdk", "tmsdkobf", "xcrash", "com.tencent", "com.xiaomi")
                        .matcher(MethodMatcher.create().usingStrings("getUnSystemAppList error", "AntiDefraudAppManager"))
                ).singleOrNull()?.getMethodInstance(lpparam.classLoader)
            },
            install = { method, hook -> ModuleHelper.hookMethod(method, hook) != null },
        )
    }

    internal fun installDefraudAppsHook(
        resolve: () -> Method?,
        install: (Method, MethodHook) -> Boolean,
    ): FeatureInstallResult {
        val method = try {
            resolve()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            HookDiagnostics.recordDexKit("com.miui.guardprovider", "AntiDefraudAppManager", exceptionType = t.javaClass.name)
            return FeatureInstallResult.FAILED_TRANSIENT
        }
        if (method == null) {
            HookDiagnostics.recordDexKit("com.miui.guardprovider", "AntiDefraudAppManager", noMatch = true)
            return FeatureInstallResult.FAILED_PERMANENT
        }
        return try {
            if (install(method, defraudAppsResultHook())) FeatureInstallResult.INSTALLED else FeatureInstallResult.FAILED_TRANSIENT
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            HookDiagnostics.recordDexKit("com.miui.guardprovider", "AntiDefraudAppManager", exceptionType = t.javaClass.name)
            FeatureInstallResult.FAILED_TRANSIENT
        }
    }

    internal fun defraudAppsResultHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? = ArrayList<Any?>()
    }

    @JvmStatic
    fun ShowTempInBatteryHook(lpparam: PackageReadyParam) {
        val InterceptBaseFragmentClass = XposedHelpers.findClass("com.miui.powercenter.nightcharge.SmartChargeFragment", lpparam.classLoader)
        val innerClasses = InterceptBaseFragmentClass.declaredClasses
        var HandlerClass: Class<*>? = null
        for (innerClass in innerClasses) {
            if (Handler::class.java.isAssignableFrom(innerClass)) {
                HandlerClass = innerClass
                break
            }
        }
        if (HandlerClass != null) {
            val fields = HandlerClass.declaredFields
            var fieldName: String? = null
            for (field in fields) {
                if (WeakReference::class.java.isAssignableFrom(field.type)) {
                    fieldName = field.name
                    break
                }
            }
            if (fieldName == null) {
                return
            }
            val finalFieldName = fieldName
            ModuleHelper.findAndHookMethod(HandlerClass, "handleMessage", Message::class.java, batteryTemperatureHook(finalFieldName))
        }
    }

    internal fun batteryTemperatureHook(referenceField: String) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            try {
                val thisObject = chain.thisObject

                val msg = chain.getArg(0) as Message
                if (msg.what == 1) {
                    val wk = XposedHelpers.getObjectField(thisObject, referenceField) as? WeakReference<*>
                    val frag = wk?.get()
                    if (frag == null) return result
                    updateBatteryTemperature(frag)
                }

            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
            return result
        }
    }

    internal fun updateBatteryTemperature(fragment: Any) {
        val activity = XposedHelpers.callMethod(fragment, "getActivity") as? Activity ?: return
        val battery = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED) ?: return
        if (!battery.hasExtra("temperature")) return
        val preference = XposedHelpers.callMethod(fragment, "findPreference", "reference_current_temp") ?: return
        XposedHelpers.callMethod(preference, "setText", "${battery.getIntExtra("temperature", 0) / 10}℃")
    }

    @JvmStatic
    fun DisableDockSuggestHook(lpparam: PackageReadyParam) {
        val callerUnhooker = installDockSuggestionCallerScope(lpparam.classLoader) ?: return
        val clearHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                return dockSuggestionResult(
                    dockSuggestionCallerScope.isActive()
                ) {
                    chain.proceed()
                }
            }
        }
        // Device bytecode calls the framework getter reflectively through n6.b#p/#q.
        // Only DockAppEditActivity's verified loader worker reaches q from the old whitelist.
        // BubblesSettings has no path to p or q in SecurityCenter 8.9.5-240801.1.1.
        // Other q callers and every p caller must continue receiving the cleared result.
        // Scoping n6.b#q itself would therefore broaden the old whitelist incorrectly.
        // The verified Dock loader worker is the narrowest behavior-equivalent boundary.
        // Unknown or future callers fail closed without a stack-scanning fallback.
        // Caller resolution and reflection stay on the feature-install cold path.
        // The callback hot path performs only a bounded ThreadLocal depth read.
        // The exact Context overload is verified in the target miui-framework.jar.
        // The existing mutable single-element ArrayList contract remains unchanged.
        val coreUnhooker = ModuleHelper.findAndHookMethod(
            "android.util.MiuiMultiWindowUtils", lpparam.classLoader,
            "getFreeformSuggestionList", Context::class.java, clearHook
        )
        if (coreUnhooker == null) {
            unhookDockSuggestionCallerScope(callerUnhooker)
        }
    }

    @JvmStatic
    fun AlarmCompatHook() {
        ModuleHelper.findAndHookMethod(Settings.System::class.java, "getStringForUser", ContentResolver::class.java,
            String::class.java, Int::class.javaPrimitiveType!!, alarmCompatSettingHook())
    }

    internal fun alarmCompatSettingHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            if (chain.getArg(1) != "next_alarm_formatted") return chain.proceed()
            val args = XposedHelpers.getArgsArray(chain)
            args[1] = "next_alarm_clock_formatted"
            return chain.proceed(args)
        }
    }

    @JvmStatic
    fun AlarmCompatServiceHook(lpparam: SystemServerStartingParam) {
        installVariousSnapshot()
        ModuleHelper.findAndHookMethod("com.android.server.alarm.AlarmManagerService", lpparam.classLoader, "onBootPhase", Int::class.javaPrimitiveType!!, object : MethodHook() {
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

                    if ((chain.getArg(0) as Int) != 500) { return XposedHelpers.throwOrReturn(throwable, result) }

                    val mContext = XposedHelpers.callMethod(thisObject, "getContext") as Context?
                    if (mContext == null) {
                        XposedHelpers.log("AlarmCompatServiceHook", "Context is NULL")
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    val resolver = mContext.contentResolver
                    val alarmObserver = object : ContentObserver(Handler(mContext.mainLooper)) {
                        override fun onChange(selfChange: Boolean) = ModuleHelper.guarded {
                            if (selfChange) return@guarded
                            XposedHelpers.setAdditionalInstanceField(thisObject, "mNextAlarmTime", ModuleHelper.getNextMIUIAlarmTime(mContext))
                        }
                    }
                    alarmObserver.onChange(false)
                    resolver.registerContentObserver(Settings.System.getUriFor("next_alarm_clock_formatted"), false, alarmObserver)
                    ModuleHelper.replaceModuleRegistration("alarmManager_next_alarm") { resolver.unregisterContentObserver(alarmObserver) }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.server.alarm.AlarmManagerService", lpparam.classLoader,
            "getNextAlarmClockImpl", Int::class.javaPrimitiveType!!, alarmCompatResultHook())
    }

    internal fun alarmCompatUserAllowed(uid: Int, requestedUser: Int): Boolean =
        uid >= 0 && uid / 100000 == 0 && requestedUser == 0

    internal fun alarmCompatResultHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val original = chain.proceed()
            val uid = Binder.getCallingUid()
            if (!alarmCompatUserAllowed(uid, chain.getArg(0) as Int)) return original
            return ModuleHelper.guarded<Any?>(original) {
                val owner = chain.thisObject
                val context = XposedHelpers.callMethod(owner, "getContext") as? Context ?: return@guarded original
                val pkg = context.packageManager.getNameForUid(uid) ?: return@guarded original
                if (pkg !in variousConfig.alarmCompatApps) return@guarded original
                val next = XposedHelpers.getAdditionalInstanceField(owner, "mNextAlarmTime") as? Long ?: return@guarded original
                // The legacy formatted source is owned by user 0; never publish it for
                // another caller/profile, an unrelated setting, or a failed native query.
                if (next < 0) original else if (next == 0L) null else AlarmManager.AlarmClockInfo(next, null)
            }
        }
    }

    @JvmStatic
    fun AnswerCallInHeadUpHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.incallui.InCallPresenter", lpparam.classLoader, "answerIncomingCall", Context::class.java, String::class.java, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, answerCallInHeadUpHook())
    }

    internal fun answerCallInHeadUpHook(
        foregroundPackage: () -> String? = { ProcessManager.getForegroundInfo()?.mForegroundPackageName },
    ) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            if (chain.getArg(3) != true) return chain.proceed()
            val topPackage = try {
                foregroundPackage()
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                return chain.proceed()
            }
            if (topPackage == null || topPackage == "com.miui.home") return chain.proceed()
            val args = try {
                XposedHelpers.getArgsArray(chain)
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                return chain.proceed()
            }
            args[3] = false
            return chain.proceed(args)
        }
    }

    @JvmStatic
    fun ShowCallUIHook(lpparam: PackageReadyParam) {
        installVariousSnapshot()
        ModuleHelper.hookAllMethods("com.android.incallui.InCallPresenter", lpparam.classLoader, "startUi", showCallUiHook())
    }

    internal fun showCallUiHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            try {
                val thisObject = chain.thisObject

                if (!(result as Boolean) || chain.getArg(0).toString() != "INCOMING") { return result }
                val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                if (variousConfig.showCallUi == 3) {
                    val topPackage = Settings.Global.getString(mContext.contentResolver, Helpers.modulePkg + ".foreground.package")
                    if (topPackage != null && topPackage != "com.miui.home") {
                        return result
                    }
                }

                if (variousConfig.showCallUi == 1) {
                    val fullScreen = Settings.Global.getInt(mContext.contentResolver, Helpers.modulePkg + ".foreground.fullscreen", 0)
                    if (fullScreen == 1) { return result }
                }

                XposedHelpers.callMethod(thisObject, "showInCall", false, false)
                val mStatusBarNotifier = XposedHelpers.getObjectField(thisObject, "mStatusBarNotifier")
                if (mStatusBarNotifier != null) XposedHelpers.callMethod(mStatusBarNotifier, "cancelInCall")

            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
            return result
        }
    }

    @JvmStatic
    fun InCallBrightnessHook(lpparam: PackageReadyParam) {
        installVariousSnapshot()
        ModuleHelper.findAndHookMethod("com.android.incallui.InCallActivity", lpparam.classLoader, "onCreate", Bundle::class.java, inCallBrightnessHook(lpparam.classLoader))
    }

    internal fun inCallBrightnessHook(
        classLoader: ClassLoader,
        currentTime: () -> Calendar = { Calendar.getInstance() },
    ) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            try {
                val thisObject = chain.thisObject

                val act = thisObject as Activity

                val cfg = variousConfig
                val opt = cfg.callUiBrightType
                if (opt == 1 || opt == 2) {
                    val presenter = XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.android.incallui.InCallPresenter", classLoader), "getInstance")
                    if (presenter == null) {
                        XposedHelpers.log("InCallBrightnessHook", "InCallPresenter is null")
                        return result
                    }

                    val state = XposedHelpers.callMethod(presenter, "getInCallState").toString()
                    if (opt == 1 && state != "INCOMING") { return result }
                    else if (opt == 2 && state != "OUTGOING" && state != "PENDING_OUTGOING") { return result }
                }

                if (cfg.callUiBrightNight) {
                    val start = cfg.callUiBrightNightStartHour * 60 + cfg.callUiBrightNightStartMinute
                    val end = cfg.callUiBrightNightEndHour * 60 + cfg.callUiBrightNightEndMinute
                    // Same convention as vibration periods: [start, end), equal means all day.
                    val isNight = if (start == end) true else {
                        val now = currentTime()
                        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                        if (start < end) minute >= start && minute < end else minute >= start || minute < end
                    }
                    if (isNight) return result
                }

                val params = act.window.attributes
                val brightness = cfg.callUiBrightVal
                if (brightness == 0) { return result }
                params.screenBrightness = brightness / 100f
                act.window.setAttributes(params)

            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
            return result
        }
    }

    @JvmStatic
    fun AppInfoDuringMiuiInstallHook(lpparam: PackageReadyParam) {
        val owner = XposedHelpers.findClassIfExists("com.miui.packageInstaller.ui.listcomponets.AppInfoViewObject", lpparam.classLoader) ?: return
        val holder = XposedHelpers.findClassIfExists("com.miui.packageInstaller.ui.listcomponets.AppInfoViewObject\$ViewHolder", lpparam.classLoader) ?: return
        val apk = XposedHelpers.findClassIfExists("com.miui.packageInstaller.model.ApkInfo", lpparam.classLoader) ?: return
        val access = InstallerAppInfo.prepare(owner, holder, apk) ?: return
        ModuleHelper.hookMethod(access.bind, access.hook())
    }

    @JvmStatic
    fun MiuiPackageInstallerHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethodSilently("com.miui.packageInstaller.InstallStart", lpparam.classLoader, "getCallingPackage", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    skipped = true; result = "com.android.fileexplorer"; throwable = null

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
    fun PurePackageInstallerHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("android.app.SharedPreferencesImpl", lpparam.classLoader, "getBoolean", String::class.java, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val prefKey = chain.getArg(0) as String
                    if (prefKey == "ads_enable" || prefKey == "app_store_recommend" || prefKey == "secure_verify_enable") {
                        skipped = true; result = false; throwable = null
                    } else if (prefKey == "secure_verify_cloud_once") {
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
        ModuleHelper.findAndHookMethod(Settings.System::class.java, "getInt", ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val prefKey = chain.getArg(1) as String
                    if (prefKey == "virus_scan_install") {
                        skipped = true; result = 0; throwable = null
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
        ModuleHelper.findAndHookMethod(Settings.Secure::class.java, "getInt", ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val prefKey = chain.getArg(1) as String
                    if (prefKey == "miui_safe_mode") {
                        skipped = true; result = 0; throwable = null
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
        ModuleHelper.findAndHookMethod("com.miui.packageInstaller.ui.listcomponets.SafeModeTipViewObject\$ViewHolder", lpparam.classLoader, "updateSuggestionMsgState", Context::class.java, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
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

                    val itemView = XposedHelpers.getObjectField(thisObject, "itemView") as View
                    itemView.visibility = View.GONE
                    val layoutParams2 = itemView.layoutParams
                    if (layoutParams2 == null) {
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    layoutParams2.height = 0

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun GboardPaddingHook(lpparam: PackageReadyParam) {
        installVariousSnapshot()
        ModuleHelper.findAndHookMethod(XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader), "get", String::class.java, gboardPaddingHook())
    }

    internal fun gboardPaddingHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            var skipped = false
            var result: Any? = null
            var throwable: Throwable? = null
            try {
                val key = chain.getArg(0) as String
                if (key == "ro.com.google.ime.kb_pad_port_b") {
                    val opt = variousConfig.gboardPaddingPort
                    if (opt > 0) { skipped = true; result = opt.toString(); throwable = null }
                } else if (key == "ro.com.google.ime.kb_pad_land_b") {
                    val opt = variousConfig.gboardPaddingLand
                    if (opt > 0) { skipped = true; result = opt.toString(); throwable = null }
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
    fun FixInputMethodBottomMarginHook(lpparam: PackageReadyParam): FeatureInstallResult =
        installInputMethodOuterHooks(XposedHelpers.findClassIfExists("android.inputmethodservice.InputMethodServiceInjector", lpparam.classLoader))

    internal fun installInputMethodOuterHooks(
        injector: Class<*>?,
        installHook: (Method, MethodHook) -> CustomMethodUnhooker? = ModuleHelper::hookMethod,
    ): FeatureInstallResult {
        if (injector == null) {
            ModuleHelper.hookAllMethods(injector, "addMiuiBottomView", HookerClassHelper.DO_NOTHING)
            return FeatureInstallResult.FAILED_PERMANENT
        }
        val methods = try {
            val entries = injector.declaredMethods.filter { it.name == "addMiuiBottomView" }
            if (entries.isEmpty()) {
                ModuleHelper.hookAllMethods(injector, "addMiuiBottomView", HookerClassHelper.DO_NOTHING)
                return FeatureInstallResult.FAILED_PERMANENT
            }
            val field = XposedHelpers.findField(injector, "sClassLoader")
            if (!Modifier.isStatic(field.modifiers) || !ClassLoader::class.java.isAssignableFrom(field.type)) {
                return FeatureInstallResult.FAILED_PERMANENT
            }
            entries
        } catch (missing: NoSuchFieldError) {
            XposedHelpers.log(missing)
            return FeatureInstallResult.FAILED_PERMANENT
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log(t)
            return FeatureInstallResult.FAILED_TRANSIENT
        }
        val callback = inputMethodBottomMarginHook(injector)
        return PreparedHookBatch(installHook).install(methods.map { it to callback })
    }

    internal fun inputMethodBottomMarginHook(
        injector: Class<*>,
        resolveUtil: (ClassLoader) -> Class<*>? = { XposedHelpers.findClassIfExists("com.miui.inputmethod.InputMethodUtil", it) },
        installUtil: (Class<*>) -> CustomMethodUnhooker? = {
            ModuleHelper.findAndHookMethod(it, "updateGestureLineEnable", Context::class.java, inputMethodGestureLineHook(it))
        },
    ) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            ModuleHelper.guarded {
                val loader = XposedHelpers.getStaticObjectField(injector, "sClassLoader") as? ClassLoader ?: return@guarded
                val util = resolveUtil(loader) ?: return@guarded
                // State holds no Class/loader/View: the existing weak class-owned store owns it.
                val state = synchronized(util) {
                    (XposedHelpers.getAdditionalStaticField(util, "customiuizer.ime.gestureLineInstall") as? HookInstallStateMachine)
                        ?: HookInstallStateMachine().also {
                            XposedHelpers.setAdditionalStaticField(util, "customiuizer.ime.gestureLineInstall", it)
                        }
                }
                val installedNow = state.install {
                    ModuleHelper.guarded(false) prepare@{
                        val field = XposedHelpers.findField(util, "sIsGestureLineEnable")
                        val update = XposedHelpers.findMethodExact(util, "updateGestureLineEnable", Context::class.java)
                        if (field.type != Boolean::class.javaPrimitiveType || !Modifier.isStatic(field.modifiers) || Modifier.isFinal(field.modifiers) ||
                            !Modifier.isStatic(update.modifiers) || update.returnType != Void.TYPE) return@prepare false
                        field.getBoolean(null) // Complete static initialization/access before installing.
                        val handle = installUtil(util) ?: return@prepare false
                        try {
                            field.setBoolean(null, false)
                        } catch (t: Throwable) {
                            try { handle.unhook() } catch (cleanup: Throwable) { XposedHelpers.log(cleanup) }
                            throw t
                        }
                        true
                    }
                }
                if (!installedNow && state.state == HookInstallStateMachine.State.INSTALLED) {
                    XposedHelpers.setStaticBooleanField(util, "sIsGestureLineEnable", false)
                }
            }
            return result
        }
    }

    internal fun inputMethodGestureLineHook(util: Class<*>) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            XposedHelpers.setStaticBooleanField(util, "sIsGestureLineEnable", false)
            return null
        }
    }
}
