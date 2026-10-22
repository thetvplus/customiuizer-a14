package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import java.lang.reflect.Executable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumePanelRestorationTest {
    private val savedPrefs = MainModule.mPrefs.getAll()
    private val activity = Robolectric.buildActivity(Activity::class.java).setup()
    private val hook = SystemUIControlCenterHooks.VolumeBlurWindowHook()

    @After
    fun tearDown() {
        MainModule.mPrefs.replaceSnapshot(savedPrefs)
        SystemUIControlCenterHooks.refreshVolumeBlurSnapshot()
        activity.pause().stop().destroy()
    }

    @Test
    fun collapsedDefaultKeepsTheNativeDimmingAndFocusFlags() {
        configure(0, 80)
        val dialog = Dialog(activity.get().window)
        val chain = Chain(dialog)
        hook.intercept(chain)
        assertEquals(1, chain.proceeds)
        assertEquals(0.15f, dialog.mWindow.attributes.dimAmount, 0.001f)
        assertTrue(dialog.mWindow.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(dialog.blurTargets.isEmpty())
    }

    @Test
    fun expandedDefaultKeepsTheNativeBlurAndDimAmount() {
        configure(65, 0)
        val dialog = Dialog(activity.get().window).apply { mExpanded = true }
        hook.intercept(Chain(dialog))
        assertEquals(listOf(1f), dialog.blurTargets)
        assertEquals(0.4f, dialog.mWindow.attributes.dimAmount, 0.001f)
    }

    @Test
    fun returningToCollapsedDefaultClearsOnlyThePreviouslyOwnedBlur() {
        configure(0, 80)
        val dialog = Dialog(activity.get().window).apply { mExpanded = true }
        hook.intercept(Chain(dialog))
        assertEquals(0.8f, dialog.blurTargets.last(), 0.001f)
        dialog.mExpanded = false
        hook.intercept(Chain(dialog))
        assertEquals(0f, dialog.blurTargets.last(), 0.001f)
        assertEquals(0.15f, dialog.mWindow.attributes.dimAmount, 0.001f)
        val count = dialog.blurTargets.size
        hook.intercept(Chain(dialog))
        assertEquals(count, dialog.blurTargets.size)
    }

    @Test
    fun customCollapsedBlurIsAppliedWhenTheNativeStateCollapses() {
        configure(65, 80)
        val dialog = Dialog(activity.get().window)
        hook.intercept(Chain(dialog))
        assertEquals(listOf(0.65f), dialog.blurTargets)
        assertEquals(0f, dialog.mWindow.attributes.dimAmount, 0.001f)
    }

    @Test
    fun aNewWindowWithDefaultValuesDoesNotInheritAnotherWindowsCleanup() {
        configure(0, 80)
        val first = Dialog(activity.get().window).apply { mExpanded = true }
        hook.intercept(Chain(first))
        val other = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val second = Dialog(other.get().window)
            hook.intercept(Chain(second))
            assertTrue(second.blurTargets.isEmpty())
            assertEquals(0.15f, second.mWindow.attributes.dimAmount, 0.001f)
        } finally {
            other.pause().stop().destroy()
        }
    }

    @Test
    fun disablingCustomCollapsedBlurRestoresNativeStateOnTheNextUpdate() {
        configure(65, 0)
        val dialog = Dialog(activity.get().window)
        hook.intercept(Chain(dialog))
        configure(0, 0)
        hook.intercept(Chain(dialog))
        assertEquals(listOf(0.65f, 0f), dialog.blurTargets)
        assertEquals(0.15f, dialog.mWindow.attributes.dimAmount, 0.001f)
        assertTrue(dialog.mWindow.attributes.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
    }

    @Test
    fun forcingCollapsedDimensionsUsesTheCollapsedPreference() {
        configure(65, 80)
        val dialog = Dialog(activity.get().window).apply { mExpanded = true }
        hook.intercept(Chain(dialog, forceCollapsed = true))
        assertEquals(listOf(1f, 0.65f), dialog.blurTargets)
    }

    @Test
    fun aNativeWindowFailureIsPreservedWithoutApplyingCustomEffects() {
        configure(65, 80)
        val dialog = Dialog(activity.get().window)
        val failure = IllegalStateException("native window failure")
        val chain = Chain(dialog, failure = failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { hook.intercept(chain) })
        assertEquals(1, chain.proceeds)
        assertTrue(dialog.blurTargets.isEmpty())
    }

    private fun configure(collapsed: Int, expanded: Int) {
        MainModule.mPrefs.put("system_volumeblur_collapsed", collapsed)
        MainModule.mPrefs.put("system_volumeblur_expanded", expanded)
        SystemUIControlCenterHooks.refreshVolumeBlurSnapshot()
    }

    class Dialog(@JvmField val mWindow: Window) {
        @JvmField var mExpanded = false
        val blurTargets = mutableListOf<Float>()
        @Suppress("UNUSED_PARAMETER")
        fun startBlurAnim(from: Float, to: Float, duration: Int) { blurTargets += to }
        fun updateDialogWindowH(forceCollapsed: Boolean) {
            mWindow.setDimAmount(if (mExpanded && !forceCollapsed) 0.4f else 0.15f)
            if (mExpanded) {
                mWindow.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                startBlurAnim(0f, 1f, 0)
            } else {
                mWindow.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }
        }
    }

    private class Chain(val dialog: Dialog, val forceCollapsed: Boolean = false, val failure: Throwable? = null) : XposedInterface.Chain {
        var proceeds = 0
        override fun getExecutable(): Executable = Dialog::class.java.getMethod("updateDialogWindowH", Boolean::class.javaPrimitiveType)
        override fun getThisObject(): Any = dialog
        override fun getArgs(): List<Any?> = listOf(forceCollapsed)
        override fun getArg(index: Int): Any = forceCollapsed
        override fun proceed(): Any? { proceeds++; failure?.let { throw it }; dialog.updateDialogWindowH(forceCollapsed); return null }
        override fun proceed(args: Array<Any>): Any? = proceed()
        override fun proceedWith(target: Any): Any = error("unused")
        override fun proceedWith(target: Any, args: Array<Any>): Any = error("unused")
    }
}
