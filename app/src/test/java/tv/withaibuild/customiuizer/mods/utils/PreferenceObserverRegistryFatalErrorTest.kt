package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException

/**
 * Fatal-throwable boundary for [PreferenceObserverRegistry.handlePreferenceChanged].
 *
 * Only [OutOfMemoryError], [ThreadDeath], [VirtualMachineError] (other than OOM),
 * and [LinkageError] must propagate through the dispatch path. Ordinary runtime
 * exceptions must be swallowed and logged without stopping later observers.
 */
class PreferenceObserverRegistryFatalErrorTest {

    private class RecordingObserver : ModuleHelper.PreferenceObserver {
        val received = mutableListOf<String?>()
        override fun onChange(key: String?) {
            received.add(key)
        }
    }

    private class ThrowingObserver(private val toThrow: Throwable) : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            throw toThrow
        }
    }

    private class TestVirtualMachineError : VirtualMachineError("test virtual machine error")
    private class TestLinkageError : LinkageError("test linkage error")

    @After
    fun tearDown() {
        clearProcessObservers()
    }

    private fun clearProcessObservers() {
        val field = PreferenceObserverRegistry::class.java.getDeclaredField("observers")
            .apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val set = field.get(PreferenceObserverRegistry) as? java.util.concurrent.CopyOnWriteArraySet<ModuleHelper.PreferenceObserver>
        set?.clear()
    }

    // ---------------------------------------------------------------------------
    // Process-scoped observers
    // ---------------------------------------------------------------------------

    @Test
    fun processObserver_threadDeath_isRethrownAndStopsDispatch() {
        val fatal = ThreadDeath()
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        try {
            ModuleHelper.handlePreferenceChanged("system_x")
            org.junit.Assert.fail("expected ThreadDeath")
        } catch (t: ThreadDeath) {
            assertSame(fatal, t)
            assertEquals(listOf("system_x"), first.received)
            assertTrue(last.received.isEmpty())
        }
    }

    @Test
    fun processObserver_linkageError_isRethrownAndStopsDispatch() {
        val fatal = TestLinkageError()
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        try {
            ModuleHelper.handlePreferenceChanged("system_y")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("system_y"), first.received)
            assertTrue(last.received.isEmpty())
        }
    }

    @Test
    fun processObserver_virtualMachineError_isRethrownAndStopsDispatch() {
        val fatal = TestVirtualMachineError()
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        try {
            ModuleHelper.handlePreferenceChanged("system_z")
            org.junit.Assert.fail("expected VirtualMachineError")
        } catch (vm: VirtualMachineError) {
            assertSame(fatal, vm)
            assertEquals(listOf("system_z"), first.received)
            assertTrue(last.received.isEmpty())
        }
    }

    @Test
    fun processObserver_outOfMemoryError_isRethrownAndStopsDispatch() {
        val fatal = OutOfMemoryError("test oom")
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        try {
            ModuleHelper.handlePreferenceChanged("system_oom")
            org.junit.Assert.fail("expected OutOfMemoryError")
        } catch (oom: OutOfMemoryError) {
            assertSame(fatal, oom)
            assertEquals(listOf("system_oom"), first.received)
            assertTrue(last.received.isEmpty())
        }
    }

    @Test
    fun processObserver_ordinaryRuntimeException_isSwallowedAndDoesNotStopDispatch() {
        val first = RecordingObserver()
        val throwing = ThrowingObserver(IllegalStateException("ordinary"))
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        ModuleHelper.handlePreferenceChanged("system_normal")

        assertEquals(listOf("system_normal"), first.received)
        assertEquals(listOf("system_normal"), last.received)
    }

    // ---------------------------------------------------------------------------
    // Owner-bound / weak observers
    // ---------------------------------------------------------------------------

    @Test
    fun ownerBoundObserver_threadDeath_isRethrownAndStopsDispatch() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val fatal = ThreadDeath()
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_x")
            org.junit.Assert.fail("expected ThreadDeath")
        } catch (t: ThreadDeath) {
            assertSame(fatal, t)
            assertEquals(listOf("owner_x"), first.received)
            assertTrue(last.received.isEmpty())
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    @Test
    fun ownerBoundObserver_linkageError_isRethrownAndStopsDispatch() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val fatal = TestLinkageError()
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_y")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("owner_y"), first.received)
            assertTrue(last.received.isEmpty())
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    @Test
    fun ownerBoundObserver_virtualMachineError_isRethrownAndStopsDispatch() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val fatal = TestVirtualMachineError()
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_z")
            org.junit.Assert.fail("expected VirtualMachineError")
        } catch (vm: VirtualMachineError) {
            assertSame(fatal, vm)
            assertEquals(listOf("owner_z"), first.received)
            assertTrue(last.received.isEmpty())
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    @Test
    fun ownerBoundObserver_outOfMemoryError_isRethrownAndStopsDispatch() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val fatal = OutOfMemoryError("test oom")
        val first = RecordingObserver()
        val throwing = ThrowingObserver(fatal)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_oom")
            org.junit.Assert.fail("expected OutOfMemoryError")
        } catch (oom: OutOfMemoryError) {
            assertSame(fatal, oom)
            assertEquals(listOf("owner_oom"), first.received)
            assertTrue(last.received.isEmpty())
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    @Test
    fun ownerBoundObserver_ordinaryRuntimeException_isSwallowedAndDoesNotStopDispatch() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val first = RecordingObserver()
        val throwing = ThrowingObserver(IllegalStateException("ordinary"))
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_normal")

            assertEquals(listOf("owner_normal"), first.received)
            assertEquals(listOf("owner_normal"), last.received)
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    // ---------------------------------------------------------------------------
    // Wrapped fatal errors (Xposed / reflection / concurrent wrappers)
    // ---------------------------------------------------------------------------

    @Test
    fun processObserver_xposedInvocationTargetWrappedLinkage_isRethrown() {
        val fatal = TestLinkageError()
        val wrapped = XposedHelpers.InvocationTargetError(fatal)
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        try {
            ModuleHelper.handlePreferenceChanged("wrapped_xposed")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("wrapped_xposed"), first.received)
            assertTrue(last.received.isEmpty())
        }
    }

    @Test
    fun processObserver_javaInvocationTargetWrappedLinkage_isRethrown() {
        val fatal = TestLinkageError()
        val wrapped = InvocationTargetException(fatal)
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        try {
            ModuleHelper.handlePreferenceChanged("wrapped_java")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("wrapped_java"), first.received)
            assertTrue(last.received.isEmpty())
        }
    }

    @Test
    fun processObserver_executionExceptionWrappedLinkage_isRethrown() {
        val fatal = TestLinkageError()
        val wrapped = ExecutionException(fatal)
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        try {
            ModuleHelper.handlePreferenceChanged("wrapped_exec")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("wrapped_exec"), first.received)
            assertTrue(last.received.isEmpty())
        }
    }

    @Test
    fun processObserver_ordinaryRuntimeExceptionWrappedInInvocationTarget_isSwallowed() {
        val wrapped = InvocationTargetException(IllegalStateException("ordinary"))
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        ModuleHelper.observePreferenceChange(first)
        ModuleHelper.observePreferenceChange(throwing)
        ModuleHelper.observePreferenceChange(last)

        ModuleHelper.handlePreferenceChanged("wrapped_ordinary")

        assertEquals(listOf("wrapped_ordinary"), first.received)
        assertEquals(listOf("wrapped_ordinary"), last.received)
    }

    @Test
    fun ownerBoundObserver_xposedInvocationTargetWrappedLinkage_isRethrown() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val fatal = TestLinkageError()
        val wrapped = XposedHelpers.InvocationTargetError(fatal)
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_wrapped_xposed")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("owner_wrapped_xposed"), first.received)
            assertTrue(last.received.isEmpty())
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    @Test
    fun ownerBoundObserver_javaInvocationTargetWrappedLinkage_isRethrown() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val fatal = TestLinkageError()
        val wrapped = InvocationTargetException(fatal)
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_wrapped_java")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("owner_wrapped_java"), first.received)
            assertTrue(last.received.isEmpty())
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    @Test
    fun ownerBoundObserver_executionExceptionWrappedLinkage_isRethrown() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val fatal = TestLinkageError()
        val wrapped = ExecutionException(fatal)
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_wrapped_exec")
            org.junit.Assert.fail("expected LinkageError")
        } catch (le: LinkageError) {
            assertSame(fatal, le)
            assertEquals(listOf("owner_wrapped_exec"), first.received)
            assertTrue(last.received.isEmpty())
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }

    @Test
    fun ownerBoundObserver_ordinaryRuntimeExceptionWrappedInInvocationTarget_isSwallowed() {
        val owner1 = Any()
        val owner2 = Any()
        val owner3 = Any()

        val wrapped = InvocationTargetException(IllegalStateException("ordinary"))
        val first = RecordingObserver()
        val throwing = ThrowingObserver(wrapped)
        val last = RecordingObserver()

        try {
            ModuleHelper.observePreferenceChange(first, owner1)
            ModuleHelper.observePreferenceChange(throwing, owner2)
            ModuleHelper.observePreferenceChange(last, owner3)

            ModuleHelper.handlePreferenceChanged("owner_wrapped_ordinary")

            assertEquals(listOf("owner_wrapped_ordinary"), first.received)
            assertEquals(listOf("owner_wrapped_ordinary"), last.received)
        } finally {
            ModuleHelper.unregisterPreferenceObserver(owner1)
            ModuleHelper.unregisterPreferenceObserver(owner2)
            ModuleHelper.unregisterPreferenceObserver(owner3)
        }
    }
}
