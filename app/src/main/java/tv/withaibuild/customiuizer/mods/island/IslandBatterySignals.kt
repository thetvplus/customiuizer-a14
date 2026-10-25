package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.utils.PrefMap

/** Only edges survive a broadcast. No samples, history, timer, wakelock or service. */
internal class IslandBatteryEdges {
    data class Event(val level: Int, val low: Boolean)
    private var level = -1
    private var plugged = false
    private var connectionReported = false
    fun observe(next: Int, connected: Boolean, charging: Boolean, initial: Boolean): Event? {
        if (next !in 0..100) return null
        val old = level
        val event = if (initial || old < 0) null
            else if (connected && charging && !connectionReported) Event(next, false)
            else if (!connected && (bucket(next) > bucket(old) || plugged && bucket(next) > 0)) Event(next, true)
            else null
        if (!connected) connectionReported = false else if (initial || old < 0 || charging) connectionReported = true
        level = next; plugged = connected
        return event
    }
    private fun bucket(level: Int) = when { level <= 5 -> 3; level <= 10 -> 2; level <= 20 -> 1; else -> 0 }
}

internal class IslandBatterySignals(context: Context, scope: CoroutineScope, io: CoroutineDispatcher,
    handler: Handler, charging: Boolean, low: Boolean,
    private val power: (Boolean) -> Unit, private val event: (IslandBatteryEdges.Event) -> Unit,
) : AutoCloseable {
    @Volatile private var closed = false
    private val edges = IslandBatteryEdges()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = ModuleHelper.guarded {
            if (closed) return@guarded
            if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
                power(ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true)
            } else if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val raw = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val percent = if (raw >= 0 && scale > 0) (raw * 100L / scale).toInt() else -1
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                edges.observe(percent, intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
                    status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL, isInitialStickyBroadcast)
                    ?.takeIf { if (it.low) low else charging }?.let(event)
            }
        }
    }
    private val job = scope.launch(io) {
        var registered = false
        try {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED).apply {
                if (charging || low) addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_NOT_EXPORTED)
            registered = true
            val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            withContext(Dispatchers.Main.immediate) { if (!closed) power(saver) }
            awaitCancellation()
        } finally {
            withContext(NonCancellable) {
                if (registered) ModuleHelper.guarded { context.unregisterReceiver(receiver) }
            }
        }
    }
    override fun close() { closed = true; job.cancel() }
}

/** SecurityCenter consults this only on its existing warning-setting read. A four-second
 * acknowledgement is published only after the replacement is actually visible. Missing,
 * delayed or failed writes retain the native warning. The controller owns one conflated slot. */
internal class IslandBatteryWarningMarker(private val resolver: ContentResolver, scope: CoroutineScope,
    io: CoroutineDispatcher) : AutoCloseable {
    private val updates = Channel<Long>(Channel.CONFLATED)
    private val job = scope.launch(io) {
        try {
            for (until in updates) ModuleHelper.guarded {
                Settings.Global.putString(resolver, KEY, until.takeIf { it > 0 }?.toString())
            }
        } finally {
            withContext(NonCancellable) { ModuleHelper.guarded { Settings.Global.putString(resolver, KEY, null) } }
        }
    }
    fun visible(shown: Boolean) { updates.trySend(if (shown) SystemClock.elapsedRealtime() + 4000 else 0) }
    override fun close() { updates.close(); job.cancel() }
    companion object {
        private const val KEY = "customiuizer_island_low_battery_until"
        fun enabled(prefs: PrefMap) = StrongToastPresentationMode.fromPreference(prefs.getStringAsInt("system_strong_toast_mode", 0)) ==
            StrongToastPresentationMode.DYNAMIC_ISLAND &&
            prefs.getBoolean("system_island_feedback_low_battery")
        fun acknowledged(resolver: ContentResolver): Boolean = ModuleHelper.guarded(false) {
            val until = Settings.Global.getString(resolver, KEY)?.toLongOrNull() ?: return@guarded false
            until - SystemClock.elapsedRealtime() in 1..4000
        }
    }
}

/** Process-lifetime policy, owned by SecurityCenter's single installed setting hook. */
internal class IslandBatteryWarningPolicy(private val prefs: PrefMap) {
    @Volatile private var manual = prefs.getBoolean("system_hidelowbatwarn")
    @Volatile private var island = IslandBatteryWarningMarker.enabled(prefs)
    private val observer = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (key == null || key == "system_hidelowbatwarn" || key == "system_strong_toast_mode" ||
                key == "system_island_feedback_low_battery") {
                manual = prefs.getBoolean("system_hidelowbatwarn")
                island = IslandBatteryWarningMarker.enabled(prefs)
            }
        }
    }
    init { ModuleHelper.observePreferenceChange(observer, this) }
    fun suppress(resolver: ContentResolver?) = manual || island && resolver != null &&
        IslandBatteryWarningMarker.acknowledged(resolver)
}
