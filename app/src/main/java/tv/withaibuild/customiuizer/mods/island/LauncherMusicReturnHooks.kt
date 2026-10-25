package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.animation.ValueAnimator
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal object LauncherMusicReturnHooks {
    private const val OWNER = "customiuizer_music_return_owner"
    @Volatile private var active = WeakReference<Owner>(null)
    private data class Attempt(val params: Any, val snapshot: IslandMusicReturnSignal.Snapshot,
        val epoch: Long, val sequence: Long, val impulse: Float)

    fun install(param: PackageReadyParam, prefs: PrefMap) {
        val access = LauncherMusicReturnAccess(param.classLoader)
        val targetHook = ModuleHelper.hookMethod(access.closingTarget, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val nav = chain.thisObject ?: return chain.proceed()
                val selected = ModuleHelper.guarded(false) { active.get()?.selectWindowTarget(nav) == true }
                return if (selected) null else chain.proceed()
            }
        })
        if (targetHook == null) return
        ModuleHelper.hookMethod(access.attached, object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                val view = callback.getThisObject() as? View ?: return@guarded
                if (callback.getThrowable() != null || !view.isAttachedToWindow ||
                    XposedHelpers.getAdditionalInstanceField(view, OWNER) != null) return@guarded
                active.get()?.dispose()
                val owner = Owner(view, prefs, access)
                XposedHelpers.setAdditionalInstanceField(view, OWNER, owner)
                active = WeakReference(owner)
            }
        })
        ModuleHelper.hookMethod(access.detached, object : MethodHook() {
            override fun after(callback: AfterHookCallback) = ModuleHelper.guarded {
                val view = callback.getThisObject() ?: return@guarded
                (XposedHelpers.removeAdditionalInstanceField(view, OWNER) as? Owner)?.dispose()
            }
        })
        ModuleHelper.hookMethod(access.touch, object : MethodHook() {
            override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                // NavStubView can rewrite a MOVE to DOWN internally; inspect the original event.
                (callback.getArg(0) as? MotionEvent)?.let { active.get()?.touch(it) }
            }
        })
        var fastInstalled = false
        fun animationHook(method: java.lang.reflect.Method) = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                if (access.animationEntry(chain.thisObject, fastInstalled) != method) return chain.proceed()
                val original = chain.getArg(0) ?: return chain.proceed()
                val owner = active.get()
                val attempt = ModuleHelper.guarded<Attempt?>(null) {
                    val element = chain.thisObject ?: return@guarded null
                    owner?.prefetchOpening(original, element)
                    owner?.retarget(original, element)
                }
                if (attempt == null) return chain.proceed()
                // Native null-target cleanup retires the floating icon before the spring.
                // Keep its task leash, listeners, velocity and cancellation policy intact.
                val result = chain.proceed(arrayOf(attempt.params))
                ModuleHelper.guarded { owner?.committed(attempt, chain.thisObject!!) }
                return result
            }
        }
        access.fastAnimate?.let { fastInstalled = ModuleHelper.hookMethod(it, animationHook(it)) != null }
        ModuleHelper.hookMethod(access.animate, animationHook(access.animate))
    }

    private class Owner(host: View, private val prefs: PrefMap, private val access: LauncherMusicReturnAccess) :
        ScreenStateController.ScreenStateListener {
        private val view = WeakReference(host)
        private val context = host.context.applicationContext
        private val handler = Handler(Looper.getMainLooper())
        private val queued = AtomicBoolean()
        private val gesture = AtomicLong()
        private val activeAttempt = AtomicLong()
        private var committedAttempt = -1L
        private val impulse = IslandGestureImpulse()
        @Volatile private var disposed = false
        @Volatile private var options = IslandOptions.read(prefs)
        @Volatile private var returnPackages = options.returnPackages()
        @Volatile private var snapshot: IslandMusicReturnSignal.Snapshot? = null
        @Volatile private var scope: CoroutineScope? = null
        private var commitJob: Job? = null
        private var sequence = -1L
        private var registered = false
        private var reported = false
        private val diagnostics = IslandDiagnostics()
        private val reconfigure = Runnable { ModuleHelper.guarded { configure() } }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (disposed || !registered) return@guarded
                if (!ModuleHelper.isTrustedBroadcast(this, IslandMusicReturnSignal.SYSTEM_UI)) {
                    diagnostics.report("music-return-untrusted-state"); return@guarded
                }
                val next = IslandMusicReturnSignal.decode(intent, SystemClock.uptimeMillis()) ?: return@guarded
                if (next.sequence > sequence) { sequence = next.sequence; snapshot = next }
            }
        }
        private val observer = object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) {
                if (disposed) return
                if (key != null && key != "system_strong_toast_mode" && !key.startsWith("system_island_")) return
                if (Looper.myLooper() == Looper.getMainLooper()) configure()
                else { handler.removeCallbacks(reconfigure); handler.post(reconfigure) }
            }
        }
        init {
            ModuleHelper.observePreferenceChange(observer, this)
            try { configure() } catch (error: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(error)
                dispose()
                throw error
            }
        }
        private fun enabled() = !disposed && options.enabled && returnPackages.isNotEmpty() &&
            !options.simplified && ValueAnimator.areAnimatorsEnabled()

        private fun configure() {
            if (disposed) return
            options = IslandOptions.read(prefs)
            returnPackages = options.returnPackages()
            invalidateAttempt()
            snapshot = null
            if (!enabled()) { stop(); return }
            if (!registered) {
                context.registerReceiver(receiver, IntentFilter(IslandMusicReturnSignal.STATE), null, handler, Context.RECEIVER_EXPORTED)
                registered = true
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + ModuleHelper.coroutineFailureHandler)
                ScreenStateController.addListener(context, this)
            }
            request()
        }
        fun request(gesture: Boolean = false) {
            val tasks = scope ?: return
            if (!enabled()) return
            if (gesture) invalidateAttempt()
            if (!queued.compareAndSet(false, true)) return
            tasks.launch {
                try {
                    if (enabled()) ModuleHelper.guarded {
                        ModuleHelper.sendBroadcastWithIdentity(context, Intent(IslandMusicReturnSignal.REQUEST)
                            .setPackage(IslandMusicReturnSignal.SYSTEM_UI).addFlags(Intent.FLAG_RECEIVER_FOREGROUND))
                    }
                } finally { queued.set(false) }
            }
        }
        fun touch(event: MotionEvent) {
            if (!enabled()) return
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { impulse.begin(event.rawY, event.eventTime); request(gesture = true) }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> if (event.pointerCount == 1)
                    impulse.move(event.rawY, event.eventTime, context.resources.displayMetrics.density) else impulse.cancel()
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> impulse.cancel()
            }
        }
        fun retarget(original: Any, element: Any): Attempt? {
            if (!enabled() || !ScreenStateController.isScreenOn()) return null
            val epoch = gesture.get()
            val current = snapshot
            if (current == null) { diagnostics.report("music-return-no-snapshot"); return null }
            val frame = current.frame ?: return null
            if (!current.fresh(SystemClock.uptimeMillis()) || frame.packageName !in returnPackages) return null
            val nav = view.get()?.takeIf { it.isAttachedToWindow } ?: return null
            if (nav.display?.rotation != android.view.Surface.ROTATION_0) return null
            val result = access.retarget(original, element, nav, frame) ?: return null
            if (epoch != gesture.get()) return null
            return Attempt(result, current, epoch, IslandApplicationOverlaySignal.nextSequence(), impulse.strength)
                .also { activeAttempt.set(it.sequence) }
        }
        fun prefetchOpening(original: Any, element: Any) {
            if (!enabled() || !ScreenStateController.isScreenOn()) return
            val opening = access.openingPackage(original, element) ?: return
            invalidateAttempt()
            if (opening in returnPackages) request()
        }
        fun selectWindowTarget(native: Any): Boolean {
            val nav = view.get()?.takeIf { it === native && it.isAttachedToWindow } ?: return false
            if (!enabled() || !ScreenStateController.isScreenOn() || nav.display?.rotation != android.view.Surface.ROTATION_0) return false
            val current = snapshot ?: run { diagnostics.report("music-return-target-not-ready"); return false }
            val frame = current.frame ?: run { diagnostics.report("music-return-target-inactive"); return false }
            if (!current.fresh(SystemClock.uptimeMillis())) {
                diagnostics.report("music-return-target-expired"); return false
            }
            if (frame.packageName !in returnPackages) return false
            return access.selectWindowTarget(nav, frame)
        }
        fun committed(attempt: Attempt, element: Any, deferred: Boolean = false) {
            val tasks = scope ?: return
            if (!enabled() || attempt.sequence == committedAttempt || !current(attempt)) return
            if (!access.accepted(attempt.params, element)) {
                // An interrupted opening can start its replacement spring at the end of
                // this native dispatch. Recheck once on the existing looper, never poll.
                if (!deferred) handler.post { committed(attempt, element, deferred = true) }
                else diagnostics.report("music-return-native-not-running")
                return
            }
            committedAttempt = attempt.sequence
            val current = attempt.snapshot
            val message = IslandMusicReturnSignal.encode(current.frame, current.issuedAt, current.sequence)
                .setAction(IslandMusicReturnSignal.REQUEST).setPackage(IslandMusicReturnSignal.SYSTEM_UI)
                .putExtra("committed", true).putExtra("gesture", attempt.sequence)
                .putExtra("impulse", attempt.impulse)
                .putExtra("committedAt", SystemClock.uptimeMillis()).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            commitJob?.cancel()
            commitJob = tasks.launch {
                if (enabled() && current(attempt)) ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(context, message) }
            }
            if (!reported) {
                reported = true
                XposedHelpers.log("Music Island: native CLOSE_TO_HOME target accepted")
            }
        }
        override fun onScreenStateChanged(isOn: Boolean) {
            if (!isOn) { invalidateAttempt(); snapshot = null } else request()
        }
        private fun current(attempt: Attempt) = attempt.epoch == gesture.get() && attempt.sequence == activeAttempt.get()
        private fun invalidateAttempt() { gesture.incrementAndGet(); activeAttempt.set(0); commitJob?.cancel(); commitJob = null }
        private fun stop() {
            invalidateAttempt()
            snapshot = null
            commitJob?.cancel(); commitJob = null
            scope?.cancel(); scope = null
            queued.set(false)
            if (registered) {
                registered = false
                try { context.unregisterReceiver(receiver) } finally { ModuleHelper.guarded { ScreenStateController.removeListener(this) } }
            }
        }
        fun dispose() {
            if (disposed) return
            disposed = true
            ModuleHelper.unregisterPreferenceObserver(this)
            // The handler also delivers framework broadcasts; let queued deliveries finish.
            handler.removeCallbacks(reconfigure)
            stop()
        }
    }
}
