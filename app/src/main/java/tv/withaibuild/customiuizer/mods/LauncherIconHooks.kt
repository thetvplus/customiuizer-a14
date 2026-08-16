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
import java.util.HashSet
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.MainModule
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

    private fun modifyTitle(thisObject: Any) {
        val isApplicatoin = XposedHelpers.callMethod(thisObject, "isApplicatoin") as Boolean
        if (!isApplicatoin) return
        val pkgName = XposedHelpers.callMethod(thisObject, "getPackageName") as String
        val actName = XposedHelpers.callMethod(thisObject, "getClassName") as String
        val user = XposedHelpers.getObjectField(thisObject, "user") as UserHandle
        val newTitle = MainModule.mPrefs.getString("launcher_renameapps_list:" + pkgName + "|" + actName + "|" + user.hashCode(), "")
        if (!TextUtils.isEmpty(newTitle)) XposedHelpers.setObjectField(thisObject, "mLabel", newTitle)
    }

    @JvmStatic
    fun NoClockHideHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.home.launcher.Launcher", lpparam.classLoader, "updateStatusBarClock", Long::class.javaPrimitiveType!!, HookerClassHelper.DO_NOTHING)
    }

    @JvmStatic
    fun RenameShortcutsHook(lpparam: PackageReadyParam) {
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
                try {
                    val thisObject = chain.getThisObject()

                    ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) {
                            ModuleHelper.guarded {
                                if (key == null || !key.startsWith("launcher_renameapps_list")) return
                                val newTitle = MainModule.mPrefs.getString(key, "")
                                var mAllLoadedApps: HashSet<*>? = null
                                if (XposedHelpers.findFieldIfExists(thisObject.javaClass, "mAllLoadedShortcut") != null)
                                    mAllLoadedApps = XposedHelpers.getObjectField(thisObject, "mAllLoadedShortcut") as? HashSet<*>
                                else if (XposedHelpers.findFieldIfExists(thisObject.javaClass, "mAllLoadedApps") != null)
                                    mAllLoadedApps = XposedHelpers.getObjectField(thisObject, "mAllLoadedApps") as? HashSet<*>
                                val act = thisObject as Activity
                                if (mAllLoadedApps != null)
                                    for (shortcut in mAllLoadedApps) {
                                        val shortcutObj = shortcut ?: continue
                                        val isApplicatoin = XposedHelpers.callMethod(shortcutObj, "isApplicatoin") as Boolean
                                        if (!isApplicatoin) continue
                                        val pkgName = XposedHelpers.callMethod(shortcutObj, "getPackageName") as String
                                        val actName = XposedHelpers.callMethod(shortcutObj, "getClassName") as String
                                        val user = XposedHelpers.getObjectField(shortcutObj, "user") as UserHandle
                                        if (("launcher_renameapps_list:" + pkgName + "|" + actName + "|" + user.hashCode()) == key) {
                                            val newStr: CharSequence? = if (TextUtils.isEmpty(newTitle)) XposedHelpers.getAdditionalInstanceField(shortcutObj, "mLabelOrig") as? CharSequence else newTitle
                                            XposedHelpers.setObjectField(shortcutObj, "mLabel", newStr)

                                            act.runOnUiThread {
                                                ModuleHelper.guarded {
                                                    if (lpparam.packageName == "com.miui.home") {
                                                        XposedHelpers.callMethod(shortcutObj, "updateBuddyIconView", act)
                                                    } else {
                                                        val buddyIconView = XposedHelpers.callMethod(shortcutObj, "getBuddyIconView")
                                                        if (buddyIconView != null) XposedHelpers.callMethod(buddyIconView, "updateInfo", thisObject, shortcutObj)
                                                    }
                                                }
                                            }
                                            break
                                        }
                                    }
                            }
                        }
                    }, thisObject)

                } catch (t: Throwable) {
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
                    ModuleHelper.unregisterPreferenceObserver(chain.getThisObject())
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
                try {
                    val thisObject = chain.getThisObject()
                    val args = chain.args

                    XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", XposedHelpers.getObjectField(thisObject, "mLabel"))
                    if (args.size > 0) modifyTitle(thisObject)

                } catch (t: Throwable) {
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
                try {
                    val thisObject = chain.getThisObject()

                    XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", XposedHelpers.getObjectField(thisObject, "mLabel"))
                    modifyTitle(thisObject)

                } catch (t: Throwable) {
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
                try {
                    val thisObject = chain.getThisObject()

                    XposedHelpers.setAdditionalInstanceField(thisObject, "mLabelOrig", chain.getArg(0))
                    modifyTitle(thisObject)

                } catch (t: Throwable) {
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
                try {
                    val thisObject = chain.getThisObject()

                    modifyTitle(thisObject)

                } catch (t: Throwable) {
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
                try {
                    val thisObject = chain.getThisObject()

                    modifyTitle(thisObject)

                } catch (t: Throwable) {
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

                    val mTitle = XposedHelpers.getObjectField(thisObject, "mTitle") as? TextView
                    if (mTitle != null) mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, MainModule.mPrefs.getInt("launcher_titlefontsize", 5).toFloat())

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "fromXml", object : MethodHook() {
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
                    val args = chain.args

                    val buddyIcon = XposedHelpers.callMethod(args[3], "getBuddyIconView", args[2])
                    if (buddyIcon == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mTitle = XposedHelpers.getObjectField(buddyIcon, "mTitle") as? TextView
                    if (mTitle != null) mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, MainModule.mPrefs.getInt("launcher_titlefontsize", 5).toFloat())

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods("com.miui.home.launcher.ShortcutIcon", lpparam.classLoader, "createShortcutIcon", object : MethodHook() {
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

                    val buddyIcon = result
                    if (buddyIcon == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val mTitle = XposedHelpers.getObjectField(buddyIcon, "mTitle") as? TextView
                    if (mTitle != null) mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, MainModule.mPrefs.getInt("launcher_titlefontsize", 5).toFloat())

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        ModuleHelper.hookAllMethods("com.miui.home.launcher.common.Utilities", lpparam.classLoader, "adaptTitleStyleToWallpaper", object : MethodHook() {
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

                    val mTitle = chain.getArg(1) as? TextView
                    if (mTitle != null && mTitle.id == mTitle.resources.getIdentifier("icon_title", "id", "com.miui.home"))
                        mTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, MainModule.mPrefs.getInt("launcher_titlefontsize", 5).toFloat())

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun TitleTopMarginHook(lpparam: PackageReadyParam) {
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

                    val mTitleContainer = XposedHelpers.getObjectField(thisObject, "mTitleContainer") as? ViewGroup
                    if (mTitleContainer == null) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val lp = mTitleContainer.layoutParams
                    val opt = Math.round((MainModule.mPrefs.getInt("launcher_titletopmargin", 0) - 11) * mTitleContainer.resources.displayMetrics.density)
                    if (lp is RelativeLayout.LayoutParams) {
                        lp.topMargin = opt
                        mTitleContainer.layoutParams = lp
                    } else {
                        mTitleContainer.translationY = opt.toFloat()
                        mTitleContainer.clipChildren = false
                        mTitleContainer.clipToPadding = false
                        (mTitleContainer.parent as? ViewGroup)?.clipChildren = false
                        (mTitleContainer.parent as? ViewGroup)?.clipToPadding = false
                    }

                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
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
