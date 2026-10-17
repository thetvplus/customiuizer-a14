package tv.withaibuild.customiuizer.tasker

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTokenLogicTest {

    private val provider = UnlockTokenProvider()

    private fun hostA() = UnlockTokenProvider.HostInfo("com.host.a", "Host A", setOf("cert-a-1", "cert-a-2"))
    private fun hostB() = UnlockTokenProvider.HostInfo("com.host.b", "Host B", setOf("cert-b-1"))

    @Test
    fun prepareDoesNotCreateToken() {
        val prefs = FakeSharedPreferences()
        val status = provider.prepare(prefs, hostA())
        assertTrue(status is UnlockTokenProvider.HostBindingStatus.NewHost)
        assertNull(prefs.getString("host_token_com.host.a", null))
        assertNull(prefs.getStringSet("host_certs_com.host.a", null))
    }

    @Test
    fun cancelOrBackDoesNotWrite() {
        val prefs = FakeSharedPreferences()
        provider.prepare(prefs, hostA())
        // No bind() call: nothing is written.
        assertNull(prefs.getString("host_token_com.host.a", null))
    }

    @Test
    fun bindCreatesToken() {
        val prefs = FakeSharedPreferences()
        val status = provider.prepare(prefs, hostA())
        assertTrue(status is UnlockTokenProvider.HostBindingStatus.NewHost)

        val token = provider.bind(prefs, hostA())
        assertNotNull(token)
        assertEquals("com.host.a", token?.hostPackage)
        assertEquals(token?.token, prefs.getString("host_token_com.host.a", null))
        assertEquals(setOf("cert-a-1", "cert-a-2"), prefs.getStringSet("host_certs_com.host.a", null))
    }

    @Test
    fun secondPageShowsReuse() {
        val prefs = FakeSharedPreferences()
        provider.bind(prefs, hostA())
        val status = provider.prepare(prefs, hostA())
        assertTrue(status is UnlockTokenProvider.HostBindingStatus.Reuse)
        val reuseToken = (status as UnlockTokenProvider.HostBindingStatus.Reuse).hostToken
        assertEquals("com.host.a", reuseToken.hostPackage)
    }

    @Test
    fun differentHostsGetDifferentTokens() {
        val prefs = FakeSharedPreferences()
        val a = provider.bind(prefs, hostA())
        val b = provider.bind(prefs, hostB())
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(a?.token, b?.token)
    }

    @Test
    fun samePackageDifferentCertIsRejected() {
        val prefs = FakeSharedPreferences()
        provider.bind(prefs, hostA())
        val attacker = UnlockTokenProvider.HostInfo("com.host.a", "Host A Fake", setOf("cert-fake"))
        assertNull(provider.bind(prefs, attacker))
        assertTrue(provider.prepare(prefs, attacker) is UnlockTokenProvider.HostBindingStatus.Mismatch)
    }

    @Test
    fun certificateRotationUpdatesOnBind() {
        val prefs = FakeSharedPreferences()
        val initial = UnlockTokenProvider.HostInfo("com.host.a", "Host A", setOf("cert-a-1"))
        val first = provider.bind(prefs, initial)

        // Prepare-only must not update lineage.
        val rotated = UnlockTokenProvider.HostInfo("com.host.a", "Host A", setOf("cert-a-1", "cert-a-2"))
        val prepareStatus = provider.prepare(prefs, rotated)
        assertTrue(prepareStatus is UnlockTokenProvider.HostBindingStatus.Reuse)
        assertEquals(setOf("cert-a-1"), prefs.getStringSet("host_certs_com.host.a", null))

        // Bind updates lineage.
        val second = provider.bind(prefs, rotated)
        assertNotNull(second)
        assertEquals(first?.token, second?.token)
        assertEquals(setOf("cert-a-1", "cert-a-2"), prefs.getStringSet("host_certs_com.host.a", null))
    }

    @Test
    fun correctTokenVerifiedForCorrectHost() {
        val prefs = FakeSharedPreferences()
        val token = provider.bind(prefs, hostA())
        assertTrue(provider.verify(prefs, "com.host.a", token?.token))
    }

    @Test
    fun aHostTokenCannotVerifyForBHost() {
        val prefs = FakeSharedPreferences()
        val a = provider.bind(prefs, hostA())
        assertFalse(provider.verify(prefs, "com.host.b", a?.token))
    }

    @Test
    fun bundleRejectsMismatchedOrMissingSender() {
        val prefs = FakeSharedPreferences()
        val token = provider.bind(prefs, hostA())
        assertFalse(provider.verify(prefs, "com.host.b", token?.token))
        assertFalse(provider.verifyBundle(prefs, null, "com.host.a"))
        assertFalse(provider.verifyBundle(prefs, Bundle(), "com.host.a"))
    }

    @Test
    fun missingEmptyOrWrongTokenIsRejected() {
        val prefs = FakeSharedPreferences()
        provider.bind(prefs, hostA())
        assertFalse(provider.verify(prefs, "com.host.a", null))
        assertFalse(provider.verify(prefs, "com.host.a", ""))
        assertFalse(provider.verify(prefs, "com.host.a", "wrong-token"))
        assertFalse(provider.verifyBundle(prefs, Bundle(), "com.host.a"))
        assertFalse(provider.verifyBundle(prefs, null, "com.host.a"))
    }

    @Test
    fun legacyGlobalTokenIsInvalid() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("unlock_token", "legacy-token").apply()
        assertFalse(provider.verify(prefs, "", "legacy-token"))
        assertFalse(provider.verify(prefs, "com.host.a", "legacy-token"))
    }
}
