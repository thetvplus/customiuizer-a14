package tv.withaibuild.customiuizer.mods.vpn

import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchPolicy.Action.*
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchPolicy.Session
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchPolicy.Snapshot
import tv.withaibuild.customiuizer.utils.PrefMap
import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings as Settings

class VpnAutoSwitchPolicyTest {
    private val target = Settings.Target("app", "vpn.client")
    private val taskA = Any()
    private val taskB = Any()
    private val down = Snapshot(true)
    private fun policy(close: Boolean = true, selected: Settings.Target = target) = VpnAutoSwitchPolicy(
        Settings.Options(true, selected, setOf("app.a", "app.b"), close))
    private fun opened(p: VpnAutoSwitchPolicy, pkg: String = "app.a", id: Int = 1, owner: Any = taskA) {
        assertTrue(p.resumed(pkg, 0, id, owner))
    }
    private fun connected(p: VpnAutoSwitchPolicy, token: Any = Any()): Snapshot {
        assertEquals(START, p.evaluate(down, 100))
        return Snapshot(true, session = Session(target, token)).also { assertEquals(NONE, p.evaluate(it, 200)) }
    }

    @Test fun idleHasNoWorkOrTimer() {
        val p = policy()
        assertEquals(NONE, p.evaluate(down, 0))
        assertEquals(0L, p.deadline)
    }

    @Test fun twoAppsAndMultipleTasksShareOneConnectionUntilLastTaskCloses() {
        val p = policy()
        opened(p)
        val up = connected(p)
        opened(p, "app.a", 2)
        opened(p, "app.b", 3, taskB)
        p.removed(1, 0, 300)
        p.removed(2, 0, 400)
        assertEquals(NONE, p.evaluate(up, 10_000))
        p.removed(3, 0, 11_000)
        assertEquals(NONE, p.evaluate(up, 13_999))
        assertEquals(STOP, p.evaluate(up, 14_000))
        assertTrue(p.owned!!.same(up.session))
    }

    @Test fun repeatedResumeIsDeduplicatedAndHomeDoesNotDisconnect() {
        val p = policy(); opened(p)
        val up = connected(p)
        assertFalse(p.resumed("app.a", 0, 1, taskA))
        assertFalse(p.resumed("launcher", 0, 2, Any()))
        assertEquals(NONE, p.evaluate(up, 80_000))
        assertEquals(0L, p.deadline)
        assertNotNull(p.owned)
    }

    @Test fun taskReopenCancelsDisconnectGrace() {
        val p = policy(); opened(p)
        val up = connected(p)
        p.removed(1, 0, 500)
        opened(p, id = 2)
        assertEquals(NONE, p.evaluate(up, 10_000))
        assertEquals(0L, p.deadline)
    }

    @Test fun closingOneTaskDoesNotCloseAnotherTask() {
        val p = policy(); opened(p)
        val up = connected(p)
        opened(p, "app.b", 2, taskB)
        assertTrue(p.removed(1, 0, 500))
        assertEquals(NONE, p.evaluate(up, 10_000))
        assertTrue(p.removed(2, 0, 11_000))
        assertEquals(STOP, p.evaluate(up, 14_000))
    }

    @Test fun oldTaskRemovalCannotRemoveReopenedTask() {
        val p = policy(); opened(p)
        val up = connected(p)
        val replacement = Any()
        assertTrue(p.resumed("app.a", 0, 1, replacement))
        assertFalse(p.removed(1, 0, 500, taskA))
        assertEquals(NONE, p.evaluate(up, 50_000))
        assertTrue(p.removed(1, 0, 51_000, replacement))
        assertEquals(STOP, p.evaluate(up, 54_000))
    }

    @Test fun otherUsersAndUnselectedAppsNeverTriggerOrRemove() {
        val p = policy()
        assertFalse(p.resumed("app.a", 10, 1, taskA))
        assertFalse(p.resumed("vpn.client", 0, 1, taskA))
        assertEquals(NONE, p.evaluate(down, 1))
        opened(p); val up = connected(p)
        assertFalse(p.removed(1, 10, 400))
        assertEquals(NONE, p.evaluate(up, 20_000))
    }

    @Test fun manuallyConnectedSelectedVpnIsNeverAdoptedOrDisconnected() {
        val p = policy(); opened(p)
        val manual = Snapshot(true, session = Session(target, Any()))
        assertEquals(NONE, p.evaluate(manual, 100))
        assertNull(p.owned)
        p.removed(1, 0, 200)
        assertEquals(NONE, p.evaluate(manual, 20_000))
    }

    @Test fun differentVpnBlocksStartAndIsNeverDisconnected() {
        val p = policy(); opened(p)
        val other = Snapshot(true, session = Session(Settings.Target("app", "other.vpn"), Any()))
        assertEquals(NONE, p.evaluate(other, 100))
        assertEquals(NONE, p.evaluate(down, 200))
        p.removed(1, 0, 300)
        assertEquals(NONE, p.evaluate(other, 50_000))
        assertNull(p.owned)
    }

    @Test fun manualDisconnectStaysPausedUntilAllAppsCloseAndReopen() {
        val p = policy(); opened(p); connected(p)
        assertEquals(NONE, p.evaluate(down, 300))
        opened(p, "app.b", 2, taskB)
        assertEquals(NONE, p.evaluate(down, 400))
        p.removed(1, 0, 500)
        assertEquals(NONE, p.evaluate(down, 600))
        p.removed(2, 0, 700)
        opened(p, id = 3)
        assertEquals(START, p.evaluate(down, 800))
    }

    @Test fun replacementSessionInSameClientIsNotOwnedEvenDuringGrace() {
        val p = policy(); opened(p); connected(p)
        p.removed(1, 0, 300)
        val manual = Snapshot(true, session = Session(target, Any()))
        assertEquals(NONE, p.evaluate(manual, 10_000))
        assertNull(p.owned)
    }

    @Test fun alwaysOnAndLockdownOverrideEvenAnOwnedSession() {
        val p = policy(); opened(p)
        val up = connected(p)
        assertEquals(NONE, p.evaluate(up.copy(protected = true), 300))
        assertNull(p.owned)
        p.removed(1, 0, 400)
        assertEquals(NONE, p.evaluate(up, 50_000))
    }

    @Test fun noRepeatedStartWhilePendingAndFailureHasNoWatchdog() {
        val p = policy(); opened(p)
        assertEquals(START, p.evaluate(down, 100))
        assertEquals(NONE, p.evaluate(down, 500))
        assertEquals(20_100L, p.deadline)
        assertEquals(NONE, p.evaluate(down, 20_100))
        assertEquals(VpnAutoSwitchPolicy.Status.FAILED, p.status)
        assertEquals(0L, p.deadline)
        assertEquals(NONE, p.evaluate(down, 100_000))
    }

    @Test fun startExceptionDoesNotCreateRetryLoop() {
        val p = policy(); opened(p)
        assertEquals(START, p.evaluate(down, 100))
        p.startFailed()
        assertEquals(NONE, p.evaluate(down, 500))
        assertEquals(0L, p.deadline)
    }

    @Test fun delayedConnectAfterLastTaskClosesStillClosesOwnedConnection() {
        val p = policy(); opened(p)
        assertEquals(START, p.evaluate(down, 100))
        p.removed(1, 0, 200)
        val late = Snapshot(true, session = Session(target, Any()))
        assertEquals(STOP, p.evaluate(late, 4_000))
    }

    @Test fun timeoutNeverAdoptsALaterManualConnection() {
        val p = policy(); opened(p)
        assertEquals(START, p.evaluate(down, 100))
        p.evaluate(down, 20_100)
        p.removed(1, 0, 21_000)
        val late = Snapshot(true, session = Session(target, Any()))
        assertEquals(NONE, p.evaluate(late, 22_000))
        assertNull(p.owned)
    }

    @Test fun unavailableSnapshotPreservesOwnershipAndDoesNotAct() {
        val p = policy(); opened(p); val up = connected(p)
        p.removed(1, 0, 300)
        assertEquals(NONE, p.evaluate(Snapshot(false), 10_000))
        assertTrue(p.owned!!.same(up.session))
    }

    @Test fun autoCloseOffLeavesConnectionAndHasNoTimerAfterAppsExit() {
        val p = policy(close = false); opened(p); val up = connected(p)
        p.removed(1, 0, 300)
        assertEquals(NONE, p.evaluate(up, 20_000))
        assertEquals(0L, p.deadline)
    }

    @Test fun systemProfilesAreMatchedByKeyNotJustLegacyOwner() {
        val system = Settings.Target("system", "profile-a")
        val p = policy(selected = system); opened(p)
        assertEquals(START, p.evaluate(down, 100))
        val wrongProfile = Snapshot(true, session = Session(Settings.Target("system", "profile-b"), Any()))
        assertEquals(NONE, p.evaluate(wrongProfile, 200))
        assertNull(p.owned)
        p.removed(1, 0, 300)
        assertEquals(NONE, p.evaluate(wrongProfile, 10_000))
    }

    @Test fun taskOverflowFailsConservativelyWithoutEvictingLiveOwners() {
        val p = policy(); opened(p); val up = connected(p)
        for (id in 2..Settings.MAX_TASKS) opened(p, id = id)
        assertFalse(p.resumed("app.a", 0, 129, taskA))
        for (id in 1..Settings.MAX_TASKS) p.removed(id, 0, 300)
        assertEquals(NONE, p.evaluate(up, 20_000))
        assertEquals(0L, p.deadline)
    }

    @Test fun preferenceSnapshotKeepsSingleTargetAndCopiesMultiAppSelection() {
        val apps = mutableSetOf("app.a", "vpn.client")
        val prefs = PrefMap().apply {
            put("system_vpnauto_enabled", true)
            put("system_vpnauto_client", "vpn.client")
            put("system_vpnauto_apps", apps)
        }
        val options = Settings.Options.read(prefs)
        apps.add("app.b")
        assertEquals(target, options.target)
        assertEquals(setOf("app.a"), options.apps)
        assertTrue(options.autoClose)
    }

    @Test fun unavailableOrExpiredBackendNeverSchedulesRapidRetries() {
        val p = policy(); opened(p)
        assertEquals(START, p.evaluate(down, 100))
        assertNull(p.nextDelay(Snapshot(false), 20_101))
        assertNull(p.nextDelay(down, 20_101))
        assertEquals(20_000L, p.nextDelay(down, 100))
    }

    @Test fun ruleUpdateRetainsUnchangedSessionAndOnlyRemovesUnselectedTasks() {
        val p = policy(); opened(p); val up = connected(p)
        opened(p, "app.b", 2, taskB)
        p.update(p.options.copy(apps = setOf("app.b")), 300)
        assertTrue(p.owned!!.same(up.session))
        assertEquals(NONE, p.evaluate(up, 10_000))
        assertFalse(p.removed(1, 0, 11_000))
        assertTrue(p.removed(2, 0, 12_000))
        assertEquals(STOP, p.evaluate(up, 15_000))
    }

    @Test fun connectingNativeRunnerIsOwnedButDoesNotClaimConnected() {
        val p = policy(); opened(p)
        assertEquals(START, p.evaluate(down, 100))
        val connecting = Snapshot(true, session = Session(target, Any()), connected = false)
        assertEquals(NONE, p.evaluate(connecting, 200))
        assertEquals(VpnAutoSwitchPolicy.Status.CONNECTING, p.status)
        assertNotNull(p.owned)
        assertEquals(NONE, p.evaluate(connecting.copy(connected = true), 300))
        assertEquals(VpnAutoSwitchPolicy.Status.OWNED, p.status)
    }

    @Test fun invalidOrOversizedConfigurationDoesNotActivate() {
        val prefs = PrefMap().apply { put("system_vpnauto_enabled", true) }
        assertFalse(Settings.Options.read(prefs).configured)
        prefs.put("system_vpnauto_client", "vpn.client")
        prefs.put("system_vpnauto_apps", (0..Settings.MAX_APPS).map { "app.$it" }.toSet())
        assertFalse(Settings.Options.read(prefs).configured)
        assertTrue(Settings.Options.read(prefs).apps.isEmpty())
        prefs.put("system_vpnauto_apps", setOf("app.a"))
        assertTrue(Settings.Options.read(prefs).configured)
        prefs.put("system_vpnauto_kind", "unknown")
        assertFalse(Settings.Options.read(prefs).configured)
    }

    @Test fun profileKeyMatchingAnAppPackageDoesNotExcludeThatTrigger() {
        val prefs = PrefMap().apply {
            put("system_vpnauto_enabled", true)
            put("system_vpnauto_kind", "system")
            put("system_vpnauto_profile", "app.a")
            put("system_vpnauto_apps", setOf("app.a"))
        }
        assertEquals(setOf("app.a"), Settings.Options.read(prefs).apps)
        assertTrue(Settings.Options.read(prefs).configured)
    }

    @Test fun sharedTaskRetainsOtherSelectedAppWhenRulesChange() {
        val p = policy(); opened(p); val up = connected(p)
        opened(p, "app.b", 1, taskA)
        p.update(p.options.copy(apps = setOf("app.a")), 300)
        assertEquals(NONE, p.evaluate(up, 10_000))
        assertTrue(p.removed(1, 0, 11_000, taskA))
        assertEquals(STOP, p.evaluate(up, 14_000))
    }

    @Test fun configurableDelaySupportsImmediateAndThirtySecondBoundaries() {
        for (seconds in listOf(0, 1, 3, 30)) {
            val p = policy(); p.update(p.options.copy(closeDelaySeconds = seconds), 0)
            opened(p); val up = connected(p)
            p.removed(1, 0, 500)
            val at = 500 + seconds * 1_000L
            if (seconds > 0) assertEquals(NONE, p.evaluate(up, at - 1))
            assertEquals(STOP, p.evaluate(up, at))
        }
    }

    @Test fun delayUpdatesKeepElapsedIdleTimeAndReopenCancelsTheTimer() {
        val p = policy(); opened(p); val up = connected(p)
        p.removed(1, 0, 500)
        p.update(p.options.copy(closeDelaySeconds = 30), 1_000)
        assertEquals(30_500L, p.deadline)
        assertEquals(NONE, p.evaluate(up, 30_499))
        p.update(p.options.copy(closeDelaySeconds = 1), 30_499)
        assertEquals(STOP, p.evaluate(up, 30_499))
        opened(p, id = 2)
        assertEquals(NONE, p.evaluate(up, 30_500))
        assertEquals(0L, p.deadline)
    }

    @Test fun disablingCloseCancelsTheTimerAndReenablingStartsANewGrace() {
        val p = policy(); opened(p); val up = connected(p)
        p.removed(1, 0, 500)
        p.update(p.options.copy(autoClose = false), 1_000)
        assertEquals(0L, p.deadline)
        assertEquals(NONE, p.evaluate(up, 10_000))
        p.update(p.options.copy(autoClose = true), 11_000)
        assertEquals(14_000L, p.deadline)
        assertEquals(NONE, p.evaluate(up, 13_999))
        assertEquals(STOP, p.evaluate(up, 14_000))
    }

    @Test fun delayPreferenceDefaultsClampsAndRejectsMalformedTypes() {
        val prefs = PrefMap()
        assertEquals(3_000L, Settings.Options.read(prefs).closeDelayMs)
        for ((raw, expected) in listOf(-1 to 0L, 0 to 0L, 30 to 30_000L, Int.MAX_VALUE to 30_000L)) {
            prefs.put("system_vpnauto_delay", raw)
            assertEquals(expected, Settings.Options.read(prefs).closeDelayMs)
        }
        prefs.put("system_vpnauto_delay", "30")
        assertEquals(3_000L, Settings.Options.read(prefs).closeDelayMs)
    }

    @Test fun queuedResumeSurvivesDelayChangesButRejectsReplacedTargets() {
        val old = policy().options
        val latest = old.copy(closeDelaySeconds = 30)
        assertTrue(latest.acceptsResumeFrom(old, "app.a"))
        assertFalse(latest.copy(target = Settings.Target("app", "other.vpn")).acceptsResumeFrom(old, "app.a"))
        assertFalse(latest.copy(apps = setOf("app.b")).acceptsResumeFrom(old, "app.a"))
    }
}
