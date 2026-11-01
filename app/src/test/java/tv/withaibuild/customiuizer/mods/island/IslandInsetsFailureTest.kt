package tv.withaibuild.customiuizer.mods.island

import android.view.ViewTreeObserver
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.ClassName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@Implements(ViewTreeObserver::class)
class FailingInsetsObserverShadow {
    @Implementation fun addOnComputeInternalInsetsListener(
        @ClassName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener") listener: Any
    ) { addFailure?.let { throw it } }
    @Implementation fun removeOnComputeInternalInsetsListener(
        @ClassName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener") listener: Any
    ) { removeFailure?.let { throw it } }
    companion object {
        var addFailure: Throwable? = null
        var removeFailure: Throwable? = null
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [FailingInsetsObserverShadow::class])
class IslandInsetsFailureTest {
    @Before fun resetFailures() {
        FailingInsetsObserverShadow.addFailure = null
        FailingInsetsObserverShadow.removeFailure = null
    }
    @Test fun failedAttachDisablesInputAndRepeatedAttachDoesNotRetry() {
        val root = root()
        var failures = 0
        root.onUnavailable = { failures++ }
        FailingInsetsObserverShadow.addFailure = IllegalStateException("injected")
        attach(root)
        assertFalse(root.inputRegionAvailable)
        attach(root)
        assertEquals(1, failures)
        detach(root)
        assertNull(ReflectionHelpers.getField<ViewTreeObserver?>(root, "observer"))
    }

    @Test fun failedRemovalStillClearsOwnerAndCancelsPendingInput() {
        val root = root()
        FailingInsetsObserverShadow.removeFailure = IllegalStateException("injected")
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
        FailingInsetsObserverShadow.removeFailure = OutOfMemoryError("injected")
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
}
