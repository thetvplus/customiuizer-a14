package tv.withaibuild.customiuizer

import android.app.Activity
import android.app.Application
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.content.Intent
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadows.ShadowKeyguardManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@Implements(KeyguardManager::class)
class UnavailableCredentialShadow : ShadowKeyguardManager() {
    @Implementation fun createConfirmDeviceCredentialIntent(title: CharSequence?, description: CharSequence?): Intent? {
        throw SecurityException("Synthetic credential service failure")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CredentialsLifecycleTest {
    @Test fun creatingACredentialShortcutKeepsTheExternalLauncherResultWithoutPinningIt() {
        val controller = Robolectric.buildActivity(CredentialsShortcut::class.java).create()
        try {
            val activity = controller.get()
            val result = shadowOf(activity).resultIntent
            assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
            assertTrue(activity.isFinishing)
            val target = result.getParcelableExtra("android.intent.extra.shortcut.INTENT", Intent::class.java)!!
            assertEquals(Credentials::class.java.name, target.component!!.className)
            assertEquals(activity.packageName, target.component!!.packageName)
            assertEquals(Intent.ACTION_VIEW, target.action)
            assertEquals(activity.getString(R.string.credentials_unlock),
                result.getStringExtra("android.intent.extra.shortcut.NAME"))
            val icon = result.getParcelableExtra("android.intent.extra.shortcut.ICON_RESOURCE",
                Intent.ShortcutIconResource::class.java)!!
            assertEquals(activity.packageName, icon.packageName)
            assertEquals(activity.resources.getResourceName(R.drawable.ic_credentials), icon.resourceName)
            assertTrue(activity.getSystemService(android.content.pm.ShortcutManager::class.java).pinnedShortcuts.isEmpty())
        } finally { controller.destroy() }
    }

    @Test fun manifestShortcutTargetsTheInstalledA14CredentialActivity() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.getXml(R.xml.shortcuts).use { parser ->
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "intent") {
                    val intent = Intent.parseIntent(context.resources, parser, android.util.Xml.asAttributeSet(parser))
                    assertEquals(context.packageName, intent.component!!.packageName)
                    assertEquals(Credentials::class.java.name, intent.component!!.className)
                    assertEquals(Credentials::class.java.name, context.packageManager.getActivityInfo(intent.component!!, 0).name)
                    return
                }
            }
        }
        fail("The declared credential shortcut must contain its explicit activity intent")
    }

    @Before fun secure() {
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(KeyguardManager::class.java)).apply { setIsKeyguardSecure(true); setIsDeviceSecure(true) }
        ShadowToast.reset()
    }
    @Test fun successRequiresTheSystemResultAndCancelDoesNotReportSuccess() {
        for (code in listOf(Activity.RESULT_CANCELED, Activity.RESULT_OK)) {
            ShadowToast.reset()
            val controller = Robolectric.buildActivity(Credentials::class.java).setup()
            val activity = controller.get()
            val request = shadowOf(activity).nextStartedActivityForResult
            assertNotNull(request)
            assertFalse(activity.isFinishing)
            assertNull(ShadowToast.getTextOfLatestToast())
            activity.activityResultRegistry.dispatchResult(request.requestCode, code, null)
            assertTrue(activity.isFinishing)
            if (code == Activity.RESULT_OK) assertEquals(activity.getString(R.string.credentials_success), ShadowToast.getTextOfLatestToast())
            else assertNull(ShadowToast.getTextOfLatestToast())
            controller.pause().stop().destroy()
        }
    }
    @Test fun recreationRestoresPendingRequestWithoutLaunchingAgain() {
        val first = Robolectric.buildActivity(Credentials::class.java).setup()
        val request = shadowOf(first.get()).nextStartedActivityForResult
        val state = Bundle()
        first.saveInstanceState(state).pause().stop().destroy()
        val second = Robolectric.buildActivity(Credentials::class.java).create(state).start().resume()
        assertNull(shadowOf(second.get()).nextStartedActivityForResult)
        second.get().activityResultRegistry.dispatchResult(request.requestCode, Activity.RESULT_OK, null)
        assertTrue(second.get().isFinishing)
        second.pause().stop().destroy()
    }
    @Test
    @Config(shadows = [UnavailableCredentialShadow::class])
    fun serviceFailureFinishesWithoutSuccess() {
        val controller = Robolectric.buildActivity(Credentials::class.java).setup()
        assertTrue(controller.get().isFinishing)
        assertNull(ShadowToast.getTextOfLatestToast())
        assertNull(shadowOf(controller.get()).nextStartedActivityForResult)
        controller.pause().stop().destroy()
    }
    @Test fun unrelatedSavedStateDoesNotLeaveAnEmptyTransparentActivity() {
        val controller = Robolectric.buildActivity(Credentials::class.java).create(Bundle()).start().resume()
        assertNotNull(shadowOf(controller.get()).nextStartedActivityForResult)
        controller.pause().stop().destroy()
    }
    @Test fun unsecuredDeviceOpensSystemSetupAndFinishes() {
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(KeyguardManager::class.java)).apply { setIsKeyguardSecure(false); setIsDeviceSecure(false) }
        val controller = Robolectric.buildActivity(Credentials::class.java).setup()
        assertEquals(DevicePolicyManager.ACTION_SET_NEW_PASSWORD, shadowOf(controller.get()).nextStartedActivity.action)
        assertTrue(controller.get().isFinishing)
        assertNull(ShadowToast.getTextOfLatestToast())
        controller.pause().stop().destroy()
    }
}
