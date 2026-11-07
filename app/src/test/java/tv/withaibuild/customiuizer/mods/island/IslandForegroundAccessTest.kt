package tv.withaibuild.customiuizer.mods.island

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

    @Test fun absentCatchUpAccessorKeepsNativeUpdatesAndTheLastObservedForeground() {
        val loader = object : ClassLoader(javaClass.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == "com.miui.systemui.MiuiDependency") throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        val access = IslandForegroundAccess(loader)
        val observer = com.miui.systemui.functions.MiuiTopActivityObserver()
        observer.mTopActivity = ComponentName("clock.example", "Clock")
        assertEquals(2, access.updates.size)
        assertEquals("clock.example", access.packageName(observer))
        assertEquals("clock.example", access.currentPackage(access.packageName(observer)))
        observer.mTopActivity = ComponentName("launcher.example", "Home")
        assertEquals("launcher.example", access.packageName(observer))
    }

    @Test fun bindingCatchesUpFromTheExistingNativeSingleton() {
        val native = NativeObserver().apply { mTopActivity = ComponentName("clock.example", "Clock") }
        NativeDependency.owner = native
        NativeDependency.calls = 0
        val access = IslandForegroundAccess(NativeObserver::class.java,
            NativeDependency::class.java.getDeclaredMethod("get", Class::class.java).apply { isAccessible = true })
        assertEquals("clock.example", access.currentPackage())
        assertEquals(1, NativeDependency.calls)
        assertEquals(0, native.nativeCalls)
        native.mTopActivity = ComponentName("launcher.example", "Home")
        assertEquals("launcher.example", access.packageName(native))
        assertEquals("No native dependency queries on subsequent updates", 1, NativeDependency.calls)
        native.mTopActivity = null
        assertNull("A present owner can explicitly clear foreground", access.currentPackage("clock.example"))
        NativeDependency.owner = null
        assertEquals("clock.example", access.currentPackage("clock.example"))
    }

    private object NativeDependency {
        var owner: Any? = null
        var calls = 0
        @JvmStatic fun get(type: Class<*>): Any? { assertEquals(NativeObserver::class.java, type); calls++; return owner }
    }

    private class NativeObserver {
        @JvmField @Volatile var mTopActivity: ComponentName? = null
        var nativeCalls = 0
        fun updateTopActivity() { nativeCalls++ }
        fun notifyListeners() { nativeCalls++ }
    }

    private class WrongField { @JvmField var mTopActivity: String? = null }
}
