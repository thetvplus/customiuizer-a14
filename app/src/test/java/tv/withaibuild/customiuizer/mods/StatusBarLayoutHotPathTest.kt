package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedModuleInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private class LayoutCountingMap : AbstractMap<String, Any>() {
    private val delegate = HashMap<String, Any>()
    val reads = mutableListOf<String>()

    override val entries: Set<Map.Entry<String, Any>> get() = delegate.entries
    override val size: Int get() = delegate.size

    fun putAll(values: Map<String, Any>) = delegate.putAll(values)

    override operator fun get(key: String): Any? {
        reads.add(key)
        return delegate[key]
    }

    fun reset() = reads.clear()
    fun readCount(): Int = reads.size
}

class StatusBarLayoutHotPathTest {

    @Before
    fun setUp() {
        resetLayoutState()
        SystemUIStatusBarHooks.installStatusBarLayoutSnapshot()
    }

    @After
    fun tearDown() {
        resetLayoutState()
        MainModule.mPrefs.clear()
    }

    @Test
    fun buildSnapshot_readsEachLayoutKeyOnce() {
        val values = layoutKeys().associateWith { defaultFor(it) }.toMutableMap()
        values["system_statusbar_dualrows_firstrow_horizmargin"] = true
        values["system_statusbar_batterytempandcurrent"] = true
        values["system_statusbar_showdevicetemperature"] = true
        val countingMap = LayoutCountingMap().apply { putAll(values) }
        val prefs = PrefMap().apply { clear() }
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(countingMap))

        SystemUIStatusBarHooks.buildStatusBarLayoutSnapshot(prefs)

        layoutKeys().forEach { assertTrue("Expected read for $it", it in countingMap.reads) }
        assertEquals(layoutKeys().size, countingMap.reads.toSet().size)
    }

    @Test
    fun hotHelpers_100Calls_zeroPrefReads() {
        val snapshot = SystemUIStatusBarHooks.buildStatusBarLayoutSnapshot(MainModule.mPrefs)
        val countingMap = LayoutCountingMap()
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(MainModule.mPrefs, AtomicReference<Map<String, Any>>(countingMap))
        countingMap.reset()

        repeat(100) { index ->
            val name = resolveMobileTypeDisplayName(if (index % 2 == 0) "4G" else "4G+", snapshot)
            assertTrue(name == "LTE" || name == "LTE+" || name.isEmpty() || name == snapshot.mobileShownName)
            assertEquals(
                if (snapshot.digitalSignalHideUnit) "12" else "12dBm",
                formatDigitalSignalLabel(12, snapshot.digitalSignalHideUnit),
            )
            assertEquals(snapshot.dualRowsLeftRatio, snapshot.dualRowsLeftRatio)
            assertEquals(snapshot.netSpeedIntervalMs, snapshot.netSpeedIntervalMs)
        }

        assertEquals(0, countingMap.readCount())
    }

    @Test
    fun convert4gToLte_mapsFourGLabels() {
        val snapshot = sampleSnapshot(convert4gToLte = true)
        assertEquals("LTE", resolveMobileTypeDisplayName("4G", snapshot))
        assertEquals("LTE+", resolveMobileTypeDisplayName("4G+", snapshot))
        assertEquals("5G", resolveMobileTypeDisplayName("5G", snapshot))
    }

    @Test
    fun customMobileName_usedWhenFourGConversionOff() {
        val snapshot = sampleSnapshot(convert4gToLte = false, mobileShownName = "NR")
        assertEquals("NR", resolveMobileTypeDisplayName("4G", snapshot))
    }

    @Test
    fun observerRebuildsSnapshotWithoutInstall() {
        val first = SystemUIStatusBarHooks.buildStatusBarLayoutSnapshot(MainModule.mPrefs)
        val state = layoutState()
        val current = layoutSnapshotRef()
        current.set(first)
        val generator = layoutIdGenerator()
        val before = generator.get()

        val observer = state.javaClass.getDeclaredField("observer").apply { isAccessible = true }.get(state)
            as tv.withaibuild.customiuizer.mods.utils.ModuleHelper.PreferenceObserver
        observer.onChange("system_netspeedinterval")

        val second = current.get()
        assertTrue(second != null && second.id > before)
        assertFalse(second === first)
    }

    private fun layoutKeys(): List<String> = listOf(
        "system_statusbar_dualrows_firstrow_horizmargin",
        "system_statusbar_dualrows_firstrow_horizmargin_left",
        "system_statusbar_dualrows_firstrow_horizmargin_right",
        "system_statusbar_dualrows_clock_span2rows",
        "system_statusbar_batterytempandcurrent",
        "system_statusbar_showdevicetemperature",
        "system_statusbar_batterytempandcurrent_atright",
        "system_statusbar_showdevicetemperature_atright",
        "system_statusbar_netspeed_atsecondrow",
        "system_statusbar_dualrows_left_ratio",
        "system_statusbar_mobile_digital_signal_in2rows",
        "system_statusbar_mobile_digital_signal_hideunit",
        "system_netspeedinterval",
        "system_4gtolte",
        "system_statusbar_mobile_showname",
        "system_statusbar_mobiletype_single_atleft",
        "system_statusbar_mobiletype_single_leftmargin",
        "system_statusbar_mobiletype_single_rightmargin",
        "system_statusbar_mobiletype_single_verticaloffset",
        "system_statusbar_mobiletype_single_fontsize",
        "system_statusbar_mobiletype_single_bold",
        "system_statusbar_horizmargin_left",
        "system_statusbar_horizmargin_right",
        "system_mobiletypeicon",
        "system_networkindicator_mobile",
        "system_statusbar_mobiletype_single",
        "system_statusbar_mobiletype_show_wificonnected",
    )

    private fun defaultFor(key: String): Any = when {
        key.endsWith("_ratio") -> 4
        key.endsWith("_leftmargin") || key.endsWith("_rightmargin") -> 0
        key.endsWith("interval") -> 4
        key.endsWith("fontsize") -> 27
        key.endsWith("verticaloffset") -> 8
        key.endsWith("showname") -> ""
        key.endsWith("mobiletypeicon") -> "1"
        key.endsWith("horizmargin_left") || key.endsWith("horizmargin_right") -> 16
        else -> false
    }

    private fun sampleSnapshot(
        convert4gToLte: Boolean,
        mobileShownName: String = "",
    ): StatusBarLayoutSnapshot {
        val idGenerator = AtomicLong(1L)
        val prefs = PrefMap().apply { clear() }
        val values = layoutKeys().associateWith { defaultFor(it) }.toMutableMap()
        values["system_4gtolte"] = convert4gToLte
        values["system_statusbar_mobile_showname"] = mobileShownName
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(values))
        // Build through production so field mapping stays in one place.
        MainModule.mPrefs.clear()
        snapshotField.set(MainModule.mPrefs, AtomicReference<Map<String, Any>>(values))
        val built = SystemUIStatusBarHooks.buildStatusBarLayoutSnapshot(MainModule.mPrefs)
        return built.copy(id = idGenerator.get(), convert4gToLte = convert4gToLte, mobileShownName = mobileShownName)
    }

    private fun layoutState(): Any {
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("layoutRuntimeState").apply { isAccessible = true }
        return field.get(SystemUIStatusBarHooks) ?: error("layoutRuntimeState missing")
    }

    private fun layoutSnapshotRef(): AtomicReference<StatusBarLayoutSnapshot?> {
        val state = layoutState()
        @Suppress("UNCHECKED_CAST")
        return state.javaClass.getDeclaredField("currentSnapshot").apply { isAccessible = true }
            .get(state) as AtomicReference<StatusBarLayoutSnapshot?>
    }

    private fun layoutIdGenerator(): AtomicLong {
        val state = layoutState()
        return state.javaClass.getDeclaredField("idGenerator").apply { isAccessible = true }.get(state) as AtomicLong
    }

    private fun resetLayoutState() {
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("layoutRuntimeState").apply { isAccessible = true }
        val state = field.get(SystemUIStatusBarHooks)
        if (state != null) {
            try {
                PreferenceObserverRegistry.unregisterPreferenceObserver(state)
            } catch (_: Throwable) {
            }
        }
        field.set(SystemUIStatusBarHooks, null)
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return java.lang.reflect.Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java),
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
}
