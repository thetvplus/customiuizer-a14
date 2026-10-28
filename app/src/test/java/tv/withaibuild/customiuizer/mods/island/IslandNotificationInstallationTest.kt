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
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandNotificationInstallationTest {
    private val privacy = Target::class.java.getDeclaredMethod("privacy", List::class.java)
    private val binding = Target::class.java.getDeclaredMethod("bind", Any::class.java)
    private val privacyHook = object : MethodHook() {}
    private val bindHook = object : MethodHook() {}
    private val attempts = ArrayList<Method>()
    private val builders = linkedMapOf<Method, Builder>()
    private var previous: XposedInterface? = null

    @Before fun prepare() {
        previous = XposedHelpers.moduleInst
        framework()
    }

    @After fun restore() { XposedHelpers.moduleInst = previous }

    private fun framework() {
        attempts.clear()
        builders.clear()
        builders[privacy] = Builder(privacy)
        builders[binding] = Builder(binding)
        XposedHelpers.moduleInst = Proxy.newProxyInstance(
            XposedInterface::class.java.classLoader, arrayOf(XposedInterface::class.java),
        ) { _, method, args ->
            when (method.name) {
                "hook" -> (args!![0] as Method).let { target ->
                    attempts.add(target)
                    requireNotNull(builders[target])
                }
                else -> error("Unexpected framework call: ${method.name}")
            }
        } as XposedInterface
    }

    private fun install(method: Method? = privacy) =
        installIslandNotificationTakeover(method, binding, privacyHook, bindHook)

    @Test fun missingPrivacyEntryNeverInstallsNotificationTakeover() {
        assertFalse(install(null))
        assertTrue(attempts.isEmpty())
    }

    @Test fun rejectedPrivacyInstallationLeavesBothNativePathsUntouched() {
        builders.getValue(privacy).failure = IllegalStateException("policy hook rejected")
        assertFalse(install())
        assertEquals(listOf(privacy), attempts)
        assertNull(builders.getValue(binding).callback)
        assertEquals(0, builders.getValue(privacy).unhooks)
    }

    @Test fun rejectedBindInstallationRetiresOnlyTheNewPrivacyObserver() {
        builders.getValue(binding).failure = IllegalStateException("bind hook rejected")
        assertFalse(install())
        assertEquals(listOf(privacy, binding), attempts)
        assertEquals(1, builders.getValue(privacy).unhooks)
        assertEquals(0, builders.getValue(binding).unhooks)
    }

    @Test fun successfulPairKeepsTheOriginalCallbacksAndDoesNotRunOrRetireThem() {
        assertTrue(install())
        assertEquals(listOf(privacy, binding), attempts)
        assertSame(privacyHook, builders.getValue(privacy).callback)
        assertSame(bindHook, builders.getValue(binding).callback)
        assertEquals(0, builders.getValue(privacy).unhooks)
        assertEquals(0, builders.getValue(binding).unhooks)
    }

    @Test fun fatalBindFailuresRetireTheObserverAndKeepTheOriginalFatalIdentity() {
        for (fatal in listOf(OutOfMemoryError("install"), StackOverflowError("install"), ThreadDeath())) {
            for (failure in listOf(fatal, InvocationTargetException(fatal))) {
                framework()
                builders.getValue(binding).failure = failure
                assertSame(fatal, assertThrows(Throwable::class.java) { install() })
                assertEquals(listOf(privacy, binding), attempts)
                assertEquals(1, builders.getValue(privacy).unhooks)
                assertEquals(0, builders.getValue(binding).unhooks)
            }
        }
    }

    @Test fun anOrdinaryRollbackFailureCannotEnableTheRejectedTakeover() {
        builders.getValue(binding).failure = IllegalStateException("bind hook rejected")
        builders.getValue(privacy).unhookFailure = IllegalStateException("unhook rejected")
        assertFalse(install())
        assertEquals(1, builders.getValue(privacy).unhooks)
        assertNull(builders.getValue(binding).callback)
    }

    @Test fun fatalRollbackFailuresAreNotSwallowed() {
        val fatal = OutOfMemoryError("unhook")
        builders.getValue(binding).failure = IllegalStateException("bind hook rejected")
        builders.getValue(privacy).unhookFailure = InvocationTargetException(fatal)
        assertSame(fatal, assertThrows(Throwable::class.java) { install() })
        assertEquals(1, builders.getValue(privacy).unhooks)
    }

    @Test fun aFatalRollbackCannotReplaceTheOriginalFatalInstallFailure() {
        val original = OutOfMemoryError("bind install")
        val cleanup = StackOverflowError("observer cleanup")
        builders.getValue(binding).failure = InvocationTargetException(original)
        builders.getValue(privacy).unhookFailure = InvocationTargetException(cleanup)
        assertSame(original, assertThrows(Throwable::class.java) { install() })
        assertArrayEquals(arrayOf(cleanup), original.suppressed)
        assertEquals(1, builders.getValue(privacy).unhooks)
    }

    private class Target {
        @Suppress("UNUSED_PARAMETER") fun privacy(entries: List<Any>) = Unit
        @Suppress("UNUSED_PARAMETER") fun bind(entry: Any) = Unit
    }

    private class Builder(private val target: Method) : XposedInterface.HookBuilder {
        var failure: Throwable? = null
        var unhookFailure: Throwable? = null
        var unhooks = 0
        var callback: XposedInterface.Hooker? = null
        private val handle = Proxy.newProxyInstance(
            XposedInterface.HookHandle::class.java.classLoader, arrayOf(XposedInterface.HookHandle::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "unhook" -> { unhooks++; unhookFailure?.let { throw it }; null }
                "getExecutable" -> target
                else -> error("Unexpected handle call: ${method.name}")
            }
        } as XposedInterface.HookHandle

        override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
        override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
        override fun setId(id: String?): XposedInterface.HookBuilder = error("No new stable Hook ID")
        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
            failure?.let { throw it }
            callback = hooker
            return handle
        }
    }
}
