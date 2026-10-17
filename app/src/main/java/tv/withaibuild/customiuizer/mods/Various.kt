package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.Fragment as AppFragment
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
import android.graphics.Canvas
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
import android.text.SpannableStringBuilder
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
import org.luckypray.dexkit.result.MethodData
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.TimeZone
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
    var mLastPackageInfo: PackageInfo? = null

    @JvmField
    var mSupportFragment: Any? = null

    @JvmField
    val MIUI_CORE_APPS = setOf(
        "com.lbe.security.miui", "com.miui.securitycenter", "com.miui.packageinstaller", "com.miui.home"
    )

    @JvmStatic
    fun AppInfoHook(lpparam: PackageReadyParam) {
        val amaCls = XposedHelpers.findClassIfExists("com.miui.appmanager.AMAppInfomationActivity", lpparam.classLoader)
        if (amaCls == null) {
            XposedHelpers.log("AppInfoHook", "Cannot find activity class!")
            return
        }

        val xfragCls = XposedHelpers.findClassIfExists("androidx.fragment.app.Fragment", lpparam.classLoader)
        if (xfragCls != null) {
            ModuleHelper.findAndHookConstructor("androidx.fragment.app.Fragment", lpparam.classLoader, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    val thisObject = chain.thisObject
                    try {
                        try {
                            val piField = XposedHelpers.findFirstFieldByExactType(thisObject.javaClass, PackageInfo::class.java)
                            if (piField != null) mSupportFragment = thisObject
                        } catch (ignore: Throwable) {
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

        ModuleHelper.findAndHookMethod(amaCls, "onCreate", Bundle::class.java, object : MethodHook() {
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

                    val handler = Handler(Looper.getMainLooper())
                    handler.post { ModuleHelper.guarded {
                        val act = thisObject as Activity
                        val contentFrag = act.fragmentManager.findFragmentById(android.R.id.content)
                        val frag = contentFrag ?: mSupportFragment
                        if (frag == null) {
                            XposedHelpers.log("AppInfoHook", "Unable to find fragment")
                            return@post
                        }

                        val modRes = ModuleHelper.getModuleRes(act)!!
                        val piField = XposedHelpers.findFirstFieldByExactType(frag.javaClass, PackageInfo::class.java)
                        mLastPackageInfo = piField.get(frag) as PackageInfo?
                        val addPref = XposedHelpers.findMethodsByExactParameters(frag.javaClass, Void.TYPE, String::class.java, String::class.java, String::class.java)
                        if (mLastPackageInfo == null || addPref.isEmpty()) {
                            XposedHelpers.log("AppInfoHook", "Unable to find field/class/method in SecurityCenter to hook")
                            return@post
                        } else {
                            addPref[0].isAccessible = true
                        }
                        val pkgInfo = mLastPackageInfo!!
                        addPref[0].invoke(frag, "apk_versioncode", modRes.getString(R.string.appdetails_apk_version_code), pkgInfo.versionCode.toString())
                        addPref[0].invoke(frag, "apk_filename", modRes.getString(R.string.appdetails_apk_file), pkgInfo.applicationInfo!!.sourceDir)
                        addPref[0].invoke(frag, "data_path", modRes.getString(R.string.appdetails_data_path), pkgInfo.applicationInfo!!.dataDir)
                        addPref[0].invoke(frag, "app_uid", modRes.getString(R.string.appdetails_app_uid), pkgInfo.applicationInfo!!.uid.toString())
                        addPref[0].invoke(frag, "target_sdk", modRes.getString(R.string.appdetails_sdk), pkgInfo.applicationInfo!!.targetSdkVersion.toString())
                            handler.post { ModuleHelper.guarded {
                                addPref[0].invoke(frag, "open_in_store", modRes.getString(R.string.appdetails_playstore), "")
                                addPref[0].invoke(frag, "launch_app", modRes.getString(R.string.appdetails_launch), "")
                            }}

                        ModuleHelper.hookAllMethods(frag.javaClass, "onPreferenceTreeClick", object : MethodHook() {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                var skipped = false
                                var result: Any? = null
                                var throwable: Throwable? = null
                                val thisObject2 = chain.thisObject
                                try {
                                    val key = XposedHelpers.callMethod(chain.getArg(0), "getKey") as String
                                    val title = XposedHelpers.callMethod(chain.getArg(0), "getTitle") as String
                                    val pkgInfo = mLastPackageInfo!!
                                    when (key) {
                                        "apk_filename" -> {
                                            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title, pkgInfo.applicationInfo!!.sourceDir))
                                            Toast.makeText(act, act.resources.getIdentifier("app_manager_copy_pkg_to_clip", "string", act.packageName), Toast.LENGTH_SHORT).show()
                                            skipped = true; result = true; throwable = null
                                        }
                                        "data_path" -> {
                                            (act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title, pkgInfo.applicationInfo!!.dataDir))
                                            Toast.makeText(act, act.resources.getIdentifier("app_manager_copy_pkg_to_clip", "string", act.packageName), Toast.LENGTH_SHORT).show()
                                            skipped = true; result = true; throwable = null
                                        }
                                        "open_in_store" -> {
                                            try {
                                                val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkgInfo.packageName))
                                                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                                act.startActivity(launchIntent)
                                            } catch (anfe: android.content.ActivityNotFoundException) {
                                                val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkgInfo.packageName))
                                                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                                act.startActivity(launchIntent)
                                            }
                                            skipped = true; result = true; throwable = null
                                        }
                                        "launch_app" -> {
                                            val launchIntent = act.packageManager.getLaunchIntentForPackage(pkgInfo.packageName)
                                            if (launchIntent == null) {
                                                Toast.makeText(act, modRes.getString(R.string.appdetails_nolaunch), Toast.LENGTH_SHORT).show()
                                            } else {
                                                val uid = act.intent.getIntExtra("am_app_uid", -1)
                                                val user = resolveAppInfoLaunchUserId(uid) { appUid ->
                                                    XposedHelpers.callStaticMethod(UserHandle::class.java, "getUserId", appUid) as Int
                                                }
                                                if (user == null) {
                                                    XposedHelpers.log("AppInfoHook", "launch_app: invalid or missing am_app_uid=$uid; aborting launch")
                                                } else {
                                                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                                    if (user != 0) {
                                                        try {
                                                            XposedHelpers.callMethod(act, "startActivityAsUser", launchIntent, XposedHelpers.newInstance(UserHandle::class.java, user))
                                                        } catch (t: Throwable) {
                                                            FatalErrors.unwrapAndRethrowIfFatal(t)
                                                            XposedHelpers.log(t)
                                                        }
                                                    } else {
                                                        act.startActivity(launchIntent)
                                                    }
                                                }
                                            }
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
                    }}
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

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

    @JvmStatic
    fun checkBundle(context: Context?, bundle: Bundle?): Bundle? {
        if (context == null) {
            XposedHelpers.log("AppsDefaultSortHook", "Context is null!")
            return null
        }
        val b = bundle ?: Bundle()
        var order = MainModule.mPrefs.getStringAsInt("various_appsort", 1)
        order -= 1
        b.putInt("current_sory_type", order)
        b.putInt("current_sort_type", order)
        return b
    }

    @JvmStatic
    fun AppsDefaultSortHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.appmanager.AppManagerMainActivity", lpparam.classLoader, "onCreate", Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                val thisObject = chain.thisObject
                try {
                    args[0] = checkBundle(thisObject as Context, args[0] as Bundle?)

                    var fragCls: String? = null
                    val xfragCls = XposedHelpers.findClassIfExists("androidx.fragment.app.Fragment", lpparam.classLoader)
                    val fields = thisObject.javaClass.declaredFields
                    for (field in fields)
                        if (AppFragment::class.java.isAssignableFrom(field.type) ||
                            (xfragCls != null && xfragCls.isAssignableFrom(field.type))) {
                            fragCls = field.type.canonicalName
                            break
                        }

                    if (fragCls != null)
                        ModuleHelper.hookAllMethods(fragCls, lpparam.classLoader, "onActivityCreated", object : MethodHook() {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                var result: Any? = null
                                var throwable: Throwable? = null
                                val args2 = XposedHelpers.getArgsArray(chain)
                                val thisObject2 = chain.thisObject
                                try {
                                    try {
                                        args2[0] = checkBundle(XposedHelpers.callMethod(thisObject2, "getContext") as Context, args2[0] as Bundle?)
                                    } catch (t: Throwable) {
                                        XposedHelpers.log("AppsDefaultSortHook", t.message)
                                    }

                                    result = chain.proceed(args2)
                                } catch (t: Throwable) {
                                    throwable = t
                                    result = null
                                }
                                return XposedHelpers.throwOrReturn(throwable, result)
                            }
                        })

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    private fun setAppState(act: Activity, pkgName: String, item: MenuItem, enable: Boolean) {
        ModuleHelper.guarded {
            val pm = act.packageManager
            pm.setApplicationEnabledSetting(pkgName, if (enable) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0)
            val state = pm.getApplicationEnabledSetting(pkgName)
            val isEnabledOrDefault = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            if ((enable && isEnabledOrDefault) || (!enable && !isEnabledOrDefault)) {
                item.setTitle(act.resources.getIdentifier(if (enable) "app_manager_disable_text" else "app_manager_enable_text", "string", "com.miui.securitycenter"))
                Toast.makeText(act, act.resources.getIdentifier(if (enable) "app_manager_enabled" else "app_manager_disabled", "string", "com.miui.securitycenter"), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(act, ModuleHelper.getModuleRes(act)!!.getString(R.string.disable_app_fail), Toast.LENGTH_LONG).show()
            }
            Handler(Looper.getMainLooper()).postDelayed({ ModuleHelper.guarded { act.invalidateOptionsMenu() } }, 500)
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
                    val menu = chain.getArg(0) as Menu
                    val dis = menu.add(0, 666, 1, act.resources.getIdentifier("app_manager_disable_text", "string", lpparam.packageName))
                    dis.setIcon(act.resources.getIdentifier("action_button_stop", "drawable", lpparam.packageName))
                    dis.isEnabled = true
                    dis.setShowAsAction(1)

                    val pm = act.packageManager
                    val piField = XposedHelpers.findFirstFieldByExactType(act.javaClass, PackageInfo::class.java)
                    val mPackageInfo = piField.get(act) as PackageInfo
                    val appInfo = pm.getApplicationInfo(mPackageInfo.packageName, PackageManager.GET_META_DATA)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                    dis.setTitle(act.resources.getIdentifier(if (appInfo.enabled) "app_manager_disable_text" else "app_manager_enable_text", "string", lpparam.packageName))

                    if (!appInfo.enabled || (isSystem && !isUpdatedSystem)) {
                        val item = menu.findItem(2)
                        item?.isVisible = false
                    }
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.appmanager.ApplicationsDetailsActivity", lpparam.classLoader, "onOptionsItemSelected", MenuItem::class.java, object : MethodHook() {
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

                    val item = chain.getArg(0) as MenuItem
                    if (item.itemId != 666) { return XposedHelpers.throwOrReturn(throwable, result) }

                    val act = thisObject as Activity
                    val modRes = ModuleHelper.getModuleRes(act)!!
                    val piField = XposedHelpers.findFirstFieldByExactType(act.javaClass, PackageInfo::class.java)
                    val mPackageInfo = piField.get(act) as PackageInfo
                    if (MIUI_CORE_APPS.contains(mPackageInfo.packageName)) {
                        Toast.makeText(act, modRes.getString(R.string.disable_app_settings), Toast.LENGTH_SHORT).show()
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }

                    val pm = act.packageManager
                    val appInfo = pm.getApplicationInfo(mPackageInfo.packageName, PackageManager.GET_META_DATA)
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val state = pm.getApplicationEnabledSetting(mPackageInfo.packageName)
                    val isEnabledOrDefault = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                    if (isEnabledOrDefault) {
                        if (isSystem) {
                            val title = modRes.getString(R.string.disable_app_title)
                            val text = modRes.getString(R.string.disable_app_text)
                            AlertDialog.Builder(act).setTitle(title).setMessage(text).setPositiveButton(android.R.string.ok) { _, _ ->
                                ModuleHelper.guarded { setAppState(act, mPackageInfo.packageName, item, false) }
                            }.setNegativeButton(android.R.string.cancel, null).show()
                        } else setAppState(act, mPackageInfo.packageName, item, false)
                    } else setAppState(act, mPackageInfo.packageName, item, true)
                    result = true; throwable = null
                } catch (t: Throwable) {
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
            ModuleHelper.hookMethod(mGetAppInfo[0], object : MethodHook() {
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
                        val args = chain.args
                        if ((args[3] as Int) == 128 && (args[4] as Int) == 0) {
                            val appInfo = result as ApplicationInfo
                            appInfo.flags = appInfo.flags and ApplicationInfo.FLAG_SYSTEM.inv()
                            result = appInfo; throwable = null
                        }
                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })

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
        val isHooked = booleanArrayOf(false, false)
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
                        } else {
                            registeredReceiver[0] = null
                            registeredOwnerView[0] = null
                            isHooked[0] = false
                        }

                        if (!isHooked[1]) {
                            isHooked[1] = true
                            val myhandler = Handler(Looper.myLooper()!!)
                            val removeBg = Runnable { ModuleHelper.guarded {
                                myhandler.removeCallbacks(this as Runnable)
                                if (!enableSideBar) {
                                    val li = XposedHelpers.getObjectField(view, "mListenerInfo")
                                    val mOnTouchListener = XposedHelpers.getObjectField(li, "mOnTouchListener")
                                    ModuleHelper.findAndHookMethod(mOnTouchListener.javaClass, "onTouch", View::class.java, MotionEvent::class.java, object : MethodHook() {
                                        override fun intercept(chain: XposedInterface.Chain): Any? {
                                            var skipped = false
                                            var result: Any? = null
                                            var throwable: Throwable? = null
                                            try {
                                                val me = chain.getArg(1) as MotionEvent
                                                if (me.source != 9999) {
                                                    skipped = true; result = false; throwable = null
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
                                    val bgDrawable = view.background!!.javaClass
                                    ModuleHelper.findAndHookMethod(bgDrawable, "draw", Canvas::class.java, object : MethodHook() {
                                        override fun intercept(chain: XposedInterface.Chain): Any? {
                                            var skipped = false
                                            var result: Any? = null
                                            var throwable: Throwable? = null
                                            try {
                                                skipped = true; result = null; throwable = null

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
                                    view.background = null
                                }
                            }}
                            myhandler.postDelayed(removeBg, 150)
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
        val settingHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val key = chain.getArg(1) as String
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
    fun DisableDefraudAppsCheck(lpparam: PackageReadyParam) {
        val bridge = XposedHelpers.bridge
        if (bridge == null) {
            HookDiagnostics.recordDexKit("com.miui.guardprovider", "AntiDefraudAppManager", exceptionType = "bridge-null")
            return
        }
        var methodData: MethodData? = null
        try {
            methodData = bridge.findMethod(
                FindMethod.create()
                    .excludePackages("tmsdk", "tmsdkobf", "xcrash", "com.tencent", "com.xiaomi")
                    .matcher(MethodMatcher.create().usingStrings("getUnSystemAppList error", "AntiDefraudAppManager"))
            ).singleOrNull()
        } catch (t: Throwable) {
            HookDiagnostics.recordDexKit("com.miui.guardprovider", "AntiDefraudAppManager", exceptionType = t.javaClass.name)
            return
        }

        if (methodData == null) {
            HookDiagnostics.recordDexKit("com.miui.guardprovider", "AntiDefraudAppManager", noMatch = true)
            return
        }

        val fakeUserAppsHook = object : MethodHook() {
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
            try {
                val method = methodData.getMethodInstance(lpparam.classLoader)
                ModuleHelper.hookMethod(method, fakeUserAppsHook)
            } catch (ign: Throwable) {
            }
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
            ModuleHelper.findAndHookMethod(HandlerClass, "handleMessage", Message::class.java, object : MethodHook() {
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

                        val msg = chain.getArg(0) as Message
                        if (msg.what == 1) {
                            val wk = XposedHelpers.getObjectField(thisObject, finalFieldName)
                            val frag = XposedHelpers.callMethod(wk, "get")
                            if (frag == null) return XposedHelpers.throwOrReturn(throwable, result)
                            val batteryView = XposedHelpers.callMethod(frag, "getActivity") as Activity
                            val temp = (batteryView.registerReceiver(null, IntentFilter("android.intent.action.BATTERY_CHANGED"), Context.RECEIVER_NOT_EXPORTED)?.getIntExtra("temperature", 0) ?: 0) / 10
                            val tempPreference = XposedHelpers.callMethod(frag, "findPreference", "reference_current_temp")
                            XposedHelpers.callMethod(tempPreference, "setText", "${temp}℃")
                        }

                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        }
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
        ModuleHelper.findAndHookMethod(Settings.System::class.java, "getStringForUser", ContentResolver::class.java, String::class.java, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {
                    val key = args[1] as String
                    if ("next_alarm_formatted" == key) {
                        args[1] = "next_alarm_clock_formatted"
                    }

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

        ModuleHelper.findAndHookMethod("com.android.server.alarm.AlarmManagerService", lpparam.classLoader, "getNextAlarmClockImpl", Int::class.javaPrimitiveType!!, object : MethodHook() {
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

                    val mContext = XposedHelpers.callMethod(thisObject, "getContext") as Context?
                    if (mContext != null) {
                        val pkgName = mContext.packageManager.getNameForUid(Binder.getCallingUid())
                        val mNextAlarmTime = XposedHelpers.getAdditionalInstanceField(thisObject, "mNextAlarmTime")
                        val set = variousConfig.alarmCompatApps
                        if (mNextAlarmTime != null && pkgName != null && set.contains(pkgName)) {
                            result = if (mNextAlarmTime as Long == 0L) null else AlarmManager.AlarmClockInfo(mNextAlarmTime as Long, null)
                            throwable = null
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
    fun AnswerCallInHeadUpHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.incallui.InCallPresenter", lpparam.classLoader, "answerIncomingCall", Context::class.java, String::class.java, Int::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {
                    val showUi = args[3] as Boolean
                    if (showUi) {
                        val foregroundInfo = ProcessManager.getForegroundInfo()
                        if (foregroundInfo != null) {
                            val topPackage = foregroundInfo.mForegroundPackageName
                            if (topPackage != "com.miui.home") {
                                args[3] = false
                            }
                        }
                    }

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
    fun ShowCallUIHook(lpparam: PackageReadyParam) {
        installVariousSnapshot()
        ModuleHelper.hookAllMethods("com.android.incallui.InCallPresenter", lpparam.classLoader, "startUi", object : MethodHook() {
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

                    if (!(result as Boolean) || chain.getArg(0).toString() != "INCOMING") { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    if (variousConfig.showCallUi == 3) {
                        val topPackage = Settings.Global.getString(mContext.contentResolver, Helpers.modulePkg + ".foreground.package")
                        if (topPackage != null && topPackage != "com.miui.home") {
                            return XposedHelpers.throwOrReturn(throwable, result)
                        }
                    }

                    if (variousConfig.showCallUi == 1) {
                        val fullScreen = Settings.Global.getInt(mContext.contentResolver, Helpers.modulePkg + ".foreground.fullscreen", 0)
                        if (fullScreen == 1) { return XposedHelpers.throwOrReturn(throwable, result) }
                    }

                    XposedHelpers.callMethod(thisObject, "showInCall", false, false)
                    val mStatusBarNotifier = XposedHelpers.getObjectField(thisObject, "mStatusBarNotifier")
                    if (mStatusBarNotifier != null) XposedHelpers.callMethod(mStatusBarNotifier, "cancelInCall")
                    result = true; throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun InCallBrightnessHook(lpparam: PackageReadyParam) {
        installVariousSnapshot()
        ModuleHelper.findAndHookMethod("com.android.incallui.InCallActivity", lpparam.classLoader, "onCreate", Bundle::class.java, object : MethodHook() {
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

                    val opt = variousConfig.callUiBrightType
                    if (opt == 1 || opt == 2) {
                        val presenter = XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.android.incallui.InCallPresenter", lpparam.classLoader), "getInstance")
                        if (presenter == null) {
                            XposedHelpers.log("InCallBrightnessHook", "InCallPresenter is null")
                            return XposedHelpers.throwOrReturn(throwable, result)
                        }

                        val state = XposedHelpers.callMethod(presenter, "getInCallState").toString()
                        if (opt == 1 && state != "INCOMING") { return XposedHelpers.throwOrReturn(throwable, result) }
                        else if (opt == 2 && state != "OUTGOING" && state != "PENDING_OUTGOING") { return XposedHelpers.throwOrReturn(throwable, result) }
                    }

                    val cfg = variousConfig
                    val checkNight = cfg.callUiBrightNight
                    if (checkNight) {
                        val start_hour = cfg.callUiBrightNightStartHour
                        val start_minute = cfg.callUiBrightNightStartMinute
                        val end_hour = cfg.callUiBrightNightEndHour
                        val end_minute = cfg.callUiBrightNightEndMinute

                        val formatter = SimpleDateFormat("H:m", Locale.ENGLISH)
                        formatter.timeZone = TimeZone.getDefault()
                        val start = formatter.parse("$start_hour:$start_minute")
                        val end = formatter.parse("$end_hour:$end_minute")
                        val now = formatter.parse(formatter.format(Date()))
                        if (start == null || end == null || now == null) { return XposedHelpers.throwOrReturn(throwable, result) }

                        val isNight = if (start.before(end)) now.after(start) && now.before(end) else now.before(end) || now.after(start)
                        if (isNight) { return XposedHelpers.throwOrReturn(throwable, result) }
                    }

                    val params = act.window.attributes
                    val brightness = variousConfig.callUiBrightVal
                    if (brightness == 0) { return XposedHelpers.throwOrReturn(throwable, result) }
                    params.screenBrightness = brightness / 100f
                    act.window.setAttributes(params)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun AppInfoDuringMiuiInstallHook(lpparam: PackageReadyParam) {
        val AppInfoViewObjectClass = XposedHelpers.findClassIfExists("com.miui.packageInstaller.ui.listcomponets.AppInfoViewObject", lpparam.classLoader)
        if (AppInfoViewObjectClass != null) {
            val ViewHolderClass = XposedHelpers.findClassIfExists("com.miui.packageInstaller.ui.listcomponets.AppInfoViewObject\$ViewHolder", lpparam.classLoader)
            val methods = XposedHelpers.findMethodsByExactParameters(AppInfoViewObjectClass, Void.TYPE, ViewHolderClass)
            if (methods.isEmpty()) {
                XposedHelpers.log("AppInfoDuringMiuiInstallHook", "Cannot find appropriate method")
                return
            }
            val ApkInfoClass = XposedHelpers.findClassIfExists("com.miui.packageInstaller.model.ApkInfo", lpparam.classLoader)

            val fields = AppInfoViewObjectClass.declaredFields
            var apkInfoFieldName: String? = null
            for (field in fields)
                if (ApkInfoClass.isAssignableFrom(field.type)) {
                    apkInfoFieldName = field.name
                    break
                }
            if (apkInfoFieldName == null) return
            val finalApkInfoFieldName = apkInfoFieldName
            ModuleHelper.hookMethod(methods[0], object : MethodHook() {
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

                        val viewHolder = chain.getArg(0) ?: return XposedHelpers.throwOrReturn(throwable, result)
                        val tvAppVersion = XposedHelpers.callMethod(viewHolder, "getTvDes") as TextView?
                        val tvAppSize = XposedHelpers.callMethod(viewHolder, "getAppSize") as TextView?
                        val tvAppName = XposedHelpers.callMethod(viewHolder, "getTvAppName") as TextView?
                        if (tvAppVersion == null) { return XposedHelpers.throwOrReturn(throwable, result) }

                        val appNameLp = tvAppName!!.layoutParams as ViewGroup.MarginLayoutParams
                        appNameLp.topMargin = 0
                        tvAppName.layoutParams = appNameLp

                        val apkInfo = XposedHelpers.getObjectField(thisObject, finalApkInfoFieldName)
                        val mAppInfo = XposedHelpers.callMethod(apkInfo, "getInstalledPackageInfo") as ApplicationInfo?
                        val mPkgInfo = XposedHelpers.callMethod(apkInfo, "getPackageInfo") as PackageInfo
                        val modRes = ModuleHelper.getModuleRes(tvAppVersion.context)!!
                        val builder = SpannableStringBuilder()
                        builder.append(modRes.getString(R.string.various_installappinfo_vername)).append(": ")
                        if (mAppInfo != null) builder.append(XposedHelpers.callMethod(apkInfo, "getInstalledVersionName") as String).append(" ➟ ")
                        builder.append(mPkgInfo.versionName).append("\n")
                        builder.append(tvAppSize!!.text).append("\n")
                        builder.append(modRes.getString(R.string.various_installappinfo_vercode)).append(": ")
                        if (mAppInfo != null) builder.append(XposedHelpers.callMethod(apkInfo, "getInstalledVersionCode").toString()).append(" ➟ ")
                        builder.append(mPkgInfo.getLongVersionCode().toString()).append("\n")
                        builder.append(modRes.getString(R.string.various_installappinfo_sdk)).append(": ")
                        if (mAppInfo != null) builder.append(mAppInfo.minSdkVersion.toString()).append("-").append(mAppInfo.targetSdkVersion.toString()).append(" ➟ ")
                        builder.append(mPkgInfo.applicationInfo!!.minSdkVersion.toString()).append("-").append(mPkgInfo.applicationInfo!!.targetSdkVersion.toString())

                        tvAppVersion.text = builder
                        tvAppVersion.setSingleLine(false)
                        tvAppVersion.maxLines = 10
                        val layout = tvAppVersion.parent as LinearLayout
                        val versionSizeLp = layout.layoutParams as ViewGroup.MarginLayoutParams
                        versionSizeLp.topMargin = 0
                        layout.layoutParams = versionSizeLp
                        layout.removeAllViews()
                        layout.addView(tvAppVersion)

                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        }
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
        ModuleHelper.findAndHookMethod(XposedHelpers.findClass("android.os.SystemProperties", lpparam.classLoader), "get", String::class.java, object : MethodHook() {
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
        })
    }

    @JvmStatic
    fun FixInputMethodBottomMarginHook(lpparam: PackageReadyParam) {
        val InputMethodServiceInjectorClass = XposedHelpers.findClassIfExists("android.inputmethodservice.InputMethodServiceInjector", lpparam.classLoader)
        ModuleHelper.hookAllMethods(InputMethodServiceInjectorClass, "addMiuiBottomView", object : MethodHook() {
            private var isHooked = false
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

                    if (isHooked) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val sClassLoader = XposedHelpers.getStaticObjectField(InputMethodServiceInjectorClass, "sClassLoader") as ClassLoader?
                    if (sClassLoader != null) {
                        isHooked = true
                        val InputMethodUtil = XposedHelpers.findClassIfExists("com.miui.inputmethod.InputMethodUtil", sClassLoader)
                        if (InputMethodUtil != null) {
                            XposedHelpers.setStaticBooleanField(InputMethodUtil, "sIsGestureLineEnable", false)
                            ModuleHelper.findAndHookMethod(InputMethodUtil, "updateGestureLineEnable", Context::class.java, object : MethodHook() {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    var skipped = false
                                    var result: Any? = null
                                    var throwable: Throwable? = null
                                    try {
                                        XposedHelpers.setStaticBooleanField(InputMethodUtil, "sIsGestureLineEnable", false)
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
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }
}
