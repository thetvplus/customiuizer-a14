package tv.withaibuild.customiuizer.mods.island

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.Animatable
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

internal enum class IslandSystemKind { DND, RINGER, DARK, AIRPLANE, NFC, HOTSPOT, TORCH, WIFI, DATA, BLUETOOTH }

internal data class IslandSystemActivity(val kind: IslandSystemKind, val value: Int) {
    fun content(context: Context): IslandToastContent {
        val res = ModuleHelper.getModuleRes(context)
        val mode = when (kind) {
            IslandSystemKind.DND -> R.string.system_statusbaricons_dnd_title
            IslandSystemKind.RINGER -> when (value) {
                AudioManager.RINGER_MODE_SILENT -> R.string.system_island_mode_silent
                AudioManager.RINGER_MODE_VIBRATE -> R.string.system_island_mode_vibrate
                else -> R.string.system_island_mode_ring
            }
            IslandSystemKind.DARK -> if (value == 1) R.string.system_island_mode_dark else R.string.system_island_mode_light
            IslandSystemKind.AIRPLANE -> R.string.system_secureqs_airplane_title
            IslandSystemKind.NFC -> R.string.array_global_toggle_nfc
            IslandSystemKind.HOTSPOT -> R.string.system_statusbaricons_hotspot_title
            IslandSystemKind.TORCH -> R.string.array_global_toggle_torch
            IslandSystemKind.WIFI -> R.string.array_global_toggle_wifi
            IslandSystemKind.DATA -> R.string.array_global_toggle_mobiledata
            IslandSystemKind.BLUETOOTH -> R.string.array_global_toggle_bt
        }
        val enabled = value != 0 || kind == IslandSystemKind.RINGER || kind == IslandSystemKind.DARK
        return IslandToastContent(res.getString(if (enabled) R.string.system_island_state_enabled else R.string.system_island_state_disabled),
            "com.android.systemui", IslandSystemIcons.load(context, kind, value), detail = res.getString(mode),
            status = true)
    }
}

/** Capacity is the fixed list of device modes. Initial samples and repeated snapshots never emit. */
internal class IslandSystemEdges {
    private val states = IntArray(IslandSystemKind.entries.size) { UNKNOWN }
    fun current(kind: IslandSystemKind): Int? = states[kind.ordinal].takeUnless { it == UNKNOWN }
    fun seed(kind: IslandSystemKind, value: Int, replace: Boolean = false) {
        if (replace || current(kind) == null) states[kind.ordinal] = value
    }
    fun observe(kind: IslandSystemKind, value: Int): IslandSystemActivity? {
        val previous = states[kind.ordinal]
        states[kind.ordinal] = value
        return if (previous == UNKNOWN || previous == value) null else IslandSystemActivity(kind, value)
    }
    fun forget(kind: IslandSystemKind) { states[kind.ordinal] = UNKNOWN }
    fun clear() { states.fill(UNKNOWN) }
    private companion object { const val UNKNOWN = Int.MIN_VALUE }
}

/** One controller-owned source. Reads/registration are cold IO; Main callbacks only compare state. */
internal class IslandSystemActivities(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onEvent: (IslandSystemActivity) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val edges = IslandSystemEdges()
    private val readMutex = Mutex()
    private var registration: Job? = null
    private var settingsRead: Job? = null
    private var fullSeedPending = false // Main-owned; survives coalesced/cancelled reads.
    @Volatile private var disposed = false
    private var generation = 0L
    private var torchId: String? = null
    private var observersActive = false // IO, under readMutex.
    private var dataSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var dataObserverRegistered = false
    private val resolver = context.contentResolver

    fun current(kind: IslandSystemKind): Int? = edges.current(kind)

    fun start() {
        if (disposed || registration != null) return
        val epoch = generation
        registration = scope.launch(ioDispatcher) {
            var receiverRegistered = false
            var observerRegistered = false
            var torchRegistered = false
            var camera: CameraManager? = null
            try {
                val baseline = readInitial()
                withContext(Dispatchers.Main.immediate) {
                    if (!disposed && generation == epoch) for ((kind, value) in baseline) edges.seed(kind, value)
                }
                ensureActive()
                readMutex.withLock {
                    ModuleHelper.guarded {
                        for (key in arrayOf("zen_mode", "mobile_data", "multi_sim_data_call")) {
                            resolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
                            observerRegistered = true
                        }
                        observersActive = true
                        observeDataSubscription(SubscriptionManager.getDefaultDataSubscriptionId())
                    }
                }
                ModuleHelper.guarded {
                    context.registerReceiver(receiver, IntentFilter().apply {
                        addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
                        addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
                        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                        addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)
                        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                        addAction(HOTSPOT_ACTION)
                    }, null, handler, Context.RECEIVER_EXPORTED)
                    receiverRegistered = true
                }
                ModuleHelper.guarded {
                    camera = context.getSystemService(CameraManager::class.java)
                    val id = camera?.cameraIdList?.take(32)?.firstOrNull { id ->
                        val info = camera!!.getCameraCharacteristics(id)
                        info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                            info.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    }
                    withContext(Dispatchers.Main.immediate) { if (!disposed) torchId = id }
                    if (id != null) { camera!!.registerTorchCallback(torch, handler); torchRegistered = true }
                }
                withContext(Dispatchers.Main.immediate) { if (!disposed) readSettings() }
                awaitCancellation()
            } finally {
                withContext(NonCancellable + ioDispatcher) {
                    if (receiverRegistered) ModuleHelper.guarded { context.unregisterReceiver(receiver) }
                    readMutex.withLock {
                        observersActive = false
                        if (observerRegistered) ModuleHelper.guarded { resolver.unregisterContentObserver(observer) }
                        if (dataObserverRegistered) ModuleHelper.guarded { resolver.unregisterContentObserver(dataObserver) }
                        dataObserverRegistered = false
                        dataSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    }
                    if (torchRegistered) ModuleHelper.guarded { camera?.unregisterTorchCallback(torch) }
                }
            }
        }
    }

    fun configurationChanged(configuration: Configuration) {
        if (disposed) return
        when (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> changed(IslandSystemKind.DARK, 1)
            Configuration.UI_MODE_NIGHT_NO -> changed(IslandSystemKind.DARK, 0)
        }
    }

    fun resetForUserSwitch() {
        if (disposed) return
        generation++
        val torchState = edges.current(IslandSystemKind.TORCH)
        val hotspotState = edges.current(IslandSystemKind.HOTSPOT)
        edges.clear()
        torchState?.let { edges.seed(IslandSystemKind.TORCH, it) }
        hotspotState?.let { edges.seed(IslandSystemKind.HOTSPOT, it) }
        readSettings(seed = true)
        configurationChanged(context.resources.configuration)
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        generation++
        settingsRead?.cancel(); settingsRead = null
        fullSeedPending = false
        registration?.cancel(); registration = null
        torchId = null
        edges.clear()
    }

    private fun changed(kind: IslandSystemKind, value: Int) {
        if (!disposed) edges.observe(kind, value)?.let(onEvent)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = ModuleHelper.guarded {
            if (disposed) return@guarded
            val sample = decode(intent) ?: return@guarded
            if (isInitialStickyBroadcast) edges.seed(sample.kind, sample.value, replace = true)
            else changed(sample.kind, sample.value)
        }
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) = ModuleHelper.guarded {
            if (disposed) return@guarded
            val key = uri?.lastPathSegment
            if (key == null || key == "zen_mode" || key.startsWith("mobile_data") || key == "multi_sim_data_call") readSettings()
        }
    }
    private val dataObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = ModuleHelper.guarded { if (!disposed) readSettings() }
    }
    private val torch = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) = ModuleHelper.guarded {
            if (cameraId == torchId) changed(IslandSystemKind.TORCH, if (enabled) 1 else 0)
        }
        // Unavailable is not an off state. Keep the last actual state, rather than inventing an edge.
    }

    private fun readSettings(seed: Boolean = false) {
        if (seed) fullSeedPending = true
        val epoch = generation
        settingsRead?.cancel()
        settingsRead = scope.launch {
            val fullSeed = fullSeedPending
            val samples = withContext(ioDispatcher) {
                readMutex.withLock { ensureActive(); if (fullSeed) readInitial() else readSettingsSnapshot() }
            }
            if (!disposed && generation == epoch) {
                for ((kind, value) in samples) {
                    if (fullSeed) edges.seed(kind, value) else changed(kind, value)
                }
                if (fullSeed) fullSeedPending = false
            }
        }
    }

    private fun readSettingsSnapshot(): Map<IslandSystemKind, Int> = buildMap {
        ModuleHelper.guarded { put(IslandSystemKind.DND, if (Settings.Global.getInt(resolver, "zen_mode", 0) == 0) 0 else 1) }
        ModuleHelper.guarded {
            val subscription = SubscriptionManager.getDefaultDataSubscriptionId()
            observeDataSubscription(subscription)
            val manager = context.getSystemService(TelephonyManager::class.java)
            if (manager != null && SubscriptionManager.isValidSubscriptionId(subscription)) {
                try {
                    put(IslandSystemKind.DATA, if (manager.createForSubscriptionId(subscription).isDataEnabled) 1 else 0)
                } catch (error: SecurityException) {
                    // A host may revoke access: unavailable is not an observed OFF state.
                    XposedHelpers.log("Dynamic Island data state unavailable", error)
                }
            }
        }
    }

    private fun observeDataSubscription(subscription: Int) {
        if (!observersActive || disposed || subscription == dataSubscription) return
        if (dataObserverRegistered) {
            resolver.unregisterContentObserver(dataObserver)
            dataObserverRegistered = false
        }
        dataSubscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        if (SubscriptionManager.isValidSubscriptionId(subscription)) {
            resolver.registerContentObserver(Settings.Global.getUriFor("mobile_data$subscription"), false, dataObserver)
            dataSubscription = subscription
            dataObserverRegistered = true
        }
    }

    private fun readInitial(): Map<IslandSystemKind, Int> = buildMap {
        putAll(readSettingsSnapshot())
        ModuleHelper.guarded { context.getSystemService(AudioManager::class.java)?.ringerMode?.let { put(IslandSystemKind.RINGER, it) } }
        ModuleHelper.guarded { put(IslandSystemKind.AIRPLANE, if (Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0) 1 else 0) }
        ModuleHelper.guarded { context.getSystemService(WifiManager::class.java)?.wifiState?.let { binary(it, 1, 3)?.let { value -> put(IslandSystemKind.WIFI, value) } } }
        ModuleHelper.guarded {
            // On A14 the hotspot broadcast is no longer sticky. This runtime SystemApi is
            // verified in the target Wi-Fi module and read only while preparing a baseline.
            context.getSystemService(WifiManager::class.java)?.let { manager ->
                val value = XposedHelpers.callMethod(manager, "getWifiApState") as? Int
                value?.let { binary(it, 11, 13)?.let { state -> put(IslandSystemKind.HOTSPOT, state) } }
            }
        }
        ModuleHelper.guarded { NfcAdapter.getDefaultAdapter(context)?.let { put(IslandSystemKind.NFC, if (it.isEnabled) 1 else 0) } }
        ModuleHelper.guarded { context.getSystemService(BluetoothManager::class.java)?.adapter?.state?.let { binary(it, 10, 12)?.let { value -> put(IslandSystemKind.BLUETOOTH, value) } } }
        when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> put(IslandSystemKind.DARK, 1)
            Configuration.UI_MODE_NIGHT_NO -> put(IslandSystemKind.DARK, 0)
        }
    }

    companion object {
        // A14 WifiManager's SystemApi protected broadcast, also used by the native Settings UI.
        internal const val HOTSPOT_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"
        private fun binary(value: Int, off: Int, on: Int): Int? = when (value) { off -> 0; on -> 1; else -> null }
        internal fun decode(intent: Intent): IslandSystemActivity? {
            val kind: IslandSystemKind
            val value: Int?
            when (intent.action) {
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    kind = IslandSystemKind.RINGER
                    value = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1).takeIf { it in 0..2 }
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    kind = IslandSystemKind.AIRPLANE
                    value = if (intent.hasExtra("state")) { if (intent.getBooleanExtra("state", false)) 1 else 0 } else null
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    kind = IslandSystemKind.WIFI; value = binary(intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1), 1, 3)
                }
                NfcAdapter.ACTION_ADAPTER_STATE_CHANGED -> {
                    kind = IslandSystemKind.NFC; value = binary(intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, -1), 1, 3)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    kind = IslandSystemKind.BLUETOOTH; value = binary(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1), 10, 12)
                }
                HOTSPOT_ACTION -> { kind = IslandSystemKind.HOTSPOT; value = binary(intent.getIntExtra("wifi_state", -1), 11, 13) }
                else -> return null
            }
            return value?.let { IslandSystemActivity(kind, it) }
        }
    }
}

/** Event-cold lookup of a closed set of native SystemUI glyphs. No view or global icon hooks. */
internal object IslandSystemIcons {
    internal fun feedbackResourceName(kind: IslandSystemKind, value: Int): String? = when (kind) {
        IslandSystemKind.DND -> when (value) {
            0 -> "miui_statusbar_dnd_mode_off"
            1 -> "miui_statusbar_dnd_mode_on"
            else -> null
        }
        IslandSystemKind.RINGER -> when (value) {
            AudioManager.RINGER_MODE_SILENT -> "miui_statusbar_silent_mode_on"
            AudioManager.RINGER_MODE_NORMAL -> "miui_statuebar_silent_mode_off"
            else -> null
        }
        else -> null
    }

    fun resourceName(kind: IslandSystemKind, value: Int): String? {
        if (kind == IslandSystemKind.RINGER) return when (value) {
            AudioManager.RINGER_MODE_SILENT -> "ic_qs_mute_on"
            AudioManager.RINGER_MODE_VIBRATE -> "ic_qs_vibrate_on"
            AudioManager.RINGER_MODE_NORMAL -> "ic_qs_mute_off"
            else -> null
        }
        if (value !in 0..1) return null
        val on = value == 1
        return when (kind) {
            IslandSystemKind.DND -> if (on) "ic_qs_dnd_on" else "ic_qs_dnd_off"
            IslandSystemKind.DARK -> if (on) "ic_qs_night_mode_on" else "ic_qs_night_mode_off"
            IslandSystemKind.AIRPLANE -> if (on) "ic_signal_airplane_enable" else "ic_signal_airplane_disable"
            IslandSystemKind.NFC -> if (on) "ic_qs_nfc_enabled" else "ic_qs_nfc_disabled"
            IslandSystemKind.HOTSPOT -> if (on) "ic_qs_hotspot_enabled" else "ic_qs_hotspot_disabled"
            IslandSystemKind.TORCH -> if (on) "ic_qs_flashlight_enabled" else "ic_qs_flashlight_disabled"
            IslandSystemKind.WIFI -> if (on) "ic_qs_wifi_on" else "ic_qs_wifi_off"
            IslandSystemKind.DATA -> if (on) "ic_qs_data_on" else "ic_qs_data_off"
            IslandSystemKind.BLUETOOTH -> if (on) "ic_qs_bluetooth_on" else "ic_qs_bluetooth_off"
            IslandSystemKind.RINGER -> return null
        }
    }

    fun load(context: Context, kind: IslandSystemKind, value: Int): Drawable? {
        val name = resourceName(kind, value) ?: return null
        val feedback = feedbackResourceName(kind, value)?.let { feedbackName ->
            try {
                // Resources-only context: use the installed ROM plugin and its active overlays,
                // without loading plugin code or retaining its controller/view/context.
                val plugin = context.createPackageContext("miui.systemui.plugin", 0)
                val id = plugin.resources.getIdentifier(feedbackName, "drawable", "miui.systemui.plugin")
                if (id != 0) plugin.getDrawable(id) else null
            } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                null
            }
        }
        val native = feedback ?: try {
            // Other modes, or ROMs missing that feedback resource, use native SystemUI glyphs.
            val id = context.resources.getIdentifier(name, "drawable", "com.android.systemui")
            if (id != 0) context.getDrawable(id) else null
        } catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            null
        }
        val drawable = (native ?: context.getDrawable(android.R.drawable.ic_dialog_info))?.mutate()?.apply {
            setTint(Color.WHITE)
            if (this is Animatable) stop()
            jumpToCurrentState()
        } ?: return null
        return try { normalize(drawable) }
        catch (error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            // A ROM overlay can replace a drawable with an unsupported renderer.
            // Keep the actual native resource, with the fixed cell still bounding it.
            drawable
        }
    }

    /** One 96x96 cold probe, immediately released. No bitmap/icon cache or animation task.
     * Only this loader's closed resource list reaches here; artwork/app icons never do. */
    internal fun normalize(native: Drawable): Drawable {
        val probe = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val previous = Rect(native.bounds)
        val ratio = native.intrinsicWidth.coerceAtLeast(1).toFloat() / native.intrinsicHeight.coerceAtLeast(1)
        val width = if (ratio >= 1f) 96 else (96 * ratio).toInt().coerceAtLeast(1)
        val height = if (ratio <= 1f) 96 else (96 / ratio).toInt().coerceAtLeast(1)
        try {
            native.setBounds(0, 0, width, height)
            native.draw(Canvas(probe))
            val pixels = IntArray(96 * 96)
            probe.getPixels(pixels, 0, 96, 0, 0, 96, 96)
            var left = 96; var top = 96; var right = -1; var bottom = -1
            for (y in 0 until height) for (x in 0 until width) {
                if (pixels[y * 96 + x] ushr 24 != 0) {
                    left = minOf(left, x); right = maxOf(right, x)
                    top = minOf(top, y); bottom = maxOf(bottom, y)
                }
            }
            if (right < left || bottom < top) { native.bounds = previous; return native }
            return NativeGlyph(native, width, height, Rect(left, top, right + 1, bottom + 1))
        } finally {
            probe.recycle()
        }
    }

    /** Delegates drawing to the installed drawable; only its viewport/padding is normalized. */
    private class NativeGlyph(private val native: Drawable, width: Int, height: Int,
        private val visible: Rect) : Drawable() {
        private var scale = 1f
        private var x = 0f; private var y = 0f
        init { native.setBounds(0, 0, width, height) }
        override fun getIntrinsicWidth() = 24
        override fun getIntrinsicHeight() = 24
        override fun onBoundsChange(bounds: Rect) {
            // 20dp visible ink inside the common 24dp cell. Preserve every glyph's aspect.
            scale = minOf(bounds.width().toFloat() / visible.width(), bounds.height().toFloat() / visible.height()) * (20f / 24f)
            x = bounds.exactCenterX() - visible.exactCenterX() * scale
            y = bounds.exactCenterY() - visible.exactCenterY() * scale
        }
        override fun draw(canvas: Canvas) {
            val save = canvas.save()
            canvas.translate(x, y); canvas.scale(scale, scale)
            native.draw(canvas)
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { native.alpha = alpha; invalidateSelf() }
        override fun getAlpha() = native.alpha
        override fun setColorFilter(filter: ColorFilter?) { native.colorFilter = filter; invalidateSelf() }
        @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
