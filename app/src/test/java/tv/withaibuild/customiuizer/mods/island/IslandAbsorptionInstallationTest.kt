package tv.withaibuild.customiuizer.mods.island

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandAbsorptionInstallationTest {
    private val methods = listOf("frame", "finish", "cancel").map { Target::class.java.getDeclaredMethod(it) }
    private val attempts = ArrayList<Method>()
    private val removed = ArrayList<Method>()
    private val callbacks = LinkedHashMap<Method, XposedInterface.Hooker>()
    private var rejected: Method? = null
    private var fatal: Throwable? = null
    private var fatalCleanup: Throwable? = null
    private var previous: XposedInterface? = null

    @Before fun prepare() {
        previous = XposedHelpers.moduleInst
        XposedHelpers.moduleInst = Proxy.newProxyInstance(XposedInterface::class.java.classLoader,
            arrayOf(XposedInterface::class.java)) { _, method, args ->
            check(method.name == "hook")
            val target = args!![0] as Method
            attempts.add(target)
            object : XposedInterface.HookBuilder {
                override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
                override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
                override fun setId(id: String?): XposedInterface.HookBuilder = error("No new stable ID")
                override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                    if (target == rejected) throw (fatal ?: IllegalStateException("rejected"))
                    callbacks[target] = hooker
                    return Proxy.newProxyInstance(XposedInterface.HookHandle::class.java.classLoader,
                        arrayOf(XposedInterface.HookHandle::class.java)) { _, call, _ ->
                        when (call.name) {
                            "getExecutable" -> target
                            "unhook" -> {
                                removed.add(target); callbacks.remove(target)
                                fatalCleanup?.let { throw it }
                                null
                            }
                            else -> error("Unexpected handle operation: ${call.name}")
                        }
                    } as XposedInterface.HookHandle
                }
            }
        } as XposedInterface
    }

    @After fun restore() { XposedHelpers.moduleInst = previous }

    private fun install(endings: List<Method> = methods.drop(1)) = installIslandAbsorptionHooks(
        methods[0], object : MethodHook() {}, endings) { object : MethodHook() {} }

    @Test fun everyRejectedInstallationRetiresItsPartialGroupAndStopsInstalling() {
        for (index in methods.indices) {
            attempts.clear(); removed.clear(); callbacks.clear()
            rejected = methods[index]
            assertFalse(install())
            assertEquals(methods.take(index + 1), attempts)
            assertEquals(methods.take(index).reversed(), removed)
            assertTrue("No unused frame/ending observer survives a rejected optional mesh", callbacks.isEmpty())
        }
    }

    @Test fun noEndingsLeaveNativeUntouchedAndCompleteInstallationKeepsEveryCallback() {
        assertFalse(install(emptyList()))
        assertTrue(attempts.isEmpty())
        assertTrue(install())
        assertEquals(methods, attempts)
        assertEquals(methods.toSet(), callbacks.keys)
        assertTrue(removed.isEmpty())
    }

    @Test fun fatalInstallationKeepsItsIdentityAndStillRetiresEveryEarlierHook() {
        rejected = methods.last()
        val original = OutOfMemoryError("install")
        val cleanup = StackOverflowError("cleanup")
        fatal = original; fatalCleanup = cleanup
        assertSame(original, assertThrows(Throwable::class.java) { install() })
        assertEquals(methods.take(2).reversed(), removed)
        assertTrue(callbacks.isEmpty())
        assertTrue(original.suppressed.contains(cleanup))
    }

    @Test fun fatalCleanupContinuesThroughTheRemainingHandlesAndCannotEnableTheMesh() {
        rejected = methods.last()
        val cleanup = OutOfMemoryError("cleanup")
        fatalCleanup = cleanup
        assertSame(cleanup, assertThrows(Throwable::class.java) { install() })
        assertEquals(methods.take(2).reversed(), removed)
        assertTrue(callbacks.isEmpty())
    }

    private class Target {
        fun frame() = Unit
        fun finish() = Unit
        fun cancel() = Unit
    }
}
