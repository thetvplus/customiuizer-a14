package tv.withaibuild.customiuizer.mods

import android.os.AsyncTask
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChargingHintCallerScopeTest {

    private class KeyguardOuter

    @Suppress("DEPRECATION")
    private class MatchingKeyguardTask : AsyncTask<Void, Void, String>() {
        @JvmField
        val `this$0` = KeyguardOuter()

        @JvmField
        val `val$batteryLevel` = 50

        @JvmField
        val `val$powerPluggedIn` = true

        override fun doInBackground(vararg params: Void?): String = "hint"
    }

    @Suppress("DEPRECATION")
    private class WrongKeyguardTask : AsyncTask<Void, Void, String>() {
        @JvmField
        val `this$0` = KeyguardOuter()

        override fun doInBackground(vararg params: Void?): String = "hint"
    }

    private class MiuiChargeFixture {
        fun onContentChanged(
            @Suppress("UNUSED_PARAMETER") key: String,
            @Suppress("UNUSED_PARAMETER") value: String
        ) = Unit

        fun onContentChanged(@Suppress("UNUSED_PARAMETER") key: String) = Unit
    }

    @Test
    fun resolvesVerifiedKeyguardAsyncTaskShapeWithoutUsingItsOrdinal() {
        val method = resolveKeyguardChargingHintCallerMethod(
            listOf(WrongKeyguardTask::class.java, MatchingKeyguardTask::class.java),
            KeyguardOuter::class.java.name
        )

        assertEquals("doInBackground", method.name)
        assertEquals(Any::class.java, method.returnType)
        assertTrue(method.parameterTypes.contentEquals(arrayOf(Array<Any>::class.java)))
    }

    @Test
    fun resolvesOnlyExactMiuiChargeMethodShape() {
        val method = resolveMiuiChargeChargingHintCallerMethod(MiuiChargeFixture::class.java)

        assertEquals("onContentChanged", method.name)
        assertEquals(Void.TYPE, method.returnType)
        assertTrue(
            method.parameterTypes.contentEquals(
                arrayOf(String::class.java, String::class.java)
            )
        )
    }

    @Test
    fun keyguardHasPriorityOverMiuiChargeAndUnknownIsExcluded() {
        val scopes = ChargingHintCallerScopes()

        assertEquals(ChargingHintCaller.UNKNOWN, scopes.currentCaller())
        assertFalse(scopes.isKeyguardCaller())

        scopes.callFromMiuiCharge {
            assertEquals(ChargingHintCaller.MIUI_CHARGE, scopes.currentCaller())
            assertFalse(scopes.isKeyguardCaller())

            scopes.callFromKeyguard {
                assertEquals(ChargingHintCaller.KEYGUARD, scopes.currentCaller())
                assertTrue(scopes.isKeyguardCaller())
            }

            assertEquals(ChargingHintCaller.MIUI_CHARGE, scopes.currentCaller())
        }

        assertEquals(ChargingHintCaller.UNKNOWN, scopes.currentCaller())
    }

    @Test
    fun nestedKeyguardCallsRemainActiveUntilTheOuterCallExits() {
        val scopes = ChargingHintCallerScopes()

        scopes.callFromKeyguard {
            assertTrue(scopes.isKeyguardCaller())
            scopes.callFromKeyguard {
                assertTrue(scopes.isKeyguardCaller())
            }
            assertTrue(scopes.isKeyguardCaller())
        }

        assertFalse(scopes.isKeyguardCaller())
    }

    @Test
    fun callerExceptionIsPropagatedAndScopeIsCleared() {
        val scopes = ChargingHintCallerScopes()
        val expected = IllegalStateException("ROM failure")

        try {
            scopes.callFromKeyguard<Unit> { throw expected }
            fail("Expected the original caller exception")
        } catch (actual: IllegalStateException) {
            assertSame(expected, actual)
        }

        assertEquals(ChargingHintCaller.UNKNOWN, scopes.currentCaller())
    }

    @Test
    fun chargingInfoHotPathNoLongerScansTheThreadStack() {
        val hookSource = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt"
        )
        val scopeSource = source(
            "app/src/main/java/tv/withaibuild/customiuizer/mods/ChargingHintCallerScope.kt"
        )
        val sectionStart = hookSource.indexOf("fun ChargingInfoHook(")
        val sectionEnd = hookSource.indexOf("fun NoSOSHook(", sectionStart)
        val section = hookSource.substring(sectionStart, sectionEnd)

        assertFalse(section.contains("Thread.currentThread().stackTrace"))
        assertTrue(section.contains("chargingHintCallerScopes.isKeyguardCaller()"))
        assertTrue(scopeSource.contains("finally {"))
        assertTrue(scopeSource.contains("chain.proceed()"))
    }

    private fun <T> ChargingHintCallerScopes.callFromKeyguard(block: () -> T): T {
        enterKeyguard()
        return try {
            block()
        } finally {
            exitKeyguard()
        }
    }

    private fun <T> ChargingHintCallerScopes.callFromMiuiCharge(block: () -> T): T {
        enterMiuiCharge()
        return try {
            block()
        } finally {
            exitMiuiCharge()
        }
    }

    private fun source(relativePath: String): String {
        var directory = File(requireNotNull(java.lang.System.getProperty("user.dir"))).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
