package tv.withaibuild.customiuizer.mods

import android.content.ComponentName
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IslandForegroundAccessTest {
    @Test fun cachedFieldReadsFollowNativeStateWithoutInvokingNativeQueriesOrKeepingOwners() {
        val access = IslandForegroundAccess(NativeObserver::class.java)
        val first = NativeObserver()
        assertNull(access.packageName(first))
        first.mTopActivity = ComponentName("music.app", "Player")
        assertEquals("music.app", access.packageName(first))
        first.mTopActivity = ComponentName("com.miui.home", "Launcher")
        assertEquals("com.miui.home", access.packageName(first))
        val replacement = NativeObserver().apply { mTopActivity = ComponentName("chat.app", "Chat") }
        assertEquals("chat.app", access.packageName(replacement))
        assertEquals("com.miui.home", access.packageName(first))
        first.mTopActivity = null
        assertNull(access.packageName(first))
        assertNull(access.packageName(null))
        assertEquals(0, first.nativeCalls + replacement.nativeCalls)
        assertEquals(listOf("updateTopActivity", "notifyListeners"), access.updates.map { it.name })
        assertFalse(access.javaClass.declaredFields.any { field ->
            field.isAccessible = true
            field.get(access) === first || field.get(access) === replacement
        })
    }

    @Test fun incompatibleNativeFieldContractsAreRejectedBeforeHookInstallation() {
        assertThrows(IllegalArgumentException::class.java) { IslandForegroundAccess(WrongField::class.java) }
    }

    private class NativeObserver {
        @JvmField @Volatile var mTopActivity: ComponentName? = null
        var nativeCalls = 0
        fun updateTopActivity() { nativeCalls++ }
        fun notifyListeners() { nativeCalls++ }
    }

    private class WrongField { @JvmField var mTopActivity: String? = null }
}
