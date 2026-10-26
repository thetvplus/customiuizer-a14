package tv.withaibuild.customiuizer.mods.island

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowVisualizer

/** Exercise the actual native-backend setup, including its return codes and release path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [IslandMediaCaptureFormatTest.FormatVisualizer::class])
class IslandMediaCaptureFormatTest {
    @Before fun reset() {
        FormatVisualizer.rate = 48_000_000
        FormatVisualizer.mode = Visualizer.SCALING_MODE_NORMALIZED
        FormatVisualizer.scaleStatus = Visualizer.SUCCESS
        FormatVisualizer.sizeStatus = Visualizer.SUCCESS
        FormatVisualizer.size = 0
        FormatVisualizer.released = 0
        FormatVisualizer.captureEnabled = false
        FormatVisualizer.maximumSize = 1024
        FormatVisualizer.readback = null
    }

    private fun backend() = IslandAudioSpectrum.NativeBackend(RuntimeEnvironment.getApplication(), Handler(Looper.getMainLooper()))

    @Test fun realCaptureKeepsPlaybackLevelInsteadOfAmplifyingQuietContent() {
        val capture = backend().capture(42) { _, _ -> }
        assertEquals(Visualizer.SCALING_MODE_AS_PLAYED, FormatVisualizer.mode)
        assertEquals(512, FormatVisualizer.size)
        assertTrue(FormatVisualizer.captureEnabled)
        capture.close()
        assertEquals(1, FormatVisualizer.released)
        assertFalse(FormatVisualizer.captureEnabled)
    }

    @Test fun highNativeSampleRatesRetainAtLeastOneLowFrequencyBinWithinNativeCapacity() {
        for (rate in listOf(44_100_000, 48_000_000, 88_200_000, 96_000_000, 176_400_000, 192_000_000)) {
            FormatVisualizer.rate = rate
            val capture = backend().capture(42) { _, _ -> }
            assertTrue("rate=$rate, size=${FormatVisualizer.size}", rate / 1000f / FormatVisualizer.size <= 200f)
            assertTrue(FormatVisualizer.size in 512..1024)
            capture.close()
        }
    }

    @Test fun unsupportedNativeScalingOrCaptureSizeReleasesWithoutEnablingFalseLevels() {
        FormatVisualizer.scaleStatus = Visualizer.ERROR_BAD_VALUE
        assertThrows(IllegalStateException::class.java) { backend().capture(42) { _, _ -> } }
        assertEquals(1, FormatVisualizer.released)
        assertFalse(FormatVisualizer.captureEnabled)
        reset()
        FormatVisualizer.sizeStatus = Visualizer.ERROR_BAD_VALUE
        assertThrows(IllegalStateException::class.java) { backend().capture(42) { _, _ -> } }
        assertEquals(1, FormatVisualizer.released)
        assertFalse(FormatVisualizer.captureEnabled)
    }

    @Test fun ignoredScalingRequestsCannotSilentlyPublishNormalizedLevels() {
        FormatVisualizer.readback = Visualizer.SCALING_MODE_NORMALIZED
        assertThrows(IllegalStateException::class.java) { backend().capture(42) { _, _ -> } }
        assertEquals(1, FormatVisualizer.released)
        assertFalse(FormatVisualizer.captureEnabled)
    }

    @Test fun nativeCaptureCapacityStillLimitsHighRateSources() {
        FormatVisualizer.rate = 192_000_000
        FormatVisualizer.maximumSize = 512
        val capture = backend().capture(42) { _, _ -> }
        assertEquals(512, FormatVisualizer.size)
        capture.close()
    }

    @Implements(Visualizer::class)
    class FormatVisualizer : ShadowVisualizer() {
        @Implementation override fun native_getSamplingRate() = rate
        @Implementation override fun native_setCaptureSize(value: Int): Int {
            if (sizeStatus == Visualizer.SUCCESS) size = value
            return sizeStatus
        }
        @Implementation override fun native_setEnabled(enabled: Boolean): Int {
            captureEnabled = enabled
            return Visualizer.SUCCESS
        }
        @Implementation override fun native_getEnabled() = captureEnabled
        @Implementation fun native_setScalingMode(value: Int): Int {
            if (scaleStatus == Visualizer.SUCCESS) mode = value
            return scaleStatus
        }
        @Implementation fun native_getScalingMode() = readback ?: mode
        @Implementation override fun native_release() { released++ }
        companion object {
            var rate = 48_000_000
            var mode = Visualizer.SCALING_MODE_NORMALIZED
            var scaleStatus = Visualizer.SUCCESS
            var sizeStatus = Visualizer.SUCCESS
            var size = 0
            var released = 0
            var captureEnabled = false
            var maximumSize = 1024
            var readback: Int? = null
            @JvmStatic @Implementation fun getCaptureSizeRange() = intArrayOf(128, maximumSize)
            @JvmStatic @Implementation fun getMaxCaptureRate() = 30_000
        }
    }
}
