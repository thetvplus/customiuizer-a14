package tv.withaibuild.customiuizer.mods

import android.animation.Animator
import android.app.KeyguardManager
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.view.SurfaceControl
import android.view.accessibility.AccessibilityNodeInfo
import tv.withaibuild.customiuizer.mods.island.IslandOptions
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.hasConfiguredActionCode
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Optional, installation-time global actions. No service, poller, media command or per-frame hook. */
internal object SystemPipActions {
    const val HIDE = 33
    const val STASH = 34
    @Volatile var visibilityObserver: ((Any, Boolean) -> Unit)? = null
    @Volatile private var access: PipWindowAccess? = null
    @Volatile private var owner = WeakReference<Any>(null)
    @Volatile private var control: PipWindowControl? = null
    private var hideInstalled = false
    private var stashInstalled = false
    private val pending = AtomicBoolean()
    @Volatile private var ownerGeneration = 0L
    @Volatile internal var screenshotHidden = false
        private set

    /** Combines the existing screenshot owner with the explicit gesture lease. */
    fun screenshotVisibility(organizer: Any, shown: Boolean): Boolean {
        val resolved = access ?: return shown
        val current = owner.get() ?: return shown
        if (resolved.organizer(current) !== organizer) return shown
        screenshotHidden = !shown
        return shown && control?.isHidden != true
    }

    internal fun selected(prefs: PrefMap, code: Int): Boolean {
        if (hasConfiguredActionCode(code)) return true
        val island = IslandOptions.read(prefs)
        if (!island.enabled) return false
        if (island.idle) {
            val idle = island.idleActions
            if (idle.tap == code || listOf(idle.gestures.left, idle.gestures.right, idle.gestures.down,
                    idle.gestures.doubleTap, idle.gestures.longPress).any { it == code }) return true
        }
        if (!island.media) return false
        val actions = island.musicActions
        return actions.left == code || actions.right == code || actions.down == code ||
            actions.doubleTap == code || actions.longPress == code
    }

    fun install(loader: ClassLoader, prefs: PrefMap) {
        if (access != null) return
        val hide = selected(prefs, HIDE)
        val stash = selected(prefs, STASH)
        if (!hide && !stash) return
        val installed = ArrayList<CustomMethodUnhooker>()
        try {
            val resolved = PipWindowAccess(loader)
            val capture = object : MethodHook() {
                override fun after(callback: AfterHookCallback) {
                    if (callback.getThrowable() != null) return
                    val next = callback.getThisObject() ?: return
                    resolved.executor(next).execute {
                        ModuleHelper.guarded {
                            if (owner.get() === next) return@guarded
                            // Return the old task's borrowed Surface/input on its Shell executor
                            // before replacement. The port revalidates that old task; a new task
                            // never receives the old lease or a delayed old command.
                            ModuleHelper.guarded { control?.nativeTransition() }
                            ownerGeneration++
                            screenshotHidden = false
                            pending.set(false)
                            owner = WeakReference(next)
                            control = PipWindowControl(resolved.port(next))
                        }
                    }
                }
            }
            if (hide) {
                installed.add(requireNotNull(ModuleHelper.hookMethod(resolved.visibility, object : MethodHook() {
                    override fun before(callback: BeforeHookCallback) {
                        val current = owner.get() ?: return
                        val lease = control?.takeIf { it.isHidden } ?: return
                        if (resolved.organizer(current) !== callback.getThisObject()) return
                        val requested = callback.getArg(0) as Boolean
                        val applied = lease.nativeVisibility(requested)
                        if (applied != requested) callback.getArgs()[0] = applied
                    }
                })))
                installed.add(requireNotNull(ModuleHelper.hookMethod(resolved.stateChanged, object : MethodHook() {
                    override fun before(callback: BeforeHookCallback) {
                        val lease = control?.takeIf { it.isHidden } ?: return
                        val current = owner.get() ?: return
                        if (resolved.stateOwner(current) === callback.getThisObject() && callback.getArg(0) != 4)
                            lease.nativeTransition()
                    }
                })))
                installed.add(requireNotNull(ModuleHelper.hookMethod(resolved.animationStart, object : MethodHook() {
                    override fun before(callback: BeforeHookCallback) {
                        if (control?.isHidden == true)
                            control?.nativeTransition(resolved.animationSurface(callback.getThisObject() ?: return))
                    }
                })))
            }
            for (constructor in resolved.controllerClass.declaredConstructors) {
                val parameters = constructor.parameterTypes.map { it as Any? }.toMutableList()
                parameters.add(capture)
                installed.add(requireNotNull(ModuleHelper.findAndHookConstructor(resolved.controllerClass.name,
                    loader, *parameters.toTypedArray())))
            }
            hideInstalled = hide; stashInstalled = stash; access = resolved
        } catch (error: Throwable) {
            installed.forEach { ModuleHelper.guarded { it.unhook() } }
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("PiP gesture actions: native contract unavailable", error)
        }
    }

    fun request(action: Int): Boolean {
        if (action == HIDE && !hideInstalled || action == STASH && !stashInstalled || action != HIDE && action != STASH) return false
        val resolved = access ?: return false
        val current = owner.get() ?: return false
        val active = control ?: return false
        val port = resolved.port(current)
        val generation = ownerGeneration
        val task = port.task() ?: return false
        if (!task.stable || task.locked || task.display != 0 || screenshotHidden || !pending.compareAndSet(false, true)) return false
        return try {
            resolved.executor(current).execute {
                try {
                    ModuleHelper.guarded {
                        if (owner.get() !== current || control !== active || screenshotHidden) return@guarded
                        if (action == HIDE) active.toggleFull(task) else active.toggleStash(task)
                    }
                } finally { if (generation == ownerGeneration) pending.set(false) }
            }
            true
        } catch (error: Throwable) {
            if (generation == ownerGeneration) pending.set(false)
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("PiP gesture action rejected", error)
            false
        }
    }
}

/** Cold A14 Shell metadata. A port borrows one weak controller; only commands access native fields. */
internal class PipWindowAccess(loader: ClassLoader) {
    val controllerClass = XposedHelpers.findClass("com.android.wm.shell.pip.phone.PipController", loader)
    private fun field(type: Class<*>, name: String) = XposedHelpers.findField(type, name)
    private val context = field(controllerClass, "mContext")
    private val main = field(controllerClass, "mMainExecutor")
    private val organizer = field(controllerClass, "mPipTaskOrganizer")
    private val transition = field(organizer.type, "mPipTransitionState")
    private val state = field(transition.type, "mState")
    private val info = field(organizer.type, "mTaskInfo")
    private val taskToken = field(info.type, "token")
    private val tokenBinder = taskToken.type.getMethod("asBinder")
    private val taskDisplay = field(info.type, "displayId")
    private val surface = field(organizer.type, "mLeash")
    private val surfaceValid = surface.type.getMethod("isValid")
    private val keyguard = field(controllerClass, "mIsKeyguardShowingOrAnimating")
    private val input = field(controllerClass, "mPipInputConsumer")
    private val receiver = field(input.type, "mInputEventReceiver")
    private val dispose = receiver.type.getMethod("dispose")
    private val channel = field(receiver.type, "mInputChannel")
    private val channelToken = channel.type.getMethod("getToken")
    private val wm = field(input.type, "mWindowManager")
    private val inputName = field(input.type, "mName")
    private val destroyInput = wm.type.getMethod("destroyInputConsumer", String::class.java, Int::class.javaPrimitiveType)
    private val registerInput = input.type.getDeclaredMethod("registerInputConsumer")
    private val touch = field(controllerClass, "mTouchHandler")
    private val registered = touch.type.getDeclaredMethod("onRegistrationChanged", Boolean::class.javaPrimitiveType)
    private val menu = field(controllerClass, "mMenuController")
    private val hideMenu = menu.type.getDeclaredMethod("hideMenu", Int::class.javaPrimitiveType)
    private val bounds = field(controllerClass, "mPipBoundsState")
    private val stashed = bounds.type.getDeclaredMethod("isStashed")
    private val connection = field(touch.type, "mConnection")
    private val connectionImpl = field(connection.type, "mConnectionImpl")
    private val connectionExecutor = field(connection.type, "mMainExcutor")
    private val callbackType = XposedHelpers.findClass("android.view.accessibility.IAccessibilityInteractionConnectionCallback", loader)
    private val performAction = connectionImpl.type.getDeclaredMethod("performAccessibilityAction",
        Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Bundle::class.java, Int::class.javaPrimitiveType,
        callbackType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType)
    private val rootNode = XposedHelpers.getStaticLongField(AccessibilityNodeInfo::class.java, "ROOT_NODE_ID")
    val visibility = organizer.type.getDeclaredMethod("setPipVisibility", Boolean::class.javaPrimitiveType)
    val stateChanged = transition.type.getDeclaredMethod("setTransitionState", Int::class.javaPrimitiveType)
    private val animator = XposedHelpers.findClass("com.android.wm.shell.pip.PipAnimationController\$PipTransitionAnimator", loader)
    val animationStart = animator.getDeclaredMethod("onAnimationStart", Animator::class.java)
    private val animatorSurface = field(animator, "mLeash")
    fun animationSurface(value: Any): Any? = animatorSurface.get(value)
    fun organizer(value: Any): Any = requireNotNull(organizer.get(value))
    fun stateOwner(value: Any): Any = requireNotNull(transition.get(organizer(value)))
    fun executor(value: Any): Executor = main.get(value) as Executor

    fun port(native: Any): PipWindowPort = object : PipWindowPort {
        private val owner = WeakReference(native)
        override fun task(): PipWindowPort.Task? {
            val current = owner.get() ?: return null
            val organizer = organizer(current)
            val info = info.get(organizer) ?: return null
            val token = taskToken.get(info) ?: return null
            val leash = surface.get(organizer) ?: return null
            if (surfaceValid.invoke(leash) != true) return null
            val ctx = context.get(current) as Context
            val locked = keyguard.getBoolean(current) || ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false
            return PipWindowPort.Task(requireNotNull(tokenBinder.invoke(token)), leash,
                state.getInt(stateOwner(current)) == 4, locked, taskDisplay.getInt(info))
        }
        override fun inputRegistered(): Boolean {
            val current = owner.get() ?: return false
            val eventReceiver = receiver.get(input.get(current)) ?: return false
            return channelToken.invoke(channel.get(eventReceiver)) != null
        }
        override fun input(enabled: Boolean): Boolean {
            val current = owner.get() ?: return false
            val consumer = input.get(current) ?: return false
            if (enabled) { registerInput.invoke(consumer); return inputRegistered() }
            val previous = receiver.get(consumer) ?: return false
            if (destroyInput.invoke(wm.get(consumer), inputName.get(consumer), 0) != true) return false
            receiver.set(consumer, null)
            dispose.invoke(previous)
            registered.invoke(touch.get(current), false)
            return true
        }
        override fun visibility(visible: Boolean, releasing: Boolean) {
            val current = owner.get() ?: return
            val target = organizer(current)
            val leash = surface.get(target) as SurfaceControl
            // Alpha alone is not an input policy. Hide the parent layer as well so even
            // bufferless descendants stop participating in input, then clear OUR flag on
            // release while retaining the native keyguard alpha. No task visibility changes.
            SurfaceControl.Transaction().use { transaction ->
                transaction.setAlpha(leash, if (visible) 1f else 0f)
                transaction.setVisibility(leash, (releasing || visible) && !SystemPipActions.screenshotHidden)
                transaction.apply()
            }
            SystemPipActions.visibilityObserver?.invoke(target, visible)
        }
        override fun hideMenu() { owner.get()?.let { hideMenu.invoke(menu.get(it), 0) } }
        override fun toggleStash(): Boolean {
            val current = owner.get() ?: return false
            val target = connection.get(touch.get(current)) ?: return false
            // The native accessibility entry uses the same executor and native bounds/animation
            // path as touch stashing. No copied coordinates, synthetic swipe or generated class.
            if (connectionExecutor.get(target) !== main.get(current)) return false
            val ctx = context.get(current) as Context
            val name = if (stashed.invoke(bounds.get(current)) == true) "action_pip_unstash" else "action_pip_stash"
            val actionId = ctx.resources.getIdentifier(name, "id", "com.android.systemui")
            if (actionId == 0) return false
            val result = AtomicBoolean()
            val binder = Binder()
            val callback = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { proxy, method, args ->
                when (method.name) {
                    "asBinder" -> binder
                    "setPerformAccessibilityActionResult" -> { result.set(args?.get(0) == true); null }
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> java.lang.System.identityHashCode(proxy)
                    "toString" -> "PiP gesture result"
                    else -> null
                }
            }
            performAction.invoke(connectionImpl.get(target), rootNode, actionId, null, 0, callback, 0, Process.myPid(), 0L)
            return result.get()
        }
    }
}
