package tv.withaibuild.customiuizer.mods.island

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.UserHandle
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.animation.ValueAnimator
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** HyperOS 1's pre-WindowElement gesture path. Never installs alongside WindowElement. */
internal object LegacyLauncherIslandReturn {
    private const val OWNER = "customiuizer_legacy_island_return_owner"
    @Volatile private var active = WeakReference<Owner>(null)

    fun install(param: PackageReadyParam, prefs: PrefMap) {
        val api = Access(param.classLoader)
        installIslandReturnHooks { hook ->
            hook(api.attached, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    ModuleHelper.guarded {
                        active.get()?.dispose()
                        val view = chain.thisObject as View
                        val owner = Owner(view, prefs, api)
                        XposedHelpers.setAdditionalInstanceField(view, OWNER, owner)
                        active = WeakReference(owner)
                    }
                    return result
                }
            })
            hook(api.detached, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? = try { chain.proceed() }
                finally { ModuleHelper.guarded {
                    active.get()?.takeIf { it.nav.get() === chain.thisObject }?.dispose()
                    XposedHelpers.removeAdditionalInstanceField(chain.thisObject, OWNER)
                } }
            })
            hook(api.touch, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    ModuleHelper.guarded {
                        if ((chain.getArg(0) as? MotionEvent)?.actionMasked == MotionEvent.ACTION_DOWN)
                            active.get()?.takeIf { it.nav.get() === chain.thisObject }?.invalidateAndRequest()
                    }
                    return chain.proceed()
                }
            })
            hook(api.closing, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val selected = ModuleHelper.guarded(false) { active.get()?.select(chain.thisObject) == true }
                    return if (selected) null else chain.proceed()
                }
            })
            hook(api.init, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    ModuleHelper.guarded { active.get()?.prepared(chain.thisObject, chain.getArg(0)) }
                    return result
                }
            })
            hook(api.start, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    ModuleHelper.guarded { active.get()?.start(chain.thisObject) }
                    return result
                }
            })
            hook(api.update, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val owner = active.get()
                    val spring = chain.thisObject ?: return chain.proceed()
                    if (owner?.owns(spring) != true) return chain.proceed()
                    val result = chain.proceed()
                    ModuleHelper.guarded { owner.frame(spring) }
                    return result
                }
            })
            hook(api.transform, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    ModuleHelper.guarded {
                        active.get()?.alphaFor(chain.thisObject)?.let { api.setAlpha(chain.thisObject!!, it) }
                    }
                    return result
                }
            })
            for (ending in listOf(api.end, api.cancel)) hook(ending, object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val owner = active.get()
                    val spring = chain.thisObject
                    val ticket = owner?.ticket(spring)
                    return try { chain.proceed() } finally {
                        ModuleHelper.guarded { owner?.finish(spring, ticket, ending == api.end) }
                    }
                }
            })
        }
    }

    internal data class Task(val task: Int, val infoTask: Int, val user: Int, val infoUser: Int,
        val display: Int, val windowingMode: Int, val component: String?, val top: String?,
        val quickSwitch: Boolean, val autoPip: Boolean)

    internal fun eligible(task: Task, frame: IslandMusicReturnSignal.Frame): Boolean =
        frame.valid() && !task.quickSwitch && !task.autoPip && task.task == task.infoTask && task.user == task.infoUser &&
            task.display == 0 && task.windowingMode == 1 && task.user in 0..10000 &&
            frame.user == UserHandle.getUserHandleForUid(task.user * 100000) &&
            task.component == frame.packageName && task.top == frame.packageName

    private class Access(loader: ClassLoader) {
        val nav = XposedHelpers.findClass("com.miui.home.recents.NavStubView", loader)
        val spring = XposedHelpers.findClass("com.miui.home.recents.util.RectFSpringAnim", loader)
        private val target = XposedHelpers.findClass("com.miui.home.launcher.anim.LaunchAppAndBackHomeAnimTarget", loader)
        val attached = nav.getDeclaredMethod("onAttachedToWindow")
        val detached = nav.getDeclaredMethod("onDetachedFromWindow")
        val touch = nav.getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
        val closing = nav.getDeclaredMethod("findClosingAnimTarget", Boolean::class.javaPrimitiveType)
        val init = nav.getDeclaredMethod("initAppToHomeAnim", spring, Int::class.javaPrimitiveType, target, Boolean::class.javaPrimitiveType)
        val start = spring.getDeclaredMethod("startAllAnim")
        val update = spring.getDeclaredMethod("onUpdate")
        val end = spring.declaredMethods.single { it.name.startsWith("lambda\$init\$") &&
            it.parameterTypes.lastOrNull() == Boolean::class.javaPrimitiveType }
        val cancel = spring.getDeclaredMethod("cancel")
        fun field(type: Class<*>, name: String) = type.getDeclaredField(name).apply { isAccessible = true }
        val releaseAlpha = field(nav, "mCurTaskAlpha")
        val transform = nav.getDeclaredMethod("setAppTransformParams", RectF::class.java,
            Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Float::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        private val transformParams = field(nav, "mTransformParams")
        private val targetAlpha = transformParams.type.getDeclaredMethod("setTargetAlpha", Float::class.javaPrimitiveType)
        fun setAlpha(nav: Any, alpha: Float) { targetAlpha.invoke(transformParams.get(nav), alpha) }
        val rectangle = field(spring, "mCurrentRect")
        val radius = field(spring, "mCurrentRadius")
        val progress = spring.getDeclaredMethod("getProgress").apply { isAccessible = true }
        val appSpring = field(nav, "mAppToHomeAnim2")
        val destination = field(nav, "mAppToHomeTargetRect")
        val task = field(nav, "mRunningTaskId")
        val user = field(nav, "mRunningTaskUserId")
        val component = field(nav, "mRunningTaskComponentName")
        val info = field(nav, "mRunningTaskInfo")
        private val infoUser = apiField("userId")
        private val infoDisplay = apiField("displayId")
        private val pip = apiField("pictureInPictureParams")
        private val autoPip = pip.type.getMethod("isAutoEnterEnabled")
        private val windowMode = info.type.getMethod("getWindowingMode")
        private fun apiField(name: String) = XposedHelpers.findField(info.type, name)
        val quickSwitch = nav.getDeclaredMethod("isQuickSwitchMode").apply { isAccessible = true }
        private val listener = field(nav, "mRecentsAnimationListenerImpl")
        private val targets = listener.type.getDeclaredMethod("getTargetSet").apply { isAccessible = true }
        private val apps = field(targets.returnType, "apps")
        private val remote = XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat", loader)
        private val remoteTask = field(remote, "taskId")
        private val remotePackage = field(remote, "packageName")
        private val remoteMode = field(remote, "mode")
        private val translucent = field(remote, "isTranslucent")
        private val leash = field(remote, "leash")
        private val surface = field(leash.type, "mSurfaceControl")
        val capture = LauncherWindowAbsorption.Capture(loader)
        private val launcher = XposedHelpers.findClass("com.miui.home.launcher.Application", loader).getDeclaredMethod("getLauncher")
        fun root() = (launcher.invoke(null) as? android.app.Activity)?.window?.decorView
        fun task(nav: Any): Task? {
            val info = info.get(nav) as? ActivityManager.RunningTaskInfo ?: return null
            return Task(task.getInt(nav), info.taskId, user.getInt(nav), infoUser.getInt(info), infoDisplay.getInt(info),
                windowMode.invoke(info) as Int, (component.get(nav) as? ComponentName)?.packageName,
                info.topActivity?.packageName, quickSwitch.invoke(nav) == true, pip.get(info)?.let { autoPip.invoke(it) } == true)
        }
        fun surface(nav: Any, frame: IslandMusicReturnSignal.Frame): SurfaceControl? {
            val set = listener.get(nav)?.let { targets.invoke(it) } ?: return null
            val app = (apps.get(set) as? Array<*>)?.singleOrNull() ?: return null
            if (remoteTask.getInt(app) != task.getInt(nav) || remotePackage.get(app) != frame.packageName ||
                remoteMode.getInt(app) != 1 || translucent.getBoolean(app)) return null
            return surface.get(leash.get(app)) as? SurfaceControl
        }
    }

    private class Attempt(val state: IslandMusicReturnSignal.Snapshot, val surface: SurfaceControl?,
        val releaseAlpha: Float) {
        val sequence = IslandApplicationOverlaySignal.nextSequence()
        @Volatile var spring = WeakReference<Any>(null)
        val rect = RectF()
        var progress = 0f; var radius = 0f; var alpha = 1f
        var queued = false
    }

    private class Owner(view: View, private val prefs: PrefMap, private val api: Access) : ScreenStateController.ScreenStateListener {
        val nav = WeakReference(view)
        private val context = view.context.applicationContext
        private val handler = Handler(Looper.getMainLooper())
        @Volatile private var options = IslandOptions.read(prefs)
        @Volatile private var enabled = false
        @Volatile private var snapshot: IslandMusicReturnSignal.Snapshot? = null
        private var sequence = -1L
        private var publisher: IBinder? = null
        private var death: IBinder.DeathRecipient? = null
        private var registered = false
        private var disposed = false
        private var scope: CoroutineScope? = null
        private val requested = AtomicBoolean()
        private val selected = AtomicReference<Attempt?>()
        @Volatile private var meshAttempt: Attempt? = null
        private var reported = false
        private val timing = LegacyLauncherReturnTiming(api.spring)
        val mesh = LauncherWindowAbsorption(object : LauncherWindowAbsorption.NativeAccess {
            override fun root() = api.root()
            override fun current(element: Any, copy: Any) = meshAttempt === copy && copy.spring.get() === element &&
                nav.get()?.let { api.appSpring.get(it) === element } == true
            override fun translucent(copy: Any) = false
            override fun surface(element: Any, copy: Any) = (copy as Attempt).surface
            override fun capture(surface: SurfaceControl, width: Int, height: Int) = api.capture.capture(surface, width, height)
        }, handler)
        private fun dispatch(block: () -> Unit) {
            if (handler.looper.isCurrentThread) ModuleHelper.guarded(block = block)
            else handler.post { ModuleHelper.guarded(block = block) }
        }
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (!enabled || !ModuleHelper.isTrustedBroadcast(this, IslandMusicReturnSignal.SYSTEM_UI)) return@guarded
                val next = IslandMusicReturnSignal.decode(intent, SystemClock.uptimeMillis()) ?: return@guarded
                if (next.sequence <= sequence) return@guarded
                val source = intent.extras?.getBinder("owner") ?: return@guarded
                if (publisher !== source) {
                    unlink()
                    val callback = IBinder.DeathRecipient { dispatch { if (publisher === source) { snapshot = null; invalidate(); unlink() } } }
                    source.linkToDeath(callback, 0); publisher = source; death = callback
                }
                sequence = next.sequence; snapshot = next
            }
        }
        private val observer = object : ModuleHelper.PreferenceObserver {
            override fun onChange(key: String?) {
                if (key == null || key == "system_strong_toast_mode" || key.startsWith("system_island_")) dispatch { configure() }
            }
        }
        init {
            ModuleHelper.observePreferenceChange(observer, this)
            try { configure() } catch (error: Throwable) {
                try { dispose() } finally { FatalErrors.unwrapAndRethrowIfFatal(error) }
                throw error
            }
        }
        private fun configure() {
            if (disposed) return
            invalidate(); snapshot = null
            options = IslandOptions.read(prefs)
            enabled = options.enabled && options.returnEffectEnabled && options.returnPackages().isNotEmpty() && ValueAnimator.areAnimatorsEnabled()
            if (!enabled) { stop(); return }
            if (!registered) {
                context.registerReceiver(receiver, IntentFilter(IslandMusicReturnSignal.STATE), null, handler, Context.RECEIVER_EXPORTED)
                registered = true
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + ModuleHelper.coroutineFailureHandler)
                ScreenStateController.addListener(context, this)
            }
            request()
        }
        private fun request() {
            val tasks = scope ?: return
            if (!enabled || !requested.compareAndSet(false, true)) return
            tasks.launch {
                try { if (enabled) ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(context,
                    Intent(IslandMusicReturnSignal.REQUEST).setPackage(IslandMusicReturnSignal.SYSTEM_UI).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)) } }
                finally { requested.set(false) }
            }
        }
        fun invalidateAndRequest() { invalidate(); request() }
        fun select(native: Any?): Boolean {
            val view = nav.get()?.takeIf { it === native && it.isAttachedToWindow && it.display?.rotation == 0 } ?: return false
            val state = snapshot?.takeIf { it.fresh(SystemClock.uptimeMillis()) } ?: return false
            val frame = state.frame ?: return false
            if (!enabled || !ScreenStateController.isScreenOn() || frame.packageName !in options.returnPackages() ||
                api.task(view)?.let { eligible(it, frame) } != true) return false
            val surface = api.surface(view, frame) ?: return false
            val alpha = api.releaseAlpha.getFloat(view).takeIf { it.isFinite() && it in 0f..1f } ?: return false
            val target = frame.landingRectangle()
            (api.destination.get(view) as Rect).set(target.left.toInt(), target.top.toInt(), target.right.toInt(), target.bottom.toInt())
            selected.set(Attempt(state, surface, alpha))
            return true
        }
        fun prepared(native: Any?, spring: Any?) {
            val attempt = selected.get() ?: return
            val view = nav.get()?.takeIf { it === native } ?: return
            if (spring != null && api.appSpring.get(view) === spring) {
                attempt.spring = WeakReference(spring)
                timing.apply(spring)
            }
        }
        fun owns(spring: Any) = selected.get()?.spring?.get() === spring
        fun alphaFor(native: Any?): Float? {
            val view = nav.get()?.takeIf { it === native } ?: return null
            val attempt = selected.get() ?: return null
            val spring = attempt.spring.get()?.takeIf { api.appSpring.get(view) === it } ?: return null
            return if (mesh.hides(spring)) 0f else attempt.releaseAlpha
        }
        fun ticket(spring: Any?) = selected.get()?.takeIf { it.spring.get() === spring }
        fun start(spring: Any?) {
            val attempt = ticket(spring) ?: return
            dispatch {
                if (!enabled || selected.get() !== attempt || spring == null) return@dispatch
                val tasks = scope ?: return@dispatch
                val frame = attempt.state.frame ?: return@dispatch
                meshAttempt = attempt
                val progress = mesh.begin(tasks, spring, attempt, frame,
                    instant = options.returnEffect == IslandReturnEffect.INSTANT,
                    headerPercent = options.absorptionHeaderPercent)
                val message = IslandMusicReturnSignal.encode(frame, SystemClock.uptimeMillis(), attempt.state.sequence)
                    .setAction(IslandMusicReturnSignal.REQUEST).setPackage(IslandMusicReturnSignal.SYSTEM_UI)
                    .putExtra("committed", true).putExtra("gesture", attempt.sequence).putExtra("impulse", .5f)
                    .putExtra("committedAt", SystemClock.uptimeMillis()).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    .putExtras(android.os.Bundle().apply { putBinder("progress", progress) })
                tasks.launch { if (enabled && selected.get() === attempt) ModuleHelper.guarded { ModuleHelper.sendBroadcastWithIdentity(context, message) } }
                if (!reported) { reported = true; XposedHelpers.log("Music Island: legacy native Home target accepted") }
            }
        }
        fun frame(spring: Any) {
            val attempt = ticket(spring) ?: return
            synchronized(attempt) {
                attempt.rect.set(api.rectangle.get(spring) as RectF)
                attempt.progress = api.progress.invoke(spring) as Float
                attempt.radius = api.radius.getFloat(spring); attempt.alpha = attempt.releaseAlpha
                if (attempt.queued) return
                attempt.queued = true
            }
            handler.post { ModuleHelper.guarded {
                synchronized(attempt) {
                    attempt.queued = false
                    if (selected.get() === attempt) mesh.frame(spring, attempt.rect, attempt.progress, attempt.radius, attempt.alpha)
                }
            } }
        }
        fun finish(spring: Any?, attempt: Attempt?, completed: Boolean) {
            if (attempt == null || spring == null) return
            dispatch {
                if (selected.get() === attempt) {
                    mesh.end(spring, mesh.lease(spring), completed)
                    timing.restore(spring)
                    meshAttempt = null
                    selected.compareAndSet(attempt, null)
                }
            }
        }
        private fun invalidate() {
            val old = selected.getAndSet(null)
            dispatch {
                if (meshAttempt === old) { mesh.cancel(); timing.restore(); meshAttempt = null }
            }
        }
        override fun onScreenStateChanged(isOn: Boolean) {
            invalidate(); snapshot = null
            if (isOn) request()
        }
        private fun unlink() {
            death?.let { callback -> publisher?.let { ModuleHelper.guarded { it.unlinkToDeath(callback, 0) } } }
            death = null; publisher = null
        }
        private fun stop() {
            enabled = false; snapshot = null; invalidate(); unlink()
            scope?.cancel(); scope = null; requested.set(false)
            if (registered) {
                registered = false
                try { context.unregisterReceiver(receiver) } finally { ScreenStateController.removeListener(this) }
            }
        }
        fun dispose() {
            if (disposed) return
            disposed = true; ModuleHelper.unregisterPreferenceObserver(this); stop()
        }
    }
}
