package tv.withaibuild.customiuizer.mods

import android.content.ContentValues
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.provider.MediaStore
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import org.luckypray.dexkit.result.MethodData
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StatusBarHeightConfig
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.ArrayList
import java.util.HashSet
import tv.withaibuild.customiuizer.utils.HookUtils

object System {

    internal data class SystemMiscSnapshot(
        val forceCloseApps: Set<String> = emptySet(),
        val screenshotFormat: Int = 2,
        val screenshotPath: Int = 1,
        val screenshotMyPath: String = "",
        val screenshotQuality: Int = 100,
        val toastTime: Int = 0,
        val blockToasts: Int = 1,
        val blockToastsApps: Set<String> = emptySet(),
    )

    @Volatile
    internal var systemMiscConfig = SystemMiscSnapshot()

    private var systemMiscObserverRegistered = false

    private val SYSTEM_MISC_KEYS = setOf(
        "system_forceclose_apps",
        "system_screenshot_format",
        "system_screenshot_path",
        "system_screenshot_mypath",
        "system_screenshot_quality",
        "system_toasttime",
        "system_blocktoasts",
        "system_blocktoasts_apps",
    )

    internal fun refreshSystemMiscSnapshot() {
        val prefs = MainModule.mPrefs
        systemMiscConfig = SystemMiscSnapshot(
            forceCloseApps = HashSet(prefs.getStringSet("system_forceclose_apps")),
            screenshotFormat = prefs.getStringAsInt("system_screenshot_format", 2),
            screenshotPath = prefs.getStringAsInt("system_screenshot_path", 1),
            screenshotMyPath = prefs.getString("system_screenshot_mypath", ""),
            screenshotQuality = prefs.getInt("system_screenshot_quality", 100),
            toastTime = prefs.getInt("system_toasttime", 0),
            blockToasts = prefs.getStringAsInt("system_blocktoasts", 1),
            blockToastsApps = HashSet(prefs.getStringSet("system_blocktoasts_apps")),
        )
    }

    @JvmStatic
    internal fun installSystemMiscSnapshot() {
        refreshSystemMiscSnapshot()
        if (systemMiscObserverRegistered) return
        systemMiscObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in SYSTEM_MISC_KEYS) refreshSystemMiscSnapshot()
            }
        })
    }

    @JvmStatic
    fun ViewWifiPasswordHook(lpparam: PackageReadyParam) {
        val titleId = MainModule.resHooks.addFakeResource("system_wifipassword_btn_title", R.string.system_wifipassword_btn_title, "string")
        val dlgTitleId = MainModule.resHooks.addFakeResource("system_wifi_password_dlgtitle", R.string.system_wifi_password_dlgtitle, "string")
        ModuleHelper.hookAllMethods("com.android.settings.wifi.SavedAccessPointPreference", lpparam.classLoader, "onBindViewHolder", object : MethodHook() {
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

                    val view = XposedHelpers.getObjectField(thisObject, "mView") as View
                    val btnId = HookUtils.getResId(view.resources, "btn_delete", "id", "com.android.settings")
                    val button = view.findViewById<Button>(btnId)
                    button.setText(titleId)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        val wifiSharedKey = arrayOfNulls<String?>(1)
        val passwordTitle = arrayOfNulls<String?>(1)
        ModuleHelper.findAndHookMethod("miuix.appcompat.app.AlertDialog\$Builder", lpparam.classLoader, "setTitle", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    if (wifiSharedKey[0] != null) {
                        args[0] = dlgTitleId
                    }

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("miuix.appcompat.app.AlertDialog\$Builder", lpparam.classLoader, "setMessage", CharSequence::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    if (wifiSharedKey[0] != null) {
                        var str = args[0] as CharSequence
                        str = "$str\n${passwordTitle[0]}: ${wifiSharedKey[0]}"
                        args[0] = str
                    }

                    result = chain.proceed(args)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods("miuix.appcompat.app.AlertDialog", lpparam.classLoader, "onCreate", object : MethodHook() {
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

                    if (wifiSharedKey[0] != null) {
                        val messageView = XposedHelpers.callMethod(thisObject, "getMessageView") as TextView
                        messageView.setTextIsSelectable(true)
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.hookAllMethods("com.android.settings.wifi.MiuiSavedAccessPointsWifiSettings", lpparam.classLoader, "showDeleteDialog", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val wifiEntry = chain.getArg(0)
                    val canShare = XposedHelpers.callMethod(wifiEntry, "canShare") as Boolean
                    if (canShare) {
                        if (passwordTitle[0] == null) {
                            val modRes = ModuleHelper.getModuleRes(XposedHelpers.callMethod(thisObject, "getContext") as Context)
                            passwordTitle[0] = modRes.getString(R.string.system_wifi_password_label)
                        }
                        val mWifiManager = XposedHelpers.getObjectField(thisObject, "mWifiManager")
                        val wifiConfiguration = XposedHelpers.callMethod(wifiEntry, "getWifiConfiguration")
                        val WifiDppUtilsClass = XposedHelpers.findClass("com.android.settings.wifi.dpp.WifiDppUtils", lpparam.classLoader)
                        var sharedKey = XposedHelpers.callStaticMethod(WifiDppUtilsClass, "getPresharedKey", mWifiManager, wifiConfiguration) as String
                        sharedKey = XposedHelpers.callStaticMethod(WifiDppUtilsClass, "removeFirstAndLastDoubleQuotes", sharedKey) as String
                        wifiSharedKey[0] = sharedKey
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val wifiEntry = chain.getArg(0)
                    val canShare = XposedHelpers.callMethod(wifiEntry, "canShare") as Boolean
                    if (canShare) {
                        wifiSharedKey[0] = null
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }




    @JvmStatic
    fun StatusBarHeightHook(lpparam: PackageReadyParam) {
        val resources = ModuleHelper.findContext(lpparam)?.resources ?: Resources.getSystem()
        StatusBarHeightConfig.configure(MainModule.mPrefs, resources)

        val heightDp = StatusBarHeightConfig.configuredDp
        val pkgName = lpparam.packageName
        ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height_default", heightDp)
        ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height", heightDp)
        ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height_portrait", heightDp)
        ModuleHelper.replacePkgAndFrameworkValue(pkgName, "dimen", "status_bar_height_landscape", heightDp)
    }

    @JvmStatic
    fun HideMemoryCleanHook(lpparam: PackageReadyParam, isInLauncher: Boolean) {
        val raClass = if (isInLauncher) "com.miui.home.recents.views.RecentsContainer" else "com.android.systemui.recents.RecentsActivity"
        if (isInLauncher && XposedHelpers.findClassIfExists(raClass, lpparam.classLoader) == null) return
        ModuleHelper.findAndHookMethod(raClass, lpparam.classLoader, "setupVisible", object : MethodHook() {
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

                    val mMemoryAndClearContainer = XposedHelpers.getObjectField(thisObject, "mMemoryAndClearContainer") as ViewGroup?
                    if (mMemoryAndClearContainer != null) mMemoryAndClearContainer.visibility = View.GONE

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }



    private fun checkToast(pkgName: String): Boolean {
        try {
            val cfg = systemMiscConfig
            val isSelected = pkgName in cfg.blockToastsApps
            return (cfg.blockToasts == 2 && !isSelected) || (cfg.blockToasts == 3 && isSelected)
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            return false
        }
    }

    @JvmStatic
    fun SelectiveToastsHook(lpparam: SystemServerStartingParam) {
        installSystemMiscSnapshot()
        ModuleHelper.hookAllMethods("com.android.server.notification.NotificationManagerService", lpparam.classLoader, "tryShowToast", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val pkgName = XposedHelpers.getObjectField(chain.getArg(0), "pkg") as String?
                    if (pkgName == null) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    if (checkToast(pkgName)) { skipped = true; result = false; throwable = null }

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
    fun ForceCloseHook(lpparam: SystemServerStartingParam) {
        installSystemMiscSnapshot()
        ModuleHelper.hookAllConstructors("com.android.server.policy.BaseMiuiPhoneWindowManager", lpparam.classLoader, object : MethodHook() {
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

                    val mSystemKeyPackages = XposedHelpers.getObjectField(thisObject, "mSystemKeyPackages") as HashSet<String>
                    mSystemKeyPackages.remove("com.miui.securitycenter")
                    mSystemKeyPackages.remove("com.miui.securityadd")
                    mSystemKeyPackages.remove("com.android.phone")
                    mSystemKeyPackages.remove("com.android.mms")
                    mSystemKeyPackages.remove("com.android.contacts")
                    mSystemKeyPackages.remove("com.miui.home")
                    mSystemKeyPackages.remove("com.jeejen.family.miui")
                    mSystemKeyPackages.remove("com.miui.backup")
                    mSystemKeyPackages.remove("com.xiaomi.mihomemanager")
                    mSystemKeyPackages.addAll(systemMiscConfig.forceCloseApps)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun HideProximityWarningHook(lpparam: SystemServerStartingParam) {
        ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiScreenOnProximityLock", lpparam.classLoader, "showHint", HookerClassHelper.DO_NOTHING)
        ModuleHelper.findAndHookMethod("com.android.server.policy.MiuiScreenOnProximityLock", lpparam.classLoader, "prepareHintWindow", HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun ScreenshotConfigHook(lpparam: PackageReadyParam) {
        installSystemMiscSnapshot()
        ModuleHelper.hookAllMethods("android.content.ContentResolver", lpparam.classLoader, "update", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {

                    if (args.size != 4) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val contentValues = args[1] as ContentValues
                    var displayName = contentValues.getAsString("_display_name")
                    if (displayName != null && displayName.contains("Screenshot")) {
                        val format = systemMiscConfig.screenshotFormat
                        val ext = if (format <= 2) ".jpg" else if (format == 3) ".png" else ".webp"

                        displayName = displayName.replace(".png", "").replace(".jpg", "").replace(".webp", "") + ext
                        contentValues.put("_display_name", displayName)
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
        ModuleHelper.findAndHookMethod("android.content.ContentResolver", lpparam.classLoader, "insert", Uri::class.java, ContentValues::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                try {

                    val imgUri = args[0] as Uri
                    val contentValues = args[1] as ContentValues
                    var displayName = contentValues.getAsString("_display_name")
                    if (MediaStore.Images.Media.EXTERNAL_CONTENT_URI == imgUri && displayName != null && displayName.contains("Screenshot")) {
                        val folder = systemMiscConfig.screenshotPath
                        val dir = systemMiscConfig.screenshotMyPath
                        val format = systemMiscConfig.screenshotFormat
                        val ext = if (format <= 2) ".jpg" else if (format == 3) ".png" else ".webp"

                        var mScreenshotDir: File? = null
                        displayName = displayName.replace(".png", "").replace(".jpg", "").replace(".webp", "") + ext
                        if (folder > 1) {
                            mScreenshotDir = if (folder == 4 && !TextUtils.isEmpty(dir)) File(dir) else File(Environment.getExternalStoragePublicDirectory(if (folder == 2) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DCIM), "Screenshots")
                            if (!mScreenshotDir.exists()) mScreenshotDir.mkdirs()
                            val relativePath = mScreenshotDir.path.replace(Environment.getExternalStorageDirectory().path + File.separator, "")
                            contentValues.put("relative_path", relativePath)
                            if (contentValues.getAsString("_data") != null) {
                                contentValues.put("_data", mScreenshotDir.path + "/" + displayName)
                            }
                        }
                        contentValues.put("_display_name", displayName)
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        val format = systemMiscConfig.screenshotFormat
        if (format > 2) {
            val bridge = XposedHelpers.bridge
            if (bridge == null) {
                HookDiagnostics.recordDexKit("com.miui.screenshot", "saveBitmapToUri", exceptionType = "bridge-null")
                return
            }
            var methodCandidates: List<MethodData>? = null
            try {
                methodCandidates = bridge.findMethod(FindMethod.create()
                    .excludePackages("android", "androidx", "com.xiaomi", "com.google.json", "kotlin", "kotlinx.coroutines", "miuix")
                    .matcher(MethodMatcher.create().usingStrings("saveBitmapToUri: external storage"))
                )
            } catch (t: Throwable) {
                HookDiagnostics.recordDexKit("com.miui.screenshot", "saveBitmapToUri", exceptionType = t.javaClass.name)
                return
            }
            var methodData: MethodData? = null
            var compatibleCandidateCount = 0
            for (candidate in methodCandidates) {
                if (candidate.paramCount >= 7 &&
                    candidate.paramTypeNames[4] == Bitmap.CompressFormat::class.java.name
                ) {
                    compatibleCandidateCount++
                    methodData = candidate
                }
            }
            if (compatibleCandidateCount != 1) {
                XposedHelpers.log("ScreenshotConfigHook: expected one compatible save method, found $compatibleCandidateCount")
                HookDiagnostics.recordDexKit("com.miui.screenshot", "saveBitmapToUri", noMatch = true)
                methodData = null
            }

            val changeFormatHook = object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {

                        if (chain.args.size < 7) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                        val args = XposedHelpers.getArgsArray(chain)
                        val compress = if (format <= 2) Bitmap.CompressFormat.JPEG else if (format == 3) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.WEBP
                        args[4] = compress

                        result = chain.proceed(args)
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            }
            if (methodData != null) {
                try {
                    val method = methodData.getMethodInstance(lpparam.classLoader)
                    ModuleHelper.hookMethod(method, changeFormatHook)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
            }
        }

        ModuleHelper.hookAllMethods("android.graphics.Bitmap", lpparam.classLoader, "compress", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    var quality = chain.getArg(1) as Int
                    if (quality != 100 || (chain.getArg(2) is ByteArrayOutputStream)) { return XposedHelpers.proceedOrThrow(chain, throwable) }
                    val format2 = systemMiscConfig.screenshotFormat
                    quality = systemMiscConfig.screenshotQuality
                    if (format2 == 3) {
                        quality = 100
                    }
                    val compress = if (format2 <= 2) Bitmap.CompressFormat.JPEG else if (format2 == 3) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.WEBP
                    val args = XposedHelpers.getArgsArray(chain)
                    args[0] = compress
                    args[1] = quality

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
    fun ToastTimeHook(lpparam: SystemServerStartingParam) {
        installSystemMiscSnapshot()
        ModuleHelper.findAndHookMethod("com.android.server.notification.NotificationManagerService", lpparam.classLoader, "showNextToastLocked", object : MethodHook() {
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
                    val mHandler = XposedHelpers.getObjectField(thisObject, "mHandler") as Handler?
                    val mToastQueue = XposedHelpers.getObjectField(thisObject, "mToastQueue") as ArrayList<Any>?
                    if (mContext == null || mHandler == null || mToastQueue == null || mToastQueue.size == 0) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mod = (systemMiscConfig.toastTime - 4) * 1000
                    for (record in mToastQueue)
                        if (mHandler.hasMessages(2, record)) {
                            mHandler.removeCallbacksAndMessages(record)
                            val duration = XposedHelpers.getIntField(record, "duration")
                            val delay = Math.max(1000, (if (duration == 1) 3500 else 2000) + mod)
                            mHandler.sendMessageDelayed(Message.obtain(mHandler, 2, record), delay.toLong())
                        }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        val windowClass = "com.android.server.wm.DisplayPolicy"
        ModuleHelper.hookAllMethods(windowClass, lpparam.classLoader, "adjustWindowParamsLw", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = chain.args
                val thisObject = chain.thisObject
                try {

                    val lp = if (args.size == 1) args[0] else args[1]
                    XposedHelpers.setAdditionalInstanceField(thisObject, "mPrevHideTimeout", XposedHelpers.getLongField(lp, "hideTimeoutMilliseconds"))

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }

                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val lp = if (args.size == 1) args[0] else args[1]
                    val mPrevHideTimeout = XposedHelpers.getAdditionalInstanceField(thisObject, "mPrevHideTimeout") as Long
                    val mHideTimeout = XposedHelpers.getLongField(lp, "hideTimeoutMilliseconds")
                    if (mPrevHideTimeout == -1L || mHideTimeout == -1L) { return XposedHelpers.throwOrReturn(throwable, result) }

                    var dur = 0L
                    if (mPrevHideTimeout == 1000L || mPrevHideTimeout == 4000L || mPrevHideTimeout == 5000L || mPrevHideTimeout == 7000L || mPrevHideTimeout != mHideTimeout)
                        dur = Math.max(1000, 3500 + (systemMiscConfig.toastTime - 4) * 1000).toLong()
                    if (dur != 0L) XposedHelpers.setLongField(lp, "hideTimeoutMilliseconds", dur)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ClearAllTasksHook(lpparam: SystemServerStartingParam) {
        val wpuClass = "com.android.server.wm.WindowProcessUtils"
        ModuleHelper.hookAllMethods(wpuClass, lpparam.classLoader, "getPerceptibleRecentAppList", object : MethodHook() {
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

                    result = null; throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun GalleryScreenshotPathHook(lpparam: PackageReadyParam) {
        val MIUIStorageConstants = XposedHelpers.findClass("com.miui.gallery.storage.constants.MIUIStorageConstants", lpparam.classLoader)
        val folder = MainModule.mPrefs.getStringAsInt("system_gallery_screenshots_path", 1)
        var ssPath = ""
        if (folder == 2) {
            ssPath = Environment.DIRECTORY_PICTURES + File.separator + "Screenshots"
        } else if (folder == 3) {
            ssPath = Environment.DIRECTORY_DCIM + File.separator + "Screenshots"
        }
        if (folder > 1) {
            XposedHelpers.setStaticObjectField(MIUIStorageConstants, "DIRECTORY_SCREENSHOT_PATH", ssPath)
        }
    }

    @JvmStatic
    fun NetworkIndicatorWifi(lpparam: PackageReadyParam) {
        val hideWifiActivity = object : MethodHook() {
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

                    val mWifiActivityView = XposedHelpers.getObjectField(thisObject, "mWifiActivityView")
                    XposedHelpers.callMethod(mWifiActivityView, "setVisibility", View.INVISIBLE)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarWifiView", lpparam.classLoader, "applyWifiState", hideWifiActivity)
    }

}
