package tv.withaibuild.customiuizer.mods.island

import android.view.ViewTreeObserver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandInsetsFailureTest {
    @Test fun failedAttachDisablesInputAndRepeatedAttachDoesNotRetry() {
        val root = root()
        var failures = 0
        root.onUnavailable = { failures++ }
        replaceBridge(root, "fail")
        attach(root)
        assertFalse(root.inputRegionAvailable)
        attach(root)
        assertEquals(1, failures)
        detach(root)
        assertNull(ReflectionHelpers.getField<ViewTreeObserver?>(root, "observer"))
    }

    @Test fun failedRemovalStillClearsOwnerAndCancelsPendingInput() {
        val root = root()
        replaceBridge(root, "accept", "fail")
        attach(root)
        ReflectionHelpers.setField(root, "tracking", true)
        detach(root)
        assertFalse(ReflectionHelpers.getField<Boolean>(root, "tracking"))
        assertTrue(ReflectionHelpers.getField<Boolean>(root.shell, "inputCancelled"))
        assertNull(ReflectionHelpers.getField<ViewTreeObserver?>(root, "observer"))
        assertFalse(root.inputRegionAvailable)
    }

    @Test fun fatalReflectionFailuresPropagateAfterDetachCleanup() {
        val root = root()
        replaceBridge(root, "accept", "fatal")
        attach(root)
        try {
            detach(root)
            fail("Fatal errors must propagate")
        } catch (error: OutOfMemoryError) {
            assertEquals("injected", error.message)
        }
        assertNull(ReflectionHelpers.getField<ViewTreeObserver?>(root, "observer"))
        assertTrue(ReflectionHelpers.getField<Boolean>(root.shell, "inputCancelled"))
    }

    @Test fun malformedCallbackDisablesTheOwnerAndRequestsNativeFallback() {
        val root = root()
        var failures = 0
        root.onUnavailable = { failures++ }
        val bridge = ReflectionHelpers.getField<Any>(root, "insets")
        val listener = ReflectionHelpers.getField<Any>(bridge, "listener")
        val handler = java.lang.reflect.Proxy.getInvocationHandler(listener)
        val type = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
        val method = type.declaredMethods.single()
        handler.invoke(listener, method, arrayOf(Any()))
        assertFalse(root.inputRegionAvailable)
        assertEquals(1, failures)
    }

    private fun root() = IslandTouchWindow(IslandActivityView(RuntimeEnvironment.getApplication()))
    private fun attach(root: IslandTouchWindow) = ReflectionHelpers.callInstanceMethod<Unit>(root, "onAttachedToWindow")
    private fun detach(root: IslandTouchWindow) = ReflectionHelpers.callInstanceMethod<Unit>(root, "onDetachedFromWindow")
    private fun replaceBridge(root: IslandTouchWindow, add: String, remove: String = "accept") {
        val type = Class.forName("tv.withaibuild.customiuizer.mods.island.IslandTouchWindow\$Insets")
        val constructor = type.declaredConstructors.single().apply { isAccessible = true }
        ReflectionHelpers.setField(root, "insets", constructor.newInstance(Any(),
            IslandInsetsFailureTest::class.java.getMethod(add, Any::class.java),
            IslandInsetsFailureTest::class.java.getMethod(remove, Any::class.java)))
    }

    companion object {
        @JvmStatic fun accept(listener: Any) = Unit
        @JvmStatic fun fail(listener: Any): Unit = throw IllegalStateException("injected")
        @JvmStatic fun fatal(listener: Any): Unit = throw OutOfMemoryError("injected")
    }
}
