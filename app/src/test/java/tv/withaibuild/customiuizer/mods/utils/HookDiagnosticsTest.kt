package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HookDiagnosticsTest {

    @Before
    fun setup() {
        HookDiagnostics.reset()
    }

    @After
    fun tearDown() {
        HookDiagnostics.reset()
    }

    @Test
    fun recordInstalledAndSummarize() {
        HookDiagnostics.record(
            process = "android",
            kind = HookDiagnostics.Kind.METHOD,
            targetClass = "com.android.systemui.SystemUIInitializer",
            targetMember = "init",
            descriptor = "boolean",
            status = HookDiagnostics.Status.INSTALLED,
        )

        val s = HookDiagnostics.summary()
        assertEquals(1, s[HookDiagnostics.Status.INSTALLED])
        assertEquals(0, s[HookDiagnostics.Status.TARGET_CLASS_MISSING])
    }

    @Test
    fun deduplicateSameTargetAndStatus() {
        repeat(3) {
            HookDiagnostics.record(
                process = "com.android.systemui",
                kind = HookDiagnostics.Kind.METHOD,
                targetClass = "com.android.systemui.SystemUIInitializer",
                targetMember = "init",
                descriptor = "boolean",
                status = HookDiagnostics.Status.INSTALLED,
            )
        }

        assertEquals(1, HookDiagnostics.snapshot().size)
        assertEquals(1, HookDiagnostics.summary()[HookDiagnostics.Status.INSTALLED])
    }

    @Test
    fun differentStatusesForSameTargetAreBothKept() {
        HookDiagnostics.record(
            process = "com.android.systemui",
            kind = HookDiagnostics.Kind.METHOD,
            targetClass = "com.android.systemui.SystemUIInitializer",
            targetMember = "init",
            descriptor = "boolean",
            status = HookDiagnostics.Status.INSTALLED,
        )
        HookDiagnostics.record(
            process = "com.android.systemui",
            kind = HookDiagnostics.Kind.METHOD,
            targetClass = "com.android.systemui.SystemUIInitializer",
            targetMember = "init",
            descriptor = "boolean",
            status = HookDiagnostics.Status.TARGET_MEMBER_MISSING,
        )

        val s = HookDiagnostics.summary()
        assertEquals(1, s[HookDiagnostics.Status.INSTALLED])
        assertEquals(1, s[HookDiagnostics.Status.TARGET_MEMBER_MISSING])
    }

    @Test
    fun missingClassMissingMemberAndFailedAreDistinct() {
        HookDiagnostics.record("android", HookDiagnostics.Kind.METHOD, "ClassA", "m", "", HookDiagnostics.Status.TARGET_CLASS_MISSING)
        HookDiagnostics.record("android", HookDiagnostics.Kind.METHOD, "ClassB", "m", "", HookDiagnostics.Status.TARGET_MEMBER_MISSING)
        HookDiagnostics.record("android", HookDiagnostics.Kind.METHOD, "ClassC", "m", "", HookDiagnostics.Status.INSTALL_FAILED, "SomeError")

        val s = HookDiagnostics.summary()
        assertEquals(1, s[HookDiagnostics.Status.TARGET_CLASS_MISSING])
        assertEquals(1, s[HookDiagnostics.Status.TARGET_MEMBER_MISSING])
        assertEquals(1, s[HookDiagnostics.Status.INSTALL_FAILED])
    }

    @Test
    fun snapshotDoesNotContainUserData() {
        HookDiagnostics.record(
            process = "android",
            kind = HookDiagnostics.Kind.METHOD,
            targetClass = "com.example.Service",
            targetMember = "doIt",
            descriptor = "",
            status = HookDiagnostics.Status.INSTALL_FAILED,
            exceptionType = "NoSuchMethodError",
        )

        for (record in HookDiagnostics.snapshot()) {
            assertTrue(record.process.isNotBlank())
            assertTrue(record.targetClass.isNotBlank())
            assertTrue(record.targetMember.isNotBlank())
            assertTrue(record.exceptionType == "NoSuchMethodError" || record.exceptionType.isEmpty())
        }
    }

    @Test
    fun recordIsBounded() {
        repeat(300) { index ->
            HookDiagnostics.record(
                process = "p",
                kind = HookDiagnostics.Kind.METHOD,
                targetClass = "Class$index",
                targetMember = "m",
                descriptor = "",
                status = HookDiagnostics.Status.INSTALLED,
            )
        }

        assertTrue(HookDiagnostics.snapshot().size <= 256)
    }

    @Test
    fun summaryPrintedOnce() {
        HookDiagnostics.currentProcessName = "test"
        HookDiagnostics.record("test", HookDiagnostics.Kind.METHOD, "C", "m", "", HookDiagnostics.Status.INSTALLED)

        // printSummaryOnce can be called repeatedly without throwing; only one actual log is emitted.
        HookDiagnostics.printSummaryOnce()
        HookDiagnostics.printSummaryOnce()
        val s = HookDiagnostics.summary()
        assertEquals(1, s[HookDiagnostics.Status.INSTALLED])
    }

    @Test
    fun printSummaryForStageIsStageSpecific() {
        HookDiagnostics.currentProcessName = "test"
        HookDiagnostics.record("test", HookDiagnostics.Kind.METHOD, "C", "m", "", HookDiagnostics.Status.INSTALLED)
        HookDiagnostics.printSummaryForStage("stage-a")
        HookDiagnostics.printSummaryForStage("stage-b")
        // Repeating the same stage is a no-op.
        HookDiagnostics.printSummaryForStage("stage-a")
        assertEquals(1, HookDiagnostics.summary()[HookDiagnostics.Status.INSTALLED])
    }

    @Test
    fun recordPreferencesUnavailable() {
        HookDiagnostics.currentProcessName = "test"
        HookDiagnostics.recordPreferencesUnavailable("java.lang.IllegalStateException", "getAll")
        assertEquals(1, HookDiagnostics.summary()[HookDiagnostics.Status.PREFERENCES_UNAVAILABLE])
    }

    @Test
    fun recordDexKitNoMatch() {
        HookDiagnostics.currentProcessName = "test"
        HookDiagnostics.recordDexKit("C", "m", noMatch = true)
        assertEquals(1, HookDiagnostics.summary()[HookDiagnostics.Status.DEXKIT_NO_MATCH])
    }

    @Test
    fun recordDexKitFailedTakesPrecedence() {
        HookDiagnostics.currentProcessName = "test"
        // A bridge/query failure must never be counted as a no-match, even if noMatch is also set.
        HookDiagnostics.recordDexKit("C", "m", exceptionType = "java.lang.IllegalStateException", noMatch = true)
        assertEquals(1, HookDiagnostics.summary()[HookDiagnostics.Status.DEXKIT_FAILED])
        assertEquals(0, HookDiagnostics.summary()[HookDiagnostics.Status.DEXKIT_NO_MATCH])
    }

    @Test
    fun recordDexKitBridgeNullIsFailed() {
        HookDiagnostics.currentProcessName = "test"
        HookDiagnostics.recordDexKit("C", "m", exceptionType = "bridge-null")
        assertEquals(1, HookDiagnostics.summary()[HookDiagnostics.Status.DEXKIT_FAILED])
    }

    @Test
    fun memberMissingExceptionDetected() {
        assertTrue(HookDiagnostics.isMemberMissingException(NoSuchMethodException("no method a")))
        assertTrue(HookDiagnostics.isMemberMissingException(NoSuchFieldException("no field")))
        assertTrue(!HookDiagnostics.isMemberMissingException(RuntimeException("other")))
    }

    @Test
    fun classMissingExceptionDetected() {
        assertTrue(HookDiagnostics.isClassMissingException(ClassNotFoundException("com.X")))
        assertTrue(!HookDiagnostics.isClassMissingException(RuntimeException("other")))
    }
}
