package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.ContextWrapper
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

/** Covers dispatch and original-method boundaries; ROM timing and key-up still need device tests. */
class NavBarLongPressHookTest {
    private val savedConfig = Controls.controlsConfig
    private val primaryKeyField = Rule::class.java.getDeclaredField("mKeyCode").apply { isAccessible = true }
    private val contextField = Rule::class.java.getDeclaredField("mContext").apply { isAccessible = true }

    @After
    fun restoreConfig() {
        Controls.controlsConfig = savedConfig
    }

    @Test
    fun disabledActionsDoNotReadTheOwnerAndProceedExactlyOnce() {
        Controls.controlsConfig = Controls.ControlsSnapshot()
        val chain = FakeChain(ownerFailure = AssertionError("disabled callback accessed owner"))
        assertEquals("native", support(chain))
        assertEquals(1, chain.proceedCount)
        val longPress = FakeChain(ownerFailure = AssertionError("disabled callback accessed owner"))
        assertEquals("native", dispatch(longPress))
        assertEquals(1, longPress.proceedCount)
    }

    @Test
    fun firstEnableAndDisableUseTheNewSnapshotWithoutReinstall() {
        val chain = FakeChain(Rule(4))
        Controls.controlsConfig = Controls.ControlsSnapshot()
        assertEquals("native", support(chain))
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        assertEquals(true, support(chain))
        Controls.controlsConfig = Controls.ControlsSnapshot()
        assertEquals("native", support(chain))
        assertEquals(3, chain.proceedCount)
    }

    @Test
    fun firstConfiguredSupportPreparesTheNativeActionForAFailedCustomAction() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 8)
        val owner = NativeRule(3, "launch_google_search")
        assertEquals(null, owner.cachedNativeFunction)
        val supportChain = FakeChain(owner, nativeOriginal = owner::nativeSupport)
        assertEquals(true, support(supportChain))
        assertEquals("launch_google_search", owner.cachedNativeFunction)
        assertEquals(1, supportChain.proceedCount)
        val pressChain = FakeChain(owner, nativeOriginal = owner::nativeLongPress)
        assertEquals(null, dispatch(pressChain) { _, _, _ -> false })
        assertEquals(listOf("launch_google_search"), owner.nativeActions)
        assertEquals(1, pressChain.proceedCount)
    }

    @Test
    fun nativeFunctionChangesAreRefreshedBeforeEachFallback() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 8)
        val owner = NativeRule(4, "none")
        val supportChain = FakeChain(owner, nativeOriginal = owner::nativeSupport)
        assertEquals(true, support(supportChain))
        assertEquals("none", owner.cachedNativeFunction)
        owner.nativeFunction = "close_app"
        assertEquals(true, support(supportChain))
        assertEquals("close_app", owner.cachedNativeFunction)
        assertEquals(2, supportChain.proceedCount)
        val pressChain = FakeChain(owner, nativeOriginal = owner::nativeLongPress)
        dispatch(pressChain) { _, _, _ -> false }
        assertEquals(listOf("close_app"), owner.nativeActions)
        assertEquals(1, pressChain.proceedCount)
    }

    @Test
    fun nativeNoneFallbackStillRunsTheOriginalWithoutInventingAnAction() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 8)
        val owner = NativeRule(4, null)
        assertEquals(true, support(FakeChain(owner, nativeOriginal = owner::nativeSupport)))
        val pressChain = FakeChain(owner, nativeOriginal = owner::nativeLongPress)
        dispatch(pressChain) { _, _, _ -> false }
        assertEquals(1, owner.nativeLongPressCalls)
        assertEquals(emptyList<String>(), owner.nativeActions)
        assertEquals(1, pressChain.proceedCount)
    }

    @Test
    fun modernMenuKeyIs82AndAppSwitchIsNotRemapped() {
        Controls.controlsConfig = Controls.ControlsSnapshot(menuLongAction = 3)
        val menu = FakeChain(Rule(82))
        assertEquals(true, support(menu))
        assertEquals(1, menu.proceedCount)
        val appSwitch = FakeChain(Rule(187))
        assertEquals("native", support(appSwitch))
        assertEquals(1, appSwitch.proceedCount)
        assertEquals("native", dispatch(appSwitch) { _, _, _ -> error("unexpected action") })
        assertEquals(2, appSwitch.proceedCount)
    }

    @Test
    fun successfulActionsDispatchEachKeyOnceAndSkipTheOriginal() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2, homeLongAction = 3, menuLongAction = 7)
        val seen = mutableListOf<Pair<String, Int>>()
        for (key in listOf(4, 3, 82)) {
            val chain = FakeChain(Rule(key))
            var vibrations = 0
            val result = Controls.interceptNavBarLongPress(
                chain, primaryKeyField, contextField,
                { _, actionKey, action -> seen.add(actionKey to action); true },
                { vibrations++ },
            )
            assertEquals(null, result)
            assertEquals(0, chain.proceedCount)
            assertEquals(1, vibrations)
        }
        assertEquals(listOf("controls_backlong" to 2, "controls_homelong" to 3, "controls_menulong" to 7), seen)
    }

    @Test
    fun failedCustomActionFallsBackToTheNativeOnceWithoutVibration() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val chain = FakeChain(Rule(4))
        var executions = 0
        val result = Controls.interceptNavBarLongPress(
            chain, primaryKeyField, contextField,
            { _, _, _ -> executions++; false },
            { error("unhandled action vibrated") },
        )
        assertEquals("native", result)
        assertEquals(1, executions)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun ordinaryActionFailureFallsBackWithoutCallingTheOriginalTwice() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 3)
        val chain = FakeChain(Rule(3))
        assertEquals("native", dispatch(chain) { _, _, _ -> throw IllegalStateException("failed action") })
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun vibrationFailureDoesNotRunTheNativeAfterAnActionWasHandled() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 3)
        val chain = FakeChain(Rule(3))
        assertEquals(null, Controls.interceptNavBarLongPress(
            chain, primaryKeyField, contextField,
            { _, _, _ -> true },
            { throw IllegalStateException("failed vibration") },
        ))
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun nullContextLeavesTheOriginalIntact() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val chain = FakeChain(Rule(4, null))
        assertEquals("native", dispatch(chain) { _, _, _ -> error("null context dispatched") })
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun ordinaryOwnerFailureLeavesBothOriginalMethodsIntact() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val supportChain = FakeChain(ownerFailure = IllegalStateException("invalid owner"))
        assertEquals("native", support(supportChain))
        assertEquals(1, supportChain.proceedCount)
        val pressChain = FakeChain(ownerFailure = IllegalStateException("invalid owner"))
        assertEquals("native", dispatch(pressChain))
        assertEquals(1, pressChain.proceedCount)
    }

    @Test
    fun fatalActionErrorsPropagateAndDoNotEnterTheOriginal() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        for (fatal in listOf(OutOfMemoryError("oom"), ThreadDeath(), InternalError("vm"))) {
            val chain = FakeChain(Rule(4))
            try {
                dispatch(chain) { _, _, _ -> throw fatal }
                throw AssertionError("fatal error was swallowed")
            } catch (failure: Throwable) {
                assertSame(fatal, failure)
            }
            assertEquals(0, chain.proceedCount)
        }
    }

    @Test
    fun wrappedFatalActionAndVibrationErrorsPropagateWithoutNativeFallback() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        for (fatal in listOf(OutOfMemoryError("oom"), ThreadDeath(), InternalError("vm"))) {
            for (wrapper in listOf(InvocationTargetException(fatal), ExecutionException(fatal))) {
                for (failDuringVibration in listOf(false, true)) {
                    val chain = FakeChain(Rule(4))
                    try {
                        Controls.interceptNavBarLongPress(
                            chain, primaryKeyField, contextField,
                            { _, _, _ -> if (!failDuringVibration) throw wrapper; true },
                            { if (failDuringVibration) throw wrapper },
                        )
                        throw AssertionError("wrapped fatal error was swallowed")
                    } catch (actual: Throwable) {
                        assertSame(fatal, actual)
                    }
                    assertEquals(0, chain.proceedCount)
                }
            }
        }
    }

    @Test
    fun originalFailurePropagatesOnceOnFallback() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val failure = IllegalStateException("native failure")
        val chain = FakeChain(Rule(4), proceedFailure = failure)
        try {
            dispatch(chain) { _, _, _ -> false }
            throw AssertionError("original failure was swallowed")
        } catch (actual: Throwable) {
            assertSame(failure, actual)
        }
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun actionUsesOneSnapshotWhenPreferencesChangeDuringDispatch() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val chain = FakeChain(Rule(4))
        assertEquals(null, dispatch(chain) { _, key, action ->
            Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 7)
            assertEquals("controls_backlong", key)
            assertEquals(2, action)
            true
        })
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun handledLongPressConsumesItsRemainingEventsEvenAfterDisable() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val consumption = NavBarLongPressConsumption()
        val chain = FakeChain(Rule(4), downTime = 123L)
        assertEquals(null, Controls.interceptNavBarLongPress(
            chain, primaryKeyField, contextField, { _, _, _ -> true }, {}, consumption,
        ))
        Controls.controlsConfig = Controls.ControlsSnapshot()
        val release = FakeChain()
        assertEquals(-1L, consumption.dispatch(release, 4, 123L, 1))
        assertEquals(0, release.proceedCount)
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun failedAndOrdinaryErrorActionsDoNotMarkKeyConsumption() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 8)
        for (failWithException in listOf(false, true)) {
            val consumption = NavBarLongPressConsumption()
            val chain = FakeChain(Rule(4), downTime = 234L)
            assertEquals("native", Controls.interceptNavBarLongPress(
                chain, primaryKeyField, contextField,
                { _, _, _ -> if (failWithException) throw IllegalStateException("failed action"); false },
                {}, consumption,
            ))
            assertFalse(consumption.consume(4, 234L, true))
            assertEquals(1, chain.proceedCount)
        }
    }

    @Test
    fun vibrationErrorAfterSuccessfulActionKeepsConsumption() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val consumption = NavBarLongPressConsumption()
        val chain = FakeChain(Rule(4), downTime = 345L)
        assertEquals(null, Controls.interceptNavBarLongPress(
            chain, primaryKeyField, contextField, { _, _, _ -> true },
            { throw IllegalStateException("failed vibration") }, consumption,
        ))
        assertTrue(consumption.consume(4, 345L, true))
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun invalidDownTimeLeavesTheNativeActionIntact() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2)
        val consumption = NavBarLongPressConsumption()
        for (time in listOf(-1L, NavBarLongPressConsumption.MAX_DOWN_TIME + 1L)) {
            val chain = FakeChain(Rule(4), downTime = time)
            assertEquals("native", Controls.interceptNavBarLongPress(
                chain, primaryKeyField, contextField, { _, _, _ -> error("invalid time dispatched") }, {}, consumption,
            ))
            assertFalse(consumption.consume(4, time, true))
            assertEquals(1, chain.proceedCount)
        }
    }

    private fun support(chain: FakeChain): Any? =
        Controls.interceptNavBarSupportLongPress(chain, primaryKeyField)

    private fun dispatch(
        chain: FakeChain,
        execute: (Context, String, Int) -> Boolean = { _, _, _ -> false },
    ): Any? = Controls.interceptNavBarLongPress(chain, primaryKeyField, contextField, execute, {})

    private open class Rule(
        @JvmField val mKeyCode: Int,
        @JvmField val mContext: Context? = ContextWrapper(null),
    )

    private class NativeRule(keyCode: Int, var nativeFunction: String?) : Rule(keyCode) {
        var cachedNativeFunction: String? = null
        var nativeLongPressCalls = 0
        val nativeActions = mutableListOf<String>()
        fun nativeSupport(): Boolean {
            cachedNativeFunction = nativeFunction
            return !cachedNativeFunction.isNullOrEmpty() && cachedNativeFunction != "none"
        }
        fun nativeLongPress(): Any? {
            nativeLongPressCalls++
            cachedNativeFunction?.takeUnless { it.isEmpty() || it == "none" }?.let(nativeActions::add)
            return null
        }
    }

    private class FakeChain(
        private val owner: Any? = null,
        private val ownerFailure: Throwable? = null,
        private val proceedFailure: Throwable? = null,
        private val nativeOriginal: (() -> Any?)? = null,
        private val downTime: Long? = null,
    ) : XposedInterface.Chain {
        var proceedCount = 0
        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? { ownerFailure?.let { throw it }; return owner }
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = if (index == 0) downTime else error("not used")
        override fun proceed(): Any? {
            proceedCount++
            proceedFailure?.let { throw it }
            return nativeOriginal?.invoke() ?: if (nativeOriginal == null) "native" else null
        }
        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }
}
