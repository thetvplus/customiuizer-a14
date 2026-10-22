package tv.withaibuild.customiuizer.mods.volumedialogautohide

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import java.lang.reflect.Executable
import java.util.concurrent.atomic.AtomicReference

class VolumeTimeoutAccessibilityTest {
    @Test
    fun customExpandedDelayUsesTheSystemRecommendationForControls() {
        checkRecommendation(expanded = true, base = 2500, flags = 4)
    }

    @Test
    fun customCollapsedDelayUsesTheSystemRecommendationForControls() {
        checkRecommendation(expanded = false, base = 2500, flags = 4)
    }

    @Test
    fun safetyWarningKeepsTextAndControlsInTheRecommendation() {
        checkRecommendation(safety = true, base = 2500, flags = 6)
    }

    @Test
    fun hoveringKeepsTheNativeSixteenSecondBaseAndControlsFlag() {
        checkRecommendation(hovering = true, base = 16000, flags = 4)
    }

    @Test
    fun safetyDefaultKeepsTheNativeFiveSecondBase() {
        checkRecommendation(safety = true, configured = 0, base = 5000, flags = 6)
    }

    @Test
    fun unavailableRecommendationLeavesTheNativeMethodInCharge() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl()
        val callback = callback(dialog)
        effect { _, _, _ -> null }.before(callback)
        assertFalse(callback.skipped)
    }

    @Test
    fun defaultDelayDoesNotQueryOrReplaceTheNativeRecommendation() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl()
        val callback = callback(dialog)
        effect(configured = 0) { _, _, _ -> error("default must remain native") }.before(callback)
        assertFalse(callback.skipped)
    }

    private fun checkRecommendation(
        expanded: Boolean = false,
        safety: Boolean = false,
        hovering: Boolean = false,
        configured: Int = 2500,
        base: Int,
        flags: Int,
    ) {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mExpanded = expanded
            mIsSafetyShowing = safety
            mHovering = hovering
        }
        var calls = 0
        val callback = callback(dialog)
        effect(configured) { owner, duration, contentFlags ->
            assertSame(dialog, owner)
            assertEquals(base, duration)
            assertEquals(flags, contentFlags)
            calls++
            45000
        }.before(callback)
        assertEquals(1, calls)
        assertTrue(callback.skipped)
        assertEquals(45000, callback.result)
    }

    private fun effect(configured: Int = 2500, recommend: (Any?, Int, Int) -> Int?) =
        VolumeDialogAutohideDelayEffect(
            VolumeDialogAutohideDelayResolver.resolve(VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java),
            AtomicReference(VolumeDialogAutohideDelaySnapshot(configured, configured)),
            recommend,
        )

    private fun callback(dialog: Any) = HookerClassHelper.BeforeHookCallback(object : XposedInterface.Chain {
        override fun getExecutable(): Executable = dialog.javaClass.getMethod("computeTimeoutH")
        override fun getThisObject(): Any = dialog
        override fun getArgs(): List<Any?> = emptyList()
        override fun getArg(index: Int): Any? = null
        override fun proceed(): Any = error("before callback only")
        override fun proceed(args: Array<Any>): Any = error("before callback only")
        override fun proceedWith(target: Any): Any = error("unused")
        override fun proceedWith(target: Any, args: Array<Any>): Any = error("unused")
    })
}
