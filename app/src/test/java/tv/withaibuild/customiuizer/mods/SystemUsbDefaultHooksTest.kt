package tv.withaibuild.customiuizer.mods

import android.os.Message
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemUsbDefaultHooks.ContextReason
import tv.withaibuild.customiuizer.mods.SystemUsbDefaultHooks.ResolvedTargets
import tv.withaibuild.customiuizer.mods.SystemUsbDefaultHooks.SetEnabledCall
import tv.withaibuild.customiuizer.mods.SystemUsbDefaultHooks.UsbDefaultContext
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import java.lang.reflect.Field
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

class SystemUsbDefaultHooksTest {

    companion object {
        private const val FUNCTION_MIDI = 8L
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.remove(SystemUsbDefaultHooks.PREF_KEY)
        UsbDefaultContext.clear()
    }

    // --- PrefMap / getStringAsInt semantics ---

    @Test
    fun getStringAsInt_readsValidStringModes() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")
        assertEquals(2, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))

        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "3")
        assertEquals(3, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    @Test
    fun getStringAsInt_defaultsToFollowSystemWhenMissing() {
        assertEquals(0, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    @Test
    fun getStringAsInt_defaultsToFollowSystemForInvalidString() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "invalid")
        assertEquals(0, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    @Test
    fun getStringAsInt_acceptsNumberStorage() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, 1)
        assertEquals(1, MainModule.mPrefs.getStringAsInt(SystemUsbDefaultHooks.PREF_KEY, 0))
    }

    // --- Mode sanitization ---

    @Test
    fun getMode_returnsSanitizedValue() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")
        assertEquals(SystemUsbDefaultHooks.MODE_MTP, SystemUsbDefaultHooks.getMode())
    }

    @Test
    fun getMode_invalidValueFallsBackToFollowSystem() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "7")
        assertEquals(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, SystemUsbDefaultHooks.getMode())
    }

    @Test
    fun getMode_negativeValueFallsBackToFollowSystem() {
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "-1")
        assertEquals(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, SystemUsbDefaultHooks.getMode())
    }

    // --- JZI contract parser ---

    @Test
    fun parseSetEnabledCall_validMtpCall() {
        val call = SystemUsbDefaultHooks.parseSetEnabledCall(arrayOf(4L, false, 123))!!
        assertEquals(4L, call.functions)
        assertFalse(call.forceRestart)
        assertEquals(123, call.operationId)
    }

    @Test
    fun parseSetEnabledCall_validPtpCall() {
        val call = SystemUsbDefaultHooks.parseSetEnabledCall(arrayOf(16L, true, 456))!!
        assertEquals(16L, call.functions)
        assertTrue(call.forceRestart)
        assertEquals(456, call.operationId)
    }

    @Test
    fun parseSetEnabledCall_rejectsWrongArity() {
        assertNull(SystemUsbDefaultHooks.parseSetEnabledCall(arrayOf(4L, false)))
        assertNull(SystemUsbDefaultHooks.parseSetEnabledCall(arrayOf(4L, false, 123, "extra")))
    }

    @Test
    fun parseSetEnabledCall_rejectsWrongTypes() {
        assertNull(SystemUsbDefaultHooks.parseSetEnabledCall(arrayOf(4, false, 123)))
        assertNull(SystemUsbDefaultHooks.parseSetEnabledCall(arrayOf(4L, "false", 123)))
        assertNull(SystemUsbDefaultHooks.parseSetEnabledCall(arrayOf(4L, false, 123L)))
    }

    // --- Production rewrite boundary ---

    @Test
    fun applySetEnabledFunctionsOverride_rewritesMtp() {
        val args = arrayOf<Any?>(0L, false, 1)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_MTP,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = true,
        )
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, effective)
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, args[0])
        assertEquals(false, args[1])
        assertEquals(1, args[2])
    }

    @Test
    fun applySetEnabledFunctionsOverride_rewritesPtp() {
        val args = arrayOf<Any?>(0L, false, 2)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_PTP,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = true,
        )
        assertEquals(SystemUsbDefaultHooks.FUNCTION_PTP, effective)
        assertEquals(SystemUsbDefaultHooks.FUNCTION_PTP, args[0])
        assertEquals(false, args[1])
        assertEquals(2, args[2])
    }

    @Test
    fun applySetEnabledFunctionsOverride_chargingSetsNone() {
        val args = arrayOf<Any?>(16L, false, 3)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_CHARGING,
            nativeDefault = 16L,
            screenLocked = false,
            transferAllowed = true,
        )
        assertEquals(SystemUsbDefaultHooks.FUNCTION_NONE, effective)
        assertEquals(SystemUsbDefaultHooks.FUNCTION_NONE, args[0])
    }

    @Test
    fun applySetEnabledFunctionsOverride_followSystemPreservesArgs() {
        val args = arrayOf<Any?>(4L, false, 4)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = true,
        )
        assertNull(effective)
        assertEquals(4L, args[0])
    }

    @Test
    fun applySetEnabledFunctionsOverride_forceRestartNoRewrite() {
        val args = arrayOf<Any?>(0L, true, 5)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_MTP,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = true,
        )
        assertNull(effective)
        assertEquals(0L, args[0])
        assertEquals(true, args[1])
    }

    @Test
    fun applySetEnabledFunctionsOverride_screenLockedNoRewrite() {
        val args = arrayOf<Any?>(0L, false, 6)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_MTP,
            nativeDefault = 0L,
            screenLocked = true,
            transferAllowed = true,
        )
        assertNull(effective)
        assertEquals(0L, args[0])
    }

    @Test
    fun applySetEnabledFunctionsOverride_transferBlockedDowngradesToNone() {
        val args = arrayOf<Any?>(16L, false, 7)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_MTP,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = false,
        )
        assertEquals(SystemUsbDefaultHooks.FUNCTION_NONE, effective)
        assertEquals(SystemUsbDefaultHooks.FUNCTION_NONE, args[0])
    }

    @Test
    fun applySetEnabledFunctionsOverride_transferBlockedAlreadyNoneNoRewrite() {
        val args = arrayOf<Any?>(0L, false, 8)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_MTP,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = false,
        )
        assertNull(effective)
        assertEquals(0L, args[0])
    }

    @Test
    fun applySetEnabledFunctionsOverride_alreadyCorrectNoRewrite() {
        val args = arrayOf<Any?>(SystemUsbDefaultHooks.FUNCTION_MTP, false, 9)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_MTP,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = true,
        )
        assertNull(effective)
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, args[0])
    }

    @Test
    fun applySetEnabledFunctionsOverride_invalidArgsNoCrash() {
        val args = arrayOf<Any?>("not-a-long", false, 10)
        val effective = SystemUsbDefaultHooks.applySetEnabledFunctionsOverride(
            args = args,
            mode = SystemUsbDefaultHooks.MODE_MTP,
            nativeDefault = 0L,
            screenLocked = false,
            transferAllowed = true,
        )
        assertNull(effective)
    }

    // --- Pure mapping ---

    @Test
    fun resolveEffective_followSystemPreservesNativeDefault() {
        assertEquals(0L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, 0L))
        assertEquals(4L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, 4L))
        assertEquals(16L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM, 16L))
    }

    @Test
    fun resolveEffective_chargingMapsToNone() {
        assertEquals(0L, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_CHARGING, 16L))
    }

    @Test
    fun resolveEffective_mtpMapsToMtp() {
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_MTP, 0L))
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_MTP, 16L))
    }

    @Test
    fun resolveEffective_ptpMapsToPtp() {
        assertEquals(SystemUsbDefaultHooks.FUNCTION_PTP, SystemUsbDefaultHooks.resolveEffective(SystemUsbDefaultHooks.MODE_PTP, 4L))
    }

    @Test
    fun resolveEffective_invalidModeFallsBackToNativeDefault() {
        assertEquals(99L, SystemUsbDefaultHooks.resolveEffective(7, 99L))
    }

    // --- Policy / transfer guards ---

    @Test
    fun computeEffectiveUsbFunctions_followSystemReturnsNull() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_FOLLOW_SYSTEM,
                nativeDefault = 16L,
                currentFunctions = 16L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_honoursScreenLocked() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = true,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_honoursForceRestart() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = false,
                forceRestart = true,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_allowsMtpWhenTransferAllowed() {
        assertEquals(
            SystemUsbDefaultHooks.FUNCTION_MTP,
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_blocksMtpWhenTransferDisallowed() {
        assertEquals(
            0L,
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 16L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = false,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_noRewriteWhenAlreadyCorrect() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = SystemUsbDefaultHooks.FUNCTION_MTP,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_noRewriteWhenTransferBlockedAndAlreadyNone() {
        assertNull(
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_MTP,
                nativeDefault = 0L,
                currentFunctions = 0L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = false,
            )
        )
    }

    @Test
    fun computeEffectiveUsbFunctions_chargingOverridesPtp() {
        assertEquals(
            0L,
            SystemUsbDefaultHooks.computeEffectiveUsbFunctions(
                mode = SystemUsbDefaultHooks.MODE_CHARGING,
                nativeDefault = 16L,
                currentFunctions = 16L,
                screenLocked = false,
                forceRestart = false,
                transferAllowed = true,
            )
        )
    }

    // --- SetEnabledFunctionsHook end-to-end ---

    @Test
    fun setEnabledFunctionsHook_rewritesMtpInDefaultContext() {
        val handler = TestHandler()
        val targets = createTestTargets(handler)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 0)

        val initialArgs = arrayOf<Any?>(0L, false, 1)
        val callback = createBeforeCallback(handler, initialArgs)

        val hook = SystemUsbDefaultHooks.SetEnabledFunctionsHook(targets)
        hook.before(callback)

        val args = callback.getArgs()
        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, args[0])
        assertEquals(false, args[1])
        assertEquals(1, args[2])

        UsbDefaultContext.pop()
    }

    @Test
    fun setEnabledFunctionsHook_noRewriteWithoutContext() {
        val handler = TestHandler()
        val targets = createTestTargets(handler)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val initialArgs = arrayOf<Any?>(0L, false, 1)
        val callback = createBeforeCallback(handler, initialArgs)

        val hook = SystemUsbDefaultHooks.SetEnabledFunctionsHook(targets)
        hook.before(callback)

        assertEquals(0L, callback.getArgs()[0])
    }

    @Test
    fun setEnabledFunctionsHook_noRewriteWhenTransferBlocked() {
        val handler = TestHandler(transferAllowed = false)
        val targets = createTestTargets(handler)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 0)

        val initialArgs = arrayOf<Any?>(16L, false, 1)
        val callback = createBeforeCallback(handler, initialArgs)

        val hook = SystemUsbDefaultHooks.SetEnabledFunctionsHook(targets)
        hook.before(callback)

        assertEquals(SystemUsbDefaultHooks.FUNCTION_NONE, callback.getArgs()[0])
        UsbDefaultContext.pop()
    }

    // --- HandleMessageHook end-to-end ---

    @Test
    fun handleMessageHook_pushesForDefaultMessages() {
        val targets = createTestTargets(TestHandler(), msgUpdateScreenLock = 13)
        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)

        val msg = Message().apply { what = 13; arg1 = 0 }
        val beforeCallback = createBeforeCallback(TestHandler(), arrayOf(msg))
        hook.before(beforeCallback)

        assertNotNull(UsbDefaultContext.peek())
        assertEquals(ContextReason.HANDLE_MESSAGE, UsbDefaultContext.peek()!!.reason)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_doesNotPushForBootCompleted() {
        val targets = createTestTargets(TestHandler(), msgBootCompleted = 4)
        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)

        val msg = Message().apply { what = 4 }
        val callback = createBeforeCallback(TestHandler(), arrayOf(msg))
        hook.before(callback)

        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_doesNotPushForSystemReady() {
        val targets = createTestTargets(TestHandler(), msgSystemReady = 3)
        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)

        val msg = Message().apply { what = 3 }
        val callback = createBeforeCallback(TestHandler(), arrayOf(msg))
        hook.before(callback)

        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_cleansUpEvenWithNullThisObject() {
        val targets = createTestTargets(TestHandler(), msgUpdateScreenLock = 13)
        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)

        val msg = Message().apply { what = 13; arg1 = 0 }
        val beforeCallback = createBeforeCallback(null, arrayOf(msg))
        hook.before(beforeCallback)

        assertNotNull(UsbDefaultContext.peek())

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_nativeNoneSupplementCallsSetEnabledFunctions() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateScreenLock = 13)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 13; arg1 = 0 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, handler.lastSetFunctions)
        assertFalse(handler.lastSetForceRestart)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_nativeNoneSupplementBlockedWhenTransferDisallowed() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = false,
        )
        val targets = createTestTargets(handler, msgUpdateScreenLock = 13)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 13; arg1 = 0 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(-1L, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_noSupplementWhenOriginalMessageHandlerFailed() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateScreenLock = 13)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 13; arg1 = 0 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val originalThrowable = RuntimeException("rom failure")
        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, originalThrowable)
        hook.after(afterCallback)

        assertEquals(-1L, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_connectAppliesMtpWhenNativeDefaultIsCharging() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateState = 0)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 0; arg1 = 1 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(SystemUsbDefaultHooks.FUNCTION_MTP, handler.lastSetFunctions)
        assertFalse(handler.lastSetForceRestart)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_connectAppliesPtpWhenNativeDefaultIsCharging() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateState = 0)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "3")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 0; arg1 = 1 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(SystemUsbDefaultHooks.FUNCTION_PTP, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_connectAppliesChargingWhenNativeDefaultIsMtp() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = SystemUsbDefaultHooks.FUNCTION_MTP,
            currentFunctions = SystemUsbDefaultHooks.FUNCTION_MTP,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateState = 0)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "1")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 0; arg1 = 1 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(SystemUsbDefaultHooks.FUNCTION_NONE, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_connectDoesNotStealManualMidiSession() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = FUNCTION_MIDI,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateState = 0)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 0; arg1 = 1 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(-1L, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_disconnectDoesNotSupplementConnectOverride() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateState = 0)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 0; arg1 = 0 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(-1L, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_connectFollowSystemDoesNotOverride() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = true,
        )
        val targets = createTestTargets(handler, msgUpdateState = 0)

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 0; arg1 = 1 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(-1L, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun handleMessageHook_connectBlockedWhenTransferDisallowed() {
        val handler = TestHandler(
            screenLocked = false,
            screenUnlockedFunctions = 0L,
            currentFunctions = 0L,
            bootCompleted = true,
            transferAllowed = false,
        )
        val targets = createTestTargets(handler, msgUpdateState = 0)
        MainModule.mPrefs.put(SystemUsbDefaultHooks.PREF_KEY, "2")

        val hook = SystemUsbDefaultHooks.HandleMessageHook(targets)
        val msg = Message().apply { what = 0; arg1 = 1 }
        val beforeCallback = createBeforeCallback(handler, arrayOf(msg))
        hook.before(beforeCallback)

        val afterCallback = HookerClassHelper.AfterHookCallback(beforeCallback, null, null)
        hook.after(afterCallback)

        assertEquals(-1L, handler.lastSetFunctions)
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun isOverridableCurrentFunction_allowsNativeDefaultAndCharging() {
        assertTrue(SystemUsbDefaultHooks.isOverridableCurrentFunction(0L, 0L))
        assertTrue(
            SystemUsbDefaultHooks.isOverridableCurrentFunction(
                SystemUsbDefaultHooks.FUNCTION_MTP,
                SystemUsbDefaultHooks.FUNCTION_MTP,
            ),
        )
        assertTrue(SystemUsbDefaultHooks.isOverridableCurrentFunction(0L, SystemUsbDefaultHooks.FUNCTION_MTP))
        assertFalse(
            SystemUsbDefaultHooks.isOverridableCurrentFunction(
                FUNCTION_MIDI,
                0L,
            ),
        )
        assertFalse(
            SystemUsbDefaultHooks.isOverridableCurrentFunction(
                SystemUsbDefaultHooks.FUNCTION_MTP,
                0L,
            ),
        )
    }

    @Test
    fun shouldSupplementDefaultApplication_recognizesUnlockAndConnect() {
        val targets = createTestTargets(TestHandler(), msgUpdateState = 0, msgUpdateScreenLock = 13)
        assertTrue(
            SystemUsbDefaultHooks.shouldSupplementDefaultApplication(
                targets,
                Message().apply { what = 13; arg1 = 0 },
            ),
        )
        assertFalse(
            SystemUsbDefaultHooks.shouldSupplementDefaultApplication(
                targets,
                Message().apply { what = 13; arg1 = 1 },
            ),
        )
        assertTrue(
            SystemUsbDefaultHooks.shouldSupplementDefaultApplication(
                targets,
                Message().apply { what = 0; arg1 = 1 },
            ),
        )
        assertFalse(
            SystemUsbDefaultHooks.shouldSupplementDefaultApplication(
                targets,
                Message().apply { what = 0; arg1 = 0 },
            ),
        )
        assertFalse(
            SystemUsbDefaultHooks.shouldSupplementDefaultApplication(
                targets,
                Message().apply { what = 12; arg1 = 1 },
            ),
        )
    }

    // --- Thread-local ownership guard ---

    @Test
    fun usbDefaultContext_pushAndPeek() {
        assertNull(UsbDefaultContext.peek())
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 13)
        val frame = UsbDefaultContext.peek()
        assertNotNull(frame)
        assertEquals(ContextReason.HANDLE_MESSAGE, frame!!.reason)
        assertEquals(13, frame.messageWhat)
        UsbDefaultContext.pop()
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun usbDefaultContext_isThreadLocal() {
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 0)
        var otherPeek: SystemUsbDefaultHooks.ContextFrame? = null
        val thread = Thread {
            otherPeek = UsbDefaultContext.peek()
        }
        thread.start()
        thread.join()
        assertNull(otherPeek)
        assertNotNull(UsbDefaultContext.peek())
        UsbDefaultContext.pop()
    }

    @Test
    fun usbDefaultContext_nestedPushPop() {
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 0)
        UsbDefaultContext.push(ContextReason.SET_SCREEN_UNLOCKED_FUNCTIONS)
        val top = UsbDefaultContext.peek()
        assertEquals(ContextReason.SET_SCREEN_UNLOCKED_FUNCTIONS, top!!.reason)
        UsbDefaultContext.pop()
        val outer = UsbDefaultContext.peek()
        assertEquals(ContextReason.HANDLE_MESSAGE, outer!!.reason)
        UsbDefaultContext.pop()
        assertNull(UsbDefaultContext.peek())
    }

    @Test
    fun usbDefaultContext_lastPopRemovesStorage() {
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 0)
        UsbDefaultContext.pop()
        assertNull(UsbDefaultContext.peek())
        // A second push/pop on the same thread should still behave correctly.
        UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, 1)
        assertNotNull(UsbDefaultContext.peek())
        UsbDefaultContext.pop()
        assertNull(UsbDefaultContext.peek())
    }

    // --- Constants ---

    @Test
    fun usbFunctionConstantsAreAospValues() {
        assertEquals(0L, SystemUsbDefaultHooks.FUNCTION_NONE)
        assertEquals(4L, SystemUsbDefaultHooks.FUNCTION_MTP)
        assertEquals(16L, SystemUsbDefaultHooks.FUNCTION_PTP)
    }

    // --- Test helpers ---

    class TestHandler(
        @JvmField var screenLocked: Boolean = false,
        @JvmField var screenUnlockedFunctions: Long = 0L,
        @JvmField var currentFunctions: Long = 0L,
        @JvmField var bootCompleted: Boolean = false,
        @JvmField var transferAllowed: Boolean = true,
    ) {
        @JvmField
        var lastSetFunctions: Long = -1L

        @JvmField
        var lastSetForceRestart: Boolean = false

        @JvmField
        var lastSetOperationId: Int = -1

        @Suppress("unused")
        fun realSetEnabledFunctions(functions: Long, forceRestart: Boolean, operationId: Int) {
            lastSetFunctions = functions
            lastSetForceRestart = forceRestart
            lastSetOperationId = operationId
        }

        @Suppress("unused")
        fun realIsUsbTransferAllowed(): Boolean = transferAllowed
    }

    private fun createTestTargets(
        handler: TestHandler,
        msgUpdateState: Int = 0,
        msgSetScreenUnlockedFunctions: Int = 0xc,
        msgUpdateScreenLock: Int = 0xd,
        msgBootCompleted: Int = 0x4,
        msgSystemReady: Int = 0x3,
    ): ResolvedTargets {
        val handlerClass = TestHandler::class.java
        val fieldScreenUnlockedFunctions = handlerClass.getDeclaredField("screenUnlockedFunctions")
        val fieldScreenLocked = handlerClass.getDeclaredField("screenLocked")
        val fieldCurrentFunctions = handlerClass.getDeclaredField("currentFunctions")
        val fieldBootCompleted = handlerClass.getDeclaredField("bootCompleted")
        val methodSetEnabledFunctions = handlerClass.getDeclaredMethod(
            "realSetEnabledFunctions",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        val methodIsUsbTransferAllowed = handlerClass.getDeclaredMethod("realIsUsbTransferAllowed")

        return ResolvedTargets(
            handlerClass = handlerClass,
            handlerHalClass = handlerClass,
            handlerLegacyClass = null,
            msgUpdateState = msgUpdateState,
            msgSetScreenUnlockedFunctions = msgSetScreenUnlockedFunctions,
            msgUpdateScreenLock = msgUpdateScreenLock,
            msgBootCompleted = msgBootCompleted,
            msgSystemReady = msgSystemReady,
            fieldScreenUnlockedFunctions = fieldScreenUnlockedFunctions,
            fieldScreenLocked = fieldScreenLocked,
            fieldCurrentFunctions = fieldCurrentFunctions,
            fieldBootCompleted = fieldBootCompleted,
            methodSetScreenUnlockedFunctions = TestHandler::class.java.getMethod("toString"),
            methodFinishBoot = TestHandler::class.java.getMethod("toString"),
            methodHandleMessage = TestHandler::class.java.getMethod("toString"),
            methodSetEnabledFunctionsHal = methodSetEnabledFunctions,
            methodSetEnabledFunctionsLegacy = null,
            methodIsUsbTransferAllowed = methodIsUsbTransferAllowed,
            operationCounter = AtomicInteger(0),
        )
    }

    private fun createBeforeCallback(thisObject: Any?, args: Array<Any?>): HookerClassHelper.BeforeHookCallback {
        val argList = args.toList()
        val chain = Proxy.newProxyInstance(
            XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java),
            InvocationHandler { _, method, methodArgs ->
                when (method.name) {
                    "getThisObject" -> thisObject
                    "getArgs" -> argList
                    "getArg" -> argList[methodArgs[0] as Int]
                    else -> null
                }
            },
        ) as XposedInterface.Chain
        return HookerClassHelper.BeforeHookCallback(chain)
    }
}
