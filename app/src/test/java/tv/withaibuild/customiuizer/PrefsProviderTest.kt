package tv.withaibuild.customiuizer

import android.app.Application
import android.content.pm.PermissionInfo
import android.net.Uri
import java.io.FileNotFoundException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.robolectric.shadows.ShadowBinder
import tv.withaibuild.customiuizer.utils.AppHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PrefsProviderTest {

    @Test fun committedHeightIsRestrictedToSystemUidAndDoesNotInventAMissingMirror() {
        ShadowBinder.setCallingUid(10381)
        try {
            assertThrows(SecurityException::class.java) { provider.call(PrefsProvider.COMMITTED_HEIGHT, null, null) }
            ShadowBinder.setCallingUid(1000)
            val previous = AppHelper.remotePrefs
            try {
                AppHelper.remotePrefs = null
                assertNull(provider.call(PrefsProvider.COMMITTED_HEIGHT, null, null))
            } finally { AppHelper.remotePrefs = previous }
        } finally { ShadowBinder.reset() }
    }

    private val provider = Robolectric.buildContentProvider(PrefsProvider::class.java).create().get()
    private fun uri(path: String) = Uri.parse("content://${PrefsProvider.AUTHORITY}/$path")

    @Test
    fun allOpenWithSamplesRemainReadable() {
        for (sample in 0..5) {
            provider.openAssetFile(uri("test/$sample"), "r")!!.use { descriptor ->
                descriptor.createInputStream().use { assertTrue(it.read() >= 0) }
            }
        }
    }

    @Test
    fun writeRequestsAndUnknownPathsAreRejected() {
        for (mode in listOf("w", "rw", "rwt", "wa", "")) {
            assertThrows(FileNotFoundException::class.java) { provider.openAssetFile(uri("test/0"), mode) }
        }
        for (path in listOf("", "test", "test/6", "test/0/extra", "test/../test0.png", "prefs/token")) {
            assertThrows(FileNotFoundException::class.java) { provider.openAssetFile(uri(path), "r") }
        }
        assertThrows(FileNotFoundException::class.java) {
            provider.openAssetFile(Uri.parse("content://other.provider/test/0"), "r")
        }
    }

    @Test
    fun sampleProviderRequiresAnExplicitUriGrant() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.resolveContentProvider(PrefsProvider.AUTHORITY, 0)!!
        assertFalse(info.exported)
        assertTrue(info.grantUriPermissions)
    }

    @Test
    fun nativeServiceBinderProviderRequiresTheModuleSignature() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        val permission = BuildConfig.APPLICATION_ID + ".permission.BROADCAST"
        val info = pm.resolveContentProvider(BuildConfig.APPLICATION_ID + ".XposedService", 0)!!
        assertTrue("Framework handshake must remain exported", info.exported)
        org.junit.Assert.assertEquals(permission, info.readPermission)
        org.junit.Assert.assertEquals(permission, info.writePermission)
        org.junit.Assert.assertEquals(PermissionInfo.PROTECTION_SIGNATURE,
            pm.getPermissionInfo(permission, 0).protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
    }
}
