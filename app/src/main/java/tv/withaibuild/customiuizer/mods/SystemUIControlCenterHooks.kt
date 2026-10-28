package tv.withaibuild.customiuizer.mods

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.ApplicationInfo
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Message
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.ref.WeakReference
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.mods.utils.StepCounterController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.ArrayList
import java.util.Comparator
import java.lang.System
import java.lang.reflect.Field
import java.lang.reflect.Method
import tv.withaibuild.customiuizer.utils.HookUtils
import tv.withaibuild.customiuizer.mods.utils.ControlCenterPluginRuntime
import tv.withaibuild.customiuizer.mods.volumedialogautohide.VolumeDialogAutohideDelayHook
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.VolumeTimerValues
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEntry
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEvent
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureMachine
import tv.withaibuild.customiuizer.mods.utils.gesture.StatusBarGestureDependenciesResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.StatusBarGestureEffectExecutor

/**
 * Control centre, volume dialog and brightness UI hooks.
 * StatusBarGesturesHook lives here rather than with the status bar: the status bar
 * brightness slide and the control centre brightness overlay share the same slider
 * state, so they cannot be separated without inventing shared mutable state.
 */
object SystemUIControlCenterHooks {

    @JvmStatic
    fun AddCustomTileHook(lpparam: PackageReadyParam) {
        SystemUIMonitorAndTileHooks.AddCustomTileHook(lpparam)
    }



    /**
     * Extract the miui.systemui.plugin ClassLoader from a PluginFactory instance.
     * Tolerates field-name changes by falling back to type-based reflection.
     *
     * The [getObjectField] and [callInstanceMethod] parameters are test seams so fatal-error
     * boundaries can be exercised without depending on real SystemUI internals.
     */
    @JvmStatic
    internal fun extractPluginLoader(
        factory: Any?,
        getObjectField: (Any, String) -> Any? = { obj, name -> XposedHelpers.getObjectField(obj, name) },
        callInstanceMethod: (Any, String) -> Any? = { obj, name -> XposedHelpers.callMethod(obj, name) }
    ): ClassLoader? {
        val safeFactory = factory ?: return null
        val clazz = safeFactory.javaClass
        val appInfo = try {
            getObjectField(safeFactory, "mAppInfo") as? ApplicationInfo
        } catch (e: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(e)
            try {
                val field = clazz.declaredFields.firstOrNull { it.type == ApplicationInfo::class.java } ?: return null
                field.isAccessible = true
                field.get(safeFactory) as? ApplicationInfo
            } catch (nested: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(nested)
                null
            }
        } ?: return null
        if (appInfo.packageName != "miui.systemui.plugin") return null

        val loaderFactory = try {
            getObjectField(safeFactory, "mClassLoaderFactory")
        } catch (e: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(e)
            try {
                val field = clazz.declaredFields.firstOrNull { f ->
                    f.name.contains("ClassLoader", ignoreCase = true) && try {
                        f.type.getMethod("get").parameterTypes.isEmpty()
                    } catch (e: NoSuchMethodException) {
                        false
                    }
                } ?: return null
                field.isAccessible = true
                field.get(safeFactory)
            } catch (nested: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(nested)
                null
            }
        } ?: return null
        return try {
            callInstanceMethod(loaderFactory, "get") as? ClassLoader
        } catch (e: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(e)
            null
        }
    }

    @JvmStatic
    fun VolumeDialogAutohideDelayHook(classLoader: ClassLoader) {
        VolumeDialogAutohideDelayHook.install(classLoader)
    }

    internal data class VolumeBlurSnapshot(
        val collapsed: Float = 0f,
        val expanded: Float = 0f
    )

    @Volatile
    private var volumeBlurSnapshot = VolumeBlurSnapshot()

    private var volumeBlurObserverRegistered = false

    private val VOLUME_BLUR_COLLAPSED_KEY = "system_volumeblur_collapsed"
    private val VOLUME_BLUR_EXPANDED_KEY = "system_volumeblur_expanded"

    internal fun refreshVolumeBlurSnapshot() {
        val collapsed = MainModule.mPrefs.getInt(VOLUME_BLUR_COLLAPSED_KEY, 0) / 100f
        val expanded = MainModule.mPrefs.getInt(VOLUME_BLUR_EXPANDED_KEY, 0) / 100f
        volumeBlurSnapshot = VolumeBlurSnapshot(collapsed, expanded)
    }

    internal fun onVolumeBlurPreferenceChanged(key: String?) {
        if (key == null) {
            refreshVolumeBlurSnapshot()
        } else if (key == VOLUME_BLUR_COLLAPSED_KEY || key == VOLUME_BLUR_EXPANDED_KEY) {
            refreshVolumeBlurSnapshot()
        }
    }

    internal fun getVolumeBlurSnapshot(): VolumeBlurSnapshot = volumeBlurSnapshot

    private val volumeBlurPreferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) = ModuleHelper.guarded {
            onVolumeBlurPreferenceChanged(key)
        }
    }

    @JvmStatic
    fun BlurVolumeDialogBackgroundHook(classLoader: ClassLoader) {
        refreshVolumeBlurSnapshot()
        if (!volumeBlurObserverRegistered) {
            volumeBlurObserverRegistered = true
            ModuleHelper.observePreferenceChange(volumeBlurPreferenceObserver)
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader,
            "updateDialogWindowH", Boolean::class.javaPrimitiveType!!, VolumeBlurWindowHook())
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "showVolumeDialogH", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                if (param.getThrowable() != null) return
                val snapshot = volumeBlurSnapshot
                val window = XposedHelpers.getObjectField(param.getThisObject(), "mWindow") as? Window ?: return
                if (snapshot.collapsed > 0f || snapshot.expanded > 0f ||
                    XposedHelpers.getAdditionalInstanceField(window, VOLUME_BLUR_OWNED_FIELD) == true) {
                    // Re-read native flags and dim amounts on an actual show, including async shows.
                    XposedHelpers.callMethod(param.getThisObject(), "updateDialogWindowH", false)
                }
            }
        })
    }

    private const val VOLUME_BLUR_OWNED_FIELD = "a14_volume_blur_owned"

    internal class VolumeBlurWindowHook : MethodHook() {
        override fun after(param: AfterHookCallback) {
            if (param.getThrowable() != null) return
            val dialog = param.getThisObject()
            val window = XposedHelpers.getObjectField(dialog, "mWindow") as? Window ?: return
            val expanded = XposedHelpers.getBooleanField(dialog, "mExpanded")
            val snapshot = volumeBlurSnapshot
            val blurRatio = if (expanded && !(param.getArg(0) as Boolean)) snapshot.expanded else snapshot.collapsed
            if (blurRatio > 0f) {
                window.setDimAmount(0f)
                if (!expanded) window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                XposedHelpers.setAdditionalInstanceField(window, VOLUME_BLUR_OWNED_FIELD, true)
                XposedHelpers.callMethod(dialog, "startBlurAnim", 0f, blurRatio, 0)
            } else if (XposedHelpers.getAdditionalInstanceField(window, VOLUME_BLUR_OWNED_FIELD) == true) {
                // The original method has restored dim/focus and expanded blur. It does not clear
                // a collapsed blur that we previously added, so release only that owned effect.
                if (!expanded) {
                    XposedHelpers.callMethod(dialog, "startBlurAnim", 0f, 0f, 0)
                }
                XposedHelpers.removeAdditionalInstanceField(window, VOLUME_BLUR_OWNED_FIELD)
            }
        }
    }

    internal data class VolumeModeButtonColorSnapshot(
        val enabled: Boolean,
        val backgroundColor: Int,
        val iconColor: Int
    ) {
        // The target ROM's selected blends are (ARGB, SRC_OVER), prepared off the update path.
        val backgroundBlendColors = intArrayOf(backgroundColor, 3)
        val iconBlendColors = intArrayOf(iconColor, 3)
        val backgroundColors: ColorStateList? = ColorStateList.valueOf(backgroundColor)
    }

    internal data class VolumeModeButtonVisibilitySnapshot(
        val hideMute: Boolean,
        val hideDnd: Boolean
    )

    @Volatile
    private var volumeModeButtonColorSnapshot: VolumeModeButtonColorSnapshot? = null

    @Volatile
    private var volumeModeButtonVisibilitySnapshot: VolumeModeButtonVisibilitySnapshot? = null

    private var volumeModeButtonObserverRegistered = false

    private const val VOLUME_MODE_BUTTON_COLORS_ENABLED_KEY =
        "system_volume_mode_button_colors"
    private const val VOLUME_MODE_BUTTON_BACKGROUND_COLOR_KEY =
        "system_volume_mode_button_background_color"
    private const val VOLUME_MODE_BUTTON_ICON_COLOR_KEY =
        "system_volume_mode_button_icon_color"

    private const val VOLUME_MODE_BUTTON_HIDE_MUTE_KEY =
        "system_volume_hide_mute_shortcut"
    private const val VOLUME_MODE_BUTTON_HIDE_DND_KEY =
        "system_volume_hide_dnd_shortcut"

    private const val VOLUME_MODE_BUTTON_ROLE_FIELD = "customiuizer_volumeModeButtonRole"
    private const val VOLUME_MODE_BUTTON_ROOT_FIELD = "customiuizer_volumeModeButtonRoot"
    private const val VOLUME_MODE_BUTTON_VISIBILITY_OWNERSHIP_FIELD =
        "customiuizer_volumeModeButtonVisibilityOwnership"
    private const val VOLUME_MODE_SHARED_VISIBILITY_STATE_FIELD =
        "customiuizer_volumeModeSharedVisibilityState"
    private const val VOLUME_MODE_BUTTON_COLOR_STATE_FIELD =
        "customiuizer_volumeModeButtonColorState"
    private const val VOLUME_MODE_BUTTON_BACKGROUND_OWNER_FIELD =
        "customiuizer_volumeModeButtonBackgroundOwner"
    private const val VOLUME_MODE_BUTTON_ICON_OWNER_FIELD =
        "customiuizer_volumeModeButtonIconOwner"

    internal enum class VolumeModeButtonRole {
        MUTE, DND, UNKNOWN
    }

    internal fun volumeModeButtonRoleFromIsZen(isZen: Boolean?): VolumeModeButtonRole =
        when (isZen) {
            true -> VolumeModeButtonRole.DND
            false -> VolumeModeButtonRole.MUTE
            else -> VolumeModeButtonRole.UNKNOWN
        }

    internal const val NO_VISIBILITY_WRITE = Int.MIN_VALUE

    internal data class VolumeModeButtonVisibilityOwnership(
        var romVisibility: Int,
        var customHidden: Boolean = false
    )

    internal fun reconcileVolumeModeButtonVisibility(
        ownership: VolumeModeButtonVisibilityOwnership,
        shouldHide: Boolean,
        currentVisibility: Int
    ): Int {
        if (!shouldHide) {
            if (ownership.customHidden) {
                if (currentVisibility == View.GONE) {
                    ownership.customHidden = false
                    val target = ownership.romVisibility
                    return if (target != View.GONE) target else NO_VISIBILITY_WRITE
                }
                ownership.romVisibility = currentVisibility
                ownership.customHidden = false
                return NO_VISIBILITY_WRITE
            }
            return NO_VISIBILITY_WRITE
        }

        if (ownership.customHidden) {
            if (currentVisibility == View.GONE) {
                return NO_VISIBILITY_WRITE
            }
            ownership.romVisibility = currentVisibility
            return View.GONE
        }

        if (currentVisibility == View.GONE) {
            ownership.romVisibility = View.GONE
            return NO_VISIBILITY_WRITE
        }

        ownership.romVisibility = currentVisibility
        ownership.customHidden = true
        return View.GONE
    }

    internal class VolumeModeSharedVisibilityState(layout: View) {
        val layoutRef = WeakReference<View>(layout)
        val dividerRef = WeakReference<View>(
            layout.findViewById(
                layout.resources.getIdentifier(
                    "miui_volume_ringer_divider",
                    "id",
                    layout.context.packageName
                )
            )
        )
        val layoutOwnership = VolumeModeButtonVisibilityOwnership(romVisibility = layout.visibility)
        val dividerOwnership = VolumeModeButtonVisibilityOwnership(
            romVisibility = dividerRef.get()?.visibility ?: View.VISIBLE
        )
    }

    internal fun shouldHideVolumeModeDivider(hideMute: Boolean, hideDnd: Boolean): Boolean =
        hideMute || hideDnd

    internal fun shouldHideVolumeModeContainer(hideMute: Boolean, hideDnd: Boolean): Boolean =
        hideMute && hideDnd

    internal class VolumeModeButtonColorState(helper: Any) {
        val standardViewRef = WeakReference<View>(
            XposedHelpers.getObjectField(helper, "mStandardView") as? View
        )
        val iconRef = WeakReference<ImageView>(
            XposedHelpers.getObjectField(helper, "mIcon") as? ImageView
        )

        var iconFilter: PorterDuffColorFilter? = null
        var lastIconColor: Int = 0
        var isSelected = false
        var snapshot: VolumeModeButtonColorSnapshot? = null
        var backgroundBlended = false
        var iconBlended = false
        var coloredDrawableRef: WeakReference<GradientDrawable>? = null
        var originalBackgroundColors: ColorStateList? = null
        var appliedBackgroundColors: ColorStateList? = null
        var originalIconFilter: ColorFilter? = null
        var iconFilterApplied = false

        fun getOrCreateIconFilter(color: Int): PorterDuffColorFilter {
            val existing = iconFilter
            if (existing != null && lastIconColor == color) return existing
            lastIconColor = color
            return PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN).also {
                iconFilter = it
            }
        }

        /** Undo only our own writes before the ROM prepares its next state or theme. */
        fun restoreOwnedColors() {
            val drawable = coloredDrawableRef?.get()
            if (drawable != null && drawable.color === appliedBackgroundColors) {
                drawable.color = originalBackgroundColors
            }
            if (drawable == null) coloredDrawableRef = null
            originalBackgroundColors = null
            appliedBackgroundColors = null
            val icon = iconRef.get()
            if (iconFilterApplied && icon != null && icon.colorFilter === iconFilter) {
                icon.colorFilter = originalIconFilter
            }
            iconFilterApplied = false
            originalIconFilter = null
            backgroundBlended = false
            iconBlended = false
        }
    }

    internal fun volumeModeBlendColors(
        snapshot: VolumeModeButtonColorSnapshot,
        selected: Boolean,
        background: Boolean
    ): IntArray? = if (!snapshot.enabled || !selected) null
        else if (background) snapshot.backgroundBlendColors else snapshot.iconBlendColors

    internal fun volumeModeGradientDrawable(
        drawable: Drawable?,
        smoothChildMethod: Method?
    ): GradientDrawable? {
        if (drawable is GradientDrawable) return drawable.mutate() as? GradientDrawable
        if (drawable == null || smoothChildMethod == null ||
            !smoothChildMethod.declaringClass.isInstance(drawable)) return null
        return try {
            (smoothChildMethod.invoke(drawable) as? GradientDrawable)?.mutate() as? GradientDrawable
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    internal fun refreshVolumeModeButtonColorSnapshot() {
        val enabled = MainModule.mPrefs.getBoolean(VOLUME_MODE_BUTTON_COLORS_ENABLED_KEY, false)
        val backgroundColor = MainModule.mPrefs.getInt(
            VOLUME_MODE_BUTTON_BACKGROUND_COLOR_KEY,
            0xffffffff.toInt()
        )
        val iconColor = MainModule.mPrefs.getInt(
            VOLUME_MODE_BUTTON_ICON_COLOR_KEY,
            0xff277af7.toInt()
        )
        volumeModeButtonColorSnapshot = VolumeModeButtonColorSnapshot(
            enabled = enabled,
            backgroundColor = backgroundColor,
            iconColor = iconColor
        )
    }

    internal fun onVolumeModeButtonColorPreferenceChanged(key: String?) {
        if (key == null ||
            key == VOLUME_MODE_BUTTON_COLORS_ENABLED_KEY ||
            key == VOLUME_MODE_BUTTON_BACKGROUND_COLOR_KEY ||
            key == VOLUME_MODE_BUTTON_ICON_COLOR_KEY
        ) {
            refreshVolumeModeButtonColorSnapshot()
        }
    }

    internal fun getVolumeModeButtonColorSnapshot(): VolumeModeButtonColorSnapshot {
        if (volumeModeButtonColorSnapshot == null) refreshVolumeModeButtonColorSnapshot()
        return volumeModeButtonColorSnapshot!!
    }

    internal fun refreshVolumeModeButtonVisibilitySnapshot() {
        volumeModeButtonVisibilitySnapshot = VolumeModeButtonVisibilitySnapshot(
            hideMute = MainModule.mPrefs.getBoolean(VOLUME_MODE_BUTTON_HIDE_MUTE_KEY, false),
            hideDnd = MainModule.mPrefs.getBoolean(VOLUME_MODE_BUTTON_HIDE_DND_KEY, false)
        )
    }

    internal fun onVolumeModeButtonVisibilityPreferenceChanged(key: String?) {
        if (key == null ||
            key == VOLUME_MODE_BUTTON_HIDE_MUTE_KEY ||
            key == VOLUME_MODE_BUTTON_HIDE_DND_KEY
        ) {
            refreshVolumeModeButtonVisibilitySnapshot()
        }
    }

    internal fun getVolumeModeButtonVisibilitySnapshot(): VolumeModeButtonVisibilitySnapshot {
        if (volumeModeButtonVisibilitySnapshot == null) refreshVolumeModeButtonVisibilitySnapshot()
        return volumeModeButtonVisibilitySnapshot!!
    }

    internal fun installVolumeModeButtonColorSnapshot() {
        refreshVolumeModeButtonColorSnapshot()
        installVolumeModeButtonObserver()
    }

    internal fun installVolumeModeButtonVisibilitySnapshot() {
        refreshVolumeModeButtonVisibilitySnapshot()
        installVolumeModeButtonObserver()
    }

    private val volumeModeButtonPreferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) = ModuleHelper.guarded {
            onVolumeModeButtonColorPreferenceChanged(key)
            onVolumeModeButtonVisibilityPreferenceChanged(key)
        }
    }

    private fun installVolumeModeButtonObserver() {
        if (!volumeModeButtonObserverRegistered) {
            volumeModeButtonObserverRegistered = true
            ModuleHelper.observePreferenceChange(volumeModeButtonPreferenceObserver)
        }
    }

    @JvmStatic
    fun BlurMTKVolumeBarHook(classLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.Util", classLoader, "isSupportBlurS", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun initControlCenter(loader: ClassLoader) {
        if (MainModule.mPrefs.getBoolean("system_nosilentvibrate")) {
            ModuleHelper.hookAllMethods("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", loader, "vibrateH", HookerClassHelper.DO_NOTHING)
        }
        if (MainModule.mPrefs.getInt("system_volumedialogdelay_collapsed", 0) > 0 || MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0) > 0) {
            VolumeDialogAutohideDelayHook(loader)
        }
        if (MainModule.mPrefs.getInt("system_volumeblur_collapsed", 0) > 0 || MainModule.mPrefs.getInt("system_volumeblur_expanded", 0) > 0) {
            BlurVolumeDialogBackgroundHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_volumebar_blur_mtk")) {
            BlurMTKVolumeBarHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_volumetimer")) {
            VolumeTimerValuesRes(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_volume_mode_button_colors") ||
            MainModule.mPrefs.getBoolean(VOLUME_MODE_BUTTON_HIDE_MUTE_KEY) ||
            MainModule.mPrefs.getBoolean(VOLUME_MODE_BUTTON_HIDE_DND_KEY)
        ) {
            VolumeModeButtonColorsHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_tile_roundedrect")) {
            CCTileCornerHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_volume_showpct")) {
            ShowVolumePctHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_qs_hideoperator")
            || MainModule.mPrefs.getBoolean("system_cc_hideoperator_delimiter")
            || MainModule.mPrefs.getBoolean("system_cc_show_stepcount")
        ) {
            CCHeaderHook(loader)
        }
        val customCCGrid = MainModule.mPrefs.getInt("system_ccgridcolumns", 4) > 4
        if (customCCGrid) {
            SystemCCGridHookLoader(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_hide_edit")
            || MainModule.mPrefs.getBoolean("system_cc_hide_profile_monitoring")
        ) {
            CCHideEditButtonHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_btandtorch_ascard")) {
            CCBluetoothAsCardHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_tile_enabled_color")) {
            CCTileColorHook()
        }
        if (MainModule.mPrefs.getBoolean("system_cc_card_enabled_color")) {
            CCCardColorHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_slider_color_enable")) {
            CCSliderColorHook()
        }
    }

    @JvmStatic
    fun CCHeaderHook(classLoader: ClassLoader) {
        val hideOperator = MainModule.mPrefs.getBoolean("system_qs_hideoperator")
        val hideDelimiter = MainModule.mPrefs.getBoolean("system_cc_hideoperator_delimiter")
        val showStep = MainModule.mPrefs.getBoolean("system_cc_show_stepcount")
        val stepViewId = ResourceHooks.getFakeResId("cc_step_view")
        val tag = "StepInControlCenter"
        val hideViewHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                if (hideOperator || hideDelimiter) {
                    val resId = headerView.resources.getIdentifier("header_carrier_view", "id", "miui.systemui.plugin")
                    val mCarrierText = headerView.findViewById<TextView>(resId)
                    if (hideOperator) {
                        mCarrierText.text = ""
                    } else {
                        mCarrierText.text = mCarrierText.text.toString().replace(" | ", "")
                    }
                }
                if (showStep) {
                    val stepView = headerView.findViewWithTag<TextView>(tag)
                    if (stepView != null) {
                        val promptInfo = XposedHelpers.getObjectField(param.getThisObject(), "promptInfo")
                        val miuiPromptInfo = XposedHelpers.getObjectField(param.getThisObject(), "miuiPromptInfo")
                        var viz = View.GONE
                        if (promptInfo == null && miuiPromptInfo == null) {
                            val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", classLoader)
                            val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                            val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", headerView.context) as Boolean
                            if (verticalMode) {
                                viz = View.VISIBLE
                            }
                        }
                        stepView.visibility = viz
                    }
                }
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "adjustCarrierOrPrompt", hideViewHook)

        if (showStep) {
            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "onExpandChange", Float::class.javaPrimitiveType!!, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                    val stepView = headerView.findViewWithTag<TextView>(tag)
                    if (stepView != null) {
                        stepView.translationY = param.getArg(0) as Float
                    }
                }
            })
            val initStepViewHook = object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                    var stepView = headerView.findViewWithTag<TextView>(tag)
                    if (stepView == null) {
                        stepView = TextView(headerView.context)
                        stepView.id = stepViewId
                        val res = headerView.resources
                        val styleId = res.getIdentifier("TextAppearance.Header.Text", "style", "miui.systemui.plugin")
                        stepView.setTextAppearance(styleId)
                        stepView.setTag(tag)
                        headerView.addView(stepView)
                        StepCounterController.bindStepView(stepView)
                    }
                }
            }
            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "createStatusBarViews", initStepViewHook)

            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "updateConstraint", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                    val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", classLoader)
                    val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                    val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", headerView.context) as Boolean
                    if (verticalMode) {
                        val ConstraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
                        val constraintSet = XposedHelpers.newInstance(ConstraintSetClass)
                        XposedHelpers.callMethod(constraintSet, "clone", headerView)
                        val carrierId = headerView.resources.getIdentifier("header_carrier_view", "id", "miui.systemui.plugin")
                        val iconsId = headerView.resources.getIdentifier("header_status_bar_icons", "id", "miui.systemui.plugin")
                        val dimId = headerView.resources.getIdentifier("header_carrier_vertical_mode_margin_bottom", "dimen", "miui.systemui.plugin")
                        val marginBottom = headerView.resources.getDimensionPixelSize(dimId)
                        XposedHelpers.callMethod(constraintSet, "connect", stepViewId, 4, iconsId, 3, marginBottom)
                        XposedHelpers.callMethod(constraintSet, "connect", stepViewId, 7, carrierId, 6, HookUtils.dp2px(4f).toInt())
                        XposedHelpers.callMethod(constraintSet, "applyTo", headerView)
                    }
                }
            })
        }
    }

    @JvmStatic
    fun hasControlCenterModifications(): Boolean {
        return MainModule.mPrefs.getBoolean("system_nosilentvibrate")
            || MainModule.mPrefs.getInt("system_volumedialogdelay_collapsed", 0) > 0
            || MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0) > 0
            || MainModule.mPrefs.getInt("system_volumeblur_collapsed", 0) > 0
            || MainModule.mPrefs.getInt("system_volumeblur_expanded", 0) > 0
            || MainModule.mPrefs.getBoolean("system_volumebar_blur_mtk")
            || MainModule.mPrefs.getBoolean("system_volumetimer")
            || MainModule.mPrefs.getBoolean("system_volume_mode_button_colors")
            || MainModule.mPrefs.getBoolean(VOLUME_MODE_BUTTON_HIDE_MUTE_KEY)
            || MainModule.mPrefs.getBoolean(VOLUME_MODE_BUTTON_HIDE_DND_KEY)
            || MainModule.mPrefs.getBoolean("system_cc_tile_roundedrect")
            || MainModule.mPrefs.getBoolean("system_cc_volume_showpct")
            || MainModule.mPrefs.getBoolean("system_qs_hideoperator")
            || MainModule.mPrefs.getBoolean("system_cc_hideoperator_delimiter")
            || MainModule.mPrefs.getBoolean("system_cc_show_stepcount")
            || MainModule.mPrefs.getInt("system_ccgridcolumns", 4) > 4
            || MainModule.mPrefs.getBoolean("system_cc_hide_edit")
            || MainModule.mPrefs.getBoolean("system_cc_hide_profile_monitoring")
            || MainModule.mPrefs.getBoolean("system_cc_btandtorch_ascard")
            || MainModule.mPrefs.getBoolean("system_cc_tile_enabled_color")
            || MainModule.mPrefs.getBoolean("system_cc_card_enabled_color")
            || MainModule.mPrefs.getBoolean("system_cc_slider_color_enable")
    }

    private const val VOLUME_MODE_BUTTON_STATE_FIELD_NAME = "mState"

    /**
     * Resolves the ROM helper's active-state field once when the plugin ClassLoader is installed.
     * The resolved [Field] is captured as prepared/cold state so the hot [updateState] callback
     * can call [Field.getBoolean] directly without field discovery. If the field cannot be found
     * or is not a boolean, color styling fails open and the ROM owns the inactive appearance.
     */
    private fun resolveVolumeModeButtonStateField(pluginLoader: ClassLoader): Field? {
        val helperClassName =
            "com.android.systemui.miui.volume.MiuiRingerModeLayout\$RingerButtonHelper"
        return try {
            val helperClass = XposedHelpers.findClassIfExists(helperClassName, pluginLoader)
                ?: return null
            val field = XposedHelpers.findFieldIfExists(helperClass, VOLUME_MODE_BUTTON_STATE_FIELD_NAME)
            if (field?.type == Boolean::class.javaPrimitiveType) {
                field
            } else {
                null
            }
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
    }

    /**
     * Styles and/or hides the ROM-owned silent and DND shortcuts at their state-update boundary.
     * Colors and visibility are captured once when the plugin ClassLoader is installed, keeping
     * preference and resource lookups out of the volume panel's hot update path.
     */
    @JvmStatic
    fun VolumeModeButtonColorsHook(pluginLoader: ClassLoader) {
        installVolumeModeButtonColorSnapshot()
        installVolumeModeButtonVisibilitySnapshot()

        val mStateField = resolveVolumeModeButtonStateField(pluginLoader)
        if (mStateField == null) {
            XposedHelpers.log("VolumeModeButtonColors: mState field not resolved; color styling disabled")
        }

        val helperClassName =
            "com.android.systemui.miui.volume.MiuiRingerModeLayout\$RingerButtonHelper"

        val smoothChildMethod = try {
            XposedHelpers.findClassIfExists("miuix.smooth.SmoothContainerDrawable", pluginLoader)
                ?.getDeclaredMethod("getChildDrawable")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }

        // Replace native selected blend inputs instead of stacking a second tint over them.
        // Only views bound to these two ROM helpers participate; all other volume views pass.
        fun blendHook(colorsIndex: Int) = object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                ModuleHelper.guarded {
                    val view = callback.getArg(0) as? View ?: return@guarded
                    val backgroundOwner = XposedHelpers.getAdditionalInstanceField(
                        view, VOLUME_MODE_BUTTON_BACKGROUND_OWNER_FIELD
                    ) as? VolumeModeButtonColorState
                    val owner = backgroundOwner ?: XposedHelpers.getAdditionalInstanceField(
                        view, VOLUME_MODE_BUTTON_ICON_OWNER_FIELD
                    ) as? VolumeModeButtonColorState ?: return@guarded
                    val snapshot = owner.snapshot ?: return@guarded
                    val background = backgroundOwner != null
                    val colors = volumeModeBlendColors(snapshot, owner.isSelected, background)
                        ?: return@guarded
                    callback.getArgs()[colorsIndex] = colors
                    if (background) owner.backgroundBlended = true else owner.iconBlended = true
                }
            }
        }
        if (mStateField != null && volumeModeButtonColorSnapshot?.enabled == true) {
            ModuleHelper.findAndHookMethod(
                "com.android.systemui.miui.volume.Util", pluginLoader,
                "setMiViewBlurAndBlendColor", View::class.java,
                Boolean::class.javaPrimitiveType, Context::class.java,
                Int::class.javaPrimitiveType, IntArray::class.java,
                Boolean::class.javaPrimitiveType, blendHook(4)
            )
            ModuleHelper.findAndHookMethod(
                "com.android.systemui.miui.volume.Util", pluginLoader,
                "setMiViewBlurAndBlendColor", View::class.java,
                Int::class.javaPrimitiveType, IntArray::class.java, blendHook(2)
            )
        }

        val applyVisibility = { helper: Any ->
            ModuleHelper.guarded {
                val snapshot = volumeModeButtonVisibilitySnapshot ?: return@guarded
                val sharedState = XposedHelpers.getAdditionalInstanceField(
                    helper,
                    VOLUME_MODE_SHARED_VISIBILITY_STATE_FIELD
                ) as? VolumeModeSharedVisibilityState

                val role = XposedHelpers.getAdditionalInstanceField(
                    helper,
                    VOLUME_MODE_BUTTON_ROLE_FIELD
                ) as? VolumeModeButtonRole ?: return@guarded
                if (role == VolumeModeButtonRole.UNKNOWN) return@guarded
                val shouldHide = when (role) {
                    VolumeModeButtonRole.MUTE -> snapshot.hideMute
                    VolumeModeButtonRole.DND -> snapshot.hideDnd
                    VolumeModeButtonRole.UNKNOWN -> false
                }
                val rootRef = XposedHelpers.getAdditionalInstanceField(
                    helper,
                    VOLUME_MODE_BUTTON_ROOT_FIELD
                ) as? WeakReference<View>
                val root = rootRef?.get() ?: return@guarded
                val ownership = XposedHelpers.getAdditionalInstanceField(
                    helper,
                    VOLUME_MODE_BUTTON_VISIBILITY_OWNERSHIP_FIELD
                ) as? VolumeModeButtonVisibilityOwnership ?: return@guarded
                val newVisibility = reconcileVolumeModeButtonVisibility(
                    ownership,
                    shouldHide,
                    root.visibility
                )
                if (newVisibility != NO_VISIBILITY_WRITE) {
                    root.visibility = newVisibility
                }

                if (sharedState != null) {
                    applyVolumeModeSharedVisibility(sharedState, snapshot.hideMute, snapshot.hideDnd)
                }
            }
        }

        val applyColors = { helper: Any ->
            ModuleHelper.guarded {
                val colorState = XposedHelpers.getAdditionalInstanceField(
                    helper,
                    VOLUME_MODE_BUTTON_COLOR_STATE_FIELD
                ) as? VolumeModeButtonColorState ?: return@guarded
                val snapshot = colorState.snapshot ?: return@guarded
                if (!snapshot.enabled) return@guarded
                if (!colorState.isSelected) return@guarded
                val standardView = colorState.standardViewRef.get() ?: return@guarded
                val icon = colorState.iconRef.get() ?: return@guarded

                if (!colorState.backgroundBlended) {
                    // Non-blur ROM path: change the solid fill inside its original smooth mask.
                    // This preserves corners/animation and uses the requested alpha once.
                    val background = standardView.background?.mutate()
                    // SmoothContainerDrawable does not mutate its child on this ROM.
                    // Isolate the actual GradientDrawable ConstantState before changing its fill.
                    val gradient = volumeModeGradientDrawable(background, smoothChildMethod)
                    val colors = snapshot.backgroundColors
                    if (gradient != null && colors != null) {
                        colorState.originalBackgroundColors = gradient.color
                        colorState.appliedBackgroundColors = colors
                        if (colorState.coloredDrawableRef?.get() !== gradient) {
                            colorState.coloredDrawableRef = WeakReference(gradient)
                        }
                        if (gradient.color !== colors) gradient.color = colors
                    }
                }
                if (!colorState.iconBlended) {
                    colorState.originalIconFilter = icon.colorFilter
                    val filter = colorState.getOrCreateIconFilter(snapshot.iconColor)
                    if (icon.colorFilter !== filter) icon.colorFilter = filter
                    colorState.iconFilterApplied = true
                }
            }
        }

        val prepareColors = { helper: Any ->
            ModuleHelper.guarded {
                val colorState = XposedHelpers.getAdditionalInstanceField(
                    helper, VOLUME_MODE_BUTTON_COLOR_STATE_FIELD
                ) as? VolumeModeButtonColorState ?: return@guarded
                colorState.isSelected = false
                colorState.snapshot = volumeModeButtonColorSnapshot
                colorState.restoreOwnedColors()
                if (colorState.snapshot?.enabled != true) return@guarded
                // Resolve the field once per plugin loader, read it before the ROM paints.
                colorState.isSelected = mStateField?.getBoolean(helper) == true
            }
        }

        val constructorHook = object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() != null) return
                val helper = callback.getThisObject() ?: return
                val layout = try {
                    callback.getArgs().getOrNull(0) as? View
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    null
                }
                val root = try {
                    callback.getArgs().getOrNull(1) as? View
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    null
                }
                bindVolumeModeButtonRole(helper, root)
                bindVolumeModeButtonSharedState(helper, layout)
                bindVolumeModeButtonColorState(helper)
                prepareColors(helper)
                applyColors(helper)
                applyVisibility(helper)
            }
        }

        val updateStateHook = object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                callback.getThisObject()?.let(prepareColors)
            }

            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() != null) return
                callback.getThisObject()?.let { helper ->
                    applyColors(helper)
                    applyVisibility(helper)
                }
            }
        }

        ModuleHelper.hookAllConstructors(
            helperClassName,
            pluginLoader,
            constructorHook
        )
        ModuleHelper.hookAllMethods(
            helperClassName,
            pluginLoader,
            "updateState",
            updateStateHook
        )
    }

    private fun applyVolumeModeSharedVisibility(
        state: VolumeModeSharedVisibilityState,
        hideMute: Boolean,
        hideDnd: Boolean
    ) {
        val layout = state.layoutRef.get() ?: return
        val layoutNewVisibility = reconcileVolumeModeButtonVisibility(
            state.layoutOwnership,
            shouldHideVolumeModeContainer(hideMute, hideDnd),
            layout.visibility
        )
        if (layoutNewVisibility != NO_VISIBILITY_WRITE) {
            layout.visibility = layoutNewVisibility
        }

        val divider = state.dividerRef.get() ?: return
        val dividerNewVisibility = reconcileVolumeModeButtonVisibility(
            state.dividerOwnership,
            shouldHideVolumeModeDivider(hideMute, hideDnd),
            divider.visibility
        )
        if (dividerNewVisibility != NO_VISIBILITY_WRITE) {
            divider.visibility = dividerNewVisibility
        }
    }

    private fun bindVolumeModeButtonRole(helper: Any, root: View?) {
        if (root == null) return
        val isZen = try {
            XposedHelpers.getBooleanField(helper, "mIsZen")
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
        val role = volumeModeButtonRoleFromIsZen(isZen)
        XposedHelpers.setAdditionalInstanceField(helper, VOLUME_MODE_BUTTON_ROLE_FIELD, role)
        if (role != VolumeModeButtonRole.UNKNOWN) {
            XposedHelpers.setAdditionalInstanceField(
                helper,
                VOLUME_MODE_BUTTON_ROOT_FIELD,
                WeakReference(root)
            )
            XposedHelpers.setAdditionalInstanceField(
                helper,
                VOLUME_MODE_BUTTON_VISIBILITY_OWNERSHIP_FIELD,
                VolumeModeButtonVisibilityOwnership(romVisibility = root.visibility)
            )
        }
    }

    private fun bindVolumeModeButtonSharedState(helper: Any, layout: View?) {
        if (layout == null) return
        val state = XposedHelpers.getAdditionalInstanceField(
            layout,
            VOLUME_MODE_SHARED_VISIBILITY_STATE_FIELD
        ) as? VolumeModeSharedVisibilityState
            ?: VolumeModeSharedVisibilityState(layout).also {
                XposedHelpers.setAdditionalInstanceField(
                    layout,
                    VOLUME_MODE_SHARED_VISIBILITY_STATE_FIELD,
                    it
                )
            }
        XposedHelpers.setAdditionalInstanceField(helper, VOLUME_MODE_SHARED_VISIBILITY_STATE_FIELD, state)
    }

    private fun bindVolumeModeButtonColorState(helper: Any) {
        if (XposedHelpers.getAdditionalInstanceField(helper, VOLUME_MODE_BUTTON_COLOR_STATE_FIELD) != null) {
            return
        }
        val state = VolumeModeButtonColorState(helper)
        XposedHelpers.setAdditionalInstanceField(
            helper,
            VOLUME_MODE_BUTTON_COLOR_STATE_FIELD,
            state
        )
        state.standardViewRef.get()?.let {
            XposedHelpers.setAdditionalInstanceField(it, VOLUME_MODE_BUTTON_BACKGROUND_OWNER_FIELD, state)
        }
        state.iconRef.get()?.let {
            XposedHelpers.setAdditionalInstanceField(it, VOLUME_MODE_BUTTON_ICON_OWNER_FIELD, state)
        }
    }

    @JvmStatic
    fun ControlCenterPluginHook(lpparam: PackageReadyParam) {
        ControlCenterPluginRuntime.hookIfNeeded(lpparam)
    }

    private var iconScaleRatio = 1f

    @JvmStatic
    fun SystemCCGridHookLoader(pluginLoader: ClassLoader) {
        val cols = MainModule.mPrefs.getInt("system_ccgridcolumns", 4)
        iconScaleRatio = 4f / cols
        val resizeIconFrame = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisView = param.getThisObject() as FrameLayout
                val resId = thisView.resources.getIdentifier("icon_frame", "id", "miui.systemui.plugin")
                val iconFrame = thisView.findViewById<View>(resId)
                val iconSize = HookUtils.dp2px(68f * iconScaleRatio).toInt()
                iconFrame.layoutParams.width = iconSize
                iconFrame.layoutParams.height = iconSize

                if (param.getMember().name == "onFinishInflate") {
                    XposedHelpers.callMethod(thisView, "changeExpand")
                }
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "updateSize", resizeIconFrame)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "onFinishInflate", resizeIconFrame)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "updateContainerHeight", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val iconSize = HookUtils.dp2px(85f * iconScaleRatio + 1).toInt()
                XposedHelpers.setObjectField(param.getThisObject(), "containerHeight", iconSize)
            }
        })

        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelController", pluginLoader, "setUseSeparatedPanels", Boolean::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (param.getArgs().isEmpty() || param.getArg(0) == null) {
                    param.returnAndSkip(null)
                    return
                }
                val bool = param.getArg(0) as? Boolean ?: return
                val oldVal = XposedHelpers.getObjectField(param.getThisObject(), "useSeparatedPanels")
                if (bool == oldVal) {
                    param.returnAndSkip(null)
                    return
                }
                XposedHelpers.setObjectField(param.getThisObject(), "useSeparatedPanels", bool)
                val horizontalMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "horizontalMainPanel") as LinearLayout
                val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                horizontalMainPanel.removeView(leftMainPanel)
                if (!bool) {
                    horizontalMainPanel.addView(leftMainPanel)
                    val layoutParams = leftMainPanel.layoutParams
                    (layoutParams as ViewGroup.MarginLayoutParams).setMarginEnd(0)
                    horizontalMainPanel.orientation = LinearLayout.VERTICAL
                } else {
                    horizontalMainPanel.addView(leftMainPanel, 0)
                    val marginId = horizontalMainPanel.resources.getIdentifier("control_center_horizontal_margin_center", "dimen", "miui.systemui.plugin")
                    val marginEnd = horizontalMainPanel.resources.getDimensionPixelSize(marginId)
                    XposedHelpers.setObjectField(param.getThisObject(), "panelMargin", marginEnd)
                    val layoutParams = leftMainPanel.layoutParams
                    (layoutParams as ViewGroup.MarginLayoutParams).setMarginEnd(marginEnd)
                    horizontalMainPanel.orientation = LinearLayout.HORIZONTAL
                }
                param.returnAndSkip(null)
            }
        })

        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, "distributePanels", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val horizontal = param.getArg(0) as Boolean
                if (!horizontal && XposedHelpers.getBooleanField(param.getThisObject(), "inited")) {
                    val rightPanelContent = XposedHelpers.getObjectField(param.getThisObject(), "rightPanelContent") as ArrayList<*>
                    val leftPanelContent = XposedHelpers.getObjectField(param.getThisObject(), "leftPanelContent") as ArrayList<Any>
                    val size = rightPanelContent.size
                    for (i in size - 1 downTo 0) {
                        val controller = rightPanelContent[i]
                        val className = controller?.javaClass?.canonicalName ?: ""
                        if (className.contains("EditButtonController")
                            || className.contains("SecurityFooterController")
                            || className.contains("QSListController")
                        ) {
                            rightPanelContent.removeAt(i)
                            leftPanelContent.add(controller)
                        } else if (className.contains("FooterSpaceController")) {
                            rightPanelContent.removeAt(i)
                        }
                    }
                    leftPanelContent.sortWith(Comparator { lhs, rhs ->
                        val leftPriority = XposedHelpers.callMethod(lhs, "getPriority") as Int
                        val rightPriority = XposedHelpers.callMethod(rhs, "getPriority") as Int
                        leftPriority - rightPriority
                    })
                }
            }
        })

        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelController", pluginLoader, "updatePanelSize", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val useSeparatedPanels = XposedHelpers.getObjectField(param.getThisObject(), "useSeparatedPanels") as? Boolean
                if (useSeparatedPanels != true) {
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    val rightMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "rightMainPanel") as ViewGroup
                    val panelWidth = XposedHelpers.getIntField(param.getThisObject(), "panelWidth")
                    leftMainPanel.layoutParams.width = panelWidth
                    leftMainPanel.layoutParams.height = -2
                    rightMainPanel.layoutParams.width = panelWidth
                    rightMainPanel.layoutParams.height = -2
                    param.returnAndSkip(null)
                }
            }
        })

        val MainPanelAdapter = XposedHelpers.findClass("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", pluginLoader)

        val spanSizeHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val adapter = XposedHelpers.getSurroundingThis(param.getThisObject())
                val leftAdapter = XposedHelpers.getAdditionalInstanceField(adapter, "leftAdapter") != null
                if (leftAdapter) {
                    val companion = XposedHelpers.getStaticObjectField(MainPanelAdapter, "Companion")
                    val contentMap = XposedHelpers.getObjectField(adapter, "contentMap")
                    if (param.getArgs().isEmpty()) {
                        param.returnAndSkip(cols)
                        return
                    }
                    val panelItem = XposedHelpers.callMethod(companion, "getItem", contentMap, param.getArg(0))
                    if (panelItem == null) {
                        param.returnAndSkip(cols)
                    } else {
                        param.returnAndSkip(XposedHelpers.callMethod(panelItem, "getSpanSize"))
                    }
                }
            }
        }

        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter\$Factory", pluginLoader, "create", object : MethodHook() {
            private var hooked = false
            override fun after(param: AfterHookCallback) {
                if (!hooked) {
                    hooked = true
                    XposedHelpers.setAdditionalInstanceField(param.getResult(), "leftAdapter", true)
                    val layoutManager = XposedHelpers.getObjectField(param.getResult(), "layoutManager")
                    XposedHelpers.callMethod(layoutManager, "setSpanCount", cols)
                    val spanSizeLookup = XposedHelpers.callMethod(layoutManager, "getSpanSizeLookup")
                    ModuleHelper.findAndHookMethod(spanSizeLookup.javaClass, "getSpanSize", Int::class.javaPrimitiveType!!, spanSizeHook)
                }
            }
        })

        val columnsReplaceHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.returnAndSkip(cols)
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.HeaderSpaceController", pluginLoader, "getSpanSize", columnsReplaceHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.security.SecurityFooterController", pluginLoader, "getSpanSize", columnsReplaceHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.qs.EditButtonController", pluginLoader, "getSpanSize", columnsReplaceHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.qs.QSListController\$EditModeDividerTextItem", pluginLoader, "getSpanSize", columnsReplaceHook)

        // handle secondary panel show
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "updateVisibility", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", pluginLoader)
                val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                val mContext = XposedHelpers.callMethod(param.getThisObject(), "getContext")
                val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", mContext) as Boolean
                if (verticalMode) {
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    leftMainPanel.visibility = param.getArg(0) as Int
                }
            }
        })
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "forceToShow", Object::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                leftMainPanel.alpha = 1.0f
            }
        })
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "onAnimUpdate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", pluginLoader)
                val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                val mContext = XposedHelpers.callMethod(param.getThisObject(), "getContext")
                val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", mContext) as Boolean
                if (verticalMode) {
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    val rightMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "rightMainPanel") as ViewGroup
                    val alpha = rightMainPanel.alpha
                    leftMainPanel.alpha = alpha
                }
            }
        })
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "onConfigurationChanged", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val i = param.getArg(0) as Int
                if ((i and 128) != 0) {
                    val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", pluginLoader)
                    val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                    val mContext = XposedHelpers.callMethod(param.getThisObject(), "getContext")
                    val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", mContext) as Boolean
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    val rightMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "rightMainPanel") as ViewGroup
                    if (verticalMode) {
                        leftMainPanel.alpha = rightMainPanel.alpha
                        leftMainPanel.visibility = rightMainPanel.visibility
                    } else {
                        leftMainPanel.alpha = 1.0f
                        leftMainPanel.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    @JvmStatic
    fun CCHideEditButtonHook(pluginLoader: ClassLoader) {
        val hideEdit = MainModule.mPrefs.getBoolean("system_cc_hide_edit")
        val hideSecurity = MainModule.mPrefs.getBoolean("system_cc_hide_profile_monitoring")
        if (!hideEdit && !hideSecurity) return

        fun shouldHide(controller: Any?): Boolean {
            val className = controller?.javaClass?.canonicalName ?: ""
            return (hideEdit && className.contains("EditButtonController"))
                || (hideSecurity && className.contains("SecurityFooterController"))
        }

        // Filter the source list when the distributor is first created.
        ModuleHelper.hookAllConstructors("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisObj = param.getThisObject()
                val rawChildControllers = try {
                    XposedHelpers.getObjectField(thisObj, "childControllers")
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    return
                }

                val childControllers: MutableList<Any?> = when (rawChildControllers) {
                    is MutableList<*> -> rawChildControllers.toMutableList()
                    is List<*> -> ArrayList(rawChildControllers as Collection<Any?>)
                    else -> return
                }

                val iter = childControllers.iterator()
                while (iter.hasNext()) {
                    if (shouldHide(iter.next())) iter.remove()
                }

                try {
                    XposedHelpers.setObjectField(thisObj, "childControllers", childControllers)
                } catch (ignored: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(ignored)
                    // final or unmodifiable field: rely on the distributePanels fallback
                }
            }
        })

        // Also remove from the panel content lists each time they are rebuilt.
        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, "distributePanels", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisObj = param.getThisObject()
                val rightPanelContent = try {
                    XposedHelpers.getObjectField(thisObj, "rightPanelContent") as? ArrayList<*>
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    null
                } ?: return
                val leftPanelContent = try {
                    XposedHelpers.getObjectField(thisObj, "leftPanelContent") as? ArrayList<*>
                } catch (t: Throwable) {
                    FatalErrors.rethrowIfFatal(t)
                    null
                } ?: return

                val sizeRight = rightPanelContent.size
                for (i in sizeRight - 1 downTo 0) {
                    if (shouldHide(rightPanelContent[i])) rightPanelContent.removeAt(i)
                }
                val sizeLeft = leftPanelContent.size
                for (i in sizeLeft - 1 downTo 0) {
                    if (shouldHide(leftPanelContent[i])) leftPanelContent.removeAt(i)
                }
            }
        })
    }

    @JvmStatic
    fun CCBluetoothAsCardHook(pluginLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.QSController", pluginLoader, "getCardStyleTileSpecs", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.returnAndSkip(listOf("wifi", "cell", "bt", "flashlight"))
            }
        })
    }

    @JvmStatic
    fun CCTileColorHook() {
        val customColor = MainModule.mPrefs.getInt("system_cc_tile_enabled_color_custom", 0xff277af7.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_enabled_color", customColor)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_warning_color", customColor)

        val iconColor = MainModule.mPrefs.getInt("system_cc_tile_enabled_iconcolor_custom", 0xffffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_icon_enabled_color", iconColor)
    }

    @JvmStatic
    fun CCCardColorHook(loader: ClassLoader) {
        val customColor = MainModule.mPrefs.getInt("system_cc_card_enabled_color_custom", 0xff3482ff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_cellular_color", customColor)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_enabled_color", customColor)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_flashlight_color", customColor)

        val primaryColor = MainModule.mPrefs.getInt("system_cc_card_enabled_primary_textcolor", 0xffffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_primary_text_enabled_color", primaryColor)
        val secondaryColor = MainModule.mPrefs.getInt("system_cc_card_enabled_secondary_textcolor", 0x80ffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_secondary_text_enabled_color", secondaryColor)

        val iconColor = MainModule.mPrefs.getInt("system_cc_card_enabled_iconcolor_custom", 0xffffffff.toInt())
        if (iconColor != 0xffffffff.toInt()) {
            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", loader, "updateResources", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    XposedHelpers.setObjectField(param.getThisObject(), "iconColor", iconColor)
                }
            })
        }
    }

    @JvmStatic
    fun CCSliderColorHook() {
        val customColor = MainModule.mPrefs.getInt("system_cc_slider_progress_color", 0xffffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "toggle_slider_progress_color", customColor)
        val blendColors = intArrayOf(customColor, 3)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "integer-array", "toggle_slider_progress_blend_colors", blendColors)

        val iconColor = MainModule.mPrefs.getInt("system_cc_slider_icon_color", 0xff959595.toInt())
        if (iconColor != 0xff959595.toInt()) {
            MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "toggle_slider_icon_color", iconColor)
            val iconBlendColors = intArrayOf(iconColor, 3)
            MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "integer-array", "toggle_slider_icon_blend_colors", iconBlendColors)
        }
    }

    @JvmStatic
    fun VolumeTimerValuesRes(pluginLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeTimerDrawableHelper", pluginLoader, "initTimerString", VolumeTimerLabelsHook())
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.TimerItem", pluginLoader, "getTimePos", Int::class.javaPrimitiveType!!, VolumeTimerPositionHook())

        ModuleHelper.guarded {
            val helperClass = XposedHelpers.findClass(
                "com.android.systemui.miui.volume.MiuiVolumeTimerDrawableHelper", pluginLoader,
            )
            ModuleHelper.findAndHookMethod(helperClass, "updateDrawables", VolumeTimerSegmentHook(helperClass))
        }
    }

    internal class VolumeTimerPositionHook : MethodHook() {
        override fun before(callback: BeforeHookCallback) {
            val timer = XposedHelpers.getObjectField(callback.getThisObject(), "mTimerTime")
            val halfTimerWidth = (XposedHelpers.callMethod(timer, "getWidth") as Int) / 2.0f
            val mContext = XposedHelpers.getObjectField(callback.getThisObject(), "mContext") as Context
            val mTimerSeekbarWidth = ModuleHelper.getObjectFieldSilently(callback.getThisObject(), "mTimerSeekbarWidth")
            val seekbarWidthResId: Int
            if (mTimerSeekbarWidth == ModuleHelper.NOT_EXIST_SYMBOL) {
                seekbarWidthResId = mContext.resources.getIdentifier("miui_volume_timer_seekbar_width", "dimen", "miui.systemui.plugin")
            } else {
                seekbarWidthResId = mTimerSeekbarWidth as Int
            }
            if (seekbarWidthResId == 0) return
            val mTimerSeekbarMarginLeft = mContext.resources.getIdentifier("miui_volume_timer_seekbar_margin_left", "dimen", "miui.systemui.plugin")
            if (mTimerSeekbarMarginLeft == 0) return
            val seekWidth = mContext.resources.getDimension(seekbarWidthResId)
            val marginLeft = mContext.resources.getDimensionPixelSize(mTimerSeekbarMarginLeft)
            val seg = XposedHelpers.getObjectField(callback.getThisObject(), "mDeterminedSegment") as Int
            callback.returnAndSkip(seekWidth / 10 * seg + marginLeft - halfTimerWidth)
        }
    }

    internal class VolumeTimerLabelsHook : MethodHook() {
        override fun after(callback: AfterHookCallback) {
            if (callback.getThrowable() != null) return
            val helper = callback.getThisObject()
            val nativeTitles = XposedHelpers.getObjectField(helper, "mTimeSegmentTitle") as? Array<*> ?: return
            val offLabel = nativeTitles.firstOrNull() as? String ?: return
            val titles = ModuleHelper.guarded<Array<String>?>(null) {
                val context = XposedHelpers.getObjectField(helper, "mContext") as Context
                val locale = context.resources.configuration.locales[0] ?: return@guarded null
                VolumeTimerValues.labels(locale, offLabel)
            } ?: VolumeTimerValues.labels(null, offLabel)
            // Always publish a whole array; the ROM clamps extended choices to its last title.
            XposedHelpers.setObjectField(helper, "mTimeSegmentTitle", titles)
        }
    }

    internal class VolumeTimerSegmentHook(helperClass: Class<*>) : MethodHook() {
        private val currentSegment = XposedHelpers.findField(helperClass, "mCurrentSegment")
        private val determinedSegment = XposedHelpers.findField(helperClass, "mDeterminedSegment")

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val helper = ModuleHelper.guarded<Any?>(null) { chain.thisObject }
            val previous = if (helper == null) null else ModuleHelper.guarded<Int?>(null) {
                val segment = currentSegment.getInt(helper)
                if (segment in 1..2 || (segment == 3 && determinedSegment.getInt(helper) == 3)) {
                    currentSegment.setInt(helper, 0)
                    segment
                } else null
            }

            // This temporary draw-only value belongs to one invocation, including reentry.
            // The shared before/after adapter deliberately skips after on fatal failures.
            try {
                return chain.proceed()
            } catch (t: Throwable) {
                throw FatalErrors.unwrapAndRethrowIfFatal(t)
            } finally {
                if (helper != null && previous != null) {
                    ModuleHelper.guarded {
                        // Preserve a newer segment written by the ROM or another hook.
                        if (currentSegment.getInt(helper) == 0) currentSegment.setInt(helper, previous)
                    }
                }
            }
        }
    }

    @JvmStatic
    fun CCTileCornerHook(pluginLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getCornerRadius", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val radius = 20 * iconScaleRatio
                param.returnAndSkip(HookUtils.dp2px(radius))
            }
        })
        val radiusHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val drawable = param.getResult()
                val gradientDrawable = drawable as? GradientDrawable
                if (gradientDrawable != null) {
                    val radius = 20 * iconScaleRatio
                    gradientDrawable.cornerRadius = HookUtils.dp2px(radius)
                }
            }
        }
        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getDisabledBackgroundDrawable", radiusHook)
        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getActiveBackgroundDrawable", radiusHook)
    }

    @JvmStatic
    fun StatusBarGesturesHook(lpparam: PackageReadyParam) {
        val runtime = ControlCenterPluginRuntime
        runtime.configPublisher.publish()

        val statusBarObserver = object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                runtime.configPublisher.publish()
            }
        }
        ModuleHelper.observePreferenceChange(statusBarObserver)

        val statusBarMachine = GestureMachine(
            classLoaderIdentity = lpparam.packageName.orEmpty(),
            configResolver = { runtime.configPublisher.get() },
            depsResolver = StatusBarGestureDependenciesResolver(lpparam.classLoader),
            effectExecutor = StatusBarGestureEffectExecutor(),
            arbiter = runtime.arbiter(),
        )
        val statusBarHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val thisObject = param.getThisObject() as? View ?: return
                if (param.getArgs().isEmpty()) return
                val event = param.getArg(0) as? MotionEvent ?: return
                // INTERCEPT events do not yet own the touch stream; only process TOUCH.
                if (param.getMember().name != "onTouchEvent") return
                val gestureEvent = GestureEvent(
                    entry = GestureEntry.STATUS_BAR_TOUCH,
                    actionMasked = event.actionMasked,
                    downTime = event.downTime,
                    eventTime = event.eventTime,
                    x = event.x,
                    y = event.y,
                    pointerCount = event.pointerCount,
                    ownerId = System.identityHashCode(thisObject),
                    deviceId = event.deviceId,
                    source = event.source,
                )
                ModuleHelper.guarded {
                    statusBarMachine.dispatch(gestureEvent, thisObject)
                }
            }
        }

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onInterceptTouchEvent", MotionEvent::class.java, statusBarHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onTouchEvent", MotionEvent::class.java, statusBarHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onAttachedToWindow", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val thisObject = param.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    statusBarMachine.prepare(System.identityHashCode(thisObject), thisObject)
                }
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onDetachedFromWindow", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val thisObject = param.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    statusBarMachine.clear(System.identityHashCode(thisObject))
                }
            }
        })

        runtime.hookIfNeeded(lpparam)
    }

    internal data class SecureQsSnapshot(
        val wifi: Boolean = false,
        val bt: Boolean = false,
        val cell: Boolean = false,
        val airplane: Boolean = false,
        val gps: Boolean = false,
        val hotspot: Boolean = false,
        val nfc: Boolean = false,
        val sync: Boolean = false,
        val custom: Boolean = false,
        val keepOpened: Boolean = false,
    )

    @Volatile
    private var secureQsSnapshot = SecureQsSnapshot()

    private var secureQsObserverRegistered = false

    private val SECURE_QS_KEYS = setOf(
        "system_secureqs_wifi",
        "system_secureqs_bt",
        "system_secureqs_mobiledata",
        "system_secureqs_airplane",
        "system_secureqs_location",
        "system_secureqs_hotspot",
        "system_secureqs_nfc",
        "system_secureqs_sync",
        "system_secureqs_custom",
        "system_secureqs_keepopened",
    )

    internal fun refreshSecureQsSnapshot() {
        val prefs = MainModule.mPrefs
        secureQsSnapshot = SecureQsSnapshot(
            wifi = prefs.getBoolean("system_secureqs_wifi"),
            bt = prefs.getBoolean("system_secureqs_bt"),
            cell = prefs.getBoolean("system_secureqs_mobiledata"),
            airplane = prefs.getBoolean("system_secureqs_airplane"),
            gps = prefs.getBoolean("system_secureqs_location"),
            hotspot = prefs.getBoolean("system_secureqs_hotspot"),
            nfc = prefs.getBoolean("system_secureqs_nfc"),
            sync = prefs.getBoolean("system_secureqs_sync"),
            custom = prefs.getBoolean("system_secureqs_custom"),
            keepOpened = prefs.getBoolean("system_secureqs_keepopened"),
        )
    }

    internal fun isSecureQsTile(name: String, snapshot: SecureQsSnapshot = secureQsSnapshot): Boolean =
        when (name) {
            "wifi" -> snapshot.wifi
            "bt" -> snapshot.bt
            "cell" -> snapshot.cell
            "airplane" -> snapshot.airplane
            "gps" -> snapshot.gps
            "hotspot" -> snapshot.hotspot
            "nfc" -> snapshot.nfc
            "sync" -> snapshot.sync
            "intent", "custom" -> snapshot.custom
            else -> false
        }

    internal fun installSecureQsSnapshot() {
        refreshSecureQsSnapshot()
        if (secureQsObserverRegistered) return
        secureQsObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in SECURE_QS_KEYS) refreshSecureQsSnapshot()
            }
        })
    }

    @JvmStatic
    fun SecureQSTilesHook(lpparam: PackageReadyParam) {
        installSecureQsSnapshot()
        val clickHook = object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getObjectField(param.getThisObject(), "mTileSpec") as String
                var name = tileName
                if (name.startsWith("intent(")) name = "intent"
                else if (name.startsWith("custom(")) name = "custom"
                val snapshot = secureQsSnapshot
                if (isSecureQsTile(name, snapshot)) {
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val kgMgr = mContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (!kgMgr.isKeyguardLocked || !kgMgr.isKeyguardSecure) return
                    val activityStater = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.plugins.ActivityStarter")
                    val selectedName = name
                    val reopen = secureQsReopener(mContext).begin {
                        val current = secureQsSnapshot
                        current.keepOpened && isSecureQsTile(selectedName, current)
                    }
                    XposedHelpers.callMethod(activityStater, "postQSRunnableDismissingKeyguard", true, reopen)
                    val mStatusBar = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.CommandQueue")
                    XposedHelpers.callMethod(mStatusBar, "animateCollapsePanels", 0, false)
                    param.returnAndSkip(null)
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.java, clickHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "longClick", View::class.java, clickHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "secondaryClick", View::class.java, clickHook)
    }

    private var qsReopener: tv.withaibuild.customiuizer.mods.utils.SecureQsReopener? = null

    @Synchronized
    private fun secureQsReopener(context: Context): tv.withaibuild.customiuizer.mods.utils.SecureQsReopener {
        qsReopener?.let { return it }
        val application = context.applicationContext
        val keyguard = application.getSystemService(KeyguardManager::class.java)
        return tv.withaibuild.customiuizer.mods.utils.SecureQsReopener(Handler(application.mainLooper),
            { keyguard != null && !keyguard.isKeyguardLocked }, {
                val intent = Intent(GlobalActions.ACTION_PREFIX + "ExpandSettings").setPackage("com.android.systemui")
                application.sendBroadcast(intent)
            }).also { qsReopener = it }
    }

    private var mPctRef: WeakReference<TextView>? = null
    private var mPctOwnerRef: WeakReference<Any>? = null

    private val mPct: TextView?
        get() = mPctRef?.get()

    private const val PCT_SOURCE_BRIGHTNESS = 2
    private const val PCT_SOURCE_VOLUME = 3

    /**
     * Volume percentage sits in the volume overlay, which can draw under the status bar. Its top
     * edge is the live statusBars inset, not a stored dp preference, so a custom status bar height
     * moves the next overlay instead of leaving the number inside the bar.
     */
    @JvmStatic
    internal fun resolveVolumePctTopMarginPx(statusBarBottomPx: Int): Int =
        statusBarBottomPx.coerceAtLeast(0)

    private fun statusBarBottomPx(view: View): Int {
        val insets = view.rootWindowInsets
            ?: view.context.getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics?.windowInsets
            ?: return 0
        return insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
    }

    private fun applyPctTopMargin(pct: TextView, container: View, source: Int) {
        val lp = (pct.layoutParams as? FrameLayout.LayoutParams) ?: return
        lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
        lp.topMargin = if (source == PCT_SOURCE_VOLUME) {
            resolveVolumePctTopMarginPx(statusBarBottomPx(container))
        } else {
            Math.round(
                MainModule.mPrefs.getInt("system_showpct_top", 28) *
                    pct.resources.displayMetrics.density
            )
        }
        pct.layoutParams = lp
    }

    private fun ownedPct(owner: Any?, source: Int): TextView? {
        if (owner == null || mPctOwnerRef?.get() !== owner) return null
        return mPct?.takeIf { it.tag == source }
    }

    private fun initPct(container: ViewGroup, source: Int, context: Context, owner: Any): TextView {
        val res = context.resources
        var pct = mPct
        if (pct != null && (pct.parent !== container || ownedPct(owner, source) !== pct)) {
            removePct(pct)
            pct = null
        }
        if (pct == null) {
            pct = TextView(container.context)
            pct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            pct.gravity = Gravity.CENTER
            val density = res.displayMetrics.density
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            pct.setPadding(Math.round(20 * density), Math.round(10 * density), Math.round(18 * density), Math.round(12 * density))
            pct.layoutParams = lp
            try {
                val modRes = ModuleHelper.getModuleRes(context)
                pct.setTextColor(modRes.getColor(R.color.color_on_surface_variant, context.theme))
                pct.background = ResourcesCompat.getDrawable(modRes, R.drawable.input_background, context.theme)
            } catch (err: Throwable) {
                FatalErrors.rethrowIfFatal(err)
                XposedHelpers.log(err)
            }
            pct.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) {
                    if (mPctRef?.get() === view) {
                        view.visibility = View.GONE
                        mPctOwnerRef = null
                        // Keep the weak handle so replacement can remove this child after the
                        // parent's detach traversal, without adding another overlay on reattach.
                    }
                }
            })
            container.addView(pct)
            mPctRef = WeakReference(pct)
            mPctOwnerRef = WeakReference(owner)
        }
        applyPctTopMargin(pct, container, source)
        pct.setTag(source)
        pct.visibility = View.GONE
        return pct
    }

    private fun removePct(mPctText: TextView?) {
        if (mPctText != null) {
            mPctText.visibility = View.GONE
            (mPctText.parent as? ViewGroup)?.removeView(mPctText)
            if (mPctRef?.get() === mPctText) {
                mPctRef = null
                mPctOwnerRef = null
            }
        }
    }

    private fun removePct(owner: Any?, source: Int) {
        removePct(ownedPct(owner, source))
    }

    private fun startShowPct(lpparam: PackageReadyParam, mContext: Context, owner: Any) {
        val controlCenter = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.controlcenter.phone.ControlPanelWindowManager")
        val controlCenterWindowView = XposedHelpers.getObjectField(controlCenter, "windowView")
        val windowView = XposedHelpers.callMethod(controlCenterWindowView, "getView") as ViewGroup
        initPct(windowView, PCT_SOURCE_BRIGHTNESS, mContext, owner).visibility = View.VISIBLE
    }

    @JvmStatic
    fun BrightnessPctHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "onStart", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val owner = param.getThisObject() ?: return
                val mContext = XposedHelpers.getObjectField(owner, "mContext") as Context
                startShowPct(lpparam, mContext, owner)
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "setToggleSliderBase", object : MethodHook() {
            private var inited = false
            override fun after(param: AfterHookCallback) {
                if (!inited && param.getArg(0) != null) {
                    inited = true
                    val className = param.getArg(0)!!.javaClass.simpleName
                    if ("ToggleSliderViewHolder" == className) return
                    val sliderRef = WeakReference(param.getArg(0))
                    val ownerRef = WeakReference(param.getThisObject())
                    val brightnessSeekBar = sliderRef.get() ?: return
                    val mOnSeekBarChangeListener = XposedHelpers.getObjectField(brightnessSeekBar, "mOnSeekBarChangeListener") ?: return
                    ModuleHelper.findAndHookMethod(mOnSeekBarChangeListener.javaClass, "onStartTrackingTouch", SeekBar::class.java, object : MethodHook() {
                        override fun before(param: BeforeHookCallback) {
                            val thisObject = XposedHelpers.getSurroundingThis(param.getThisObject())
                            if (sliderRef.get() !== thisObject) return
                            val owner = ownerRef.get() ?: return
                            val mContext = XposedHelpers.getObjectField(owner, "mContext") as Context
                            startShowPct(lpparam, mContext, owner)
                        }
                    })
                }
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "onStop", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                removePct(param.getThisObject(), PCT_SOURCE_BRIGHTNESS)
            }
        })

        val BrightnessUtils = XposedHelpers.findClassIfExists("com.android.systemui.controlcenter.policy.BrightnessUtils", lpparam.classLoader)
        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "onChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                if (param.getThrowable() != null) return
                val pct = ownedPct(param.getThisObject(), PCT_SOURCE_BRIGHTNESS) ?: return
                val currentLevel = param.getArg(3) as Int
                if (BrightnessUtils != null) {
                    val maxLevel = XposedHelpers.getStaticObjectField(BrightnessUtils, "GAMMA_SPACE_MAX") as Int
                    pct.text = ((currentLevel * 100) / maxLevel).toString() + "%"
                }
            }
        })
    }

    @JvmStatic
    fun ShowVolumePctHook(pluginLoader: ClassLoader) {
        val MiuiVolumeDialogImpl = XposedHelpers.findClassIfExists("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", pluginLoader) ?: return
        val listenerClass = XposedHelpers.findClassIfExists("com.android.systemui.miui.volume.MiuiVolumeDialogImpl\$VolumeSeekBarChangeListener", pluginLoader) ?: return
        val ownerField = XposedHelpers.findFieldIfExists(listenerClass, "this\$0") ?: return
        if (!MiuiVolumeDialogImpl.isAssignableFrom(ownerField.type)) return
        ModuleHelper.findAndHookMethod(MiuiVolumeDialogImpl, "showVolumeDialogH", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                if (param.getThrowable() != null) return
                val owner = param.getThisObject() ?: return
                val mDialogView = XposedHelpers.getObjectField(owner, "mDialogView") as View
                val windowView = mDialogView.parent as? ViewGroup ?: return
                initPct(windowView, PCT_SOURCE_VOLUME, windowView.context, owner)
            }
        })

        ModuleHelper.findAndHookMethod(MiuiVolumeDialogImpl, "dismissH", Int::class.javaPrimitiveType!!, VolumePercentageDismissHook())

        ModuleHelper.hookAllMethods(listenerClass, "onProgressChanged", VolumePercentageProgressHook(ownerField))
    }

    internal class VolumePercentageDismissHook : MethodHook() {
        override fun after(param: AfterHookCallback) {
            if (param.getThrowable() == null && !XposedHelpers.getBooleanField(param.getThisObject(), "mShowing")) {
                removePct(param.getThisObject(), PCT_SOURCE_VOLUME)
            }
        }
    }

    internal class VolumePercentageProgressHook(private val ownerField: Field) : MethodHook() {
        override fun after(param: AfterHookCallback) {
            if (param.getThrowable() != null) return
            val pct = ownedPct(ownerField.get(param.getThisObject()), PCT_SOURCE_VOLUME) ?: return
            val mColumn = XposedHelpers.getObjectField(param.getThisObject(), "mColumn")
            val ss = XposedHelpers.getObjectField(mColumn, "ss")
            if (ss == null) return
            if (XposedHelpers.getIntField(mColumn, "stream") == 10) return

            val fromUser = param.getArg(2) as Boolean
            val currentLevel: Int = if (fromUser) {
                param.getArg(1) as Int
            } else {
                val anim = XposedHelpers.getObjectField(mColumn, "anim") as ObjectAnimator?
                if (anim == null || !anim.isRunning) return
                XposedHelpers.getIntField(mColumn, "animTargetProgress")
            }
            val levelMin = XposedHelpers.getIntField(ss, "levelMin")
            var adjustedLevel = currentLevel
            if (levelMin > 0 && adjustedLevel < levelMin * 1000) {
                adjustedLevel = levelMin * 1000
            }
            val seekBar = param.getArg(0) as SeekBar
            val max = seekBar.max
            val maxLevel = max / 1000
            if (maxLevel <= 0) return
            adjustedLevel = adjustedLevel.coerceIn(0, max)
            if (adjustedLevel != 0) {
                val i3 = maxLevel - 1
                adjustedLevel = if (adjustedLevel == max) maxLevel else ((adjustedLevel.toFloat() / max) * i3).toInt() + 1
            }
            val text = ((adjustedLevel * 100) / maxLevel).toString() + "%"
            if (pct.text.toString() != text) pct.text = text
            pct.visibility = View.VISIBLE
        }
    }

    @JvmStatic
    fun HideSafeVolumeDlgHook(lpparam: PackageReadyParam) {
        val workerClass = XposedHelpers.findClass(
            "com.android.systemui.volume.VolumeDialogControllerImpl\$W", lpparam.classLoader,
        )
        val hook = SafeVolumeWarningHook(workerClass)
        ModuleHelper.findAndHookMethod(workerClass, "handleMessage", Message::class.java, hook)
    }

    internal class SafeVolumeWarningHook(workerClass: Class<*>) : MethodHook() {
        // Resolve only against this host ClassLoader, before installing any callback.
        private val ownerField = workerClass.getDeclaredField("this\$0").apply { isAccessible = true }
        private val warningField = ownerField.type.getDeclaredField("mShowSafetyWarning").apply { isAccessible = true }
        private val audioField = ownerField.type.getDeclaredField("mAudio").apply { isAccessible = true }
        private val disableWarning = audioField.type.getDeclaredMethod("disableSafeMediaVolume").apply { isAccessible = true }

        override fun intercept(chain: XposedInterface.Chain): Any? {
            val acknowledged = ModuleHelper.guarded(false) {
                val message = chain.getArg(0) as? Message ?: return@guarded false
                // A14 host W: 14 = SHOW_SAFETY_WARNING; 17 = SHOW_CSD_WARNING stays native.
                if (message.what != 14) return@guarded false
                val owner = ownerField.get(chain.thisObject) ?: return@guarded false
                if (!warningField.getBoolean(owner)) return@guarded false
                val audio = audioField.get(owner) ?: return@guarded false
                // Acknowledge each re-armed warning, including native pending-volume replay.
                // Leave the native gate intact so a service/permission failure can show its dialog.
                disableWarning.invoke(audio)
                true
            }
            return if (acknowledged) null else chain.proceed()
        }
    }

    @JvmStatic
    fun LongClickTileOpenInFreeFormHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "handleLongClick", View::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val longClickIntent = XposedHelpers.callMethod(param.getThisObject(), "getLongClickIntent") as Intent?
                if (longClickIntent != null) {
                    val action = longClickIntent.action
                    val isSettings = action?.startsWith("android.settings") == true
                    if (!isSettings && longClickIntent.component != null) {
                        val foregroundInfo = ProcessManager.getForegroundInfo()
                        if (foregroundInfo != null) {
                            val topPackage = foregroundInfo.mForegroundPackageName
                            if ("com.miui.home" == topPackage) {
                                return
                            }
                        }
                        val bIntent = Intent(GlobalActions.ACTION_PREFIX + "SetFreeFormPackage")
                        bIntent.putExtra("package", longClickIntent.component!!.packageName)
                        bIntent.putExtra("user", ModuleHelper.getUserId())
                        bIntent.putExtra("component", longClickIntent.component!!.flattenToString())
                        bIntent.setPackage("android")
                        val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                        mContext.sendBroadcast(bIntent)
                    }
                }
            }
        })
    }

    @JvmStatic
    fun CollapseCCAfterClickHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mState = XposedHelpers.callMethod(param.getThisObject(), "getState")
                val state = XposedHelpers.getIntField(mState, "state")
                if (state != 0) {
                    val tileSpec = XposedHelpers.callMethod(param.getThisObject(), "getTileSpec") as String
                    if (tileSpec != "edit") {
                        val mHost = XposedHelpers.getObjectField(param.getThisObject(), "mHost")
                        XposedHelpers.callMethod(mHost, "collapsePanels")
                    }
                }
            }
        })
    }

    @JvmStatic
    fun SwitchCCAndNotificationHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "handleEvent", MotionEvent::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mPanelController = XposedHelpers.getObjectField(param.getThisObject(), "mPanelController")
                val useCC = XposedHelpers.callMethod(mPanelController, "isExpandable") as Boolean
                if (useCC) {
                    val bar = param.getThisObject() as FrameLayout
                    val mControlPanelWindowManager = XposedHelpers.getObjectField(param.getThisObject(), "mControlPanelWindowManager")
                    if (param.getArgs().isEmpty()) {
                        param.returnAndSkip(false)
                        return
                    }
                    val dispatchToControlPanel = XposedHelpers.callMethod(mControlPanelWindowManager, "dispatchToControlPanel", param.getArg(0), bar.width.toFloat()) as Boolean
                    XposedHelpers.callMethod(mControlPanelWindowManager, "setTransToControlPanel", dispatchToControlPanel)
                    param.returnAndSkip(dispatchToControlPanel)
                    return
                }
                param.returnAndSkip(false)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.controlcenter.phone.ControlPanelWindowManager", lpparam.classLoader, "dispatchToControlPanel", MotionEvent::class.java, Float::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val added = XposedHelpers.getBooleanField(param.getThisObject(), "added")
                if (added) {
                    val controlCenterController = XposedHelpers.getObjectField(param.getThisObject(), "controlCenterController")
                    val useCC = XposedHelpers.getBooleanField(controlCenterController, "useControlCenter")
                    if (useCC) {
                        if (param.getArgs().size < 2) {
                            param.returnAndSkip(false)
                            return
                        }
                        val motionEvent = param.getArg(0) as? MotionEvent ?: return
                        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                            XposedHelpers.setFloatField(param.getThisObject(), "downX", motionEvent.rawX)
                        }
                        val controlCenterWindowView = XposedHelpers.getObjectField(param.getThisObject(), "windowView")
                        if (controlCenterWindowView == null) {
                            param.returnAndSkip(false)
                        } else {
                            val mDownX = XposedHelpers.getFloatField(param.getThisObject(), "downX")
                            val width = param.getArg(1) as? Float ?: return
                            if (mDownX < width / 2.0f) {
                                param.returnAndSkip(XposedHelpers.callMethod(controlCenterWindowView, "handleMotionEvent", motionEvent, true))
                            } else {
                                param.returnAndSkip(false)
                            }
                        }
                        return
                    }
                }
                param.returnAndSkip(false)
            }
        })
    }

}
