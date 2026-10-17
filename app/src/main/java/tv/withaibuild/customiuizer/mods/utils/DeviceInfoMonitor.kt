package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Battery / device-temperature status bar text icon monitor.
 *
 * Goals: screen off pauses the ticker; screen on resumes immediately; no
 * message is sent to the main thread when the computed text has not changed;
 * sysfs reads back off on repeated failures; all user preferences are captured
 * in an immutable snapshot that is refreshed when a relevant preference key
 * changes.
 *
 * activeContext is the SystemUI application context, captured in the hook and
 * released in [stopMonitoring]. It is used to register/unregister the screen receiver
 * and schedule monitor ticks. Lint cannot see the explicit ownership/receiver
 * lifecycle, so the static-Context warning is suppressed at the object level.
 */
@SuppressLint("StaticFieldLeak")
object DeviceInfoMonitor {

    private const val MONITOR_MESSAGE = 200021
    private const val UPDATE_MESSAGE = 100021
    private const val BASE_MONITOR_DELAY_MS = 2000L
    private const val MAX_MONITOR_DELAY_MS = 60000L

    private val NETWORK_SPEED_VIEW_CANDIDATES = listOf(
        "com.android.systemui.statusbar.views.NetworkSpeedView",
        "com.miui.systemui.statusbar.views.NetworkSpeedView"
    )

    private data class ConfigSnapshot(
        val showBatteryDetail: Boolean,
        val showDeviceTemp: Boolean,
        val dualRows: Boolean,
        val batteryAtRight: Boolean,
        val tempAtRight: Boolean,
        val batteryAtLeft: Boolean,
        val tempAtLeft: Boolean,
        val batteryInCharge: Boolean,
        val batteryTempDecimal: Boolean,
        val batteryFixCurrentRatio: Boolean,
        val batteryPositive: Boolean,
        val batterySingleRow: Boolean,
        val batteryReverseOrder: Boolean,
        val batteryHideUnit: Int,
        val deviceTempSingleRow: Boolean,
        val deviceTempReverseOrder: Boolean,
        val deviceTempHideUnit: Boolean,
        val batteryContentOpt: Int,
        val deviceTempContentOpt: Int
    ) {
        val hasDeviceInfoWork: Boolean get() = showBatteryDetail || showDeviceTemp

        /**
         * Icon types injected through StatusBarIconController. Dual-row right-side
         * views are created by DualRowsStatusbarHook instead, so they are not listed
         * here. Monitor enablement uses [hasDeviceInfoWork], not this list.
         */
        val controllerManagedIconTypes: List<Int> = buildList {
            if (batteryAtLeft || batteryAtRight) add(91)
            if (tempAtLeft || tempAtRight) add(92)
        }

        fun toFormatterConfig(): DeviceInfoConfig = DeviceInfoConfig(
            showBatteryDetail = showBatteryDetail,
            showDeviceTemp = showDeviceTemp,
            batteryInCharge = batteryInCharge,
            batteryTempDecimal = batteryTempDecimal,
            batteryFixCurrentRatio = batteryFixCurrentRatio,
            batteryPositive = batteryPositive,
            batterySingleRow = batterySingleRow,
            batteryReverseOrder = batteryReverseOrder,
            batteryHideUnit = batteryHideUnit,
            deviceTempSingleRow = deviceTempSingleRow,
            deviceTempReverseOrder = deviceTempReverseOrder,
            deviceTempHideUnit = deviceTempHideUnit,
            batteryContentOpt = batteryContentOpt,
            deviceTempContentOpt = deviceTempContentOpt
        )
    }

    private data class IconUpdate(
        val type: Int,
        val show: Boolean,
        val text: String,
        val generation: Long
    )

    private data class DeviceData(
        val batteryShow: Boolean,
        val batteryText: String,
        val tempShow: Boolean,
        val tempText: String
    )

    private val firstLifecycleLog = AtomicBoolean(false)
    private val firstConstructorLog = AtomicBoolean(false)
    private val firstIconGraphLog = AtomicBoolean(false)
    private val firstTickLog = AtomicBoolean(false)
    private val firstType92ViewLog = AtomicBoolean(false)
    private val firstType92SetIconLog = AtomicBoolean(false)

    private val monitorState = DeviceInfoMonitorState()

    private val monitorLock = Any()

    @Volatile
    private var config: ConfigSnapshot? = null

    private var activeContext: Context? = null
    private var activeMainHandler: Handler? = null
    private var activeBgHandler: Handler? = null
    private var screenReceiver: BroadcastReceiver? = null

    private var chargeUtilsClass: Class<*>? = null
    private var lpClassLoader: ClassLoader? = null

    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key == null ||
                key.startsWith("system_statusbar_batterytempandcurrent") ||
                key.startsWith("system_statusbar_showdevicetemperature")
            ) {
                onConfigMayHaveChanged()
            }
        }
    }

    @JvmStatic
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun hook(lpparam: PackageReadyParam, mPrefs: PrefMap) {
        ModuleHelper.observePreferenceChange(preferenceObserver)

        val cfg = buildConfig(mPrefs)
        config = cfg
        lpClassLoader = lpparam.classLoader
        if (firstLifecycleLog.compareAndSet(false, true)) {
            XposedHelpers.log(
                "DeviceInfoMonitor: DEVICE_INFO_FEATURE_INSTALLED work=${cfg.hasDeviceInfoWork} " +
                    "showTemp=${cfg.showDeviceTemp} showBattery=${cfg.showBatteryDetail} " +
                    "dualRows=${cfg.dualRows} tempAtRight=${cfg.tempAtRight} tempAtLeft=${cfg.tempAtLeft} " +
                    "controllerTypes=${cfg.controllerManagedIconTypes}"
            )
        }
        if (!cfg.hasDeviceInfoWork) return

        val networkSpeedViewClass = resolveNetworkSpeedViewClassName(lpparam.classLoader)
        if (networkSpeedViewClass == null) {
            XposedHelpers.log("DeviceInfoMonitor: NetworkSpeedView not available, skipping icon slots")
        } else {
            if (cfg.controllerManagedIconTypes.isNotEmpty()) {
                hookIconSlots(lpparam, cfg)
            }
            hookNetworkSpeedView(lpparam, networkSpeedViewClass)
            XposedHelpers.log("DeviceInfoMonitor: NETWORK_SPEED_VIEW_CLASS=$networkSpeedViewClass")
        }

        if (cfg.showBatteryDetail && cfg.batteryInCharge) {
            chargeUtilsClass = DeviceInfoChargeVisibility.resolveChargeUtilsClass(lpparam.classLoader)
        }

        // Icon slot placement is a StatusBarIconController concern and is skipped when DualRows
        // owns the right-side views. The ticker must still run whenever battery or temperature
        // work is enabled.
        hookMonitor(lpparam)
        XposedHelpers.log("DeviceInfoMonitor: MONITOR_HOOK_INSTALLED")
    }

    /**
     * Resolves the actual NetworkSpeedView class name for this ROM, trying the known
     * candidates in order. Returns null if neither exists.
     *
     * The [probe] parameter is normally [XposedHelpers.findClassIfExists]; it is exposed so
     * unit tests can verify the resolution strategy without requiring a real ROM class loader.
     */
    @JvmStatic
    internal fun resolveNetworkSpeedViewClassName(
        classLoader: ClassLoader,
        probe: (String, ClassLoader) -> Class<*>? = { name, loader -> XposedHelpers.findClassIfExists(name, loader) }
    ): String? {
        for (name in NETWORK_SPEED_VIEW_CANDIDATES) {
            val found = try {
                probe(name, classLoader) != null
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                false
            }
            if (found) return name
        }
        return null
    }

    private fun buildConfig(mPrefs: PrefMap): ConfigSnapshot {
        val showBatteryDetail = mPrefs.getBoolean("system_statusbar_batterytempandcurrent")
        val showDeviceTemp = mPrefs.getBoolean("system_statusbar_showdevicetemperature")
        val placement = resolveDeviceInfoPlacement(
            showBatteryDetail = showBatteryDetail,
            showDeviceTemp = showDeviceTemp,
            dualRows = mPrefs.getBoolean("system_statusbar_dualrows"),
            batteryAtRightPref = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright"),
            tempAtRightPref = mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright"),
        )
        return ConfigSnapshot(
            showBatteryDetail = showBatteryDetail,
            showDeviceTemp = showDeviceTemp,
            dualRows = placement.dualRows,
            batteryAtRight = placement.batteryAtRight,
            tempAtRight = placement.tempAtRight,
            batteryAtLeft = placement.batteryAtLeft,
            tempAtLeft = placement.tempAtLeft,
            batteryInCharge = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_incharge"),
            batteryTempDecimal = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_temp_decimal"),
            batteryFixCurrentRatio = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_fixcurrentratio"),
            batteryPositive = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_positive"),
            batterySingleRow = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_singlerow"),
            batteryReverseOrder = mPrefs.getBoolean("system_statusbar_batterytempandcurrent_reverseorder"),
            batteryHideUnit = mPrefs.getStringAsInt("system_statusbar_batterytempandcurrent_hideunit", 0),
            deviceTempSingleRow = mPrefs.getBoolean("system_statusbar_showdevicetemperature_singlerow"),
            deviceTempReverseOrder = mPrefs.getBoolean("system_statusbar_showdevicetemperature_reverseorder"),
            deviceTempHideUnit = mPrefs.getBoolean("system_statusbar_showdevicetemperature_hideunit"),
            batteryContentOpt = mPrefs.getStringAsInt("system_statusbar_batterytempandcurrent_content", 1),
            deviceTempContentOpt = mPrefs.getStringAsInt("system_statusbar_showdevicetemperature_content", 1)
        )
    }

    private fun onConfigMayHaveChanged() {
        val mPrefs = MainModule.mPrefs ?: return
        val newConfig = buildConfig(mPrefs)
        config = newConfig

        if (!newConfig.hasDeviceInfoWork) {
            stopMonitoring()
            return
        }

        if (newConfig.showBatteryDetail && newConfig.batteryInCharge && chargeUtilsClass == null) {
            chargeUtilsClass = XposedHelpers.findClassIfExists(
                "com.miui.charge.ChargeUtils",
                lpClassLoader
            )
        }

        synchronized(monitorLock) {
            activeBgHandler?.removeMessages(MONITOR_MESSAGE)
            activeBgHandler?.removeMessages(UPDATE_MESSAGE)
            monitorState.screenOn = true
            activeBgHandler?.sendEmptyMessage(MONITOR_MESSAGE)
        }
    }

    private fun hookIconSlots(lpparam: PackageReadyParam, cfg: ConfigSnapshot) {
        val StatusBarIconHolder = XposedHelpers.findClass(
            "com.android.systemui.statusbar.phone.StatusBarIconHolder",
            lpparam.classLoader
        )

        ModuleHelper.hookAllConstructors(
            "com.android.systemui.statusbar.policy.NetworkSpeedController",
            lpparam.classLoader,
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val iconController = XposedHelpers.getObjectField(
                        param.getThisObject(),
                        "mStatusBarIconController"
                    ) ?: return
                    val iconList = XposedHelpers.getObjectField(iconController, "mStatusBarIconList") ?: return
                    if (firstIconGraphLog.compareAndSet(false, true)) {
                        XposedHelpers.log(
                            "DeviceInfoMonitor: ICON_CONTROLLER_CLASS=${iconController.javaClass.name} " +
                                "ICON_LIST_CLASS=${iconList.javaClass.name} SET_ICON_OWNER=${iconController.javaClass.name}"
                        )
                    }
                    for (iconType in cfg.controllerManagedIconTypes) {
                        val slot = SystemUIStatusBarHooks.getSlotNameByType(iconType)
                        var iconHolder = XposedHelpers.callMethod(iconList, "getIconHolder", 0, slot)
                        if (iconHolder == null) {
                            iconHolder = XposedHelpers.newInstance(StatusBarIconHolder)
                            XposedHelpers.setObjectField(iconHolder, "mType", iconType)
                            XposedHelpers.callMethod(iconController, "setIcon", slot, iconHolder)
                            if (iconType == 92 && firstType92SetIconLog.compareAndSet(false, true)) {
                                XposedHelpers.log("DeviceInfoMonitor: TYPE92_SETICON_REACHED slot=$slot")
                            }
                        }
                    }
                }
            }
        )

        ModuleHelper.hookAllMethods(
            "com.android.systemui.statusbar.phone.StatusBarIconController\$IconManager",
            lpparam.classLoader,
            "addHolder",
            object : MethodHook() {
                override fun before(param: BeforeHookCallback) = interceptAddHolder(param)
            }
        )
    }

    private fun interceptAddHolder(param: BeforeHookCallback) {
        // We only care about our custom icon types; for everything else the ROM's original
        // implementation is the right path.
        val args = param.getArgs()
        if (args.isEmpty()) return

        val iconHolderArg = args.find { it?.javaClass?.simpleName?.contains("StatusBarIconHolder") == true } ?: return
        val type = try {
            XposedHelpers.getIntField(iconHolderArg, "mType")
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            // We cannot determine the type; let the original method handle it.
            return
        }
        if (type != 91 && type != 92) return

        // Confirmed custom type. The whole custom handling path is in a fatal-aware boundary;
        // any non-fatal failure returns null so SystemUI does not crash on an unknown icon type.
        try {
            val thisObj = param.getThisObject() ?: run {
                param.returnAndSkip(null)
                return
            }

            val group = findIconManagerGroup(thisObj) ?: run {
                param.returnAndSkip(null)
                return
            }

            // Find the insertion index. The first argument is the index in every known signature;
            // if it is not an int, default to 0 and clamp it below.
            val requestedIndex = (args[0] as? Int) ?: 0

            // If a custom icon with the same type already lives in this group, reuse it.
            for (j in 0 until group.childCount) {
                val child = group.getChildAt(j)
                if (child != null && child.getTag(SystemUIStatusBarHooks.textIconTagId) == type) {
                    param.returnAndSkip(child)
                    return
                }
            }

            val context = findIconManagerContext(thisObj, group) ?: run {
                param.returnAndSkip(null)
                return
            }

            val lp = try {
                XposedHelpers.callMethod(thisObj, "onCreateLayoutParams") as? LinearLayout.LayoutParams
                    ?: LinearLayout.LayoutParams(-2, -2)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                LinearLayout.LayoutParams(-2, -2)
            }

            val iconView = try {
                SystemUIStatusBarHooks.createStatusbarTextIcon(context, lp, type, true)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                XposedHelpers.log("DeviceInfoMonitor: failed to create custom icon view for type $type: ${t.javaClass.simpleName}")
                param.returnAndSkip(null)
                return
            }

            val safeIndex = StatusbarViewMaths.clampStatusIconInsertIndex(requestedIndex, group.childCount)
            group.addView(iconView, safeIndex)
            SystemUIStatusBarHooks.registerStatusbarTextIcon(iconView)
            if (type == 92 && firstType92ViewLog.compareAndSet(false, true)) {
                XposedHelpers.log("DeviceInfoMonitor: TYPE92_VIEW_CREATED fromController=true")
            }

            val classLoader = lpClassLoader
            if (classLoader != null) {
                @Suppress("UNUSED_VARIABLE")
                val handle = CustomTextIconTintRoute.register(iconView, classLoader, "left")
            } else {
                XposedHelpers.log("DeviceInfoMonitor: cannot register dark receiver, class loader not captured")
            }

            param.returnAndSkip(iconView)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            XposedHelpers.log("DeviceInfoMonitor: addHolder custom path failed: ${t.javaClass.simpleName}")
            param.returnAndSkip(null)
        }
    }

    private fun findIconManagerGroup(iconManager: Any): ViewGroup? {
        return FieldCandidateResolver.resolve(iconManager, listOf("mGroup", "mIcons", "iconGroup"))
    }

    private fun findIconManagerContext(iconManager: Any, fallbackGroup: ViewGroup): Context? {
        val context = try {
            XposedHelpers.getObjectField(iconManager, "mContext")
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
        return (context as? Context) ?: fallbackGroup.context
    }

    private fun hookNetworkSpeedView(lpparam: PackageReadyParam, className: String) {
        ModuleHelper.findAndHookMethod(
            className,
            lpparam.classLoader,
            "getSlot",
            object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    val nsView = param.getThisObject() as? View ?: return
                    val tagData = nsView.getTag(SystemUIStatusBarHooks.textIconTagId)
                    if (tagData != null) {
                        param.returnAndSkip(SystemUIStatusBarHooks.getSlotNameByType(tagData as Int))
                    }
                }
            }
        )
    }

    /**
     * Installs the ticker.
     *
     * Deliberately takes no [ConfigSnapshot]. It used to capture the one built at hook time
     * in the constructor hook's closure and hand it to every tick for the life of the
     * process, so [onConfigMayHaveChanged] refreshed a `config` field that the ticker never
     * read: changing the temperature format, the hide-unit option or the in-charge
     * condition had no effect until SystemUI restarted, while the preference screen behaved as
     * though it had. Each tick now takes one snapshot of the volatile field and uses that.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun hookMonitor(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors(
            "com.android.systemui.statusbar.policy.NetworkSpeedController",
            lpparam.classLoader,
            object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val args = param.getArgs()
                    if (firstConstructorLog.compareAndSet(false, true)) {
                        val argClasses = args.mapIndexed { index, value ->
                            "ARG${index}_CLASS=${value?.javaClass?.name ?: "null"}"
                        }
                        XposedHelpers.log(
                            "DeviceInfoMonitor: NETWORK_SPEED_CONTROLLER_CONSTRUCTOR " +
                                argClasses.joinToString(" ")
                        )
                    }
                    val mContext = resolveNetworkSpeedControllerContext(args) ?: return
                    val bgHandler = try {
                        XposedHelpers.getObjectField(param.getThisObject(), "mBgHandler") as? Handler
                    } catch (oom: OutOfMemoryError) {
                        throw oom
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        null
                    }
                    val looper = resolveNetworkSpeedControllerLooper(args, bgHandler) ?: return
                    XposedHelpers.log("DeviceInfoMonitor: MONITOR_CONSTRUCTOR_REACHED")

                    synchronized(monitorLock) {
                        activeBgHandler?.removeMessages(MONITOR_MESSAGE)
                        activeBgHandler?.removeMessages(UPDATE_MESSAGE)
                        stopScreenReceiverLocked()

                        val handlerId = monitorState.startNewGeneration()
                        monitorState.screenOn = true
                        monitorState.resetFailCount()

                        activeMainHandler = object : Handler(Looper.getMainLooper()) {
                            val myId = handlerId
                            override fun handleMessage(msg: Message) = ModuleHelper.guarded {
                                if (!monitorState.isActiveMain(myId)) {
                                    removeMessages(UPDATE_MESSAGE)
                                    return@guarded
                                }
                                if (msg.what == UPDATE_MESSAGE) {
                                    val update = msg.obj as? IconUpdate ?: return@guarded
                                    if (update.generation != myId) {
                                        removeMessages(UPDATE_MESSAGE)
                                        return@guarded
                                    }
                                    monitorState.commitPublished(myId, update.type, update.show, update.text)
                                    SystemUIStatusBarHooks.updateStatusbarTextIcons(update.type, update.show, update.text)
                                }
                            }
                        }

                        activeBgHandler = object : Handler(looper) {
                            val myId = handlerId
                            override fun handleMessage(msg: Message) = ModuleHelper.guarded {
                                if (!monitorState.isActiveBg(myId)) {
                                    removeMessages(MONITOR_MESSAGE)
                                    removeMessages(UPDATE_MESSAGE)
                                    return@guarded
                                }
                                if (msg.what == MONITOR_MESSAGE) {
                                    // One read of the volatile per tick, so the whole tick
                                    // works from a single consistent snapshot even if a
                                    // preference changes halfway through it.
                                    val current = config ?: return@guarded
                                    doMonitorTick(mContext, current, myId)
                                }
                            }
                        }

                        activeContext = mContext
                        startScreenReceiverLocked(mContext)
                    }

                    activeBgHandler?.removeMessages(MONITOR_MESSAGE)
                    activeBgHandler?.sendEmptyMessage(MONITOR_MESSAGE)
                    XposedHelpers.log("DeviceInfoMonitor: MONITOR_HANDLER_STARTED")
                }
            }
        )
    }

    private fun startScreenReceiverLocked(context: Context) {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        monitorState.screenOn = true
                        activeBgHandler?.removeMessages(MONITOR_MESSAGE)
                        activeBgHandler?.sendEmptyMessage(MONITOR_MESSAGE)
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        monitorState.screenOn = false
                        activeBgHandler?.removeMessages(MONITOR_MESSAGE)
                        activeBgHandler?.removeMessages(UPDATE_MESSAGE)
                    }
                }
            }
        }.also { receiver ->
            context.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                Context.RECEIVER_NOT_EXPORTED
            )
        }
    }

    private fun stopScreenReceiverLocked() {
        val receiver = screenReceiver ?: return
        screenReceiver = null
        try {
            activeContext?.unregisterReceiver(receiver)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
        }
    }

    private fun stopMonitoring() {
        synchronized(monitorLock) {
            activeBgHandler?.removeMessages(MONITOR_MESSAGE)
            activeBgHandler?.removeMessages(UPDATE_MESSAGE)
            stopScreenReceiverLocked()
            activeMainHandler = null
            activeBgHandler = null
            activeContext = null
            monitorState.stop()
            monitorState.resetFailCount()
            CustomTextIconTintRoute.releaseAll()
        }
    }

    /**
     * One monitor pass on the NetworkSpeedController background thread.
     *
     * The next tick is scheduled from `finally`: a sysfs read that returns an unexpected shape
     * must degrade to "no reading this round", never to a ticker that stops for the rest of the
     * process lifetime.
     */
    private fun doMonitorTick(mContext: Context, cfg: ConfigSnapshot, handlerId: Long) {
        if (!monitorState.screenOn) return
        if (!monitorState.isActiveBg(handlerId)) return

        val powerMgr = mContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerMgr != null && !powerMgr.isInteractive) {
            monitorState.screenOn = false
            return
        }

        try {
            val data = readDeviceData(mContext, cfg, handlerId)
            if (data != null && monitorState.isActiveBg(handlerId)) {
                if (firstTickLog.compareAndSet(false, true)) {
                    XposedHelpers.log(
                        "DeviceInfoMonitor: MONITOR_TICK_REACHED batteryShow=${data.batteryShow} " +
                            "tempShow=${data.tempShow} batteryText=${data.batteryText} tempText=${data.tempText}"
                    )
                }
                publishReadings(cfg, data, handlerId)
            }
        } finally {
            if (monitorState.isActiveBg(handlerId)) {
                scheduleNextTick(handlerId)
            }
        }
    }

    private fun publishReadings(cfg: ConfigSnapshot, data: DeviceData, handlerId: Long) {
        if (!monitorState.isActiveBg(handlerId) || !monitorState.isActiveMain(handlerId)) return

        val handler = activeMainHandler ?: return

        if (cfg.showBatteryDetail &&
            monitorState.shouldPublish(handlerId, 91, data.batteryShow, data.batteryText)
        ) {
            handler.sendMessage(
                handler.obtainMessage(UPDATE_MESSAGE, IconUpdate(91, data.batteryShow, data.batteryText, handlerId))
            )
        }
        if (cfg.showDeviceTemp &&
            monitorState.shouldPublish(handlerId, 92, data.tempShow, data.tempText)
        ) {
            handler.sendMessage(
                handler.obtainMessage(UPDATE_MESSAGE, IconUpdate(92, data.tempShow, data.tempText, handlerId))
            )
        }
    }

    private fun scheduleNextTick(handlerId: Long) {
        if (!monitorState.screenOn) return
        if (!monitorState.isActiveBg(handlerId)) return
        val delay = monitorState.calculateDelay(BASE_MONITOR_DELAY_MS, MAX_MONITOR_DELAY_MS)
        activeBgHandler?.sendEmptyMessageDelayed(MONITOR_MESSAGE, delay)
    }

    private fun readDeviceData(mContext: Context, cfg: ConfigSnapshot, handlerId: Long): DeviceData? {
        if (!monitorState.isActiveBg(handlerId)) return null

        val shouldShowBattery = if (cfg.showBatteryDetail) shouldShowBatteryInfo(cfg) else false
        val shouldShowTemp = cfg.showDeviceTemp
        val needBatteryTemp = shouldShowTemp && DeviceInfoFormatter.needsBatteryTemperature(cfg.deviceTempContentOpt)
        val needCpuTemp = shouldShowTemp && DeviceInfoFormatter.needsCpuTemperature(cfg.deviceTempContentOpt)

        if (!shouldShowBattery && !shouldShowTemp) return DeviceData(false, "", false, "")

        val props: Properties? = if (shouldShowBattery || needBatteryTemp) DeviceInfoFormatter.readBatteryProps() else null
        val cpuProps: String? = if (needCpuTemp) DeviceInfoFormatter.readCpuTemp(ModuleHelper.getCPUThermalId()) else null

        val batteryFailed = shouldShowBattery && props == null
        val tempFailed = when {
            !shouldShowTemp -> false
            needBatteryTemp && needCpuTemp -> props == null && cpuProps.isNullOrEmpty()
            needBatteryTemp -> props == null
            needCpuTemp -> cpuProps.isNullOrEmpty()
            else -> false
        }
        val anyFailed = batteryFailed || tempFailed

        if (anyFailed) {
            monitorState.bumpFailCount()
            if (props == null && cpuProps.isNullOrEmpty()) return null
        } else {
            monitorState.resetFailCount()
        }

        val fmtCfg = cfg.toFormatterConfig()
        val batteryText = if (shouldShowBattery && props != null) DeviceInfoFormatter.formatBatteryInfo(fmtCfg, props) else ""
        val deviceText = if (shouldShowTemp) DeviceInfoFormatter.formatDeviceInfo(fmtCfg, props, cpuProps) else ""

        return DeviceData(
            batteryShow = shouldShowBattery,
            batteryText = batteryText,
            tempShow = shouldShowTemp && deviceText.isNotEmpty(),
            tempText = deviceText
        )
    }

    private fun shouldShowBatteryInfo(cfg: ConfigSnapshot): Boolean {
        if (!cfg.batteryInCharge) return true
        val chargeUtils = chargeUtilsClass ?: return true
        val batteryStatus = ModuleHelper.getStaticObjectFieldSilently(chargeUtils, "sBatteryStatus")
        // Keep the resolved class across ticks. Clearing it on NOT_EXIST would fail-open
        // on the next pass (`chargeUtilsClass ?: return true`) and show the reading.
        return DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly(
            batteryStatus,
            ModuleHelper.NOT_EXIST_SYMBOL,
        )
    }
}

internal object DeviceInfoChargeVisibility {
    private val CHARGE_UTILS_CLASSES = arrayOf(
        "com.miui.charge.ChargeUtils",
        "com.android.keyguard.charge.ChargeUtils",
    )

    fun resolveChargeUtilsClass(
        classLoader: ClassLoader,
        find: (String, ClassLoader) -> Class<*>? = { name, loader ->
            XposedHelpers.findClassIfExists(name, loader)
        },
    ): Class<*>? {
        for (name in CHARGE_UTILS_CLASSES) {
            val found = find(name, classLoader)
            if (found != null) return found
        }
        return null
    }

    /**
     * Upstream v24.10.12: when "show when charging only" is on and ChargeUtils was found,
     * a missing `sBatteryStatus` hides the reading instead of showing it unconditionally.
     * A missing ChargeUtils class still skips the in-charge filter (show).
     */
    fun shouldShowWhenInChargeOnly(
        batteryStatus: Any?,
        notExistSymbol: Any,
    ): Boolean {
        if (batteryStatus == null || batteryStatus === notExistSymbol) return false
        return try {
            readIsCharging(batteryStatus) ?: false
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    fun shouldShowWhenInChargeOnly(
        batteryStatus: Any?,
        notExistSymbol: Any,
        isCharging: (Any) -> Boolean?,
    ): Boolean {
        if (batteryStatus == null || batteryStatus === notExistSymbol) return false
        return try {
            isCharging(batteryStatus) ?: false
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    private fun readIsCharging(status: Any): Boolean? =
        XposedHelpers.callMethod(status, "isCharging") as? Boolean
}

internal data class DeviceInfoPlacement(
    val hasDeviceInfoWork: Boolean,
    val dualRows: Boolean,
    val batteryAtRight: Boolean,
    val tempAtRight: Boolean,
    val batteryAtLeft: Boolean,
    val tempAtLeft: Boolean,
) {
    val controllerManagedIconTypes: List<Int> = buildList {
        if (batteryAtLeft || batteryAtRight) add(91)
        if (tempAtLeft || tempAtRight) add(92)
    }
}

internal fun resolveDeviceInfoPlacement(
    showBatteryDetail: Boolean,
    showDeviceTemp: Boolean,
    dualRows: Boolean,
    batteryAtRightPref: Boolean,
    tempAtRightPref: Boolean,
): DeviceInfoPlacement {
    // Dual-row owns both left and right custom text views itself. Controller
    // slots are only the single-row StatusBarIconController path.
    val controllerLeftRight = !dualRows
    return DeviceInfoPlacement(
        hasDeviceInfoWork = showBatteryDetail || showDeviceTemp,
        dualRows = dualRows,
        batteryAtRight = showBatteryDetail && controllerLeftRight && batteryAtRightPref,
        tempAtRight = showDeviceTemp && controllerLeftRight && tempAtRightPref,
        batteryAtLeft = showBatteryDetail && controllerLeftRight && !batteryAtRightPref,
        tempAtLeft = showDeviceTemp && controllerLeftRight && !tempAtRightPref,
    )
}

internal fun findUniqueInstance(args: Array<Any?>, matcher: (Any) -> Boolean): Any? {
    var found: Any? = null
    for (arg in args) {
        if (arg != null && matcher(arg)) {
            if (found != null) return null
            found = arg
        }
    }
    return found
}

internal fun resolveNetworkSpeedControllerContext(args: Array<Any?>): Context? =
    findUniqueInstance(args) { it is Context } as? Context

internal fun resolveNetworkSpeedControllerLooper(args: Array<Any?>, bgHandler: Handler?): Looper? {
    (findUniqueInstance(args) { it is Looper } as? Looper)?.let { return it }
    (findUniqueInstance(args) { it is Handler } as? Handler)?.looper?.let { return it }
    return bgHandler?.looper
}
