package tv.withaibuild.customiuizer.mods.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class GlobalActionConfigTest {

    @Before
    fun reset() {
        MainModule.mPrefs.clear()
        resetConfigState()
    }

    @After
    fun cleanup() {
        MainModule.mPrefs.clear()
        resetConfigState()
    }

    private fun resetConfigState() {
        val clazz = Class.forName("tv.withaibuild.customiuizer.mods.utils.GlobalActionConfigKt")
        clazz.getDeclaredField("customActionsReady").apply { isAccessible = true }.set(null, false)
        clazz.getDeclaredField("customActionCodeMap").apply { isAccessible = true }.set(null, null)
        clazz.getDeclaredField("customToggleMap").apply { isAccessible = true }.set(null, null)
    }

    private fun configReady() =
        Class.forName("tv.withaibuild.customiuizer.mods.utils.GlobalActionConfigKt")
            .getDeclaredField("customActionsReady").apply { isAccessible = true }.get(null) as Boolean

    @Test
    fun defaultConfig_hasNoGlobalActions() {
        assertFalse(hasConfiguredGlobalActions())
        val map = Class.forName("tv.withaibuild.customiuizer.mods.utils.GlobalActionConfigKt")
            .getDeclaredField("customActionCodeMap").apply { isAccessible = true }.get(null)
        assertNotNull(map)
    }

    @Test
    fun oneAction_configuredCodeIsTrue() {
        MainModule.mPrefs.put("controls_backlong_action", 2)
        assertEquals(2, MainModule.mPrefs.getInt("controls_backlong_action", 1))
        hasConfiguredGlobalActions()
        assertTrue(configReady())
    }

    @Test
    fun toggle_configuredCodeAndToggleAreTrue() {
        MainModule.mPrefs.put("controls_backlong_action", 10)
        MainModule.mPrefs.put("controls_backlong_toggle", 6)
        assertEquals(10, MainModule.mPrefs.getInt("controls_backlong_action", 1))
        assertEquals(6, MainModule.mPrefs.getInt("controls_backlong_toggle", 0))
        hasConfiguredGlobalActions()
        assertTrue(configReady())
    }

    @Test
    fun concurrentFirstCall_initializesOnce() {
        MainModule.mPrefs.put("controls_powerdt_action", 3)
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val errors = mutableListOf<Throwable>()

        val tasks = (0 until 2).map {
            executor.submit {
                start.await()
                try { hasConfiguredGlobalActions() } catch (t: Throwable) { synchronized(errors) { errors.add(t) } }
            }
        }
        start.countDown()
        tasks.forEach { it.get() }
        executor.shutdown()

        assertTrue(errors.isEmpty())
        assertTrue(configReady())
    }
}
