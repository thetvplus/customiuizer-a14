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
    @Test fun nativeCompletionAndCancellationEndOnceWithoutReenteringTheFrameCallback() {
        for (terminal in intArrayOf(1_000_000, -1_000_000)) {
            val host = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
            val memory = SharedMemory.create("island-return-completion", 4)
            val writer = memory.mapReadWrite().order(ByteOrder.nativeOrder())
            val shell = IslandActivityView(host).apply { absorption = true }
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
                var endings = 0
                clock!!.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { endings++ }
                })
                writer.putInt(0, terminal)
                clock!!.currentPlayTime = 400L
                assertEquals("Exactly one terminal callback", 1, endings)
                clock!!.currentPlayTime = 800L // A stale pulse cannot resume the retired owner.
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
