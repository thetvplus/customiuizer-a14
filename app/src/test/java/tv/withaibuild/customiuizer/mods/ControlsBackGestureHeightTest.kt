package tv.withaibuild.customiuizer.mods

import android.view.WindowManager
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class ControlsBackGestureHeightTest {
    private val saved = Controls.controlsConfig
    private val owner = Stub()
    private val params = WindowManager.LayoutParams()
    private var calls = 0
    private var failure: Throwable? = null
    private val chain = Proxy.newProxyInstance(XposedInterface.Chain::class.java.classLoader,
        arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
        when (method.name) {
            "getThisObject" -> owner
            "proceed" -> {
                calls++
                failure?.let { throw it }
                // The ROM rewrites the same params on every call, with a full-height
                // side window in portrait split screen and a thin bottom window.
                params.width = if (owner.mGestureStubPos == 2) -1 else 54
                params.height = if (owner.mGestureStubPos == 2) 54
                    else if (owner.mRotation % 2 == 0) {
                        if (owner.mIsInMultiWindowMode && !owner.mIsInMinimizedMultiWindowMode) 2400 else 1440
                    } else 648
                params
            }
            else -> error("Unexpected chain access ${method.name}")
        }
    } as XposedInterface.Chain

    @After fun restore() { Controls.controlsConfig = saved }

    @Test fun sideCoverageUsesTheNativeRotationAndSplitScreenBaselineWithoutAccumulation() {
        Controls.controlsConfig = saved.copy(fsgCoverage = 80)
        val hook = Controls.backGestureHeightHook()
        for (position in 0..1) for (rotation in 0..3) for (multi in listOf(false, true)) {
            owner.mGestureStubPos = position
            owner.mRotation = rotation
            owner.mIsInMultiWindowMode = multi
            for (minimized in listOf(false, true)) {
                owner.mIsInMinimizedMultiWindowMode = minimized
                repeat(2) {
                    assertSame(params, hook.intercept(chain))
                    assertEquals(if (rotation % 2 == 0) 1920 else 864, params.height)
                    assertEquals(54, params.width)
                }
            }
        }
        assertEquals(64, calls)
    }

    @Test fun changingSideCoverageNeverChangesTheBottomGestureStrip() {
        owner.mGestureStubPos = 2
        for (coverage in listOf(20, 60, 95)) {
            Controls.controlsConfig = saved.copy(fsgCoverage = coverage)
            assertSame(params, Controls.backGestureHeightHook().intercept(chain))
            assertEquals(54, params.height)
            assertEquals(-1, params.width)
        }
    }

    @Test fun defaultRestoresTheNativeSplitScreenHeightAndNativeErrorsPropagateOnce() {
        Controls.controlsConfig = saved.copy(fsgCoverage = 60)
        owner.mIsInMultiWindowMode = true
        assertSame(params, Controls.backGestureHeightHook().intercept(chain))
        assertEquals(2400, params.height)
        for (error in listOf(IllegalStateException("native failed"), OutOfMemoryError("native fatal"))) {
            failure = error
            assertSame(error, assertThrows(error.javaClass) { Controls.backGestureHeightHook().intercept(chain) })
            assertEquals(2400, params.height)
        }
        assertEquals(3, calls)
    }

    class Stub {
        @JvmField var mGestureStubPos = 0
        @JvmField var mRotation = 0
        @JvmField var mIsInMultiWindowMode = false
        @JvmField var mIsInMinimizedMultiWindowMode = false
    }
}
