package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.View
import android.view.ViewGroup
import android.widget.GridView
import android.widget.ImageView
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field

/**
 * Folder and app drawer hooks.
 * Column count and width, blur, the privacy folder, and closing on launch.
 */
object LauncherFolderHooks {

    private const val PREF_FOLDER_WIDTH = "launcher_folderwidth"
    private const val PREF_FOLDER_BLUR = "launcher_folderblur_opacity"
    private const val PREF_FOLDER_BLUR_DISABLED = "launcher_folderblur_disable"

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
    private var folderPreferenceObserverRegistered = false

    /** `Folder.mContent` / `Folder.mFakeIcon`, resolved once instead of per layout pass. */
    @Volatile
    private var folderContentField: Field? = null

    @Volatile
    private var folderFakeIconField: Field? = null

    /** Set when the Folder ABI could not be resolved, so layout stops probing every frame. */
    @Volatile
    private var folderLayoutAbiUnavailable = false

    private fun refreshFolderPreferences() {
        folderWidthEnabled = MainModule.mPrefs.getBoolean(PREF_FOLDER_WIDTH)
        val disabled = MainModule.mPrefs.getBoolean(PREF_FOLDER_BLUR_DISABLED)
        val opacityPercent = MainModule.mPrefs.getInt(PREF_FOLDER_BLUR, 0)
        folderBlurRatio = resolveFolderBlurRatio(disabled, opacityPercent)
        folderBlurOverrideEnabled = resolveFolderBlurOverrideEnabled(disabled, opacityPercent)
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
                    key == PREF_FOLDER_BLUR_DISABLED
                ) {
                    refreshFolderPreferences()
                }
            }
        })
    }

    /**
     * Resolves the two Folder fields the layout pass needs. A ROM without them disables the
     * layout adjustment instead of throwing from every `onLayout`.
     */
    private fun resolveFolderLayoutFields(folder: Any): Boolean {
        if (folderLayoutAbiUnavailable) return false
        val content = folderContentField
        if (content != null && content.declaringClass.isInstance(folder)) return true

        val resolvedContent = XposedHelpers.findFieldIfExists(folder.javaClass, "mContent")
        val resolvedFakeIcon = XposedHelpers.findFieldIfExists(folder.javaClass, "mFakeIcon")
        if (resolvedContent == null || resolvedFakeIcon == null) {
            folderLayoutAbiUnavailable = true
            XposedHelpers.log("[FolderWidth] Folder.mContent/mFakeIcon unavailable, layout adjustment disabled")
            return false
        }
        folderContentField = resolvedContent
        folderFakeIconField = resolvedFakeIcon
        return true
    }

    @JvmStatic
    fun CloseFolderOnLaunchHook(lpparam: PackageReadyParam) {
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

                    if (MainModule.mPrefs.getStringAsInt("launcher_closefolders", 1) != 2) { return XposedHelpers.throwOrReturn(throwable, result) }
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
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "integer", "config_folder_columns_count", folderCols)
    }

    private fun setFolderWidth(thisObject: Any) {
        if (folderWidthEnabled) {
            val mContent = XposedHelpers.getObjectField(thisObject, "mContent") as GridView
            val lp = mContent.layoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            mContent.layoutParams = lp
        }
    }

    @JvmStatic
    fun FolderColumnsHook(lpparam: PackageReadyParam) {
        installFolderPreferenceSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Folder", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
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

                    setFolderWidth(thisObject)
                    val cols = MainModule.mPrefs.getInt("launcher_folder_cols", 1)
                    if (cols > 3 && MainModule.mPrefs.getBoolean("launcher_folderspace")) {
                        val mBackgroundView = XposedHelpers.getObjectField(thisObject, "mBackgroundView") as ViewGroup
                        mBackgroundView.setPadding(
                            mBackgroundView.paddingLeft / 3,
                            mBackgroundView.paddingTop,
                            mBackgroundView.paddingRight / 3,
                            mBackgroundView.paddingBottom
                        )
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Folder", lpparam.classLoader, "resetViewsLayoutParams", object : MethodHook() {
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

                    setFolderWidth(thisObject)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods("com.miui.home.launcher.Folder", lpparam.classLoader, "onLayout", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (!folderWidthEnabled) { return XposedHelpers.throwOrReturn(throwable, result) }
                try {
                    val thisObject = chain.getThisObject()

                    if (!resolveFolderLayoutFields(thisObject)) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mContent = folderContentField!!.get(thisObject) as GridView
                    val mFakeIcon = folderFakeIconField!!.get(thisObject) as ImageView
                    mFakeIcon.layout(mContent.left, mContent.top, mContent.right, mContent.top + mContent.width)

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun PrivacyFolderHook(lpparam: PackageReadyParam) {
        if (MainModule.mPrefs.getBoolean("launcher_privacyapps_gest")) {
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
                    val opt = MainModule.mPrefs.getBoolean("launcher_privacyapps_gest")
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
                        val launcher = chain.getArg(0) as? Activity
                        val withAnimation = chain.getArg(1) as? Boolean
                        if (launcher != null && withAnimation != null) {
                            XposedHelpers.callStaticMethod(
                                BlurUtils,
                                "fastBlur",
                                folderBlurRatio,
                                launcher.window,
                                withAnimation
                            )
                            return null
                        }
                    } catch (t: Throwable) {
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

                        val isFolderShowing = XposedHelpers.callMethod(chain.getArg(0), "isFolderShowing") as Boolean
                        if (folderBlurOverrideEnabled && isFolderShowing) {
                            skipped = true
                            result = folderBlurRatio
                            throwable = null
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

                        val isFolderShowing = XposedHelpers.callMethod(thisObject, "isFolderShowing") as Boolean
                        if (folderBlurOverrideEnabled && isFolderShowing) {
                            val launcher = thisObject as Activity
                            XposedHelpers.callStaticMethod(BlurUtils, "fastBlur", folderBlurRatio, launcher.window, true)
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
    internal fun resolveFolderBlurRatio(disabled: Boolean, opacityPercent: Int): Float =
        if (disabled) 0f else opacityPercent.coerceIn(0, 100) / 100f

    @JvmStatic
    internal fun resolveFolderBlurOverrideEnabled(disabled: Boolean, opacityPercent: Int): Boolean =
        disabled || opacityPercent > 0

    @JvmStatic
    fun CloseFolderOrDrawerOnLaunchShortcutMenuHook(lpparam: PackageReadyParam) {
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
                        if (MainModule.mPrefs.getBoolean("launcher_closedrawer")) XposedHelpers.callMethod(launcher, "hideAppView")
                        if (MainModule.mPrefs.getStringAsInt("launcher_closefolders", 1) > 1) XposedHelpers.callMethod(launcher, "closeFolder")
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
