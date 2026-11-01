package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class NotificationSlideListTest {
    private val old = SystemWindowHooks.windowConfig
    @After fun restore() { SystemWindowHooks.windowConfig = old }
    class Settings { @JvmField val mAllowNotificationSlide = arrayListOf<String>() }
    class Manager { @JvmField val notificationSettingsManager = Settings() }
    class Row {
        val manager = Manager()
        fun getMiniWindowTargetPkg() = "synthetic.example"
        fun getMAppMiniWindowManager() = manager
    }

    @Test fun repeatedUpdatesKeepOneEntryAndPreserveWhitelistPriority() {
        val pkg = "synthetic.example"
        SystemWindowHooks.windowConfig = old.copy(floatApps = setOf(pkg), floatAppsBlack = setOf(pkg))
        val row = Row()
        var calls = 0
        val result = Any()
        val chain = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.Chain::class.java)) { _, method, _ ->
            when (method.name) {
                "getThisObject" -> row
                "proceed" -> { calls++; result }
                else -> error("Unexpected chain access: ${method.name}")
            }
        } as XposedInterface.Chain
        val hook = SystemWindowHooks.notificationSlideHook()
        repeat(4) { assertSame(result, hook.intercept(chain)) }
        assertEquals(4, calls)
        assertEquals(listOf(pkg), row.manager.notificationSettingsManager.mAllowNotificationSlide)
        SystemWindowHooks.windowConfig = old.copy(floatAppsBlack = setOf(pkg))
        assertSame(result, hook.intercept(chain))
        assertTrue(row.manager.notificationSettingsManager.mAllowNotificationSlide.isEmpty())
        assertEquals(5, calls)
    }
}
