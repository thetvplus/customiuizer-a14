package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.os.UserHandle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Icon and label hooks.
 * Renaming, title font, shadow, margins and visibility, and icon scale.
 */
object LauncherIconHooks {

    private const val PREF_TITLE_FONT_SIZE = "launcher_titlefontsize"
    private const val PREF_TITLE_TOP_MARGIN = "launcher_titletopmargin"
    private const val PREF_RENAME_PREFIX = "launcher_renameapps_list"

    internal data class IconStyleSnapshot(
        val titleFontSizeSp: Float = 5f,
        val titleTopMargin: Int = 0,
        val renameTitles: Map<String, String> = emptyMap(),
    )

    @Volatile
    internal var iconStyleConfig = IconStyleSnapshot()

    private var iconStyleObserverRegistered = false

    @Synchronized
    internal fun refreshIconStyleSnapshot(changedKey: String? = null) {
        val prefs = MainModule.mPrefs.getAll()
        val current = iconStyleConfig
        val renamed = when {
            changedKey == null -> prefs.entries.asSequence()
                .filter { it.key.startsWith(PREF_RENAME_PREFIX) && it.value is String }
                .associate { it.key to it.value as String }
            changedKey.startsWith(PREF_RENAME_PREFIX) -> {
                val value = prefs[changedKey] as? String
                if (current.renameTitles[changedKey] == value) current.renameTitles
                else current.renameTitles.toMutableMap().apply {
                    if (value == null) remove(changedKey) else put(changedKey, value)
                }
            }
            else -> current.renameTitles
        }
        iconStyleConfig = IconStyleSnapshot(
            titleFontSizeSp = (prefs[PREF_TITLE_FONT_SIZE] as? Int ?: 5).coerceIn(5, 20).toFloat(),
            titleTopMargin = (prefs[PREF_TITLE_TOP_MARGIN] as? Int ?: 0).coerceIn(0, 21),
            renameTitles = renamed,
        )
    }

    @JvmStatic
    internal fun installIconStyleSnapshot() {
        refreshIconStyleSnapshot()
        if (iconStyleObserverRegistered) return
        iconStyleObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null ||
                    key == PREF_TITLE_FONT_SIZE ||
                    key == PREF_TITLE_TOP_MARGIN ||
                    key.startsWith(PREF_RENAME_PREFIX)
                ) {
                    refreshIconStyleSnapshot(key)
                }
            }
        })
    }

    private fun modifyTitle(thisObject: Any) {
        val isApplicatoin = XposedHelpers.callMethod(thisObject, "isApplicatoin") as Boolean
        if (!isApplicatoin) return
        val pkgName = XposedHelpers.callMethod(thisObject, "getPackageName") as String
        val actName = XposedHelpers.callMethod(thisObject, "getClassName") as String
        val user = XposedHelpers.getObjectField(thisObject, "user") as UserHandle
        val newTitle = iconStyleConfig.renameTitles[
            "launcher_renameapps_list:" + pkgName + "|" + actName + "|" + user.hashCode()
        ] ?: ""
        if (!TextUtils.isEmpty(newTitle)) XposedHelpers.setObjectField(thisObject, "mLabel", newTitle)
    }

    internal fun updateRenamedShortcuts(owner: Activity, changedKey: String?, packageName: String) {
        owner.runOnUiThread {
            ModuleHelper.guarded {
                if (owner.isDestroyed || owner.isFinishing) return@guarded
                val field = XposedHelpers.findFieldIfExists(owner.javaClass, "mAllLoadedShortcut")
                    ?: XposedHelpers.findFieldIfExists(owner.javaClass, "mAllLoadedApps") ?: return@guarded
                val shortcuts = field.get(owner) as? Collection<*> ?: return@guarded
                val titles = iconStyleConfig.renameTitles
                for (shortcut in shortcuts.toList()) {
                    shortcut ?: continue
                    if (XposedHelpers.callMethod(shortcut, "isApplicatoin") != true) continue
                    val pkg = XposedHelpers.callMethod(shortcut, "getPackageName") as String
                    val act = XposedHelpers.callMethod(shortcut, "getClassName") as String
                    val user = XposedHelpers.getObjectField(shortcut, "user") as UserHandle
                    val key = "$PREF_RENAME_PREFIX:$pkg|$act|${user.hashCode()}"
                    if (changedKey != null && changedKey != key) continue
                    val title = titles[key]?.takeUnless { it.isEmpty() }
                        ?: XposedHelpers.getAdditionalInstanceField(shortcut, "mLabelOrig") as? CharSequence ?: continue
                    if (XposedHelpers.getObjectField(shortcut, "mLabel") == title) continue
                    XposedHelpers.setObjectField(shortcut, "mLabel", title)
                    if (packageName == "com.miui.home") XposedHelpers.callMethod(shortcut, "updateBuddyIconView", owner)
                    else {
                        val buddy = XposedHelpers.callMethod(shortcut, "getBuddyIconView")
                        if (buddy != null) XposedHelpers.callMethod(buddy, "updateInfo", owner, shortcut)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun NoClockHideHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "updateStatusBarClock", Long::class.javaPrimitiveType!!, HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun RenameShortcutsHook(lpparam: PackageReadyParam) {
        installIconStyleSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onCreate", Bundle::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (throwable != null) return XposedHelpers.throwOrReturn(throwable, result)
                try {
                    val thisObject = chain.getThisObject()

                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) {
                            ModuleHelper.guarded {
                                if (key != null && !key.startsWith(PREF_RENAME_PREFIX)) return
                                refreshIconStyleSnapshot(key)
                                updateRenamedShortcuts(thisObject as Activity, key, lpparam.packageName)
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "onDestroy", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    ModuleHelper.guarded { ModuleHelper.unregisterPreferenceObserver(chain.getThisObject()) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllConstructors("com.miui.home.launcher.ShortcutInfo", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (throwable != null) return XposedHelpers.throwOrReturn(throwable, result)
                try {
                    val thisObject = chain.getThisObject()

                    XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", XposedHelpers.getObjectField(thisObject, "mLabel"))
                    if (chain.executable.parameterCount > 0) modifyTitle(thisObject)

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.ShortcutInfo", lpparam.classLoader, "loadToggleInfo", Context::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (throwable != null) return XposedHelpers.throwOrReturn(throwable, result)
                try {
                    val thisObject = chain.getThisObject()

                    XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", XposedHelpers.getObjectField(thisObject, "mLabel"))
                    modifyTitle(thisObject)

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.ShortcutInfo", lpparam.classLoader, "setLabelAndUpdateDB", CharSequence::class.java, Context::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (throwable != null) return XposedHelpers.throwOrReturn(throwable, result)
                try {
                    val thisObject = chain.getThisObject()

                    XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", chain.getArg(0))
                    modifyTitle(thisObject)

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ShortcutInfo", lpparam.classLoader, "load", Context::class.java, Cursor::class.java, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (throwable != null) return XposedHelpers.throwOrReturn(throwable, result)
                try {
                    val thisObject = chain.getThisObject()

                    modifyTitle(thisObject)

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethodsSilently("com.miui.home.launcher.BaseAppInfo", lpparam.classLoader, "resetTitle", object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                if (throwable != null) return XposedHelpers.throwOrReturn(throwable, result)
                try {
                    val thisObject = chain.getThisObject()

                    modifyTitle(thisObject)

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun TitleShadowHook(lpparam: PackageReadyParam) {
        if (lpparam.packageName == "com.miui.home")
            ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "getIconTitleShadowColor", object : MethodHook() {
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
                        val color = result as Int
                        if (color == Color.TRANSPARENT) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = Color.argb(Math.round(Color.alpha(color) + (255 - Color.alpha(color)) / 1.9f), Color.red(color), Color.green(color), Color.blue(color))
                        throwable = null
                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        else
            ModuleHelper.findAndHookMethod("com.miui.home.launcher.WallpaperUtils", lpparam.classLoader, "getTitleShadowColor", Int::class.javaPrimitiveType!!, object : MethodHook() {
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
                        val color = result as Int
                        if (color == Color.TRANSPARENT) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = Color.argb(Math.round(Color.alpha(color) + (255 - Color.alpha(color)) / 1.9f), Color.red(color), Color.green(color), Color.blue(color))
                        throwable = null
                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
    }

    @JvmStatic
    fun IconScaleHook(lpparam: PackageReadyParam) {
        val iconScale = Math.sqrt((MainModule.mPrefs.getInt("launcher_iconscale", 100) / 100f).toDouble()).toFloat()
        var iconMessageMaxWidthResId = 0

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "restoreToInitState", object : MethodHook() {
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

                    val mIconContainer = XposedHelpers.getObjectField(thisObject, "mIconContainer") as? ViewGroup
                    val icon = mIconContainer?.getChildAt(0)
                    if (icon == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    icon.scaleX = iconScale
                    icon.scaleY = iconScale

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
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

                    val mIconContainer = XposedHelpers.getObjectField(thisObject, "mIconContainer") as? ViewGroup
                    val icon = mIconContainer?.getChildAt(0)
                    if (icon != null) {
                        icon.scaleX = iconScale
                        icon.scaleY = iconScale
                        mIconContainer.clipToPadding = false
                        mIconContainer.clipChildren = false
                    }

                    if (iconScale > 1) {
                        val mMessage = XposedHelpers.getObjectField(thisObject, "mMessage") as? TextView
                        if (mMessage != null) {
                            if (iconMessageMaxWidthResId == 0) {
                                iconMessageMaxWidthResId = HookUtils.getResId(
                                    mMessage.resources,
                                    "icon_message_max_width",
                                    "dimen",
                                    lpparam.packageName
                                )
                            }
                            val maxWidthResId = iconMessageMaxWidthResId
                            mMessage.addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: Editable) {
                                    ModuleHelper.guarded {
                                        val maxWidth = mMessage.resources.getDimensionPixelSize(maxWidthResId)
                                        mMessage.measure(View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST))
                                        mMessage.translationX = -mMessage.measuredWidth * (iconScale - 1) / 2f
                                        mMessage.translationY = mMessage.measuredHeight * (iconScale - 1) / 2f
                                    }
                                }
                            })
                        }
                    }

                    XposedHelpers.setAdditionalInstanceField(thisObject, "mMessageAnimationOrig", XposedHelpers.getObjectField(thisObject, "mMessageAnimation"))
                    XposedHelpers.setObjectField(thisObject, "mMessageAnimation", object : Runnable {
                        override fun run() {
                            ModuleHelper.guarded {
                                val mMessageAnimationOrig = XposedHelpers.getAdditionalInstanceField(thisObject, "mMessageAnimationOrig") as Runnable
                                mMessageAnimationOrig.run()
                                val mIsShowMessageAnimation = XposedHelpers.getBooleanField(thisObject, "mIsShowMessageAnimation")
                                if (mIsShowMessageAnimation) {
                                    val mMessage = XposedHelpers.getObjectField(thisObject, "mMessage") as View
                                    mMessage.animate().cancel()
                                    mMessage.animate().scaleX(iconScale).scaleY(iconScale).setStartDelay(0).start()
                                }
                            }
                        }
                    })

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "getIconLocation", object : MethodHook() {
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

                    val rect = result as Rect?
                    if (rect == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    rect.right = rect.left + Math.round(rect.width() * iconScale)
                    rect.bottom = rect.top + Math.round(rect.height() * iconScale)
                    result = rect
                    throwable = null

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.gadget.ClearButton", lpparam.classLoader, "onCreate", object : MethodHook() {
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

                    val mIconContainer = XposedHelpers.getObjectField(thisObject, "mIconContainer") as? ViewGroup
                    val icon = mIconContainer?.getChildAt(0)
                    if (icon == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    icon.scaleX = iconScale
                    icon.scaleY = iconScale

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun TitleFontSizeHook(lpparam: PackageReadyParam) {
        installIconStyleSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", titleFontHook { chain, _ ->
            XposedHelpers.getObjectField(chain.thisObject, "mTitle") as? TextView
        })
        val resultHook = titleFontHook { _, result ->
            result?.let { XposedHelpers.getObjectField(it, "mTitle") as? TextView }
        }
        ModuleHelper.hookAllMethods("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "fromXml", resultHook)
        ModuleHelper.hookAllMethods("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "createShortcutIcon", resultHook)
        ModuleHelper.hookAllMethods("com.miui.home.launcher.common.Utilities", lpparam.classLoader, "adaptTitleStyleToWallpaper", titleFontHook { chain, _ ->
            (chain.getArg(1) as? TextView)?.takeIf { it.id == it.resources.getIdentifier("icon_title", "id", "com.miui.home") }
        })
    }

    internal fun titleFontHook(title: (XposedInterface.Chain, Any?) -> TextView?) = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val size = iconStyleConfig.titleFontSizeSp
            if (size <= 5f) return result
            try { title(chain, result)?.setTextSize(TypedValue.COMPLEX_UNIT_SP, size) }
            catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t); XposedHelpers.log(t) }
            return result
        }
    }

    @JvmStatic
    fun TitleTopMarginHook(lpparam: PackageReadyParam) {
        installIconStyleSnapshot()
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", titleTopMarginHook())
    }

    internal fun titleTopMarginHook() = object : MethodHook() {
        override fun intercept(chain: XposedInterface.Chain): Any? {
            val result = chain.proceed()
            val margin = iconStyleConfig.titleTopMargin
            if (margin == 0) return result
            try {
                val container = XposedHelpers.getObjectField(chain.thisObject, "mTitleContainer") as? ViewGroup ?: return result
                val offset = Math.round((margin - 11) * container.resources.displayMetrics.density)
                val lp = container.layoutParams
                if (lp is RelativeLayout.LayoutParams) {
                    if (lp.topMargin != offset) { lp.topMargin = offset; container.layoutParams = lp }
                } else {
                    container.translationY = offset.toFloat()
                    container.clipChildren = false
                    container.clipToPadding = false
                    (container.parent as? ViewGroup)?.clipChildren = false
                    (container.parent as? ViewGroup)?.clipToPadding = false
                }
            } catch (t: Throwable) { FatalErrors.unwrapAndRethrowIfFatal(t); XposedHelpers.log(t) }
            return result
        }
    }

    @JvmStatic
    fun HideTitlesHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.ItemIcon", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
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

                    val mTitleContainer = XposedHelpers.getObjectField(thisObject, "mTitleContainer") as? View
                    if (mTitleContainer != null) mTitleContainer.visibility = View.GONE

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun ShowHotseatTitlesHook(lpparam: PackageReadyParam) {
        MainModule.resHooks.setThemeValueReplacement("com.miui.home", "bool", "config_hide_hotseats_app_title", false)
        ModuleHelper.findAndHookMethodSilently("com.miui.home.launcher.Launcher", lpparam.classLoader, "createItemIcon", ViewGroup::class.java, "com.miui.home.launcher.ItemInfo", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val args = XposedHelpers.getArgsArray(chain)
                try {

                    args[2] = false

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
