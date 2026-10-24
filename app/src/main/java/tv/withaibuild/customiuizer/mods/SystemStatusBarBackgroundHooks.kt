package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.ViewGroup
import android.view.Window
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import java.lang.reflect.Method

object SystemStatusBarBackgroundHooks {

    private const val ACTION_BAR_COLOR_FIELD = "customiuizer_statusbar_actionbar_color"

    private fun getActionBarColor(window: Window, oldColor: Int): Int {
        val actionBarColor = XposedHelpers.getAdditionalInstanceField(window, ACTION_BAR_COLOR_FIELD) as? Int
        if (actionBarColor != null) return actionBarColor

        val outValue = TypedValue()
        if (!window.context.theme.resolveAttribute(android.R.attr.actionBarStyle, outValue, true)) return oldColor
        val abStyle: TypedArray = window.context.theme.obtainStyledAttributes(outValue.resourceId, intArrayOf(android.R.attr.background))
        return try {
            val bg = abStyle.getDrawable(0)
            if (bg is ColorDrawable) bg.color else oldColor
        } finally {
            abStyle.recycle()
        }
    }

    private fun updateActionBarColor(window: Window, bg: Drawable?) {
        if (bg !is ColorDrawable) {
            XposedHelpers.removeAdditionalInstanceField(window, ACTION_BAR_COLOR_FIELD)
            return
        }
        XposedHelpers.setAdditionalInstanceField(window, ACTION_BAR_COLOR_FIELD, bg.color)
        val current = window.statusBarColor
        // Transparent/translucent requests belong to the window, including preview transitions.
        // Erasing their alpha can activate DecorView's native transparent-color contrast scrim.
        if (Color.alpha(current) == 255 && current != bg.color) window.statusBarColor = bg.color
    }

    @Suppress("UNCHECKED_CAST")
    private fun hookToolbar(thisObject: Any, bg: Drawable?) {
        val mDecorToolbar = XposedHelpers.getObjectField(thisObject, "mDecorToolbar")
        val mToolbar = XposedHelpers.getObjectField(mDecorToolbar, "mToolbar") as ViewGroup
        val mDecorContext = mToolbar.rootView.context ?: return
        val mActivityContext = XposedHelpers.getObjectField(mDecorContext, "mActivityContext") as WeakReference<Context>
        val activity = mActivityContext.get() as? Activity ?: return
        updateActionBarColor(activity.window, bg)
    }

    internal fun hookWindowDecor(thisObject: Any, bg: Drawable?) {
        val activity = XposedHelpers.getObjectField(thisObject, "mActivity") as? Activity ?: return
        updateActionBarColor(activity.window, bg)
    }

    internal fun statusBarLayoutHook(): MethodHook = object : MethodHook() {
        override fun after(callback: AfterHookCallback) {
            if (callback.getThrowable() != null) return
            val window = callback.getThisObject() as? Window ?: return
            val current = window.statusBarColor
            if (current == Color.BLACK || Color.alpha(current) < 255) return
            val color = getActionBarColor(window, current)
            if (color != current) window.statusBarColor = color
        }
    }

    internal fun statusBarColorHook(): MethodHook = object : MethodHook() {
        override fun before(callback: BeforeHookCallback) {
            val requested = callback.getArg(0) as Int
            if (Color.alpha(requested) < 255) return
            val color = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), ACTION_BAR_COLOR_FIELD) as? Int ?: return
            // Materialize arguments only when actually changing an opaque color.
            if (color != requested) callback.getArgs()[0] = color
        }
    }

    internal fun actionBarBackgroundHook(toolbar: Boolean): MethodHook = object : MethodHook() {
        override fun before(callback: BeforeHookCallback) {
            val owner = callback.getThisObject() ?: return
            val bg = callback.getArg(0) as Drawable?
            // The shared callback boundary isolates optional metadata failures and always lets
            // the native call proceed once. Fatal and native exceptions retain their semantics.
            if (toolbar) hookToolbar(owner, bg) else hookWindowDecor(owner, bg)
        }
    }

    @JvmStatic
    fun StatusBarBackgroundHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.internal.policy.PhoneWindow", lpparam.classLoader, "generateLayout", "com.android.internal.policy.DecorView", statusBarLayoutHook())
        ModuleHelper.findAndHookMethod("com.android.internal.policy.PhoneWindow", lpparam.classLoader, "setStatusBarColor", Int::class.javaPrimitiveType!!, statusBarColorHook())
        ModuleHelper.findAndHookMethod("com.android.internal.app.ToolbarActionBar", lpparam.classLoader, "setBackgroundDrawable", Drawable::class.java, actionBarBackgroundHook(true))
        ModuleHelper.findAndHookMethod("com.android.internal.app.WindowDecorActionBar", lpparam.classLoader, "setBackgroundDrawable", Drawable::class.java, actionBarBackgroundHook(false))
    }

    @JvmStatic
    fun StatusBarBackgroundCompatHook(lpparam: PackageReadyParam) {
        var androidx = false
        var sbdMethod: Method? = null
        val tabCls = XposedHelpers.findClassIfExists("androidx.appcompat.app.ToolbarActionBar", lpparam.classLoader)
        if (tabCls != null) sbdMethod = XposedHelpers.findMethodExactIfExists(tabCls, "setBackgroundDrawable", Drawable::class.java)
        if (sbdMethod != null) {
            androidx = true
            ModuleHelper.hookMethod(sbdMethod, actionBarBackgroundHook(true))
        }

        sbdMethod = null
        val wdabCls = XposedHelpers.findClassIfExists("androidx.appcompat.app.WindowDecorActionBar", lpparam.classLoader)
        if (wdabCls != null) sbdMethod = XposedHelpers.findMethodExactIfExists(wdabCls, "setBackgroundDrawable", Drawable::class.java)
        if (sbdMethod != null) {
            androidx = true
            ModuleHelper.hookMethod(sbdMethod, actionBarBackgroundHook(false))
        }

        if (!androidx) {
            sbdMethod = null
            val tabv7Cls = XposedHelpers.findClassIfExists("android.support.v7.internal.app.ToolbarActionBar", lpparam.classLoader)
            if (tabv7Cls != null) sbdMethod = XposedHelpers.findMethodExactIfExists(tabv7Cls, "setBackgroundDrawable", Drawable::class.java)
            if (sbdMethod != null) ModuleHelper.hookMethod(sbdMethod, actionBarBackgroundHook(true))

            sbdMethod = null
            val wdabv7Cls = XposedHelpers.findClassIfExists("android.support.v7.internal.app.WindowDecorActionBar", lpparam.classLoader)
            if (wdabv7Cls != null) sbdMethod = XposedHelpers.findMethodExactIfExists(wdabv7Cls, "setBackgroundDrawable", Drawable::class.java)
            if (sbdMethod != null) ModuleHelper.hookMethod(sbdMethod, actionBarBackgroundHook(false))
        }
    }
}
