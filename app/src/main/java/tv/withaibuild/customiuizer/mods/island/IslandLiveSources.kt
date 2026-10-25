package tv.withaibuild.customiuizer.mods.island

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import java.lang.reflect.Field

/** These calls execute in SystemUI, using its existing grant, never the module APK permissions. */
internal object IslandLiveCalls {
    @android.annotation.SuppressLint("MissingPermission")
    fun state(context: Context): Pair<Boolean, Boolean>? {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return try {
            val ringing = context.getSystemService(TelephonyManager::class.java)?.callState == TelephonyManager.CALL_STATE_RINGING
            Pair(context.getSystemService(TelecomManager::class.java)?.isInCall == true || ringing, ringing)
        } catch (_: SecurityException) { null }
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun open(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        try { context.getSystemService(TelecomManager::class.java)?.showInCallScreen(false) }
        catch (_: SecurityException) { /* Native call UI remains available through the system. */ }
    }
}

/** Metadata only. Native AppOps filtering remains authoritative, including disabled/recent access. */
internal class IslandPrivacyAccess(private val loader: ClassLoader) {
    private val controller = loader.loadClass("com.android.systemui.appops.AppOpsControllerImpl")
    private val active = controller.getDeclaredMethod("getActiveAppOps", Boolean::class.javaPrimitiveType)
    private val item = loader.loadClass("com.android.systemui.appops.AppOpItem")
    private val code = field("mCode")
    private val uid = field("mUid")
    private val pkg = field("mPackageName")
    private fun field(name: String): Field = item.getDeclaredField(name).apply { isAccessible = true }
    fun read(user: Int, excluded: Set<String>): IslandLiveActivity? {
        val owner = ModuleHelper.getDepInstance(loader, "com.android.systemui.appops.AppOpsController") ?: return null
        var bits = 0
        var source: String? = null
        var sourceUid = -1
        for (entry in active.invoke(owner, false) as List<*>) {
            if (entry == null) continue
            val id = uid.getInt(entry)
            if (id / 100000 != user) continue
            val packageName = pkg.get(entry) as String
            if (packageName in excluded) continue
            val bit = when (code.getInt(entry)) { 26, 101 -> CAMERA; 27, 100 -> MICROPHONE; else -> 0 }
            if (bit == 0) continue
            bits = bits or bit
            if (source == null) { source = packageName; sourceUid = id }
        }
        return source?.let { IslandLiveActivity(IslandLiveKind.PRIVACY, it, sourceUid, privacy = bits) }
    }
    companion object { const val MICROPHONE = 1; const val CAMERA = 2 }
}

/** Attached-SystemUI owner. Event-driven IO reads are conflated; no timer, wake lock or service. */
internal class IslandLiveSources(private val context: Context, private val handler: Handler,
    private val scope: CoroutineScope, private val io: CoroutineDispatcher, private val user: Int,
    private val kinds: Set<IslandLiveKind>, private val privacy: IslandPrivacyAccess?,
    private val excluded: Set<String>,
    private val changed: (IslandLiveKind, IslandLiveActivity?) -> Unit) : AutoCloseable {
    private var closed = false
    private var setup: Job? = null
    private var subscriber: IslandLiveSignal.Subscriber? = null
    private var callRegistered = false
    private var read: Job? = null
    private var pending = false
    private var callSession = 0L
    private val calls = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            // PHONE_STATE is a protected platform action; receiver also requires privileged sender permission.
            if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) refresh()
        }
    }
    init {
        if (IslandLiveKind.CALL in kinds) ModuleHelper.guarded {
            context.registerReceiver(calls, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                Manifest.permission.MODIFY_PHONE_STATE, handler, Context.RECEIVER_EXPORTED)
            callRegistered = true
        }
        setup = scope.launch {
            val identities = withContext(io) {
                buildMap {
                    for (pkg in IslandLiveKind.sourcePackages(kinds.map { it.value })) ModuleHelper.guarded {
                        // An explicitly selected native-compatible app can be installed in /data
                        // (e.g. the global Recorder). Pin its current UID, not its install location.
                        // The subscriber still checks framework sender package/UID and token ownership.
                        identity(context, pkg, user)?.let { put(pkg, it) }
                    }
                }
            }
            if (!closed && identities.isNotEmpty()) {
                subscriber = IslandLiveSignal.Subscriber(context, handler, identities) { kind, value ->
                    if (!closed && kind in kinds) changed(kind, value)
                }
                withContext(io) { ModuleHelper.guarded { subscriber?.request() } }
            }
        }
        refresh()
    }
    fun refresh() {
        if (closed) return
        if (read?.isActive == true) { pending = true; return }
        if (IslandLiveKind.CALL !in kinds && (IslandLiveKind.PRIVACY !in kinds || privacy == null)) return
        read = scope.launch {
            do {
                pending = false
                val result = withContext(io) {
                    val call = if (IslandLiveKind.CALL in kinds) ModuleHelper.guarded<Pair<Boolean, Boolean>?>(null) {
                        IslandLiveCalls.state(context)
                    } else null
                    val access = if (IslandLiveKind.PRIVACY in kinds) ModuleHelper.guarded<IslandLiveActivity?>(null) {
                        privacy?.read(user, excluded)
                    } else null
                    Pair(call, access)
                }
                if (closed) return@launch
                if (IslandLiveKind.CALL in kinds) {
                    val active = result.first?.first == true
                    if (!active) callSession = 0 else if (callSession == 0L)
                        callSession = IslandApplicationOverlaySignal.nextSequence()
                    changed(IslandLiveKind.CALL, if (active) IslandLiveActivity(IslandLiveKind.CALL,
                        "com.android.incallui", user * 100000 + 1000, session = callSession,
                        alert = result.first?.second == true) else null)
                }
                if (IslandLiveKind.PRIVACY in kinds) changed(IslandLiveKind.PRIVACY, result.second)
            } while (pending && !closed)
        }
    }
    override fun close() {
        if (closed) return
        closed = true
        setup?.cancel(); setup = null
        read?.cancel(); read = null
        subscriber?.close(); subscriber = null
        if (callRegistered) { callRegistered = false; ModuleHelper.guarded { context.unregisterReceiver(calls) } }
    }
    companion object {
        fun identity(context: Context, pkg: String, user: Int): Int? {
            if (pkg != IslandLiveKind.CLOCK_PACKAGE && pkg != IslandLiveKind.RECORDER_PACKAGE) return null
            val app = context.packageManager.getApplicationInfo(pkg, 0)
            return app.uid.takeIf { app.enabled && it / 100000 == user }
        }
    }
}
