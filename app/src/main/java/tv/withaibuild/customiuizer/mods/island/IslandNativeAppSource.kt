package tv.withaibuild.customiuizer.mods.island

import android.app.Application
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.matchers.MethodMatcher
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.EnumMap

/** Opted-in native process adapters. They observe successful state changes, never replace them. */
internal object IslandNativeAppSource {
    fun enabled(prefs: PrefMap, pkg: String): Boolean =
        StrongToastPresentationMode.fromPreference(prefs.getStringAsInt("system_strong_toast_mode", 0)) ==
            StrongToastPresentationMode.DYNAMIC_ISLAND &&
            IslandLiveKind.read(prefs).any { it.signalPackage == pkg }

    fun install(param: PackageReadyParam, prefs: PrefMap) {
        val pkg = param.packageName.orEmpty()
        val process = Application.getProcessName()
        val hooks = Installation()
        try {
            when {
                pkg == IslandLiveKind.CLOCK_PACKAGE && process == pkg -> Clock(param.classLoader, prefs, hooks).install()
                pkg == IslandLiveKind.RECORDER_PACKAGE && process == "$pkg:remote" -> installRecorder(param, prefs, hooks)
            }
            hooks.commit()
        } catch (error: Throwable) {
            hooks.abort()
            throw error
        }
    }

    /** App-process lifetime owner; at most three current scalar snapshots, no Activity references. */
    private class Publisher(val context: Context, val prefs: PrefMap, val refresh: () -> Unit) : AutoCloseable {
        val handler = Handler(Looper.getMainLooper())
        val values = EnumMap<IslandLiveKind, IslandLiveActivity>(IslandLiveKind::class.java)
        // This token has no command interface; it only lets SystemUI observe producer death.
        private val lifetime = android.os.Binder()
        private val sysuiUid = context.packageManager.getApplicationInfo("com.android.systemui", 0).uid
        private var closed = false
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = ModuleHelper.guarded {
                // Validate framework-supplied identity before deserializing any payload.
                if (closed || getSentFromUid() != sysuiUid ||
                    !ModuleHelper.isTrustedBroadcast(this, "com.android.systemui")) return@guarded
                if (intent.action != IslandLiveSignal.REQUEST) return@guarded
                val at = intent.getLongExtra("at", -1)
                val now = SystemClock.uptimeMillis()
                val nonce = intent.getLongExtra("nonce", 0)
                if (nonce <= 0 || at < 0 || at > now || now - at > 5000) return@guarded
                refresh()
                for (kind in IslandLiveKind.entries) if (IslandLiveSignal.owns(context.packageName, kind)) send(kind, nonce)
            }
        }
        init { context.registerReceiver(receiver, IntentFilter(IslandLiveSignal.REQUEST), null,
            handler, Context.RECEIVER_EXPORTED) }
        fun update(kind: IslandLiveKind, clock: IslandLiveClock?, active: Boolean, alert: Boolean = false,
            meter: android.os.IBinder? = null) {
            val old = values[kind]
            val value = if (!active || !enabled(prefs, context.packageName) || kind !in IslandLiveKind.read(prefs)) null
            else IslandLiveActivity(kind, context.packageName, Process.myUid(), clock,
                old?.open ?: open(kind), session = if (old == null || old.alert != alert)
                    IslandApplicationOverlaySignal.nextSequence() else old.session, alert = alert, meter = meter)
            if (value == old) return
            if (value == null) values.remove(kind) else values[kind] = value
            send(kind)
        }
        private fun open(kind: IslandLiveKind): PendingIntent? {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
            if (context.packageName == IslandLiveKind.CLOCK_PACKAGE) intent.putExtra("navigation_tab", when (kind) {
                IslandLiveKind.TIMER -> 3
                IslandLiveKind.STOPWATCH -> 2
                else -> 0
            })
            return PendingIntent.getActivity(context, 1100 + kind.ordinal, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        private fun send(kind: IslandLiveKind, nonce: Long = 0) {
            if (!closed) ModuleHelper.sendBroadcastWithIdentity(context, IslandLiveSignal.encode(context.packageName,
                kind, values[kind], IslandApplicationOverlaySignal.nextSequence(), nonce, lifetime))
        }
        override fun close() {
            if (closed) return
            for (kind in values.keys.toList()) { values.remove(kind); send(kind) }
            closed = true
            context.unregisterReceiver(receiver)
        }
    }

    private class Installation {
        private val handles = ArrayList<AutoCloseable>()
        fun hook(method: Method, callback: MethodHook) {
            val handle = checkNotNull(ModuleHelper.hookMethod(method, callback)) { "Native activity hook unavailable" }
            handles.add(AutoCloseable { handle.unhook() })
        }
        fun after(method: Method, action: (AfterHookCallback) -> Unit) = hook(method, object : MethodHook() {
                override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                    if (callback.getThrowable() == null) action(callback)
                }
            })
        fun abort() {
            for (handle in handles.asReversed()) ModuleHelper.guarded { handle.close() }
            handles.clear()
        }
        fun commit() { handles.clear() }
    }

    private class Clock(private val loader: ClassLoader, private val prefs: PrefMap, private val hooks: Installation) {
        private val handler = Handler(Looper.getMainLooper())
        private var publisher: Publisher? = null
        private var stopwatch: SharedPreferences? = null
        private var timerOwner = WeakReference<Service>(null)
        private var ringOwner = WeakReference<Service>(null)
        private var ringing: IslandLiveKind? = null
        private var queued = false
        private val timerClass = loader.loadClass("com.android.deskclock.timer.Timer")
        private val timerService = loader.loadClass("com.android.deskclock.timer.TimerService")
        private val model = timerService.getDeclaredField("mTimer").apply { isAccessible = true }
        private val getState = timerClass.getDeclaredMethod("getState")
        private val getTime = timerClass.getDeclaredMethod("getTime")
        private val getRemain = timerClass.getDeclaredMethod("getRemain")
        private val running = timerClass.getDeclaredField("STATE_RUNNING").getInt(null)
        private val paused = timerClass.getDeclaredField("STATE_PAUSE").getInt(null)
        private val dirty = Runnable { queued = false; ModuleHelper.guarded { snapshot() } }
        private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in stopwatchKeys) queue()
        }
        private fun queue() { if (!queued) { queued = true; handler.post(dirty) } }
        fun install() = with(hooks) {
            val app = loader.loadClass("com.android.deskclock.DeskClockApp")
            val nativePrefs = loader.loadClass("com.android.deskclock.util.FBEUtil")
                .getDeclaredMethod("getDefaultSharedPreferences", Context::class.java)
            val appCreate = app.getDeclaredMethod("onCreate")
            val mutations = listOf(timerService.getDeclaredMethod("onCreate"),
                timerService.getDeclaredMethod("startTimer", timerClass), timerService.getDeclaredMethod("pauseTimer"),
                timerService.getDeclaredMethod("continueTimer"), timerService.getDeclaredMethod("reContinueTimer"),
                timerService.getDeclaredMethod("cancelTimer"), timerService.getDeclaredMethod("timerOff"),
                timerService.getDeclaredMethod("setTimerState", Int::class.javaPrimitiveType))
            val timerDestroy = timerService.getDeclaredMethod("onDestroy")
            val alarm = loader.loadClass("com.android.deskclock.alarm.alert.AlarmService")
            val alarmModel = loader.loadClass("com.android.deskclock.Alarm")
            val alarmId = alarmModel.getDeclaredField("id")
            val play = alarm.getDeclaredMethod("play", alarmModel)
            val alarmDestroy = alarm.getDeclaredMethod("onDestroy")
            // Resolve the entire native contract before installing the first callback.
            after(appCreate) { callback ->
                if (publisher != null) return@after
                val context = callback.getThisObject() as Application
                stopwatch = (nativePrefs.invoke(null, context) as SharedPreferences).also {
                    if (IslandLiveKind.STOPWATCH in IslandLiveKind.read(prefs)) it.registerOnSharedPreferenceChangeListener(preferenceListener)
                }
                publisher = Publisher(context, prefs, ::snapshot)
                queue()
            }
            for (method in mutations) after(method) { callback ->
                    timerOwner = WeakReference(callback.getThisObject() as Service); queue()
            }
            after(timerDestroy) { callback ->
                if (timerOwner.get() === callback.getThisObject()) { snapshot(); timerOwner.clear() }
            }
            // play() is reached only after native validity/space checks, for both alarm and timer.
            after(play) { callback ->
                val kind = if (alarmId.getInt(callback.getArg(0)) == -2) IslandLiveKind.TIMER else IslandLiveKind.ALARM
                ringOwner = WeakReference(callback.getThisObject() as Service)
                ringing = kind; queue()
            }
            after(alarmDestroy) { callback ->
                if (ringOwner.get() === callback.getThisObject()) {
                    val kind = ringing; ringing = null; ringOwner.clear()
                    if (kind != null) publisher?.update(kind, null, false)
                    queue()
                }
            }
        }
        private fun snapshot() {
            val output = publisher ?: return
            val now = SystemClock.elapsedRealtime()
            stopwatch?.let { values ->
                val active = values.getBoolean("stopwatch_state_running_pref", false)
                val duration = if (active) IslandLiveTime.difference(System.currentTimeMillis(),
                    values.getLong("stopwatch_base_time_pref", Long.MAX_VALUE)) else values.getLong("stopwatch_elapsed_time_pref", 0)
                val clock = duration?.let { IslandLiveTime.elapsed(it, now, !active) }
                output.update(IslandLiveKind.STOPWATCH, clock, clock != null && (active || duration != 0L))
            }
            if (ringing == IslandLiveKind.TIMER) output.update(IslandLiveKind.TIMER, null, true, alert = true)
            else timerOwner.get()?.let { owner ->
                val timer = model.get(owner)
                val state = timer?.let { getState.invoke(it) as Int }
                val clock = when (state) {
                    running -> IslandLiveTime.deadline(getTime.invoke(timer) as Long, System.currentTimeMillis(), now)
                    paused -> (getRemain.invoke(timer) as Long).takeIf { it in 0..IslandLiveTime.MAX_SPAN }
                        ?.let { IslandLiveClock(now + it, true, it) }
                    else -> null
                }
                // Native countdown completion and its alert Service arrive on separate callbacks.
                // Retain zero briefly across that handoff; an explicit early cancel clears at once.
                val ending = output.values[IslandLiveKind.TIMER]?.clock?.takeIf {
                    it.countdown && it.pausedMillis == null && now - it.base in 0..2999
                }
                output.update(IslandLiveKind.TIMER, clock ?: ending, clock != null || ending != null)
            }
            output.update(IslandLiveKind.ALARM, null, ringing == IslandLiveKind.ALARM, alert = true)
        }
        companion object {
            val stopwatchKeys = setOf("stopwatch_state_running_pref", "stopwatch_base_time_pref", "stopwatch_elapsed_time_pref")
        }
    }

    private fun installRecorder(param: PackageReadyParam, prefs: PrefMap, hooks: Installation) = with(hooks) {
        // Semantic literals + full signatures, not obfuscated names. Bridge is cold and always closed.
        val state: Method
        val duration: Method
        val amplitude: Method?
        try {
            MainModule.loadDexKit()
            XposedHelpers.createBridge(param.applicationInfo.sourceDir)
            val bridge = requireNotNull(XposedHelpers.bridge)
            val cls = "com.android.soundrecorder.RecorderService"
            fun locate(literal: String, params: List<String>, result: Class<*>): Method {
                val data = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .declaredClass(cls).usingStrings(literal))).filter { it.paramTypeNames == params }.single()
                return data.getMethodInstance(param.classLoader).apply {
                    check(returnType == result && declaringClass.name == cls); isAccessible = true
                }
            }
            state = locate("setState: state from ", listOf("int", "java.lang.String", "long"), Void.TYPE)
            duration = locate("getRecordingDuration mLastPausedRecordDuration: ", emptyList(), Long::class.javaPrimitiveType!!)
            amplitude = ModuleHelper.guarded<Method?>(null) {
                bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().declaredClass(cls)
                    .returnType(Int::class.javaPrimitiveType!!).paramTypes(emptyList<String>())
                    .addInvoke(MethodMatcher.create().declaredClass("miuix.media.h").name("f")
                        .returnType(Int::class.javaPrimitiveType!!).paramTypes(emptyList<String>()))))
                    .single().getMethodInstance(param.classLoader).apply {
                        check(declaringClass == state.declaringClass && returnType == Int::class.javaPrimitiveType)
                        isAccessible = true
                    }
            }
        } finally { XposedHelpers.closeBridge() }
        var publisher: Publisher? = null
        var owner = WeakReference<Service>(null)
        var recordingState = 0
        val meterOwner = java.util.concurrent.atomic.AtomicReference<Service?>(null)
        var meter: android.os.IBinder? = null
        val handler = Handler(Looper.getMainLooper())
        val activeKey = "customiuizer_island_recorder_live"
        fun snapshot() {
            val service = owner.get() ?: return
            val clock = if (recordingState in 1..2) IslandLiveTime.elapsed(duration.invoke(service) as Long,
                SystemClock.elapsedRealtime(), recordingState == 2) else null
            meterOwner.set(service.takeIf { recordingState == 1 })
            publisher?.update(IslandLiveKind.RECORDER, clock, clock != null, meter = meter)
        }
        val create = state.declaringClass.getDeclaredMethod("onCreate")
        val destroy = state.declaringClass.getDeclaredMethod("onDestroy")
        hook(create, object : MethodHook() {
            override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                XposedHelpers.setAdditionalInstanceField(callback.getThisObject(), activeKey, true)
            }
        })
        after(state) { callback ->
            val service = WeakReference(callback.getThisObject() as Service)
            val value = callback.getArg(0) as Int
            handler.post { ModuleHelper.guarded {
                val current = service.get() ?: return@guarded
                if (XposedHelpers.getAdditionalInstanceField(current, activeKey) != true) return@guarded
                if (owner.get() !== current) {
                    publisher?.close(); owner = WeakReference(current)
                    publisher = Publisher(current.applicationContext, prefs, ::snapshot)
                    meter = amplitude?.let { method ->
                        // Use the resolved SystemUI UID, including ROMs with a dedicated shared UID.
                        val allowed = current.packageManager.getApplicationInfo("com.android.systemui", 0).uid
                        val expected = WeakReference(current)
                        IslandRecorderMeter.producer(allowed) {
                            val active = meterOwner.get() ?: return@producer -1
                            if (active !== expected.get()) -1 else method.invoke(active) as Int
                        }
                    }
                }
                recordingState = value
                snapshot()
            } }
        }
        after(destroy) { callback ->
            val service = WeakReference(callback.getThisObject() as Service)
            XposedHelpers.setAdditionalInstanceField(callback.getThisObject(), activeKey, false)
            handler.post { ModuleHelper.guarded {
                if (owner.get() === service.get()) {
                    meterOwner.set(null); meter = null
                    publisher?.close(); publisher = null; owner.clear(); recordingState = 0
                }
            } }
        }
    }
}
