package tv.withaibuild.customiuizer.mods

import android.hardware.display.DisplayManager
import android.view.Display
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class HiddenNavigationBoundaryTest {
    @Test fun navigationStatePreparationIsLimitedToTheDefaultDisplaysNavigationWindow() {
        var prepared = 0
        val hook = Controls.hiddenNavWindowStateHook { prepared++ }
        for ((display, window) in listOf(1 to 2, 0 to 1, 0 to 0)) {
            val chain = Chain(listOf(display, window, 0))
            assertEquals(41, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
            assertEquals(0, prepared)
        }
        val chain = Chain(listOf(0, 2, 0))
        assertEquals(41, hook.intercept(chain)); assertEquals(1, chain.nativeCalls)
        assertEquals(1, prepared)
    }
    @Test fun failedModulePreparationStillCallsNativeOnceAndNativeFailureIsUnchanged() {
        val hook = Controls.hiddenNavWindowStateHook { error("missing native dependency") }
        val normal = Chain(listOf(0, 2, 0))
        assertEquals(41, hook.intercept(normal)); assertEquals(1, normal.nativeCalls)
        val failure = IllegalStateException("native window state")
        val broken = Chain(listOf(0, 2, 0), failure)
        assertSame(failure, assertThrows(IllegalStateException::class.java) { hook.intercept(broken) })
        assertEquals(1, broken.nativeCalls)
    }
    @Test fun hidingThePrimaryNavigationBarDoesNotSuppressSecondaryDisplayCreation() {
        val displays = RuntimeEnvironment.getApplication().getSystemService(DisplayManager::class.java)
        val primary = requireNotNull(displays.getDisplay(Display.DEFAULT_DISPLAY))
        val extra = ShadowDisplayManager.addDisplay("w800dp-h600dp")
        val secondary = requireNotNull(displays.getDisplay(extra))
        val hook = Controls.hiddenNavCreationHook()
        val main = Chain(listOf(primary, null, null))
        assertNull(hook.intercept(main)); assertEquals(0, main.nativeCalls)
        val other = Chain(listOf(secondary, null, null))
        assertEquals(41, hook.intercept(other)); assertEquals(1, other.nativeCalls)
        val unknown = Chain(listOf(null, null, null))
        assertEquals(41, hook.intercept(unknown)); assertEquals(1, unknown.nativeCalls)
        ShadowDisplayManager.removeDisplay(extra)
    }
    private class Chain(private val values: List<Any?>, private val failure: Throwable? = null) : XposedInterface.Chain {
        var nativeCalls = 0
        override fun getExecutable(): Executable = error("unused")
        override fun getThisObject(): Any = error("unrelated owners must not be read")
        override fun getArgs(): List<Any?> = error("must not copy arguments")
        override fun getArg(index: Int): Any? = values[index]
        override fun proceed(): Any? { nativeCalls++; failure?.let { throw it }; return 41 }
        override fun proceed(args: Array<Any?>): Any? = error("unused")
        override fun proceedWith(receiver: Any): Any? = error("unused")
        override fun proceedWith(receiver: Any, args: Array<Any>): Any? = error("unused")
    }
}
