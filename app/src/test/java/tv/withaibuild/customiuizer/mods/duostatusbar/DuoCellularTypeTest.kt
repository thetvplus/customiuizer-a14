package tv.withaibuild.customiuizer.mods.duostatusbar

import com.android.systemui.statusbar.SignalIcon
import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class DuoCellularTypeTest {
    @Test fun nativeNamesMapToTheirGenerationWithoutTreatingLteBrandingAsNr() {
        for (name in arrayOf("5G", "5G+", "5G UW", "5G UWB")) assertEquals(5, DuoCellularType.generation(name))
        for (name in arrayOf("4G", "4G+", "LTE", "LTE+", "LTE-A", "4G LTE", "5GE", "5Ge", "5G E"))
            assertEquals(4, DuoCellularType.generation(name))
        for (name in arrayOf("3G", "3G+", "H", "H+", "HSPA", "HSPA+")) assertEquals(3, DuoCellularType.generation(name))
        for (name in arrayOf("2G", "G", "E", "1X", "1x", "GPRS", "EDGE")) assertEquals(2, DuoCellularType.generation(name))
        for (generation in 2..5) assertEquals("${generation}G", DuoCellularType.label(generation))
    }

    @Test fun missingOrUnrecognizedNativeNamesUseTheGraphicInsteadOfGuessing() {
        for (name in arrayOf(null, "", "Unknown", "NR", "CARRIER_NETWORK_CHANGE", "Carrier 5G plan", "6G"))
            assertEquals(0, DuoCellularType.generation(name))
        assertEquals("", DuoCellularType.label(0))
    }

    @Test fun preparedMetadataReadsTheNativeStateAndIsolatesAFailedTextRead() {
        val abi = requireNotNull(DuoAbi.resolve(javaClass.classLoader!!))
        val reader = requireNotNull(DuoCellularType.resolve(abi))
        val state = SignalIcon.MobileState()
        assertEquals(0, reader.read(state))
        state.showName = "4G"
        assertEquals(4, reader.read(state))
        state.showName = "5G"
        assertEquals(5, reader.read(state))
        assertEquals(0, reader.read("wrong state"))
        assertEquals(0, reader.read(state))
        state.level = 5
        assertEquals(5, abi.mobileLevel.getInt(state))
    }

    @Test fun preferenceDefaultsToGraphicAndOnlyExplicitTextChangesTheCellularCenter() {
        assertEquals(1, DuoConfig.read(PrefMap()).cellularStyle)
        val prefs = PrefMap().apply { put("system_statusbar_duo_cellularstyle", "2") }
        val text = DuoConfig.read(prefs)
        assertEquals(2, text.cellularStyle)
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        state.mobile(0, 10, 4, true, true, 5, 5)
        assertEquals(5, DuoCenter.value(DuoCenter.key(state.snapshot, 0, text)))
        assertEquals(0, DuoCenter.value(DuoCenter.key(state.snapshot, 0, DuoConfig(true, 24))))
        state.mobile(0, 10, 4, true, true, 5, 0)
        assertEquals(0, DuoCenter.value(DuoCenter.key(state.snapshot, 0, text)))
    }

    @Test fun wifiAudioAndAirplaneKeepTheirPriorityInTextMode() {
        val text = DuoConfig(true, 24, cellularStyle = 2)
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        state.mobile(0, 10, 4, true, true, 5, 5)
        fun glyph(audio: Int = 0, config: DuoConfig = text) = DuoCenter.glyph(DuoCenter.key(state.snapshot, audio, config))
        assertEquals(DuoAudioState.CELLULAR_GLYPH, glyph())
        state.wifi(true, true, 3)
        assertEquals(DuoAudioState.WIFI_GLYPH, glyph())
        assertEquals(DuoAudioState.WIRED_GLYPH, glyph(DuoAudioState.WIRED))
        state.wifi(true, true, 3, false)
        assertEquals(DuoAudioState.WIFI_GLYPH, glyph(DuoAudioState.WIRED))
        state.wifi(false, false, 0)
        state.airplane(true)
        assertEquals(DuoAudioState.PLANE_GLYPH, glyph(DuoAudioState.WIRED))
        state.airplane(false)
        state.mobile(0, 10, 4, false, true, 5, 5)
        assertEquals(DuoAudioState.OFFLINE_GLYPH, glyph())
        assertEquals(DuoAudioState.WIFI_GLYPH, glyph(config = DuoConfig(true, 24, networkFallback = false, cellularStyle = 2)))
    }
}
