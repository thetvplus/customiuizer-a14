package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.view.ViewGroup
import android.view.Window
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Folder and app drawer hooks.
 * Column count and width, blur, the privacy folder, and closing on launch.
 */
object LauncherFolderHooks {

    private const val PREF_FOLDER_WIDTH = "launcher_folderwidth"
    private const val PREF_FOLDER_BLUR = "launcher_folderblur_opacity"
    private const val PREF_FOLDER_BLUR_DISABLED = "launcher_folderblur_disable"
    private const val PREF_CLOSE_FOLDERS = "launcher_closefolders"
    private const val PREF_FOLDER_COLS = "launcher_folder_cols"
    private const val PREF_FOLDER_SPACE = "launcher_folderspace"
    private const val PREF_PRIVACY_GEST = "launcher_privacyapps_gest"
    private const val PREF_CLOSE_DRAWER = "launcher_closedrawer"

    /**
     * Folder state read from layout and blur callbacks.
     *
     * `Folder.onLayout` and `BlurUtils.getLauncherBlur` run per frame during folder animations,
     * so they read these primitives instead of the preference map. Both are refreshed only when
     * the corresponding preference changes.
     */
    @Volatile
    private var folderWidthEnabled = false

    @Volatile
    private var folderBlurRatio = 0f

    @Volatile
    private var folderBlurOverrideEnabled = false

    @Volatile
    private var closeFoldersMode = 1

    @Volatile
    private var folderCols = 1

    @Volatile
    private var folderSpace = false

    @Volatile
    private var privacyGest = false

    @Volatile
    private var closeDrawer = false

    @Volatile
    private var folderPreferenceObserverRegistered = false

    /**
     * Last class probed for folder-blur host methods. Only the most recent Class is kept so
     * the cache stays bounded; the launcher window context is one class for the process.
     */
    @Volatile
    private var folderHostClass: Class<*>? = null

    @Volatile
    private var folderHostMatch = false

    @Volatile
    private var folderOpenedMethod: Method? = null

    @Volatile
    private var folderOpenedResolved = false

    private val folderOpenedResolveLock = Any()

    internal fun refreshFolderPreferences() {
        folderWidthEnabled = MainModule.mPrefs.getBoolean(PREF_FOLDER_WIDTH)
        val disabled = MainModule.mPrefs.getBoolean(PREF_FOLDER_BLUR_DISABLED)
        val opacityPercent = MainModule.mPrefs.getInt(PREF_FOLDER_BLUR, 0)
        folderBlurRatio = resolveFolderBlurRatio(disabled, opacityPercent)
        folderBlurOverrideEnabled = resolveFolderBlurOverrideEnabled(disabled, opacityPercent)
        closeFoldersMode = MainModule.mPrefs.getStringAsInt(PREF_CLOSE_FOLDERS, 1)
        folderCols = MainModule.mPrefs.getInt(PREF_FOLDER_COLS, 1)
        folderSpace = MainModule.mPrefs.getBoolean(PREF_FOLDER_SPACE)
        privacyGest = MainModule.mPrefs.getBoolean(PREF_PRIVACY_GEST)
        closeDrawer = MainModule.mPrefs.getBoolean(PREF_CLOSE_DRAWER)
    }

    @JvmStatic
    internal fun installFolderPreferenceSnapshot() {
        refreshFolderPreferences()
        if (folderPreferenceObserverRegistered) return
        folderPreferenceObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (
                    key == null ||
                    key == PREF_FOLDER_WIDTH ||
                    key == PREF_FOLDER_BLUR ||
                    key == PREF_FOLDER_BLUR_DISABLED ||
                    key == PREF_CLOSE_FOLDERS ||
                    key == PREF_FOLDER_COLS ||
                    key == PREF_FOLDER_SPACE ||
                    key == PREF_PRIVACY_GEST ||
                    key == PREF_CLOSE_DRAWER
                ) {
                    refreshFolderPreferences()
                }
            }
        })
    }

    @JvmStatic
    fun CloseFolderOnLaunchHook(lpparam: PackageReadyParam) {
        installFolderPreferenceSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "launch", "com.miui.home.launcher.ShortcutInfo", View::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.getThisObject()

                    if (closeFoldersMode != 2) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mHasLaunchedAppFromFolder = XposedHelpers.getBooleanField(thisObject, "mHasLaunchedAppFromFolder")
                    if (mHasLaunchedAppFromFolder) XposedHelpers.callMethod(thisObject, "closeFolder")

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun FolderColumnsRes(folderCols: Int) {
        MainModule.getResourceHooks().setThemeValueReplacement("com.miui.home", "integer", "config_folder_columns_count", folderCols)
    }

    internal fun applyFolderWidth(content: View) {
        val params = content.layoutParams ?: return
        if (params.width == ViewGroup.LayoutParams.MATCH_PARENT) return
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        content.layoutParams = params
    }

    internal fun layoutFolderFakeIcon(content: View, icon: View) {
        val left = content.left
        val top = content.top
        val right = content.right
        val bottom = top + content.width
        if (icon.isLayoutRequested || icon.left != left || icon.top != top ||
            icon.right != right || icon.bottom != bottom
        ) {
            icon.layout(left, top, right, bottom)
        }
    }

    @JvmStatic
    fun FolderColumnsHook(lpparam: PackageReadyParam) {
        installFolderPreferenceSnapshot()
        val folder = XposedHelpers.findClass("com.miui.home.launcher.Folder", lpparam.classLoader)
        val content = XposedHelpers.findFieldIfExists(folder, "mContent")
        val background = XposedHelpers.findFieldIfExists(folder, "mBackgroundView")
        val fakeIcon = XposedHelpers.findFieldIfExists(folder, "mFakeIcon")

        ModuleHelper.findAndHookMethod(folder, "onFinishInflate", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                ModuleHelper.guarded {
                    val owner = chain.thisObject
                    if (folderWidthEnabled) (content?.get(owner) as? View)?.let(::applyFolderWidth)
                    if (folderCols > 3 && folderSpace) {
                        val view = background?.get(owner) as? View ?: return@guarded
                        view.setPadding(view.paddingLeft / 3, view.paddingTop, view.paddingRight / 3, view.paddingBottom)
                    }
                }
                return result
            }
        })

        if (content == null) return
        ModuleHelper.findAndHookMethod(folder, "resetViewsLayoutParams", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val result = chain.proceed()
                if (folderWidthEnabled) ModuleHelper.guarded {
                    (content.get(chain.thisObject) as? View)?.let(::applyFolderWidth)
                }
                return result
            }
        })

        if (fakeIcon == null) return
        ModuleHelper.findAndHookMethod(folder, "onLayout",
            Boolean::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
            folderLayoutHook(content, fakeIcon))
    }

    internal fun folderLayoutHook(content: Field, fakeIcon: Field) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            if (folderWidthEnabled) ModuleHelper.guarded {
                val owner = chain.thisObject
                val grid = content.get(owner) as? View ?: return@guarded
                val icon = fakeIcon.get(owner) as? View ?: return@guarded
                layoutFolderFakeIcon(grid, icon)
            }
            return result
        }
    }

    @JvmStatic
    fun PrivacyFolderHook(lpparam: PackageReadyParam) {
        installFolderPreferenceSnapshot()
        if (privacyGest) {
            ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "registerBroadcastReceivers", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    try {
                        val thisObject = chain.getThisObject()

                        val act = thisObject as Activity
                        val intentFilter = IntentFilter()
                        intentFilter.addAction("android.telephony.action.SECRET_CODE")
                        intentFilter.addDataAuthority("233233", null)
                        intentFilter.addDataScheme("android_secret_code")

                        val secretCodeReceiver = ModuleHelper.registerOwnedReceiver(
                            act,
                            act,
                            "secretCodeReceiver",
                            intentFilter,
                            Context.RECEIVER_NOT_EXPORTED
                        ) { _, owner, _, intent ->
                            ModuleHelper.guarded {
                                if ("android.telephony.action.SECRET_CODE" == intent.action) {
                                    XposedHelpers.setAdditionalInstanceField(owner, "fromSecretCode", true)
                                    XposedHelpers.callMethod(owner, "startSecurityHide")
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
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "startSecurityHide", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    if (XposedHelpers.getAdditionalInstanceField(thisObject, "fromSecretCode") != null) {
                        XposedHelpers.removeAdditionalInstanceField(thisObject, "fromSecretCode")
                        return XposedHelpers.proceedOrThrow(chain, throwable)
                    }
                    if (GlobalActions.handleAction(thisObject as Activity, "launcher_spread")) {
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                    val opt = privacyGest
                    if (opt) { skipped = true; result = null; throwable = null }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onDestroy", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    val act = chain.getThisObject() as Activity
                    ModuleHelper.unregisterOwnedReceiver(act, "secretCodeReceiver")
                    ModuleHelper.unregisterOwnedReceiver(act, "fetchAppConfigReceiver")
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
    fun FolderBlurHook(lpparam: PackageReadyParam) {
        val BlurUtils = XposedHelpers.findClassIfExists("com.miui.home.launcher.common.BlurUtils", lpparam.classLoader)
        if (BlurUtils != null) {
            installFolderPreferenceSnapshot()
            ModuleHelper.hookAllMethods(BlurUtils, "fastBlurWhenOpenOrCloseFolder", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (!folderBlurOverrideEnabled) {
                        return XposedHelpers.proceedOrThrow(chain, null)
                    }
                    try {
                        val launcher = chain.getArg(0)
                        val withAnimation = readWithAnimation(chain)
                        val applied = resolveAppliedFolderBlurRatio(
                            folderBlurOverrideEnabled,
                            isFolderActiveForBlur(launcher),
                            folderBlurRatio
                        )
                        val window = (launcher as? Activity)?.window
                        if (applied != null && window != null) {
                            XposedHelpers.callStaticMethod(
                                BlurUtils,
                                "fastBlur",
                                applied,
                                window,
                                withAnimation
                            )
                            return null
                        }
                    } catch (t: Throwable) {
                        FatalErrors.rethrowIfFatal(t)
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.proceedOrThrow(chain, null)
                }
            })
            ModuleHelper.hookAllMethods(BlurUtils, "getLauncherBlur", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var skipped = false
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {

                        if (shouldClampFolderFastBlur(folderBlurOverrideEnabled, isFolderActiveForBlur(chain.getArg(0)))) {
                            skipped = true
                            result = folderBlurRatio
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
            ModuleHelper.hookAllMethods(BlurUtils, "fastBlur", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (!folderBlurOverrideEnabled) {
                        return XposedHelpers.proceedOrThrow(chain, null)
                    }
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        val host = launcherFromFastBlurChain(chain)
                        if (shouldClampFolderFastBlur(folderBlurOverrideEnabled, isFolderActiveForBlur(host))) {
                            val args = XposedHelpers.getArgsArray(chain)
                            if (args.isNotEmpty() && args[0] is Float) {
                                args[0] = folderBlurRatio
                                return XposedHelpers.proceedOrThrow(chain, args, null)
                            }
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

            ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "cancelShortcutMenu", Int::class.javaPrimitiveType!!, "com.miui.home.launcher.shortcuts.CancelShortcutMenuReason", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    try {
                        val thisObject = chain.getThisObject()

                        if (shouldClampFolderFastBlur(folderBlurOverrideEnabled, isFolderActiveForBlur(thisObject))) {
                            val launcher = thisObject as Activity
                            XposedHelpers.callStaticMethod(BlurUtils, "fastBlur", folderBlurRatio, launcher.window, true)
                        }

                    } catch (t: Throwable) {
                        FatalErrors.rethrowIfFatal(t)
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        }
    }

    @JvmStatic
    internal fun resolveFolderBlurRatio(disabled: Boolean, opacityPercent: Int): Float =
        if (disabled) 0f else opacityPercent.coerceIn(0, 100) / 100f

    @JvmStatic
    internal fun resolveFolderBlurOverrideEnabled(disabled: Boolean, opacityPercent: Int): Boolean =
        disabled || opacityPercent > 0

    /**
     * Open/close entry: when the override is on, never fall through to HyperOS default blur.
     * An active folder uses the requested ratio; a closing or drag-gap frame uses 0.
     */
    @JvmStatic
    internal fun resolveAppliedFolderBlurRatio(
        overrideEnabled: Boolean,
        folderActive: Boolean,
        folderBlurRatio: Float
    ): Float? {
        if (!overrideEnabled) return null
        return if (folderActive) folderBlurRatio else 0f
    }

    @JvmStatic
    internal fun shouldClampFolderFastBlur(overrideEnabled: Boolean, folderActive: Boolean): Boolean =
        overrideEnabled && folderActive

    /**
     * Folder drag keeps the folder logically open while `isFolderShowing` can flicker.
     * Treat either flag as active so in-folder icon drags cannot briefly apply the
     * HyperOS default blur.
     */
    @JvmStatic
    internal fun isFolderActiveForBlur(host: Any?): Boolean {
        if (host == null || !isFolderHost(host)) return false
        try {
            if (XposedHelpers.callMethod(host, "isFolderShowing") as Boolean) return true
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            return false
        }
        return invokeFolderOpened(host)
    }

    private fun readWithAnimation(chain: XposedInterface.Chain): Boolean {
        return try {
            chain.getArg(1) as? Boolean ?: true
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            true
        }
    }

    private fun launcherFromFastBlurChain(chain: XposedInterface.Chain): Any? {
        val window = try {
            chain.getArg(1) as? Window
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
        return window?.context
    }

    private fun isFolderHost(host: Any): Boolean {
        val clazz = host.javaClass
        val cachedClass = folderHostClass
        if (cachedClass === clazz) return folderHostMatch
        val match = XposedHelpers.findMethodExactIfExists(clazz, "isFolderShowing") != null ||
            try {
                XposedHelpers.findMethodBestMatch(clazz, "isFolderShowing")
                true
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                false
            }
        folderHostClass = clazz
        folderHostMatch = match
        return match
    }

    private fun invokeFolderOpened(host: Any): Boolean {
        val method = resolveFolderOpenedMethod(host) ?: return false
        return try {
            method.invoke(host) as? Boolean ?: false
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    private fun resolveFolderOpenedMethod(host: Any): Method? {
        if (folderOpenedResolved) return folderOpenedMethod
        synchronized(folderOpenedResolveLock) {
            if (folderOpenedResolved) return folderOpenedMethod
            val clazz = host.javaClass
            var resolved = XposedHelpers.findMethodExactIfExists(clazz, "isFolderOpened")
                ?: XposedHelpers.findMethodExactIfExists(clazz, "isFolderOpen")
            if (resolved == null) {
                resolved = try {
                    XposedHelpers.findMethodBestMatch(clazz, "isFolderOpened")
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    try {
                        XposedHelpers.findMethodBestMatch(clazz, "isFolderOpen")
                    } catch (t2: Throwable) {
                        FatalErrors.rethrowIfFatal(t2)
                        null
                    }
                }
            }
            resolved?.isAccessible = true
            folderOpenedMethod = resolved
            folderOpenedResolved = true
            return folderOpenedMethod
        }
    }

    @JvmStatic
    fun CloseFolderOrDrawerOnLaunchShortcutMenuHook(lpparam: PackageReadyParam) {
        installFolderPreferenceSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.shortcuts.AppShortcutMenuItem", lpparam.classLoader, "getOnClickListener", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {

                    val listener = result as View.OnClickListener
                    result = View.OnClickListener {
                        listener.onClick(it)
                        val appCls = XposedHelpers.findClassIfExists("com.miui.home.launcher.Application", lpparam.classLoader)
                        if (appCls == null) return@OnClickListener
                        val launcher = XposedHelpers.callStaticMethod(appCls, "getLauncher")
                        if (launcher == null) return@OnClickListener
                        if (closeDrawer) XposedHelpers.callMethod(launcher, "hideAppView")
                        if (closeFoldersMode > 1) XposedHelpers.callMethod(launcher, "closeFolder")
                    }
                    throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun CloseDrawerOnLaunchHook(lpparam: PackageReadyParam) {
        val hook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.getThisObject()
                try {

                    XposedHelpers.callMethod(XposedHelpers.getObjectField(thisObject, "mLauncher"), "hideAppView")

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.allapps.category.fragment.AppsListFragment", lpparam.classLoader, "onClick", View::class.java, hook)
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.allapps.category.fragment.RecommendCategoryAppListFragment", lpparam.classLoader, "onClick", View::class.java, hook)
    }

}
