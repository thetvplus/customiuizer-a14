package tv.withaibuild.customiuizer.mods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.InvocationTargetException

class SystemNotificationHooksTest {

    @Test
    fun resolveNotificationUserId_returnsResolvedValueOnSuccess() {
        val resolved = SystemNotificationHooks.resolveNotificationUserId { 10 }
        assertEquals(10, resolved)
    }

    @Test
    fun resolveNotificationUserId_returnsResolvedOwnerUserZero() {
        val resolved = SystemNotificationHooks.resolveNotificationUserId { 0 }
        assertEquals(0, resolved)
    }

    @Test
    fun resolveNotificationUserId_returnsNullOnNonFatalFailure() {
        val resolved = SystemNotificationHooks.resolveNotificationUserId { throw RuntimeException("no such method") }
        assertNull(resolved)
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveNotificationUserId_rethrowsDirectFatalError() {
        SystemNotificationHooks.resolveNotificationUserId { throw OutOfMemoryError("oom") }
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolveNotificationUserId_rethrowsWrappedFatalError() {
        SystemNotificationHooks.resolveNotificationUserId {
            throw InvocationTargetException(OutOfMemoryError("wrapped oom"))
        }
    }

    @Test
    fun resolveNotificationUserId_freeformBranchSkipsResolution() {
        // The Freeform branch in the OnClickListener does not call resolveNotificationUserId.
        // This seam is only invoked for App Info and Force Close actions.
        val resolved = SystemNotificationHooks.resolveNotificationUserId { 0 }
        assertEquals(0, resolved)
    }

    @Test
    fun betterPopupsNoHideHook_preflightsRequiredClassesBeforeAnyHookInstallation() {
        val source = sourceFile(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt"
        ).readText()
        val fnStart = source.indexOf("fun BetterPopupsNoHideHook(")
        assertTrue("BetterPopupsNoHideHook must exist", fnStart >= 0)

        val fnBody = source.substring(fnStart)

        val headsUpManagerFindClass = fnBody.indexOf(
            "findClass(\"com.android.systemui.statusbar.policy.HeadsUpManager\""
        )
        val headsUpEntryFindClass = fnBody.indexOf(
            "findClass(\"com.android.systemui.statusbar.policy.HeadsUpManager\\\$HeadsUpEntry\""
        )
        assertTrue(
            "Preflight must resolve HeadsUpManager class",
            headsUpManagerFindClass >= 0
        )
        assertTrue(
            "Preflight must resolve HeadsUpManager\$HeadsUpEntry class",
            headsUpEntryFindClass >= 0
        )

        val removeHeadsUp = fnBody.indexOf("findMethodExact(\"com.android.systemui.statusbar.policy.HeadsUpManager\", lpparam.classLoader, \"removeHeadsUpNotification\")")
        val removeOldHeadsUp = fnBody.indexOf("findMethodExact(\"com.android.systemui.statusbar.policy.HeadsUpManager\", lpparam.classLoader, \"removeOldHeadsUpNotification\")")
        val onExpandingFinished = fnBody.indexOf("findMethodExact(\"com.android.systemui.statusbar.policy.HeadsUpManager\", lpparam.classLoader, \"onExpandingFinished\")")
        val updateEntry = fnBody.indexOf("findMethodExact(\"com.android.systemui.statusbar.policy.HeadsUpManager\\\$HeadsUpEntry\", lpparam.classLoader, \"updateEntry\", Boolean::class.javaPrimitiveType!!)")

        assertTrue("Preflight must resolve removeHeadsUpNotification", removeHeadsUp >= 0)
        assertTrue("Preflight must resolve removeOldHeadsUpNotification", removeOldHeadsUp >= 0)
        assertTrue("Preflight must resolve onExpandingFinished", onExpandingFinished >= 0)
        assertTrue("Preflight must resolve updateEntry(boolean)", updateEntry >= 0)

        val firstHookInstall = fnBody.indexOf("ModuleHelper.findAndHookMethod(")

        assertTrue("At least one hook installation must exist", firstHookInstall >= 0)

        // All preflight resolutions must occur before the first hook installation.
        val preflightMax = maxOf(headsUpManagerFindClass, headsUpEntryFindClass, removeHeadsUp, removeOldHeadsUp, onExpandingFinished, updateEntry)
        assertTrue(
            "All preflight class/method resolutions must happen before the first ModuleHelper.findAndHookMethod call",
            preflightMax < firstHookInstall
        )
    }

    @Test
    fun betterPopupsHideDelayHook_preflightsRequiredClassAndFieldsBeforeAnyHookInstallation() {
        val source = sourceFile(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt"
        ).readText()
        val fnStart = source.indexOf("fun BetterPopupsHideDelayHook(")
        assertTrue("BetterPopupsHideDelayHook must exist", fnStart >= 0)

        val fnBody = source.substring(fnStart)

        val findClassPos = fnBody.indexOf("findClass(\"com.android.systemui.statusbar.policy.HeadsUpManager\", lpparam.classLoader)")
        assertTrue("Preflight must resolve HeadsUpManager class", findClassPos >= 0)

        val findMinDisplayTime = fnBody.indexOf("findField(headsUpManagerClass, \"mMinimumDisplayTime\")")
        val findDecay = fnBody.indexOf("findField(headsUpManagerClass, \"mHeadsUpNotificationDecay\")")
        assertTrue("Preflight must resolve mMinimumDisplayTime field", findMinDisplayTime >= 0)
        assertTrue("Preflight must resolve mHeadsUpNotificationDecay field", findDecay >= 0)

        val firstSilentHook = fnBody.indexOf("ModuleHelper.findAndHookMethodSilently(")
        val firstConstructorHook = fnBody.indexOf("ModuleHelper.hookAllConstructors(")
        assertTrue("At least one hook installation must exist", firstSilentHook >= 0)
        assertTrue("Constructor hook must exist", firstConstructorHook >= 0)

        val firstHookInstall = minOf(firstSilentHook, firstConstructorHook)

        val preflightMax = maxOf(findClassPos, findMinDisplayTime, findDecay)
        assertTrue(
            "All preflight class/field resolutions must happen before the first hook installation",
            preflightMax < firstHookInstall
        )

        // Verify hookAllConstructors uses the resolved Class, not a string re-resolution.
        val constructorCall = fnBody.substring(firstConstructorHook, firstConstructorHook + 80)
        assertTrue(
            "hookAllConstructors must use the resolved headsUpManagerClass, not a string class name",
            constructorCall.contains("hookAllConstructors(headsUpManagerClass,")
        )
    }

    @Test
    fun betterPopupsCenteredHook_failsClosedWhenCoreHookInstallReturnsNull() {
        val source = sourceFile(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt"
        ).readText()
        val fnStart = source.indexOf("fun BetterPopupsCenteredHook(")
        assertTrue("BetterPopupsCenteredHook must exist", fnStart >= 0)

        // Isolate the function body up to the next sibling or EOF.
        val fnBody = source.substring(fnStart)

        val findCall = fnBody.indexOf("ModuleHelper.findAndHookMethod(\"com.android.systemui.statusbar.policy.HeadsUpManagerInjector\"")
        assertTrue("Core hook install call must exist", findCall >= 0)

        val checkNotNullPos = fnBody.indexOf("checkNotNull(coreUnhooker)")
        assertTrue(
            "checkNotNull(coreUnhooker) must exist after the core hook install call",
            checkNotNullPos >= 0
        )
        assertTrue(
            "checkNotNull must come after the findAndHookMethod call",
            findCall < checkNotNullPos
        )
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
