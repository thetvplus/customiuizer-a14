package tv.withaibuild.customiuizer.mods.utils

import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.ref.WeakReference
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
    private var binding = false
    // Plugin UI hooks are not lease-scoped. A retired loader must not install them again.
    // Weak identities avoid retaining loaders after their native plugin is collected.
    private val attemptedLoaders = ArrayList<WeakReference<ClassLoader>>()

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
     * - Reentrant installation and retired loaders never add another set of UI hooks.
     * - A new loader ends the previous lease, clears the previous gesture runtime and tokens,
     *   then runs a fresh install transaction.
     * - A non-fatal UI install failure remains FAILED_PARTIAL without cancelling ready gestures.
     *   Fatal failures and failed gesture preparation do not leave a live gesture runtime.
     */
    @Synchronized
    fun bind(loader: ClassLoader): ControlCenterBindResult {
        // Native class preparation can re-enter PluginFactory before a loader is published.
        if (binding) return ControlCenterBindResult.AlreadyInstalled
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

        val previous = attemptedLoaders.iterator()
        while (previous.hasNext()) {
            val attempted = previous.next().get()
            if (attempted == null) previous.remove()
            else if (attempted === loader) return ControlCenterBindResult.NoRetry
        }
        attemptedLoaders.add(WeakReference(loader))

        binding = true
        return try {
            // New loader (or first bind): retire the previous runtime before preparing hooks.
            clearInternal()
            val lease = RuntimeLease()
            activeLease = lease
            installState = InstallState.INSTALLING
            lastFailure = null

            try {
                _runtimeHolder.bind(loader)
                if (lease.active) {
                    try {
                        onInstallPluginHooks(loader)
                    } catch (e: Throwable) {
                        // Styling is independent of the prepared native gesture owner.
                        // Fatal errors still reach the outer cleanup boundary.
                        FatalErrors.unwrapAndRethrowIfFatal(e)
                        lastFailure = e
                    }
                }
                if (!lease.active) {
                    // A reentrant teardown may run during either installer. The holder can
                    // finish publishing after clear(), so release it again before returning.
                    clearInternal()
                    installState = InstallState.IDLE
                    ControlCenterBindResult.NoRetry
                } else {
                    installState = if (lastFailure == null) InstallState.INSTALLED else InstallState.FAILED_PARTIAL
                    activeLoader = loader
                    lastFailure?.let { ControlCenterBindResult.Failed(it) } ?: ControlCenterBindResult.Installed
                }
            } catch (e: Throwable) {
                clearInternal()
                FatalErrors.unwrapAndRethrowIfFatal(e)
                activeLoader = loader
                installState = InstallState.FAILED_PARTIAL
                lastFailure = e
                ControlCenterBindResult.Failed(e)
            }
        } finally {
            binding = false
        }
    }

    /**
     * Explicitly detach the current plugin.
     *
     * Invalidates the current lease, clears the gesture runtime and its physical-gesture
     * tokens, and drops the active loader references so the old ClassLoader can be collected.
     */
    @Synchronized
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
                    override fun before(callback: BeforeHookCallback) {
                        val lease = activeLease
                        // A failed plugin lease must not disable discovery of its replacement loader.
                        if (lease != null && !lease.active && installState != InstallState.FAILED_PARTIAL) return
                        val loader = SystemUIControlCenterHooks.extractPluginLoader(callback.getThisObject()) ?: return
                        bind(loader)
                    }
                },
            )
            hooked = true
        } catch (e: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(e)
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
        ModuleHelper.hookAllMethodsWithId(
            "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
            classLoader,
            "createPlugin",
            HookInstallation.STABLE_ID_PLUGIN_CREATE,
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
        val destroy: MethodHook,
    )

    internal open fun installControlCenterGestureHooks(
        classLoader: ClassLoader,
        controlCenterMachine: GestureMachine,
        lease: RuntimeLease,
    ): ControlCenterGestureHooks {
        val controlCenterHook = object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                if (!lease.active) return
                if (callback.getArgs().size >= 2 && (callback.getArg(1) as? Boolean) == true) return
                if (callback.getArgs().isEmpty()) return
                val event = callback.getArg(0) as? MotionEvent ?: return
                val thisObject = callback.getThisObject() as? View ?: return
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
            override fun before(callback: BeforeHookCallback) {
                if (!lease.active) return
                val thisObject = callback.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    controlCenterMachine.prepare(System.identityHashCode(thisObject), thisObject)
                }
            }
        }
        val destroyHook = object : MethodHook() {
            override fun before(callback: BeforeHookCallback) {
                if (!lease.active) return
                val thisObject = callback.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    controlCenterMachine.clear(System.identityHashCode(thisObject))
                }
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "handleMotionEvent", MotionEvent::class.java, Boolean::class.javaPrimitiveType!!, controlCenterHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "onAttachedToWindow", onAttachedHook)
        // HyperOS 1 destroys this owner before removing its window. The plugin View
        // inherits onDetachedFromWindow, so the exact-method installer cannot hook it here.
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "destroy", destroyHook)
        return ControlCenterGestureHooks(controlCenterHook, onAttachedHook, destroyHook)
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
