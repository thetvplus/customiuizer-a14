package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException

class SystemLockScreenHooksTest {

    @Test
    fun resolveWallpaperUserId_returnsResolvedValueOnSuccess() {
        val resolved = SystemLockScreenHooks.resolveWallpaperUserId { 10 }
        assertEquals(10, resolved)
    }

    @Test
    fun resolveWallpaperUserId_returnsNullOnNonFatalFailure() {
        val resolved = SystemLockScreenHooks.resolveWallpaperUserId { throw RuntimeException("no such method") }
        assertNull(resolved)
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveWallpaperUserId_rethrowsDirectFatalError() {
        SystemLockScreenHooks.resolveWallpaperUserId { throw OutOfMemoryError("oom") }
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveWallpaperUserId_rethrowsWrappedFatalError() {
        SystemLockScreenHooks.resolveWallpaperUserId {
            throw InvocationTargetException(OutOfMemoryError("wrapped oom"))
        }
    }

    @Test
    fun noScreenLockHook_preflightsSixCoreMethodsBeforeAnyInstall() {
        val source = sourceFile(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt"
        ).readText()
        val fnStart = source.indexOf("fun NoScreenLockHook(")
        assertTrue("NoScreenLockHook must exist", fnStart >= 0)

        val fnBody = source.substring(fnStart)

        val h1 = fnBody.indexOf("findMethodExact(\n            \"com.android.systemui.keyguard.KeyguardViewMediator\",\n            lpparam.classLoader,\n            \"handleKeyguardDone\"")
        val h2 = fnBody.indexOf("findMethodExact(\n            \"com.android.keyguard.KeyguardUpdateMonitor\",\n            lpparam.classLoader,\n            \"onFingerprintAuthenticated\",\n            Int::class.javaPrimitiveType!!,\n            Boolean::class.javaPrimitiveType!!")
        val h3 = fnBody.indexOf("findMethodExact(\n            \"com.android.keyguard.KeyguardSecurityContainerController\",\n            lpparam.classLoader,\n            \"onInit\"")
        val h4 = fnBody.indexOf("findMethodExact(\n            \"com.android.systemui.keyguard.KeyguardViewMediator\",\n            lpparam.classLoader,\n            \"doKeyguardLocked\",\n            Bundle::class.java")
        val h5 = fnBody.indexOf("findMethodExact(\n            \"com.android.systemui.keyguard.KeyguardViewMediator\",\n            lpparam.classLoader,\n            \"setupLocked\"")
        val h6 = fnBody.indexOf("findMethodExact(\n            \"com.android.keyguard.KeyguardSecurityModel\",\n            lpparam.classLoader,\n            \"getSecurityMode\",\n            Int::class.javaPrimitiveType!!")

        assertTrue("Preflight H1 handleKeyguardDone must exist", h1 >= 0)
        assertTrue("Preflight H2 onFingerprintAuthenticated must exist", h2 >= 0)
        assertTrue("Preflight H3 onInit must exist", h3 >= 0)
        assertTrue("Preflight H4 doKeyguardLocked must exist", h4 >= 0)
        assertTrue("Preflight H5 setupLocked must exist", h5 >= 0)
        assertTrue("Preflight H6 getSecurityMode must exist", h6 >= 0)

        val firstHookInstall = fnBody.indexOf("ModuleHelper.findAndHookMethod(\"com.android.systemui.keyguard.KeyguardViewMediator\", lpparam.classLoader, \"handleKeyguardDone\"")
        assertTrue("First hook installation must exist", firstHookInstall >= 0)

        val preflightMax = maxOf(h1, h2, h3, h4, h5, h6)
        assertTrue(
            "All six core preflight method resolutions must happen before the first hook installation",
            preflightMax < firstHookInstall
        )

        // Preflight must not include auxiliary/optional symbols.
        assertFalse(
            "Preflight must NOT include BluetoothControllerImpl constructor hook",
            fnBody.substring(0, firstHookInstall).contains("BluetoothControllerImpl")
        )
        assertFalse(
            "Preflight must NOT include updateConnected hook",
            fnBody.substring(0, firstHookInstall).contains("updateConnected")
        )
        assertFalse(
            "Preflight must NOT include SecurityMode enum",
            fnBody.substring(0, firstHookInstall).contains("KeyguardSecurityModel\\\$SecurityMode")
        )
    }

    @Test
    fun doKeyguardLockedHook_usesPhasedFailClosedRuntimeFallback() {
        val source = sourceFile(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt"
        ).readText()

        val h4Start = source.indexOf(
            "ModuleHelper.findAndHookMethod(\"com.android.systemui.keyguard.KeyguardViewMediator\", lpparam.classLoader, \"doKeyguardLocked\", Bundle::class.java"
        )
        assertTrue("H4 doKeyguardLocked hook must exist", h4Start >= 0)

        val h5Start = source.indexOf(
            "ModuleHelper.findAndHookMethod(\"com.android.systemui.keyguard.KeyguardViewMediator\", lpparam.classLoader, \"setupLocked\"",
            h4Start
        )
        assertTrue("H5 setupLocked hook must exist after H4", h5Start >= 0)

        val h4Body = source.substring(h4Start, h5Start)

        // Old generated state machine removed.
        assertFalse("H4 must not contain old 'var skipped'", h4Body.contains("var skipped"))
        assertFalse("H4 must not contain old 'var result'", h4Body.contains("var result"))
        assertFalse("H4 must not contain old 'var throwable'", h4Body.contains("var throwable"))
        assertFalse("H4 must not use throwOrReturn", h4Body.contains("throwOrReturn"))
        assertFalse("H4 must not use proceedOrThrow", h4Body.contains("proceedOrThrow"))

        // Phase 1: forcedOption guard proceeds directly.
        assertTrue(
            "H4 must keep forcedOption == 0 → chain.proceed()",
            h4Body.contains("if (forcedOption == 0) { return chain.proceed() }")
        )

        // Phase 1: mContext failure → log → chain.proceed().
        val mContextCatch = h4Body.indexOf("} catch (t: Throwable) {\n                    FatalErrors.unwrapAndRethrowIfFatal(t)\n                    XposedHelpers.log(t)\n                    return chain.proceed()\n                }")
        assertTrue("mContext lookup failure must return chain.proceed()", mContextCatch >= 0)

        // Phase 1: isUnlocked failure → log → chain.proceed().
        val isUnlockedTry = h4Body.indexOf("val unlocked = try {\n                    isUnlocked(mContext, lpparam.classLoader)")
        val isUnlockedCatch = h4Body.indexOf("} catch (t: Throwable) {\n                    FatalErrors.unwrapAndRethrowIfFatal(t)\n                    XposedHelpers.log(t)\n                    return chain.proceed()\n                }", isUnlockedTry)
        assertTrue("isUnlocked failure must return chain.proceed()", isUnlockedTry >= 0 && isUnlockedCatch > isUnlockedTry)

        // Phase 1: skip preference failure → log → chain.proceed().
        val skipTry = h4Body.indexOf("val skip = try {\n                    MainModule.mPrefs.getBoolean(\"system_noscreenlock_skip\")")
        val skipCatch = h4Body.indexOf("} catch (t: Throwable) {\n                    FatalErrors.unwrapAndRethrowIfFatal(t)\n                    XposedHelpers.log(t)\n                    return chain.proceed()\n                }", skipTry)
        assertTrue("skip preference failure must return chain.proceed()", skipTry >= 0 && skipCatch > skipTry)

        // Phase 2: keyguardDone failure handling.
        val keyguardDoneTry = h4Body.indexOf("try {\n                        XposedHelpers.callMethod(thisObject, \"keyguardDone\")")
        assertTrue("keyguardDone call must be in try", keyguardDoneTry >= 0)
        val keyguardDoneCatch = h4Body.indexOf(
            "} catch (t: Throwable) {\n                        FatalErrors.unwrapAndRethrowIfFatal(t)\n                        if (t is XposedHelpers.InvocationTargetError) {\n                            throw t\n                        }\n                        XposedHelpers.log(t)\n                        return chain.proceed()\n                    }",
            keyguardDoneTry
        )
        assertTrue("keyguardDone catch must handle InvocationTargetError and fallback", keyguardDoneTry >= 0 && keyguardDoneCatch > keyguardDoneTry)

        // Phase 3: broadcast failure only logs and continues.
        val broadcastTry = h4Body.indexOf("val unlockIntent = Intent(GlobalActions.ACTION_PREFIX + \"UnlockStrongAuth\")")
        assertTrue("UnlockStrongAuth broadcast must exist", broadcastTry >= 0)
        val broadcastCatch = h4Body.indexOf("} catch (t: Throwable) {\n                    FatalErrors.unwrapAndRethrowIfFatal(t)\n                    XposedHelpers.log(t)\n                }", broadcastTry)
        assertTrue("broadcast failure must log and continue", broadcastTry >= 0 && broadcastCatch > broadcastTry)

        // Final skip path returns null, non-skip returns chain.proceed().
        assertTrue("H4 must return null when skip", h4Body.contains("if (skip) { return null }"))
        assertTrue("H4 must return chain.proceed() as final", h4Body.contains("return chain.proceed()"))

        // isUnlockedInnerCall flag set before broadcast and after keyguardDone, not reset on broadcast failure.
        val flagSet = h4Body.indexOf("isUnlockedInnerCall = true")
        val broadcastStart = h4Body.indexOf("mContext.sendBroadcast(unlockIntent)")
        assertTrue("isUnlockedInnerCall must be set before broadcast", flagSet >= 0 && flagSet < broadcastStart)
    }

    private fun sourceFile(relativePath: String): File {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
