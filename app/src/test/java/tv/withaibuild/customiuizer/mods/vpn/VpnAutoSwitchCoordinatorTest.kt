package tv.withaibuild.customiuizer.mods.vpn

import org.junit.Assert.*
import org.junit.Test
import tv.withaibuild.customiuizer.utils.VpnAutoSwitchSettings as Settings
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchPolicy.Session
import tv.withaibuild.customiuizer.mods.vpn.VpnAutoSwitchPolicy.Snapshot

class VpnAutoSwitchCoordinatorTest {
    private val target = Settings.Target("app", "vpn.client")
    private val options = Settings.Options(true, target, setOf("app.a", "app.b"), true)
    private class Backend : VpnAutoSwitchCoordinator.Backend {
        var state = Snapshot(true)
        var fail = false
        var synchronous = false
        var beforeStop: (() -> Unit)? = null
        val starts = mutableListOf<Settings.Target>()
        val stops = mutableListOf<Session>()
        override fun snapshot() = state
        override fun start(target: Settings.Target): Boolean {
            starts.add(target)
            if (synchronous) state = Snapshot(true, session = Session(target, Any()))
            return !fail
        }
        override fun stop(expected: Session): Boolean {
            beforeStop?.invoke()
            if (state.protected || !expected.same(state.session)) return false
            stops.add(expected)
            state = Snapshot(true)
            return true
        }
    }

    private fun start(coordinator: VpnAutoSwitchCoordinator) {
        coordinator.policy.resumed("app.a", 0, 1, Any())
        coordinator.reconcile(100)
    }

    @Test fun disablingDuringStartCleansLateConnectionAndReleasesAllWork() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        c.configure(options.copy(enabled = false), 200)
        assertTrue(c.active)
        assertEquals(19_900L, c.reconcile(200))
        backend.state = Snapshot(true, session = Session(target, Any()))
        assertNull(c.reconcile(500))
        assertEquals(1, backend.stops.size)
        assertFalse(c.active)
        assertEquals(1, backend.starts.size)
    }

    @Test fun missingLateConnectionHasOneBoundedDeadlineEvenIfBackendIsUnavailable() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c); c.configure(options.copy(enabled = false), 200)
        backend.state = Snapshot(false)
        assertEquals(19_900L, c.reconcile(200))
        assertNull(c.reconcile(20_100))
        assertFalse(c.active)
        backend.state = Snapshot(true, session = Session(target, Any()))
        assertNull(c.reconcile(21_000))
        assertTrue(backend.stops.isEmpty())
    }

    @Test fun replacingTargetWaitsForOldStartBeforeStartingNewVpn() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        val replacement = options.copy(target = Settings.Target("app", "vpn.new"))
        c.configure(replacement, 200)
        assertTrue(c.policy.hasTasks)
        c.reconcile(300)
        assertEquals(listOf(target), backend.starts)
        backend.state = Snapshot(true, session = Session(target, Any()))
        c.reconcile(500)
        assertEquals(listOf(target, replacement.target), backend.starts)
        assertEquals(target, backend.stops.single().target)
    }

    @Test fun repeatedConfigurationChangesDoNotReplaceOrExtendTheRetiringLease() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        for (n in 1..5) c.configure(options.copy(target = Settings.Target("app", "vpn.$n")), n * 1_000L)
        c.configure(options.copy(enabled = false), 6_000)
        assertEquals(14_100L, c.reconcile(6_000))
        assertNull(c.reconcile(20_100))
        assertFalse(c.active)
        assertEquals(1, backend.starts.size)
    }

    @Test fun unrelatedManualVpnIsNeverStoppedByRetiringCleanup() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c); c.configure(options.copy(enabled = false), 200)
        backend.state = Snapshot(true, session = Session(Settings.Target("app", "manual.vpn"), Any()))
        c.reconcile(500)
        assertTrue(backend.stops.isEmpty())
        c.reconcile(20_100)
        assertFalse(c.active)
    }

    @Test fun autoCloseOffKeepsBothEstablishedAndPendingConnectionsOnDisable() {
        for (synchronous in listOf(false, true)) {
            val backend = Backend().apply { this.synchronous = synchronous }
            val cfg = options.copy(autoClose = false)
            val c = VpnAutoSwitchCoordinator(cfg, backend)
            start(c); c.configure(cfg.copy(enabled = false), 200)
            if (!synchronous) {
                assertTrue(c.active)
                backend.state = Snapshot(true, session = Session(target, Any()))
            }
            c.reconcile(500)
            assertFalse(c.active)
            assertTrue(backend.stops.isEmpty())
        }
    }

    @Test fun nativeReplacementImmediatelyBeforeStopIsPreserved() {
        val backend = Backend().apply { synchronous = true }
        val c = VpnAutoSwitchCoordinator(options.copy(closeDelaySeconds = 0), backend)
        start(c)
        val manual = Session(target, Any())
        backend.beforeStop = { backend.state = Snapshot(true, session = manual) }
        c.policy.removed(1, 0, 200)
        c.reconcile(200)
        assertTrue(backend.stops.isEmpty())
        assertTrue(manual.same(backend.state.session))
        assertNull(c.policy.owned)
    }

    @Test fun manualPreexistingConnectionAndManualDisconnectArePreserved() {
        val backend = Backend().apply { state = Snapshot(true, session = Session(target, Any())) }
        val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        assertTrue(backend.starts.isEmpty())
        c.policy.removed(1, 0, 200)
        c.reconcile(10_000)
        assertTrue(backend.stops.isEmpty())
        val ownBackend = Backend().apply { synchronous = true }
        val own = VpnAutoSwitchCoordinator(options, ownBackend)
        start(own)
        ownBackend.state = Snapshot(true)
        own.reconcile(200)
        own.policy.resumed("app.b", 0, 2, Any())
        own.reconcile(300)
        assertEquals(1, ownBackend.starts.size)
    }

    @Test fun userSwitchAbandonsBothCurrentAndRetiringOwnership() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c); c.configure(options.copy(enabled = false), 200)
        c.abandon()
        backend.state = Snapshot(true, session = Session(target, Any()))
        assertNull(c.reconcile(500))
        assertTrue(backend.stops.isEmpty())
        assertFalse(c.active)
    }

    @Test fun reopeningAfterQuickOffOnReusesThePendingStart() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        c.configure(options.copy(enabled = false), 200)
        c.configure(options, 300)
        c.policy.resumed("app.a", 0, 2, Any())
        c.reconcile(400)
        assertEquals(1, backend.starts.size)
        backend.state = Snapshot(true, session = Session(target, Any()))
        c.reconcile(500)
        assertTrue(backend.stops.isEmpty())
        assertNotNull(c.policy.owned)
    }

    @Test fun establishedManualReplacementRemainsUntouchedWhenMasterIsDisabled() {
        val backend = Backend().apply { synchronous = true }
        val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        val manual = Session(target, Any())
        backend.state = Snapshot(true, session = manual)
        c.configure(options.copy(enabled = false), 200)
        c.reconcile(200)
        assertTrue(backend.stops.isEmpty())
        assertTrue(manual.same(backend.state.session))
        assertFalse(c.active)
    }

    @Test fun disablingAutoCloseAndMasterTogetherPreservesTheOwnedConnection() {
        val backend = Backend().apply { synchronous = true }
        val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        c.configure(options.copy(enabled = false, autoClose = false), 200)
        c.reconcile(200)
        assertTrue(backend.stops.isEmpty())
        assertNotNull(backend.state.session)
        assertFalse(c.active)
    }

    @Test fun disablingAutoCloseWhileCleanupIsPendingPreservesTheLateConnection() {
        val backend = Backend(); val c = VpnAutoSwitchCoordinator(options, backend)
        start(c)
        c.configure(options.copy(enabled = false), 200)
        c.configure(options.copy(enabled = false, autoClose = false), 300)
        backend.state = Snapshot(true, session = Session(target, Any()))
        c.reconcile(500)
        assertTrue(backend.stops.isEmpty())
        assertNotNull(backend.state.session)
        assertFalse(c.active)
    }
}
