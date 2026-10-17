package tv.withaibuild.customiuizer.mods.duostatusbar

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.SubscriptionInfo
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedInterface
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import tv.withaibuild.customiuizer.mods.utils.CallbackGuard
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** No receiver, observer, native runtime, service, polling, or private changes to the stock state. */
internal class DuoStatusBarHooks(private val abi: DuoAbi, private val config: DuoConfig) {
    private val network = DuoNetworkState()
    private val audio = DuoAudioState()
    private val audioAbi = if (config.showAudio) DuoAudioAbi.resolve(abi, config.bluetoothBatteryColor) else null
    private val cellularType = if (config.networkFallback && config.cellularStyle == 2) DuoCellularType.resolve(abi) else null
    private val handler = Handler(Looper.getMainLooper())
    private val queued = AtomicBoolean(false)
    private val bindings = arrayOfNulls<WeakReference<DuoBinding>>(4)
    @Volatile private var failed = false
    @Volatile private var battery = -1
    @Volatile private var bindingCount = 0
    @Volatile private var audioFailed = false
    @Volatile private var batteryHintsFailed = false
    @Volatile private var audioPolicy: WeakReference<Any>? = null
    private var loggedHosts = 0
    private var scheduledAudioExpiry = 0L
    private val audioExpiry = Runnable {
        CallbackGuard.guarded {
            scheduledAudioExpiry = 0L
            if (!failed) protectAudio {
                if (audio.expire(SystemClock.uptimeMillis())) queueRefresh()
                scheduleAudioExpiry()
            }
        }
    }
    private val refresh = Runnable {
        CallbackGuard.guarded {
            queued.set(false)
            protect {
                for (reference in bindings) {
                    val binding = reference?.get() ?: continue
                    if (failed) binding.restore()
                    else reconcile(binding)
                }
                scheduleAudioExpiry()
            }
        }
    }

    private inline fun protect(block: () -> Unit) {
        try { block() } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            fail(t)
        }
    }

    private fun fail(t: Throwable) {
        if (failed) return
        failed = true
        XposedHelpers.log(t)
        if (Looper.myLooper() == handler.looper) {
            for (reference in bindings) CallbackGuard.guarded { reference?.get()?.restore() }
            scheduleAudioExpiry()
        } else queueRefresh()
    }

    private fun queueRefresh() {
        if (bindingCount == 0) return
        if (queued.compareAndSet(false, true) && !handler.post(refresh)) queued.set(false)
    }

    /** One expiry per connection hint, never a periodic task or animation loop. Main thread only. */
    private fun scheduleAudioExpiry() {
        val due = if (failed || audioFailed || bindingCount == 0) 0L else audio.expiresAt
        if (due == scheduledAudioExpiry) return
        handler.removeCallbacks(audioExpiry)
        scheduledAudioExpiry = due
        if (due > 0L && !handler.postAtTime(audioExpiry, due)) {
            scheduledAudioExpiry = 0L
            protectAudio { error("Duo: audio hint expiry unavailable") }
        }
    }

    private fun reconcile(binding: DuoBinding) {
        val bits = battery
        binding.reconcile(if (bits < 0) -1 else bits and 127, bits and 128 != 0,
            bits and 256 != 0, network.snapshot, audio.snapshot)
    }

    /** Layout events only. The four weak bindings share the source row, per display, before drawing. */
    private fun refreshGeometry() = protect {
        if (failed) return@protect
        for (reference in bindings) {
            val binding = reference?.get() ?: continue
            if (binding.primaryHost) {
                binding.shareHeight(0)
                continue
            }
            val source = duoGeometrySource(bindings, binding.displayId, binding.geometrySource)
            binding.geometrySource = source
            binding.shareHeight(source?.get()?.view?.rowHeightPx ?: 0)
        }
    }

    private inline fun protectAudio(block: () -> Unit) {
        if (audioFailed) return
        try { block() } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            audioFailed = true
            audioPolicy = null
            if (audio.clear()) queueRefresh()
            XposedHelpers.log("Duo audio indicators disabled: ${t.message}")
        }
    }

    private fun readBluetooth(controller: Any, notify: Boolean) = protectAudio {
        val fields = audioAbi ?: return@protectAudio
        if (audio.bluetooth(fields.enabled.getBoolean(controller), fields.connection.getInt(controller) == 2,
                fields.audioOnly.getBoolean(controller), fields.active.getBoolean(controller),
                SystemClock.uptimeMillis(), notify && bindingCount > 0)) queueRefresh()
        if (DuoAudioState.batteryStep(audio.snapshot) != 0) readBluetoothBattery(controller, false)
    }

    private fun readWired(policy: Any, notify: Boolean) = protectAudio {
        val fields = audioAbi ?: return@protectAudio
        if (audioPolicy?.get() !== policy) return@protectAudio // retired native receiver
        if (audio.wired(!(fields.headsetMap.get(policy) as Map<*, *>).isEmpty(),
                SystemClock.uptimeMillis(), notify && bindingCount > 0)) queueRefresh()
    }

    private fun readBluetoothBattery(controller: Any, fresh: Boolean) {
        if (failed || audioFailed || batteryHintsFailed) return
        val fields = audioAbi?.battery ?: return
        try {
            val devices = fields.devices.get(controller) as Collection<*>
            val singleDevice = synchronized(devices) { devices.size == 1 }
            val step = if (fresh) fields.level.getInt(controller) else DuoAudioState.batteryStep(audio.snapshot)
            if (audio.bluetoothBattery(step, singleDevice)) queueRefresh()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            batteryHintsFailed = true
            if (audio.bluetoothBattery(-1, false)) queueRefresh()
            XposedHelpers.log("Duo Bluetooth battery colors disabled: ${t.message}")
        }
    }

    private fun readBattery(controller: Any) {
        val rawLevel = abi.batteryLevel.getInt(controller)
        val next = if (rawLevel !in 0..100) -1 else rawLevel or
            (if (abi.charging.getBoolean(controller)) 128 else 0) or
            (if (abi.saver.getBoolean(controller)) 256 else 0)
        if (next == battery) return
        battery = next
        queueRefresh()
    }

    private fun readWifi(controller: Any) {
        val state = abi.wifiState.get(controller) ?: return
        if (network.wifi(abi.wifiEnabled.getBoolean(state), abi.wifiConnected.getBoolean(state),
                abi.wifiLevel.getInt(state), abi.wifiInternet.getInt(state) == 1)) queueRefresh()
    }

    private fun readMobile(controller: Any) {
        val state = abi.mobileState.get(controller) ?: return
        val info = abi.subscription.get(controller) as SubscriptionInfo
        if (network.mobile(info.simSlotIndex, info.subscriptionId, abi.mobileLevel.getInt(state),
                abi.mobileConnected.getBoolean(state), abi.mobileDataSim.getBoolean(state),
                abi.mobileMaxLevel, cellularType?.read(state) ?: 0)) queueRefresh()
    }

    private fun readSubscriptions(subscriptions: List<*>) {
        var first = -1
        var second = -1
        var supported = subscriptions.size <= 2
        for (item in subscriptions) {
            val info = item as SubscriptionInfo
            when (info.simSlotIndex) {
                0 -> { if (first >= 0) supported = false; first = info.subscriptionId }
                1 -> { if (second >= 0) supported = false; second = info.subscriptionId }
                else -> supported = false
            }
        }
        if (network.subscriptions(first, second, supported)) queueRefresh()
    }

    /** Called only on view attachment; Dependency/class discovery stays out of recurring callbacks. */
    private fun seed() {
        val loader = abi.batteryView.classLoader
        val battery = ModuleHelper.getDepInstance(loader, "com.android.systemui.statusbar.policy.BatteryController")
            ?: error("Duo: BatteryController unavailable")
        val controller = ModuleHelper.getDepInstance(loader, "com.android.systemui.statusbar.connectivity.NetworkController")
            ?: error("Duo: NetworkController unavailable")
        require(abi.batteryController.isInstance(battery) && abi.networkController.isInstance(controller))
        readBattery(battery)
        readWifi(abi.networkWifi.get(controller))
        val mobiles = abi.networkMobile.get(controller) as SparseArray<*>
        val subs = ArrayList<SubscriptionInfo>(2)
        for (i in 0 until mobiles.size()) subs.add(abi.subscription.get(mobiles.valueAt(i)) as SubscriptionInfo)
        readSubscriptions(subs)
        for (i in 0 until mobiles.size()) readMobile(mobiles.valueAt(i))
        network.airplane(abi.airplane.getBoolean(controller))
        if (audioAbi != null && !audioFailed) protectAudio {
            ModuleHelper.getDepInstance(loader, "com.android.systemui.statusbar.policy.BluetoothController")
                ?.let { readBluetooth(it, false) }
            audioPolicy?.get()?.let { readWired(it, false) }
        }
    }

    private fun root(view: View): ViewGroup? {
        var current = view.parent
        while (current is ViewGroup) {
            if (abi.statusBar.isInstance(current) || abi.keyguard.isInstance(current) ||
                abi.controlCenter?.isInstance(current) == true ||
                abi.transition?.isInstance(current) == true) return current
            current = current.parent
        }
        return null
    }

    private fun child(owner: ViewGroup): DuoStatusBarView? {
        for (i in 0 until owner.childCount) {
            val candidate = owner.getChildAt(i)
            if (candidate is DuoStatusBarView) return candidate
        }
        return null
    }

    private fun attach(owner: ViewGroup) {
        if (failed || child(owner) != null) return
        val root = root(owner) ?: return // Unrelated battery views stay native.
        val slot = bindings.indexOfFirst { it?.get() == null }
        if (slot < 0) return // bounded multi-display capacity; excess hosts stay native
        seed()
        val view = DuoStatusBarView(owner.context, config, ::fail, ::refreshGeometry)
        view.visibility = View.GONE
        val binding = DuoBinding(owner, root, view, abi)
        view.binding = binding
        bindings[slot] = WeakReference(binding)
        bindingCount++
        owner.addView(view, ViewGroup.LayoutParams(view.iconSizePx, ViewGroup.LayoutParams.MATCH_PARENT))
        reconcile(binding)
        refreshGeometry()
        val host = when {
            abi.statusBar.isInstance(root) -> 1
            abi.keyguard.isInstance(root) -> 2
            abi.controlCenter?.isInstance(root) == true -> 4
            else -> 8
        }
        if (loggedHosts and host == 0) {
            loggedHosts = loggedHosts or host
            XposedHelpers.log("Duo host ready: ${root.javaClass.simpleName}, ${view.iconSizePx}px, auto=${config.autoSize}")
        }
    }

    private fun detach(owner: ViewGroup) {
        val binding = child(owner)?.binding ?: return
        for (i in bindings.indices) if (bindings[i]?.get() === binding) bindings[i] = null
        bindingCount = bindings.count { it?.get() != null }
        binding.release()
        refreshGeometry()
        if (bindings.all { it?.get() == null }) {
            handler.removeCallbacks(refresh)
            queued.set(false)
            handler.removeCallbacks(audioExpiry)
            scheduledAudioExpiry = 0L
            audio.finishHint()
        }
    }

    private fun stockChanged(stock: View, restore: Boolean) {
        if (failed) return
        val root = root(stock) ?: return
        for (reference in bindings) {
            val binding = reference?.get() ?: continue
            if (binding.root === root) { binding.stockChanged(stock, restore); return }
        }
    }

    private fun installAudio(installed: MutableList<CustomMethodUnhooker>) {
        val fields = audioAbi ?: return
        val hooks = ArrayList<CustomMethodUnhooker>(5)
        try {
            val constructorHook = object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    if (failed || param.getThrowable() != null) return
                    protectAudio {
                        val policy = param.getThisObject() ?: return@protectAudio
                        audioPolicy = WeakReference(policy)
                        readWired(policy, false)
                    }
                }
            }
            val types = fields.constructor.parameterTypes
            val args = arrayOfNulls<Any>(types.size + 1)
            for (i in types.indices) args[i] = types[i]
            args[types.size] = constructorHook
            hooks.add(ModuleHelper.findAndHookConstructor(fields.policy.name, fields.policy.classLoader, *args)
                ?: error("Duo: audio policy hook failed"))
            for (method in arrayOf(fields.connectionChanged, fields.activeChanged, fields.enabledChanged)) {
                hooks.add(ModuleHelper.hookMethod(method, object : MethodHook() {
                    override fun after(param: AfterHookCallback) {
                        if (!failed && param.getThrowable() == null) {
                            param.getThisObject()?.let { readBluetooth(it, true) }
                        }
                    }
                }) ?: error("Duo: Bluetooth hook failed"))
            }
            hooks.add(ModuleHelper.hookMethod(fields.headsetChanged, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    if (failed || audioFailed || param.getThrowable() != null) return
                    protectAudio {
                        if ((param.getArgs()[1] as? Intent)?.action != Intent.ACTION_HEADSET_PLUG) return@protectAudio
                        param.getThisObject()?.let { receiver ->
                            fields.receiverOwner.get(receiver)?.let { readWired(it, true) }
                        }
                    }
                }
            }) ?: error("Duo: headset hook failed"))
            installed.addAll(hooks)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            for (unhooker in hooks.asReversed()) CallbackGuard.guarded { unhooker.unhook() }
            protectAudio { throw t }
        }
        if (audioFailed) return
        val battery = fields.battery ?: return
        try {
            val unhooker = ModuleHelper.hookMethod(battery.changed, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    if (param.getThrowable() == null) param.getThisObject()?.let { readBluetoothBattery(it, true) }
                }
            }) ?: error("Duo: Bluetooth battery hook failed")
            installed.add(unhooker)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            batteryHintsFailed = true
            XposedHelpers.log("Duo Bluetooth battery colors unavailable: ${t.message}")
        }
    }

    fun install(): FeatureInstallResult {
        val installed = ArrayList<CustomMethodUnhooker>(21)
        fun hook(method: Method, callback: MethodHook) {
            installed.add(ModuleHelper.hookMethod(method, callback) ?: error("Duo: hook failed: ${method.name}"))
        }
        fun after(method: Method, action: (Any) -> Unit) {
            hook(method, object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
                override fun after(param: AfterHookCallback) {
                    if (failed || param.getThrowable() != null) return
                    protect { param.getThisObject()?.let(action) }
                }
            })
        }
        try {
            after(abi.attach) { attach(it as ViewGroup) }
            hook(abi.detach, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    protect { (param.getThisObject() as? ViewGroup)?.let { detach(it) } }
                }
            })
            hook(abi.updateBattery, object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
                override fun before(param: BeforeHookCallback) {
                    if (!failed) protect { child(param.getThisObject() as ViewGroup)?.binding?.restoreBattery() }
                }
                override fun after(param: AfterHookCallback) {
                    if (param.getThrowable() != null) {
                        protect { child(param.getThisObject() as ViewGroup)?.binding?.restore() }
                        return
                    }
                    if (!failed) protect {
                        child(param.getThisObject() as ViewGroup)?.binding?.let(::reconcile)
                    }
                }
            })
            after(abi.darkBattery) { child(it as ViewGroup)?.binding?.let(::reconcile) }
            after(abi.batteryChanged, ::readBattery)
            after(abi.saverChanged, ::readBattery)
            after(abi.wifiChanged, ::readWifi)
            after(abi.mobileChanged, ::readMobile)
            hook(abi.airplaneChanged, object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    if (!failed) protect {
                        val icon = param.getArg(0) ?: return@protect
                        if (network.airplane(abi.airplaneVisible.getBoolean(icon))) queueRefresh()
                    }
                }
            })
            hook(abi.subscriptionsChanged, object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    if (!failed) protect { (param.getArg(0) as? List<*>)?.let(::readSubscriptions) }
                }
            })
            after(abi.applyWifi) { stockChanged(it as View, false) }
            after(abi.applyMobile) { stockChanged(it as View, false) }
            after(abi.updateMobile) { stockChanged(it as View, false) }
            val visibilityHook = object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
                override fun before(param: BeforeHookCallback) {
                    if (!failed) protect { stockChanged(param.getThisObject() as View, true) }
                }
                override fun after(param: AfterHookCallback) {
                    if (!failed) protect { stockChanged(param.getThisObject() as View, false) }
                }
            }
            hook(abi.wifiVisibleState, visibilityHook)
            hook(abi.mobileVisibleState, visibilityHook)
            installAudio(installed)
            XposedHelpers.log("Duo status bar hooks installed")
            return FeatureInstallResult.INSTALLED
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            failed = true
            for (unhooker in installed.asReversed()) CallbackGuard.guarded { unhooker.unhook() }
            XposedHelpers.log(t)
            return FeatureInstallResult.FAILED_PERMANENT
        }
    }

    companion object {
        fun install(loader: ClassLoader, config: DuoConfig): FeatureInstallResult {
            val abi = DuoAbi.resolve(loader) ?: return FeatureInstallResult.FAILED_PERMANENT
            return DuoStatusBarHooks(abi, config).install()
        }
    }
}
