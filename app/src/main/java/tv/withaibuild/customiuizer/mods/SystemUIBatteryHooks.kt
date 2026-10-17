package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.System
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.battery.BatteryStyleEffect
import tv.withaibuild.customiuizer.mods.battery.BatteryStyleResolver
import tv.withaibuild.customiuizer.utils.BatteryIndicator

@Suppress("MemberVisibilityCanBePrivate")
object SystemUIBatteryHooks {
    private const val StatusBarCls = "com.android.systemui.statusbar.phone.CentralSurfacesImpl"

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
    )

    @Volatile
    internal var batteryStyle: BatteryStyle? = null

    /**
     * Immutable fallback effect for direct helper/test calls that do not supply an effect.
     * It is never mutated after initialization and is not used by the production hook callback.
     */
    private val FALLBACK_BATTERY_STYLE_EFFECT = BatteryStyleEffect(null)

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

    private fun setTextSizeIfChanged(view: TextView, sizeDp: Float) {
        val target = sizeDp * view.resources.displayMetrics.density
        if (view.textSize != target) view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp)
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
            override fun after(param: AfterHookCallback) {
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                val sbWindowController = XposedHelpers.getObjectField(param.getThisObject(), "mStatusBarWindowController")
                val mStatusBarWindow = XposedHelpers.getObjectField(sbWindowController, "mStatusBarWindowView") as ViewGroup

                val indicator = BatteryIndicator(mContext)
                mStatusBarWindow.addView(indicator)
                indicator.setAdjustViewBounds(false)
                indicator.init(param.getThisObject())
                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator", indicator)
                val mNotificationIconAreaController = XposedHelpers.getObjectField(param.getThisObject(), "mNotificationIconAreaController")
                XposedHelpers.setAdditionalInstanceField(mNotificationIconAreaController, "mBatteryIndicator", indicator)
                val mBatteryController = XposedHelpers.getObjectField(param.getThisObject(), "mBatteryController")
                XposedHelpers.setAdditionalInstanceField(mBatteryController, "mBatteryIndicator", indicator)
                XposedHelpers.callMethod(mBatteryController, "fireBatteryLevelChanged")
                XposedHelpers.callMethod(mBatteryController, "firePowerSaveChanged")
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "updatePanelExpanded", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mPanelExpanded = XposedHelpers.getBooleanField(param.getThisObject(), "mPanelExpanded")
                val isKeyguardShowing = XposedHelpers.callMethod(param.getThisObject(), "isKeyguardShowing") as Boolean
                val mStatusBar = XposedHelpers.getObjectField(param.getThisObject(), "mCentralSurfaces")
                val indicator = XposedHelpers.getAdditionalInstanceField(mStatusBar, "mBatteryIndicator") as BatteryIndicator?
                indicator?.onExpandingChanged(!isKeyguardShowing && mPanelExpanded)
            }
        })

        ModuleHelper.findAndHookMethod(StatusBarCls, lpparam.classLoader, "updateIsKeyguard", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val isKeyguardShowing = XposedHelpers.callMethod(param.getThisObject(), "isKeyguardShowing") as Boolean
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onKeyguardStateChanged(isKeyguardShowing)
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.NotificationIconAreaController", lpparam.classLoader, "onDarkChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onDarkModeChanged(param.getArgs()[1] as Float, param.getArgs()[2] as Int)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.MiuiBatteryControllerImpl", lpparam.classLoader, "fireBatteryLevelChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                val mLevel = XposedHelpers.getIntField(param.getThisObject(), "mLevel")
                val mCharging = XposedHelpers.getBooleanField(param.getThisObject(), "mCharging")
                val mCharged = XposedHelpers.getBooleanField(param.getThisObject(), "mCharged")
                indicator?.onBatteryLevelChanged(mLevel, mCharging, mCharged)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.BatteryControllerImpl", lpparam.classLoader, "firePowerSaveChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val indicator = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "mBatteryIndicator") as BatteryIndicator?
                indicator?.onPowerSaveChanged(XposedHelpers.getBooleanField(param.getThisObject(), "mPowerSave"))
            }
        })
    }

    @JvmStatic
    fun StatusBarStyleBatteryIconHook(lpparam: PackageReadyParam) {
        installBatteryStyleSnapshot()
        val abi = BatteryStyleResolver.resolve(lpparam.classLoader)
        val effect = BatteryStyleEffect(abi)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiBatteryMeterView", lpparam.classLoader, "updateAll", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val style = batteryStyle ?: return
                val owner = param.getThisObject() ?: return
                val batteryView = owner as? ViewGroup ?: return
                val state = getOrCreateBatteryViewState(owner)
                reconcileBatteryView(batteryView, style, state, effect)
            }
        })
    }

    internal fun reconcileBatteryView(
        parent: ViewGroup,
        style: BatteryStyle,
        state: BatteryViewState,
        effect: BatteryStyleEffect = FALLBACK_BATTERY_STYLE_EFFECT,
    ) {
        val baseline = state.baseline

        val childrenChanged = baseline == null || childIdentitiesChanged(parent, baseline.childIds)
        val newBaseline = when {
            baseline == null -> captureBatteryBaseline(parent, effect)
            childrenChanged -> captureBatteryBaseline(parent, effect)
            state.appliedStyle == null && !matchesBaseline(parent, baseline, effect) -> captureBatteryBaseline(parent, effect)
            else -> baseline
        }
        if (newBaseline == null) return
        state.baseline = newBaseline

        val defaultStyle = isBatteryStyleDefault(style)
        when {
            defaultStyle -> {
                if (state.appliedStyle != null || !matchesBaseline(parent, newBaseline, effect)) {
                    restoreBatteryBaseline(parent, newBaseline, effect)
                }
                state.appliedStyle = null
            }
            state.appliedStyle != style || !matchesTarget(parent, newBaseline, style, effect) -> {
                applyBatteryStyle(parent, newBaseline, style, effect)
                state.appliedStyle = style
            }
        }
    }

    private fun isBatteryStyleDefault(style: BatteryStyle): Boolean {
        return !style.swap &&
            style.fontSizeDp == 7.5f &&
            style.markFontSizeDp == 7.5f &&
            !style.bold &&
            style.leftMarginDp == 0f &&
            style.rightMarginDp == 0f &&
            style.verticalOffset == 8 &&
            style.markVerticalOffset == 17
    }

    internal data class Padding(
        val start: Int,
        val top: Int,
        val end: Int,
        val bottom: Int
    )

    internal data class BatteryBaseline(
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

    internal data class BatteryViewState(
        var baseline: BatteryBaseline? = null,
        var appliedStyle: BatteryStyle? = null
    )

    internal fun getOrCreateBatteryViewState(owner: Any): BatteryViewState {
        var state = XposedHelpers.getAdditionalInstanceField(owner, "customiuizer_battery_view_state") as? BatteryViewState
        if (state == null) {
            state = BatteryViewState()
            XposedHelpers.setAdditionalInstanceField(owner, "customiuizer_battery_view_state", state)
        }
        return state
    }

    internal fun childIdentitiesChanged(parent: ViewGroup, childIds: List<Int>): Boolean {
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

    internal fun captureBatteryBaseline(
        owner: ViewGroup,
        effect: BatteryStyleEffect = FALLBACK_BATTERY_STYLE_EFFECT,
    ): BatteryBaseline? {
        val useFast = effect.useFastPath(owner)
        val digitView = effect.readDigitView(owner, useFast) ?: return null
        val percentView = effect.readPercentView(owner, useFast) ?: return null
        val markView = effect.readMarkView(owner, useFast) ?: return null
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

    internal fun matchesBaseline(
        parent: ViewGroup,
        baseline: BatteryBaseline,
        effect: BatteryStyleEffect = FALLBACK_BATTERY_STYLE_EFFECT,
    ): Boolean {
        val useFast = effect.useFastPath(parent)
        val digitView = effect.readDigitView(parent, useFast) ?: return false
        val percentView = effect.readPercentView(parent, useFast) ?: return false
        val markView = effect.readMarkView(parent, useFast) ?: return false
        return parent.indexOfChild(percentView) == baseline.percentIndex &&
            parent.indexOfChild(markView) == baseline.markIndex &&
            digitView.textSize == baseline.digitTextSize &&
            percentView.textSize == baseline.percentTextSize &&
            markView.textSize == baseline.markTextSize &&
            digitView.typeface === baseline.digitTypeface &&
            percentView.typeface === baseline.percentTypeface &&
            paddingEquals(percentView, baseline.percentPadding) &&
            paddingEquals(markView, baseline.markPadding)
    }

    private fun paddingEquals(view: TextView, padding: Padding): Boolean {
        return view.paddingStart == padding.start &&
            view.paddingTop == padding.top &&
            view.paddingEnd == padding.end &&
            view.paddingBottom == padding.bottom
    }

    internal fun matchesTarget(
        parent: ViewGroup,
        baseline: BatteryBaseline,
        style: BatteryStyle,
        effect: BatteryStyleEffect = FALLBACK_BATTERY_STYLE_EFFECT,
    ): Boolean {
        val useFast = effect.useFastPath(parent)
        val digitView = effect.readDigitView(parent, useFast) ?: return false
        val percentView = effect.readPercentView(parent, useFast) ?: return false
        val markView = effect.readMarkView(parent, useFast) ?: return false
        val metrics = parent.resources.displayMetrics

        val percentIndex = parent.indexOfChild(percentView)
        val markIndex = parent.indexOfChild(markView)
        if (style.swap) {
            if (percentIndex != 0 || markIndex != 1) return false
        } else {
            if (percentIndex != baseline.percentIndex || markIndex != baseline.markIndex) return false
        }

        val targetDigitSize = expectedTextSize(digitView, style.fontSizeDp, baseline.digitTextSize)
        val targetPercentSize = expectedTextSize(percentView, style.fontSizeDp, baseline.percentTextSize)
        val targetMarkSize = expectedTextSize(markView, style.markFontSizeDp, baseline.markTextSize)
        if (digitView.textSize != targetDigitSize) return false
        if (percentView.textSize != targetPercentSize) return false
        if (markView.textSize != targetMarkSize) return false

        val targetDigitTypeface = if (style.bold) Typeface.DEFAULT_BOLD else baseline.digitTypeface
        val targetPercentTypeface = if (style.bold) Typeface.DEFAULT_BOLD else baseline.percentTypeface
        if (digitView.typeface !== targetDigitTypeface) return false
        if (percentView.typeface !== targetPercentTypeface) return false

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
            if (!paddingEquals(percentView, baseline.percentPadding)) return false
        } else {
            if (percentView.paddingStart != leftMargin ||
                percentView.paddingTop != topMargin ||
                percentView.paddingEnd != rightMarginOnPercent ||
                percentView.paddingBottom != 0
            ) {
                return false
            }
        }

        if (useBaselineMark) {
            if (!paddingEquals(markView, baseline.markPadding)) return false
        } else {
            if (markView.paddingStart != 0 ||
                markView.paddingTop != markTopMargin ||
                markView.paddingEnd != rightMarginOnMark ||
                markView.paddingBottom != 0
            ) {
                return false
            }
        }

        return true
    }

    internal fun expectedTextSize(view: TextView, sizeDp: Float, baselineSize: Float): Float {
        return if (sizeDp == 7.5f) baselineSize else sizeDp * view.resources.displayMetrics.density
    }

    private fun dipToPx(dp: Float, metrics: android.util.DisplayMetrics): Int {
        return (dp * metrics.density).toInt()
    }

    internal fun restoreBatteryBaseline(
        parent: ViewGroup,
        baseline: BatteryBaseline,
        effect: BatteryStyleEffect = FALLBACK_BATTERY_STYLE_EFFECT,
    ) {
        val useFast = effect.useFastPath(parent)
        val digitView = effect.readDigitView(parent, useFast) ?: return
        val percentView = effect.readPercentView(parent, useFast) ?: return
        val markView = effect.readMarkView(parent, useFast) ?: return

        restoreChildOrder(parent, percentView, markView, baseline.percentIndex, baseline.markIndex)

        setTextSizePxIfChanged(digitView, baseline.digitTextSize)
        setTextSizePxIfChanged(percentView, baseline.percentTextSize)
        setTextSizePxIfChanged(markView, baseline.markTextSize)

        setTypefaceIfChanged(digitView, baseline.digitTypeface)
        setTypefaceIfChanged(percentView, baseline.percentTypeface)

        setPaddingRelativeIfChanged(percentView, baseline.percentPadding)
        setPaddingRelativeIfChanged(markView, baseline.markPadding)
    }

    internal fun applyBatteryStyle(
        parent: ViewGroup,
        baseline: BatteryBaseline,
        style: BatteryStyle,
        effect: BatteryStyleEffect = FALLBACK_BATTERY_STYLE_EFFECT,
    ) {
        val useFast = effect.useFastPath(parent)
        val digitView = effect.readDigitView(parent, useFast) ?: return
        val percentView = effect.readPercentView(parent, useFast) ?: return
        val markView = effect.readMarkView(parent, useFast) ?: return
        val metrics = parent.resources.displayMetrics

        if (style.swap) {
            applyBatteryChildSwapIfNeeded(parent, percentView, markView)
        } else {
            restoreChildOrder(parent, percentView, markView, baseline.percentIndex, baseline.markIndex)
        }

        if (style.fontSizeDp == 7.5f) {
            setTextSizePxIfChanged(digitView, baseline.digitTextSize)
            setTextSizePxIfChanged(percentView, baseline.percentTextSize)
        } else {
            setTextSizeIfChanged(digitView, style.fontSizeDp)
            setTextSizeIfChanged(percentView, style.fontSizeDp)
        }

        if (style.markFontSizeDp == 7.5f) {
            setTextSizePxIfChanged(markView, baseline.markTextSize)
        } else {
            setTextSizeIfChanged(markView, style.markFontSizeDp)
        }

        if (style.bold) {
            setTypefaceIfChanged(digitView, Typeface.DEFAULT_BOLD)
            setTypefaceIfChanged(percentView, Typeface.DEFAULT_BOLD)
        } else {
            setTypefaceIfChanged(digitView, baseline.digitTypeface)
            setTypefaceIfChanged(percentView, baseline.percentTypeface)
        }

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
