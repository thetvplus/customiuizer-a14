package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Pair
import android.util.SparseIntArray
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import miui.os.Build
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.CustomTextIconTintRoute
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.statusbariconvisibility.StatusBarIconVisibilityEffect
import tv.withaibuild.customiuizer.mods.statusbariconvisibility.StatusBarIconVisibilityResolver
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.HookInstallStateMachine
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.OwnedRegistrations
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.mods.utils.StatusBarDisplayRegistry
import tv.withaibuild.customiuizer.mods.utils.StatusBarDisplayState
import tv.withaibuild.customiuizer.mods.utils.StatusBarNetworkSpeedDispatcher
import tv.withaibuild.customiuizer.mods.utils.releaseRegistrationSilently
import tv.withaibuild.customiuizer.mods.utils.StepCounterController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.Helpers
import tv.withaibuild.customiuizer.utils.HookUtils
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.net.NetworkInterface
import java.util.ArrayList
import java.util.HashSet
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Status bar content hooks.
 * Owns the custom text-icon registry that DeviceInfoMonitor writes battery and
 * temperature readings into, plus the signal, network speed, clock position and
 * icon visibility hooks that share it.
 */
internal fun resolveNetSpeedLineSpacing(
    fontSize: Int,
    adjustmentPercent: Int
): Float {
    val baseSpacing =
        if (fontSize > 17) 0.85f
        else 0.90f

    val adjustment = adjustmentPercent.coerceIn(70, 130)

    return baseSpacing * adjustment / 100f
}

internal fun resolveDigitalSignalCustomTextSizeDp(rawValue: Int): Float? =
    rawValue.takeIf { it > 0 }?.coerceIn(14, 40)?.times(0.5f)

internal fun resolveDigitalSignalLineSpacing(textSizeDp: Float): Float =
    if (textSizeDp > 8.5f) 0.85f else 0.9f

internal fun resolveNetSpeedTypefaceStyle(baseStyle: Int, bold: Boolean): Int =
    if (bold) baseStyle or Typeface.BOLD else baseStyle and Typeface.BOLD.inv()

internal fun formatNetSpeedValue(value: Float): String {
    if (value >= 100.0f) return Math.round(value).toString()

    val tenths = Math.round(value * 10.0f)
    return "${tenths / 10}.${tenths % 10}"
}

/**
 * Immutable snapshot of the network-speed text-style configuration.
 *
 * Stores logical (dp/sp/enum) values, not physical px, so density, fontScale and configuration
 * changes do not require snapshot reconstruction; the hot path converts to px on demand using the
 * current View resources.  The [id] is a per-process monotonic value used by each NetworkSpeedView
 * to skip redundant style setters when the same snapshot is applied repeatedly.
 */
internal data class NetSpeedTextStyleSnapshot(
    val id: Long,
    val speedStyle: Int,
    val bold: Boolean,
    val fontSize: Int,
    val fixedWidth: Int,
    val leftMargin: Int,
    val rightMargin: Int,
    val verticalOffset: Int,
    val align: Int,
    val adjustment: Int,
)

/**
 * Immutable snapshot of the detailed network-speed text-format configuration.
 *
 * Contains only the logical format flags used by [DetailedNetSpeedHook.updateText]; the module
 * resource unit strings are read from the provided [Resources] each tick because they may depend
 * on the current configuration. The snapshot itself holds no View, Context or controller.
 */
internal data class DetailedNetSpeedFormatSnapshot(
    val id: Long,
    val hideLow: Boolean,
    val lowLevelBytes: Int,
    val speedStyle: Int,
    val icons: Int,
    val hideSecUnit: Boolean,
)

/**
 * Immutable snapshot of the status-bar icon visibility hide configuration.
 *
 * Contains one [Boolean] per `system_statusbaricons_*` key that is read in the three
 * hide-icon hot paths (`checkSlot`, `HideIconsSignalHook`, `HideIconsFromSystemManager`).
 * It holds no View, Context, Resources or controller.
 */
internal data class StatusBarIconVisibilitySnapshot(
    val id: Long,
    val hideHeadset: Boolean,
    val hideSound: Boolean,
    val hideDnd: Boolean,
    val hideAlarm: Boolean,
    val hideProfile: Boolean,
    val hideVpn: Boolean,
    val hideAirplane: Boolean,
    val hideNfc: Boolean,
    val hideSecondSpace: Boolean,
    val hideGps: Boolean,
    val hideWifi: Boolean,
    val hideHotspot: Boolean,
    val hideNoSims: Boolean,
    val hideBtBattery: Boolean,
    val hideBleUnlock: Boolean,
    val hideBluetoothIcn: Boolean,
    val hideVolte: Boolean,
    val hideSignal: Boolean,
    val hideSignalWifiConnected: Boolean,
    val hideSim1: Boolean,
    val hideSim2: Boolean,
    val hideSimNoData: Boolean,
    val hideRoaming: Boolean,
    val hidePrivacy: Boolean,
    val hideMute: Boolean,
    val hideSpeaker: Boolean,
    val hideRecord: Boolean,
    val hideWirelessHeadset: Boolean,
)

/** Converts [dp] to physical pixels using the current [Resources] display metrics. */
private fun Resources.dp2px(dp: Float): Float =
    dp * getDisplayMetrics().density

object SystemUIStatusBarHooks {

    private val StatusBarCls = "com.android.systemui.statusbar.phone.CentralSurfacesImpl"

    val textIconTagId = ResourceHooks.getFakeResId("text_icon_tag")

    private val viewInitedTag = ResourceHooks.getFakeResId("view_inited_tag")

    /** Additional instance field key for the last full [NetSpeedTextStyleSnapshot] id applied to a view. */
    private const val NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID = "netspeed_last_full_style_snapshot_id"

    /** Nullable B1/B2 runtime-state holder. Only created when a net-speed feature is installed. */
    private var netSpeedRuntimeState: NetSpeedRuntimeState? = null

    /** Nullable B3 icon-visibility runtime-state holder. Only created when a B3 feature is installed. */
    private var iconVisibilityRuntimeState: StatusBarIconVisibilityRuntimeState? = null

    /** Runtime state for the B1 network-speed text-style feature. */
    private class NetSpeedStyleRuntimeState {
        /** Process-scoped, atomically published snapshot for the network-speed text-style hot path. */
        val currentSnapshot = AtomicReference<NetSpeedTextStyleSnapshot?>(null)

        /** Monotonic id generator for [NetSpeedTextStyleSnapshot]. */
        val idGenerator = AtomicLong(0L)

        /** Keys whose changes require the network-speed style snapshot to be rebuilt. */
        val relevantKeys = setOf(
            "system_detailednetspeed_style",
            "system_netspeed_boldfont",
            "system_netspeed_fontsize",
            "system_netspeed_fixedcontent_width",
            "system_netspeed_leftmargin",
            "system_netspeed_rightmargin",
            "system_netspeed_verticaloffset",
            "system_detailednetspeed_align",
            "system_netspeed_rowspacing",
        )

        /** Fake-resource tag IDs used only by B1. Generated once when B1 is installed. */
        val numberViewTag = ResourceHooks.getFakeResId("netspeed_number_view")
        val unitViewTag = ResourceHooks.getFakeResId("netspeed_unit_view")
        val typefaceStateTag = ResourceHooks.getFakeResId("netspeed_typeface_state")
        val originalStyleStateTag = ResourceHooks.getFakeResId("netspeed_original_style_state")
    }

    /** Runtime state for the B2 detailed network-speed text-format feature. */
    private class DetailedNetSpeedRuntimeState {
        /** Process-scoped, atomically published snapshot for the detailed network-speed text hot path. */
        val currentSnapshot = AtomicReference<DetailedNetSpeedFormatSnapshot?>(null)

        /** Monotonic id generator for [DetailedNetSpeedFormatSnapshot]. */
        val idGenerator = AtomicLong(0L)

        /** Keys whose changes require the detailed network-speed format snapshot to be rebuilt. */
        val relevantKeys = setOf(
            "system_detailednetspeed_low",
            "system_detailednetspeed_lowlevel",
            "system_detailednetspeed_style",
            "system_detailednetspeed_icon",
            "system_detailednetspeed_secunit",
        )
    }

    /** Combined B1/B2 runtime state holding a single shared preference observer. */
    private class NetSpeedRuntimeState {
        /** B1 text-style substate, present only when [NetSpeedStyleHook] is installed. */
        var styleState: NetSpeedStyleRuntimeState? = null

        /** B2 detailed-format substate, present only when [DetailedNetSpeedHook] is installed. */
        var detailedState: DetailedNetSpeedRuntimeState? = null

        /** Shared preference observer for B1/B2. Only active while the holder exists. */
        val observer = object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) {
                if (key != null) {
                    styleState?.takeIf { key in it.relevantKeys }?.let {
                        it.currentSnapshot.set(buildNetSpeedTextStyleSnapshot(MainModule.mPrefs, it.idGenerator))
                    }
                    detailedState?.takeIf { key in it.relevantKeys }?.let {
                        it.currentSnapshot.set(null)
                    }
                } else {
                    styleState?.let { it.currentSnapshot.set(buildNetSpeedTextStyleSnapshot(MainModule.mPrefs, it.idGenerator)) }
                    detailedState?.currentSnapshot?.set(null)
                }
            }
        }
    }

    /** Runtime state for B3 status-bar icon-visibility features. */
    private class StatusBarIconVisibilityRuntimeState {
        /** Process-scoped, atomically published snapshot for the status-bar icon visibility hot path. */
        val currentSnapshot = AtomicReference<StatusBarIconVisibilitySnapshot?>(null)

        /** Monotonic id generator for [StatusBarIconVisibilitySnapshot]. */
        val idGenerator = AtomicLong(0L)

        /** Keys whose changes require the status-bar icon visibility snapshot to be rebuilt. */
        val relevantKeys = setOf(
            "system_statusbaricons_headset",
            "system_statusbaricons_sound",
            "system_statusbaricons_dnd",
            "system_statusbaricons_alarm",
            "system_statusbaricons_profile",
            "system_statusbaricons_vpn",
            "system_statusbaricons_airplane",
            "system_statusbaricons_nfc",
            "system_statusbaricons_secondspace",
            "system_statusbaricons_gps",
            "system_statusbaricons_wifi",
            "system_statusbaricons_hotspot",
            "system_statusbaricons_nosims",
            "system_statusbaricons_btbattery",
            "system_statusbaricons_ble_unlock",
            "system_statusbaricons_bluetoothicn",
            "system_statusbaricons_volte",
            "system_statusbaricons_signal",
            "system_statusbaricons_signal_wificonnected",
            "system_statusbaricons_sim1",
            "system_statusbaricons_sim2",
            "system_statusbaricons_sim_nodata",
            "system_statusbaricons_roaming",
            "system_statusbaricons_privacy",
            "system_statusbaricons_mute",
            "system_statusbaricons_speaker",
            "system_statusbaricons_record",
            "system_statusbaricons_wireless_headset",
        )

        /** Preference observer that rebuilds the snapshot when a B3 icon-visibility key changes. */
        val observer = object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) {
                val state = this@StatusBarIconVisibilityRuntimeState
                if (key != null && key !in state.relevantKeys) return
                val built = buildStatusBarIconVisibilitySnapshot(MainModule.mPrefs, state.idGenerator)
                state.currentSnapshot.set(built)
            }
        }
    }

    /** Ensures the B1/B2 runtime-state holder exists, creating it and registering a shared observer on first use. */
    private fun ensureNetSpeedRuntimeState(): NetSpeedRuntimeState {
        return netSpeedRuntimeState ?: NetSpeedRuntimeState().also { created ->
            netSpeedRuntimeState = created
            ModuleHelper.observePreferenceChange(created.observer, SystemUIStatusBarHooks)
        }
    }

    /** Ensures the B1 text-style substate exists. */
    private fun NetSpeedRuntimeState.ensureStyleState(): NetSpeedStyleRuntimeState {
        return styleState ?: NetSpeedStyleRuntimeState().also { styleState = it }
    }

    /** Ensures the B2 detailed-format substate exists. */
    private fun NetSpeedRuntimeState.ensureDetailedState(): DetailedNetSpeedRuntimeState {
        return detailedState ?: DetailedNetSpeedRuntimeState().also { detailedState = it }
    }

    /** Ensures the B3 runtime-state holder exists, creating it and registering its observer on first use. */
    private fun ensureStatusBarIconVisibilityRuntimeState(): StatusBarIconVisibilityRuntimeState {
        return iconVisibilityRuntimeState ?: StatusBarIconVisibilityRuntimeState().also { created ->
            iconVisibilityRuntimeState = created
            ModuleHelper.observePreferenceChange(created.observer, created)
        }
    }

    @JvmStatic
    fun getSlotNameByType(mIconType: Int): String {
        var slotName = ""
        if (mIconType == 91) {
            slotName = "battery_info"
        } else if (mIconType == 92) {
            slotName = "device_temp"
        }
        return slotName
    }

    @JvmStatic
    fun MonitorDeviceInfoHook(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        SystemUIMonitorAndTileHooks.MonitorDeviceInfoHook(lpparam, mPrefs)
    }

    private fun getIconTextView(iconView: View): TextView? {
        return try {
            XposedHelpers.getObjectField(iconView, "mNetworkSpeedNumberText") as? TextView
                ?: iconView.findViewWithTag<View>("network_speed_number") as? TextView
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            iconView.findViewWithTag<View>("network_speed_number") as? TextView
        }
    }

    @JvmStatic
    fun initStatusbarTextIcon(mContext: Context, iconType: Int, iconView: View, fromController: Boolean) {
        if (!fromController) {
            try {
                XposedHelpers.callMethod(iconView, "setBlocked", false)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
        }
        val iconTextView = getIconTextView(iconView)
        if (iconTextView == null) {
            XposedHelpers.log("SystemUIStatusBarHooks: no number TextView for text icon $iconType; skipping style init")
            return
        }
        val res = mContext.resources
        val styleId = res.getIdentifier("TextAppearance.StatusBar.Clock", "style", "com.android.systemui")
        iconTextView.setTextAppearance(styleId)
        var subKey = ""
        if (iconType == 91) {
            subKey = "batterytempandcurrent"
        } else if (iconType == 92) {
            subKey = "showdevicetemperature"
        }
        val fontSize = MainModule.mPrefs.getInt("system_statusbar_${subKey}_fontsize", 16) * 0.5f
        val opt = MainModule.mPrefs.getStringAsInt("system_statusbar_${subKey}_content", 1)
        if ((opt == 1 || opt == 4 || opt == 5) && !MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_singlerow")) {
            iconTextView.maxLines = 2
            iconTextView.setLineSpacing(0f, if (fontSize > 8.5f) 0.85f else 0.9f)
        }
        iconTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize)
        if (MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_bold")) {
            iconTextView.typeface = Typeface.DEFAULT_BOLD
        }
        var leftMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_leftmargin", 8)
        leftMargin = HookUtils.dp2px(leftMargin * 0.5f).toInt()
        var rightMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_rightmargin", 8)
        rightMargin = HookUtils.dp2px(rightMargin * 0.5f).toInt()
        var topMargin = 0
        val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_${subKey}_verticaloffset", 8)
        if (verticalOffset != 8) {
            topMargin = HookUtils.dp2px((verticalOffset - 8) * 0.5f).toInt()
        }
        iconTextView.setPaddingRelative(leftMargin, topMargin, rightMargin, 0)
        val fixedWidth = MainModule.mPrefs.getInt("system_statusbar_${subKey}_fixedcontent_width", 10)
        if (fixedWidth > 10) {
            val lp = iconTextView.layoutParams as LinearLayout.LayoutParams
            lp.width = HookUtils.dp2px(fixedWidth.toFloat()).toInt()
            iconTextView.layoutParams = lp
        }

        val align = MainModule.mPrefs.getStringAsInt("system_statusbar_${subKey}_align", 1)
        if (align == 2) {
            iconTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        } else if (align == 3) {
            iconTextView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        } else if (align == 4) {
            iconTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }
    }

    @JvmStatic
    fun createStatusbarTextIcon(mContext: Context, lp: LinearLayout.LayoutParams, iconType: Int, fromController: Boolean): View {
        val iconView = try {
            LayoutInflater.from(mContext).inflate(tv.withaibuild.customiuizer.mods.utils.statusbarTextIconLayoutResId, null)
                ?: throw IllegalStateException("LayoutInflater returned null for statusbar_text_icon")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            throw IllegalStateException("Failed to inflate statusbar_text_icon for type $iconType", t)
        }
        iconView.setTag(textIconTagId, iconType)
        iconView.layoutParams = lp
        val mNumber = iconView.findViewWithTag<View>("network_speed_number")
        val mUnit = iconView.findViewWithTag<View>("network_speed_unit")
        if (mNumber != null) {
            try {
                XposedHelpers.setObjectField(iconView, "mNetworkSpeedNumberText", mNumber)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
        }
        if (mUnit != null) {
            try {
                XposedHelpers.setObjectField(iconView, "mNetworkSpeedUnitText", mUnit)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
            }
        }
        initStatusbarTextIcon(mContext, iconType, iconView, fromController)
        return iconView
    }

    /**
     * Battery detail / device temperature text icons created by this module inside SystemUI.
     *
     * SystemUI re-inflates the status bar on theme, density, display and fold changes, so a strong
     * static list keeps every dead View (and its Context) alive for the whole process lifetime and
     * makes the 2 s monitor tick walk detached views. References are weak and dead entries are
     * dropped on every register/update. All access happens on the SystemUI main thread.
     */
    private val statusbarTextIcons = ArrayList<WeakReference<View>>(4)

    /**
     * DarkIconDispatcher receivers and StatusBarIconController icon groups registered by this
     * module, keyed by the MiuiPhoneStatusBarView generation they belong to.
     *
     * The weak [statusbarTextIcons] registry alone is not enough: addDarkReceiver makes the
     * dispatcher singleton hold a strong reference to every module View, so a dead status bar
     * generation is never collected and keeps receiving dark callbacks. Registrations from a
     * previous generation are removed (removeDarkReceiver / removeIconGroup) whenever a newer
     * generation appears. All access is on the SystemUI main thread.
     */
    /**
     * Per-display status bar state. SystemUI may host multiple status bars (fold cover,
     * external display, etc.); each display has its own generation, second row and
     * registration list so a new generation on display 0 cannot clean a live status bar
     * on display 1.
     *
     * The registry is given a scheduler that posts a single pending-prune runnable to the
     * SystemUI main looper. This bounds the cleanup of never-bound pending owners and avoids
     * a permanent background thread.
     */
    private lateinit var statusBarDisplayRegistry: StatusBarDisplayRegistry<View, LinearLayout>

    private val statusBarMainHandler = Handler(Looper.getMainLooper())
    private var statusBarPendingPruneScheduled = false
    private val statusBarPendingPruneRunnable = Runnable {
        statusBarPendingPruneScheduled = false
        statusBarDisplayRegistry.prune()
    }

    init {
        statusBarDisplayRegistry = StatusBarDisplayRegistry(
            onPendingChanged = { hasPending ->
                if (hasPending) {
                    if (!statusBarPendingPruneScheduled) {
                        statusBarPendingPruneScheduled = true
                        statusBarMainHandler.postDelayed(statusBarPendingPruneRunnable, STATUS_BAR_PENDING_PRUNE_DELAY_MS)
                    }
                } else {
                    statusBarMainHandler.removeCallbacks(statusBarPendingPruneRunnable)
                    statusBarPendingPruneScheduled = false
                }
            }
        )
    }

    /** Process-level once-guard for the status bar view onDetachedFromWindow hook. */
    private val statusBarViewDetachHookInstaller = HookInstallStateMachine()

    /** Tag on the module-inflated second-row network speed view, used to find it again per generation. */
    private const val NETSPEED_ROW2_TAG = "customiuizer_netspeed_row2"

    /** Process-level once-guarded hook installer for the network speed second row. */
    private val netSpeedSecondRowHookInstaller = HookInstallStateMachine()

    /** ClassLoader captured when the network speed hook is installed; used by the callback. */
    private var netSpeedSecondRowClassLoader: ClassLoader? = null

    /**
     * Resolve the display id for a status bar view. [View.getDisplay] is preferred; before
     * attach it may be null, in which case we fall back to the view's context. A null result
     * means the view is not yet associated with a display and should use the temporary
     * identity-scoped pending bucket.
     */
    private fun resolveDisplayId(view: View): Int? {
        val display = view.display
        if (display != null) return display.displayId
        return view.context.display?.displayId
    }

    private const val STATUS_BAR_PENDING_PRUNE_DELAY_MS = 250L

    private val netSpeedMainHandler = Handler(Looper.getMainLooper())
    private val netSpeedSequence = AtomicLong(0)
    private val netSpeedLastAppliedSequence = AtomicLong(0)

    private fun installNetSpeedSecondRowHook(lpparam: PackageReadyParam) {
        netSpeedSecondRowClassLoader = lpparam.classLoader
        netSpeedSecondRowHookInstaller.install {
            ModuleHelper.hookAllMethodsSilently(
                "com.android.systemui.statusbar.phone.StatusBarIconControllerImpl",
                lpparam.classLoader,
                "setNetworkSpeedIcon",
                netSpeedSecondRowHookCallback,
            )
        }
    }

    /**
     * Install a process-level once-guarded hook on [MiuiPhoneStatusBarView.onDetachedFromWindow].
     *
     * When a status bar view is detached, release the exact owner from the per-display registry
     * and run a prune. This is the primary lifecycle boundary for displays that never re-attach
     * (for example on display removal or process recreation).
     */
    private fun installStatusBarViewLifecycleHook(lpparam: PackageReadyParam) {
        statusBarViewDetachHookInstaller.install {
            ModuleHelper.findAndHookMethod(
                "com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView",
                lpparam.classLoader,
                "onDetachedFromWindow",
                object : MethodHook() {
                    override fun after(param: AfterHookCallback) {
                        val sbView = param.getThisObject() as View
                        statusBarMainHandler.post {
                            statusBarDisplayRegistry.detach(sbView)
                            statusBarDisplayRegistry.prune()
                        }
                    }
                },
            )
            true
        }
    }

    /**
     * Shared hook callback for the network speed second row.
     *
     * The hook may run on the NetworkSpeedController background handler. It therefore does not
     * read the registry or touch any View directly. It only copies the payload, assigns a
     * monotonic sequence, and posts a single runnable to the main looper. The posted runnable
     * captures only the immutable payload and the sequence; it does not capture any View, owner
     * or registry state.
     */
    private val netSpeedSecondRowHookCallback = object : MethodHook() {
        override fun after(param: AfterHookCallback) {
            val networkSpeedState = param.getArgs()[0]
            val number = XposedHelpers.getObjectField(networkSpeedState, "networkSpeedNumber")
            val unit = XposedHelpers.getObjectField(networkSpeedState, "networkSpeedUnit")
            val visible = XposedHelpers.getObjectField(networkSpeedState, "visible")

            val payload = StatusBarNetworkSpeedDispatcher.NetworkSpeedPayload(number, unit, visible)
            val seq = netSpeedSequence.incrementAndGet()
            netSpeedMainHandler.post {
                StatusBarNetworkSpeedDispatcher.dispatch(
                    payload,
                    seq,
                    netSpeedLastAppliedSequence,
                    statusBarDisplayRegistry,
                    ::applyNetworkSpeedToRow,
                )
            }
        }
    }

    private fun applyNetworkSpeedToRow(
        state: StatusBarDisplayState<View, LinearLayout>,
        payload: StatusBarNetworkSpeedDispatcher.NetworkSpeedPayload,
    ) {
        val row = state.secondRow?.get() ?: return
        val owner = state.generation?.get() ?: return

        if (!row.isAttachedToWindow) return
        if (state.generation?.get() !== owner) return
        if (state.secondRow?.get() !== row) return

        var networkSpeedView: View? = row.findViewWithTag(NETSPEED_ROW2_TAG)
        if (networkSpeedView == null) {
            val ctx = row.context
            val layoutResId = ctx.resources.getIdentifier("network_speed", "layout", "com.android.systemui")
            if (layoutResId == 0) return
            val created = LayoutInflater.from(ctx).inflate(layoutResId, null) ?: return
            created.tag = NETSPEED_ROW2_TAG
            row.addView(created, 0, LinearLayout.LayoutParams(-2, -2))

            val classLoader = netSpeedSecondRowClassLoader ?: return
            val DarkIconDispatcher = ModuleHelper.getDepInstance(classLoader, "com.android.systemui.plugins.DarkIconDispatcher")
            if (DarkIconDispatcher == null) return

            val added = try {
                XposedHelpers.callMethod(DarkIconDispatcher, "addDarkReceiver", created)
                true
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                false
            }
            if (added) {
                state.registrations.register(owner) {
                    releaseRegistrationSilently(DarkIconDispatcher, "removeDarkReceiver", created, "network-speed-row2")
                }
            }
            networkSpeedView = created
        }

        XposedHelpers.callMethod(networkSpeedView, "setBlocked", false)
        XposedHelpers.callMethod(networkSpeedView, "setNetworkSpeed", payload.number, payload.unit)
        XposedHelpers.callMethod(networkSpeedView, "setVisibilityByController", payload.visible)
    }

    @JvmStatic
    fun registerStatusbarTextIcon(iconView: View) {
        for (i in statusbarTextIcons.indices.reversed()) {
            val existing = statusbarTextIcons[i].get()
            if (existing == null || existing === iconView) statusbarTextIcons.removeAt(i)
        }
        statusbarTextIcons.add(WeakReference(iconView))
    }

    @JvmStatic
    fun updateStatusbarTextIcons(iconType: Int, show: Boolean, text: String) {
        for (i in statusbarTextIcons.indices.reversed()) {
            val iconView = statusbarTextIcons[i].get()
            if (iconView == null || !iconView.isAttachedToWindow) {
                statusbarTextIcons.removeAt(i)
                continue
            }
            if (iconView.getTag(textIconTagId) != iconType) continue
            try {
                XposedHelpers.callMethod(iconView, "setVisibilityByController", show)
                if (show) XposedHelpers.callMethod(iconView, "setNetworkSpeed", text, "")
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                // If the custom NetworkSpeedView does not have the expected methods, fall back to
                // a plain View visibility/text update so the icon degrades rather than crashing.
                try {
                    iconView.visibility = if (show) View.VISIBLE else View.GONE
                    val numberView = iconView.findViewWithTag<View>("network_speed_number")
                    if (show && numberView is TextView) {
                        numberView.text = text
                    }
                } catch (t2: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t2)
                }
                XposedHelpers.log("updateStatusbarTextIcons fallback for $iconType: ${t.javaClass.simpleName}")
            }
        }
    }

    @JvmStatic
    fun DualRowsStatusbarHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val sbView = param.getThisObject() as FrameLayout
                if (XposedHelpers.getAdditionalInstanceField(sbView, "dualRowsLayoutAdded") != null) return
                // A new inflation is a generation boundary for this display. Resolve the display
                // now if possible; otherwise the view stays in the identity-scoped pending bucket
                // until onAttachedToWindow provides a real displayId.
                val displayId = resolveDisplayId(sbView)
                val state = if (displayId != null) {
                    statusBarDisplayRegistry.bind(sbView, displayId)
                } else {
                    statusBarDisplayRegistry.getOrCreatePending(sbView)
                }
                var firstRowLeftPadding = 0
                var firstRowRightPadding = 0
                if (MainModule.mPrefs.getBoolean("system_statusbar_dualrows_firstrow_horizmargin")) {
                    firstRowLeftPadding = MainModule.mPrefs.getInt("system_statusbar_dualrows_firstrow_horizmargin_left", 0)
                    firstRowRightPadding = MainModule.mPrefs.getInt("system_statusbar_dualrows_firstrow_horizmargin_right", 0)
                }
                val clock2Rows = MainModule.mPrefs.getBoolean("system_statusbar_dualrows_clock_span2rows")
                val mContext = sbView.context
                val leftContainer = XposedHelpers.getObjectField(sbView, "mStatusBarLeftContainer") as LinearLayout
                leftContainer.setTag("mStatusBarLeftContainer")
                val statusBarcontents = leftContainer.parent as LinearLayout
                val leftLayout = LinearLayout(mContext)
                val rightLayout = LinearLayout(mContext)
                statusBarcontents.addView(leftLayout, 0)
                statusBarcontents.addView(rightLayout)
                val leftGroup: LinearLayout

                if (clock2Rows) {
                    val mMiuiClock = XposedHelpers.getObjectField(sbView, "mClock") as TextView
                    leftContainer.removeView(mMiuiClock)
                    leftGroup = LinearLayout(mContext)
                    leftLayout.addView(mMiuiClock)
                    leftLayout.addView(leftGroup)
                    leftLayout.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    val groupLp = LinearLayout.LayoutParams(0, -1, 1f)
                    leftGroup.layoutParams = groupLp
                } else {
                    leftGroup = leftLayout
                    if (firstRowLeftPadding > 0) {
                        leftContainer.setPaddingRelative(firstRowLeftPadding, 0, 0, 0)
                    }
                }
                statusBarcontents.removeView(leftContainer)
                leftGroup.addView(leftContainer)
                val secondLeft = LinearLayout(mContext)
                leftGroup.addView(secondLeft)
                leftLayout.id = leftContainer.id
                leftContainer.id = View.NO_ID
                XposedHelpers.setObjectField(sbView, "mStatusBarLeftContainer", leftLayout)

                val rightContainer = XposedHelpers.getObjectField(param.getThisObject(), "mSystemIconArea") as ViewGroup
                val mFullscreenStatusBarNotificationIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mFullscreenStatusBarNotificationIconArea") as View
                rightContainer.removeView(mFullscreenStatusBarNotificationIconArea)
                secondLeft.addView(mFullscreenStatusBarNotificationIconArea)
                val mDripStatusBarNotificationIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mDripStatusBarNotificationIconArea") as View
                leftContainer.removeView(mDripStatusBarNotificationIconArea)
                secondLeft.addView(mDripStatusBarNotificationIconArea)
                secondLeft.orientation = LinearLayout.VERTICAL
                val leftLp = LinearLayout.LayoutParams(-1, 0, 1f)
                leftContainer.layoutParams = leftLp
                secondLeft.layoutParams = leftLp
                secondLeft.gravity = Gravity.START or Gravity.CENTER_VERTICAL

                XposedHelpers.setObjectField(param.getThisObject(), "mSystemIconArea", rightLayout)
                val firstRight = LinearLayout(mContext)
                rightLayout.addView(firstRight)
                firstRight.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                if (firstRowRightPadding > 0) {
                    firstRight.setPaddingRelative(0, 0, firstRowRightPadding, 0)
                }
                val secondRight = LinearLayout(mContext)
                rightLayout.addView(secondRight)
                secondRight.gravity = Gravity.END or Gravity.CENTER_VERTICAL

                rightLayout.orientation = LinearLayout.VERTICAL
                val rightLp = LinearLayout.LayoutParams(-1, 0, 1f)
                firstRight.layoutParams = rightLp
                secondRight.layoutParams = rightLp

                val rightChildCount = rightContainer.childCount
                for (i in rightChildCount - 1 downTo 0) {
                    val child = rightContainer.getChildAt(i)
                    rightContainer.removeView(child)
                    firstRight.addView(child, 0)
                }

                val resSystemIconsId = sbView.resources.getIdentifier("system_icons", "id", lpparam.packageName)
                rightLayout.id = resSystemIconsId

                val showBatteryDetail = MainModule.mPrefs.getBoolean("system_statusbar_batterytempandcurrent")
                val showDeviceTemp = MainModule.mPrefs.getBoolean("system_statusbar_showdevicetemperature")
                val batteryAtRight = showBatteryDetail && MainModule.mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
                val tempAtRight = showDeviceTemp && MainModule.mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")
                val customIconTypes = ArrayList<Int>()
                if (batteryAtRight) {
                    customIconTypes.add(91)
                }
                if (tempAtRight) {
                    customIconTypes.add(92)
                }
                if (!customIconTypes.isEmpty()) {
                    for (iconType in customIconTypes) {
                        val iconView = createStatusbarTextIcon(mContext, LinearLayout.LayoutParams(-2, -2), iconType, false)
                        secondRight.addView(iconView, 0)
                        registerStatusbarTextIcon(iconView)
                        val handle = CustomTextIconTintRoute.register(iconView, lpparam.classLoader, "right")
                        state.registrations.register(sbView) {
                            handle.release("generation-replaced")
                        }
                    }
                }

                statusBarcontents.removeView(rightContainer)

                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "leftLayout", leftLayout)
                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "rightLayout", rightLayout)
                XposedHelpers.setAdditionalInstanceField(param.getThisObject(), "dualRowsLayoutAdded", true)

                if (MainModule.mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")) {
                    // The second row for this display is where the network speed view lives.
                    // The hook itself is installed at most once per process via a state machine.
                    state.secondRow = WeakReference(secondRight)
                    installNetSpeedSecondRowHook(lpparam)
                }
            }
        })

        installStatusBarViewLifecycleHook(lpparam)

        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "updateCutoutLocation", object : MethodHook(-1000) {
            override fun after(param: AfterHookCallback) {
                val mCurrentStatusBarType = XposedHelpers.getObjectField(param.getThisObject(), "mCurrentStatusBarType") as Int
                val leftLayout = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "leftLayout") as LinearLayout?
                val rightLayout = XposedHelpers.getAdditionalInstanceField(param.getThisObject(), "rightLayout") as LinearLayout?

                if (leftLayout != null && rightLayout != null) {
                    if (mCurrentStatusBarType == 0) {
                        val leftWidth = MainModule.mPrefs.getInt("system_statusbar_dualrows_left_ratio", 4)
                        val leftLayoutLp = LinearLayout.LayoutParams(0, -1, leftWidth.toFloat())
                        leftLayout.layoutParams = leftLayoutLp
                        val rightLayoutLp = LinearLayout.LayoutParams(0, -1, (10 - leftWidth).toFloat())
                        rightLayout.layoutParams = rightLayoutLp
                    } else {
                        val leftLayoutLp = LinearLayout.LayoutParams(0, -1, 1f)
                        leftLayout.layoutParams = leftLayoutLp
                        val rightLayoutLp = LinearLayout.LayoutParams(0, -1, 1f)
                        rightLayout.layoutParams = rightLayoutLp
                    }
                }
            }
        })
    }

    private fun initDigitalSignalView(mContext: Context, digitalTextView: TextView) {
        val res = mContext.resources
        val styleId = res.getIdentifier("TextAppearance.StatusBar.Clock", "style", "com.android.systemui")
        if (styleId != 0) digitalTextView.setTextAppearance(styleId)
        val subKey = "mobile_digital_signal"
        val customTextSizeDp = resolveDigitalSignalCustomTextSizeDp(
            MainModule.mPrefs.getInt("system_statusbar_${subKey}_fontsize", 0)
        )
        if (customTextSizeDp != null) {
            digitalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, customTextSizeDp)
        }
        val dualRows = MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_in2rows")
        digitalTextView.includeFontPadding = false
        digitalTextView.gravity = Gravity.CENTER_VERTICAL
        digitalTextView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER_VERTICAL
        )
        if (dualRows) {
            digitalTextView.maxLines = 2
            val textSizeDp = digitalTextView.textSize / res.displayMetrics.density
            digitalTextView.setLineSpacing(0f, resolveDigitalSignalLineSpacing(textSizeDp))
            val minTextSizePx = HookUtils.dp2px(6f).roundToInt().coerceAtLeast(1)
            val maxTextSizePx = digitalTextView.textSize.roundToInt().coerceAtLeast(minTextSizePx)
            digitalTextView.setAutoSizeTextTypeUniformWithConfiguration(
                minTextSizePx,
                maxTextSizePx,
                1,
                TypedValue.COMPLEX_UNIT_PX
            )
        } else {
            digitalTextView.maxLines = 1
        }
        if (MainModule.mPrefs.getBoolean("system_statusbar_${subKey}_bold")) {
            digitalTextView.typeface = Typeface.create(digitalTextView.typeface, Typeface.BOLD)
        }
        var leftMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_leftmargin", 8)
        leftMargin = HookUtils.dp2px(leftMargin * 0.5f).toInt()
        var rightMargin = MainModule.mPrefs.getInt("system_statusbar_${subKey}_rightmargin", 8)
        rightMargin = HookUtils.dp2px(rightMargin * 0.5f).toInt()
        var topMargin = 0
        val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_${subKey}_verticaloffset", 8)
        if (verticalOffset != 8) {
            topMargin = HookUtils.dp2px((verticalOffset - 8) * 0.5f).toInt()
        }
        digitalTextView.setPaddingRelative(leftMargin, topMargin, rightMargin, 0)
        val align = MainModule.mPrefs.getStringAsInt("system_statusbar_${subKey}_align", 1)
        if (align == 2) {
            digitalTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
        } else if (align == 3) {
            digitalTextView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        } else if (align == 4) {
            digitalTextView.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
        }
    }

    @JvmStatic
    fun StatusBarDigitalSignalHook(lpparam: PackageReadyParam) {
        val signalLevelMap = SparseIntArray()
        val MobileStatusTrackerClass = XposedHelpers.findClass("com.android.systemui.statusbar.mobile.MobileStatusTracker", lpparam.classLoader)
        val mCallback = XposedHelpers.findField(MobileStatusTrackerClass, "mCallback")
        ModuleHelper.findAndHookMethod(mCallback.type, "onMobileStatusChanged", Boolean::class.javaPrimitiveType!!, "com.android.systemui.statusbar.mobile.MobileStatusTracker\$MobileStatus", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mobileStatus = param.getArg(1)
                val mobileSignalController = XposedHelpers.getSurroundingThis(param.getThisObject())
                val subscriptionInfo = XposedHelpers.getObjectField(mobileSignalController, "mSubscriptionInfo") as SubscriptionInfo
                val sid = subscriptionInfo.subscriptionId
                val signalStrength = XposedHelpers.getObjectField(mobileStatus, "signalStrength")
                if (signalStrength != null) {
                    val dbm = XposedHelpers.callMethod(signalStrength, "getDbm") as Int
                    signalLevelMap.put(sid, dbm)
                }
            }
        })
        val stateUpdateHook = object : MethodHook() {
            private var initAction = false
            override fun before(param: BeforeHookCallback) {
                if (param.getMember().name == "updateState") {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }

            override fun after(param: AfterHookCallback) {
                val updateStateMethod = param.getMember().name == "updateState"
                val mMobile = XposedHelpers.getObjectField(param.getThisObject(), "mMobile") as View
                val signalImageContainer = mMobile.parent as FrameLayout
                if (initAction) {
                    val digitalView = TextView(signalImageContainer.context)
                    initDigitalSignalView(signalImageContainer.context, digitalView)
                    signalImageContainer.addView(digitalView)
                    digitalView.setTag("digitalSignalView")
                    mMobile.visibility = View.GONE
                }
                if (updateStateMethod || initAction) {
                    val mobileIconState = param.getArgs()[0]
                    val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                    if (!visible) return
                    val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                    if (airplane) return
                    val dualRows = MainModule.mPrefs.getBoolean("system_statusbar_mobile_digital_signal_in2rows")
                    val subId = XposedHelpers.getObjectField(mobileIconState, "subId") as Int
                    val digitalView = signalImageContainer.findViewWithTag<TextView>("digitalSignalView")
                    val hideUnit = MainModule.mPrefs.getBoolean("system_statusbar_mobile_digital_signal_hideunit")
                    if (dualRows) {
                        val slotId = SubscriptionManager.getSlotIndex(subId)
                        if (slotId == 0) {
                            val subSubId = SubscriptionManager.getSubscriptionId(1)
                            digitalView?.text = signalLevelMap.get(subId).toString() + (if (hideUnit) "" else "dBm") +
                                "\n" + signalLevelMap.get(subSubId).toString() + (if (hideUnit) "" else "dBm")
                        }
                    } else {
                        digitalView?.text = signalLevelMap.get(subId).toString() + (if (hideUnit) "" else "dBm")
                    }
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateUpdateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateUpdateHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyDarknessInternal", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                val digitalView = (param.getThisObject() as LinearLayout).findViewWithTag<TextView>("digitalSignalView")
                if (digitalView != null) {
                    digitalView.setTextColor(mMobileTypeSingle.currentTextColor)
                }
            }
        })
        val dualRows = MainModule.mPrefs.getBoolean("system_statusbar_mobile_digital_signal_in2rows")
        if (dualRows) {
            ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setMobileIcons", object : MethodHook() {
                private var isHooked = false
                override fun before(param: BeforeHookCallback) {
                    if (!isHooked) {
                        isHooked = true
                    }
                    val iconStates = param.getArgs()[1] as List<*>
                    if (iconStates.size == 2) {
                        val iconState0 = iconStates[0]
                        val iconState1 = iconStates[1]
                        val mainIconState: Any
                        val subIconState: Any
                        val subId = XposedHelpers.getObjectField(iconState0, "subId") as Int
                        val slotId = SubscriptionManager.getSlotIndex(subId)
                        if (slotId == 0) {
                            mainIconState = iconState0!!
                            subIconState = iconState1!!
                        } else {
                            mainIconState = iconState1!!
                            subIconState = iconState0!!
                        }
                        XposedHelpers.setObjectField(subIconState, "visible", false)
                        val subDataConnected = XposedHelpers.getObjectField(subIconState, "dataConnected") as Boolean
                        if (subDataConnected) {
                            for (field in MOBILE_STATE_SYNC_FIELDS) {
                                XposedHelpers.setObjectField(mainIconState, field, XposedHelpers.getObjectField(subIconState, field))
                            }
                        }
                        param.getArgs()[1] = iconStates
                    }
                }
            })
        }
    }

    private fun getSignalLevel(res: Resources, resId: Int, cache: SparseIntArray): Int {
        if (resId == 0) return 6
        val idx = cache.indexOfKey(resId)
        if (idx >= 0) return cache.valueAt(idx)
        var level = 6
        try {
            val name = res.getResourceName(resId)
            if (name != null && name.contains("signal")) {
                if (name.contains("null")) {
                    level = 6
                } else {
                    val i = name.lastIndexOf("signal_")
                    if (i != -1) {
                        var start = i + "signal_".length
                        var end = start
                        while (end < name.length && Character.isDigit(name[end])) end++
                        if (end > start) {
                            try {
                                level = name.substring(start, end).toInt()
                                if (level < 0 || level > 5) level = 6
                            } catch (ignore: NumberFormatException) {
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
        }
        cache.put(resId, level)
        return level
    }

    private val DUAL_SIGNAL_WHITE_TINT by lazy { ColorStateList.valueOf(Color.WHITE) }

    private val DUAL_SIGNAL_BLACK_TINT by lazy { ColorStateList.valueOf(Color.BLACK) }

    private val MOBILE_STATE_SYNC_FIELDS = arrayOf("showName", "activityIn", "activityOut", "dataConnected")

    private fun applyDualSignalDrawables(mobileView: Any?, mobileIconState: Any?, subLevel: Int, systemUIRes: Resources?, signalResToLevelMap: SparseIntArray, dualSignalResIds: Array<Array<IntArray>>, selectedIconStyle: String): Boolean {
        if (systemUIRes == null) return false
        val mainSignalResId = XposedHelpers.getIntField(mobileIconState, "strengthId")
        var mainLevel = getSignalLevel(systemUIRes, mainSignalResId, signalResToLevelMap)
        if (mainLevel == 6) mainLevel = 0
        var subLevelVar = subLevel
        if (subLevelVar == 6) subLevelVar = 0
        val mLight = XposedHelpers.getBooleanField(mobileView, "mLight")
        val mUseTint = XposedHelpers.getBooleanField(mobileView, "mUseTint")
        val mSmallRoaming = XposedHelpers.getObjectField(mobileView, "mSmallRoaming")
        val mMobile = XposedHelpers.getObjectField(mobileView, "mMobile")
        if (mMobile == null || mSmallRoaming == null) return false
        val colorModeIndex = if (mUseTint && selectedIconStyle != "theme") {
            2
        } else if (!mLight) {
            1
        } else {
            0
        }
        val sim1ResId = dualSignalResIds[0][mainLevel][colorModeIndex]
        val sim2ResId = dualSignalResIds[1][subLevelVar][colorModeIndex]
        if (sim1ResId == 0 || sim2ResId == 0) return false
        XposedHelpers.callMethod(mMobile, "setImageResource", sim1ResId)
        XposedHelpers.callMethod(mSmallRoaming, "setImageResource", sim2ResId)
        var tintList: ColorStateList? = null
        val mMobileRoaming = XposedHelpers.getObjectField(mobileView, "mMobileRoaming")
        if (mMobileRoaming != null) {
            tintList = XposedHelpers.callMethod(mMobileRoaming, "getImageTintList") as ColorStateList?
        }
        if (tintList == null) {
            tintList = if (mLight) DUAL_SIGNAL_WHITE_TINT else DUAL_SIGNAL_BLACK_TINT
        }
        XposedHelpers.callMethod(mMobile, "setImageTintList", tintList)
        XposedHelpers.callMethod(mSmallRoaming, "setImageTintList", tintList)
        return true
    }

    @JvmStatic
    fun DualRowSignalHook(lpparam: PackageReadyParam) {
        val mobileTypeSingle = MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single")
        if (!mobileTypeSingle) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_mobile_type_half_to_top_distance", 3)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_mobile_left_inout_over_strength", 0)
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "status_bar_mobile_type_middle_to_strength_start", -0.4f)
        }

        val colorModeList = arrayOf("", "dark", "tint")
        val dualSignalResIds = Array(2) { Array(6) { IntArray(colorModeList.size) } }
        val selectedIconStyle = MainModule.mPrefs.getString("system_statusbar_dualsimin2rows_style", "")

        ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIApplication", lpparam.classLoader, "onCreate", object : MethodHook() {
            private var isHooked = false
            override fun after(param: AfterHookCallback) {
                if (!isHooked) {
                    isHooked = true
                    val mContext = XposedHelpers.callMethod(param.getThisObject(), "getApplicationContext") as Context
                    val modRes = ModuleHelper.getModuleRes(mContext)
                    for (slotIndex in 0..1) {
                        val slot = slotIndex + 1
                        for (lvl in 0..5) {
                            for ((colorModeIndex, colorMode) in colorModeList.withIndex()) {
                                if (selectedIconStyle != "theme" || colorMode != "tint") {
                                    val dualIconResName = "statusbar_signal_${slot}_${lvl}" + (if (colorMode.isNotEmpty()) "_$colorMode" else "") + (if (selectedIconStyle.isNotEmpty()) "_$selectedIconStyle" else "")
                                    val iconResId = modRes.getIdentifier(dualIconResName, "drawable", Helpers.modulePkg)
                                    dualSignalResIds[slotIndex][lvl][colorModeIndex] =
                                        MainModule.resHooks.addFakeResource(dualIconResName, iconResId, "drawable")
                                }
                            }
                        }
                    }
                }
            }
        })

        val systemUIRes = arrayOfNulls<Resources>(1)
        val signalResToLevelMap = SparseIntArray()
        val signalStates = intArrayOf(-1, -1) // main-subId, sub-level
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setMobileIcons", object : MethodHook() {
            private var isHooked = false
            override fun before(param: BeforeHookCallback) {
                if (!isHooked) {
                    isHooked = true
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val res = mContext.resources
                    systemUIRes[0] = res
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_0", "drawable", lpparam.packageName), 0)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_1", "drawable", lpparam.packageName), 1)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_2", "drawable", lpparam.packageName), 2)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_3", "drawable", lpparam.packageName), 3)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_4", "drawable", lpparam.packageName), 4)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_5", "drawable", lpparam.packageName), 5)
                    signalResToLevelMap.put(res.getIdentifier("stat_sys_signal_null", "drawable", lpparam.packageName), 6)
                }
                val iconStates = param.getArgs()[1] as List<*>
                if (iconStates.size == 2) {
                    val mainIconState = iconStates[0]
                    val subIconState = iconStates[1]
                    XposedHelpers.setObjectField(subIconState, "visible", false)
                    val subSignalResId = XposedHelpers.getIntField(subIconState, "strengthId")
                    signalStates[0] = XposedHelpers.getIntField(mainIconState, "subId")
                    signalStates[1] = getSignalLevel(systemUIRes[0]!!, subSignalResId, signalResToLevelMap)
                    val subDataConnected = XposedHelpers.getObjectField(subIconState, "dataConnected") as Boolean
                    if (subDataConnected) {
                        for (field in MOBILE_STATE_SYNC_FIELDS) {
                            XposedHelpers.setObjectField(mainIconState, field, XposedHelpers.getObjectField(subIconState, field))
                        }
                    }
                    param.getArgs()[1] = iconStates
                }
            }
        })

        val stateUpdateHook = object : MethodHook() {
            private var initAction = false
            override fun before(param: BeforeHookCallback) {
                if (param.getMember().name == "updateState") {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }
            override fun after(param: AfterHookCallback) {
                val updateStateMethod = param.getMember().name == "updateState"
                if (updateStateMethod || initAction) {
                    val mobileIconState = param.getArgs()[0]
                    val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                    if (!visible) return
                    val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                    if (airplane) return
                    val subId = XposedHelpers.getIntField(mobileIconState, "subId")
                    if (signalStates[0] == -1 || subId != signalStates[0]) return
                    val mSmallHd = XposedHelpers.getObjectField(param.getThisObject(), "mSmallHd")
                    XposedHelpers.callMethod(mSmallHd, "setVisibility", View.GONE)
                    val mSmallRoaming = XposedHelpers.getObjectField(param.getThisObject(), "mSmallRoaming")
                    XposedHelpers.callMethod(mSmallRoaming, "setVisibility", View.VISIBLE)
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateUpdateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateUpdateHook)

        val resetImageDrawable = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mobileIconState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                if (mobileIconState == null) return
                val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                val subId = XposedHelpers.getIntField(mobileIconState, "subId")
                if (!visible || airplane || subId != signalStates[0]) return
                applyDualSignalDrawables(param.getThisObject(), mobileIconState, signalStates[1], systemUIRes[0], signalResToLevelMap, dualSignalResIds, selectedIconStyle)
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyDarknessInternal", resetImageDrawable)

        val onDarkChangedSetter = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mobileIconState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                if (mobileIconState == null) return
                val visible = XposedHelpers.getBooleanField(mobileIconState, "visible")
                val airplane = XposedHelpers.getBooleanField(mobileIconState, "airplane")
                val subId = XposedHelpers.getIntField(mobileIconState, "subId")
                if (!visible || airplane || subId != signalStates[0]) return
                applyDualSignalDrawables(param.getThisObject(), mobileIconState, signalStates[1], systemUIRes[0], signalResToLevelMap, dualSignalResIds, selectedIconStyle)
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "onDarkChanged", onDarkChangedSetter)

        val rightMargin = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_rightmargin", 0)
        val leftMargin = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_leftmargin", 0)
        val iconScale = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_scale", 10)
        val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_dualsimin2rows_verticaloffset", 8)
        if (rightMargin > 0 || leftMargin > 0 || iconScale != 10 || verticalOffset != 8) {
            val initHook = object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mobileView = param.getThisObject() as LinearLayout
                    val inited = mobileView.getTag(viewInitedTag)
                    if (inited == null) {
                        mobileView.setTag(viewInitedTag, true)
                    } else {
                        return
                    }
                    val rightSpacing = HookUtils.dp2px(rightMargin * 0.5f).toInt()
                    val leftSpacing = HookUtils.dp2px(leftMargin * 0.5f).toInt()
                    mobileView.setPadding(leftSpacing, 0, rightSpacing, 0)
                    val mMobile = XposedHelpers.getObjectField(param.getThisObject(), "mMobile") as View
                    if (verticalOffset != 8) {
                        val marginTop = HookUtils.dp2px((verticalOffset - 8) * 0.5f)
                        val mobileIcon = mMobile.parent as FrameLayout
                        mobileIcon.translationY = marginTop
                    }
                    if (iconScale != 10) {
                        val mSmallRoaming = XposedHelpers.getObjectField(param.getThisObject(), "mSmallRoaming") as View
                        val layoutParams = mMobile.layoutParams as FrameLayout.LayoutParams?
                            ?: FrameLayout.LayoutParams(-2, HookUtils.dp2px(2.0f * iconScale).toInt())
                        layoutParams.height = HookUtils.dp2px(2.0f * iconScale).toInt()
                        layoutParams.gravity = Gravity.CENTER
                        mMobile.layoutParams = layoutParams
                        mSmallRoaming.layoutParams = layoutParams
                    }
                }
            }
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "setDripEnd", Boolean::class.javaPrimitiveType!!, initHook)
        }
    }

    @JvmStatic
    fun StatusBarIconsPositionAdjustHook(lpparam: PackageReadyParam, moveLeft: Boolean) {
        val mPrefs = MainModule.mPrefs
        val dualRows = mPrefs.getBoolean("system_statusbar_dualrows")
        val swapWifiSignal = mPrefs.getBoolean("system_statusbaricons_swap_wifi_mobile")
        val moveSignalLeft = mPrefs.getBoolean("system_statusbaricons_wifi_mobile_atleft")
        val netspeedAtRow2 = dualRows && mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow")
        val showBatteryDetail = mPrefs.getBoolean("system_statusbar_batterytempandcurrent")
        val showDeviceTemp = mPrefs.getBoolean("system_statusbar_showdevicetemperature")
        val batteryAtRight = showBatteryDetail && !dualRows && mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtRight = showDeviceTemp && !dualRows && mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")
        val batteryAtLeft = showBatteryDetail && !mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright")
        val tempAtLeft = showDeviceTemp && !mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright")

        val leftIcons = HashSet<String>()
        if (!netspeedAtRow2 && mPrefs.getBoolean("system_statusbar_netspeed_atleft")) {
            leftIcons.add("network_speed")
        }
        if (mPrefs.getBoolean("system_statusbar_gps_atleft")) {
            leftIcons.add("location")
        }
        if (mPrefs.getBoolean("system_statusbar_alarm_atleft")) {
            leftIcons.add("alarm_clock")
        }
        if (mPrefs.getBoolean("system_statusbar_sound_atleft")) {
            leftIcons.add("volume")
        }
        if (mPrefs.getBoolean("system_statusbar_dnd_atleft")) {
            leftIcons.add("zen")
        }
        if (batteryAtLeft) {
            leftIcons.add("battery_info")
        }
        if (tempAtLeft) {
            leftIcons.add("device_temp")
        }

        val signalRelatedIcons: List<String>
        signalRelatedIcons = if (!swapWifiSignal) {
            listOf("no_sim", "hd", "mobile", "demo_mobile", "airplane", "hotspot", "wifi", "demo_wifi")
        } else {
            listOf("hotspot", "wifi", "demo_wifi", "no_sim", "hd", "mobile", "demo_mobile", "airplane")
        }

        val leftBlockList = ArrayList<String>()
        val keyguardRightBlockList = ArrayList<String>()

        ModuleHelper.findAndHookConstructor("com.android.systemui.statusbar.phone.StatusBarIconList", lpparam.classLoader, Array<String>::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val allStatusIcons = ArrayList((param.getArgs()[0] as Array<String>).toList())
                val MiuiIconManagerUtils = XposedHelpers.findClass("com.android.systemui.statusbar.phone.MiuiIconManagerUtils", lpparam.classLoader)
                val rightBlockList = ModuleHelper.getStaticObjectFieldSilently(MiuiIconManagerUtils, "RIGHT_BLOCK_LIST") as? ArrayList<String> ?: ArrayList()
                val customIcons = ArrayList<String>()
                if (batteryAtLeft || batteryAtRight) {
                    customIcons.add("battery_info")
                }
                if (tempAtLeft || tempAtRight) {
                    customIcons.add("device_temp")
                }
                if (!customIcons.isEmpty()) {
                    val netspeedIndex = allStatusIcons.indexOf("network_speed") + 1
                    allStatusIcons.addAll(netspeedIndex, customIcons)
                }
                if (netspeedAtRow2) {
                    rightBlockList.add("network_speed")
                }
                if (mPrefs.getBoolean("system_statusbar_alarm_atright")) {
                    rightBlockList.remove("alarm_clock")
                }
                if (mPrefs.getBoolean("system_statusbar_btbattery_atright")) {
                    rightBlockList.remove("bluetooth_handsfree_battery")
                }
                if (mPrefs.getBoolean("system_statusbar_nfc_atright")) {
                    rightBlockList.remove("nfc")
                }
                if (mPrefs.getBoolean("system_statusbar_headset_atright")) {
                    rightBlockList.remove("headset")
                }
                if (mPrefs.getBoolean("system_statusbar_vpn_atright")) {
                    rightBlockList.remove("vpn")
                }
                if (moveLeft) {
                    keyguardRightBlockList.addAll(rightBlockList)
                    for (slotName in allStatusIcons) {
                        if (leftIcons.contains(slotName)) {
                            rightBlockList.add(slotName)
                        } else {
                            leftBlockList.add(slotName)
                        }
                    }
                }
                XposedHelpers.setStaticObjectField(MiuiIconManagerUtils, "RIGHT_BLOCK_LIST", rightBlockList)
                if (swapWifiSignal) {
                    val realSignalIcons = ArrayList<String>()
                    for (slotName in signalRelatedIcons) {
                        if (allStatusIcons.contains(slotName)) {
                            realSignalIcons.add(slotName)
                        }
                    }
                    allStatusIcons.removeAll(signalRelatedIcons)
                    allStatusIcons.addAll(realSignalIcons)
                }
                if (!customIcons.isEmpty() || swapWifiSignal) {
                    param.getArgs()[0] = allStatusIcons.toTypedArray()
                }
            }
        })

        if (moveLeft) {
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onAttachedToWindow", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mStatusBar = param.getThisObject() as FrameLayout
                    // Attach of the (possibly new) status bar view is a generation boundary for
                    // its display. Bind the view to a per-display state and drop any stale
                    // registrations that belonged to an older generation on the same display.
                    val displayId = resolveDisplayId(mStatusBar)
                    val state = if (displayId != null) {
                        statusBarDisplayRegistry.bind(mStatusBar, displayId)
                    } else {
                        statusBarDisplayRegistry.getOrCreatePending(mStatusBar)
                    }
                    state.registrations.cleanupWhere { it !== mStatusBar }
                    statusBarDisplayRegistry.prune()

                    val iconController = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.StatusBarIconController") ?: return
                    val existingContainer = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconContainer") as? LinearLayout
                    val oldHandle = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle") as? OwnedRegistrations.RegistrationHandle

                    if (existingContainer != null && existingContainer.parent != null) {
                        // The container is still attached. Make sure a registration handle exists
                        // so the next re-attach can remove the group exactly once.
                        if (oldHandle == null) {
                            val staleManager = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconManager")
                            if (staleManager != null) {
                                val handle = state.registrations.register(mStatusBar) {
                                    releaseRegistrationSilently(iconController, "removeIconGroup", staleManager, "left-icon-group")
                                }
                                XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)
                            }
                        }
                        return
                    }

                    // The container was removed or this is a new generation. Remove the old group
                    // exactly once, either through the saved handle or a direct cleanup.
                    if (oldHandle != null) {
                        oldHandle.cleanupNow()
                    } else {
                        val staleManager = XposedHelpers.getAdditionalInstanceField(mStatusBar, "leftIconManager")
                        if (staleManager != null) {
                            releaseRegistrationSilently(iconController, "removeIconGroup", staleManager, "left-icon-group")
                        }
                    }
                    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconContainer", null)
                    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconManager", null)
                    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", null)

                    val IconsContainer = XposedHelpers.findClass("com.android.systemui.statusbar.views.MiuiStatusIconContainer", lpparam.classLoader)
                    val iconContainer = try {
                        XposedHelpers.newInstance(IconsContainer, mStatusBar.context) as LinearLayout
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        return
                    }
                    iconContainer.layoutDirection = View.LAYOUT_DIRECTION_RTL
                    iconContainer.setTag("leftIconsContainer")

                    val leftNotifyContainer = if (dualRows) null else XposedHelpers.getObjectField(mStatusBar, "mDripStatusBarNotificationIconArea") as View
                    val leftContainer: LinearLayout = if (dualRows) {
                        mStatusBar.findViewWithTag<View>("mStatusBarLeftContainer") as LinearLayout
                    } else {
                        leftNotifyContainer!!.parent as LinearLayout
                    }
                    if (dualRows) {
                        leftContainer.addView(iconContainer)
                    } else {
                        leftContainer.addView(iconContainer, leftContainer.indexOfChild(leftNotifyContainer))
                    }

                    val miuiIconManagerFactory = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.phone.MiuiIconManagerFactory") ?: return

                    val DarkIconManager = XposedHelpers.findClass("com.android.systemui.statusbar.phone.StatusBarIconController\$DarkIconManager", lpparam.classLoader)
                    val mDarkIconManager = try {
                        XposedHelpers.newInstance(DarkIconManager,
                            iconContainer,
                            XposedHelpers.getObjectField(miuiIconManagerFactory, "mStatusBarPipelineFlags"),
                            XposedHelpers.getObjectField(miuiIconManagerFactory, "mMobileContextProvider"),
                            XposedHelpers.getObjectField(miuiIconManagerFactory, "mDarkIconDispatcher")
                        )
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        return
                    }

                    val added = try {
                        XposedHelpers.callMethod(iconController, "addIconGroup", mDarkIconManager)
                        true
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        false
                    }
                    if (!added) return

                    try {
                        XposedHelpers.callMethod(iconContainer, "setIgnoredSlots", leftBlockList)
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                    }

                    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconContainer", iconContainer)
                    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconManager", mDarkIconManager)
                    val handle = state.registrations.register(mStatusBar) {
                        releaseRegistrationSilently(iconController, "removeIconGroup", mDarkIconManager, "left-icon-group")
                    }
                    XposedHelpers.setAdditionalInstanceField(mStatusBar, "leftIconRegistrationHandle", handle)
                }
            })

            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiCollapsedStatusBarFragment", lpparam.classLoader, "updateStatusBarVisibilities", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
                private var lastShowLeftIcons = -1
                override fun after(param: AfterHookCallback) {
                    val mLastIsFocusedNotifPromptViewShowing = XposedHelpers.getBooleanField(param.getThisObject(), "mLastIsFocusedNotifPromptViewShowing")
                    val mIsShowNotifPromptView = XposedHelpers.getBooleanField(param.getThisObject(), "mIsShowNotifPromptView")
                    val mLastModifiedVisibility = XposedHelpers.getObjectField(param.getThisObject(), "mLastModifiedVisibility")
                    val showSystemInfo = XposedHelpers.getBooleanField(mLastModifiedVisibility, "showSystemInfo")
                    val showLeftIcons = showSystemInfo && (!mIsShowNotifPromptView || !mLastIsFocusedNotifPromptViewShowing)
                    val showFlag = if (showLeftIcons) 1 else 0
                    if (showFlag == lastShowLeftIcons) return
                    lastShowLeftIcons = showFlag
                    val mStatusBar = XposedHelpers.getObjectField(param.getThisObject(), "mStatusBar") as FrameLayout
                    val leftIconContainer = mStatusBar.findViewWithTag<View>("leftIconsContainer")
                    if (leftIconContainer != null) {
                        leftIconContainer.visibility = if (showLeftIcons) View.VISIBLE else View.INVISIBLE
                    }
                }
            })

            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiKeyguardStatusBarView", lpparam.classLoader, "miuiOnAttachedToWindow", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val mTintedIconManager = XposedHelpers.getObjectField(param.getThisObject(), "mTintedIconManager")
                    val mBlockList = XposedHelpers.getObjectField(mTintedIconManager, "mBlockList") as ArrayList<Any>
                    mBlockList.clear()
                    mBlockList.addAll(keyguardRightBlockList)
                    val statusBarIconController = XposedHelpers.getObjectField(mTintedIconManager, "mController")
                    XposedHelpers.callMethod(statusBarIconController, "refreshIconGroup", mTintedIconManager)
                }
            })

            installStatusBarViewLifecycleHook(lpparam)
        }
    }

    @JvmStatic
    fun StatusBarClockPositionHook(lpparam: PackageReadyParam) {
        val pos = MainModule.mPrefs.getStringAsInt("system_statusbar_clock_position", 1)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val sbView = param.getThisObject() as FrameLayout
                if (XposedHelpers.getAdditionalInstanceField(sbView, "clockPositionInitialized") != null) return
                val mContext = sbView.context
                val mClockView = XposedHelpers.getObjectField(param.getThisObject(), "mClock") as TextView
                val leftIconsContainer = mClockView.parent as LinearLayout
                leftIconsContainer.removeView(mClockView)
                val spaceView = XposedHelpers.getObjectField(param.getThisObject(), "mCutoutSpace") as View
                val mContentsContainer = spaceView.parent as LinearLayout
                val spaceIndex = mContentsContainer.indexOfChild(spaceView)
                val rightContainer = LinearLayout(mContext)
                val rightLp = LinearLayout.LayoutParams(0, -1, 1.0f)
                val mSystemIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mSystemIconArea") as View
                mContentsContainer.removeView(mSystemIconArea)
                mContentsContainer.addView(rightContainer, spaceIndex + 1, rightLp)
                rightContainer.addView(mSystemIconArea)

                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
                if (pos == 2) {
                    lp.gravity = Gravity.CENTER
                    mContentsContainer.addView(mClockView, spaceIndex, lp)
                } else {
                    rightContainer.addView(mClockView, lp)
                }
                XposedHelpers.setAdditionalInstanceField(sbView, "clockPositionInitialized", true)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "updateLayoutForCutout", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mCurrentStatusBarType = XposedHelpers.getObjectField(param.getThisObject(), "mCurrentStatusBarType") as Int
                val mSystemIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mSystemIconArea") as View
                val mStatusBarLeftContainer = XposedHelpers.getObjectField(param.getThisObject(), "mStatusBarLeftContainer") as View
                if (mCurrentStatusBarType == 0) {
                    val mSystemIconAreaLp = mSystemIconArea.layoutParams as LinearLayout.LayoutParams
                    mSystemIconAreaLp.width = 0
                    mSystemIconAreaLp.weight = 1.0f
                    if (pos == 2) {
                        val rightContainer = mSystemIconArea.parent as LinearLayout
                        val mDripStatusBarNotificationIconArea = XposedHelpers.getObjectField(param.getThisObject(), "mDripStatusBarNotificationIconArea") as View
                        mDripStatusBarNotificationIconArea.visibility = View.VISIBLE
                        val mStatusBarLeftContainerLp = mStatusBarLeftContainer.layoutParams as LinearLayout.LayoutParams
                        mStatusBarLeftContainerLp.width = 0
                        mStatusBarLeftContainerLp.weight = 1.0f
                        val sbView = param.getThisObject() as FrameLayout
                        val leftPadding = sbView.paddingStart
                        val rightPadding = sbView.paddingEnd
                        if (Math.abs(leftPadding - rightPadding) > 12) {
                            val topPadding = sbView.paddingTop
                            val bottomPadding = sbView.paddingBottom
                            mStatusBarLeftContainer.setPadding(leftPadding, 0, 0, 0)
                            rightContainer.setPadding(0, 0, rightPadding, 0)
                            sbView.setPadding(0, topPadding, 0, bottomPadding)
                            var focusedNotifView = sbView.findViewWithTag<View>("focused_notif_view")
                            if (focusedNotifView == null) {
                                val focusedNotifViewResId = sbView.resources.getIdentifier("focused_notif_view", "id", "com.android.systemui")
                                if (focusedNotifViewResId > 0) {
                                    focusedNotifView = sbView.findViewById<View>(focusedNotifViewResId)
                                    focusedNotifView.setTag("focused_notif_view")
                                }
                            }
                            focusedNotifView?.let {
                                it.setPaddingRelative(leftPadding, it.paddingTop, 0, 0)
                            }
                        }
                    }
                } else {
                    if (pos == 2) {
                        val mCutoutSpace = XposedHelpers.getObjectField(param.getThisObject(), "mCutoutSpace") as View
                        mCutoutSpace.visibility = View.GONE
                        mStatusBarLeftContainer.setPadding(0, 0, 0, 0)
                        val rightContainer = mSystemIconArea.parent as LinearLayout
                        rightContainer.setPadding(0, 0, 0, 0)
                    }
                }
            }
        })
        if (pos == 2) {
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "updateNotificationIconAreaInnnerParent", object : MethodHook() {
                private var originType = 0
                override fun before(param: BeforeHookCallback) {
                    val mCurrentStatusBarType = XposedHelpers.getIntField(param.getThisObject(), "mCurrentStatusBarType")
                    if (mCurrentStatusBarType == 0) {
                        XposedHelpers.setObjectField(param.getThisObject(), "mCurrentStatusBarType", 1)
                    }
                    originType = mCurrentStatusBarType
                }
                override fun after(param: AfterHookCallback) {
                    XposedHelpers.setObjectField(param.getThisObject(), "mCurrentStatusBarType", originType)
                }
            })
        }
    }

    private var measureTime = 0L

    private var txBytesTotal = 0L

    private var rxBytesTotal = 0L

    private var txSpeed = 0L

    private var rxSpeed = 0L

    private var sampledTxBytes = -1L

    private var sampledRxBytes = -1L

    private fun sampleTrafficBytes() {
        var tx = 0L
        var rx = 0L
        var sampled = false

        try {
            val list = NetworkInterface.getNetworkInterfaces()
            while (list != null && list.hasMoreElements()) {
                val iface = list.nextElement()
                if (iface.isUp && !iface.isVirtual && !iface.isLoopback && !iface.isPointToPoint && "" != iface.name) {
                    val ifaceTx = TrafficStats.getTxBytes(iface.name)
                    val ifaceRx = TrafficStats.getRxBytes(iface.name)
                    // TrafficStats returns UNSUPPORTED (-1) for interfaces it cannot account
                    // for. Accumulating that would silently corrupt the running total.
                    if (ifaceTx < 0 || ifaceRx < 0) continue
                    tx += ifaceTx
                    rx += ifaceRx
                    sampled = true
                }
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            sampled = false
        }

        if (!sampled) {
            // No interface could be accounted for individually; fall back to the
            // process-wide counters rather than publishing a synthetic total.
            tx = TrafficStats.getTotalTxBytes()
            rx = TrafficStats.getTotalRxBytes()
        }

        sampledTxBytes = tx
        sampledRxBytes = rx
    }

    /** Result of turning one traffic sample into a speed plus the next baseline. */
    internal data class NetSpeedDelta(
        val txSpeed: Long,
        val rxSpeed: Long,
        val txTotal: Long,
        val rxTotal: Long,
    )

    /**
     * Converts a cumulative traffic sample into per-second speeds and the baseline for the
     * next sample.
     *
     * An unavailable sample (`TrafficStats.UNSUPPORTED`) must never be stored as a baseline:
     * the following successful sample would subtract a negative total and report the entire
     * cumulative byte counter as a single interval of traffic. Such a sample re-baselines
     * instead, which the existing zero-baseline rule already reports as 0.
     */
    internal fun computeNetSpeedDelta(
        newTxBytes: Long,
        newRxBytes: Long,
        prevTxTotal: Long,
        prevRxTotal: Long,
        elapsedNanos: Long,
    ): NetSpeedDelta {
        if (newTxBytes < 0 || newRxBytes < 0) return NetSpeedDelta(0L, 0L, 0L, 0L)

        var txDelta = newTxBytes - prevTxTotal
        var rxDelta = newRxBytes - prevRxTotal
        if (txDelta < 0 || prevTxTotal == 0L) txDelta = 0
        if (rxDelta < 0 || prevRxTotal == 0L) rxDelta = 0

        val elapsedSeconds = elapsedNanos / 1_000_000_000.0
        return NetSpeedDelta(
            txSpeed = Math.round(txDelta / elapsedSeconds),
            rxSpeed = Math.round(rxDelta / elapsedSeconds),
            txTotal = newTxBytes,
            rxTotal = newRxBytes,
        )
    }

    /**
     * Formats [bytes] as a human-readable network speed string.
     *
     * Pure function: it reads only [bytes], the format [snapshot] and the module [modRes]. It does
     * not access [MainModule.mPrefs], [Context], [View] or reflection. The original implementation
     * only ever divided once by 1024 (KB -> MB), so this preserves the same unit boundaries.
     */
    internal fun humanReadableByteCount(bytes: Long, snapshot: DetailedNetSpeedFormatSnapshot, modRes: Resources): String {
        try {
            val unitSuffix = if (snapshot.hideSecUnit) "" else modRes.getString(R.string.Bs)
            var f = bytes / 1024.0f
            var expIndex = 0
            if (f > 999.0f) {
                expIndex = 1
                f /= 1024.0f
            }
            val pre = modRes.getString(R.string.speedunits)[expIndex]
            val number = formatNetSpeedValue(f)
            return StringBuilder().append(number).append(pre).append(unitSuffix).toString()
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            return ""
        }
    }

    /**
     * Builds the detailed network speed text for [updateText].
     *
     * All formatting uses the [snapshot]; [modRes] is only used to resolve module unit strings.
     */
    internal fun formatDetailedNetSpeedText(
        txSpeed: Long,
        rxSpeed: Long,
        snapshot: DetailedNetSpeedFormatSnapshot,
        modRes: Resources,
    ): Array<String?> {
        val txarrow: String
        val rxarrow: String
        if (snapshot.speedStyle == 2) {
            when (snapshot.icons) {
                2 -> {
                    txarrow = if (txSpeed < snapshot.lowLevelBytes) "△" else "▲"
                    rxarrow = if (rxSpeed < snapshot.lowLevelBytes) "▽" else "▼"
                }
                3 -> {
                    txarrow = if (txSpeed < snapshot.lowLevelBytes) " ☖" else " ☗"
                    rxarrow = if (rxSpeed < snapshot.lowLevelBytes) " ⛉" else " ⛊"
                }
                else -> {
                    txarrow = ""
                    rxarrow = ""
                }
            }
        } else {
            txarrow = ""
            rxarrow = ""
        }

        val rx = if (snapshot.hideLow && rxSpeed < snapshot.lowLevelBytes) "" else humanReadableByteCount(rxSpeed, snapshot, modRes) + rxarrow
        val text = if (snapshot.speedStyle == 2) {
            val tx = if (snapshot.hideLow && txSpeed < snapshot.lowLevelBytes) "" else humanReadableByteCount(txSpeed, snapshot, modRes) + txarrow
            "$tx\n$rx"
        } else {
            rx
        }
        return arrayOf<String?>(text, "")
    }

    @JvmStatic
    fun DetailedNetSpeedHook(lpparam: PackageReadyParam) {
        // Ensure the shared B1/B2 runtime-state holder exists. B2 only adds the detailed-format
        // substate; if B1 is installed later the same observer and owner are reused.
        ensureNetSpeedRuntimeState().ensureDetailedState()

        val NetworkSpeedController = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.policy.NetworkSpeedController", lpparam.classLoader)
        if (NetworkSpeedController == null) {
            XposedHelpers.log("DetailedNetSpeedHook", "No NetworkSpeed view or controller")
            return
        }

        val mBgHandlerField = XposedHelpers.findField(NetworkSpeedController, "mBgHandler")
        ModuleHelper.findAndHookMethod(mBgHandlerField.type, "handleMessage", Message::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val message = param.getArg(0) as Message
                if (message.what == 200001) {
                    val thisObect = XposedHelpers.getSurroundingThis(param.getThisObject())
                    var isConnected = false
                    val mContext = XposedHelpers.getObjectField(thisObect, "mContext") as Context
                    val mConnectivityManager = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val nw = mConnectivityManager.activeNetwork
                    if (nw != null) {
                        val capabilities = mConnectivityManager.getNetworkCapabilities(nw)
                        if (capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))) {
                            isConnected = true
                        }
                    }
                    if (isConnected) {
                        val nanoTime = java.lang.System.nanoTime()
                        var newTime = nanoTime - measureTime
                        measureTime = nanoTime
                        if (newTime > 12_000_000_000L || newTime == 0L) newTime = 4_000_000_000L
                        sampleTrafficBytes()
                        val delta = computeNetSpeedDelta(
                            sampledTxBytes,
                            sampledRxBytes,
                            txBytesTotal,
                            rxBytesTotal,
                            newTime,
                        )
                        txSpeed = delta.txSpeed
                        rxSpeed = delta.rxSpeed
                        txBytesTotal = delta.txTotal
                        rxBytesTotal = delta.rxTotal
                    } else {
                        txSpeed = 0
                        rxSpeed = 0
                    }
                }
            }
        })

        ModuleHelper.hookAllMethods(NetworkSpeedController, "updateText", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val context = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                val modRes = ModuleHelper.getModuleRes(context)
                val snapshot = currentOrBuildDetailedNetSpeedFormatSnapshot()
                param.getArgs()[0] = formatDetailedNetSpeedText(txSpeed, rxSpeed, snapshot, modRes)
            }
        })
    }

    internal class NetSpeedTypefaceState(var base: Typeface? = null, var target: Typeface? = null)

    /**
     * Immutable snapshot of the original, system-provided style state of a
     * NetworkSpeedView and its number/unit TextViews. It deliberately holds no
     * strong references to any View, Context, Resources, controller, parent or
     * LayoutParams instance; only the primitive/value fields required to restore
     * the original appearance are kept.
     */
    internal data class NetSpeedOriginalStyleState(
        val parentTranslationY: Float,
        val parentPaddingStart: Int,
        val parentPaddingTop: Int,
        val parentPaddingEnd: Int,
        val parentPaddingBottom: Int,
        val numberTextSizePx: Float,
        val numberGravity: Int,
        val numberTextAlignment: Int,
        val numberSingleLine: Boolean,
        val numberMaxLines: Int,
        val numberLineSpacingExtra: Float,
        val numberLineSpacingMultiplier: Float,
        val numberLpWidth: Int,
        val numberLpHeight: Int,
        val numberLpWeight: Float,
        val numberLpGravity: Int,
        val numberLpLeftMargin: Int,
        val numberLpRightMargin: Int,
        val numberLpTopMargin: Int,
        val numberLpBottomMargin: Int,
        val numberLpMarginStart: Int,
        val numberLpMarginEnd: Int,
        val unitVisibility: Int,
        val unitTextSizePx: Float,
        val unitTextAlignment: Int,
    ) {
        companion object {
            fun capture(speedView: LinearLayout, numberText: TextView, unitText: TextView?): NetSpeedOriginalStyleState {
                val numberLp = numberText.layoutParams as? LinearLayout.LayoutParams
                return NetSpeedOriginalStyleState(
                    parentTranslationY = speedView.translationY,
                    parentPaddingStart = speedView.paddingStart,
                    parentPaddingTop = speedView.paddingTop,
                    parentPaddingEnd = speedView.paddingEnd,
                    parentPaddingBottom = speedView.paddingBottom,
                    numberTextSizePx = numberText.textSize,
                    numberGravity = numberText.gravity,
                    numberTextAlignment = numberText.textAlignment,
                    numberSingleLine = numberText.isSingleLine,
                    numberMaxLines = numberText.maxLines,
                    numberLineSpacingExtra = numberText.lineSpacingExtra,
                    numberLineSpacingMultiplier = numberText.lineSpacingMultiplier,
                    numberLpWidth = numberLp?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                    numberLpHeight = numberLp?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                    numberLpWeight = numberLp?.weight ?: 0f,
                    numberLpGravity = numberLp?.gravity ?: 0,
                    numberLpLeftMargin = numberLp?.leftMargin ?: 0,
                    numberLpRightMargin = numberLp?.rightMargin ?: 0,
                    numberLpTopMargin = numberLp?.topMargin ?: 0,
                    numberLpBottomMargin = numberLp?.bottomMargin ?: 0,
                    numberLpMarginStart = numberLp?.marginStart ?: 0,
                    numberLpMarginEnd = numberLp?.marginEnd ?: 0,
                    unitVisibility = unitText?.visibility ?: View.GONE,
                    unitTextSizePx = unitText?.textSize ?: 0f,
                    unitTextAlignment = unitText?.textAlignment ?: View.TEXT_ALIGNMENT_GRAVITY,
                )
            }
        }
    }

    private fun getNetSpeedOriginalStyleState(
        speedView: LinearLayout,
        numberText: TextView,
        unitText: TextView?,
    ): NetSpeedOriginalStyleState {
        val styleState = netSpeedRuntimeState?.styleState ?: error("Net speed style state not installed")
        val existing = speedView.getTag(styleState.originalStyleStateTag) as? NetSpeedOriginalStyleState
        if (existing != null) return existing
        val state = NetSpeedOriginalStyleState.capture(speedView, numberText, unitText)
        speedView.setTag(styleState.originalStyleStateTag, state)
        return state
    }

    private fun getNetSpeedNumberView(speedView: LinearLayout): TextView? {
        val styleState = netSpeedRuntimeState?.styleState ?: return null
        val cached = speedView.getTag(styleState.numberViewTag) as? TextView
        if (cached != null) return cached
        val numberText = XposedHelpers.getObjectField(speedView, "mNetworkSpeedNumberText") as? TextView ?: return null
        speedView.setTag(styleState.numberViewTag, numberText)
        return numberText
    }

    private fun getNetSpeedUnitView(speedView: LinearLayout): TextView? {
        val styleState = netSpeedRuntimeState?.styleState ?: return null
        val cached = speedView.getTag(styleState.unitViewTag) as? TextView
        if (cached != null) return cached
        val unitText = XposedHelpers.getObjectField(speedView, "mNetworkSpeedUnitText") as? TextView ?: return null
        speedView.setTag(styleState.unitViewTag, unitText)
        return unitText
    }

    private fun ensureNetSpeedTypeface(textView: TextView, bold: Boolean) {
        val styleState = netSpeedRuntimeState?.styleState ?: return
        val state = textView.getTag(styleState.typefaceStateTag) as? NetSpeedTypefaceState
            ?: NetSpeedTypefaceState().also { textView.setTag(styleState.typefaceStateTag, it) }

        val current = textView.typeface
        if (state.target != null && current === state.target && textView.paint.isFakeBoldText == bold) return

        if (state.target != null && current !== state.target) {
            state.base = current
            state.target = null
        }

        if (state.base == null) state.base = current

        val base = state.base
        val targetStyle = resolveNetSpeedTypefaceStyle(base?.style ?: 0, bold)
        val target = Typeface.create(base, targetStyle)
        textView.typeface = target
        textView.paint.isFakeBoldText = bold
        state.target = target
    }

    /**
     * Builds an immutable [NetSpeedTextStyleSnapshot] from [prefs].
     *
     * This is the only place these preference keys are read for the per-second network speed
     * styling hot path. The snapshot is published atomically, so hot-path readers never see a
     * partially-constructed snapshot.
     */
    internal fun buildNetSpeedTextStyleSnapshot(prefs: PrefMap): NetSpeedTextStyleSnapshot {
        val state = netSpeedRuntimeState?.styleState ?: error("Net speed style state not installed")
        return buildNetSpeedTextStyleSnapshot(prefs, state.idGenerator)
    }

    private fun buildNetSpeedTextStyleSnapshot(prefs: PrefMap, idGenerator: AtomicLong): NetSpeedTextStyleSnapshot {
        return NetSpeedTextStyleSnapshot(
            id = idGenerator.incrementAndGet(),
            speedStyle = prefs.getStringAsInt("system_detailednetspeed_style", 1),
            bold = prefs.getBoolean("system_netspeed_boldfont"),
            fontSize = prefs.getInt("system_netspeed_fontsize", 13),
            fixedWidth = prefs.getInt("system_netspeed_fixedcontent_width", 10),
            leftMargin = prefs.getInt("system_netspeed_leftmargin", 0),
            rightMargin = prefs.getInt("system_netspeed_rightmargin", 0),
            verticalOffset = prefs.getInt("system_netspeed_verticaloffset", 8),
            align = prefs.getStringAsInt("system_detailednetspeed_align", 1),
            adjustment = prefs.getInt("system_netspeed_rowspacing", 100),
        )
    }

    /** Returns the current snapshot, building it from [MainModule.mPrefs] if it does not yet exist. */
    private fun currentOrBuildNetSpeedTextStyleSnapshot(): NetSpeedTextStyleSnapshot {
        val state = netSpeedRuntimeState?.styleState ?: error("Net speed style state not installed")
        val existing = state.currentSnapshot.get()
        if (existing != null) return existing

        val built = buildNetSpeedTextStyleSnapshot(MainModule.mPrefs, state.idGenerator)
        state.currentSnapshot.set(built)
        return built
    }

    /**
     * Builds an immutable [DetailedNetSpeedFormatSnapshot] from [prefs].
     *
     * This is the only place these preference keys are read for the per-second detailed network
     * speed text hot path.  The [lowLevelBytes] value is pre-multiplied by 1024 to match the
     * threshold used in [updateText].
     */
    internal fun buildDetailedNetSpeedFormatSnapshot(prefs: PrefMap): DetailedNetSpeedFormatSnapshot {
        val state = netSpeedRuntimeState?.detailedState ?: error("Detailed net speed state not installed")
        return buildDetailedNetSpeedFormatSnapshot(prefs, state.idGenerator)
    }

    private fun buildDetailedNetSpeedFormatSnapshot(prefs: PrefMap, idGenerator: AtomicLong): DetailedNetSpeedFormatSnapshot {
        return DetailedNetSpeedFormatSnapshot(
            id = idGenerator.incrementAndGet(),
            hideLow = prefs.getBoolean("system_detailednetspeed_low"),
            lowLevelBytes = prefs.getInt("system_detailednetspeed_lowlevel", 1) * 1024,
            speedStyle = prefs.getStringAsInt("system_detailednetspeed_style", 1),
            icons = prefs.getStringAsInt("system_detailednetspeed_icon", 2),
            hideSecUnit = prefs.getBoolean("system_detailednetspeed_secunit"),
        )
    }

    /** Returns the current snapshot, building it from [MainModule.mPrefs] if it does not yet exist. */
    private fun currentOrBuildDetailedNetSpeedFormatSnapshot(): DetailedNetSpeedFormatSnapshot {
        val state = netSpeedRuntimeState?.detailedState ?: error("Detailed net speed state not installed")
        val existing = state.currentSnapshot.get()
        if (existing != null) return existing

        val built = buildDetailedNetSpeedFormatSnapshot(MainModule.mPrefs, state.idGenerator)
        state.currentSnapshot.set(built)
        return built
    }

    /**
     * Builds an immutable [StatusBarIconVisibilitySnapshot] from [prefs].
     *
     * This is the only place the `system_statusbaricons_*` keys are read for the three hide-icon
     * hot paths. The snapshot contains one [Boolean] per relevant key.
     */
    internal fun buildStatusBarIconVisibilitySnapshot(prefs: PrefMap): StatusBarIconVisibilitySnapshot {
        val state = iconVisibilityRuntimeState ?: error("Status bar icon visibility state not installed")
        return buildStatusBarIconVisibilitySnapshot(prefs, state.idGenerator)
    }

    private fun buildStatusBarIconVisibilitySnapshot(prefs: PrefMap, idGenerator: AtomicLong): StatusBarIconVisibilitySnapshot {
        return StatusBarIconVisibilitySnapshot(
            id = idGenerator.incrementAndGet(),
            hideHeadset = prefs.getBoolean("system_statusbaricons_headset"),
            hideSound = prefs.getBoolean("system_statusbaricons_sound"),
            hideDnd = prefs.getBoolean("system_statusbaricons_dnd"),
            hideAlarm = prefs.getBoolean("system_statusbaricons_alarm"),
            hideProfile = prefs.getBoolean("system_statusbaricons_profile"),
            hideVpn = prefs.getBoolean("system_statusbaricons_vpn"),
            hideAirplane = prefs.getBoolean("system_statusbaricons_airplane"),
            hideNfc = prefs.getBoolean("system_statusbaricons_nfc"),
            hideSecondSpace = prefs.getBoolean("system_statusbaricons_secondspace"),
            hideGps = prefs.getBoolean("system_statusbaricons_gps"),
            hideWifi = prefs.getBoolean("system_statusbaricons_wifi"),
            hideHotspot = prefs.getBoolean("system_statusbaricons_hotspot"),
            hideNoSims = prefs.getBoolean("system_statusbaricons_nosims"),
            hideBtBattery = prefs.getBoolean("system_statusbaricons_btbattery"),
            hideBleUnlock = prefs.getBoolean("system_statusbaricons_ble_unlock"),
            hideBluetoothIcn = prefs.getBoolean("system_statusbaricons_bluetoothicn"),
            hideVolte = prefs.getBoolean("system_statusbaricons_volte"),
            hideSignal = prefs.getBoolean("system_statusbaricons_signal"),
            hideSignalWifiConnected = prefs.getBoolean("system_statusbaricons_signal_wificonnected"),
            hideSim1 = prefs.getBoolean("system_statusbaricons_sim1"),
            hideSim2 = prefs.getBoolean("system_statusbaricons_sim2"),
            hideSimNoData = prefs.getBoolean("system_statusbaricons_sim_nodata"),
            hideRoaming = prefs.getBoolean("system_statusbaricons_roaming"),
            hidePrivacy = prefs.getBoolean("system_statusbaricons_privacy"),
            hideMute = prefs.getBoolean("system_statusbaricons_mute"),
            hideSpeaker = prefs.getBoolean("system_statusbaricons_speaker"),
            hideRecord = prefs.getBoolean("system_statusbaricons_record"),
            hideWirelessHeadset = prefs.getBoolean("system_statusbaricons_wireless_headset"),
        )
    }

    /** Returns the current snapshot, building it from [MainModule.mPrefs] if it does not yet exist. */
    private fun currentOrBuildStatusBarIconVisibilitySnapshot(): StatusBarIconVisibilitySnapshot {
        val state = iconVisibilityRuntimeState ?: error("Status bar icon visibility state not installed")
        val existing = state.currentSnapshot.get()
        if (existing != null) return existing

        val built = buildStatusBarIconVisibilitySnapshot(MainModule.mPrefs, state.idGenerator)
        state.currentSnapshot.set(built)
        return built
    }

    /**
     * Applies the network-speed text style to [speedView].
     *
     * The style is fully reversible: the original system-provided state is captured once per view
     * and the full apply path derives the complete target state from [original] + [snapshot].
     * Setters are applied in a single guarded block; if any setter throws a non-fatal exception the
     * function returns without marking the snapshot as completed, so the next call with the same
     * snapshot can retry.
     *
     * Callers:
     * - `NetworkSpeedView.setNetworkSpeed` per tick: `typefaceOnly = false`.  This path short-circuits
     *   when the last full-style [snapshot.id] has already been applied to this [speedView], so the
     *   common per-second case performs zero full-style setters.
     * - `NetworkSpeedView.onFinishInflate`: `typefaceOnly = false`.  Full style is applied once and
     *   the view is marked as styled.
     * - `TextView.setTextAppearance` after-hook: `typefaceOnly = true`.  After the framework or
     *   `onFinishInflate` applies a text appearance, this only restores the network-speed typeface
     *   and fake-bold state.  It also invalidates the cached original style and last full snapshot so
     *   the next full apply re-captures the new baseline.
     *
     * Hot-path invariants:
     * - No [MainModule.mPrefs] reads.
     * - No reflection, resource-name searches, or temporary collections.
     * - Logical configuration is converted to physical pixels on demand using the View's
     *   current resources, so density/fontScale changes never use stale px values.
     * - The last full [NetSpeedTextStyleSnapshot.id] is stored as an additional instance field on
     *   [speedView]; when the same [snapshot] has already been fully applied, [typefaceOnly] = false
     *   returns early and touches no setters.
     */
    internal fun applyNetSpeedTextStyle(speedView: LinearLayout, snapshot: NetSpeedTextStyleSnapshot, typefaceOnly: Boolean) {
        if (speedView.tag as? String == "slot_text_icon") return

        val lastFullId = XposedHelpers.getAdditionalInstanceField(speedView, NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID) as? Long
        if (!typefaceOnly && lastFullId == snapshot.id) {
            // setNetworkSpeed per-second path: same view, same snapshot, full style already applied.
            return
        }

        val numberText = getNetSpeedNumberView(speedView) ?: return
        val unitText = getNetSpeedUnitView(speedView)

        val speedStyle = snapshot.speedStyle
        val bold = snapshot.bold

        if (typefaceOnly) {
            ensureNetSpeedTypeface(numberText, bold)
            if (speedStyle == 1) {
                unitText?.let { ensureNetSpeedTypeface(it, bold) }
            }
            return
        }

        // Guard: the original baseline cannot be captured before the framework has attached a real
        // LinearLayout.LayoutParams to the number view.  Creating a guessed LP here would poison
        // the per-view baseline and prevent correct custom->default reversibility.
        if (numberText.layoutParams == null) return

        val resources = speedView.resources
        val original = getNetSpeedOriginalStyleState(speedView, numberText, unitText)
        val fontSize = snapshot.fontSize
        val fixedWidth = snapshot.fixedWidth
        val singleOrDual = speedStyle == 2 || speedStyle == 3
        var layoutParamsReady = true

        try {
            // Parent (speedView)
            val translationY = if (snapshot.verticalOffset == 8) {
                original.parentTranslationY
            } else {
                resources.dp2px((snapshot.verticalOffset - 8) * 0.5f)
            }
            speedView.translationY = translationY

            val start = if (snapshot.leftMargin != 0) {
                resources.dp2px(snapshot.leftMargin * 0.5f).toInt()
            } else {
                original.parentPaddingStart
            }
            val end = if (snapshot.rightMargin != 0) {
                resources.dp2px(snapshot.rightMargin * 0.5f).toInt()
            } else {
                original.parentPaddingEnd
            }
            speedView.setPaddingRelative(start, original.parentPaddingTop, end, original.parentPaddingBottom)

            // Number TextView
            if (fontSize > 13) {
                numberText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize * 0.5f)
            } else {
                numberText.setTextSize(TypedValue.COMPLEX_UNIT_PX, original.numberTextSizePx)
            }

            numberText.textAlignment = if (snapshot.align > 1) {
                when (snapshot.align) {
                    3 -> View.TEXT_ALIGNMENT_CENTER
                    4 -> View.TEXT_ALIGNMENT_TEXT_END
                    else -> View.TEXT_ALIGNMENT_TEXT_START
                }
            } else {
                original.numberTextAlignment
            }

            numberText.gravity = if (singleOrDual) {
                Gravity.CENTER_VERTICAL or Gravity.START
            } else {
                original.numberGravity
            }

            when (speedStyle) {
                2 -> {
                    numberText.setSingleLine(false)
                    numberText.maxLines = 2
                    numberText.setLineSpacing(0f, resolveNetSpeedLineSpacing(fontSize, snapshot.adjustment))
                }
                else -> {
                    numberText.setSingleLine(original.numberSingleLine)
                    numberText.maxLines = original.numberMaxLines
                    numberText.setLineSpacing(original.numberLineSpacingExtra, original.numberLineSpacingMultiplier)
                }
            }

            // Number LayoutParams
            val currentNumberLp = numberText.layoutParams
            val lpRequired = singleOrDual || fixedWidth > 10
            val canSetLp = currentNumberLp == null || currentNumberLp is LinearLayout.LayoutParams
            if (canSetLp) {
                val numberLp = LinearLayout.LayoutParams(
                    if (fixedWidth > 10) numberText.resources.dp2px(fixedWidth.toFloat()).toInt() else original.numberLpWidth,
                    if (singleOrDual) ViewGroup.LayoutParams.MATCH_PARENT else original.numberLpHeight
                )
                // Explicitly set width/height in case the stub LayoutParams constructor is a no-op
                // in unit tests; the real Android constructor also sets them, so this is harmless.
                numberLp.width = if (fixedWidth > 10) numberText.resources.dp2px(fixedWidth.toFloat()).toInt() else original.numberLpWidth
                numberLp.height = if (singleOrDual) ViewGroup.LayoutParams.MATCH_PARENT else original.numberLpHeight
                numberLp.weight = original.numberLpWeight
                numberLp.gravity = original.numberLpGravity
                numberLp.leftMargin = original.numberLpLeftMargin
                numberLp.rightMargin = original.numberLpRightMargin
                numberLp.topMargin = if (singleOrDual) 0 else original.numberLpTopMargin
                numberLp.bottomMargin = if (singleOrDual) 0 else original.numberLpBottomMargin
                numberLp.marginStart = original.numberLpMarginStart
                numberLp.marginEnd = original.numberLpMarginEnd
                numberText.layoutParams = numberLp
            } else if (lpRequired) {
                layoutParamsReady = false
            }

            // Unit TextView
            unitText?.let { unit ->
                if (speedStyle == 1) {
                    unit.visibility = original.unitVisibility
                    if (fontSize > 13) {
                        unit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize * 0.5f)
                    } else {
                        unit.setTextSize(TypedValue.COMPLEX_UNIT_PX, original.unitTextSizePx)
                    }
                    unit.textAlignment = if (snapshot.align > 1) {
                        when (snapshot.align) {
                            3 -> View.TEXT_ALIGNMENT_CENTER
                            4 -> View.TEXT_ALIGNMENT_TEXT_END
                            else -> View.TEXT_ALIGNMENT_TEXT_START
                        }
                    } else {
                        original.unitTextAlignment
                    }
                } else {
                    unit.visibility = View.GONE
                }
            }

            ensureNetSpeedTypeface(numberText, bold)
            if (speedStyle == 1) {
                unitText?.let { ensureNetSpeedTypeface(it, bold) }
            }

            if (layoutParamsReady) {
                speedView.setTag(viewInitedTag, true)
                XposedHelpers.setAdditionalInstanceField(speedView, NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID, snapshot.id)
            }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("applyNetSpeedTextStyle", "Non-fatal error applying snapshot ${snapshot.id}: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Handles `NetworkSpeedView.setNetworkSpeed` per tick. Always requests a full style apply;
     * [applyNetSpeedTextStyle] short-circuits when the same snapshot is already applied, so the
     * common per-second path performs zero full-style setters and never touches the text content.
     */
    private fun onNetworkSpeedValueSet(speedView: LinearLayout) {
        if (speedView.tag as? String == "slot_text_icon") return
        val snapshot = currentOrBuildNetSpeedTextStyleSnapshot()
        applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
    }

    /**
     * Handles `TextView.setTextAppearance` for the network-speed number/unit TextViews.
     *
     * Records the new base typeface, restores the network-speed typeface and fake-bold state,
     * and invalidates the cached full-style snapshot id on the parent.  The original style
     * baseline is intentionally left in place: it was captured once for this NetworkSpeedView
     * and must survive for the lifetime of the view.  This ensures the next full apply re-applies
     * the custom NetworkSpeed style while still being able to restore the true stock baseline.
     */
    internal fun onNetworkSpeedTextAppearanceChanged(
        textView: TextView,
        parentLayout: LinearLayout? = textView.parent as? LinearLayout,
    ) {
        val styleState = netSpeedRuntimeState?.styleState ?: return
        val state = textView.getTag(styleState.typefaceStateTag) as? NetSpeedTypefaceState ?: return
        val parent = parentLayout ?: return

        state.base = textView.typeface
        state.target = null
        val snapshot = currentOrBuildNetSpeedTextStyleSnapshot()
        ensureNetSpeedTypeface(textView, snapshot.bold)

        XposedHelpers.removeAdditionalInstanceField(parent, NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID)
    }

    /**
     * Handles `NetworkSpeedView.onFinishInflate`. Creates the typeface state tags and either
     * applies the full custom style directly or first applies a system clock-style text appearance
     * (whose after-hook then restores only the typeface).  In the useClockStyle path the full
     * NetworkSpeed custom style is still applied afterwards, so the captured baseline is the
     * clock-styled appearance while the final visible state is the custom NetworkSpeed style.
     */
    internal fun onNetworkSpeedViewInflated(speedView: LinearLayout) {
        if (speedView.tag as? String == "slot_text_icon") return
        if (speedView.getTag(viewInitedTag) != null) return

        val styleState = netSpeedRuntimeState?.styleState ?: return
        val numberText = getNetSpeedNumberView(speedView) ?: return
        val unitText = getNetSpeedUnitView(speedView)

        numberText.getTag(styleState.typefaceStateTag) as? NetSpeedTypefaceState
            ?: NetSpeedTypefaceState().also { numberText.setTag(styleState.typefaceStateTag, it) }
        unitText?.let { view ->
            view.getTag(styleState.typefaceStateTag) as? NetSpeedTypefaceState
                ?: NetSpeedTypefaceState().also { view.setTag(styleState.typefaceStateTag, it) }
        }

        val snapshot = currentOrBuildNetSpeedTextStyleSnapshot()
        val useClockStyle = MainModule.mPrefs.getBoolean("system_netspeed_use_clock_style")
        if (useClockStyle) {
            val styleId = speedView.resources.getIdentifier("TextAppearance.StatusBar.Clock", "style", "com.android.systemui")
            if (styleId != 0) {
                numberText.setTextAppearance(styleId)
                if (snapshot.speedStyle == 1) unitText?.setTextAppearance(styleId)
            }
        }

        applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
    }

    @JvmStatic
    fun NetSpeedStyleHook(lpparam: PackageReadyParam) {
        // Ensure the shared B1/B2 runtime-state holder and the B1 text-style substate exist. The
        // observer is bound to the module singleton and is registered only on first use.
        ensureNetSpeedRuntimeState().ensureStyleState()

        ModuleHelper.hookAllMethods("android.widget.TextView", lpparam.classLoader, "setTextAppearance", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val textChild = param.getThisObject() as? TextView ?: return
                onNetworkSpeedTextAppearanceChanged(textChild)
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.views.NetworkSpeedView", lpparam.classLoader, "setNetworkSpeed", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val networkLayout = param.getThisObject() as? LinearLayout ?: return
                onNetworkSpeedValueSet(networkLayout)
            }
        })

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.NetworkSpeedView", lpparam.classLoader, "onFinishInflate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val networkLayout = param.getThisObject() as? LinearLayout ?: return
                onNetworkSpeedViewInflated(networkLayout)
            }
        })
    }

    @JvmStatic
    fun NetSpeedIntervalHook(lpparam: PackageReadyParam) {
        val NetworkSpeedController = XposedHelpers.findClass("com.android.systemui.statusbar.policy.NetworkSpeedController", lpparam.classLoader)
        val mBgHandlerField = XposedHelpers.findField(NetworkSpeedController, "mBgHandler")
        ModuleHelper.findAndHookMethod(mBgHandlerField.type, "handleMessage", Message::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val message = param.getArgs()[0] as Message
                if (message.what == 200001) {
                    val mBgHandler = param.getThisObject() as Handler
                    mBgHandler.removeMessages(200001)
                    val newInterval = MainModule.mPrefs.getInt("system_netspeedinterval", 4) * 1000L
                    mBgHandler.sendEmptyMessageDelayed(200001, newInterval)
                }
            }
        })
    }

    @JvmStatic
    fun MobileNetworkTypeHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.connectivity.MobileSignalController", lpparam.classLoader, "getMobileTypeName", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val net = param.getResult() as String
                if (MainModule.mPrefs.getBoolean("system_4gtolte")) {
                    if ("4G" == net) param.setResult("LTE")
                    else if ("4G+" == net) param.setResult("LTE+")
                } else {
                    val mobileType = MainModule.mPrefs.getString("system_statusbar_mobile_showname", "")
                    param.setResult(mobileType)
                }
            }
        })
    }

    @JvmStatic
    fun DisableFakeClockAnimHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "setMNCSwitching", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mNCSwitching = param.getArgs()[0] as Boolean
                if (!mNCSwitching) {
                    val mFakeClock = XposedHelpers.getObjectField(param.getThisObject(), "fakeStatusBarClockController")
                    XposedHelpers.setObjectField(mFakeClock, "ncSwitching", true)
                }
            }
        })
    }

    @JvmStatic
    fun MobileTypeSingleHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateMobileTypeLayout", HookerClassHelper.DO_NOTHING)
        val stateHook = object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
            private var initAction = false

            override fun before(param: BeforeHookCallback) {
                XposedHelpers.setObjectField(param.getArg(0), "showMobileDataTypeSingle", true)
                if (param.getMember().name == "updateState") {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }

            override fun after(param: AfterHookCallback) {
                val updateStateMethod = param.getMember().name == "updateState"
                if (updateStateMethod || initAction) {
                    val mMobileLeftContainer = XposedHelpers.getObjectField(param.getThisObject(), "mMobileLeftContainer")
                    XposedHelpers.callMethod(mMobileLeftContainer, "setVisibility", View.GONE)
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateHook)

        val initHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mobileView = param.getThisObject() as View
                val inited = ModuleHelper.getViewInfo(mobileView, "mobileTypeHook")
                if (inited == null) {
                    ModuleHelper.setViewInfo(mobileView, "mobileTypeHook", true)
                } else {
                    return
                }
                val mMobileGroup = XposedHelpers.getObjectField(param.getThisObject(), "mMobileGroup") as LinearLayout
                val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                if (!MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single_atleft")) {
                    mMobileGroup.removeView(mMobileTypeSingle)
                    mMobileGroup.addView(mMobileTypeSingle)
                }
                val mlp = mMobileTypeSingle.layoutParams as ViewGroup.MarginLayoutParams
                var leftMargin = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_leftmargin", 4)
                mlp.leftMargin = HookUtils.dp2px(leftMargin * 0.5f).toInt()
                val rightMargin = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_rightmargin", 0)
                if (rightMargin > 0) {
                    mlp.rightMargin = HookUtils.dp2px(rightMargin * 0.5f).toInt()
                }
                val verticalOffset = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_verticaloffset", 8)
                if (verticalOffset != 8) {
                    mlp.topMargin = HookUtils.dp2px((verticalOffset - 8) * 0.5f).toInt()
                }
                mMobileTypeSingle.layoutParams = mlp
                val fontSize = MainModule.mPrefs.getInt("system_statusbar_mobiletype_single_fontsize", 27)
                mMobileTypeSingle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize * 0.5f)
                if (MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single_bold")) {
                    mMobileTypeSingle.typeface = Typeface.DEFAULT_BOLD
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "setDripEnd", Boolean::class.javaPrimitiveType!!, initHook)
    }

    @JvmStatic
    fun HorizMarginHook(lpparam: PackageReadyParam) {
        val horizHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val leftMargin = MainModule.mPrefs.getInt("system_statusbar_horizmargin_left", 16)
                val leftMarginPx = HookUtils.dp2px(leftMargin.toFloat()).toInt()
                val rightMargin = MainModule.mPrefs.getInt("system_statusbar_horizmargin_right", 16)
                val rightMarginPx = HookUtils.dp2px(rightMargin.toFloat()).toInt()
                param.returnAndSkip(Pair(leftMarginPx, rightMarginPx))
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.StatusBarContentInsetsProvider", lpparam.classLoader, "getStatusBarContentInsetsForCurrentRotation", horizHook)
    }

    @JvmStatic
    fun HideIconsVoWiFiHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors("com.android.systemui.MiuiOperatorCustomizedPolicy\$MiuiOperatorConfig", lpparam.classLoader, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.getArgs()[3] = true
            }
        })
    }

    /** Result of [computeSignalIconHiding] for applying to the [mobileIconState] object. */
    internal data class SignalIconHidingResult(
        val visible: Boolean? = null,
        val roaming: Boolean? = null,
        val volte: Boolean? = null,
        val speechHd: Boolean? = null,
    )

    /**
     * Computes the visibility/roaming/volte changes for [HideIconsSignalHook].
     *
     * Pure function: it reads only the supplied primitives and the [snapshot]. It does not
     * access [MainModule.mPrefs], [View] or reflection.
     */
    internal fun computeSignalIconHiding(
        wifiAvailable: Boolean,
        subId: Int,
        dataSubId: Int,
        slotId: Int,
        snapshot: StatusBarIconVisibilitySnapshot,
    ): SignalIconHidingResult {
        if (snapshot.hideSignal) {
            if (!snapshot.hideSignalWifiConnected || wifiAvailable) {
                return SignalIconHidingResult(visible = false)
            }
        }
        if ((snapshot.hideSim1 && slotId == 0)
            || (snapshot.hideSim2 && slotId == 1)
            || (snapshot.hideSimNoData && subId != dataSubId)
        ) {
            return SignalIconHidingResult(visible = false)
        }
        return SignalIconHidingResult(
            roaming = if (snapshot.hideRoaming) false else null,
            volte = if (snapshot.hideVolte) false else null,
            speechHd = if (snapshot.hideVolte) false else null,
        )
    }

    /**
     * Determines whether a status-bar icon should be hidden for [HideIconsHook].
     *
     * Pure function: it matches the [slotName] against the fixed slot set using [when] and
     * reads only the [snapshot]. It does not touch [MainModule.mPrefs].
     */
    internal fun checkSlot(slotName: String?, snapshot: StatusBarIconVisibilitySnapshot): Boolean {
        return when (slotName) {
            "headset" -> snapshot.hideHeadset
            "volume" -> snapshot.hideSound
            "zen" -> snapshot.hideDnd
            "alarm_clock" -> snapshot.hideAlarm
            "managed_profile" -> snapshot.hideProfile
            "vpn" -> snapshot.hideVpn
            "airplane" -> snapshot.hideAirplane
            "nfc" -> snapshot.hideNfc
            "second_space" -> snapshot.hideSecondSpace
            "location" -> snapshot.hideGps
            "wifi" -> snapshot.hideWifi
            "hotspot" -> snapshot.hideHotspot
            "no_sim" -> snapshot.hideNoSims
            "bluetooth_handsfree_battery" -> snapshot.hideBtBattery
            "ble_unlock_mode" -> snapshot.hideBleUnlock
            "bluetooth" -> snapshot.hideBluetoothIcn
            "hd" -> snapshot.hideVolte
            else -> false
        }
    }

    /**
     * Determines whether a system-manager icon should be hidden.
     *
     * Pure function: it matches the fixed slot set with [when] and reads only the [snapshot].
     */
    internal fun shouldHideSystemManagerIcon(slotName: String, snapshot: StatusBarIconVisibilitySnapshot): Boolean {
        return when (slotName) {
            "stealth" -> snapshot.hidePrivacy
            "mute" -> snapshot.hideMute
            "speakerphone" -> snapshot.hideSpeaker
            "call_record" -> snapshot.hideRecord
            "wireless_headset" -> snapshot.hideWirelessHeadset
            else -> false
        }
    }

    @JvmStatic
    fun HideIconsSignalHook(lpparam: PackageReadyParam) {
        ensureStatusBarIconVisibilityRuntimeState()

        val abi = StatusBarIconVisibilityResolver.resolve(lpparam.classLoader)
        val effect = StatusBarIconVisibilityEffect(abi) { currentOrBuildStatusBarIconVisibilitySnapshot() }

        val stateHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                effect.before(param)
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateHook)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", stateHook)
    }

    @JvmStatic
    fun HideIconsHook(lpparam: PackageReadyParam) {
        ensureStatusBarIconVisibilityRuntimeState()

        val iconHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val slotName = param.getArgs()[0] as? String
                val snapshot = currentOrBuildStatusBarIconVisibilitySnapshot()
                if (checkSlot(slotName, snapshot)) {
                    param.getArgs()[1] = false
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.StatusBarIconControllerImpl", lpparam.classLoader, "setIconVisibility", String::class.java, Boolean::class.javaPrimitiveType!!, iconHook)
    }

    @JvmStatic
    fun HideIconsFromSystemManager(lpparam: PackageReadyParam) {
        ensureStatusBarIconVisibilityRuntimeState()

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.CommandQueue", lpparam.classLoader, "setIcon", String::class.java, "com.android.internal.statusbar.StatusBarIcon", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val slotName = param.getArg(0) as String
                val snapshot = currentOrBuildStatusBarIconVisibilitySnapshot()
                if (shouldHideSystemManagerIcon(slotName, snapshot)) {
                    XposedHelpers.setObjectField(param.getArg(1), "visible", false)
                }
            }
        })
    }

    @JvmStatic
    fun BatteryIndicatorHook(lpparam: PackageReadyParam) {
        SystemUIBatteryHooks.BatteryIndicatorHook(lpparam)
    }

    @JvmStatic
    fun StatusBarStyleBatteryIconHook(lpparam: PackageReadyParam) {
        SystemUIBatteryHooks.StatusBarStyleBatteryIconHook(lpparam)
    }

    @JvmStatic
    fun ForceClockUseSystemFontsHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.miui.clock.MiuiBaseClock", lpparam.classLoader, "updateViewsTextSize", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mTimeText = XposedHelpers.getObjectField(param.getThisObject(), "mTimeText") as TextView
                mTimeText.typeface = Typeface.DEFAULT
            }
        })
        ModuleHelper.findAndHookMethod("com.miui.clock.MiuiLeftTopLargeClock", lpparam.classLoader, "onLanguageChanged", String::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mTimeText = XposedHelpers.getObjectField(param.getThisObject(), "mCurrentDateLarge") as TextView
                mTimeText.typeface = Typeface.DEFAULT
            }
        })
    }

    @JvmStatic
    fun HideMobileNetworkIndicatorHook(lpparam: PackageReadyParam) {
        val singleMobileType = MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_single")
        val showOnWifi = MainModule.mPrefs.getBoolean("system_statusbar_mobiletype_show_wificonnected")
        val hideMobileActivity = object : MethodHook() {
            private var initAction = false
            override fun before(param: BeforeHookCallback) {
                if ("updateState" == param.getMember().name) {
                    return
                }
                val mState = XposedHelpers.getObjectField(param.getThisObject(), "mState")
                initAction = mState == null
            }
            override fun after(param: AfterHookCallback) {
                val updateStateMethod = "updateState" == param.getMember().name
                if (updateStateMethod || initAction) {
                    val opt = MainModule.mPrefs.getStringAsInt("system_mobiletypeicon", 1)
                    val hideIndicator = MainModule.mPrefs.getBoolean("system_networkindicator_mobile")
                    val mMobileType = XposedHelpers.getObjectField(param.getThisObject(), "mMobileType") as View
                    val dataConnected = XposedHelpers.getBooleanField(param.getArgs()[0], "dataConnected")
                    val wifiAvailable = XposedHelpers.getObjectField(param.getArgs()[0], "wifiAvailable") as Boolean
                    if (opt == 3) {
                        if (singleMobileType) {
                            val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                            mMobileTypeSingle.visibility = View.GONE
                        } else {
                            mMobileType.visibility = View.GONE
                        }
                    } else if (opt == 1) {
                        val viz = if (dataConnected && (!wifiAvailable || showOnWifi)) View.VISIBLE else View.GONE
                        if (singleMobileType) {
                            val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                            mMobileTypeSingle.visibility = viz
                        } else {
                            mMobileType.visibility = viz
                        }
                    } else if (opt == 2) {
                        val viz = if (!wifiAvailable || showOnWifi) View.VISIBLE else View.GONE
                        if (singleMobileType) {
                            val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                            mMobileTypeSingle.visibility = viz
                        } else {
                            mMobileType.visibility = viz
                        }
                    }
                    val mLeftInOut = XposedHelpers.getObjectField(param.getThisObject(), "mLeftInOut") as View
                    if (hideIndicator) {
                        val mRightInOut = XposedHelpers.getObjectField(param.getThisObject(), "mRightInOut") as View
                        mLeftInOut.visibility = View.GONE
                        mRightInOut.visibility = View.GONE
                    }
                    if (wifiAvailable && showOnWifi && (dataConnected || opt == 2)) {
                        if (!Build.IS_INTERNATIONAL_BUILD) {
                            val mSmallHd = XposedHelpers.getObjectField(param.getThisObject(), "mSmallHd") as View
                            mSmallHd.visibility = View.GONE
                        }
                        if (opt != 2) {
                            val viz = View.VISIBLE
                            if (singleMobileType) {
                                val mMobileTypeSingle = XposedHelpers.getObjectField(param.getThisObject(), "mMobileTypeSingle") as TextView
                                mMobileTypeSingle.visibility = viz
                            } else {
                                mMobileType.visibility = viz
                            }
                        }
                    }
                    if (!singleMobileType) {
                        val mMobileLeftContainer = XposedHelpers.getObjectField(param.getThisObject(), "mMobileLeftContainer") as View
                        mMobileLeftContainer.visibility = if (mMobileType.visibility == View.GONE && mLeftInOut.visibility == View.GONE) View.GONE else View.VISIBLE
                    }
                }
                if (!updateStateMethod) {
                    initAction = false
                }
            }
        }
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", hideMobileActivity)
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "updateState", hideMobileActivity)
    }

    @JvmStatic
    fun HidePrivacyIndicatorHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.privacy.MiuiPrivacyControllerImpl", lpparam.classLoader, "setStatus", Int::class.javaPrimitiveType!!, String::class.java, Bundle::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.returnAndSkip(null)
            }
        })
    }

}
