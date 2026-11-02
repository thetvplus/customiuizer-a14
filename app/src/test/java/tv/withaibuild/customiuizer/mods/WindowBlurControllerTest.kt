package tv.withaibuild.customiuizer.mods

import android.content.Context
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.PrefsProvider
import tv.withaibuild.customiuizer.mods.utils.SystemServerPreferenceInvalidation
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Executes the production policy against a Host port of the independently decoded ROM inputs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WindowBlurControllerTest {
    private val saved = MainModule.mPrefs.getAll()
    private val context: Context = RuntimeEnvironment.getApplication()
    private val savedSetting = Settings.Global.getString(context.contentResolver, "disable_window_blurs")
    @Before fun setup() { Settings.Global.putInt(context.contentResolver, "disable_window_blurs", 0) }
    private class NativeController(@JvmField val mContext: Context) {
        @JvmField val mLock = Any()
        @JvmField var mBlurDisabledSetting = false
        var powerSave = false
        var thermal = false
        var tunnel = false
        var enabled = true
        var notifications = 0
        var updates = 0
        fun updateBlurEnabled() {
            assertTrue(Thread.holdsLock(mLock))
            updates++
            val next = !mBlurDisabledSetting && !powerSave && !thermal && !tunnel
            if (enabled != next) { enabled = next; notifications++ }
        }
    }

    private fun module(disabled: Boolean) {
        MainModule.mPrefs.put("system_disable_window_blurs", disabled)
        XposedHelpers.callMethod(SystemDisplayHooks, "refreshDisplaySnapshot")
    }
    private fun apply(controller: NativeController) {
        XposedHelpers.callMethod(SystemDisplayHooks, "applyWindowBlurPolicy", controller)
    }
    @After fun close() {
        Settings.Global.putString(context.contentResolver, "disable_window_blurs", savedSetting)
        MainModule.mPrefs.replaceSnapshot(saved)
        XposedHelpers.callMethod(SystemDisplayHooks, "refreshDisplaySnapshot")
    }

    @Test fun togglingModuleRestoresTheLatestDeveloperSettingWithoutWritingIt() {
        val controller = NativeController(context)
        Settings.Global.putInt(context.contentResolver, "disable_window_blurs", 0)
        module(true); apply(controller)
        assertFalse(controller.enabled)
        assertEquals(0, Settings.Global.getInt(context.contentResolver, "disable_window_blurs"))
        Settings.Global.putInt(context.contentResolver, "disable_window_blurs", 1)
        module(false); apply(controller)
        assertFalse(controller.enabled)
        assertTrue(controller.mBlurDisabledSetting)
        Settings.Global.putInt(context.contentResolver, "disable_window_blurs", 0)
        apply(controller)
        assertTrue(controller.enabled)
        assertFalse(controller.mBlurDisabledSetting)
        assertEquals(2, controller.notifications)
    }

    @Test fun powerThermalAndTunnelRestrictionsRemainNativeAcrossDisableAndRestore() {
        for (reason in 0..2) {
            val controller = NativeController(context)
            controller.powerSave = reason == 0
            controller.thermal = reason == 1
            controller.tunnel = reason == 2
            module(true); apply(controller)
            module(false); apply(controller)
            assertFalse(controller.mBlurDisabledSetting)
            assertFalse(controller.enabled)
            assertEquals(reason == 0, controller.powerSave)
            assertEquals(reason == 1, controller.thermal)
            assertEquals(reason == 2, controller.tunnel)
            controller.powerSave = false; controller.thermal = false; controller.tunnel = false
            synchronized(controller.mLock) { controller.updateBlurEnabled() }
            val updates = controller.updates
            apply(controller)
            assertTrue(controller.enabled)
            assertEquals(updates, controller.updates)
        }
    }

    @Test fun replacementControllersUseCurrentPolicyAndRepeatedRefreshDoesNotNotifyAgain() {
        module(true)
        val old = NativeController(context)
        apply(old)
        repeat(20) { apply(old) }
        assertEquals(1, old.notifications)
        assertEquals(1, old.updates)
        module(false)
        val replacement = NativeController(context)
        apply(replacement)
        assertTrue(replacement.enabled)
        assertEquals(0, replacement.notifications)
        assertFalse(old.enabled)
        apply(old)
        assertTrue(old.enabled)
    }

    @Test fun committedMirrorOverridesStaleCacheAndAnUnavailableReadKeepsCurrentPolicy() {
        val mirror = object : ContentProvider() {
            var value: Boolean? = true
            override fun onCreate() = true
            override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
                assertEquals(PrefsProvider.COMMITTED_WINDOW_BLUR, method)
                return value?.let { Bundle().apply { putBoolean(PrefsProvider.WINDOW_BLUR_DISABLED, it) } }
            }
            override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, order: String?): Cursor? = null
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<String>?) = 0
        }
        ShadowContentResolver.registerProviderInternal(PrefsProvider.AUTHORITY, mirror)
        module(false)
        assertTrue(SystemServerPreferenceInvalidation.committedWindowBlur(context,
            PrefsProvider.WINDOW_BLUR_DISABLED)[PrefsProvider.WINDOW_BLUR_DISABLED] as Boolean)
        module(true)
        mirror.value = null
        assertTrue(SystemServerPreferenceInvalidation.committedWindowBlur(context, null)
            [PrefsProvider.WINDOW_BLUR_DISABLED] as Boolean)
        mirror.value = false
        assertFalse(SystemServerPreferenceInvalidation.committedWindowBlur(context, null)
            [PrefsProvider.WINDOW_BLUR_DISABLED] as Boolean)
        assertTrue(SystemServerPreferenceInvalidation.committedWindowBlur(context, "unrelated").isEmpty())
    }
}
