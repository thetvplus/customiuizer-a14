package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.utils.HookUtils

/**
 * Behavioral tests for the auto-brightness range snapshot.
 */
class AutoBrightnessRangeSnapshotTest {

    private val limitMinKey = "system_autobrightness_limitmin"
    private val limitMaxKey = "system_autobrightness_limitmax"
    private val minKey = "system_autobrightness_min"
    private val maxKey = "system_autobrightness_max"

    private var savedBacklightMaxLevel = 0
    private var savedMinimumBacklight = 0f
    private var savedMaximumBacklight = 0f
    private var savedPrefs: Map<String, Any> = emptyMap()

    @Before
    fun setUp() {
        savedBacklightMaxLevel = SystemDisplayHooks.backlightMaxLevel
        savedMinimumBacklight = SystemDisplayHooks.mMinimumBacklight
        savedMaximumBacklight = SystemDisplayHooks.mMaximumBacklight
        savedPrefs = MainModule.mPrefs.getAll()

        MainModule.mPrefs.clear()
        SystemDisplayHooks.backlightMaxLevel = 0
        SystemDisplayHooks.mMinimumBacklight = 0f
        SystemDisplayHooks.mMaximumBacklight = 0f
        SystemDisplayHooks.refreshAutoBrightnessRangeSnapshot()
    }

    @After
    fun tearDown() {
        MainModule.mPrefs.clear()
        if (savedPrefs.isNotEmpty()) {
            (MainModule.mPrefs).replaceSnapshot(savedPrefs)
        }
        SystemDisplayHooks.backlightMaxLevel = savedBacklightMaxLevel
        SystemDisplayHooks.mMinimumBacklight = savedMinimumBacklight
        SystemDisplayHooks.mMaximumBacklight = savedMaximumBacklight
        SystemDisplayHooks.refreshAutoBrightnessRangeSnapshot()
    }

    @Test
    fun uninitializedReturnsOriginalValue() {
        assertEquals(0.5f, SystemDisplayHooks.constrainValue(0.5f), 0.0001f)
    }

    @Test
    fun negativeValueReturnsOriginalEvenWhenInitialized() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 25, maxPct = 75)

        assertEquals(-0.5f, SystemDisplayHooks.constrainValue(-0.5f), 0.0001f)
    }

    @Test
    fun initializedClampsBelowMin() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 25, maxPct = 75)

        val expectedMin = computeExpected(25)
        assertEquals(expectedMin, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
    }

    @Test
    fun initializedClampsAboveMax() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 25, maxPct = 75)

        val expectedMax = computeExpected(75)
        assertEquals(expectedMax, SystemDisplayHooks.constrainValue(1.0f), 0.0001f)
    }

    @Test
    fun valueInsideRangeIsNotClamped() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 25, maxPct = 75)

        val expectedMin = computeExpected(25)
        val expectedMax = computeExpected(75)
        val inside = (expectedMin + expectedMax) / 2f

        assertEquals(inside, SystemDisplayHooks.constrainValue(inside), 0.0001f)
    }

    @Test
    fun onlyLimitMinApplies() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = false, minPct = 25, maxPct = 75)

        val expectedMin = computeExpected(25)
        assertEquals(expectedMin, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
        assertEquals(1.0f, SystemDisplayHooks.constrainValue(1.0f), 0.0001f)
    }

    @Test
    fun onlyLimitMaxApplies() {
        setBacklightBounds()
        setRangePrefs(limitMin = false, limitMax = true, minPct = 25, maxPct = 75)

        val expectedMax = computeExpected(75)
        assertEquals(0.0f, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
        assertEquals(expectedMax, SystemDisplayHooks.constrainValue(1.0f), 0.0001f)
    }

    @Test
    fun preferenceChangeRefreshesSnapshot() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = false, minPct = 25, maxPct = 75)

        val min25 = computeExpected(25)
        assertEquals(min25, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)

        MainModule.mPrefs.put(minKey, 50)
        SystemDisplayHooks.onAutoBrightnessRangePreferenceChanged(minKey)

        val min50 = computeExpected(50)
        assertTrue("refreshed min must be larger", min50 > min25)
        assertEquals(min50, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
    }

    @Test
    fun unrelatedPreferenceKeyDoesNotRefreshSnapshot() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = false, minPct = 25, maxPct = 75)

        MainModule.mPrefs.put(minKey, 50)
        SystemDisplayHooks.onAutoBrightnessRangePreferenceChanged("system_some_other_key")

        val min25 = computeExpected(25)
        assertEquals(min25, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
    }

    @Test
    fun nullKeyRefreshesSnapshot() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 25, maxPct = 75)

        MainModule.mPrefs.put(minKey, 40)
        MainModule.mPrefs.put(maxKey, 80)
        SystemDisplayHooks.onAutoBrightnessRangePreferenceChanged(null)

        assertEquals(computeExpected(40), SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
        assertEquals(computeExpected(80), SystemDisplayHooks.constrainValue(1.0f), 0.0001f)
    }

    @Test
    fun rawMinBelowZeroIsCoercedToZero() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = -10, maxPct = 75)

        assertEquals(0.0f, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
    }

    @Test
    fun rawMaxAbove100IsCoercedTo100() {
        setBacklightBounds()
        setRangePrefs(limitMin = false, limitMax = true, minPct = 25, maxPct = 150)

        assertEquals(1.0f, SystemDisplayHooks.constrainValue(1.0f), 0.0001f)
    }

    @Test
    fun equalMinMaxIsFailClosed() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 50, maxPct = 50)

        assertEquals(0.3f, SystemDisplayHooks.constrainValue(0.3f), 0.0001f)
    }

    @Test
    fun minGreaterThanMaxIsFailClosed() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 80, maxPct = 20)

        assertEquals(0.3f, SystemDisplayHooks.constrainValue(0.3f), 0.0001f)
    }

    @Test
    fun validMinOnlyStillClamps() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = false, minPct = 30, maxPct = 75)

        assertEquals(computeExpected(30), SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
        assertEquals(1.0f, SystemDisplayHooks.constrainValue(1.0f), 0.0001f)
    }

    @Test
    fun validMaxOnlyStillClamps() {
        setBacklightBounds()
        setRangePrefs(limitMin = false, limitMax = true, minPct = 25, maxPct = 60)

        assertEquals(0.0f, SystemDisplayHooks.constrainValue(0.0f), 0.0001f)
        assertEquals(computeExpected(60), SystemDisplayHooks.constrainValue(1.0f), 0.0001f)
    }

    @Test
    fun invalidDualRangeReturnsOriginal() {
        setBacklightBounds()
        MainModule.mPrefs.put(limitMinKey, true)
        MainModule.mPrefs.put(limitMaxKey, true)
        MainModule.mPrefs.put(minKey, 80)
        MainModule.mPrefs.put(maxKey, 20)
        SystemDisplayHooks.refreshAutoBrightnessRangeSnapshot()

        assertEquals(0.3f, SystemDisplayHooks.constrainValue(0.3f), 0.0001f)
    }

    @Test
    fun unavailableCalibrationReturnsOriginal() {
        setBacklightBounds()
        setRangePrefs(limitMin = true, limitMax = true, minPct = 25, maxPct = 75)

        SystemDisplayHooks.backlightMaxLevel = 0
        SystemDisplayHooks.refreshAutoBrightnessRangeSnapshot()

        assertEquals(0.3f, SystemDisplayHooks.constrainValue(0.3f), 0.0001f)
    }

    private fun setBacklightBounds() {
        SystemDisplayHooks.backlightMaxLevel = 4095
        SystemDisplayHooks.mMinimumBacklight = 0f
        SystemDisplayHooks.mMaximumBacklight = 1f
    }

    private fun setRangePrefs(limitMin: Boolean, limitMax: Boolean, minPct: Int, maxPct: Int) {
        MainModule.mPrefs.put(limitMinKey, limitMin)
        MainModule.mPrefs.put(limitMaxKey, limitMax)
        MainModule.mPrefs.put(minKey, minPct)
        MainModule.mPrefs.put(maxKey, maxPct)
        SystemDisplayHooks.refreshAutoBrightnessRangeSnapshot()
    }

    private fun computeExpected(percent: Int): Float {
        return HookUtils.convertGammaToLinearFloat(
            percent / 100f * SystemDisplayHooks.backlightMaxLevel,
            SystemDisplayHooks.backlightMaxLevel,
            SystemDisplayHooks.mMinimumBacklight,
            SystemDisplayHooks.mMaximumBacklight
        )
    }
}
