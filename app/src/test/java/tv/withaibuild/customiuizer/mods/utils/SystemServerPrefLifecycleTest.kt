package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemServerPrefLifecycleTest {

    @Test
    fun catchUpCompletesOnlyOnLoadedSnapshot() {
        assertTrue(
            SystemServerPrefLifecycle.shouldMarkCatchUpComplete(
                prefReady = true,
                state = PreferenceBootstrap.State.LOADED,
            ),
        )
        assertFalse(
            SystemServerPrefLifecycle.shouldMarkCatchUpComplete(
                prefReady = true,
                state = PreferenceBootstrap.State.VALID_EMPTY,
            ),
        )
        assertFalse(
            SystemServerPrefLifecycle.shouldMarkCatchUpComplete(
                prefReady = false,
                state = PreferenceBootstrap.State.LOADED,
            ),
        )
        assertFalse(
            SystemServerPrefLifecycle.shouldMarkCatchUpComplete(
                prefReady = false,
                state = PreferenceBootstrap.State.UNAVAILABLE,
            ),
        )
    }

    @Test
    fun catchUpRunsOnceWhenFirstLoaded() {
        assertTrue(
            SystemServerPrefLifecycle.shouldRunCatchUp(
                catchUpDone = false,
                state = PreferenceBootstrap.State.LOADED,
            ),
        )
        assertFalse(
            SystemServerPrefLifecycle.shouldRunCatchUp(
                catchUpDone = true,
                state = PreferenceBootstrap.State.LOADED,
            ),
        )
        assertFalse(
            SystemServerPrefLifecycle.shouldRunCatchUp(
                catchUpDone = false,
                state = PreferenceBootstrap.State.VALID_EMPTY,
            ),
        )
        assertFalse(
            SystemServerPrefLifecycle.shouldRunCatchUp(
                catchUpDone = false,
                state = PreferenceBootstrap.State.UNAVAILABLE,
            ),
        )
    }
}
