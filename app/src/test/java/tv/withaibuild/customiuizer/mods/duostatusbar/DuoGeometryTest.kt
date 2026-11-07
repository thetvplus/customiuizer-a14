package tv.withaibuild.customiuizer.mods.duostatusbar

import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class DuoGeometryTest {
    @Test fun manualSizeAndShiftRemainExplicitWithoutAnExtraAutomaticOffset() {
        val config=DuoConfig(true,28,autoSize=false)
        assertEquals(28,config.sizeDp)
        assertEquals(56f,DuoSizing.drawingPixels(config,59,2f),0f)
        assertEquals(-1f,DuoSizing.offset(100,-1f),0f)
    }
    @Test fun batteryTrimKeepsOneMonotonicTwoThirdsRange() {
        var previous = 0f
        for (level in 0..100) {
            val sweep = DuoGeometry.arcFillFraction(level)
            assertEquals((2f/3f) * level.coerceAtLeast(1) / 100, sweep, .00001f)
            assertTrue(sweep >= previous)
            previous = sweep
        }
        assertEquals(1f/3f, DuoGeometry.arcFillFraction(50), .00001f)
        assertEquals(1f/150f, DuoGeometry.arcFillFraction(-99), .00001f)
        assertEquals(2f/3f, DuoGeometry.arcFillFraction(999), .00001f)
    }
    @Test fun fullChargeHidesBoltOnlyWhenThePercentageAlreadyExpressesFullCharge() {
        assertTrue(DuoGeometry.showBolt(99,true,true,true))
        assertFalse(DuoGeometry.showBolt(100,true,true,true))
        assertTrue(DuoGeometry.showBolt(100,true,true,false))
        assertFalse(DuoGeometry.showBolt(50,false,true,true))
        assertFalse(DuoGeometry.showBolt(50,true,false,false))
    }

    @Test fun realFakeAndHeaderRowsUseTheSameDrawingBoundsIncludingDoubleRows() {
        for (referenceHeight in intArrayOf(30, 45, 54, 59, 67, 79, 109)) {
            for (offset in -40..40) {
                val sourceRow = referenceHeight
                val requiredHeight = DuoSizing.requiredHeight(67, sourceRow, offset / 2f)
                val headerRow = DuoSizing.sharedHeight(maxOf(59, requiredHeight), sourceRow)
                val fakeRow = DuoSizing.sharedHeight(maxOf(109, requiredHeight), sourceRow)
                val sourceOffset = DuoSizing.offset(sourceRow, offset / 2f)
                val sourceSize = DuoSizing.drawingSize(67, sourceRow, sourceOffset)
                assertEquals(sourceOffset, DuoSizing.offset(headerRow, offset / 2f), 0f)
                assertEquals(sourceOffset, DuoSizing.offset(fakeRow, offset / 2f), 0f)
                assertEquals(sourceSize, DuoSizing.drawingSize(67, headerRow,
                    DuoSizing.offset(headerRow, offset / 2f)), 0f)
                assertEquals(sourceSize, DuoSizing.drawingSize(67, fakeRow,
                    DuoSizing.offset(fakeRow, offset / 2f)), 0f)
            }
        }
        assertEquals(59, DuoSizing.sharedHeight(109, 59))
        assertEquals(30, DuoSizing.sharedHeight(30, 59))
        assertEquals(30, DuoSizing.sharedHeight(30, 0))
    }

    @Test fun automaticDrawingRetainsTheRequestedSizeWithoutTheStockIconHeightCap() {
        val config = DuoConfig(true, 24)
        val width = DuoSizing.pixels(config, 59, 2.8875f)
        assertEquals(77, width)
        // The previous version used the full content row. A 59px resource must not cap it.
        val ink=DuoSizing.drawingPixels(config,59,2.8875f)
        assertEquals(77f, minOf(ink,DuoSizing.drawingSize(width, 109, 0f)), 0f)
        assertEquals(77, DuoSizing.requiredHeight(width, 109, 0f))
        assertEquals(54f, DuoSizing.drawingSize(width, 54, 0f), 0f)
        assertEquals(54, DuoSizing.requiredHeight(width, 54, 0f))
        assertEquals(0, DuoSizing.requiredHeight(width, 0, 10f))
    }

    @Test fun headerSpacePreservesTheSourceOffsetAndBoundsAcrossManualAndAutomaticWidths() {
        for (width in intArrayOf(20, 40, 58, 67, 116)) for (source in 1..200) for (shift in -24..24) {
            val offset = DuoSizing.offset(source, shift / 2f)
            val required = DuoSizing.requiredHeight(width, source, shift / 2f)
            assertTrue(required in 1..source)
            val row = DuoSizing.sharedHeight(maxOf(59, required), source)
            assertEquals(offset, DuoSizing.offset(row, shift / 2f), 0f)
            assertEquals(DuoSizing.drawingSize(width, source, offset),
                DuoSizing.drawingSize(width, row, offset), 0f)
        }
    }

    @Test fun signalTintPreservesNativeAlphaAndChangesAtEveryXiaomiStep() {
        val state = DuoNetworkState()
        state.subscriptions(10, -1, true)
        val foreground = 0x99ffffff.toInt()
        var last = -1
        for (level in 0..5) {
            state.mobile(0, 10, level, true, 5)
            val alpha = (0..3).sumOf { DuoGeometry.signalColor(foreground, state.snapshot, it) ushr 24 }
            assertTrue(alpha > last)
            for (dot in 0..3) assertTrue(DuoGeometry.signalColor(foreground, state.snapshot, dot) ushr 24 in 33..153)
            last = alpha
        }
    }
    @Test fun chargingWinsOverSaverAndCriticalBattery() {
        assertEquals(0xff34c759.toInt(), DuoGeometry.batteryColor(-1, 5, true, true))
        assertEquals(0xfff2b900.toInt(), DuoGeometry.batteryColor(-1, 5, false, true))
        assertEquals(0xffff3b30.toInt(), DuoGeometry.batteryColor(-1, 19, false, false))
        assertEquals(-1, DuoGeometry.batteryColor(-1, 20, false, false))
        assertEquals(0xff000000.toInt(), DuoGeometry.batteryColor(0xff000000.toInt(), 50, false, false))
    }

    @Test fun configHasSafeDefaultsAndBoundsImportedSettings() {
        val defaults = DuoConfig.read(PrefMap())
        assertTrue(defaults.showPercent); assertEquals(24, defaults.sizeDp)
        assertTrue(defaults.showChargingBolt)
        assertTrue(defaults.autoSize); assertTrue(defaults.showAudio)
        assertFalse(defaults.bold)
        assertEquals(1.5f, defaults.horizontalPaddingDp, 0f)
        val imported = PrefMap().apply {
            put("system_statusbar_duo_percent", false)
            put("system_statusbar_duo_size", 999)
            put("system_statusbar_duo_autosize", false)
            put("system_statusbar_duo_audio", false)
            put("system_statusbar_duo_bold", true)
            put("system_statusbar_duo_chargingbolt", false)
            put("system_statusbar_duo_horizontalpadding", 999)
        }
        assertFalse(DuoConfig.read(imported).showPercent)
        assertEquals(40, DuoConfig.read(imported).sizeDp)
        assertFalse(DuoConfig.read(imported).autoSize)
        assertFalse(DuoConfig.read(imported).showAudio)
        assertTrue(DuoConfig.read(imported).bold)
        assertFalse(DuoConfig.read(imported).showChargingBolt)
        assertEquals(6f, DuoConfig.read(imported).horizontalPaddingDp, 0f)
        assertEquals(0f, DuoConfig(true, 24, horizontalPadding = -999).horizontalPaddingDp, 0f)
        assertEquals(20, DuoConfig(true, -999).sizeDp)
    }

    @Test fun wifiUsesNativeOnOffThresholdsWithoutDimmingTheLowestActivePart() {
        for (foreground in intArrayOf(0xffffffff.toInt(), 0xff000000.toInt(), 0x99ffffff.toInt())) {
            for (level in 0..4) {
                for (part in 0..2) {
                    val color = DuoGeometry.wifiColor(foreground, level, part)
                    assertEquals(if(part < level) foreground else DuoGeometry.dim(foreground),color)
                }
            }
            assertEquals(foreground,DuoGeometry.wifiColor(foreground,1,0))
            assertEquals(foreground,DuoGeometry.wifiColor(foreground,3,2))
        }
    }

    @Test fun automaticSizeUsesNativeDensityAndKeepsImportedManualSizeSeparate() {
        val auto = DuoConfig(true, 28)
        assertEquals(77, DuoSizing.pixels(auto, 59, 2.8875f))
        assertEquals(53, DuoSizing.pixels(auto, 0, 2f))
        assertEquals(46, DuoSizing.pixels(auto, 10, 2f))
        assertEquals(55, DuoSizing.pixels(auto, 200, 2f))
        assertEquals(56, DuoSizing.pixels(DuoConfig(true, 28, autoSize = false), 59, 2f))
        assertTrue(DuoSizing.drawingPixels(auto,0,.001f)>=1f)
    }

    @Test fun verticalAdjustmentFitsEveryRowAndCannotEraseASmallDoubleRow() {
        for (height in 1..200) for (offset in -40..40) {
            val actual = DuoSizing.offset(height, offset / 2f)
            val size = DuoSizing.drawingSize(120, height, actual)
            val top = (height - size) / 2f + actual
            assertTrue(size >= minOf(120f, height * 0.75f))
            assertTrue(top >= -0.001f)
            assertTrue(top + size <= height + 0.001f)
        }
        assertEquals(4f, DuoConfig(true, 24, verticalOffset = 999).offsetDp, 0f)
        assertEquals(-4f, DuoConfig(true, 24, verticalOffset = -999).offsetDp, 0f)
        assertEquals(0f, DuoConfig.read(PrefMap()).offsetDp, 0f)
    }

    @Test fun automaticInkAddsPointFourDpWithoutPixelRoundingExceedingPointFive() {
        val config = DuoConfig(true, 24)
        val densities = floatArrayOf(1f, 1.5f, 2f, 2.8875f, 3f, 4f, 6f)
        val heights = intArrayOf(0, 10, 24, 36, 40, 48, 59, 70, 100)
        // Frozen outputs of the visually accepted calibration, including fallback and both limits.
        val expected = arrayOf(
            intArrayOf(24, 21, 25, 25, 25, 25, 25, 25, 25),
            intArrayOf(36, 31, 31, 37, 37, 37, 37, 37, 37),
            intArrayOf(48, 42, 42, 43, 48, 50, 50, 50, 50),
            intArrayOf(69, 60, 60, 60, 60, 60, 70, 71, 71),
            intArrayOf(72, 62, 62, 62, 62, 62, 70, 74, 74),
            intArrayOf(95, 83, 83, 83, 83, 83, 83, 84, 99),
            intArrayOf(143, 125, 125, 125, 125, 125, 125, 125, 125),
        )
        for ((row, density) in densities.withIndex()) {
            for ((column, height) in heights.withIndex()) {
                val drawing=DuoSizing.drawingPixels(config,height,density)
                val previousVisible=(expected[row][column]+.40f*density)*1.0601073f
                val growth=(drawing-previousVisible)/density
                assertEquals("Extra .50dp above the previous visible size, within half a pixel",.50f,growth,.5f/density+.0001f)
                assertEquals(drawing.toInt().toFloat(),drawing,0f)
                assertEquals(kotlin.math.ceil(drawing).toInt(),DuoSizing.pixels(config,height,density))
            }
        }
    }

    @Test fun tintAlphaIsPreservedAndUnknownBluetoothBatteryNeverMeansFull() {
        val native = 0x99ffffff.toInt()
        assertEquals(0x99, DuoGeometry.batteryColor(native, 100, true, false) ushr 24)
        assertEquals(33, DuoGeometry.dim(native) ushr 24)
        assertEquals(0x99, DuoGeometry.bluetoothColor(native, 2) ushr 24)
        assertEquals(DuoGeometry.bluetoothColor(native, 0), DuoGeometry.bluetoothColor(native, 10))
        assertNotEquals(DuoGeometry.bluetoothColor(native, 0), DuoGeometry.bluetoothColor(native, 1))
        assertNotEquals(DuoGeometry.bluetoothColor(native, 0), DuoGeometry.bluetoothColor(native, 5))
        assertEquals(native, DuoGeometry.bluetoothColor(native, 0))
        assertEquals(native, DuoGeometry.bluetoothColor(native, 10))
        assertEquals(0xff000000.toInt(), DuoGeometry.bluetoothColor(0xff000000.toInt(), 0))
    }

    @Test fun repeatedHidingDoesNotLoseTheOriginalVisibility() {
        val state = DuoVisibility(0)
        state.observe(0); state.hide()
        repeat(10000) { state.observe(8); state.hide() }
        assertEquals(0, state.release())
        state.observe(4); state.hide()
        assertEquals(4, state.release())
    }

    @Test fun nativeRemovalWhileHiddenIsPreservedOnRollback() {
        val state = DuoVisibility(0)
        state.hide(); state.observeNativeGone(); state.observe(8)
        assertEquals(8, state.release())
        state.observe(0); state.hide()
        assertEquals(0, state.release())
    }

    @Test fun inactiveRollbackAndDetachDoNotOverwriteNewNativeVisibility() {
        val state = DuoVisibility(0)
        state.hide()
        assertEquals(0, state.release(8))
        assertEquals(8, state.release(8))
        assertEquals(4, state.release(4))
    }
}
