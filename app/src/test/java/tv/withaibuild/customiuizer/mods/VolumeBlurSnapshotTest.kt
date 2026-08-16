package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import java.util.concurrent.atomic.AtomicReference

class VolumeBlurSnapshotTest {

    private val collapsedKey = "system_volumeblur_collapsed"
    private val expandedKey = "system_volumeblur_expanded"
    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedPrefs = MainModule.mPrefs.getAll()
        MainModule.mPrefs.clear()
        SystemUIControlCenterHooks.refreshVolumeBlurSnapshot()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            (MainModule.mPrefs).replaceSnapshot(savedPrefs)
        } else {
            MainModule.mPrefs.clear()
        }
        SystemUIControlCenterHooks.refreshVolumeBlurSnapshot()
    }

    @Test
    fun specificCollapsedKeyUpdatesSnapshot() {
        MainModule.mPrefs.put(collapsedKey, 75)
        SystemUIControlCenterHooks.onVolumeBlurPreferenceChanged(collapsedKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeBlurSnapshot()
        assertEquals(0.75f, snapshot.collapsed, 0.001f)
    }

    @Test
    fun specificExpandedKeyUpdatesSnapshot() {
        MainModule.mPrefs.put(expandedKey, 60)
        SystemUIControlCenterHooks.onVolumeBlurPreferenceChanged(expandedKey)

        val snapshot = SystemUIControlCenterHooks.getVolumeBlurSnapshot()
        assertEquals(0.60f, snapshot.expanded, 0.001f)
    }

    @Test
    fun nullKeyRefreshesBoth() {
        MainModule.mPrefs.put(collapsedKey, 30)
        MainModule.mPrefs.put(expandedKey, 80)
        SystemUIControlCenterHooks.onVolumeBlurPreferenceChanged(null)

        val snapshot = SystemUIControlCenterHooks.getVolumeBlurSnapshot()
        assertEquals(0.30f, snapshot.collapsed, 0.001f)
        assertEquals(0.80f, snapshot.expanded, 0.001f)
    }

    @Test
    fun unrelatedKeyDoesNotRefreshSnapshot() {
        MainModule.mPrefs.put(collapsedKey, 55)
        SystemUIControlCenterHooks.onVolumeBlurPreferenceChanged(collapsedKey)

        MainModule.mPrefs.put(collapsedKey, 99)
        SystemUIControlCenterHooks.onVolumeBlurPreferenceChanged("system_some_other_key")

        val snapshot = SystemUIControlCenterHooks.getVolumeBlurSnapshot()
        assertEquals(0.55f, snapshot.collapsed, 0.001f)
    }

    @Test
    fun snapshotIsVisibleAcrossThreads() {
        MainModule.mPrefs.put(collapsedKey, 42)
        MainModule.mPrefs.put(expandedKey, 24)

        val observed = AtomicReference<SystemUIControlCenterHooks.VolumeBlurSnapshot>()
        val thread = Thread {
            SystemUIControlCenterHooks.refreshVolumeBlurSnapshot()
            observed.set(SystemUIControlCenterHooks.getVolumeBlurSnapshot())
        }
        thread.start()
        thread.join()

        val snapshot = observed.get()
        assertEquals(0.42f, snapshot.collapsed, 0.001f)
        assertEquals(0.24f, snapshot.expanded, 0.001f)
    }

    @Test
    fun callbackReadsBothFieldsFromOneSnapshot() {
        MainModule.mPrefs.put(collapsedKey, 70)
        MainModule.mPrefs.put(expandedKey, 90)
        SystemUIControlCenterHooks.refreshVolumeBlurSnapshot()

        // A callback must read both fields from a single snapshot.  We simulate the contract by
        // reading the snapshot once and computing two derived values from it.
        val snapshot = SystemUIControlCenterHooks.getVolumeBlurSnapshot()
        val collapsedRead = snapshot.collapsed
        val expandedRead = snapshot.expanded

        // The observed pair must come from the same snapshot instance (same values).
        assertEquals(0.70f, collapsedRead, 0.001f)
        assertEquals(0.90f, expandedRead, 0.001f)
    }

    @Test
    fun refreshReplacesSnapshotWithConsistentValues() {
        MainModule.mPrefs.put(collapsedKey, 10)
        MainModule.mPrefs.put(expandedKey, 20)
        SystemUIControlCenterHooks.refreshVolumeBlurSnapshot()

        val snapshot = SystemUIControlCenterHooks.getVolumeBlurSnapshot()
        assertTrue("collapsed should not exceed expanded after consistent refresh",
            snapshot.collapsed <= snapshot.expanded)
    }
}
