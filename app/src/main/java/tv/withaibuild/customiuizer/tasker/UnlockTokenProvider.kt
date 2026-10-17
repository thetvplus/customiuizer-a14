package tv.withaibuild.customiuizer.tasker

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Per-host binding tokens for the Tasker / Locale UnlockReceiver plugin.
 *
 * The flow is intentionally split into two stages to avoid writing a token or
 * updating certificate history before the user explicitly confirms the binding:
 *
 * - [prepare] is read-only. It inspects the stored binding for the calling host
 *   and returns whether the host is new, already bound, or has a mismatched
 *   certificate. No SharedPreferences write happens here.
 * - [bind] is called only when the user taps OK. It creates the token for a new
 *   host, reuses it for an existing host, and updates the stored certificate
 *   lineage only on successful confirmation.
 *
 * [verify] and [verifyBundle] are used by [UnlockReceiver] to check that the
 *   Bundle's host package and token match a stored binding.
 */
class UnlockTokenProvider {

    data class HostInfo(
        val packageName: String,
        val applicationLabel: String?,
        val certFingerprints: Set<String>
    ) {
        val currentFingerprint: String?
            get() = certFingerprints.firstOrNull()

        val historySummary: String
            get() = certFingerprints.joinToString("\n") { "  - $it" }
    }

    data class HostToken(
        val hostPackage: String,
        val token: String
    )

    sealed class HostBindingStatus {
        data class NewHost(val hostInfo: HostInfo) : HostBindingStatus()
        data class Reuse(val hostToken: HostToken) : HostBindingStatus()
        object Mismatch : HostBindingStatus()
    }

    /**
     * Read-only inspection of the host binding. Does **not** create or update
     * any stored state.
     */
    fun prepare(context: Context, hostInfo: HostInfo): HostBindingStatus {
        return prepare(getPrefs(context), hostInfo)
    }

    internal fun prepare(prefs: SharedPreferences, hostInfo: HostInfo): HostBindingStatus {
        val storedToken = prefs.getString(tokenKey(hostInfo.packageName), null)
        val storedCerts = getStoredCerts(prefs, hostInfo.packageName)

        return if (storedToken.isNullOrEmpty() || storedCerts.isEmpty()) {
            HostBindingStatus.NewHost(hostInfo)
        } else if (hostInfo.certFingerprints.any { it in storedCerts }) {
            HostBindingStatus.Reuse(HostToken(hostInfo.packageName, storedToken))
        } else {
            HostBindingStatus.Mismatch
        }
    }

    /**
     * Create or confirm the binding for [hostInfo]. This must only be called
     * after the user explicitly confirms the dialog. It writes the token and
     * certificate lineage to private SharedPreferences.
     *
     * Returns the [HostToken] on success, or `null` if the certificate does not
     * match the stored lineage.
     */
    fun bind(context: Context, hostInfo: HostInfo): HostToken? {
        return bind(getPrefs(context), hostInfo)
    }

    internal fun bind(prefs: SharedPreferences, hostInfo: HostInfo): HostToken? {
        val storedToken = prefs.getString(tokenKey(hostInfo.packageName), null)
        val storedCerts = getStoredCerts(prefs, hostInfo.packageName)

        return if (storedToken.isNullOrEmpty() || storedCerts.isEmpty()) {
            // First binding for this package.
            val token = generateToken()
            saveHostToken(prefs, hostInfo.packageName, token, hostInfo.certFingerprints)
            HostToken(hostInfo.packageName, token)
        } else if (hostInfo.certFingerprints.any { it in storedCerts }) {
            // Re-binding: update lineage with any new fingerprints, then return existing token.
            saveCerts(prefs, hostInfo.packageName, hostInfo.certFingerprints union storedCerts)
            HostToken(hostInfo.packageName, storedToken)
        } else {
            null
        }
    }

    fun verify(context: Context, hostPackage: String, token: String?): Boolean {
        return verify(getPrefs(context), hostPackage, token)
    }

    internal fun verify(prefs: SharedPreferences, hostPackage: String, token: String?): Boolean {
        if (token.isNullOrEmpty()) return false
        val stored = prefs.getString(tokenKey(hostPackage), null)
        return !stored.isNullOrEmpty() && stored == token
    }

    fun verifyBundle(context: Context, bundle: Bundle?, actualSender: String? = null): Boolean {
        return verifyBundle(getPrefs(context), bundle, actualSender)
    }

    internal fun verifyBundle(prefs: SharedPreferences, bundle: Bundle?, actualSender: String? = null): Boolean {
        if (bundle == null) return false
        val hostPackage = try {
            bundle.getString(BUNDLE_KEY_HOST_PACKAGE)
        } catch (t: Throwable) {
            null
        }
        val token = try {
            bundle.getString(BUNDLE_KEY_TOKEN)
        } catch (t: Throwable) {
            null
        }
        if (hostPackage.isNullOrEmpty() || token.isNullOrEmpty()) return false
        // If a sender identity is available, it must match the host recorded in the Bundle.
        if (actualSender != null && hostPackage != actualSender) return false
        return verify(prefs, hostPackage, token)
    }

    /**
     * Look up host information for [packageName]. Returns null if the package is
     * not installed or signing information cannot be read.
     */
    fun getHostInfo(context: Context, packageName: String): HostInfo? {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            val label = pm.getApplicationLabel(appInfo).toString()
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            val certs = extractCertFingerprints(packageInfo)
            if (certs.isEmpty()) null else HostInfo(packageName, label, certs)
        } catch (t: Throwable) {
            null
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getStoredCerts(prefs: SharedPreferences, packageName: String): Set<String> {
        return prefs.getStringSet(certsKey(packageName), null) ?: emptySet()
    }

    private fun saveHostToken(prefs: SharedPreferences, packageName: String, token: String, certs: Set<String>) {
        prefs.edit()
            .putString(tokenKey(packageName), token)
            .putStringSet(certsKey(packageName), certs)
            .apply()
    }

    private fun saveCerts(prefs: SharedPreferences, packageName: String, certs: Set<String>) {
        prefs.edit()
            .putStringSet(certsKey(packageName), certs)
            .apply()
    }

    private fun tokenKey(packageName: String) = "host_token_$packageName"
    private fun certsKey(packageName: String) = "host_certs_$packageName"

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun extractCertFingerprints(packageInfo: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
        return signatures?.map { sha256Hex(it.toByteArray()) }?.toSet() ?: emptySet()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val PREFS_NAME = "customiuizer_unlock_hosts"
        const val BUNDLE_KEY_TOKEN = "customiuizer.unlock_token"
        const val BUNDLE_KEY_HOST_PACKAGE = "customiuizer.unlock_host_package"
        const val TOKEN_BYTES = 32
    }
}
