package tv.withaibuild.customiuizer.mods.island

import org.junit.Assert.*
import org.junit.Test

class IslandFormStateTest {
    @Test fun manualExpansionExpiresWithoutMetadataRenewalAndInteractionDefersItsDeadline() {
        val forms = IslandFormState()
        forms.select("playing", source = IslandFormState.Source.MEDIA)
        assertTrue(forms.expand(100))
        repeat(20) { forms.select("playing", source = IslandFormState.Source.MEDIA) }
        assertEquals(5100L, forms.expansionDeadline)
        forms.interact(true, 5000)
        forms.expireExpansion(20000)
        assertTrue(forms.expanded); assertNull(forms.expansionDeadline)
        forms.interact(false, 21000)
        forms.expireExpansion(25999); assertTrue(forms.expanded)
        forms.expireExpansion(26000); assertFalse(forms.expanded)
        forms.select("playing", automaticExpanded = true, source = IslandFormState.Source.MEDIA)
        assertFalse("Updates cannot undo an automatic contraction", forms.expanded)
        assertTrue(forms.expand(30000, 15000))
        assertEquals(45000L, forms.expansionDeadline)
        forms.select("new notice", automaticExpanded = true, source = IslandFormState.Source.NOTIFICATION)
        assertNull(forms.expansionDeadline)
        forms.expireExpansion(90000)
        assertTrue("A previous expansion cannot shorten a successor notice", forms.expanded)
    }

    @Test fun everySourceIsDismissedWithOneGestureAtEitherWidth() {
        val forms = IslandFormState()
        assertEquals(IslandFormState.Form.IDLE, forms.form)
        assertEquals(IslandFormState.Dismissal.IDLE, forms.dismissal())
        for (source in IslandFormState.Source.entries.filter { it != IslandFormState.Source.IDLE }) {
            forms.select(source, source = source)
            assertEquals(source, forms.source)
            assertEquals(IslandFormState.Form.SHORT, forms.form)
            assertEquals(IslandFormState.Dismissal.SOURCE, forms.dismissal())
            assertTrue(forms.expand())
            assertEquals(IslandFormState.Form.LONG, forms.form)
            assertEquals(IslandFormState.Dismissal.SOURCE, forms.dismissal())
        }
        forms.select(null)
        assertEquals(IslandFormState.Source.IDLE, forms.source)
        assertEquals(IslandFormState.Form.IDLE, forms.form)
    }

    @Test fun notificationsActivitiesAndFeedbackHaveExplicitSourceOwnership() {
        val feedback = IslandToastContent("Copied", "source", null)
        assertEquals(IslandFormState.Source.FEEDBACK, IslandFormState.source(feedback, true))
        assertEquals(IslandFormState.Source.NOTIFICATION,
            IslandFormState.source(feedback.copy(notificationKey = "native-key"), true))
        assertEquals(IslandFormState.Source.ACTIVITY, IslandFormState.source(feedback.copy(
            liveActivity = IslandLiveActivity(IslandLiveKind.RECORDER, "source", 1000)), true))
        assertEquals(IslandFormState.Source.MEDIA, IslandFormState.source(null, true))
        assertEquals(IslandFormState.Source.IDLE, IslandFormState.source(null, false))
    }

    @Test fun onlyCurrentIdentityMayStayExpandedAndThereIsNoMessageHistory() {
        val forms = IslandFormState()
        val media = Any(); val notice = Any()
        forms.select(media)
        assertTrue(forms.expand())
        forms.select(media)
        assertTrue(forms.expanded)
        forms.select(notice)
        assertFalse(forms.expanded)
        assertTrue(forms.expand())
        forms.select(media)
        assertFalse(forms.expanded)
        forms.select(null)
        assertFalse(forms.expand())
    }

    @Test fun idleDismissalSurvivesRepeatedRendersButNewActivityOrWakeRestoresIt() {
        val forms = IslandFormState()
        forms.dismissIdle(); forms.select(null)
        assertTrue(forms.idleSuppressed)
        forms.wake(); assertFalse(forms.idleSuppressed)
        forms.dismissIdle(); forms.select(Any())
        assertFalse(forms.idleSuppressed)
    }

    @Test fun threeFormsKeepTheSharedHeightAndPhysicalCutoutWithinTheirSafeWidths() {
        for (density in listOf(1f, 2.75f, 3f)) for (hole in 0..40) {
            val heightDp = IslandSizing.automaticHeight(hole)
            assertTrue(heightDp in 36..40)
            val short = IslandSizing.collapsed(density, 1200, 1000, (40 * density).toInt())
            val idle = IslandSizing.idleWidth(density, (heightDp * density).toInt(), (hole * density).toInt(), short)
            assertTrue(idle <= short)
            assertTrue(idle >= (heightDp * density).toInt())
            assertTrue(idle >= ((hole + 16) * density).toInt() - 1)
            assertTrue(IslandSizing.maximumWidth(density, 1200, 1000) >= short)
        }
    }
}
