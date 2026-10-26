package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class IslandMotionCoordinatorTest {
    @Test fun everyContentSourceUsesTheSameIdleArrivalAndOnlyAnAcceptedNativeWindowGetsItsProfile() {
        for (kind in IslandMotionCoordinator.Kind.entries.filter { it != IslandMotionCoordinator.Kind.IDLE }) {
            val policy = IslandMotionCoordinator()
            policy.commit("idle", IslandMotionCoordinator.Kind.IDLE)
            assertEquals(IslandMotionCoordinator.Arrival.CONTENT, policy.arrival("new", kind, false, false, false))
            assertEquals(IslandMotionCoordinator.Arrival.STANDARD, policy.arrival("new", kind, true, false, false))
            assertEquals(IslandMotionCoordinator.Arrival.NATIVE_WINDOW, policy.arrival("new", kind, false, false, true))
        }
    }
    @Test fun aFeedbackPreemptionCannotTurnTheFollowingRecorderIntoAMetadataOnlyRefresh() {
        val policy = IslandMotionCoordinator()
        val recorder = IslandLiveKey(IslandLiveKind.RECORDER, 10305, 1)
        policy.commit("silent-toast", IslandMotionCoordinator.Kind.NOTICE)
        assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
            policy.arrival(recorder, IslandMotionCoordinator.Kind.ACTIVITY, false, false, false))
        policy.commit(recorder, IslandMotionCoordinator.Kind.ACTIVITY)
        repeat(64) {
            assertFalse(policy.replacement(recorder))
            assertEquals(IslandMotionCoordinator.Arrival.NONE,
                policy.arrival(recorder, IslandMotionCoordinator.Kind.ACTIVITY, false, false, false))
        }
        policy.commit("copy", IslandMotionCoordinator.Kind.NOTICE)
        assertEquals(IslandMotionCoordinator.Arrival.CONTENT,
            policy.arrival(recorder, IslandMotionCoordinator.Kind.ACTIVITY, false, false, false))
        assertEquals(0f, IslandMotion.sizeCatch(160f, 40f, 160f, 40f, 6f), 0f)
    }
    @Test fun expandedContentNeedsUsefulInformationAndAUserCollapseDoesNotReopenOnRefresh() {
        val rich = IslandToastContent("Sender", "native.message", null, notificationKey = "key",
            detail = "A notification with enough useful details")
        assertTrue(IslandMotionCoordinator.prefersLong(rich))
        assertFalse(IslandMotionCoordinator.prefersLong(rich.copy(detail = "OK")))
        assertFalse(IslandMotionCoordinator.prefersLong(rich.copy(detail = "Sender")))
        assertFalse(IslandMotionCoordinator.prefersLong(IslandToastContent("Copied", "native.app", null)))
        val forms = IslandFormState()
        forms.select("key", automaticExpanded = true); assertTrue(forms.expanded)
        forms.collapse()
        forms.select("key", automaticExpanded = true); assertFalse(forms.expanded)
        forms.select("next", automaticExpanded = true); assertTrue(forms.expanded)
    }
    @Test fun returnCatchAndArtworkScaleStayAtTheRequestedMiddleStrength() {
        assertEquals(.45f, IslandMotion.mediaReturnCatch(.775f), .00001f)
        assertEquals(.96f, IslandMotion.arrivalScale(0f), .00001f)
        assertEquals(1f, IslandMotion.arrivalScale(1f), .00001f)
        for (p in 1..99) {
            val t=p/100f
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
