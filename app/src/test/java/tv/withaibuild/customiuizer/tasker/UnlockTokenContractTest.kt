package tv.withaibuild.customiuizer.tasker

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTokenContractTest {

    private val unlockTokenProvider = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockTokenProvider.kt")
    private val unlockSettings = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockSettings.kt")
    private val unlockReceiver = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockReceiver.kt")
    private val taskerFiles = listOf(unlockTokenProvider, unlockSettings, unlockReceiver)

    @Test
    fun tokensArePerHostNotGlobal() {
        assertTrue(
            "Token storage must be keyed by host package",
            unlockTokenProvider.contains("host_token_") &&
                unlockTokenProvider.contains("host_certs_") &&
                unlockTokenProvider.contains("HostInfo")
        )
        assertFalse(
            "There must not be a single global token key",
            unlockTokenProvider.contains(Regex("const val PREF_KEY_TOKEN")) &&
                unlockTokenProvider.contains("= \"unlock_token\"")
        )
    }

    @Test
    fun hostInfoIncludesPackageLabelAndCerts() {
        assertTrue(
            "HostInfo must capture package, label and certificate fingerprints",
            unlockTokenProvider.contains("data class HostInfo") &&
                unlockTokenProvider.contains("packageName") &&
                unlockTokenProvider.contains("applicationLabel") &&
                unlockTokenProvider.contains("certFingerprints")
        )
        assertTrue(
            "Certificate extraction must use SHA-256",
            unlockTokenProvider.contains("MessageDigest.getInstance(\"SHA-256\")") &&
                unlockTokenProvider.contains("signingCertificateHistory")
        )
    }

    @Test
    fun prepareIsReadOnlyAndBindWrites() {
        val prepareSection = unlockTokenProvider.substringAfter("fun prepare(").substringBefore("fun bind(")
        assertTrue(
            "prepare must be present and read-only",
            unlockTokenProvider.contains("fun prepare(") &&
                !prepareSection.contains(".edit()") &&
                !prepareSection.contains(".apply()") &&
                !prepareSection.contains("generateToken()")
        )
        assertTrue(
            "bind must be the only write path",
            unlockTokenProvider.contains("fun bind(") &&
                unlockTokenProvider.contains("saveHostToken") &&
                unlockTokenProvider.contains("saveCerts")
        )
    }

    @Test
    fun unlockSettingsDoesNotWriteOnCreate() {
        val onCreateSection = unlockSettings.substringAfter("override fun onCreate(savedInstanceState: Bundle?)").substringBefore("private fun formatHostSummary")
        assertFalse(
            "UnlockSettings must not call bind() or create token in onCreate",
            onCreateSection.contains("bind(this") ||
                onCreateSection.contains("getOrCreateToken(this") ||
                onCreateSection.contains("generateToken()")
        )
        assertTrue(
            "UnlockSettings must call prepare() in onCreate",
            onCreateSection.contains("provider.prepare(this")
        )
        assertTrue(
            "UnlockSettings must call bind() only in the OK click handler",
            unlockSettings.contains("onConfirm()") &&
                unlockSettings.contains("provider.bind(this")
        )
    }

    @Test
    fun cancelAndBackDoNotBind() {
        val backCallback = unlockSettings.substringAfter("handleOnBackPressed() {").substringBefore("            }")
        assertTrue(
            "Back callback must return RESULT_CANCELED without calling bind",
            unlockSettings.contains("onBackPressedDispatcher.addCallback") &&
                backCallback.contains("Activity.RESULT_CANCELED") &&
                backCallback.contains("finish()") &&
                !backCallback.contains("bind(this")
        )
    }

    @Test
    fun unlockSettingsRequiresCallingPackage() {
        assertTrue(
            "UnlockSettings must read callingPackage",
            unlockSettings.contains("callingPackage")
        )
        assertTrue(
            "Missing calling package must cancel",
            unlockSettings.contains("Activity.RESULT_CANCELED") &&
                unlockSettings.contains("if (callingPackage == null)")
        )
        assertTrue(
            "Host summary must be shown to the user",
            unlockSettings.contains("R.id.host_info") &&
                unlockSettings.contains("R.string.unlock_host_summary")
        )
    }

    @Test
    fun unlockReceiverRequiresSenderIdentity() {
        assertTrue(
            "UnlockReceiver must read getSentFromPackage",
            unlockReceiver.contains("getSentFromPackage()")
        )
        assertTrue(
            "UnlockReceiver must reject when sender identity is not shared",
            unlockReceiver.contains("identity-missing")
        )
        assertTrue(
            "UnlockReceiver must verify token against the actual sender",
            unlockReceiver.contains("verifyBundle(context, bundle, sender)")
        )
        assertFalse(
            "UnlockReceiver must not fall back to explicit-component only verification",
            unlockReceiver.contains("isExplicitToThisComponent") ||
                unlockReceiver.contains("verifyBundle(context, bundle, null)")
        )
    }

    @Test
    fun bundleContainsHostPackageAndToken() {
        assertTrue(
            "Bundle must carry the host package",
            unlockSettings.contains("BUNDLE_KEY_HOST_PACKAGE") &&
                unlockSettings.contains("putString(UnlockTokenProvider.BUNDLE_KEY_HOST_PACKAGE")
        )
        assertTrue(
            "Bundle must carry the per-host token",
            unlockSettings.contains("putString(UnlockTokenProvider.BUNDLE_KEY_TOKEN")
        )
        assertTrue(
            "UnlockTokenProvider must expose host package and token bundle keys",
            unlockTokenProvider.contains("BUNDLE_KEY_HOST_PACKAGE") &&
                unlockTokenProvider.contains("BUNDLE_KEY_TOKEN")
        )
    }

    @Test
    fun rejectionLogsAreThrottledAndDoNotLeakToken() {
        assertTrue(
            "UnlockReceiver must use a rate limiter for rejections",
            unlockReceiver.contains("logLimited") &&
                unlockReceiver.contains("SystemClock.elapsedRealtime()") &&
                unlockReceiver.contains("LOG_THROTTLE_MS")
        )
        assertFalse(
            "Log messages must not include the token value",
            unlockReceiver.contains("\$token") ||
                unlockReceiver.contains("bundle.getString(BUNDLE_KEY_TOKEN)")
        )
        assertFalse(
            "Log messages must not dump the Bundle contents",
            unlockReceiver.contains("bundle.toString()") ||
                unlockReceiver.contains("Log.d(")
        )
    }

    @Test
    fun tokenNeverLoggedOrExported() {
        assertFalse(
            "Token value must never be written to logs",
            unlockReceiver.contains("XposedHelpers.log(") ||
                (unlockReceiver.contains("android.util.Log") && unlockReceiver.contains("\$token"))
        )
        assertFalse(
            "Token value must never be shown in a Toast",
            unlockSettings.contains("Toast.makeText") && unlockSettings.contains("\$token")
        )
        assertFalse(
            "Token value must not be shared via settings export",
            unlockTokenProvider.contains("appPrefs") ||
                unlockTokenProvider.contains("MainModule.mPrefs")
        )
    }

    @Test
    fun taskerComponentsDoNotReferenceHookOrLibxposed() {
        val joined = taskerFiles.joinToString("")
        assertFalse(
            "Tasker plugin files must not reference XposedHelpers",
            joined.contains("XposedHelpers")
        )
        assertFalse(
            "Tasker plugin files must not reference MainModule",
            joined.contains("MainModule")
        )
        assertFalse(
            "Tasker plugin files must not reference libxposed / XposedBridge",
            joined.contains("libxposed") ||
                joined.contains("XposedBridge") ||
                joined.contains("de.robv.android.xposed")
        )
        assertFalse(
            "UnlockReceiver must not use mods/utils ModuleHelper",
            unlockReceiver.contains("ModuleHelper")
        )
    }

    @Test
    fun constantsDoNotDefineTokenValue() {
        val constants = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/Constants.kt")
        assertFalse(
            "Constants should not contain a hard-coded token",
            constants.contains(Regex("const\\s+val\\s+.*TOKEN"))
        )
    }

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
