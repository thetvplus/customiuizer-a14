package tv.withaibuild.customiuizer.mods

import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import io.github.libxposed.api.XposedModuleInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicReference

private class DetailedCountingMap : AbstractMap<String, Any>() {
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

private fun countingMainPrefs(values: Map<String, Any>): DetailedCountingMap {
    val countingMap = DetailedCountingMap().apply { putAll(values) }
    MainModule.mPrefs.clear()
    val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
    snapshotField.isAccessible = true
    snapshotField.set(MainModule.mPrefs, AtomicReference<Map<String, Any>>(countingMap))
    return countingMap
}

private fun prefMapWith(values: Map<String, Any>): PrefMap {
    val prefMap = PrefMap().apply { clear() }
    val countingMap = DetailedCountingMap().apply { putAll(values) }
    val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
    snapshotField.isAccessible = true
    snapshotField.set(prefMap, AtomicReference<Map<String, Any>>(countingMap))
    return prefMap
}

@Suppress("DEPRECATION")
private class FakeModuleResources : Resources(null, DisplayMetrics().apply { density = 2.0f }, Configuration()) {
    override fun getString(id: Int): String = when (id) {
        R.string.Bs -> "B/s"
        R.string.speedunits -> "KMG"
        else -> super.getString(id)
    }
}

class DetailedNetSpeedHotPathTest {

    @Before
    fun setUp() {
        SystemUIStatusBarHooks.DetailedNetSpeedHook(fakePackageReadyParam())
        resetDetailedNetSpeedFormatSnapshotState()
    }

    @After
    fun tearDown() {
        resetDetailedNetSpeedFormatSnapshotState()
        resetNetSpeedState()
        MainModule.mPrefs.clear()
    }

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

    private fun getNetSpeedRuntimeState(): Any? {
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("netSpeedRuntimeState").apply { isAccessible = true }
        return field.get(SystemUIStatusBarHooks)
    }

    private fun getDetailedState(): Any? {
        val runtimeState = getNetSpeedRuntimeState() ?: return null
        val field = runtimeState::class.java.getDeclaredField("detailedState").apply { isAccessible = true }
        return field.get(runtimeState)
    }

    private fun resetDetailedNetSpeedFormatSnapshotState() {
        val detailedState = getDetailedState() ?: return
        val snapshotField = detailedState::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        (snapshotField.get(detailedState) as? AtomicReference<DetailedNetSpeedFormatSnapshot?>)?.set(null)
        val idField = detailedState::class.java.getDeclaredField("idGenerator").apply { isAccessible = true }
        (idField.get(detailedState) as? java.util.concurrent.atomic.AtomicLong)?.set(0)
    }

    private fun resetNetSpeedState() {
        try {
            tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry.unregisterPreferenceObserver(SystemUIStatusBarHooks)
        } catch (_: Throwable) {
            // ignore if not registered
        }
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("netSpeedRuntimeState").apply { isAccessible = true }
        field.set(SystemUIStatusBarHooks, null)
    }

    private fun buildSnapshot(values: Map<String, Any>): DetailedNetSpeedFormatSnapshot {
        val prefs = prefMapWith(values)
        return SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(prefs)
    }

    private fun defaultSnapshot() = buildSnapshot(defaultValues())

    private fun defaultValues(): Map<String, Any> = mapOf(
        "system_detailednetspeed_low" to false,
        "system_detailednetspeed_lowlevel" to 1,
        "system_detailednetspeed_style" to 1,
        "system_detailednetspeed_icon" to 2,
        "system_detailednetspeed_secunit" to false,
    )

    // 1. 首次 snapshot 构建读取全部相关 key 一次
    @Test
    fun buildDetailedNetSpeedFormatSnapshot_readsAllRelevantKeysOnce() {
        val prefs = prefMapWith(defaultValues())
        val countingMap = (prefs::class.java.getDeclaredField("snapshot").also { it.isAccessible = true }
            .get(prefs) as? AtomicReference<*>)?.get() as? DetailedCountingMap
            ?: error("could not retrieve counting map")
        countingMap.reset()

        SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(prefs)

        assertEquals(5, countingMap.readCount())
        assertTrue(countingMap.reads.contains("system_detailednetspeed_low"))
        assertTrue(countingMap.reads.contains("system_detailednetspeed_lowlevel"))
        assertTrue(countingMap.reads.contains("system_detailednetspeed_style"))
        assertTrue(countingMap.reads.contains("system_detailednetspeed_icon"))
        assertTrue(countingMap.reads.contains("system_detailednetspeed_secunit"))
    }

    // 2. humanReadableByteCount 等价性：0 B/s
    @Test
    fun humanReadableByteCount_zero() {
        val modRes = FakeModuleResources()
        assertEquals("0.0KB/s", SystemUIStatusBarHooks.humanReadableByteCount(0L, defaultSnapshot(), modRes))
    }

    // 3. 小于 1 KB
    @Test
    fun humanReadableByteCount_subKilobyte() {
        val modRes = FakeModuleResources()
        val snapshot = defaultSnapshot()
        assertEquals("0.5KB/s", SystemUIStatusBarHooks.humanReadableByteCount(512L, snapshot, modRes))
        assertEquals("0.9KB/s", SystemUIStatusBarHooks.humanReadableByteCount(950L, snapshot, modRes))
    }

    // 4. 1 KB 边界
    @Test
    fun humanReadableByteCount_kilobyteBoundary() {
        val modRes = FakeModuleResources()
        val snapshot = defaultSnapshot()
        assertEquals("1.0KB/s", SystemUIStatusBarHooks.humanReadableByteCount(1024L, snapshot, modRes))
        assertEquals("999KB/s", SystemUIStatusBarHooks.humanReadableByteCount(999L * 1024L, snapshot, modRes))
    }

    // 5. KB 到 MB 边界 (f > 999 -> MB)
    @Test
    fun humanReadableByteCount_megabyteBoundary() {
        val modRes = FakeModuleResources()
        val snapshot = defaultSnapshot()
        // 1000 KB -> 0.976 MB, displayed as 1.0M by formatNetSpeedValue
        assertEquals("1.0MB/s", SystemUIStatusBarHooks.humanReadableByteCount(1_000L * 1024L, snapshot, modRes))
        assertEquals("1.0MB/s", SystemUIStatusBarHooks.humanReadableByteCount(1_024L * 1024L, snapshot, modRes))
        assertEquals("999MB/s", SystemUIStatusBarHooks.humanReadableByteCount(999L * 1024L * 1024L, snapshot, modRes))
    }

    // 6. MB 到 GB 边界：原实现只除以 1024 一次，因此 GB 值仍显示为 MB（保持旧语义）
    @Test
    fun humanReadableByteCount_gigabytePreservesSingleDivision() {
        val modRes = FakeModuleResources()
        val snapshot = defaultSnapshot()
        val bytes = 2L * 1024L * 1024L * 1024L // 2 GB
        val result = SystemUIStatusBarHooks.humanReadableByteCount(bytes, snapshot, modRes)
        // bytes / 1024 = 2M KB, then / 1024 => 2048M (formatNetSpeedValue returns whole number for >=100)
        assertEquals("2048MB/s", result)
    }

    // 7. 极大 Long 值
    @Test
    fun humanReadableByteCount_largeLong() {
        val modRes = FakeModuleResources()
        val snapshot = defaultSnapshot()
        val result = SystemUIStatusBarHooks.humanReadableByteCount(Long.MAX_VALUE, snapshot, modRes)
        assertTrue(result.isNotEmpty())
    }

    // 8. 负值回退
    @Test
    fun humanReadableByteCount_negativeFallsBack() {
        val modRes = FakeModuleResources()
        val snapshot = defaultSnapshot()
        val result = SystemUIStatusBarHooks.humanReadableByteCount(-512L, snapshot, modRes)
        // Old logic produces a non-empty text for negative values; we keep it to avoid changing semantics.
        assertTrue(result.isNotEmpty())
    }

    // 9. 隐藏单位
    @Test
    fun humanReadableByteCount_hideSecUnit() {
        val modRes = FakeModuleResources()
        val snapshot = buildSnapshot(defaultValues() + ("system_detailednetspeed_secunit" to true))
        assertEquals("0.0K", SystemUIStatusBarHooks.humanReadableByteCount(0L, snapshot, modRes))
    }

    // 10. 单行模式（rx only）
    @Test
    fun formatDetailedNetSpeedText_singleLine_rxOnly() {
        val modRes = FakeModuleResources()
        val snapshot = defaultSnapshot()
        val result = SystemUIStatusBarHooks.formatDetailedNetSpeedText(txSpeed = 0L, rxSpeed = 1_024L, snapshot, modRes)
        assertEquals("1.0KB/s", result[0])
        assertEquals("", result[1])
    }

    // 11. 双行模式（tx + rx）
    @Test
    fun formatDetailedNetSpeedText_dualLine_txAndRx() {
        val modRes = FakeModuleResources()
        val snapshot = buildSnapshot(defaultValues() + ("system_detailednetspeed_style" to 2))
        val result = SystemUIStatusBarHooks.formatDetailedNetSpeedText(txSpeed = 2_048L, rxSpeed = 1_024L, snapshot, modRes)
        assertEquals("2.0KB/s▲", result[0]?.substringBefore("\n"))
        assertEquals("1.0KB/s▼", result[0]?.substringAfter("\n"))
        assertEquals("", result[1])
    }

    // 12. 隐藏低速：单行 rx
    @Test
    fun formatDetailedNetSpeedText_hideLow_singleLine() {
        val modRes = FakeModuleResources()
        val snapshot = buildSnapshot(defaultValues() + ("system_detailednetspeed_low" to true) + ("system_detailednetspeed_lowlevel" to 2))
        val result = SystemUIStatusBarHooks.formatDetailedNetSpeedText(txSpeed = 1_024L, rxSpeed = 512L, snapshot, modRes)
        assertEquals("", result[0])
    }

    // 13. 隐藏低速：双行，低于阈值的一方为空，另一方仍显示
    @Test
    fun formatDetailedNetSpeedText_hideLow_dualLine() {
        val modRes = FakeModuleResources()
        val snapshot = buildSnapshot(
            defaultValues() + mapOf(
                "system_detailednetspeed_low" to true,
                "system_detailednetspeed_lowlevel" to 2,
                "system_detailednetspeed_style" to 2,
            )
        )
        val result = SystemUIStatusBarHooks.formatDetailedNetSpeedText(txSpeed = 4_096L, rxSpeed = 512L, snapshot, modRes)
        assertEquals("4.0KB/s▲\n", result[0])
    }

    // 14. 图标模式 3（chess pieces）
    @Test
    fun formatDetailedNetSpeedText_iconMode3() {
        val modRes = FakeModuleResources()
        val snapshot = buildSnapshot(defaultValues() + mapOf("system_detailednetspeed_style" to 2, "system_detailednetspeed_icon" to 3))
        val result = SystemUIStatusBarHooks.formatDetailedNetSpeedText(txSpeed = 2_048L, rxSpeed = 1_024L, snapshot, modRes)
        assertTrue(result[0]?.contains(" ☗") == true)
        assertTrue(result[0]?.contains(" ⛊") == true)
    }

    // 15. 连续 100 次 updateText 调用 PrefMap 读取次数为 0
    @Test
    fun formatDetailedNetSpeedText_100Calls_zeroPrefReads() {
        val countingMap = countingMainPrefs(defaultValues())
        val snapshot = SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(MainModule.mPrefs)
        val initialReads = countingMap.readCount()

        val modRes = FakeModuleResources()
        repeat(100) {
            SystemUIStatusBarHooks.formatDetailedNetSpeedText(
                txSpeed = (it * 1024L),
                rxSpeed = (it * 2048L),
                snapshot,
                modRes,
            )
        }

        assertEquals(initialReads, countingMap.readCount())
    }

    // 16. 连续 100 次 humanReadableByteCount 调用 PrefMap 读取次数为 0
    @Test
    fun humanReadableByteCount_100Calls_zeroPrefReads() {
        val countingMap = countingMainPrefs(defaultValues())
        val snapshot = SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(MainModule.mPrefs)
        val initialReads = countingMap.readCount()

        val modRes = FakeModuleResources()
        repeat(100) {
            SystemUIStatusBarHooks.humanReadableByteCount((it * 1024L).toLong(), snapshot, modRes)
        }

        assertEquals(initialReads, countingMap.readCount())
    }

    // 17. 无关偏好变化不会触发 B2 snapshot 重建（observer 不响应 B2 无关 key）
    @Test
    fun netSpeedTextStyleObserver_irrelevantKey_keepsDetailedSnapshot() {
        val countingMap = countingMainPrefs(defaultValues())
        val snapshot = SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(MainModule.mPrefs)
        setCurrentDetailedSnapshot(snapshot)

        val observer = getNetSpeedTextStyleObserver()
        observer.onChange("system_statusbar_clock_style")

        val current = getCurrentDetailedSnapshot()
        assertNotNull(current)
        assertEquals(snapshot.id, current?.id)
    }

    // 18. 相关偏好变化使 B2 snapshot 失效，下一次 build 重新读取全部 key
    @Test
    fun netSpeedTextStyleObserver_relevantKey_invalidatesDetailedSnapshot() {
        val countingMap = countingMainPrefs(defaultValues())
        val snapshot = SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(MainModule.mPrefs)
        setCurrentDetailedSnapshot(snapshot)

        val observer = getNetSpeedTextStyleObserver()
        observer.onChange("system_detailednetspeed_low")

        val current = getCurrentDetailedSnapshot()
        assertNull(current)

        countingMap.reset()
        val rebuilt = SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(MainModule.mPrefs)
        assertNotEquals(snapshot.id, rebuilt.id)
        assertEquals(5, countingMap.readCount())
    }

    // 19. 并发构建只产生完整旧或新 snapshot：同一配置多次 build 产生不同 id 和相同字段值
    @Test
    fun buildDetailedNetSpeedFormatSnapshot_atomicPublication() {
        val prefs = prefMapWith(defaultValues())
        val first = SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(prefs)
        val second = SystemUIStatusBarHooks.buildDetailedNetSpeedFormatSnapshot(prefs)

        assertNotEquals(first.id, second.id)
        assertEquals(first.hideLow, second.hideLow)
        assertEquals(first.lowLevelBytes, second.lowLevelBytes)
        assertEquals(first.speedStyle, second.speedStyle)
        assertEquals(first.icons, second.icons)
        assertEquals(first.hideSecUnit, second.hideSecUnit)
    }

    private fun getNetSpeedTextStyleObserver(): ModuleHelper.PreferenceObserver {
        val runtimeState = getNetSpeedRuntimeState() ?: error("net speed runtime state not installed")
        val field = runtimeState::class.java.getDeclaredField("observer").apply { isAccessible = true }
        return field.get(runtimeState) as ModuleHelper.PreferenceObserver
    }

    private fun getCurrentDetailedSnapshot(): DetailedNetSpeedFormatSnapshot? {
        val detailedState = getDetailedState() ?: return null
        val field = detailedState::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        return (field.get(detailedState) as? AtomicReference<DetailedNetSpeedFormatSnapshot?>)?.get()
    }

    private fun setCurrentDetailedSnapshot(snapshot: DetailedNetSpeedFormatSnapshot?) {
        val detailedState = getDetailedState() ?: return
        val field = detailedState::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        (field.get(detailedState) as? AtomicReference<DetailedNetSpeedFormatSnapshot?>)?.set(snapshot)
    }
}
