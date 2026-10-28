package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LauncherDockGestureTest {
    @Test fun observerFailureCannotSkipNativeTouchAndNativeExceptionsKeepTheirIdentity() {
        val factory = LauncherGestureHooks.hotSeatDetectorFactory
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        val owner = FrameLayout(activity.get())
        var nativeCalls = 0
        var nativeFailure: Throwable? = null
        val chain = java.lang.reflect.Proxy.newProxyInstance(io.github.libxposed.api.XposedInterface.Chain::class.java.classLoader,
            arrayOf(io.github.libxposed.api.XposedInterface.Chain::class.java)) { _, method, args ->
            when (method.name) {
                "getThisObject" -> owner
                "getArg" -> { assertEquals(0, args!![0]); event }
                "proceed" -> { assertTrue(args.isNullOrEmpty()); nativeCalls++; nativeFailure?.let { throw it }; false }
                else -> error("Unexpected touch chain read: ${method.name}")
            }
        } as io.github.libxposed.api.XposedInterface.Chain
        try {
            LauncherGestureHooks.hotSeatDetectorFactory = { _, _ -> throw IllegalStateException("detector unavailable") }
            val hook = LauncherGestureHooks.hotSeatTouchHook()
            assertEquals(false, hook.intercept(chain))
            assertEquals(1, nativeCalls)
            val failure = IllegalStateException("native touch failure")
            nativeFailure = failure
            assertSame(failure, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(2, nativeCalls)
            val fatal = OutOfMemoryError("detector fatal")
            LauncherGestureHooks.hotSeatDetectorFactory = { _, _ -> throw fatal }
            assertSame(fatal, assertThrows(Throwable::class.java) { hook.intercept(chain) })
            assertEquals(2, nativeCalls)
        } finally { LauncherGestureHooks.hotSeatDetectorFactory = factory; event.recycle(); activity.pause().stop().destroy() }
    }

    @Test fun horizontalFlingRequiresBothDisplacementAndVelocityToFavorItsOwnAxis() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().visible()
        val saved = MainModule.mPrefs.getAll()
        MainModule.mPrefs.put("launcher_swipeleft_action", 2)
        MainModule.mPrefs.put("launcher_swiperight_action", 2)
        try {
            val owner = FrameLayout(activity.get())
            activity.get().setContentView(owner)
            val constructor = Class.forName("tv.withaibuild.customiuizer.mods.LauncherGestureHooks\$SwipeListenerHorizontal")
                .getDeclaredConstructor(Any::class.java).apply { isAccessible = true }
            val listener = constructor.newInstance(owner) as GestureDetector.SimpleOnGestureListener
            fun fling(x: Float, y: Float, vx: Float, vy: Float): Boolean {
                val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, x, y, 0)
                return try { listener.onFling(down, up, vx, vy) } finally { down.recycle(); up.recycle() }
            }
            assertFalse("Vertical intent must stay with Launcher", fling(100f, 240f, 500f, 1200f))
            assertFalse("The last fast vertical segment cannot submit a horizontal action", fling(-120f, 30f, -300f, 900f))
            assertFalse("Equal-axis ambiguity belongs to native input", fling(120f, 120f, 800f, 800f))
            assertTrue(fling(180f, 30f, 1000f, 200f))
            assertTrue(fling(-180f, 30f, -1000f, 200f))
        } finally { MainModule.mPrefs.replaceSnapshot(saved); activity.pause().stop().destroy() }
    }
}
