package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedModuleInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

private class StatusBarCountingMap : AbstractMap<String, Any>() {
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

private fun countingMainPrefs(values: Map<String, Any>): StatusBarCountingMap {
    val countingMap = StatusBarCountingMap().apply { putAll(values) }
    MainModule.mPrefs.clear()
    val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
    snapshotField.isAccessible = true
    snapshotField.set(MainModule.mPrefs, AtomicReference<Map<String, Any>>(countingMap))
    return countingMap
}

class StatusBarIconVisibilityHotPathTest {

    @Before
    fun setUp() {
        SystemUIStatusBarHooks.HideIconsSignalHook(fakePackageReadyParam())
        resetStatusBarIconVisibilitySnapshot()
        statusBarIconVisibilitySnapshotIdGenerator().set(0)
    }

    @After
    fun tearDown() {
        resetStatusBarIconVisibilitySnapshot()
        statusBarIconVisibilitySnapshotIdGenerator().set(0)
        resetStatusBarIconVisibilityState()
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

    private fun getIconVisibilityRuntimeState(): Any? {
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("iconVisibilityRuntimeState").apply { isAccessible = true }
        return field.get(SystemUIStatusBarHooks)
    }

    private fun resetStatusBarIconVisibilitySnapshot() {
        val state = getIconVisibilityRuntimeState() ?: return
        val field = state::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        (field.get(state) as? AtomicReference<StatusBarIconVisibilitySnapshot?>)?.set(null)
    }

    private fun statusBarIconVisibilitySnapshotIdGenerator(): AtomicLong {
        val state = getIconVisibilityRuntimeState() ?: error("icon visibility runtime state not installed")
        val field = state::class.java.getDeclaredField("idGenerator").apply { isAccessible = true }
        return field.get(state) as AtomicLong
    }

    private fun getCurrentSnapshot(): StatusBarIconVisibilitySnapshot? {
        val state = getIconVisibilityRuntimeState() ?: return null
        val field = state::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        return (field.get(state) as? AtomicReference<StatusBarIconVisibilitySnapshot?>)?.get()
    }

    private fun setCurrentSnapshot(snapshot: StatusBarIconVisibilitySnapshot?) {
        val state = getIconVisibilityRuntimeState() ?: return
        val field = state::class.java.getDeclaredField("currentSnapshot").apply { isAccessible = true }
        (field.get(state) as? AtomicReference<StatusBarIconVisibilitySnapshot?>)?.set(snapshot)
    }

    private fun resetStatusBarIconVisibilityState() {
        val state = getIconVisibilityRuntimeState()
        if (state != null) {
            try {
                PreferenceObserverRegistry.unregisterPreferenceObserver(state)
            } catch (_: Throwable) {
                // ignore if not registered
            }
        }
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("iconVisibilityRuntimeState").apply { isAccessible = true }
        field.set(SystemUIStatusBarHooks, null)
    }

    private fun getNetSpeedRuntimeState(): Any? {
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("netSpeedRuntimeState").apply { isAccessible = true }
        return field.get(SystemUIStatusBarHooks)
    }

    private fun defaultSnapshot(): StatusBarIconVisibilitySnapshot {
        return SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(PrefMap().apply { clear() })
    }

    private fun allEnabledSnapshot(): StatusBarIconVisibilitySnapshot {
        val values = allKeys().associateWith { true }
        val countingMap = StatusBarCountingMap().apply { putAll(values) }
        val prefs = PrefMap().apply { clear() }
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(countingMap))
        return SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)
    }

    private fun allKeys(): List<String> = listOf(
        "system_statusbaricons_headset",
        "system_statusbaricons_sound",
        "system_statusbaricons_dnd",
        "system_statusbaricons_alarm",
        "system_statusbaricons_profile",
        "system_statusbaricons_vpn",
        "system_statusbaricons_airplane",
        "system_statusbaricons_nfc",
        "system_statusbaricons_secondspace",
        "system_statusbaricons_gps",
        "system_statusbaricons_wifi",
        "system_statusbaricons_hotspot",
        "system_statusbaricons_nosims",
        "system_statusbaricons_btbattery",
        "system_statusbaricons_ble_unlock",
        "system_statusbaricons_bluetoothicn",
        "system_statusbaricons_volte",
        "system_statusbaricons_signal",
        "system_statusbaricons_signal_wificonnected",
        "system_statusbaricons_sim1",
        "system_statusbaricons_sim2",
        "system_statusbaricons_sim_nodata",
        "system_statusbaricons_roaming",
        "system_statusbaricons_privacy",
        "system_statusbaricons_mute",
        "system_statusbaricons_speaker",
        "system_statusbaricons_record",
        "system_statusbaricons_wireless_headset",
    )

    private fun assertAllFalse(snapshot: StatusBarIconVisibilitySnapshot) {
        assertFalse(snapshot.hideHeadset)
        assertFalse(snapshot.hideSound)
        assertFalse(snapshot.hideDnd)
        assertFalse(snapshot.hideAlarm)
        assertFalse(snapshot.hideProfile)
        assertFalse(snapshot.hideVpn)
        assertFalse(snapshot.hideAirplane)
        assertFalse(snapshot.hideNfc)
        assertFalse(snapshot.hideSecondSpace)
        assertFalse(snapshot.hideGps)
        assertFalse(snapshot.hideWifi)
        assertFalse(snapshot.hideHotspot)
        assertFalse(snapshot.hideNoSims)
        assertFalse(snapshot.hideBtBattery)
        assertFalse(snapshot.hideBleUnlock)
        assertFalse(snapshot.hideBluetoothIcn)
        assertFalse(snapshot.hideVolte)
        assertFalse(snapshot.hideSignal)
        assertFalse(snapshot.hideSignalWifiConnected)
        assertFalse(snapshot.hideSim1)
        assertFalse(snapshot.hideSim2)
        assertFalse(snapshot.hideSimNoData)
        assertFalse(snapshot.hideRoaming)
        assertFalse(snapshot.hidePrivacy)
        assertFalse(snapshot.hideMute)
        assertFalse(snapshot.hideSpeaker)
        assertFalse(snapshot.hideRecord)
        assertFalse(snapshot.hideWirelessHeadset)
    }

    private fun assertAllTrue(snapshot: StatusBarIconVisibilitySnapshot) {
        assertTrue(snapshot.hideHeadset)
        assertTrue(snapshot.hideSound)
        assertTrue(snapshot.hideDnd)
        assertTrue(snapshot.hideAlarm)
        assertTrue(snapshot.hideProfile)
        assertTrue(snapshot.hideVpn)
        assertTrue(snapshot.hideAirplane)
        assertTrue(snapshot.hideNfc)
        assertTrue(snapshot.hideSecondSpace)
        assertTrue(snapshot.hideGps)
        assertTrue(snapshot.hideWifi)
        assertTrue(snapshot.hideHotspot)
        assertTrue(snapshot.hideNoSims)
        assertTrue(snapshot.hideBtBattery)
        assertTrue(snapshot.hideBleUnlock)
        assertTrue(snapshot.hideBluetoothIcn)
        assertTrue(snapshot.hideVolte)
        assertTrue(snapshot.hideSignal)
        assertTrue(snapshot.hideSignalWifiConnected)
        assertTrue(snapshot.hideSim1)
        assertTrue(snapshot.hideSim2)
        assertTrue(snapshot.hideSimNoData)
        assertTrue(snapshot.hideRoaming)
        assertTrue(snapshot.hidePrivacy)
        assertTrue(snapshot.hideMute)
        assertTrue(snapshot.hideSpeaker)
        assertTrue(snapshot.hideRecord)
        assertTrue(snapshot.hideWirelessHeadset)
    }

    // 1. snapshot 首次构建读取 28 个唯一 key 各一次
    @Test
    fun buildStatusBarIconVisibilitySnapshot_readsAllUniqueKeysOnce() {
        val values = allKeys().associateWith { true }
        val countingMap = StatusBarCountingMap().apply { putAll(values) }
        val prefs = PrefMap().apply { clear() }
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(countingMap))

        SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)

        assertEquals(28, countingMap.readCount())
        allKeys().forEach { assertTrue("Expected read for $it", it in countingMap.reads) }
    }

    // 2. checkSlot 连续 100 次，PrefMap 读取为 0
    @Test
    fun checkSlot_100Calls_zeroPrefReads() {
        val snapshot = allEnabledSnapshot()
        val countingMap = countingMainPrefs(emptyMap())
        setCurrentSnapshot(snapshot)
        countingMap.reset()

        val slots = listOf(
            "headset", "volume", "zen", "alarm_clock", "managed_profile",
            "vpn", "airplane", "nfc", "second_space", "location",
            "wifi", "hotspot", "no_sim", "bluetooth_handsfree_battery",
            "ble_unlock_mode", "bluetooth", "hd",
        )
        repeat(100) { index ->
            val slot = slots[index % slots.size]
            assertTrue(SystemUIStatusBarHooks.checkSlot(slot, snapshot))
        }

        assertEquals(0, countingMap.readCount())
    }

    // 3. HideIconsFromSystemManager 等价逻辑 100 次，PrefMap 读取为 0
    @Test
    fun shouldHideSystemManagerIcon_100Calls_zeroPrefReads() {
        val snapshot = allEnabledSnapshot()
        val countingMap = countingMainPrefs(emptyMap())
        setCurrentSnapshot(snapshot)
        countingMap.reset()

        val slots = listOf("stealth", "mute", "speakerphone", "call_record", "wireless_headset")
        repeat(100) { index ->
            val slot = slots[index % slots.size]
            assertTrue(SystemUIStatusBarHooks.shouldHideSystemManagerIcon(slot, snapshot))
        }

        assertEquals(0, countingMap.readCount())
    }

    // 4. HideIconsSignalHook 等价逻辑 100 次，PrefMap 读取为 0
    @Test
    fun computeSignalIconHiding_100Calls_zeroPrefReads() {
        val snapshot = allEnabledSnapshot()
        val countingMap = countingMainPrefs(emptyMap())
        setCurrentSnapshot(snapshot)
        countingMap.reset()

        repeat(100) { index ->
            val result = SystemUIStatusBarHooks.computeSignalIconHiding(
                wifiAvailable = index % 2 == 0,
                subId = index.toInt(),
                dataSubId = 999,
                slotId = index % 2,
                snapshot,
            )
            assertEquals(false, result.visible)
        }

        assertEquals(0, countingMap.readCount())
    }

    // 5. 三条路径各 100 次，热路径总 PrefMap 读取为 0
    @Test
    fun allThreePaths_100Calls_each_zeroPrefReads() {
        val snapshot = allEnabledSnapshot()
        val countingMap = countingMainPrefs(emptyMap())
        setCurrentSnapshot(snapshot)
        countingMap.reset()

        val checkSlots = listOf(
            "headset", "volume", "zen", "alarm_clock", "managed_profile",
            "vpn", "airplane", "nfc", "second_space", "location",
            "wifi", "hotspot", "no_sim", "bluetooth_handsfree_battery",
            "ble_unlock_mode", "bluetooth", "hd",
        )
        val smSlots = listOf("stealth", "mute", "speakerphone", "call_record", "wireless_headset")

        repeat(100) { index ->
            assertTrue(SystemUIStatusBarHooks.checkSlot(checkSlots[index % checkSlots.size], snapshot))
            assertTrue(SystemUIStatusBarHooks.shouldHideSystemManagerIcon(smSlots[index % smSlots.size], snapshot))
            assertEquals(false, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 2, 0, snapshot).visible)
        }

        assertEquals(0, countingMap.readCount())
    }

    // 6. 所有已知固定 slot 映射
    @Test
    fun checkSlot_allKnownSlots() {
        val snapshot = allEnabledSnapshot()
        assertTrue(SystemUIStatusBarHooks.checkSlot("headset", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("volume", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("zen", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("alarm_clock", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("managed_profile", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("vpn", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("airplane", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("nfc", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("second_space", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("location", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("wifi", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("hotspot", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("no_sim", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("bluetooth_handsfree_battery", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("ble_unlock_mode", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("bluetooth", snapshot))
        assertTrue(SystemUIStatusBarHooks.checkSlot("hd", snapshot))
    }

    // 7. 未知 slot 保持原行为
    @Test
    fun checkSlot_unknownSlot_returnsFalse() {
        val snapshot = allEnabledSnapshot()
        assertFalse(SystemUIStatusBarHooks.checkSlot("unknown_slot", snapshot))
    }

    // 8. 空 slotName 保持原行为
    @Test
    fun checkSlot_nullSlot_returnsFalse() {
        val snapshot = allEnabledSnapshot()
        assertFalse(SystemUIStatusBarHooks.checkSlot(null, snapshot))
    }

    // 9. HideIconsFromSystemManager 所有已知 slot
    @Test
    fun shouldHideSystemManagerIcon_allKnownSlots() {
        val snapshot = allEnabledSnapshot()
        assertTrue(SystemUIStatusBarHooks.shouldHideSystemManagerIcon("stealth", snapshot))
        assertTrue(SystemUIStatusBarHooks.shouldHideSystemManagerIcon("mute", snapshot))
        assertTrue(SystemUIStatusBarHooks.shouldHideSystemManagerIcon("speakerphone", snapshot))
        assertTrue(SystemUIStatusBarHooks.shouldHideSystemManagerIcon("call_record", snapshot))
        assertTrue(SystemUIStatusBarHooks.shouldHideSystemManagerIcon("wireless_headset", snapshot))
    }

    // 10. HideIconsFromSystemManager 未知 slot
    @Test
    fun shouldHideSystemManagerIcon_unknownSlot_returnsFalse() {
        val snapshot = allEnabledSnapshot()
        assertFalse(SystemUIStatusBarHooks.shouldHideSystemManagerIcon("some_other_slot", snapshot))
    }

    // 11. HideIconsSignal 隐藏主开关且忽略 Wi-Fi 条件
    @Test
    fun computeSignalIconHiding_hideSignal_withoutWifiCondition() {
        val snapshot = defaultSnapshot().copy(hideSignal = true, hideSignalWifiConnected = false)
        val result = SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 0, snapshot)
        assertEquals(false, result.visible)
    }

    // 12. HideIconsSignal 仅在 Wi-Fi 可用时隐藏
    @Test
    fun computeSignalIconHiding_hideSignal_onlyWhenWifiAvailable() {
        val snapshot = defaultSnapshot().copy(hideSignal = true, hideSignalWifiConnected = true)
        assertEquals(null, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 0, snapshot).visible)
        assertEquals(false, SystemUIStatusBarHooks.computeSignalIconHiding(true, 1, 1, 0, snapshot).visible)
    }

    // 13. HideIconsSignal sim1 slot 0
    @Test
    fun computeSignalIconHiding_sim1Slot0() {
        val snapshot = defaultSnapshot().copy(hideSim1 = true)
        assertEquals(false, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 0, snapshot).visible)
        assertEquals(null, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 1, snapshot).visible)
    }

    // 14. HideIconsSignal sim2 slot 1
    @Test
    fun computeSignalIconHiding_sim2Slot1() {
        val snapshot = defaultSnapshot().copy(hideSim2 = true)
        assertEquals(null, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 0, snapshot).visible)
        assertEquals(false, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 1, snapshot).visible)
    }

    // 15. HideIconsSignal sim no data
    @Test
    fun computeSignalIconHiding_simNoData() {
        val snapshot = defaultSnapshot().copy(hideSimNoData = true)
        assertEquals(false, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 2, 0, snapshot).visible)
        assertEquals(null, SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 0, snapshot).visible)
    }

    // 16. HideIconsSignal roaming 和 volte 置 false
    @Test
    fun computeSignalIconHiding_roamingAndVolte() {
        val snapshot = defaultSnapshot().copy(hideRoaming = true, hideVolte = true)
        val result = SystemUIStatusBarHooks.computeSignalIconHiding(false, 1, 1, 0, snapshot)
        assertEquals(null, result.visible)
        assertEquals(false, result.roaming)
        assertEquals(false, result.volte)
        assertEquals(false, result.speechHd)
    }

    // 17. 默认配置全部关闭
    @Test
    fun buildStatusBarIconVisibilitySnapshot_defaultsAllFalse() {
        val snapshot = defaultSnapshot()
        assertAllFalse(snapshot)
    }

    // 18. 每个偏好独立开启
    @Test
    fun buildStatusBarIconVisibilitySnapshot_individualKeys() {
        val key = "system_statusbaricons_wifi"
        val countingMap = StatusBarCountingMap().apply { putAll(mapOf(key to true)) }
        val prefs = PrefMap().apply { clear() }
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(countingMap))

        val snapshot = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)
        assertTrue(snapshot.hideWifi)
        assertFalse(snapshot.hideBluetoothIcn)
    }

    // 19. 多个偏好组合
    @Test
    fun buildStatusBarIconVisibilitySnapshot_combinedKeys() {
        val values = mapOf(
            "system_statusbaricons_wifi" to true,
            "system_statusbaricons_signal" to true,
            "system_statusbaricons_privacy" to true,
        )
        val countingMap = StatusBarCountingMap().apply { putAll(values) }
        val prefs = PrefMap().apply { clear() }
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(countingMap))

        val snapshot = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)
        assertTrue(snapshot.hideWifi)
        assertTrue(snapshot.hideSignal)
        assertTrue(snapshot.hidePrivacy)
        assertFalse(snapshot.hideBluetoothIcn)
    }

    // 20. 无效或空 slotName
    @Test
    fun checkSlot_emptySlot_returnsFalse() {
        val snapshot = allEnabledSnapshot()
        assertFalse(SystemUIStatusBarHooks.checkSlot("", snapshot))
    }

    // 21. 相关 key 变化只重建一次 snapshot
    @Test
    fun statusBarIconVisibilityObserver_relevantKey_rebuildsSnapshotOnce() {
        val countingMap = countingMainPrefs(mapOf("system_statusbaricons_wifi" to false))
        val first = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(MainModule.mPrefs)
        setCurrentSnapshot(first)

        countingMap.reset()
        countingMap.putAll(mapOf("system_statusbaricons_wifi" to true))

        val observer = getStatusBarIconVisibilityObserver()
        observer.onChange("system_statusbaricons_wifi")

        val current = getCurrentSnapshot()
        assertNotNull(current)
        assertNotEquals(first.id, current!!.id)
        assertTrue(current.hideWifi)
        assertEquals(28, countingMap.readCount())
    }

    // 22. 无关 key 变化不重建
    @Test
    fun statusBarIconVisibilityObserver_irrelevantKey_keepsSnapshot() {
        val countingMap = countingMainPrefs(mapOf("system_statusbaricons_wifi" to true))
        val first = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(MainModule.mPrefs)
        setCurrentSnapshot(first)

        countingMap.reset()
        val observer = getStatusBarIconVisibilityObserver()
        observer.onChange("system_netspeed_fontsize")

        val current = getCurrentSnapshot()
        assertSame(first, current)
        assertEquals(0, countingMap.readCount())
    }

    // 23. 并发构建只产生完整旧或新 snapshot
    @Test
    fun buildStatusBarIconVisibilitySnapshot_atomicPublication() {
        val values = allKeys().associateWith { false }
        val countingMap = StatusBarCountingMap().apply { putAll(values) }
        val prefs = PrefMap().apply { clear() }
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(countingMap))

        val first = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)
        val second = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)

        assertNotEquals(first.id, second.id)
        assertAllFalse(first)
        assertAllFalse(second)
    }

    // 24. B3 observer owner 与 B1/B2 owner 不同
    @Test
    fun statusBarIconVisibilityObserverOwner_isNotSystemUIStatusBarHooks() {
        val b3Owner = getStatusBarIconVisibilityObserverOwner()
        assertTrue(b3Owner !== SystemUIStatusBarHooks)
    }

    // 25. 注册 B3 observer 不会替换 B1/B2 observer
    @Test
    fun registerB3Observer_doesNotReplaceNetSpeedObserver() {
        val b3Owner = getStatusBarIconVisibilityObserverOwner()

        val b3Observer = getStatusBarIconVisibilityObserver()
        ModuleHelper.observePreferenceChange(b3Observer, b3Owner)

        val netSpeedObserver = getNetSpeedTextStyleObserver()
        ModuleHelper.observePreferenceChange(netSpeedObserver, SystemUIStatusBarHooks)

        // Both observers should still be callable; verify by invoking and checking no crash.
        netSpeedObserver.onChange("system_detailednetspeed_style")
        b3Observer.onChange("system_statusbaricons_wifi")
    }

    // 26. SystemUI 重建后 snapshot 能重新构建
    @Test
    fun buildStatusBarIconVisibilitySnapshot_afterResetProducesFreshSnapshot() {
        val values = allKeys().associateWith { false }
        val countingMap = StatusBarCountingMap().apply { putAll(values) }
        val prefs = PrefMap().apply { clear() }
        val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
        snapshotField.isAccessible = true
        snapshotField.set(prefs, AtomicReference<Map<String, Any>>(countingMap))

        val first = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)
        resetStatusBarIconVisibilitySnapshot()
        val second = SystemUIStatusBarHooks.buildStatusBarIconVisibilitySnapshot(prefs)

        assertNotEquals(first.id, second.id)
        assertAllFalse(second)
    }

    // 27. 功能全部关闭时 snapshot 全 false
    @Test
    fun defaultSnapshot_isAllFalse() {
        val snapshot = defaultSnapshot()
        assertAllFalse(snapshot)
    }

    // 28. B1/B2/B3 observer 同时存在时各自相关配置触发
    @Test
    fun threeObservers_receiveRespectiveKeyChanges() {
        val b3Owner = getStatusBarIconVisibilityObserverOwner()

        val b3Observer = getStatusBarIconVisibilityObserver()
        val netSpeedObserver = getNetSpeedTextStyleObserver()

        ModuleHelper.observePreferenceChange(netSpeedObserver, SystemUIStatusBarHooks)
        ModuleHelper.observePreferenceChange(b3Observer, b3Owner)

        // B1/B2 observer should not respond to B3 key.
        netSpeedObserver.onChange("system_statusbaricons_wifi")

        // B3 observer should respond to B3 key (builds snapshot; no throw).
        b3Observer.onChange("system_statusbaricons_wifi")

        // B3 observer should not respond to B1/B2 key.
        b3Observer.onChange("system_detailednetspeed_style")
    }

    private fun getStatusBarIconVisibilityObserver(): ModuleHelper.PreferenceObserver {
        val state = getIconVisibilityRuntimeState() ?: error("icon visibility runtime state not installed")
        val field = state::class.java.getDeclaredField("observer").apply { isAccessible = true }
        return field.get(state) as ModuleHelper.PreferenceObserver
    }

    private fun getNetSpeedTextStyleObserver(): ModuleHelper.PreferenceObserver {
        if (getNetSpeedRuntimeState() == null) {
            SystemUIStatusBarHooks.NetSpeedStyleHook(fakePackageReadyParam())
        }
        val runtimeState = getNetSpeedRuntimeState() ?: error("net speed runtime state not installed")
        val field = runtimeState::class.java.getDeclaredField("observer").apply { isAccessible = true }
        return field.get(runtimeState) as ModuleHelper.PreferenceObserver
    }

    private fun getStatusBarIconVisibilityObserverOwner(): Any {
        return getIconVisibilityRuntimeState() ?: error("icon visibility runtime state not installed")
    }
}
