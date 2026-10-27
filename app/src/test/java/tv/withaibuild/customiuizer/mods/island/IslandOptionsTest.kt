package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class IslandOptionsTest {
    @Test fun boardingDefaultsToNativeAndOnlyTheExplicitAbsorptionValueEnablesCapture() {
        val prefs = PrefMap()
        assertFalse(IslandOptions.read(prefs).absorption)
        for (value in listOf("0", "1", "2", "-1")) {
            prefs.put("system_island_return_effect", value)
            assertEquals(value == "1", IslandOptions.read(prefs).absorption)
            assertFalse(IslandOptions.read(prefs).mediaReturn)
        }
    }

    @Test fun measuredWaveformGainHasABoundedDefaultAndDoesNotEnableCapture() {
        val prefs = PrefMap()
        assertEquals(200, IslandOptions.read(prefs).waveformGain)
        for ((stored, expected) in listOf(-1 to 100, 125 to 125, 5000 to 800)) {
            prefs.put("system_island_waveform_gain", stored)
            val options = IslandOptions.read(prefs)
            assertEquals(expected, options.waveformGain)
            assertFalse(options.enabled)
            assertFalse(options.media)
            assertEquals(1, options.waveformMode)
        }
    }
    @Test fun returnAdmissionUsesEnabledSelectionsAndExclusionWinsForMediaAndNativeActivities() {
        val options = IslandOptions(true, true, true, mediaReturn = true,
            mediaReturnApps = setOf("music.app", "blocked.app"), excludedApps = setOf("blocked.app", IslandLiveKind.CLOCK_PACKAGE),
            liveKinds = setOf(IslandLiveKind.TIMER, IslandLiveKind.RECORDER, IslandLiveKind.PRIVACY))
        assertTrue(options.returnsMedia("music.app"))
        assertFalse(options.returnsMedia("blocked.app"))
        assertFalse(options.returnsMedia("unknown.app"))
        assertTrue(options.returnsLive(IslandLiveKind.RECORDER, IslandLiveKind.RECORDER_PACKAGE))
        assertFalse(options.returnsLive(IslandLiveKind.TIMER, IslandLiveKind.CLOCK_PACKAGE))
        assertFalse(options.returnsLive(IslandLiveKind.STOPWATCH, IslandLiveKind.CLOCK_PACKAGE))
        assertFalse(options.returnsLive(IslandLiveKind.PRIVACY, IslandLiveKind.RECORDER_PACKAGE))
        assertEquals(setOf("music.app", IslandLiveKind.RECORDER_PACKAGE), options.returnPackages())
        val onlyNative = options.copy(mediaReturn = false)
        assertFalse(onlyNative.returnsMedia("music.app"))
        assertTrue(onlyNative.returnsLive(IslandLiveKind.RECORDER, IslandLiveKind.RECORDER_PACKAGE))
        assertEquals(setOf(IslandLiveKind.RECORDER_PACKAGE), onlyNative.returnPackages())
        assertTrue(options.copy(enabled = false).returnPackages().isEmpty())
        assertFalse(options.copy(liveReturn = false).returnsLive(IslandLiveKind.RECORDER, IslandLiveKind.RECORDER_PACKAGE))
    }

    @Test fun notificationAdmissionNeverBroadensAnEmptyPositiveSelectionAndPreservesNativeSources() {
        val options = IslandOptions(true, true, false, chat = true,
            notificationApps = setOf("chosen.app", "blocked.app"), chatExcludedApps = setOf("blocked.app"))
        assertTrue(options.acceptsNotification("chosen.app", null))
        assertFalse(options.acceptsNotification("blocked.app", null))
        assertFalse(options.acceptsNotification("unknown.app", null))
        assertFalse(options.copy(chat = false).acceptsNotification("com.android.mms", android.app.Notification.CATEGORY_MESSAGE))
        assertFalse(options.copy(chat = false).acceptsNotification("unknown.app", android.app.Notification.CATEGORY_MESSAGE))
        assertFalse(options.copy(excludedApps = setOf("com.android.mms"))
            .acceptsNotification("com.android.mms", android.app.Notification.CATEGORY_MESSAGE))
        assertFalse(options.copy(notificationApps = emptySet()).acceptsNotification("chosen.app", null))
        assertFalse(options.copy(enabled = false).acceptsNotification("chosen.app", null))
    }

    @Test fun selectedAndExcludedPackageCollectionsAreSnapshotsForAnInFlightOperation() {
        val excluded = mutableSetOf("blocked.app")
        val chatExcluded = mutableSetOf("native.app")
        val allowed = mutableSetOf("chosen.app")
        val prefs = PrefMap().apply {
            put("system_strong_toast_mode", "3"); put("system_island_chat", true)
            put("system_island_excluded_apps", excluded)
            put("system_island_chat_excluded_apps", chatExcluded)
            put("system_island_notification_apps", allowed)
        }
        val options = IslandOptions.read(prefs)
        excluded.clear(); chatExcluded.clear(); allowed.add("unknown.app")
        assertEquals(setOf("blocked.app"), options.excludedApps)
        assertEquals(setOf("native.app"), options.chatExcludedApps)
        assertTrue(options.acceptsNotification("chosen.app", null))
        assertFalse(options.acceptsNotification("unknown.app", null))
    }

    @Test fun hidingWithFloatingWindowsIsIndependentAndDefaultsOff() {
        val prefs = PrefMap()
        assertFalse(IslandOptions.read(prefs).hideInFloatingWindow)
        prefs.put("system_island_hide_in_floating_window", true)
        assertTrue(IslandOptions.read(prefs).hideInFloatingWindow)
        assertFalse(IslandOptions.read(prefs).media)
        assertFalse(IslandOptions.read(prefs).mediaReturn)
        prefs.put("system_island_media_return", true)
        prefs.put("system_island_media_return_apps", setOf("music.app"))
        assertTrue(IslandOptions.read(prefs).hideInFloatingWindow)
    }

    @Test fun nativeWindowHookRequiresTheIslandMediaOptInAndAnExplicitApp() {
        val prefs = PrefMap()
        fun active() = tv.withaibuild.customiuizer.mods.utils.feature.LauncherMusicReturnFeature.evaluateEnabled(prefs)
        assertFalse(active())
        prefs.put("system_strong_toast_mode", "3")
        prefs.put("system_island_media_return", true)
        prefs.put("system_island_media_return_apps", setOf("chosen.music"))
        assertFalse(active())
        prefs.put("system_island_media", true); assertTrue(active())
        prefs.put("system_island_media_return_apps", emptySet<String>()); assertFalse(active())
        prefs.put("system_island_media_return_apps", setOf("chosen.music"))
        prefs.put("system_island_simplified", true); assertFalse(active())
        prefs.put("system_island_simplified", false); assertTrue(active())
        prefs.put("system_strong_toast_mode", "0"); assertFalse(active())
    }

    @Test fun musicReturnIsOptInAndNeverTreatsAnEmptySelectionAsAllApps() {
        val prefs = PrefMap()
        assertFalse(IslandOptions.read(prefs).mediaReturn)
        assertTrue(IslandOptions.read(prefs).mediaReturnApps.isEmpty())
        prefs.put("system_island_media_return", true)
        assertTrue(IslandOptions.read(prefs).mediaReturnApps.isEmpty())
        val selected = mutableSetOf("music.selected")
        prefs.put("system_island_media_return_apps", selected)
        val options = IslandOptions.read(prefs)
        selected.add("music.other")
        assertEquals(setOf("music.selected"), options.mediaReturnApps)
        prefs.put("system_island_media_return", false)
        assertFalse(IslandOptions.read(prefs).mediaReturn)
        assertEquals(selected, IslandOptions.read(prefs).mediaReturnApps)
    }

    @Test fun musicSwipeDefaultsOnAndIsIndependentOfRetiredGesturePreferences() {
        val prefs = PrefMap()
        assertTrue(IslandOptions.read(prefs).mediaSwipe)
        prefs.put("system_island_gestures_horizontal", false)
        assertTrue(IslandOptions.read(prefs).mediaSwipe)
        prefs.put("system_island_media_swipe", false)
        assertFalse(IslandOptions.read(prefs).mediaSwipe)
        prefs.put("system_island_media_swipe", true)
        assertTrue(IslandOptions.read(prefs).mediaSwipe)
    }

    @Test fun waveformModeRetainsOldOffValuesAndNeverPromotesInvalidImportsToRealCapture() {
        val prefs = PrefMap()
        assertEquals(1, IslandOptions.read(prefs).waveformMode)
        prefs.put("system_island_waveform", false)
        assertEquals(0, IslandOptions.read(prefs).waveformMode)
        prefs.put("system_island_waveform_mode", "2")
        assertEquals(2, IslandOptions.read(prefs).waveformMode)
        assertTrue(IslandOptions.read(prefs).waveform)
        for (stored in listOf("-1", "99", "invalid")) {
            prefs.put("system_island_waveform_mode", stored)
            assertEquals(1, IslandOptions.read(prefs).waveformMode)
        }
        assertEquals(false, prefs.getBoolean("system_island_waveform", true))
    }

    @Test fun entranceStyleDefaultsSafelyAndRetainsItsChoiceWithSimplifiedAnimation() {
        val prefs = PrefMap()
        assertEquals(IslandEntranceStyle.TOP, IslandOptions.read(prefs).entranceStyle)
        for (stored in listOf<Any>(-1, "2", "invalid", true)) {
            prefs.put("system_island_entrance_style", stored)
            assertEquals(IslandEntranceStyle.TOP, IslandOptions.read(prefs).entranceStyle)
        }
        prefs.put("system_island_entrance_style", "0")
        assertEquals(IslandEntranceStyle.CENTER, IslandOptions.read(prefs).entranceStyle)
        for (stored in listOf<Any>("1", 1)) {
            prefs.put("system_island_entrance_style", stored)
            val options = IslandOptions.read(prefs)
            assertEquals(IslandEntranceStyle.TOP, options.entranceStyle)
            assertFalse(options.enabled)
        }
        prefs.put("system_island_simplified", true)
        assertTrue(IslandOptions.read(prefs).simplified)
        assertEquals(IslandEntranceStyle.TOP, IslandOptions.read(prefs).entranceStyle)
        prefs.put("system_island_simplified", false)
        prefs.put("system_strong_toast_mode", "3")
        assertTrue(IslandOptions.read(prefs).enabled)
        assertEquals(IslandEntranceStyle.TOP, IslandOptions.read(prefs).entranceStyle)
    }

    @Test fun retiredGestureArtworkAndConcisePreferencesCannotDisableFixedBehavior() {
        val prefs = PrefMap()
        val defaults = IslandOptions.read(prefs)
        for (key in listOf("system_island_icons", "system_island_concise", "system_island_controls",
            "system_island_gestures", "system_island_gestures_horizontal", "system_island_gestures_vertical"))
            prefs.put(key, false)
        assertEquals(defaults, IslandOptions.read(prefs))
    }

    @Test fun compactHeightIsIndependentOfExpandedWidthAndClampsImportedValues() {
        val prefs = PrefMap().apply { put("system_island_expanded_width", 450) }
        assertEquals(40, IslandOptions.read(prefs).height)
        for ((stored, expected) in listOf(-1 to 32, 32 to 32, 38 to 38, 41 to 41, 44 to 44, 64 to 64, 1000 to 64)) {
            prefs.put("system_island_height", stored)
            assertEquals(expected, IslandOptions.read(prefs).height)
            assertEquals(450, prefs.getInt("system_island_expanded_width", 0))
        }
    }

    @Test fun manualCollapsedWidthsAreBoundedOrderedAndIndependentOfExpansion() {
        val prefs = PrefMap()
        val defaults = IslandOptions.read(prefs)
        assertTrue(defaults.automaticWidth)
        assertEquals(184, defaults.shortWidth)
        assertEquals(240, defaults.longWidth)
        prefs.put("system_island_width_auto", false)
        prefs.put("system_island_width_short", 224)
        prefs.put("system_island_width_medium", 184)
        prefs.put("system_island_expanded_width", 450)
        val manual = IslandOptions.read(prefs)
        assertFalse(manual.automaticWidth)
        assertEquals(224, manual.shortWidth)
        assertEquals(224, manual.longWidth)
        assertEquals(450, prefs.getInt("system_island_expanded_width", 0))
        prefs.put("system_island_width_short", -100)
        prefs.put("system_island_width_medium", 1000)
        assertEquals(96, IslandOptions.read(prefs).shortWidth)
        assertEquals(400, IslandOptions.read(prefs).longWidth)
    }
}
