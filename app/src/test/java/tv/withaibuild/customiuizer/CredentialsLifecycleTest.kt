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
