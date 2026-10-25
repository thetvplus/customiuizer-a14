package tv.withaibuild.customiuizer.mods.island

import android.app.Notification
import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

class IslandLiveActivityTest {
    @Test fun windowReturnSharesTheSelectedSourcesButNeverAddsPrivacyOrNotificationApps() {
        val all = IslandOptions(true, false, false, liveKinds = IslandLiveKind.entries.toSet())
        assertEquals(setOf(IslandLiveKind.CALL_PACKAGE, IslandLiveKind.CLOCK_PACKAGE, IslandLiveKind.RECORDER_PACKAGE), all.returnPackages())
        assertTrue(all.copy(liveReturn = false).returnPackages().isEmpty())
        assertTrue(IslandLiveKind.canReturn(IslandLiveKind.CALL, IslandLiveKind.CALL_PACKAGE))
        assertFalse(IslandLiveKind.canReturn(IslandLiveKind.PRIVACY, IslandLiveKind.RECORDER_PACKAGE))
        assertFalse(IslandLiveKind.canReturn(IslandLiveKind.CALL, "other.dialer"))
        assertEquals(setOf(IslandLiveKind.CLOCK_PACKAGE, IslandLiveKind.RECORDER_PACKAGE),
            IslandLiveKind.sourcePackages(IslandLiveKind.entries.map { it.value }))
    }
    private fun activity(kind: IslandLiveKind, session: Long = 1, uid: Int = 10300) =
        IslandLiveActivity(kind, "native.source", uid, session = session)
    @Test fun sourcesAreOptInAndOnlyClockAndRecorderRequireAdditionalScope() {
        val prefs = PrefMap()
        assertTrue(IslandLiveKind.read(prefs).isEmpty())
        assertFalse(IslandNativeAppSource.enabled(prefs, IslandLiveKind.CLOCK_PACKAGE))
        prefs.put("system_strong_toast_mode", "3")
        prefs.put(IslandLiveKind.PREFERENCE, setOf("sms", "calendar", "privacy", "unknown"))
        assertEquals(setOf(IslandLiveKind.SMS, IslandLiveKind.CALENDAR, IslandLiveKind.PRIVACY), IslandLiveKind.read(prefs))
        assertTrue(IslandLiveKind.sourcePackages(setOf("sms", "privacy", "call")).isEmpty())
        prefs.put(IslandLiveKind.PREFERENCE, setOf("timer", "alarm", "stopwatch", "recorder"))
        assertTrue(IslandNativeAppSource.enabled(prefs, IslandLiveKind.CLOCK_PACKAGE))
        assertTrue(IslandNativeAppSource.enabled(prefs, IslandLiveKind.RECORDER_PACKAGE))
        assertFalse(IslandNativeAppSource.enabled(prefs, "arbitrary.app"))
        assertEquals(2, IslandLiveKind.sourcePackages(setOf("timer", "stopwatch", "recorder")).size)
    }
    @Test fun priorityDismissalAndSessionReplacementAreBoundedByKind() {
        val shared = IslandLiveRegistry.Dismissal()
        val registry = IslandLiveRegistry(shared)
        val timer = activity(IslandLiveKind.TIMER)
        val recording = activity(IslandLiveKind.RECORDER)
        val call = activity(IslandLiveKind.CALL)
        for (a in listOf(timer, recording, call)) registry.update(a.kind, a)
        val all = IslandLiveKind.entries.toSet()
        assertEquals(call, registry.select(all, emptySet(), 0))
        registry.dismiss(call.key)
        assertEquals(timer, registry.select(all, emptySet(), 0))
        registry.clear(preserveDismissal = true)
        registry.update(call.kind, call)
        assertNull(registry.select(all, emptySet(), 0))
        registry.update(call.kind, call.copy(session = 2))
        assertEquals(2L, registry.select(all, emptySet(), 0)!!.session)
        repeat(100) { registry.update(call.kind, call.copy(session = it + 3L)); registry.dismiss(call.copy(session = it + 3L).key) }
        assertEquals(1, shared.keys.size)
        registry.update(call.kind, null)
        assertTrue(shared.keys.isEmpty())
    }
    @Test fun userExclusionsSecondaryPrivacyAndExpiredCountdownCannotStealMedia() {
        val registry = IslandLiveRegistry()
        val privacy = activity(IslandLiveKind.PRIVACY).copy(privacy = 3)
        registry.update(privacy.kind, privacy)
        val all = IslandLiveKind.entries.toSet()
        assertNull(registry.select(all, emptySet(), 10))
        assertNull(registry.select(all, setOf(privacy.packageName), 0))
        assertNull(registry.select(all, emptySet(), 0, privacyIsSecondary = true))
        assertEquals(3, registry.privacy(0)); assertEquals(0, registry.privacy(10))
        val timer = activity(IslandLiveKind.TIMER).copy(clock = IslandLiveClock(5000, true))
        registry.update(timer.kind, timer)
        assertEquals(timer, registry.select(all, emptySet(), 0, elapsed = 7999))
        assertEquals(privacy, registry.select(all, emptySet(), 0, elapsed = 8000))
        registry.update(timer.kind, timer.copy(clock = IslandLiveClock(5000, true, 1000)))
        assertEquals(IslandLiveKind.TIMER, registry.select(all, emptySet(), 0, elapsed = 100000)!!.kind)
    }
    @Test fun nativeWallTimeIsTranslatedOnceAndPausedTimeNeverTicks() {
        val countdown = IslandLiveTime.deadline(12000, 10000, 200)!!
        assertEquals(2200L, countdown.base); assertEquals(1500L, countdown.millis(700))
        val stopwatch = IslandLiveTime.elapsed(12000, 500)!!
        assertEquals(-11500L, stopwatch.base); assertEquals(12500L, stopwatch.millis(1000))
        val paused = IslandLiveTime.elapsed(12000, 500, true)!!
        assertEquals(12000L, paused.millis(100000))
        assertNull(IslandLiveTime.deadline(Long.MAX_VALUE, -1, 1))
        assertNull(IslandLiveTime.deadline(1, 2, 1))
        assertNull(IslandLiveTime.elapsed(-1, 100))
        assertNull(IslandLiveTime.elapsed(IslandLiveTime.MAX_SPAN + 1, 100))
        assertEquals("01:01", IslandLiveLabels.time(61000)); assertEquals("1:01:01", IslandLiveLabels.time(3661000))
    }
    @Test fun smsAndCalendarClassificationRequiresBothNativePackageAndSemanticCategory() {
        assertEquals(IslandLiveKind.SMS, IslandLiveLabels.notificationKind("com.android.mms", Notification.CATEGORY_MESSAGE))
        assertEquals(IslandLiveKind.CALENDAR, IslandLiveLabels.notificationKind("com.xiaomi.calendar", Notification.CATEGORY_EVENT))
        assertNull(IslandLiveLabels.notificationKind("untrusted.app", Notification.CATEGORY_EVENT))
        assertNull(IslandLiveLabels.notificationKind("com.xiaomi.calendar", Notification.CATEGORY_PROMO))
        assertEquals(IslandLiveKind.CALENDAR, IslandLiveLabels.notificationKind("com.xiaomi.calendar", null, "calendar_channel_normal"))
        assertEquals(IslandLiveKind.CALENDAR, IslandLiveLabels.notificationKind("com.xiaomi.calendar", null, "calendar_channel_silent"))
        assertNull(IslandLiveLabels.notificationKind("com.xiaomi.calendar", null, "marketing"))
        assertNull(IslandLiveLabels.notificationKind("com.xiaomi.calendar", Notification.CATEGORY_PROMO, "calendar_channel_normal"))
        assertEquals(IslandLiveKind.SMS, IslandLiveLabels.notificationKind("com.android.mms", null,
            template = "android.app.Notification\$MessagingStyle"))
    }
}
