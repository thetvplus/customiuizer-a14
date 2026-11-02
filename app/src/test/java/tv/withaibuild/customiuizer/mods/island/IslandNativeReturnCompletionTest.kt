package tv.withaibuild.customiuizer.mods.island

import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SharedMemory
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.util.ReflectionHelpers.getField
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IslandNativeReturnCompletionTest {
    @Test fun receivingUsesTheRequestedHorizontalRhythmWithALaterOneDpCatch() {
        for (alignment in 0..2) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("receiving-axis-order", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host)
            host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
            ShadowChoreographer.setPaused(true)
            try {
                shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
                shell.geometry(240, 40, if (alignment == 1) 40 else 0, 260, alignment)
                shell.cameraAnchor(20f, 28f, 40f)
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                shell.prepareEntry(mediaReturn = true)
                shell.transition(240, 40, 240, 40, entering = true)
                val clock = getField<ValueAnimator>(shell, "motion")
                var width = shell.scaleX * 240f
                var widest = width
                var widestAt = 0L
                var deepest = 40f
                var deepestAt = 0L
                var widthAtDeepest = 0f
                for (step in 0..100) {
                    writer.putInt(0, (step / 50f * 1_000_000).toInt().coerceAtMost(1_000_000))
                    clock.currentPlayTime = step * 10L
                    if (step == 80) assertSame("The depth catch keeps the shared clock alive", clock, getField<Any?>(shell, "motion"))
                    width = shell.scaleX * 240f
                    assertTrue("Keep the physical width boundary", width <= 260.002f)
                    if (width > widest) { widest = width; widestAt = step * 10L }
                    val height = shell.scaleY * 40f
                    if (height < deepest) { deepest = height; deepestAt = step * 10L; widthAtDeepest = width }
                }
                assertTrue("Retain a restrained horizontal rebound", widest in 240.5f..256f)
                assertTrue("Reach the horizontal crest near 360 ms", widestAt in 340L..385L)
                assertTrue("The vertical crest remains later", deepestAt - widestAt >= 250L)
                assertEquals("Horizontal rebound finishes before vertical catch", 240f, widthAtDeepest, .002f)
                assertTrue(deepest in 38.999f..39.01f)
                assertEquals(240f, width, .002f)
            } finally {
                getField<ValueAnimator?>(shell, "motion")?.cancel(); shell.cancelMediaReturn()
                SharedMemory.unmap(writer); memory.close(); host.finish(); ShadowChoreographer.setPaused(false)
            }
        }
    }

    @Test fun receivingUsesTheSameOneDpDepthAcrossAllAlignments() {
        fun track(gap: Int, alignment: Int): FloatArray {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("matching-vertical-receive", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host)
            host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
            ShadowChoreographer.setPaused(true)
            try {
                shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
                shell.geometry(240, 40, gap, 260, alignment); shell.cameraAnchor(20f, 28f, 40f)
                shell.prepareEntry(); shell.transition(240, 40, 240, 40, entering = true)
                getField<ValueAnimator>(shell, "motion").end()
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                shell.prepareReturnFromCurrent(); shell.transition(240, 40, 240, 40, entering = false)
                val clock = getField<ValueAnimator>(shell, "motion")
                val heights = FloatArray(101)
                for (step in heights.indices) {
                    val time = step * 10L
                    writer.putInt(0, (time / 500f * 1_000_000).toInt().coerceAtMost(1_000_000))
                    clock.currentPlayTime = time
                    heights[step] = shell.scaleY * 40f
                    assertTrue("Temporary compression is bounded", heights[step] >= 38.999f)
                }
                assertTrue("Receive compresses rather than uniformly enlarging", heights.min() < 39.5f)
                assertNull(getField<Any?>(shell, "motion"))
                return heights
            } finally {
                getField<ValueAnimator?>(shell, "motion")?.cancel(); shell.cancelMediaReturn()
                SharedMemory.unmap(writer); memory.close(); host.finish(); ShadowChoreographer.setPaused(false)
            }
        }
        val left = track(0, 0)
        val center = track(40, 1)
        assertArrayEquals(left, center, .002f)
        assertArrayEquals(left, track(0, 2), .002f)
    }

    @Test fun aMinimumDefaultCapsuleKeepsTheCameraWidthDuringIntake() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val memory = SharedMemory.create("minimum-default-return", 4)
        val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
        val shell = IslandActivityView(host)
        host.setContentView(shell, FrameLayout.LayoutParams(40, 40))
        ShadowChoreographer.setPaused(true)
        try {
            shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
            shell.geometry(40, 40, 40, 40)
            shell.cameraAnchor(20f, 38f)
            shell.prepareEntry()
            shell.transition(40, 40, 40, 40, entering = true)
            getField<ValueAnimator>(shell, "motion").end()
            shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
            shell.prepareReturnFromCurrent()
            shell.transition(40, 40, 40, 40, entering = false)
            val clock = getField<ValueAnimator>(shell, "motion")
            for (time in 0L..1000L step 10L) {
                writer.putInt(0, (time / 120f * 1_000_000).toInt().coerceAtMost(1_000_000))
                clock.currentPlayTime = time
                assertEquals("An inward cue cannot shrink the camera seed", 40f, shell.scaleX * 40f, .001f)
            }
            assertNull(getField<Any?>(shell, "motion"))
        } finally {
            getField<ValueAnimator?>(shell, "motion")?.cancel()
            shell.cancelMediaReturn()
            SharedMemory.unmap(writer); memory.close(); host.finish()
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test fun aLateDefaultChannelDoesNotReplayTheReceivingGesture() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val memory = SharedMemory.create("late-default-return", 4)
        val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
        val shell = IslandActivityView(host)
        host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
        ShadowChoreographer.setPaused(true)
        try {
            shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
            shell.geometry(240, 40, 40, 260)
            shell.cameraAnchor(20f, 16f)
            shell.prepareEntry()
            shell.transition(240, 40, 240, 40, entering = true)
            getField<ValueAnimator>(shell, "motion").end()
            writer.putInt(0, 980_000)
            shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
            shell.prepareReturnFromCurrent()
            shell.transition(240, 40, 240, 40, entering = false)
            val clock = getField<ValueAnimator>(shell, "motion")
            writer.putInt(0, 1_000_000)
            for (time in 0L..500L step 20L) {
                clock.currentPlayTime = time
                assertEquals("An already arrived page cannot replay intake or recoil", 240f, shell.scaleX * 240f, .001f)
            }
            assertNull(getField<Any?>(shell, "motion"))
        } finally {
            getField<ValueAnimator?>(shell, "motion")?.cancel()
            shell.cancelMediaReturn()
            SharedMemory.unmap(writer); memory.close(); host.finish()
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test fun absorptionRespondsDuringIntakeAfterBothAFirstLaunchAndReopeningFromTheIsland() {
        for (existing in listOf(false, true)) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("island-return-intake", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host)
            host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
            ShadowChoreographer.setPaused(true)
            try {
                shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
                shell.geometry(240, 40, 40, 260)
                shell.cameraAnchor(20f, 16f)
                if (existing) {
                    shell.prepareEntry()
                    shell.transition(240, 40, 240, 40, entering = true)
                    getField<ValueAnimator>(shell, "motion").end()
                }
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                if (existing) shell.prepareReturnFromCurrent() else shell.prepareEntry(mediaReturn = true)
                shell.transition(240, 40, 240, 40, entering = !existing)
                val clock = getField<ValueAnimator>(shell, "motion")
                val origin = shell.scaleX
                writer.putInt(0, 60_000)
                clock.currentPlayTime = 40L
                assertTrue("Intake must move the island before page completion; existing=$existing",
                    kotlin.math.abs(shell.scaleX - origin) * 240f > 1f)
                writer.putInt(0, 550_000)
                clock.currentPlayTime = 320L
                assertEquals("Horizontal receiving precedes vertical catch", 1f, shell.scaleY, .0001f)
                clock.currentPlayTime = 730L
                assertTrue("The delayed depth catch still uses the same arrival", shell.scaleY < .99f)
                assertEquals(.55f, getField<Float>(shell, "mediaReturnProgress"), .00001f)
            } finally {
                getField<ValueAnimator?>(shell, "motion")?.cancel()
                shell.cancelMediaReturn()
                SharedMemory.unmap(writer); memory.close(); host.finish()
                ShadowChoreographer.setPaused(false)
            }
        }
    }

    @Test fun anArrivalInterruptingATopExitCannotCarryItsUpwardVelocityIntoTheNewIsland() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val memory = SharedMemory.create("island-return-origin", 4)
        val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
        val shell = IslandActivityView(host)
        host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
        ShadowChoreographer.setPaused(true)
        try {
            shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
            shell.geometry(240, 40, 40)
            shell.prepareEntry()
            shell.transition(240, 40, 240, 40, entering = true)
            getField<ValueAnimator>(shell, "motion").end()
            shell.exit {}
            getField<ValueAnimator>(shell, "motion").currentPlayTime = 55L
            val origin = shell.translationY + shell.pivotY * (1f - shell.scaleY)
            assertTrue(origin < 0f)
            shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
            shell.resumeEntry()
            shell.prepareReturnFromCurrent()
            shell.transition(240, 40, 240, 40, entering = false)
            val clock = getField<ValueAnimator>(shell, "motion")
            var previous = origin
            for (step in 0..10) {
                writer.putInt(0, step * 100_000)
                clock.currentPlayTime = step * 25L
                val top = shell.translationY + shell.pivotY * (1f - shell.scaleY)
                assertTrue("The new arrival cannot continue travelling up", top >= previous - .0001f)
                assertTrue(top <= .0001f)
                previous = top
            }
        } finally {
            getField<ValueAnimator?>(shell, "motion")?.cancel()
            shell.cancelMediaReturn()
            SharedMemory.unmap(writer); memory.close(); host.finish()
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test fun nativeCompletionAndCancellationEndOnceWithoutReenteringTheFrameCallback() {
        for (terminal in intArrayOf(1_000_000, -1_000_000)) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("island-return-completion", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host)
            host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
            ShadowChoreographer.setPaused(true)
            var clock: ValueAnimator? = null
            try {
                shell.bind("Music", null, ColorDrawable(Color.RED), true, false, false, 0, false)
                shell.geometry(240, 40, 40)
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                shell.prepareEntry(mediaReturn = true)
                shell.transition(240, 40, 240, 40, entering = true)
                clock = getField(shell, "motion")
                assertNotNull("Exercise the native-driven clock, including its terminal frame", clock)
                assertTrue("Both effects start with a camera seed instead of a full-size jump", shell.scaleX < 1f)
                var endings = 0
                clock!!.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { endings++ }
                })
                writer.putInt(0, 400_000)
                clock!!.currentPlayTime = 200L
                assertEquals(.4f, getField<Float>(shell, "mediaReturnProgress"), .00001f)
                assertEquals(0, endings)
                writer.putInt(0, terminal)
                clock!!.currentPlayTime = 400L
                if (terminal > 0) {
                    assertEquals("Page completion must not cut off the island's damped settling", 0, endings)
                    assertSame(clock, getField<Any?>(shell, "motion"))
                    clock!!.currentPlayTime = 1000L
                }
                assertEquals("Exactly one terminal callback", 1, endings)
                clock!!.currentPlayTime = 1100L // A stale pulse cannot resume the retired owner.
                assertNull(getField<Any?>(shell, "motion"))
                assertNull(getField<Any?>(shell, "returnProgress"))
                assertEquals(1f, shell.scaleX, 0f)
                assertEquals(1f, shell.alpha, 0f)
                assertEquals(1f, getField<Float>(shell, "mediaReturnProgress"), 0f)
            } finally {
                clock?.removeAllUpdateListeners()
                clock?.cancel()
                shell.cancelMediaReturn()
                SharedMemory.unmap(writer)
                memory.close()
                host.finish()
                ShadowChoreographer.setPaused(false)
            }
        }
    }
}
