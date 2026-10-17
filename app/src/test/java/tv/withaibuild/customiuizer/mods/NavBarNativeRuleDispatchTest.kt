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

/** Exercises the real ROM parent's virtual dispatch boundary without a SystemUI assistant guard. */
class NavBarNativeRuleDispatchTest {
    private val savedConfig = Controls.controlsConfig
    private val keyField = ParentRule::class.java.getDeclaredField("mKeyCode").apply { isAccessible = true }
    private val contextField = ParentRule::class.java.getDeclaredField("mContext").apply { isAccessible = true }

    @After
    fun restoreConfig() { Controls.controlsConfig = savedConfig }

    @Test
    fun navigationLongPressDoesNotEnableExtraSystemUiButtons() {
        assertFalse(Controls.hasExtraNavBarButtons(Controls.ControlsSnapshot(backLongAction = 2, homeLongAction = 2, menuLongAction = 2)))
        for (config in listOf(
            Controls.ControlsSnapshot(navbarLeftAction = 2),
            Controls.ControlsSnapshot(navbarLeftLongAction = 2),
            Controls.ControlsSnapshot(navbarRightAction = 2),
            Controls.ControlsSnapshot(navbarRightLongAction = 2),
        )) assertTrue(Controls.hasExtraNavBarButtons(config))
    }

    @Test
    fun successfulHomeCallsTheCustomActionOnceWithoutNativeAssistantOrExtraGuard() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 2)
        val rule = NativeRule(3, "launch_google_search")
        val chain = Chain(rule) { rule.onLongPress(100L) }
        val consumption = NavBarLongPressConsumption()
        var customCalls = 0
        assertEquals(null, Controls.interceptNavBarLongPress(
            chain, keyField, contextField, { _, key, action ->
                assertEquals("controls_homelong", key)
                assertEquals(2, action)
                customCalls++
                true
            }, {}, consumption,
        ))
        assertEquals(1, customCalls)
        assertEquals(0, chain.proceedCount)
        assertEquals(0, rule.nativeLongCalls)
        assertTrue(consumption.consume(3, 100L, true))
    }

    @Test
    fun disablingHomeDelegatesToTheSameParentAndNativeSubclassOnce() {
        Controls.controlsConfig = Controls.ControlsSnapshot()
        val rule = NativeRule(3, "launch_google_search")
        val support = Chain(rule) { rule.supportLongPress() }
        assertEquals(true, Controls.interceptNavBarSupportLongPress(support, keyField))
        val chain = Chain(rule) { rule.onLongPress(100L) }
        assertEquals(null, Controls.interceptNavBarLongPress(
            chain, keyField, contextField, { _, _, _ -> error("disabled HOME invoked custom action") }, {},
        ))
        assertEquals(1, support.proceedCount)
        assertEquals(1, rule.nativeSupportCalls)
        assertEquals(1, chain.proceedCount)
        assertEquals(1, rule.nativeLongCalls)
        assertEquals(listOf("launch_google_search"), rule.nativeActions)
    }

    @Test
    fun failedHomeActionFallsBackThroughTheParentWithoutRetryingTheCustomAction() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 8)
        val rule = NativeRule(3, "launch_google_search")
        Controls.interceptNavBarSupportLongPress(Chain(rule) { rule.supportLongPress() }, keyField)
        val chain = Chain(rule) { rule.onLongPress(100L) }
        val consumption = NavBarLongPressConsumption()
        var customCalls = 0
        assertEquals(null, Controls.interceptNavBarLongPress(
            chain, keyField, contextField, { _, _, _ -> customCalls++; false },
            { error("failed HOME vibrated") }, consumption,
        ))
        assertEquals(1, customCalls)
        assertEquals(1, chain.proceedCount)
        assertEquals(1, rule.nativeLongCalls)
        assertEquals(listOf("launch_google_search"), rule.nativeActions)
        assertFalse(consumption.hasHandledPress())
    }

    @Test
    fun supportRefreshesNativeSubclassStateBeforeOverridingDisabledNativeHome() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 2)
        val rule = NativeRule(3, "none")
        val chain = Chain(rule) { rule.supportLongPress() }
        assertEquals(true, Controls.interceptNavBarSupportLongPress(chain, keyField))
        assertEquals("none", rule.cachedFunction)
        rule.function = "launch_google_search"
        assertEquals(true, Controls.interceptNavBarSupportLongPress(chain, keyField))
        assertEquals("launch_google_search", rule.cachedFunction)
        assertEquals(2, chain.proceedCount)
        assertEquals(2, rule.nativeSupportCalls)
    }

    @Test
    fun nonNavigationRulesKeepNativeSupportAndLongPressWhileNavigationIsConfigured() {
        Controls.controlsConfig = Controls.ControlsSnapshot(backLongAction = 2, homeLongAction = 2, menuLongAction = 2)
        for (key in listOf(24, 25, 26, 27, 187)) {
            val rule = NativeRule(key, "native_$key")
            val support = Chain(rule) { rule.supportLongPress() }
            assertEquals(true, Controls.interceptNavBarSupportLongPress(support, keyField))
            val chain = Chain(rule) { rule.onLongPress(100L) }
            assertEquals(null, Controls.interceptNavBarLongPress(
                chain, keyField, contextField, { _, _, _ -> error("foreign key invoked custom action") }, {},
            ))
            assertEquals(1, support.proceedCount)
            assertEquals(1, chain.proceedCount)
            assertEquals(listOf("native_$key"), rule.nativeActions)
        }
    }

    @Test
    fun parentContextIsUsedEvenIfTheSubclassShadowsItsContext() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 2)
        val rule = NativeRule(3, "launch_google_search")
        var executions = 0
        assertEquals(null, Controls.interceptNavBarLongPress(
            Chain(rule) { rule.onLongPress(100L) }, keyField, contextField,
            { context, _, _ -> assertSame(rule.parentContext, context); executions++; true }, {},
        ))
        assertEquals(1, executions)
    }

    @Test
    fun originalSupportFailurePropagatesOnceBeforeOwnerMetadataIsRead() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 2)
        val failure = IllegalStateException("native support failed")
        val chain = Chain(null, ownerFailure = AssertionError("support inspected owner before native")) { throw failure }
        try {
            Controls.interceptNavBarSupportLongPress(chain, keyField)
            throw AssertionError("original failure was swallowed")
        } catch (actual: Throwable) {
            assertSame(failure, actual)
        }
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun fatalNativeHomeFallbackPropagatesWithoutASecondOriginalCall() {
        Controls.controlsConfig = Controls.ControlsSnapshot(homeLongAction = 8)
        for (fatal in listOf(OutOfMemoryError("oom"), ThreadDeath(), InternalError("vm"))) {
            val chain = Chain(NativeRule(3, "launch_google_search")) { throw fatal }
            try {
                Controls.interceptNavBarLongPress(chain, keyField, contextField, { _, _, _ -> false }, {})
                throw AssertionError("fatal native failure was swallowed")
            } catch (actual: Throwable) {
                assertSame(fatal, actual)
            }
            assertEquals(1, chain.proceedCount)
        }
    }

    private abstract class ParentRule(
        private val mKeyCode: Int,
        private val mContext: Context = ContextWrapper(null),
    ) {
        val parentContext: Context get() = mContext
        fun supportLongPress(): Boolean = miuiSupportLongPress()
        fun onLongPress(eventTime: Long) = onMiuiLongPress(eventTime)
        protected abstract fun miuiSupportLongPress(): Boolean
        protected abstract fun onMiuiLongPress(eventTime: Long)
    }

    private class NativeRule(key: Int, var function: String?) : ParentRule(key) {
        @Suppress("unused") private val mContext: Context? = null
        var nativeSupportCalls = 0
        var nativeLongCalls = 0
        var cachedFunction: String? = null
        val nativeActions = mutableListOf<String>()
        override fun miuiSupportLongPress(): Boolean {
            nativeSupportCalls++
            cachedFunction = function
            return !cachedFunction.isNullOrEmpty() && cachedFunction != "none"
        }
        override fun onMiuiLongPress(eventTime: Long) {
            nativeLongCalls++
            cachedFunction?.takeUnless { it.isEmpty() || it == "none" }?.let(nativeActions::add)
        }
    }

    private class Chain(
        private val owner: Any?,
        private val ownerFailure: Throwable? = null,
        private val original: () -> Any?,
    ) : XposedInterface.Chain {
        var proceedCount = 0
        override fun getExecutable(): Executable = error("not used")
        override fun getThisObject(): Any? { ownerFailure?.let { throw it }; return owner }
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = if (index == 0) 100L else error("not used")
        override fun proceed(): Any? {
            proceedCount++
            val result = original()
            // libxposed returns null for the ROM's void onLongPress method.
            return if (result === Unit) null else result
        }
        override fun proceed(args: Array<Any?>): Any? = error("not used")
        override fun proceedWith(p0: Any): Any? = error("not used")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used")
    }
}
