package tv.withaibuild.customiuizer.mods

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.SecureQsReopener
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class PassiveUiOwnerTest {
    private var prefs = emptyMap<String, Any>()
    @Before fun setup() { prefs = MainModule.mPrefs.getAll(); MainModule.mPrefs.clear(); LauncherIconHooks.refreshIconStyleSnapshot() }
    @After fun tearDown() { MainModule.mPrefs.replaceSnapshot(prefs); LauncherIconHooks.refreshIconStyleSnapshot(); SystemUILockScreenHooks.refreshLockScreenConfig() }
    class LauncherActivity : Activity() { @JvmField val mAllLoadedShortcut = HashSet<Shortcut>() }
    class Shortcut(@JvmField val user: UserHandle) {
        @JvmField var mLabel: CharSequence = "Original"
        var updates = 0
        fun isApplicatoin() = true
        fun getPackageName() = "example.app"
        fun getClassName() = "example.app.Main"
        fun updateBuddyIconView(owner: Activity) { assertSame(Looper.getMainLooper(), Looper.myLooper()); updates++ }
    }
    @Test fun renameUpdatesAllMatchingIconsOnTheMainThreadAndBulkRestoreReturnsOriginalTitles() {
        val activity = Robolectric.buildActivity(LauncherActivity::class.java).setup()
        val owner = activity.get(); val user = UserHandle.getUserHandleForUid(1000001)
        val icons = List(2) { Shortcut(user).also { XposedHelpers.setAdditionalInstanceField(it, "mLabelOrig", "Original") } }
        owner.mAllLoadedShortcut.addAll(icons)
        val key = "launcher_renameapps_list:example.app|example.app.Main|${user.hashCode()}"
        MainModule.mPrefs.put(key, "Renamed"); LauncherIconHooks.refreshIconStyleSnapshot(key)
        val thread = Thread { LauncherIconHooks.updateRenamedShortcuts(owner, key, "com.miui.home") }
        thread.start(); thread.join()
        assertTrue(icons.all { it.mLabel == "Original" && it.updates == 0 })
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(icons.all { it.mLabel == "Renamed" && it.updates == 1 })
        MainModule.mPrefs.clear(); LauncherIconHooks.refreshIconStyleSnapshot()
        LauncherIconHooks.updateRenamedShortcuts(owner, null, "com.miui.home")
        assertTrue(icons.all { it.mLabel == "Original" && it.updates == 2 })
        activity.pause().stop().destroy()
        MainModule.mPrefs.put(key, "Stale"); LauncherIconHooks.refreshIconStyleSnapshot(key)
        LauncherIconHooks.updateRenamedShortcuts(owner, key, "com.miui.home")
        assertTrue(icons.all { it.mLabel == "Original" && it.updates == 2 })
    }
    @Test fun fontAndMarginChangesReuseTheRenameMapAndDeletedRenameKeysDisappear() {
        val key = "launcher_renameapps_list:app|Main|0"
        MainModule.mPrefs.put(key, "Custom"); LauncherIconHooks.refreshIconStyleSnapshot(key)
        val titles = LauncherIconHooks.iconStyleConfig.renameTitles
        MainModule.mPrefs.put("launcher_titlefontsize", 14); LauncherIconHooks.refreshIconStyleSnapshot("launcher_titlefontsize")
        assertSame(titles, LauncherIconHooks.iconStyleConfig.renameTitles)
        assertEquals(14f, LauncherIconHooks.iconStyleConfig.titleFontSizeSp)
        MainModule.mPrefs.remove(key); LauncherIconHooks.refreshIconStyleSnapshot(key)
        assertFalse(LauncherIconHooks.iconStyleConfig.renameTitles.containsKey(key))
    }
    class TorchOwner { var updates = 0; fun updateLeftIcon() { updates++ } }
    @Test fun torchObserverActsOnlyOnRealChangesWhileItsOwnOptionIsEnabled() {
        val owner = TorchOwner(); val observer = SystemUILockScreenHooks.torchStateObserver(owner, Handler(Looper.getMainLooper()))
        SystemUILockScreenHooks.lockScreenConfig = SystemUILockScreenHooks.lockScreenConfig.copy(leftTapAction = true)
        observer.onChange(true); assertEquals(0, owner.updates)
        observer.onChange(false); assertEquals(1, owner.updates)
        SystemUILockScreenHooks.lockScreenConfig = SystemUILockScreenHooks.lockScreenConfig.copy(leftTapAction = false)
        observer.onChange(false); assertEquals(1, owner.updates)
    }
    @Test fun secureQsReopenKeepsOnlyTheLatestCompletedAttemptAndDoesNotRepeat() {
        var opens = 0
        val controller = SecureQsReopener(Handler(Looper.getMainLooper()), { true }, { opens++ })
        val old = controller.begin { true }; val current = controller.begin { true }
        old.run(); current.run(); current.run()
        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
        assertEquals(1, opens)
        current.run(); shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
        assertEquals(1, opens)
        val first = controller.begin { true }; first.run()
        controller.begin { true }
        shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
        assertEquals(1, opens)
    }
    @Test fun relockAndPreferenceChangesCancelAQueuedSecureQsReopen() {
        for (relock in listOf(false, true)) {
            var unlocked = true; var allowed = true; var opens = 0
            val controller = SecureQsReopener(Handler(Looper.getMainLooper()), { unlocked }, { opens++ })
            controller.begin { allowed }.run()
            if (relock) unlocked = false else allowed = false
            shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
            assertEquals(0, opens)
        }
    }
}
