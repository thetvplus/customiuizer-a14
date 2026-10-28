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
    @Test fun absorptionRespondsDuringIntakeAfterBothAFirstLaunchAndReopeningFromTheIsland() {
        for (existing in listOf(false, true)) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("island-return-intake", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host).apply { absorption = true }
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
                assertTrue("The body also reacts while the page is still travelling", shell.scaleY < .99f)
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
        val shell = IslandActivityView(host).apply { absorption = true }
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
        for (absorption in listOf(false, true)) for (terminal in intArrayOf(1_000_000, -1_000_000)) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("island-return-completion", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host).apply { this.absorption = absorption }
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
                if (absorption && terminal > 0) {
                    assertEquals("Page completion must not cut off the island's damped settling", 0, endings)
                    assertSame(clock, getField<Any?>(shell, "motion"))
                    clock!!.currentPlayTime = 850L
                }
                assertEquals("Exactly one terminal callback", 1, endings)
                clock!!.currentPlayTime = 950L // A stale pulse cannot resume the retired owner.
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
