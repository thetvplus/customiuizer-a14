package tv.withaibuild.customiuizer.mods.utils

import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.util.DisplayMetrics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class StatusBarHeightConfigTest {

    @After
    fun tearDown() {
        StatusBarHeightConfig.generation.set(0L)
        StatusBarHeightConfig.state = StatusBarHeightConfig.State(
            rawPreferenceDp = StatusBarHeightConfig.DEFAULT_SENTINEL,
            enabled = false,
            configuredDp = StatusBarHeightConfig.DEFAULT_DP,
            configuredPx = -1,
            densityDpi = -1,
            density = -1.0f,
        )
    }

    @Test
    fun resolveHeightDp_defaultSentinel_returns27dp() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 11) }
        assertEquals(27, StatusBarHeightConfig.resolveHeightDp(prefs))
    }

    @Test
    fun resolveHeightDp_customValue_returnsValue() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 40) }
        assertEquals(40, StatusBarHeightConfig.resolveHeightDp(prefs))
    }

    @Test
    fun resolveHeightDp_belowSentinel_returnsValue() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 10) }
        assertEquals(10, StatusBarHeightConfig.resolveHeightDp(prefs))
    }

    @Test
    fun isEnabled_defaultSentinel_false() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 11) }
        assertFalse(StatusBarHeightConfig.isEnabled(prefs))
    }

    @Test
    fun isEnabled_customValueAboveSentinel_true() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 12) }
        assertTrue(StatusBarHeightConfig.isEnabled(prefs))
    }

    @Test
    fun isEnabled_27ExplicitlySet_true() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 27) }
        assertTrue(StatusBarHeightConfig.isEnabled(prefs))
    }

    @Test
    fun isEnabled_missingValue_false() {
        assertFalse(StatusBarHeightConfig.isEnabled(PrefMap()))
    }

    @Test
    fun dpToPx_160dpi_27dpIs27px() {
        assertEquals(27, StatusBarHeightConfig.dpToPx(27, fakeResources(160)))
    }

    @Test
    fun dpToPx_480dpi_27dpIs81px() {
        assertEquals(81, StatusBarHeightConfig.dpToPx(27, fakeResources(480)))
    }

    @Test
    fun dpToPx_fuxiDensity_40dpIs110px() {
        // 440 dpi / 160 * 40 = 110.0
        assertEquals(110, StatusBarHeightConfig.dpToPx(40, fakeResources(440)))
    }

    @Test
    fun configure_defaultSentinel_disabledAndDefaultDp() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 11) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertFalse(StatusBarHeightConfig.enabled)
        assertEquals(11, StatusBarHeightConfig.rawPreferenceDp)
        assertEquals(27, StatusBarHeightConfig.configuredDp)
        assertEquals(27, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_12dp_enabledAndCorrectPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 12) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(12, StatusBarHeightConfig.configuredDp)
        assertEquals(12, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_27dp_enabledAndCorrectPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 27) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(27, StatusBarHeightConfig.configuredDp)
        assertEquals(27, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_28dp_enabledAndCorrectPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 28) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(28, StatusBarHeightConfig.configuredDp)
        assertEquals(28, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_35dp_enabledAndCorrectPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 35) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(35, StatusBarHeightConfig.configuredDp)
        assertEquals(35, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_38dp_enabledAndCorrectPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 38) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(38, StatusBarHeightConfig.configuredDp)
        assertEquals(38, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_40dp_enabledAndCorrectPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 40) }
        StatusBarHeightConfig.configure(prefs, fakeResources(480))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(40, StatusBarHeightConfig.configuredDp)
        assertEquals(120, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun configure_customValue_rawPreferenceDpStored() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 35) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))

        assertTrue(StatusBarHeightConfig.enabled)
        assertEquals(35, StatusBarHeightConfig.rawPreferenceDp)
        assertEquals(35, StatusBarHeightConfig.configuredDp)
        assertEquals(35, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun recomputePx_densityChange_updatesConfiguredPx() {
        val prefs = PrefMap().apply { put("system_statusbarheight", 40) }
        StatusBarHeightConfig.configure(prefs, fakeResources(160))
        assertEquals(40, StatusBarHeightConfig.configuredPx)

        StatusBarHeightConfig.recomputePx(fakeResources(480))
        assertEquals(120, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun recomputePx_disabled_doesNothing() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 11) },
            fakeResources(160),
        )
        StatusBarHeightConfig.recomputePx(fakeResources(480))
        assertEquals(27, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun reconfigure_preservesDensityAndBumpsGeneration() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 40) },
            fakeResources(469),
        )
        val before = StatusBarHeightConfig.generation.get()

        StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 44) })

        assertTrue(StatusBarHeightConfig.generation.get() > before)
        assertEquals(44, StatusBarHeightConfig.configuredDp)
        assertEquals(129, StatusBarHeightConfig.configuredPx) // 44 * 469 / 160 ≈ 129
        assertEquals(469, StatusBarHeightConfig.densityDpi)
    }

    @Test
    fun reconfigure_sameValue_doesNotBloatGeneration() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )
        val before = StatusBarHeightConfig.generation.get()

        val change = StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 44) })

        assertEquals(before, StatusBarHeightConfig.generation.get())
        assertFalse(change.changed)
        assertEquals(44, change.previous.configuredDp)
        assertEquals(44, change.current.configuredDp)
        assertEquals(129, change.previous.configuredPx)
        assertEquals(129, change.current.configuredPx)
        assertEquals(129, StatusBarHeightConfig.configuredPx)
    }

    @Test
    fun recomputePx_density440to469_44dpBecomes129AndBumpsOnce() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(440),
        )
        assertEquals(121, StatusBarHeightConfig.configuredPx)
        val before = StatusBarHeightConfig.generation.get()

        val metrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        StatusBarHeightConfig.recomputePx(metrics)

        assertEquals(129, StatusBarHeightConfig.configuredPx)
        assertEquals(before + 1, StatusBarHeightConfig.generation.get())

        // Recompute with the same density must not bump again.
        StatusBarHeightConfig.recomputePx(metrics)
        assertEquals(129, StatusBarHeightConfig.configuredPx)
        assertEquals(before + 1, StatusBarHeightConfig.generation.get())
    }

    @Test
    fun dpToPx_469dpi_44dpIs129px() {
        val metrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        assertEquals(129, StatusBarHeightConfig.dpToPx(44, metrics))
    }

    @Test
    fun dpToPx_440dpi_44dpIs121px() {
        val metrics = DisplayMetrics().apply { densityDpi = 440; density = 2.75f }
        assertEquals(121, StatusBarHeightConfig.dpToPx(44, metrics))
    }

    @Test
    fun configuredPxFor_12dpAt469dpiIs35px() {
        val metrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        assertEquals(35, StatusBarHeightConfig.configuredPxFor(12, metrics))
    }

    @Test
    fun configuredPxFor_doesNotMutateCache() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 27) },
            fakeResources(160),
        )
        val cachedBefore = StatusBarHeightConfig.configuredPx

        val metrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        StatusBarHeightConfig.configuredPxFor(44, metrics)

        assertEquals(cachedBefore, StatusBarHeightConfig.configuredPx)
        assertEquals(160, StatusBarHeightConfig.densityDpi)
    }

    @Test
    fun currentState_unchanged_returnsSameReference() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 40) },
            fakeResources(160),
        )

        val a = StatusBarHeightConfig.currentState()
        val b = StatusBarHeightConfig.currentState()

        assertSame(a, b)
    }

    @Test
    fun reconfigure_sameValue_doesNotRepublishSnapshot() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            fakeResources(469),
        )
        val before = StatusBarHeightConfig.currentState()
        val generationBefore = StatusBarHeightConfig.generation.get()

        val change = StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 44) })
        val after = StatusBarHeightConfig.currentState()

        assertFalse(change.changed)
        assertSame(before, after)
        assertSame(before, change.previous)
        assertSame(after, change.current)
        assertEquals(generationBefore, StatusBarHeightConfig.generation.get())
    }

    @Test
    fun reconfigure_changedValue_publishesNewConsistentSnapshot() {
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 40) },
            fakeResources(469),
        )
        val before = StatusBarHeightConfig.currentState()
        val generationBefore = StatusBarHeightConfig.generation.get()

        val change = StatusBarHeightConfig.reconfigure(PrefMap().apply { put("system_statusbarheight", 44) })
        val after = StatusBarHeightConfig.currentState()

        assertTrue(change.changed)
        assertNotSame(before, after)
        assertSame(before, change.previous)
        assertSame(after, change.current)
        assertEquals(generationBefore + 1, StatusBarHeightConfig.generation.get())

        assertEquals(44, after.rawPreferenceDp)
        assertEquals(true, after.enabled)
        assertEquals(44, after.configuredDp)
        assertEquals(129, after.configuredPx) // 44 * 469 / 160 ≈ 129
        assertEquals(469, after.densityDpi)
    }

    @Test
    fun recomputePx_sameMetrics_doesNotRepublishSnapshot() {
        val metrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            metrics = metrics,
        )
        val before = StatusBarHeightConfig.currentState()
        val generationBefore = StatusBarHeightConfig.generation.get()

        StatusBarHeightConfig.recomputePx(metrics)
        val after = StatusBarHeightConfig.currentState()

        assertSame(before, after)
        assertEquals(generationBefore, StatusBarHeightConfig.generation.get())
    }

    @Test
    fun recomputePx_changedDensity_publishesOneNewConsistentSnapshot() {
        val initialMetrics = DisplayMetrics().apply { densityDpi = 440; density = 2.75f }
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            metrics = initialMetrics,
        )
        val before = StatusBarHeightConfig.currentState()
        val generationBefore = StatusBarHeightConfig.generation.get()

        val newMetrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        StatusBarHeightConfig.recomputePx(newMetrics)
        val after = StatusBarHeightConfig.currentState()

        assertNotSame(before, after)
        assertEquals(generationBefore + 1, StatusBarHeightConfig.generation.get())
        assertEquals(44, after.configuredDp)
        assertEquals(129, after.configuredPx)
        assertEquals(469, after.densityDpi)
        assertEquals(2.93125f, after.density, 0.0001f)

        // Recompute with the same density again must not publish yet another snapshot.
        StatusBarHeightConfig.recomputePx(newMetrics)
        val again = StatusBarHeightConfig.currentState()
        assertSame(after, again)
        assertEquals(generationBefore + 1, StatusBarHeightConfig.generation.get())
    }

    @Test
    fun recomputePx_unchangedMetrics_resultPreviousEqualsCurrentAndState() {
        val metrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            metrics = metrics,
        )
        val before = StatusBarHeightConfig.currentState()

        val result = StatusBarHeightConfig.recomputePx(metrics)

        assertFalse(result.changed)
        assertSame(before, result.previous)
        assertSame(before, result.current)
        assertSame(before, StatusBarHeightConfig.currentState())
    }

    @Test
    fun recomputePx_changedDensity_resultPublishesNewCurrent() {
        val initialMetrics = DisplayMetrics().apply { densityDpi = 440; density = 2.75f }
        StatusBarHeightConfig.configure(
            PrefMap().apply { put("system_statusbarheight", 44) },
            metrics = initialMetrics,
        )
        val before = StatusBarHeightConfig.currentState()

        val newMetrics = DisplayMetrics().apply { densityDpi = 469; density = 2.93125f }
        val result = StatusBarHeightConfig.recomputePx(newMetrics)

        assertTrue(result.changed)
        assertSame(before, result.previous)
        assertSame(StatusBarHeightConfig.currentState(), result.current)
        assertNotSame(result.previous, result.current)
        assertEquals(129, result.current.configuredPx)
        assertEquals(469, result.current.densityDpi)
    }

    private fun fakeResources(densityDpi: Int): Resources {
        val metrics = DisplayMetrics().apply { this.densityDpi = densityDpi }
        val constructor = AssetManager::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val assetManager = constructor.newInstance()
        return FakeResources(assetManager, metrics)
    }

    private class FakeResources(assetManager: AssetManager, private val metrics: DisplayMetrics) :
        Resources(assetManager, metrics, Configuration()) {
        override fun getDisplayMetrics(): DisplayMetrics = metrics
    }
}
