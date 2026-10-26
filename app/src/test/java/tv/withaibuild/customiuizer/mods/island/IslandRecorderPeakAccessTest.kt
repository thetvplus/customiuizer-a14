package tv.withaibuild.customiuizer.mods.island

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.setField

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandRecorderPeakAccessTest {
    interface Peak { fun value(): Int }
    open class Pcm(@JvmField var recorder: AudioRecord, var peak: Int) : Peak { override fun value() = peak }
    class Mp3(recorder: AudioRecord, peak: Int) : Pcm(recorder, peak)
    class Encoded(@JvmField val recorder: MediaRecorder, var peak: Int) : Peak { override fun value() = peak }
    class Service(@JvmField var engine: Peak?) {
        var duringRead: (() -> Unit)? = null
        fun amplitude(): Int = (engine?.value() ?: -1).also { duringRead?.invoke() }
    }
    private fun access() = IslandRecorderPeakAccess(Service::class.java.getDeclaredMethod("amplitude"),
        Service::class.java.getDeclaredField("engine"))
    private fun audio(encoding: Int) = Shadow.newInstanceOf(AudioRecord::class.java).also {
        setField(it, "mAudioFormat", encoding)
    }

    @Test fun quietHighQualityRecordingIsConvertedBeforeTheS16BridgeCanSaturateIt() {
        val pcm = Pcm(audio(AudioFormat.ENCODING_PCM_24BIT_PACKED), 65536)
        val owner = Service(pcm)
        val reader = requireNotNull(access().bind(owner))
        assertTrue(reader.owns(owner))
        assertEquals(255, reader.read())
        assertEquals(0f, IslandRecorderMeter.level(reader.read()), 0f)
        pcm.peak = 2097152
        assertEquals(8191, reader.read())
        assertTrue(IslandRecorderMeter.level(reader.read()) in .16f.. .18f)
        pcm.peak = 8388607
        assertEquals(32767, reader.read())
    }

    @Test fun normalPcmAndMediaRecorderRetainTheirNativeS16Scale() {
        val pcm = Service(Pcm(audio(AudioFormat.ENCODING_PCM_16BIT), 8192))
        assertEquals(8192, requireNotNull(access().bind(pcm)).read())
        val compressed = Service(Encoded(Shadow.newInstanceOf(MediaRecorder::class.java), 8192))
        assertEquals(8192, requireNotNull(access().bind(compressed)).read())
        assertEquals(16383, IslandRecorderPeakAccess.canonical(1073741823, Int.MAX_VALUE))
    }

    @Test fun engineReplacementBeforeOrDuringReadCannotReuseThePreviousBitDepth() {
        val owner = Service(Pcm(audio(AudioFormat.ENCODING_PCM_24BIT_PACKED), 65536))
        val reader = requireNotNull(access().bind(owner))
        owner.engine = Pcm(audio(AudioFormat.ENCODING_PCM_16BIT), 8192)
        assertEquals(-1, reader.read())
        val current = requireNotNull(access().bind(owner))
        owner.duringRead = { owner.engine = null }
        assertEquals(-1, current.read())
        assertNull(access().bind(owner))
    }

    @Test fun unsupportedOrInvalidInputKeepsTheMeterUnavailableInsteadOfInventingLevels() {
        assertNull(access().bind(Service(Pcm(audio(AudioFormat.ENCODING_PCM_FLOAT), 8192))))
        assertEquals(-1, IslandRecorderPeakAccess.canonical(-1, 8388607))
        assertEquals(-1, IslandRecorderPeakAccess.canonical(8192, 0))
    }

    @Test fun inheritedMp3InputUsesThePcmPeakBeforeAnyEncodingBitrateIsApplied() {
        val engine = Mp3(audio(AudioFormat.ENCODING_PCM_16BIT), 8192)
        val owner = Service(engine)
        assertEquals(8192, requireNotNull(access().bind(owner)).read())
        engine.peak = 0
        assertEquals(0, requireNotNull(access().bind(owner)).read())
    }

    @Test fun equalInputFractionsKeepTheSameLevelAcrossAllSupportedPeakDepths() {
        for (fraction in listOf(0.0, .005, .02, .25, .6, 1.0)) {
            val expected = IslandRecorderMeter.level((32767 * fraction).toInt())
            for (fullScale in listOf(32767, 8388607, Int.MAX_VALUE)) {
                val peak = (fullScale * fraction).toInt()
                val canonical = IslandRecorderPeakAccess.canonical(peak, fullScale)
                assertEquals("fraction=$fraction fullScale=$fullScale", expected, IslandRecorderMeter.level(canonical), .0001f)
            }
        }
    }

    @Test fun oneClassMetadataCacheAvoidsRepeatedScansWithoutCachingLiveRecorderIdentityOrLevels() {
        var scans = 0
        val access = IslandRecorderPeakAccess(Service::class.java.getDeclaredMethod("amplitude"),
            Service::class.java.getDeclaredField("engine")) { type ->
            scans++; IslandRecorderPeakAccess.recorderFields(type)
        }
        val pcm = Pcm(audio(AudioFormat.ENCODING_PCM_24BIT_PACKED), 65536)
        val owner = Service(pcm)
        val previous = requireNotNull(access.bind(owner))
        repeat(32) { assertEquals(255, requireNotNull(access.bind(owner)).read()) }
        assertEquals(1, scans)
        pcm.recorder = audio(AudioFormat.ENCODING_PCM_16BIT)
        pcm.peak = 8192
        assertEquals(-1, previous.read())
        val next = requireNotNull(access.bind(owner))
        assertEquals(8192, next.read())
        pcm.peak = 0
        assertEquals(0, next.read())
        assertEquals(1, scans)
        owner.engine = Encoded(Shadow.newInstanceOf(MediaRecorder::class.java), 8192)
        assertEquals(8192, requireNotNull(access.bind(owner)).read())
        assertEquals(2, scans)
        owner.engine = pcm
        requireNotNull(access.bind(owner))
        assertEquals("Only one class entry is retained", 3, scans)
    }
}
