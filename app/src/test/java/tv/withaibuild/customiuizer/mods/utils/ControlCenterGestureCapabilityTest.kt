package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.Context
import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.gesture.*
import java.lang.reflect.Executable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ControlCenterGestureCapabilityTest {
    interface StateController { fun getState(): Int }
    class Owner(context: Context) : View(context) {
        @JvmField var statusBarStateController: StateController? = null
        fun handleMotionEvent(event: MotionEvent, external: Boolean): Boolean = external
        public override fun onAttachedToWindow() = Unit
        fun destroy() = Unit
    }
    class WrongReturn(context: Context) : View(context) {
        fun handleMotionEvent(event: MotionEvent, external: Boolean): Int = 0
        public override fun onAttachedToWindow() = Unit
        fun destroy() = Unit
    }
    class MissingDestroy(context: Context) : View(context) {
        fun handleMotionEvent(event: MotionEvent, external: Boolean): Boolean = false
        public override fun onAttachedToWindow() = Unit
    }
    class MissingState(context: Context) : View(context) {
        fun handleMotionEvent(event: MotionEvent, external: Boolean): Boolean = false
        public override fun onAttachedToWindow() = Unit
        fun destroy() = Unit
    }
    private val engine = ControlCenterPluginRuntimeEngine()
    private val arbiter = PhysicalGestureArbiter()
    private val machine = GestureMachine(
        classLoaderIdentity = "capability",
        configResolver = { GestureConfig(singleAction = 2) },
        depsResolver = object : GestureDependenciesResolver {
            override fun prepare(ownerId: Int, classLoaderIdentity: String, context: Any) = GestureDependenciesResult.Ready(
                GestureDependencies(ownerId, classLoaderIdentity, Any(), 0, 0f, 1f, null, 80, 1080, 1f),
            )
        },
        effectExecutor = GestureEffectExecutor { _, _, _, _ -> },
        arbiter = arbiter,
    )
    private fun entries(type: Class<*> = Owner::class.java, lease: RuntimeLease = RuntimeLease()) =
        engine.prepareControlCenterGestureHooks(type, machine, lease)

    @Test fun rejectsIncompleteOrWrongAbiBeforeAnyInstallation() {
        for (type in listOf(Any::class.java, WrongReturn::class.java, MissingDestroy::class.java, MissingState::class.java)) {
            var installs = 0
            val batch = PreparedHookBatch { _, _ -> installs++; error("An incomplete ABI must not reach installation") }
            assertThrows(Exception::class.java) { batch.install(entries(type)) }
            assertEquals(0, installs)
        }
    }

    @Test fun eachPartialInstallationRollsBackAndLeavesNativeReturnUntouched() {
        for (failedAt in 1..3) {
            val callbacks = ArrayList<MethodHook>()
            val closed = ArrayList<Int>()
            val batch = PreparedHookBatch { _, hook ->
                callbacks.add(hook)
                val index = callbacks.size
                if (index == failedAt) null else object : CustomMethodUnhooker {
                    override fun unhook() { closed.add(index) }
                }
            }
            assertEquals(FeatureInstallResult.FAILED_TRANSIENT, batch.install(entries()))
            assertEquals((1 until failedAt).reversed().toList(), closed)
            for (hook in callbacks) {
                val chain = Chain()
                assertEquals(true, hook.intercept(chain))
                assertEquals(1, chain.calls)
                assertEquals(0, chain.reads)
            }
            assertEquals(0, arbiter.heldTokenCount())
        }
    }

    @Test fun nativeStateFailureAndExternalOrLockedInputLeaveGesturesIdle() {
        val view = Owner(RuntimeEnvironment.getApplication())
        val hook = entries()[0].second
        val event = MotionEvent.obtain(1000, 1000, MotionEvent.ACTION_DOWN, 100f, 10f, 0)
        val fatal = OutOfMemoryError("native state")
        try {
            for (state in listOf<Int?>(null, 1, 2, 3)) {
                view.statusBarStateController = state?.let { value -> object : StateController { override fun getState() = value } }
                val chain = Chain(view, event)
                assertEquals(true, hook.intercept(chain))
                assertEquals(1, chain.calls)
                assertEquals(0, chain.copies)
                assertEquals(0, arbiter.heldTokenCount())
            }
            view.statusBarStateController = object : StateController { override fun getState(): Int = error("not ready") }
            val ordinary = Chain(view, event)
            assertEquals(true, hook.intercept(ordinary))
            assertEquals(1, ordinary.calls)
            assertEquals(0, arbiter.heldTokenCount())
            view.statusBarStateController = object : StateController { override fun getState(): Int = throw fatal }
            val external = Chain(view, event, external = true)
            assertEquals(true, hook.intercept(external))
            assertEquals(1, external.calls)
            val fatalChain = Chain(view, event)
            assertSame(fatal, assertThrows(OutOfMemoryError::class.java) { hook.intercept(fatalChain) })
            assertEquals(0, fatalChain.calls)
        } finally { event.recycle(); machine.clear() }
    }

    @Test fun completeAbiPreparesDispatchesAndDestroysOnlyItsOwner() {
        val view = Owner(RuntimeEnvironment.getApplication())
        view.statusBarStateController = object : StateController { override fun getState() = 0 }
        val hooks = entries()
        assertEquals(listOf("handleMotionEvent", "onAttachedToWindow", "destroy"), hooks.map { it.first.name })
        assertEquals(listOf(Boolean::class.javaPrimitiveType, Void.TYPE, Void.TYPE), hooks.map { it.first.returnType })
        val event = MotionEvent.obtain(1000, 1000, MotionEvent.ACTION_DOWN, 100f, 10f, 0)
        try {
            hooks[1].second.intercept(Chain(view))
            val motion = Chain(view, event)
            assertEquals(true, hooks[0].second.intercept(motion))
            assertEquals(1, motion.calls)
            assertEquals(0, motion.copies)
            assertEquals(1, arbiter.tokensForOwner(System.identityHashCode(view)))
            hooks[2].second.intercept(Chain(view))
            assertEquals(0, arbiter.heldTokenCount())
            assertNull(machine.resolvedConfig(System.identityHashCode(view)))
        } finally { event.recycle(); machine.clear() }
    }

    private class Chain(private val owner: Any? = null, private val event: MotionEvent? = null, private val external: Boolean = false) : XposedInterface.Chain {
        var calls = 0
        var reads = 0
        var copies = 0
        override fun getExecutable(): Executable = error("No executable reads")
        override fun getThisObject(): Any? { reads++; return owner }
        override fun getArgs(): List<Any?> { copies++; error("No argument array") }
        override fun getArg(index: Int): Any? { reads++; return when (index) { 0 -> event; 1 -> external; else -> error("Unexpected argument") } }
        override fun proceed(): Any { calls++; return true }
        override fun proceed(args: Array<Any>): Any = error("No argument rewriting")
        override fun proceedWith(receiver: Any): Any = error("No receiver rewriting")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any = error("No receiver rewriting")
    }
}
