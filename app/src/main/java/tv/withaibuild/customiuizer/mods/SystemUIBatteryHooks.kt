package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.BatteryIndicator
import java.lang.System
import java.lang.reflect.Field

@Suppress("MemberVisibilityCanBePrivate")
object SystemUIBatteryHooks {
    private const val StatusBarCls = "com.android.systemui.statusbar.phone.CentralSurfacesImpl"

    private const val BATTERY_BASELINE_FIELD = "customiuizer_battery_view_state"

    private const val PREF_SWAP = "system_statusbaricons_swap_batteryicon_percentage"
    private const val PREF_FONT_SIZE = "system_statusbar_batterystyle_fontsize"
    private const val PREF_MARK_FONT_SIZE = "system_statusbar_batterystyle_mark_fontsize"
    private const val PREF_BOLD = "system_statusbar_batterystyle_bold"
    private const val PREF_LEFT_MARGIN = "system_statusbar_batterystyle_leftmargin"
    private const val PREF_RIGHT_MARGIN = "system_statusbar_batterystyle_rightmargin"
    private const val PREF_VERTICAL_OFFSET = "system_statusbar_batterystyle_verticaloffset"
    private const val PREF_MARK_VERTICAL_OFFSET = "system_statusbar_batterystyle_mark_verticaloffset"
    private const val PREF_BATTERY4 = "system_statusbaricons_battery4"

    private val BATTERY_STYLE_KEYS = setOf(
        PREF_SWAP,
        PREF_FONT_SIZE,
        PREF_MARK_FONT_SIZE,
        PREF_BOLD,
        PREF_LEFT_MARGIN,
        PREF_RIGHT_MARGIN,
        PREF_VERTICAL_OFFSET,
        PREF_MARK_VERTICAL_OFFSET,
        PREF_BATTERY4,
    )

    /**
     * Immutable battery style state.
     *
     * `MiuiBatteryMeterView.updateAll` runs on every battery, dark mode and configuration
     * update, so it reads this snapshot instead of nine preference lookups and only writes a
     * view property when the current value actually differs.
     */
    internal class BatteryStyle(
        val swap: Boolean,
        val fontSizeDp: Float,
        val markFontSizeDp: Float,
        val bold: Boolean,
        val leftMarginDp: Float,
        val rightMarginDp: Float,
        val verticalOffset: Int,
        val markVerticalOffset: Int,
        val battery4: Boolean,
    ) {
        val customized = swap || fontSizeDp != 7.5f || markFontSizeDp != 7.5f || bold ||
            leftMarginDp != 0f || rightMarginDp != 0f || verticalOffset != 8 || markVerticalOffset != 17
    }

    @Volatile
    internal var batteryStyle: BatteryStyle? = null

    @Volatile
    private var batteryStyleObserverRegistered = false

    internal fun readBatteryStyle(): BatteryStyle {
        val prefs = MainModule.mPrefs
        return BatteryStyle(
            swap = prefs.getBoolean(PREF_SWAP),
            fontSizeDp = prefs.getInt(PREF_FONT_SIZE, 15) * 0.5f,
            markFontSizeDp = prefs.getInt(PREF_MARK_FONT_SIZE, 15) * 0.5f,
            bold = prefs.getBoolean(PREF_BOLD),
            leftMarginDp = prefs.getInt(PREF_LEFT_MARGIN, 0) * 0.5f,
            rightMarginDp = prefs.getInt(PREF_RIGHT_MARGIN, 0) * 0.5f,
            verticalOffset = prefs.getInt(PREF_VERTICAL_OFFSET, 8),
            markVerticalOffset = prefs.getInt(PREF_MARK_VERTICAL_OFFSET, 17),
            battery4 = prefs.getBoolean(PREF_BATTERY4),
        )
    }

    @JvmStatic
    internal fun installBatteryStyleSnapshot() {
        batteryStyle = readBatteryStyle()
        if (batteryStyleObserverRegistered) return
        batteryStyleObserverRegistered = true
        ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) = ModuleHelper.guarded {
                if (key == null || key in BATTERY_STYLE_KEYS) {
                    batteryStyle = readBatteryStyle()
                }
            }
        })
    }

    /**
     * Reorders the battery percentage and mark views to [percent, mark] at the front of
     * [parent]. The operation is idempotent: when the views are already in the target order,
     * neither removeView nor addView is called, so repeated updateAll invocations are no-ops.
     */
    @JvmStatic
    internal fun applyBatteryChildSwapIfNeeded(parent: ViewGroup, percentView: View, markView: View) {
        moveChildTo(parent, percentView, 0)
        moveChildTo(parent, markView, 1)
    }

    /** Moves [view] to [index] only when it is not already there, avoiding a needless relayout. */
    @JvmStatic
    internal fun moveChildTo(parent: ViewGroup, view: View, index: Int) {
        if (parent.indexOfChild(view) == index) return
        parent.removeView(view)
        parent.addView(view, index)
    }

    private fun setPaddingRelativeIfChanged(view: TextView, start: Int, top: Int, end: Int, bottom: Int) {
        if (view.paddingStart == start && view.paddingTop == top &&
            view.paddingEnd == end && view.paddingBottom == bottom
        ) {
            return
        }
        view.setPaddingRelative(start, top, end, bottom)
    }

    @JvmStatic
    fun BatteryIndicatorHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod(StatusBarCls, lpparam.classLoader, "start", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val mContext = XposedHelpers.getObjectField(callback.getThisObject(), "mContext") as Context
                val sbWindowController = XposedHelpers.getObjectField(callback.getThisObject(), "mStatusBarWindowController")
                val mStatusBarWindow = XposedHelpers.getObjectField(sbWindowController, "mStatusBarWindowView") as ViewGroup

                val indicator = BatteryIndicator(mContext)
                mStatusBarWindow.addView(indicator)
                indicator.setAdjustViewBounds(false)
                indicator.init(callback.getThisObject())
                XposedHelpers.setAdditionalInstanceField(callback.getThisObject(), "mBatteryIndicator", indicator)
                val mNotificationIconAreaController = XposedHelpers.getObjectField(callback.getThisObject(), "mNotificationIconAreaController")
                XposedHelpers.setAdditionalInstanceField(mNotificationIconAreaController, "mBatteryIndicator", indicator)
                val mBatteryController = XposedHelpers.getObjectField(callback.getThisObject(), "mBatteryController")
                XposedHelpers.setAdditionalInstanceField(mBatteryController, "mBatteryIndicator", indicator)
                XposedHelpers.callMethod(mBatteryController, "fireBatteryLevelChanged")
                XposedHelpers.callMethod(mBatteryController, "firePowerSaveChanged")
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "updatePanelExpanded", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val mPanelExpanded = XposedHelpers.getBooleanField(callback.getThisObject(), "mPanelExpanded")
                val isKeyguardShowing = XposedHelpers.callMethod(callback.getThisObject(), "isKeyguardShowing") as Boolean
                val mStatusBar = XposedHelpers.getObjectField(callback.getThisObject(), "mCentralSurfaces")
                val indicator = XposedHelpers.getAdditionalInstanceField(mStatusBar, "mBatteryIndicator") as BatteryIndicator?
                indicator?.onExpandingChanged(!isKeyguardShowing && mPanelExpanded)
            }
        })

        ModuleHelper.findAndHookMethod(StatusBarCls, lpparam.classLoader, "updateIsKeyguard", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val isKeyguardShowing = XposedHelpers.callMethod(callback.getThisObject(), "isKeyguardShowing") as Boolean
                val indicator = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onKeyguardStateChanged(isKeyguardShowing)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.NotificationIconAreaController", lpparam.classLoader, "onDarkChanged",
            ArrayList::class.java, Float::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onDarkModeChanged(callback.getArg(1) as Float, callback.getArg(2) as Int)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.MiuiBatteryControllerImpl", lpparam.classLoader, "fireBatteryLevelChanged", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                val mLevel = XposedHelpers.getIntField(callback.getThisObject(), "mLevel")
                val mCharging = XposedHelpers.getBooleanField(callback.getThisObject(), "mCharging")
                val mCharged = XposedHelpers.getBooleanField(callback.getThisObject(), "mCharged")
                indicator?.onBatteryLevelChanged(mLevel, mCharging, mCharged)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BatteryControllerImpl", lpparam.classLoader, "firePowerSaveChanged", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(callback.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onPowerSaveChanged(XposedHelpers.getBooleanField(callback.getThisObject(), "mPowerSave"))
            }
        })
    }

    @JvmStatic
    fun StatusBarStyleBatteryIconHook(lpparam: PackageReadyParam) {
        val ownerClass = XposedHelpers.findClass(
            "com.android.systemui.statusbar.views.MiuiBatteryMeterView", lpparam.classLoader,
        )
        val fields = BatteryMeterFields.resolve(ownerClass)
        checkNotNull(ModuleHelper.findAndHookMethod(ownerClass, "updateAll", object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() != null) return
                val style = batteryStyle ?: return
                val owner = callback.getThisObject() as ViewGroup
                reconcileBatteryView(owner, style, fields)
            }
        }))
        installBatteryStyleSnapshot()
    }

    internal class BatteryMeterFields private constructor(
        val digit: Field,
        val percent: Field,
        val mark: Field,
    ) {
        companion object {
            fun resolve(owner: Class<*>): BatteryMeterFields = BatteryMeterFields(
                owner.getDeclaredField("mBatteryTextDigitView").apply { isAccessible = true },
                owner.getDeclaredField("mBatteryPercentView").apply { isAccessible = true },
                owner.getDeclaredField("mBatteryPercentMarkView").apply { isAccessible = true },
            )
        }
    }

    internal fun reconcileBatteryView(parent: ViewGroup, style: BatteryStyle, fields: BatteryMeterFields) {
        val previous = XposedHelpers.getAdditionalInstanceField(parent, BATTERY_BASELINE_FIELD) as? BatteryBaseline
        if (previous == null && !style.customized) return

        val digit = fields.digit.get(parent) as? TextView ?: return
        val percent = fields.percent.get(parent) as? TextView ?: return
        val mark = fields.mark.get(parent) as? TextView ?: return
        val sameChildren = previous != null && !childIdentitiesChanged(parent, previous.childIds)

        if (!style.customized) {
            if (sameChildren) restoreBatteryBaseline(parent, digit, percent, mark, previous)
            XposedHelpers.removeAdditionalInstanceField(parent, BATTERY_BASELINE_FIELD)
            return
        }

        val baseline = if (sameChildren) previous else {
            captureBatteryBaseline(parent, digit, percent, mark).also {
                XposedHelpers.setAdditionalInstanceField(parent, BATTERY_BASELINE_FIELD, it)
            }
        }
        applyBatteryStyle(parent, digit, percent, mark, baseline, style)
    }

    private data class Padding(
        val start: Int,
        val top: Int,
        val end: Int,
        val bottom: Int
    )

    private class BatteryBaseline(
        val percentIndex: Int,
        val markIndex: Int,
        val digitTextSize: Float,
        val percentTextSize: Float,
        val markTextSize: Float,
        val digitTypeface: Typeface?,
        val percentTypeface: Typeface?,
        val percentPadding: Padding,
        val markPadding: Padding,
        val childIds: List<Int>
    )

    private fun childIdentitiesChanged(parent: ViewGroup, childIds: List<Int>): Boolean {
        if (parent.childCount != childIds.size) return true
        outer@ for (i in 0 until parent.childCount) {
            val currentId = System.identityHashCode(parent.getChildAt(i))
            for (baselineId in childIds) {
                if (currentId == baselineId) continue@outer
            }
            return true
        }
        return false
    }

    private fun captureBatteryBaseline(
        owner: ViewGroup,
        digitView: TextView,
        percentView: TextView,
        markView: TextView,
    ): BatteryBaseline {
        return BatteryBaseline(
            percentIndex = owner.indexOfChild(percentView),
            markIndex = owner.indexOfChild(markView),
            digitTextSize = digitView.textSize,
            percentTextSize = percentView.textSize,
            markTextSize = markView.textSize,
            digitTypeface = digitView.typeface,
            percentTypeface = percentView.typeface,
            percentPadding = Padding(percentView.paddingStart, percentView.paddingTop, percentView.paddingEnd, percentView.paddingBottom),
            markPadding = Padding(markView.paddingStart, markView.paddingTop, markView.paddingEnd, markView.paddingBottom),
            childIds = (0 until owner.childCount).map { System.identityHashCode(owner.getChildAt(it)) }
        )
    }

    private fun expectedTextSize(view: TextView, sizeDp: Float, baselineSize: Float): Float {
        return if (sizeDp == 7.5f) baselineSize else sizeDp * view.resources.displayMetrics.density
    }

    private fun dipToPx(dp: Float, metrics: android.util.DisplayMetrics): Int {
        return (dp * metrics.density).toInt()
    }

    private fun restoreBatteryBaseline(
        parent: ViewGroup,
        digitView: TextView,
        percentView: TextView,
        markView: TextView,
        baseline: BatteryBaseline,
    ) {
        restoreChildOrder(parent, percentView, markView, baseline.percentIndex, baseline.markIndex)

        setTextSizePxIfChanged(digitView, baseline.digitTextSize)
        setTextSizePxIfChanged(percentView, baseline.percentTextSize)
        setTextSizePxIfChanged(markView, baseline.markTextSize)

        setTypefaceIfChanged(digitView, baseline.digitTypeface)
        setTypefaceIfChanged(percentView, baseline.percentTypeface)

        setPaddingRelativeIfChanged(percentView, baseline.percentPadding)
        setPaddingRelativeIfChanged(markView, baseline.markPadding)
    }

    private fun applyBatteryStyle(
        parent: ViewGroup,
        digitView: TextView,
        percentView: TextView,
        markView: TextView,
        baseline: BatteryBaseline,
        style: BatteryStyle,
    ) {
        val metrics = parent.resources.displayMetrics

        if (style.swap) {
            applyBatteryChildSwapIfNeeded(parent, percentView, markView)
        } else {
            restoreChildOrder(parent, percentView, markView, baseline.percentIndex, baseline.markIndex)
        }

        setTextSizePxIfChanged(digitView, expectedTextSize(digitView, style.fontSizeDp, baseline.digitTextSize))
        setTextSizePxIfChanged(percentView, expectedTextSize(percentView, style.fontSizeDp, baseline.percentTextSize))
        setTextSizePxIfChanged(markView, expectedTextSize(markView, style.markFontSizeDp, baseline.markTextSize))
        setTypefaceIfChanged(digitView, if (style.bold) Typeface.DEFAULT_BOLD else baseline.digitTypeface)
        setTypefaceIfChanged(percentView, if (style.bold) Typeface.DEFAULT_BOLD else baseline.percentTypeface)

        val leftMargin = dipToPx(style.leftMarginDp, metrics)
        val topMargin = if (style.verticalOffset == 8) 0 else dipToPx((style.verticalOffset - 8) * 0.5f, metrics)
        val rightMargin = dipToPx(style.rightMarginDp, metrics)
        val rightMarginOnPercent = if (style.battery4) rightMargin else 0
        val rightMarginOnMark = if (style.battery4) 0 else rightMargin
        val markTopMargin = if (style.markVerticalOffset == 17 && style.verticalOffset == 8) {
            topMargin
        } else {
            dipToPx((style.markVerticalOffset - 8) * 0.5f, metrics)
        }

        val useBaselinePercent = style.leftMarginDp == 0f && style.rightMarginDp == 0f && style.verticalOffset == 8
        val useBaselineMark = style.rightMarginDp == 0f &&
            ((style.markVerticalOffset == 17 && style.verticalOffset == 8) ||
                (style.markVerticalOffset == style.verticalOffset && style.markVerticalOffset == 17))

        if (useBaselinePercent) {
            setPaddingRelativeIfChanged(percentView, baseline.percentPadding)
        } else {
            setPaddingRelativeIfChanged(percentView, leftMargin, topMargin, rightMarginOnPercent, 0)
        }

        if (useBaselineMark) {
            setPaddingRelativeIfChanged(markView, baseline.markPadding)
        } else {
            setPaddingRelativeIfChanged(markView, 0, markTopMargin, rightMarginOnMark, 0)
        }
    }

    private fun restoreChildOrder(parent: ViewGroup, percentView: View, markView: View, percentIndex: Int, markIndex: Int) {
        // Move the child with the larger target index first so the smaller target index remains
        // valid after the first move.
        if (markIndex > percentIndex) {
            moveChildTo(parent, markView, markIndex)
            moveChildTo(parent, percentView, percentIndex)
        } else {
            moveChildTo(parent, percentView, percentIndex)
            moveChildTo(parent, markView, markIndex)
        }
    }

    private fun setTextSizePxIfChanged(view: TextView, size: Float) {
        if (view.textSize != size) view.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    private fun setTypefaceIfChanged(view: TextView, typeface: Typeface?) {
        if (view.typeface !== typeface) view.typeface = typeface
    }

    private fun setPaddingRelativeIfChanged(view: TextView, padding: Padding) {
        setPaddingRelativeIfChanged(view, padding.start, padding.top, padding.end, padding.bottom)
    }
}
