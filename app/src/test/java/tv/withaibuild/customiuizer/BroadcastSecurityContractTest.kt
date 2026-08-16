package tv.withaibuild.customiuizer

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroadcastSecurityContractTest {

    private val manifest = source("app/src/main/AndroidManifest.xml")
    private val globalActions = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt")
    private val moduleHelper = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt")
    private val fastRebootBootstrap = source("app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FastRebootBootstrap.kt")
    private val systemServerHooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
    private val systemLockScreenHooks = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt")
    private val launcher = source("app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt")
    private val btList = source("app/src/main/java/tv/withaibuild/customiuizer/subs/BTList.kt")
    private val appSelector = source("app/src/main/java/tv/withaibuild/customiuizer/subs/AppSelector.kt")
    private val preferenceFragmentBase = source("app/src/main/java/tv/withaibuild/customiuizer/PreferenceFragmentBase.kt")

    @Test
    fun broadcastPermissionIsDeclaredWithSignatureOnly() {
        assertTrue(
            "Expected a permission declaration for BROADCAST",
            manifest.contains("android:name=\"tv.withaibuild.customiuizer.r14.permission.BROADCAST\"")
        )
        assertTrue(
            "Expected protectionLevel=\"signature\" (not signatureOrSystem)",
            manifest.contains("android:protectionLevel=\"signature\"")
        )
        assertFalse(
            "signatureOrSystem is deprecated and should not be used",
            manifest.contains("signatureOrSystem")
        )
        assertTrue(
            "Module app must request its own signature permission",
            manifest.contains("uses-permission android:name=\"tv.withaibuild.customiuizer.r14.permission.BROADCAST\"")
        )
    }

    @Test
    fun globalActionsExposesBroadcastPermissionAndIdentityHelpers() {
        assertTrue(
            "GlobalActions should expose BROADCAST_PERMISSION",
            globalActions.contains("BROADCAST_PERMISSION = \"tv.withaibuild.customiuizer.r14.permission.BROADCAST\"")
        )
    }

    @Test
    fun moduleHelperProvidesIdentityBroadcastAndVerification() {
        assertTrue(
            "ModuleHelper should provide sendBroadcastWithIdentity",
            moduleHelper.contains("fun sendBroadcastWithIdentity(")
        )
        assertTrue(
            "ModuleHelper should provide sendOrderedBroadcastWithIdentity",
            moduleHelper.contains("fun sendOrderedBroadcastWithIdentity(")
        )
        assertTrue(
            "Sender must share identity via BroadcastOptions",
            moduleHelper.contains(".setShareIdentityEnabled(true)")
        )
        assertTrue(
            "ModuleHelper should provide isTrustedBroadcast",
            moduleHelper.contains("fun isTrustedBroadcast(")
        )
        assertTrue(
            "Receiver must call getSentFromPackage",
            moduleHelper.contains("receiver.getSentFromPackage()")
        )
    }

    @Test
    fun highPrivilegeHostReceiversVerifySenderIdentity() {
        assertTrue(
            "mSBReceiver must verify sender",
            globalActions.contains("ModuleHelper.isTrustedBroadcast(") &&
                globalActions.contains("\"android\"") &&
                globalActions.contains("\"com.android.systemui\"") &&
                globalActions.contains("\"com.miui.home\"")
        )
        assertTrue(
            "phoneWindowManagerActionReceiver must verify sender",
            systemServerHooks.contains("ModuleHelper.isTrustedBroadcast(")
        )
        assertTrue(
            "noScreenLockReceiver must verify sender with isTrustedBroadcast",
            systemLockScreenHooks.contains("ModuleHelper.isTrustedBroadcast(this, Helpers.modulePkg") &&
                systemLockScreenHooks.contains("ModuleHelper.isTrustedBroadcast(this, \"com.android.systemui\"")
        )
    }

    @Test
    fun moduleToHostReceiversUseSignaturePermission() {
        // FastReboot, fetchCachedDevices, FETCHAPPCONFIG are the only receivers that keep the signature permission.
        assertTrue(
            "fastRebootReceiver must be protected by the signature permission",
            fastRebootBootstrap.contains("\"fastRebootReceiver\"") &&
                fastRebootBootstrap.contains("GlobalActions.BROADCAST_PERMISSION")
        )
        assertTrue(
            "fetchCachedDevicesReceiver must be protected by the signature permission",
            systemLockScreenHooks.contains("\"fetchCachedDevicesReceiver\"") &&
                systemLockScreenHooks.contains("GlobalActions.BROADCAST_PERMISSION")
        )
        assertTrue(
            "fetchAppConfigReceiver must be protected by the signature permission",
            launcher.contains("\"fetchAppConfigReceiver\"") &&
                launcher.contains("GlobalActions.BROADCAST_PERMISSION")
        )
    }

    @Test
    fun hostToModuleDataReceiversCheckSpecificSenderPackage() {
        assertTrue(
            "PUSHAPPCONFIG receiver must accept only com.miui.home",
            (appSelector.contains("isTrustedBroadcast") && appSelector.contains("\"com.miui.home\"")) ||
                appSelector.contains("getSentFromPackage() != \"com.miui.home\"")
        )
        assertTrue(
            "CACHEDDEVICESUPDATE receiver must accept only com.android.systemui",
            (btList.contains("isTrustedBroadcast") && btList.contains("\"com.android.systemui\"")) ||
                btList.contains("getSentFromPackage() != \"com.android.systemui\"")
        )
    }

    @Test
    fun softRebootBroadcastUsesIdentityAndRemainsAddressedToSystemUI() {
        val sendSoftReboot = preferenceFragmentBase.section(
            "private fun sendSoftReboot()",
            "private fun showFastRebootFailure("
        )
        assertTrue(sendSoftReboot.contains("intent.setPackage(\"com.android.systemui\")"))
        assertTrue(sendSoftReboot.contains("ModuleHelper.sendOrderedBroadcastWithIdentity("))
    }

    @Test
    fun fastRebootSenderUsesSignatureAndExplicitPackage() {
        assertTrue(
            "FastReboot sender must use ModuleHelper.sendOrderedBroadcastWithIdentity and setPackage",
            preferenceFragmentBase.contains("intent.setPackage(\"com.android.systemui\")") &&
                preferenceFragmentBase.contains("ModuleHelper.sendOrderedBroadcastWithIdentity(")
        )
        assertTrue(fastRebootBootstrap.contains("GlobalActions.BROADCAST_PERMISSION"))
        assertTrue(fastRebootBootstrap.contains("Context.RECEIVER_EXPORTED"))
    }

    @Test
    fun allModuleSendersNowShareIdentity() {
        val senders = listOf(
            globalActions,
            systemLockScreenHooks,
            launcher,
            btList,
            appSelector,
            preferenceFragmentBase
        )
        val identityCallers = senders.flatMap { it.split("\n") }
            .filter { it.contains("sendBroadcastWithIdentity") || it.contains("sendOrderedBroadcastWithIdentity") }
        assertTrue(
            "Internal module senders must call identity-sharing helpers",
            identityCallers.size >= 6
        )
    }

    @Test
    fun unlockReceiverUsesPerHostToken() {
        val unlockReceiver = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockReceiver.kt")
        val unlockTokenProvider = source("app/src/main/java/tv/withaibuild/customiuizer/tasker/UnlockTokenProvider.kt")
        assertTrue(
            "UnlockReceiver must get and verify the actual broadcast sender",
            unlockReceiver.contains("getSentFromPackage()") &&
                unlockReceiver.contains("verifyBundle(context, bundle, sender)")
        )
        assertTrue(
            "UnlockReceiver must not fall back to explicit component only",
            !unlockReceiver.contains("isExplicitToThisComponent")
        )
        assertTrue(
            "UnlockTokenProvider must use SecureRandom",
            unlockTokenProvider.contains("SecureRandom()")
        )
        assertTrue(
            "Token must not be hard-coded",
            !unlockTokenProvider.contains(Regex("const val TOKEN\\b")) &&
                !unlockTokenProvider.contains(Regex("\"[A-Za-z0-9]{16,}\""))
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

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        val endIndex = indexOf(end, startIndex + start.length)
        check(startIndex >= 0 && endIndex > startIndex) {
            "Could not extract source section between '$start' and '$end'"
        }
        return substring(startIndex, endIndex)
    }
}
