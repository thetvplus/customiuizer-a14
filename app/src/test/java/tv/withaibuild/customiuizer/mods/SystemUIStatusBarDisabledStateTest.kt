package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedModuleInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.concurrent.CopyOnWriteArrayList

/**
 * QA-A14-C3 RED/VERIFIED tests for disabled B1/B2/B3 runtime state.
 *
 * These tests use reflection so that they can be compiled and run against the
 * old implementation (where the new holder fields do not yet exist) and still
 * produce meaningful RED evidence before production code is refactored.
 */
class SystemUIStatusBarDisabledStateTest {

    @Before
    fun setUp() {
        resetAllStatusBarRuntimeState()
    }

    @After
    fun tearDown() {
        resetAllStatusBarRuntimeState()
    }

    // --------------------------------------------------------------------------
    // A. Singleton loaded, B1/B2/B3 runtime feature state not installed.
    // --------------------------------------------------------------------------

    @Test
    fun allDisabled_netSpeedRuntimeState_isNull() {
        val field = getSystemUiStatusBarField("netSpeedRuntimeState")
        if (field != null) {
            assertNull("B1/B2 holder must be null when no net-speed feature is installed", field.get(SystemUIStatusBarHooks))
        }
        // Old implementation: the field does not exist, and the old eager fields are still present.
        assertNullField("currentNetSpeedTextStyleSnapshot")
        assertNullField("netSpeedSnapshotIdGenerator")
        assertNullField("netSpeedTextStyleRelevantKeys")
        assertNullField("netSpeedTextStyleObserver")
        assertNullField("currentDetailedNetSpeedFormatSnapshot")
        assertNullField("detailedNetSpeedFormatSnapshotIdGenerator")
        assertNullField("detailedNetSpeedFormatRelevantKeys")
    }

    @Test
    fun allDisabled_iconVisibilityRuntimeState_isNull() {
        val field = getSystemUiStatusBarField("iconVisibilityRuntimeState")
        if (field != null) {
            assertNull("B3 holder must be null when no icon-visibility feature is installed", field.get(SystemUIStatusBarHooks))
        }
        assertNullField("currentStatusBarIconVisibilitySnapshot")
        assertNullField("statusBarIconVisibilitySnapshotIdGenerator")
        assertNullField("statusBarIconVisibilityRelevantKeys")
        assertNullField("statusBarIconVisibilityObserver")
        assertNoClass("tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks\$StatusBarIconVisibilityObserverOwner")
    }

    @Test
    fun allDisabled_layoutRuntimeState_isNull() {
        val field = getSystemUiStatusBarField("layoutRuntimeState")
        if (field != null) {
            assertNull("layout holder must be null when no layout feature is installed", field.get(SystemUIStatusBarHooks))
        }
    }

    @Test
    fun allDisabled_noB1B2B3ObserverRegistrations() {
        // There must be no top-level observer fields.
        assertNullField("netSpeedTextStyleObserver")
        assertNullField("statusBarIconVisibilityObserver")
        // And no runtime-state observer instances in the registry.
        assertEquals("No B1/B2/B3 runtime-state observer should be registered when all are disabled", 0, countRuntimeStateObservers())
    }

    @Test
    fun allDisabled_b1FakeTagIdsNotEagerlyRegistered() {
        assertNullField("netspeedNumberViewTag")
        assertNullField("netspeedUnitViewTag")
        assertNullField("netspeedTypefaceStateTag")
        assertNullField("netspeedOriginalStyleStateTag")
    }

    // --------------------------------------------------------------------------
    // B-E. Per-feature state creation and shared observer semantics.
    // --------------------------------------------------------------------------

    @Test
    fun b1Only_netSpeedStyleStateExists_detailedAndB3DoNot() {
        SystemUIStatusBarHooks.NetSpeedStyleHook(fakePackageReadyParam())

        val netSpeedState = requireNetSpeedRuntimeState()
        assertNotNull("B1 styleState must exist after NetSpeedStyleHook", getFieldValue(netSpeedState, "styleState"))
        assertNull("B2 detailedState must not exist when only B1 is installed", getFieldValue(netSpeedState, "detailedState"))
        assertTrue("B3 holder must still be null", getSystemUiStatusBarField("iconVisibilityRuntimeState")?.get(SystemUIStatusBarHooks) == null)
    }

    @Test
    fun b2Only_detailedStateExists_styleAndB3DoNot() {
        SystemUIStatusBarHooks.DetailedNetSpeedHook(fakePackageReadyParam())

        val netSpeedState = requireNetSpeedRuntimeState()
        assertNotNull("B2 detailedState must exist after DetailedNetSpeedHook", getFieldValue(netSpeedState, "detailedState"))
        assertNull("B1 styleState must not exist when only B2 is installed", getFieldValue(netSpeedState, "styleState"))
        assertTrue("B3 holder must still be null", getSystemUiStatusBarField("iconVisibilityRuntimeState")?.get(SystemUIStatusBarHooks) == null)
    }

    @Test
    fun b3Only_iconVisibilityRuntimeStateExists_netSpeedDoesNot() {
        SystemUIStatusBarHooks.HideIconsSignalHook(fakePackageReadyParam())

        val b3State = requireIconVisibilityRuntimeState()
        assertNotNull(b3State)
        assertTrue("B1/B2 holder must still be null", getSystemUiStatusBarField("netSpeedRuntimeState")?.get(SystemUIStatusBarHooks) == null)
        assertTrue("layout holder must still be null", getSystemUiStatusBarField("layoutRuntimeState")?.get(SystemUIStatusBarHooks) == null)
    }

    @Test
    fun layoutHooks_shareOneHolderAndOneObserver() {
        SystemUIStatusBarHooks.installStatusBarLayoutSnapshot()
        SystemUIStatusBarHooks.installStatusBarLayoutSnapshot()
        SystemUIStatusBarHooks.installStatusBarLayoutSnapshot()

        val layoutState = getSystemUiStatusBarField("layoutRuntimeState")?.get(SystemUIStatusBarHooks)
            ?: fail("layoutRuntimeState must exist after a layout hook is installed")
        val observer = getFieldValue(layoutState, "observer") ?: fail("layout observer must exist")
        assertEquals("layout hooks must share one observer", 1, activeObserverCount(observer))
    }

    @Test
    fun b1AndB2_shareOneObserver() {
        SystemUIStatusBarHooks.DetailedNetSpeedHook(fakePackageReadyParam())
        SystemUIStatusBarHooks.NetSpeedStyleHook(fakePackageReadyParam())

        val netSpeedState = requireNetSpeedRuntimeState()
        assertNotNull(getFieldValue(netSpeedState, "styleState"))
        assertNotNull(getFieldValue(netSpeedState, "detailedState"))

        val observer = getFieldValue(netSpeedState, "observer") ?: fail("shared observer must exist")
        assertEquals("B1+B2 must register exactly one shared observer", 1, activeObserverCount(observer))
    }

    @Test
    fun b2AndB1_shareOneObserver() {
        SystemUIStatusBarHooks.NetSpeedStyleHook(fakePackageReadyParam())
        SystemUIStatusBarHooks.DetailedNetSpeedHook(fakePackageReadyParam())

        val netSpeedState = requireNetSpeedRuntimeState()
        val observer = getFieldValue(netSpeedState, "observer") ?: fail("shared observer must exist")
        assertEquals(1, activeObserverCount(observer))
    }

    @Test
    fun b3_allThreeHideFeaturesUseSameHolderAndOneObserver() {
        SystemUIStatusBarHooks.HideIconsSignalHook(fakePackageReadyParam())
        SystemUIStatusBarHooks.HideIconsHook(fakePackageReadyParam())
        SystemUIStatusBarHooks.HideIconsFromSystemManager(fakePackageReadyParam())

        val b3State = requireIconVisibilityRuntimeState()
        val observer = getFieldValue(b3State, "observer") ?: fail("B3 observer must exist")
        assertEquals("B3 must register exactly one observer regardless of how many hook functions are called", 1, activeObserverCount(observer))
    }

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return java.lang.reflect.Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "isFirstPackage" -> true
                "toString" -> "FakePackageReadyParam"
                "equals" -> false
                "hashCode" -> 0
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }

    private fun resetAllStatusBarRuntimeState() {
        // Unregister B1/B2 shared observer.
        try {
            PreferenceObserverRegistry.unregisterPreferenceObserver(SystemUIStatusBarHooks)
        } catch (t: Throwable) {
            // ignore if not registered
        }

        // Unregister B3 observer if a holder exists.
        getSystemUiStatusBarField("iconVisibilityRuntimeState")?.let { field ->
            (field.get(SystemUIStatusBarHooks))?.let { state ->
                try {
                    PreferenceObserverRegistry.unregisterPreferenceObserver(state)
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }

        getSystemUiStatusBarField("layoutRuntimeState")?.let { field ->
            (field.get(SystemUIStatusBarHooks))?.let { state ->
                try {
                    PreferenceObserverRegistry.unregisterPreferenceObserver(state)
                } catch (t: Throwable) {
                    // ignore
                }
            }
        }

        // Null out holders.
        getSystemUiStatusBarField("netSpeedRuntimeState")?.set(SystemUIStatusBarHooks, null)
        getSystemUiStatusBarField("iconVisibilityRuntimeState")?.set(SystemUIStatusBarHooks, null)
        getSystemUiStatusBarField("layoutRuntimeState")?.set(SystemUIStatusBarHooks, null)
    }

    private fun getSystemUiStatusBarField(name: String): Field? =
        try {
            SystemUIStatusBarHooks::class.java.getDeclaredField(name).apply { isAccessible = true }
        } catch (e: NoSuchFieldException) {
            null
        }

    private fun assertNullField(name: String) {
        val field = try {
            SystemUIStatusBarHooks::class.java.getDeclaredField(name).apply { isAccessible = true }
        } catch (e: NoSuchFieldException) {
            null
        }
        if (field != null) {
            fail("Old eager field '$name' must not be a top-level field after C3; found $field")
        }
    }

    private fun assertNoClass(className: String) {
        try {
            Class.forName(className)
            fail("Old eager owner object $className must not exist after C3")
        } catch (e: ClassNotFoundException) {
            // expected
        }
    }

    private fun requireNetSpeedRuntimeState(): Any {
        val f = getSystemUiStatusBarField("netSpeedRuntimeState")
            ?: error("netSpeedRuntimeState field must exist")
        return f.get(SystemUIStatusBarHooks)
            ?: error("netSpeedRuntimeState must not be null after a net-speed hook is installed")
    }

    private fun requireIconVisibilityRuntimeState(): Any {
        val f = getSystemUiStatusBarField("iconVisibilityRuntimeState")
            ?: error("iconVisibilityRuntimeState field must exist")
        return f.get(SystemUIStatusBarHooks)
            ?: error("iconVisibilityRuntimeState must not be null after a B3 hook is installed")
    }

    private fun getFieldValue(target: Any, name: String): Any? {
        val f = target::class.java.getDeclaredField(name).apply { isAccessible = true }
        return f.get(target)
    }

    private fun countRuntimeStateObservers(): Int {
        val owners = getObserverOwners() ?: return 0
        var count = 0
        for (ref in owners) {
            val observer = (ref as? WeakReference<*>)?.get() ?: continue
            val name = observer.javaClass.name
            if (name.contains("NetSpeedRuntimeState") ||
                name.contains("StatusBarIconVisibilityRuntimeState") ||
                name.contains("StatusBarLayoutRuntimeState")
            ) {
                count++
            }
        }
        return count
    }

    private fun activeObserverCount(observer: Any): Int {
        val owners = getObserverOwners() ?: return 0
        var count = 0
        for (ref in owners) {
            val obs = (ref as? WeakReference<*>)?.get()
            if (obs === observer) count++
        }
        return count
    }

    private fun getObserverOwners(): List<Any?>? {
        val field = try {
            PreferenceObserverRegistry::class.java.getDeclaredField("observerOwners").apply { isAccessible = true }
        } catch (e: NoSuchFieldException) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return field.get(PreferenceObserverRegistry) as? CopyOnWriteArrayList<Any?>
    }
}
