package tv.withaibuild.customiuizer.mods.island

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import io.github.libxposed.api.XposedInterface
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Native Session inventories own the surfaces. We keep no windows, sessions or app controllers. */
internal object IslandApplicationOverlayHooks {
    @Volatile private var controller: Controller? = null

    fun install(loader: ClassLoader) = ModuleHelper.guarded {
        val access = IslandApplicationOverlayAccess(loader)
        val handler = XposedHelpers.callStaticMethod(XposedHelpers.findClass(
            "com.android.server.IoThread", loader), "getHandler") as Handler
        fun attach(service: Any) {
            if (controller?.owns(service) == true) return
            controller?.close()
            controller = Controller(service, access).also { it.start() }
        }
        check(ModuleHelper.hookMethod(access.visibilityChanged, object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() == null && callback.getArg(2) == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                    controller?.schedule()
                }
            }
        }) != null)
        check(ModuleHelper.hookMethod(access.killSession, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val session = chain.thisObject
                val hadSurfaces = ModuleHelper.guarded(false) {
                    session != null && (access.surfaces.get(session) as Collection<*>).isNotEmpty()
                }
                val result = chain.proceed()
                if (hadSurfaces) ModuleHelper.guarded { controller?.schedule() }
                return result
            }
        }) != null)
        ModuleHelper.hookAllConstructors(access.wms, object : MethodHook() {
            override fun after(callback: AfterHookCallback) {
                if (callback.getThrowable() == null) callback.getThisObject()?.let {
                    val owner = WeakReference(it)
                    handler.post { ModuleHelper.guarded { owner.get()?.let(::attach) } }
                }
            }
        })
        // Catch-up installation after preference bootstrap may find an already-published WMS.
        val manager = XposedHelpers.findClass("android.os.ServiceManager", loader)
        handler.post { ModuleHelper.guarded {
            XposedHelpers.callStaticMethod(manager, "getService", "window")?.takeIf { access.wms.isInstance(it) }?.let(::attach)
        } }
    }

    /** One SS-lifetime owner, one coalesced native-IO-handler task and one scalar snapshot. */
    private class Controller(serviceOwner: Any, private val access: IslandApplicationOverlayAccess) : AutoCloseable {
        private val owner = WeakReference(serviceOwner)
        private val context = access.context.get(serviceOwner) as Context
        private val handler = XposedHelpers.callStaticMethod(XposedHelpers.findClass(
            "com.android.server.IoThread", access.wms.classLoader), "getHandler") as Handler
        private val pending = AtomicBoolean()
        @Volatile private var closed = false
        private var snapshot = IslandApplicationOverlaySignal.Snapshot(emptySet(), false)
        private val refresh = Runnable {
            pending.set(false)
            ModuleHelper.guarded {
                if (closed) return@guarded
                val state = owner.get()?.let(access::snapshot) ?: return@guarded
                if (state != snapshot) { snapshot = state; publish() }
            }
        }
        private val request = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = ModuleHelper.guarded {
                if (closed || intent.action != IslandApplicationOverlaySignal.REQUEST || getSentFromUid() != 1000 ||
                    !ModuleHelper.isTrustedBroadcast(this, IslandApplicationOverlaySignal.SYSTEM_UI)) return@guarded
                val at = intent.getLongExtra("at", -1)
                val now = SystemClock.uptimeMillis()
                val nonce = intent.getLongExtra("nonce", -1)
                if (nonce <= 0 || at < 0 || now < at || now - at > IslandApplicationOverlaySignal.LIFETIME) return@guarded
                owner.get()?.let { snapshot = access.snapshot(it) }
                publish(nonce)
            }
        }
        fun owns(value: Any) = owner.get() === value
        fun start() {
            context.registerReceiver(request, IntentFilter(IslandApplicationOverlaySignal.REQUEST), null, handler, Context.RECEIVER_EXPORTED)
            schedule()
        }
        fun schedule() { if (!closed && pending.compareAndSet(false, true)) handler.post(refresh) }
        private fun publish(nonce: Long = 0) = ModuleHelper.sendBroadcastWithIdentity(context,
            IslandApplicationOverlaySignal.encode(snapshot, IslandApplicationOverlaySignal.nextSequence(), nonce))
        override fun close() {
            closed = true
            handler.removeCallbacks(refresh)
            pending.set(false)
            ModuleHelper.guarded { context.unregisterReceiver(request) }
        }
    }
}

/** Bounded scan only after native overlay visibility/death events or a new subscriber request. */
internal class IslandApplicationOverlayAccess(
    val wms: Class<*>, session: Class<*>, surface: Class<*>, animator: Class<*>, window: Class<*>,
) {
    constructor(loader: ClassLoader) : this(
        XposedHelpers.findClass("com.android.server.wm.WindowManagerService", loader),
        XposedHelpers.findClass("com.android.server.wm.Session", loader),
        XposedHelpers.findClass("com.android.server.wm.WindowSurfaceController", loader),
        XposedHelpers.findClass("com.android.server.wm.WindowStateAnimator", loader),
        XposedHelpers.findClass("com.android.server.wm.WindowState", loader))

    val visibilityChanged = session.getDeclaredMethod("onWindowSurfaceVisibilityChanged", surface,
        Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
    val killSession = session.getDeclaredMethod("killSessionLocked").apply { isAccessible = true }
    val service = XposedHelpers.findField(session, "mService")
    val surfaces = XposedHelpers.findField(session, "mAppOverlaySurfaces")
    val context = XposedHelpers.findField(wms, "mContext")
    private val lock = XposedHelpers.findField(wms, "mGlobalLock")
    private val sessions = XposedHelpers.findField(wms, "mSessions")
    private val uid = XposedHelpers.findField(session, "mUid")
    private val animation = XposedHelpers.findField(surface, "mAnimator")
    private val win = XposedHelpers.findField(animator, "mWin")
    private val display = window.getDeclaredMethod("getDisplayId").apply { isAccessible = true }

    fun snapshot(owner: Any): IslandApplicationOverlaySignal.Snapshot = synchronized(lock.get(owner)) {
        val users = linkedSetOf<Int>()
        var remaining = 4096
        for (session in sessions.get(owner) as Collection<*>) {
            if (--remaining < 0) return@synchronized IslandApplicationOverlaySignal.Snapshot(emptySet(), false)
            val ownerUid = uid.getInt(session)
            if (ownerUid < 0 || ownerUid % 100000 < 10000) continue
            for (surface in surfaces.get(session) as Collection<*>) {
                if (--remaining < 0) return@synchronized IslandApplicationOverlaySignal.Snapshot(emptySet(), false)
                if (display.invoke(win.get(animation.get(surface))) == Display.DEFAULT_DISPLAY) users.add(ownerUid / 100000)
                if (users.size > IslandApplicationOverlaySignal.MAX_USERS)
                    return@synchronized IslandApplicationOverlaySignal.Snapshot(emptySet(), false)
            }
        }
        IslandApplicationOverlaySignal.Snapshot(users)
    }
}
