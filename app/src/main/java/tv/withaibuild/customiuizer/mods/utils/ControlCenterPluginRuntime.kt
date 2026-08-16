package tv.withaibuild.customiuizer.mods.utils

import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemUIControlCenterHooks
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.gesture.ControlCenterGestureDependenciesResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.ControlCenterGestureRuntimeHolder
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfigPublisher
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfigResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEntry
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEvent
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureMachine
import tv.withaibuild.customiuizer.mods.utils.gesture.PhysicalGestureArbiter
import tv.withaibuild.customiuizer.mods.utils.gesture.StatusBarGestureEffectExecutor

/**
 * Outcome of a [ControlCenterPluginRuntimeEngine.bind] transaction.
 */
sealed class ControlCenterBindResult {
    object Installed : ControlCenterBindResult()
    object AlreadyInstalled : ControlCenterBindResult()
    object NoRetry : ControlCenterBindResult()
    data class Failed(val reason: Throwable) : ControlCenterBindResult()
}

/**
 * Per-runtime lease used to make every installed callback a no-op once [clear]
 * invalidates the current transaction.
 */
class RuntimeLease {
    @Volatile
    var active: Boolean = true
        private set

    fun invalidate() {
        active = false
    }
}

/**
 * Lifecycle state of the control-center plugin install transaction.
 */
enum class InstallState {
    IDLE, INSTALLING, INSTALLED, FAILED_PARTIAL
}

/**
 * Runtime engine for the SystemUI control-center plugin lifecycle.
 *
 * The engine owns the [PhysicalGestureArbiter], the [GestureConfigPublisher], the
 * [ControlCenterGestureRuntimeHolder] and the state of the active plugin ClassLoader.
 * The production singleton [ControlCenterPluginRuntime] wraps a default engine.
 */
open class ControlCenterPluginRuntimeEngine(
    val configPublisher: GestureConfigPublisher = GestureConfigPublisher(resolve = { GestureConfigResolver.resolve(MainModule.mPrefs) }),
    val arbiter: PhysicalGestureArbiter = PhysicalGestureArbiter(),
    private val dependenciesResolver: ControlCenterGestureDependenciesResolver = ControlCenterGestureDependenciesResolver(),
    private val effectExecutor: StatusBarGestureEffectExecutor = StatusBarGestureEffectExecutor(),
) {

    private val _runtimeHolder: ControlCenterGestureRuntimeHolder

    init {
        val installHooks: (ClassLoader, GestureMachine) -> Unit = { classLoader, machine ->
            onInstallGestureHooks(classLoader, machine)
        }
        _runtimeHolder = ControlCenterGestureRuntimeHolder(
            configPublisher = configPublisher,
            effectExecutor = effectExecutor,
            arbiter = arbiter,
            dependenciesResolver = dependenciesResolver,
            installHooks = installHooks,
        )
    }

    private var activeLoader: ClassLoader? = null
    private var activeLease: RuntimeLease? = null
    private var installState = InstallState.IDLE
    private var lastFailure: Throwable? = null
    private var hooked = false

    /** Expose the shared arbiter so status-bar and control-center gestures share one authority. */
    fun arbiter(): PhysicalGestureArbiter = arbiter

    /** Expose the runtime holder for diagnostics. */
    internal fun runtimeHolder(): ControlCenterGestureRuntimeHolder = _runtimeHolder

    /** Active plugin ClassLoader, or null if no plugin is currently bound. */
    fun activeLoader(): ClassLoader? = activeLoader

    /** Current install state. */
    fun installState(): InstallState = installState

    /** Last non-fatal bind failure, or null. */
    fun lastFailure(): Throwable? = lastFailure

    /** Current runtime lease, or null if no runtime has been created. */
    fun activeLease(): RuntimeLease? = activeLease

    /**
     * Bind a freshly detected [loader].
     *
     * - Same loader + [InstallState.INSTALLED] is idempotent.
     * - Same loader + [InstallState.FAILED_PARTIAL] is *not* retried.
     * - A new loader ends the previous lease, clears the previous gesture runtime and tokens,
     *   then runs a fresh install transaction.
     * - The active loader is only published after the gesture hooks and the control-center UI
     *   hooks both succeed; fatal failures during install do not leave a half-state.
     */
    fun bind(loader: ClassLoader): ControlCenterBindResult {
        if (activeLoader === loader) {
            return when (installState) {
                InstallState.INSTALLED -> ControlCenterBindResult.AlreadyInstalled
                InstallState.FAILED_PARTIAL -> ControlCenterBindResult.NoRetry
                else -> {
                    // Same loader is already in a transaction; do not start a second one.
                    ControlCenterBindResult.AlreadyInstalled
                }
            }
        }

        // New loader (or first bind): invalidate old lease and clean up previous state.
        activeLease?.invalidate()
        clearInternal()

        val lease = RuntimeLease()
        activeLease = lease
        installState = InstallState.INSTALLING
        lastFailure = null

        return try {
            _runtimeHolder.bind(loader)
            onInstallPluginHooks(loader)
            installState = InstallState.INSTALLED
            activeLoader = loader
            ControlCenterBindResult.Installed
        } catch (e: Throwable) {
            clearInternal()
            FatalErrors.rethrowIfFatal(e)
            activeLoader = loader
            installState = InstallState.FAILED_PARTIAL
            lastFailure = e
            ControlCenterBindResult.Failed(e)
        }
    }

    /**
     * Explicitly detach the current plugin.
     *
     * Invalidates the current lease, clears the gesture runtime, releases all physical-gesture
     * tokens and drops the active loader references so the old ClassLoader can be collected.
     */
    fun clear() {
        activeLease?.invalidate()
        if (activeLease == null) {
            activeLease = RuntimeLease().apply { invalidate() }
        }
        installState = InstallState.IDLE
        clearInternal()
    }

    private fun clearInternal() {
        activeLease?.invalidate()
        if (activeLease == null) {
            activeLease = RuntimeLease().apply { invalidate() }
        }
        _runtimeHolder.unbind()
        activeLoader = null
        arbiter.releaseAll()
    }

    /**
     * Install a single `PluginFactory.createPlugin` hook on [lpparam.classLoader] if it has
     * not already been installed. The hook extracts the miui.systemui.plugin ClassLoader and
     * binds it through [bind].
     */
    fun hookIfNeeded(lpparam: PackageReadyParam) = hookIfNeeded(lpparam.classLoader)

    fun hookIfNeeded(classLoader: ClassLoader) {
        if (hooked) return
        if (installState == InstallState.FAILED_PARTIAL) return

        try {
            onInstallCreatePluginHook(
                classLoader,
                object : MethodHook() {
                    override fun before(param: BeforeHookCallback) {
                        val lease = activeLease
                        if (lease != null && !lease.active) return
                        val loader = SystemUIControlCenterHooks.extractPluginLoader(param.getThisObject()) ?: return
                        bind(loader)
                    }
                },
            )
            hooked = true
        } catch (e: Throwable) {
            FatalErrors.rethrowIfFatal(e)
            installState = InstallState.FAILED_PARTIAL
            lastFailure = e
        }
    }

    /**
     * Installs the control-center UI hooks.
     *
     * Overridable so tests can substitute a no-op or a failure.
     */
    protected open fun onInstallPluginHooks(classLoader: ClassLoader) {
        SystemUIControlCenterHooks.initControlCenter(classLoader)
    }

    /**
     * Installs the `PluginFactory.createPlugin` hook.
     *
     * Overridable so tests can observe or replace the installer.
     */
    protected open fun onInstallCreatePluginHook(classLoader: ClassLoader, hook: MethodHook) {
        ModuleHelper.hookAllMethods(
            "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
            classLoader,
            "createPlugin",
            hook,
        )
    }

    /**
     * Installs the control-center gesture hooks.
     *
     * Overridable so tests can substitute a no-op.
     */
    protected open fun onInstallGestureHooks(classLoader: ClassLoader, machine: GestureMachine) {
        installControlCenterGestureHooks(
            classLoader,
            machine,
            activeLease ?: error("bind() must create an active lease before installing hooks"),
        )
    }

    data class ControlCenterGestureHooks(
        val handleMotionEvent: MethodHook,
        val onAttachedToWindow: MethodHook,
        val onDetachedFromWindow: MethodHook,
    )

    internal open fun installControlCenterGestureHooks(
        classLoader: ClassLoader,
        controlCenterMachine: GestureMachine,
        lease: RuntimeLease,
    ): ControlCenterGestureHooks {
        val controlCenterHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (!lease.active) return
                if (param.getArgs().size >= 2 && (param.getArg(1) as? Boolean) == true) return
                if (param.getArgs().isEmpty()) return
                val event = param.getArg(0) as? MotionEvent ?: return
                val thisObject = param.getThisObject() as? View ?: return
                val statusBarStateController = XposedHelpers.getObjectField(thisObject, "statusBarStateController")
                val state = XposedHelpers.callMethod(statusBarStateController, "getState") as Int
                if (state == 1 || state == 2) return
                val gestureEvent = GestureEvent(
                    entry = GestureEntry.CONTROL_CENTER_TOUCH,
                    actionMasked = event.actionMasked,
                    downTime = event.downTime,
                    eventTime = event.eventTime,
                    x = event.x,
                    y = event.y,
                    pointerCount = event.pointerCount,
                    ownerId = System.identityHashCode(thisObject),
                    deviceId = event.deviceId,
                    source = event.source,
                )
                ModuleHelper.guarded {
                    controlCenterMachine.dispatch(gestureEvent, thisObject)
                }
            }
        }
        val onAttachedHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (!lease.active) return
                val thisObject = param.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    controlCenterMachine.prepare(System.identityHashCode(thisObject), thisObject)
                }
            }
        }
        val onDetachedHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (!lease.active) return
                val thisObject = param.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    controlCenterMachine.clear(System.identityHashCode(thisObject))
                }
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "handleMotionEvent", MotionEvent::class.java, Boolean::class.javaPrimitiveType!!, controlCenterHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "onAttachedToWindow", onAttachedHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "onDetachedFromWindow", onDetachedHook)
        return ControlCenterGestureHooks(controlCenterHook, onAttachedHook, onDetachedHook)
    }
}

/**
 * Single owner for the SystemUI control-center plugin lifecycle.
 *
 * Only one `PluginFactory.createPlugin` hook is installed, regardless of whether the
 * control-center UI modifications, the status-bar gestures, or both are enabled.
 */
internal object ControlCenterPluginRuntime {

    private val engine = ControlCenterPluginRuntimeEngine()

    /** Shared gesture configuration publisher. */
    val configPublisher get() = engine.configPublisher

    /** Shared physical gesture arbiter. */
    fun arbiter(): PhysicalGestureArbiter = engine.arbiter

    /** Install the `PluginFactory.createPlugin` hook if needed. */
    fun hookIfNeeded(lpparam: PackageReadyParam) = engine.hookIfNeeded(lpparam.classLoader)
}
