package tv.withaibuild.customiuizer.mods

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Executable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VolumePercentageLifecycleTest {
    private val context = RuntimeEnvironment.getApplication()
    private val first = FrameLayout(context).apply { tag = PanelOwner() }
    private val second = FrameLayout(context).apply { tag = PanelOwner() }
    private val hook = SystemUIControlCenterHooks.VolumePercentageProgressHook(Listener::class.java.getField("owner"))

    @After
    fun tearDown() {
        val field = SystemUIControlCenterHooks::class.java.getDeclaredField("mPctRef").apply { isAccessible = true }
        field.set(null, null)
        SystemUIControlCenterHooks::class.java.getDeclaredField("mPctOwnerRef").apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun aRebuiltPanelGetsItsOwnViewAndTheOldPanelIsReleased() {
        val old = init(first)
        val current = init(second)
        assertNotSame(old, current)
        assertNull(old.parent)
        assertSame(second, current.parent)
        assertEquals(0, first.childCount)
    }

    @Test
    fun switchingFromBrightnessToVolumeMovesToTheActualVolumeWindow() {
        val brightness = init(first, 2)
        val volume = init(second, 3)
        assertNotSame(brightness, volume)
        assertNull(brightness.parent)
        assertSame(second, volume.parent)
    }

    @Test
    fun reopeningAtTheSameVolumeStillShowsThePercentage() {
        val listener = Listener(first)
        val seekBar = SeekBar(context).apply { max = 15000 }
        val pct = init(first)
        hook.intercept(Chain(listener, seekBar, 5000))
        assertEquals("33%", pct.text.toString())
        init(first)
        hook.intercept(Chain(listener, seekBar, 5000))
        assertEquals(View.VISIBLE, pct.visibility)
        assertEquals("33%", pct.text.toString())
    }

    @Test
    fun equalProgressFromDifferentStreamsUsesEachStreamsOwnMaximum() {
        val pct = init(first)
        hook.intercept(Chain(Listener(first), SeekBar(context).apply { max = 15000 }, 5000))
        hook.intercept(Chain(Listener(first), SeekBar(context).apply { max = 7000 }, 5000))
        assertEquals("71%", pct.text.toString())
    }

    @Test
    fun anUninitializedSeekBarDoesNotExposeAnEmptyOverlay() {
        val pct = init(first)
        hook.intercept(Chain(Listener(first), SeekBar(context).apply { max = 0 }, 0))
        assertEquals(View.GONE, pct.visibility)
        assertEquals("", pct.text.toString())
    }

    @Test
    fun anOldPanelsProgressDoesNotUpdateTheNewPanelsOverlay() {
        init(first)
        val pct = init(second)
        val chain = Chain(Listener(first), SeekBar(context).apply { max = 15000 }, 5000)
        hook.intercept(chain)
        assertEquals(1, chain.proceeds)
        assertEquals(View.GONE, pct.visibility)
        assertEquals("", pct.text.toString())
    }

    @Test
    fun volumeCallbacksDoNotOverwriteTheBrightnessOverlay() {
        val pct = init(first, 2)
        hook.intercept(Chain(Listener(first), SeekBar(context).apply { max = 15000 }, 5000))
        assertEquals("", pct.text.toString())
    }

    @Test
    fun anOldDismissOrAnotherSourcesStopDoesNotRemoveTheCurrentOverlay() {
        init(first)
        val pct = init(second)
        remove(first, 3)
        remove(second, 2)
        assertSame(second, pct.parent)
        remove(second, 3)
        assertNull(pct.parent)
        assertOverlayReferencesCleared()
    }

    @Test
    fun replacingTheControllerWithinTheSameContainerReleasesTheOldOverlay() {
        val old = init(first)
        val current = init(first, owner = Any())
        assertNotSame(old, current)
        assertNull(old.parent)
        assertEquals(1, first.childCount)
    }

    @Test
    fun nativeFailureDoesNotExposeOrUpdateTheOverlay() {
        val pct = init(first)
        val failure = IllegalStateException("native progress failure")
        val chain = Chain(Listener(first), SeekBar(context).apply { max = 15000 }, 5000, failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { hook.intercept(chain) })
        assertEquals(1, chain.proceeds)
        assertEquals(View.GONE, pct.visibility)
        assertEquals("", pct.text.toString())
    }

    @Test
    fun nativeMinimumAndMaximumLevelsRemainBounded() {
        val pct = init(first)
        val listener = Listener(first).apply { mColumn.ss.levelMin = 2 }
        val seekBar = SeekBar(context).apply { max = 7000 }
        hook.intercept(Chain(listener, seekBar, 0))
        assertEquals("28%", pct.text.toString())
        hook.intercept(Chain(listener, seekBar, 7000))
        assertEquals("100%", pct.text.toString())
    }

    @Test
    fun anAnimationDeferredDismissKeepsThePercentageUntilTheNativeDismissIsAccepted() {
        val owner = PanelOwner()
        val pct = init(first, owner = owner)
        val dismissHook = SystemUIControlCenterHooks.VolumePercentageDismissHook()
        val deferred = DismissChain(owner, dismiss = false)
        dismissHook.intercept(deferred)
        assertEquals(1, deferred.proceeds)
        assertSame(first, pct.parent)
        val accepted = DismissChain(owner, dismiss = true)
        dismissHook.intercept(accepted)
        assertEquals(1, accepted.proceeds)
        assertNull(pct.parent)
        assertOverlayReferencesCleared()
    }

    @Test
    fun detachingTheWindowReleasesTheOwnerAndReattachingDoesNotDuplicateTheOverlay() {
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        try {
            activity.get().setContentView(first)
            val pct = init(first).apply { visibility = View.VISIBLE }
            activity.get().setContentView(second)
            val ownerRef = SystemUIControlCenterHooks::class.java.getDeclaredField("mPctOwnerRef").apply { isAccessible = true }
            assertNull(ownerRef.get(null))
            assertEquals(View.GONE, pct.visibility)
            activity.get().setContentView(first)
            val current = init(first)
            assertNotSame(pct, current)
            assertNull(pct.parent)
            assertSame(first, current.parent)
            assertEquals(1, first.childCount)
        } finally {
            activity.pause().stop().destroy()
        }
    }

    @Test fun compactVolumeStyleDoesNotChangeBrightnessTypography() {
        val volume = init(first)
        assertEquals(26f, volume.textSize / context.resources.displayMetrics.scaledDensity, .01f)
        assertFalse(volume.includeFontPadding)
        val brightness = init(second, 2)
        assertEquals(40f, brightness.textSize / context.resources.displayMetrics.scaledDensity, .01f)
    }

    @Test fun currentInsetsMoveOnlyTheVolumeHintAfterAWindowConfigurationChange() {
        val pct = init(first)
        for (top in listOf(180, 104, 0)) {
            pct.dispatchApplyWindowInsets(android.view.WindowInsets.Builder()
                .setInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars(), android.graphics.Insets.of(0, top, 0, 0)).build())
            assertEquals(top, (pct.layoutParams as FrameLayout.LayoutParams).topMargin)
        }
        val brightness = init(second, 2)
        val previous = (brightness.layoutParams as FrameLayout.LayoutParams).topMargin
        brightness.dispatchApplyWindowInsets(android.view.WindowInsets.Builder()
            .setInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars(), android.graphics.Insets.of(0, 180, 0, 0)).build())
        assertEquals(previous, (brightness.layoutParams as FrameLayout.LayoutParams).topMargin)
    }

    @Test fun inactiveStreamsCannotOverwriteTheActiveStreamsPercentage() {
        val pct = init(first)
        val listener = Listener(first).apply { mColumn.stream = 0 }
        hook.intercept(Chain(listener, SeekBar(context).apply { max = 15000 }, 15000, fromUser = false))
        assertEquals("", pct.text.toString())
    }

    @Test fun draggingAnotherExpandedStreamTakesPrecedenceOverBackgroundUpdates() {
        val pct = init(first)
        val dragged = Listener(first).apply { mColumn.stream = 0 }
        hook.track(dragged, true)
        hook.intercept(Chain(dragged, SeekBar(context).apply { max = 15000 }, 15000))
        assertEquals("100%", pct.text.toString())
        hook.intercept(Chain(Listener(first), SeekBar(context).apply { max = 15000 }, 2000, fromUser = false))
        assertEquals("100%", pct.text.toString())
        hook.track(dragged, false)
    }

    @Test fun aNonAnimatedNativeUpdateUsesCurrentStateInsteadOfLeavingTheLabelEmpty() {
        val pct = init(first)
        val listener = Listener(first).apply { mColumn.ss.level = 5 }
        hook.intercept(Chain(listener, SeekBar(context).apply { max = 15000 }, 2000, fromUser = false))
        assertEquals("33%", pct.text.toString())
        assertEquals(View.VISIBLE, pct.visibility)
    }

    private fun init(container: ViewGroup, source: Int = 3, owner: Any = container.tag ?: container): TextView {
        val method = SystemUIControlCenterHooks::class.java.getDeclaredMethod(
            "initPct", ViewGroup::class.java, Int::class.javaPrimitiveType, android.content.Context::class.java,
            Any::class.java,
        ).apply { isAccessible = true }
        return method.invoke(SystemUIControlCenterHooks, container, source, context, owner) as TextView
    }

    private fun remove(owner: Any, source: Int) {
        SystemUIControlCenterHooks::class.java.getDeclaredMethod("removePct", Any::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(SystemUIControlCenterHooks, (owner as? View)?.tag ?: owner, source)
    }

    private fun assertOverlayReferencesCleared() {
        for (name in listOf("mPctRef", "mPctOwnerRef")) {
            val field = SystemUIControlCenterHooks::class.java.getDeclaredField(name).apply { isAccessible = true }
            assertNull(name, field.get(null))
        }
    }

    class State { @JvmField var levelMin = 0; @JvmField var level = 5 }
    class Column {
        @JvmField val ss = State()
        @JvmField var stream = 3
        @JvmField val anim: android.animation.ObjectAnimator? = null
        @JvmField val animTargetProgress = 0
    }
    class Listener(container: FrameLayout) { @JvmField val owner = container.tag as PanelOwner; @JvmField val mColumn = Column() }
    class PanelOwner { @JvmField var mShowing = true; @JvmField var mActiveStream = 3 }

    private class DismissChain(val owner: PanelOwner, val dismiss: Boolean) : XposedInterface.Chain {
        var proceeds = 0
        override fun getExecutable(): Executable = Any::class.java.getMethod("toString")
        override fun getThisObject(): Any = owner
        override fun getArgs(): List<Any?> = listOf(2)
        override fun getArg(index: Int): Any = 2
        override fun proceed(): Any? { proceeds++; if (dismiss) owner.mShowing = false; return null }
        override fun proceed(args: Array<Any>): Any? = proceed()
        override fun proceedWith(target: Any): Any = error("unused")
        override fun proceedWith(target: Any, args: Array<Any>): Any = error("unused")
    }

    private class Chain(val listener: Listener, val seekBar: SeekBar, val progress: Int, val failure: Throwable? = null, val fromUser: Boolean = true) : XposedInterface.Chain {
        var proceeds = 0
        override fun getExecutable(): Executable = Any::class.java.getMethod("toString")
        override fun getThisObject(): Any = listener
        override fun getArgs(): List<Any?> = listOf(seekBar, progress, fromUser)
        override fun getArg(index: Int): Any? = getArgs()[index]
        override fun proceed(): Any? { proceeds++; failure?.let { throw it }; return null }
        override fun proceed(args: Array<Any>): Any? = proceed()
        override fun proceedWith(target: Any): Any = error("unused")
        override fun proceedWith(target: Any, args: Array<Any>): Any = error("unused")
    }
}
