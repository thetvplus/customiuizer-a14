package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandMotionCoordinatorTest {
    @Test fun allSystemActivitiesStayShortWhileNotificationBodiesStillSelectLong() {
        for (kind in IslandLiveKind.entries) {
            val activity = IslandLiveActivity(kind, kind.windowPackage ?: "native.source", 1000,
                clock = if (kind == IslandLiveKind.TIMER) IslandLiveClock(60000, true) else null)
            assertFalse(kind.name, IslandMotionCoordinator.prefersLong(
                IslandToastContent("Current activity", activity.packageName, null,
                    detail = "State or timer details", liveActivity = activity)))
        }
        assertTrue(IslandMotionCoordinator.prefersLong(IslandToastContent("Sender", "native.message", null,
            notificationKey = "current", detail = "Body")))
    }

    @Test fun mediaAndNotificationHandoffsUseTheSharedArrivalInBothDirections() {
        val policy = IslandMotionCoordinator()
        policy.commit("music", IslandFormState.Source.MEDIA)
        assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
            policy.arrival("message", IslandFormState.Source.NOTIFICATION, false, false, false))
        policy.commit("message", IslandFormState.Source.NOTIFICATION)
        assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
            policy.arrival("music", IslandFormState.Source.MEDIA, false, false, false))
    }

    @Test fun changingFormsUsesTheSharedArrivalButRefreshingTheSameFormDoesNotReplayIt() {
        val policy = IslandMotionCoordinator()
        for (source in IslandFormState.Source.entries.filter { it != IslandFormState.Source.IDLE }) {
            policy.commit("current", source)
            assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
                policy.arrival("current", source, false, false, false, expanded = true))
            policy.commit("current", source, expanded = true)
            assertEquals(IslandMotionCoordinator.Arrival.NONE,
                policy.arrival("current", source, false, false, false, expanded = true))
            assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
                policy.arrival("current", source, false, false, false))
        }
    }

    @Test fun everyContentSourceUsesTheSameIdleArrivalAndOnlyAnAcceptedNativeWindowGetsItsProfile() {
        for (kind in IslandFormState.Source.entries.filter { it != IslandFormState.Source.IDLE }) {
            val policy = IslandMotionCoordinator()
            policy.commit("idle", IslandFormState.Source.IDLE)
            assertEquals(IslandMotionCoordinator.Arrival.CONTENT, policy.arrival("new", kind, false, false, false))
            assertEquals(IslandMotionCoordinator.Arrival.STANDARD, policy.arrival("new", kind, true, false, false))
            assertEquals(IslandMotionCoordinator.Arrival.NATIVE_WINDOW, policy.arrival("new", kind, false, false, true))
        }
    }
    @Test fun aFeedbackPreemptionCannotTurnTheFollowingRecorderIntoAMetadataOnlyRefresh() {
        val policy = IslandMotionCoordinator()
        val recorder = IslandLiveKey(IslandLiveKind.RECORDER, 10305, 1)
        policy.commit("silent-toast", IslandFormState.Source.FEEDBACK)
        assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
            policy.arrival(recorder, IslandFormState.Source.ACTIVITY, false, false, false))
        policy.commit(recorder, IslandFormState.Source.ACTIVITY)
        repeat(64) {
            assertFalse(policy.replacement(recorder))
            assertEquals(IslandMotionCoordinator.Arrival.NONE,
                policy.arrival(recorder, IslandFormState.Source.ACTIVITY, false, false, false))
        }
        policy.commit("copy", IslandFormState.Source.FEEDBACK)
        assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
            policy.arrival(recorder, IslandFormState.Source.ACTIVITY, false, false, false))
        assertEquals(0f, IslandMotion.sizeCatch(160f, 40f, 160f, 40f, 6f), 0f)
    }
    @Test fun aNotificationBodyEarnsLongFormWithoutALengthThresholdAndUserCollapseStillWins() {
        val rich = IslandToastContent("Sender", "native.message", null, notificationKey = "key",
            detail = "A notification with enough useful details")
        assertTrue(IslandMotionCoordinator.prefersLong(rich))
        assertTrue(IslandMotionCoordinator.prefersLong(rich.copy(detail = "OK")))
        assertTrue(IslandMotionCoordinator.prefersLong(rich.copy(detail = "好")))
        assertFalse(IslandMotionCoordinator.prefersLong(rich.copy(detail = null)))
        assertFalse(IslandMotionCoordinator.prefersLong(rich.copy(detail = "  ")))
        assertFalse(IslandMotionCoordinator.prefersLong(rich.copy(detail = "Sender")))
        assertFalse(IslandMotionCoordinator.prefersLong(IslandToastContent("Copied", "native.app", null)))
        val forms = IslandFormState()
        forms.select("key", automaticExpanded = true); assertTrue(forms.expanded)
        forms.collapse()
        forms.select("key", automaticExpanded = true); assertFalse(forms.expanded)
        forms.select("next", automaticExpanded = true); assertTrue(forms.expanded)
    }
    @Test fun returnCatchAndArtworkScaleStayAtTheRequestedMiddleStrength() {
        assertEquals(IslandMotion.CATCH_PEAK, IslandMotion.mediaReturnCatch(.6f), .00001f)
        assertEquals(.96f, IslandMotion.arrivalScale(0f), .00001f)
        assertEquals(1f, IslandMotion.arrivalScale(1f), .00001f)
        for (p in 1..99) {
            val t=p/100f
            if (p == 50) continue // The native landing introduces one intentional catch impulse.
            val numeric=(IslandMotion.mediaReturnCatch(t+.0001f)-IslandMotion.mediaReturnCatch(t-.0001f))/.0002f
            assertEquals(numeric, IslandMotion.catchDerivative(t), .008f)
        }
    }
    @Test fun aCurrentClockCanExpandOnPauseAndContractOnResumeWithoutOverridingManualChoice() {
        val forms = IslandFormState()
        forms.select("clock"); assertFalse(forms.expanded)
        forms.select("clock", automaticExpanded = true); assertTrue(forms.expanded)
        forms.select("clock"); assertFalse(forms.expanded)
        assertTrue(forms.expand())
        forms.select("clock"); assertTrue(forms.expanded)
        forms.collapse()
        forms.select("clock", automaticExpanded = true); assertFalse(forms.expanded)
        forms.select("next", automaticExpanded = true); assertTrue(forms.expanded)
    }
    @Test fun nativeMediaCancellationDoesNotOwnAnIndependentContentArrival() {
        val view = IslandActivityView(org.robolectric.RuntimeEnvironment.getApplication())
        view.prepareContentArrivalFromCurrent()
        view.cancelNativeArrival()
        assertEquals(0f, org.robolectric.util.ReflectionHelpers.getField<Float>(view, "mediaReturnProgress"), 0f)
        view.prepareReturnFromCurrent()
        view.cancelNativeArrival()
        assertEquals(1f, org.robolectric.util.ReflectionHelpers.getField<Float>(view, "mediaReturnProgress"), 0f)
    }
}
