package tv.withaibuild.customiuizer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.testsupport.repositorySource
import tv.withaibuild.customiuizer.utils.Helpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ActionSenderBoundaryTest.Sender::class])
class ActionSenderBoundaryTest {
    @Implements(BroadcastReceiver::class)
    class Sender {
        @Implementation fun getSentFromPackage(): String? = identity
        companion object { var identity: String? = null }
    }

    @Test fun frameworkSenderIdentityRejectsMissingAndUnrelatedPackages() {
        val receiver = object : BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) = Unit }
        try {
            for (pkg in listOf(null, "synthetic.untrusted", Helpers.modulePkg, "android", "com.android.systemui", "com.miui.home")) {
                Sender.identity = pkg
                assertEquals(pkg != null && pkg != "synthetic.untrusted", ModuleHelper.isTrustedBroadcast(
                    receiver, Helpers.modulePkg, "android", "com.android.systemui", "com.miui.home"))
            }
        } finally { Sender.identity = null }
    }

    @Test fun eachPreviouslyUnguardedActionChecksIdentityBeforePrivilegedWork() {
        val source = repositorySource("app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt")
        for ((action, work) in listOf("PinningWindow" to "ProcessManager.getForegroundInfo()",
            "SplitScreen" to "ProcessManager.getForegroundInfo()", "ToggleAutoBrightness" to "ModuleHelper.getModuleRes(mContext)")) {
            val body = source.substringAfter("if (action == GlobalActions.ACTION_PREFIX + \"$action\") {")
                .substringBefore(work)
            assertTrue("$action must authenticate before host work", body.contains("if (!ModuleHelper.isTrustedBroadcast(this,"))
            assertTrue(body.contains("Helpers.modulePkg, \"android\", \"com.android.systemui\", \"com.miui.home\""))
            assertTrue(body.contains("rejectionResultCode = GlobalActions.ACTION_FAILED)) return@guarded"))
        }
    }
}
