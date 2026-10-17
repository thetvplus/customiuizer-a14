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
    fun obsoleteNotificationHooksAreRemovedButSharedSnapshotRemains() {
        val source = sourceFile(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt"
        ).readText()
        for (obsolete in listOf(
            "BetterPopupsNoHideHook",
            "BetterPopupsHideDelayHook",
            "MinimalNotificationViewHook",
            "system_betterpopups_delay",
            "betterPopupsDelayMs",
        )) {
            assertTrue("Retired notification path must not remain: $obsolete", !source.contains(obsolete))
        }
        assertTrue(source.contains("fun installNotificationSnapshot()"))
        assertTrue(source.contains("fun ExpandHeadsUpHook("))
        assertTrue(source.contains("expandHeadsUp = prefs.getStringAsInt"))
        assertTrue(source.contains("maxSbIcons = prefs.getStringAsInt"))
        assertTrue(source.contains("ModuleHelper.observePreferenceChange("))
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
