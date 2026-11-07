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
    @Test fun aNewCameraSeedDoesNotBorrowTheExistingWideContainersAcceptanceStretch() {
        run {
            val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory=SharedMemory.create("new-seed-no-double-stretch",4)
            val writer=memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell=IslandActivityView(host)
            host.setContentView(shell,FrameLayout.LayoutParams(240,40))
            ShadowChoreographer.setPaused(true)
            try {
                shell.bind("Music",null,ColorDrawable(Color.RED),true,true,false,0L,false)
                shell.geometry(240,40,40,260);shell.layout(0,0,240,40)
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                shell.prepareEntry(mediaReturn=true);shell.transition(240,40,240,40,true)
                val clock=getField<ValueAnimator>(shell,"motion")
                for (at in 0L..600L step 10) {
                    writer.putInt(0,(at*1000L).toInt());clock.currentPlayTime=at
                    assertEquals("The new seed grows toward its form without a separate vertical bulge",40f,40*shell.scaleY,.001f)
                    assertTrue("One finite expansion, without another receive overshoot",240*shell.scaleX<=240.001f)
                }
                writer.putInt(0,1_000_000);clock.end()
                assertEquals(1f,shell.scaleX,0f);assertEquals(1f,shell.scaleY,0f)
            } finally {
                shell.cancelMediaReturn();host.finish();SharedMemory.unmap(writer);memory.close()
                ShadowChoreographer.setPaused(false)
            }
        }
    }
    @Test fun iosNativeAcceptanceUsesTheSameSlotSpringAfterAcceptedWithoutRestartingTheBody() {
        val host=Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val memory=SharedMemory.create("ios-contact",4)
        val writer=memory.mapReadWrite().order(ByteOrder.nativeOrder())
        val shell=IslandActivityView(host)
        host.setContentView(shell,FrameLayout.LayoutParams(240,40))
        ShadowChoreographer.setPaused(true)
        try {
            shell.geometry(240,40,40,280); shell.layout(0,0,240,40)
            shell.bind("Old",null,ColorDrawable(Color.RED),true,true,false,0L,false)
            shell.transition(240,40,240,40,false); getField<ValueAnimator>(shell,"motion").end()
            shell.bind("New",null,ColorDrawable(Color.BLUE),true,true,false,0L,false)
            shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
            shell.prepareReturnFromCurrent(); shell.transition(240,40,240,40,false)
            val clock=getField<ValueAnimator>(shell,"motion")
            writer.putInt(0,100_000); clock.currentPlayTime=100
            assertEquals(0f,getField<Float>(shell,"layerReveal"),.001f)
            assertTrue(shell.scaleY>1f)
            writer.putInt(0,600_000); clock.currentPlayTime=280
            val expected=IslandMotion.Spring().apply { retarget(0f,1f,IslandMotion.IOS_SLOTS,false) }
            assertEquals(expected.sample(.08f),getField<Float>(shell,"layerReveal"),.001f)
            assertSame(clock,getField<ValueAnimator>(shell,"motion"))
            shell.bind("Third",null,null,false,false,false,0L,false)
            shell.transition(240,40,240,40,false)
            assertNull(getField<Any?>(shell,"pendingContent"))
            getField<ValueAnimator>(shell,"motion").end()
            assertEquals(1f,shell.scaleY,0f); assertEquals(1f,getField<Float>(shell,"layerReveal"),0f)
        } finally {
            shell.cancelMediaReturn(); host.finish(); SharedMemory.unmap(writer); memory.close()
            ShadowChoreographer.setPaused(false)
        }
    }

    @Test fun secondActivityKeepsNewContentHiddenUntilTheNativeLeadingPhaseArrives() {
        val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
        val memory = SharedMemory.create("existing-shell-contact", 4)
        val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
        val shell = IslandActivityView(host)
        host.setContentView(shell, FrameLayout.LayoutParams(240, 40))
        ShadowChoreographer.setPaused(true)
        try {
            shell.geometry(240, 40, 40, 280); shell.layout(0, 0, 240, 40)
            shell.bind("Music", null, ColorDrawable(Color.RED), true, true, false, 0L, false)
            shell.transition(240, 40, 240, 40, false); getField<ValueAnimator>(shell, "motion").end()
            shell.bind("Stopwatch", null, ColorDrawable(Color.BLUE), false, false, false, 0L, false)
            shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
            shell.prepareReturnFromCurrent(); shell.transition(240, 40, 240, 40, false)
            val clock = getField<ValueAnimator>(shell, "motion")
            writer.putInt(0, 40_000); clock.currentPlayTime = 80L
            assertNull(getField<Any?>(shell, "pendingContent"))
            assertEquals(0f, getField<Float>(shell, "layerReveal"), .001f)
            writer.putInt(0, 200_000); clock.currentPlayTime = 100L
            writer.putInt(0, 500_000); clock.currentPlayTime = 260L
            assertNull(getField<Any?>(shell, "pendingContent"))
            assertEquals("Stopwatch", getField<String>(getField<Any>(shell, "displayedContent"), "label"))
            assertEquals(1f, shell.alpha, .001f)
            writer.putInt(0, 1_000_000); clock.end()
            assertEquals(1f, shell.scaleX, 0f); assertEquals(1f, shell.scaleY, 0f)
        } finally {
            shell.cancelMediaReturn(); host.finish(); SharedMemory.unmap(writer); memory.close()
            ShadowChoreographer.setPaused(false)
        }
    }
    @Test fun receivingUsesPositiveNativeHeightAndReturnsWithoutANegativeTail() {
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
                shell.prepareEntry()
                shell.transition(240, 40, 240, 40, entering = true)
                getField<ValueAnimator>(shell, "motion").end()
                shell.acceptReturnProgress(IslandReturnProgress(memory.mapReadOnly()))
                shell.prepareReturnFromCurrent()
                shell.transition(240, 40, 240, 40, entering = false)
                val clock = getField<ValueAnimator>(shell, "motion")
                var width = shell.scaleX * 240f
                var widest = width
                var widestAt = 0L
                for (step in 0..140) {
                    writer.putInt(0, (step / 50f * 1_000_000).toInt().coerceAtMost(1_000_000))
                    clock.currentPlayTime = step * 10L
                    if (step == 140) assertNull("Settled receive releases its finite owner", getField<Any?>(shell, "motion"))
                    width = shell.scaleX * 240f
                    assertTrue("Keep the physical width boundary", width <= 260.002f)
                    if (width > widest) { widest = width; widestAt = step * 10L }
                    val height = shell.scaleY * 40f
                    assertTrue("Receive may grow positively but must not compress below the configured height",height in 39.998f..66.001f)
                }
                assertTrue("Retain the native positive receiving preference", widest in 240.5f..260.001f)
                assertTrue("Advance the receive without a late synthetic pulse",widestAt in 100L..600L)
                assertEquals(240f, width, .002f)
            } finally {
                getField<ValueAnimator?>(shell, "motion")?.cancel(); shell.cancelMediaReturn()
                SharedMemory.unmap(writer); memory.close(); host.finish(); ShadowChoreographer.setPaused(false)
            }
        }
    }

    @Test fun receivingHeightIsIndependentOfAlignmentAndRestoresTheFinalSize() {
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
                val heights = FloatArray(141)
                for (step in heights.indices) {
                    val time = step * 10L
                    writer.putInt(0, (time / 500f * 1_000_000).toInt().coerceAtMost(1_000_000))
                    clock.currentPlayTime = time
                    heights[step] = shell.scaleY * 40f
                    assertTrue("Only a positive native receive is permitted",heights[step] in 39.998f..66.001f)
                }
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

    @Test fun lateAdoptionPreservesTheVisibleOriginAndStillReceivesOnTheSameClock() {
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
            for (time in 0L..1400L step 20L) {
                clock.currentPlayTime = time
                if(time==0L) assertEquals("Do not reset the adopted origin",240f,shell.scaleX*240f,.001f)
                assertTrue("Keep native receive within the safe width",shell.scaleX*240f in 239.999f..260.001f)
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
                if (existing) assertTrue("Accepted contact begins a positive receive without a page-percent gate",shell.scaleX>origin)
                else assertTrue("An appearing shell must grow during intake", kotlin.math.abs(shell.scaleX - origin) * 240f > 1f)
                writer.putInt(0, 140_000); clock.currentPlayTime = 80L
                clock.currentPlayTime = 150L
                assertTrue("Both paths respond before native completion", kotlin.math.abs(shell.scaleX - origin) * 240f > 1f)
                writer.putInt(0, 550_000)
                clock.currentPlayTime = 320L
                assertTrue("Native positive receiving height is allowed",shell.scaleY in 1f..1.651f)
                clock.currentPlayTime = if (existing) 810L else 730L
                assertTrue("No delayed negative depth response",shell.scaleY>=.99999f)
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
                assertEquals("Page completion or an invalid progress channel must not cut off settling", 0, endings)
                assertSame(clock, getField<Any?>(shell, "motion"))
                clock!!.currentPlayTime = 1000L
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
