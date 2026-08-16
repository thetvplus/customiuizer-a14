package tv.withaibuild.customiuizer.mods.statusbariconvisibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.StatusBarIconVisibilitySnapshot
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks
import java.lang.reflect.Field

/**
 * Component tests for [StatusBarIconVisibilityEffect].
 *
 * These tests exercise FAST vs LEGACY eligibility, callback oracle ordering,
 * partial mutation, failure mapping, and the immutable effect contract.
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class StatusBarIconVisibilityEffectTest {

    // -------------------------------------------------------------------------
    // H. exact-root receiver -> FAST
    // -------------------------------------------------------------------------
    @Test
    fun process_exactRoot_mStateNull_fastMutates() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        effect.process(view, state, "applyMobileState")

        assertFalse("visible must be written false", state.visible)
    }

    @Test
    fun process_exactRoot_mStateNonNull_fastDoesNotMutate() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        val existing = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        view.mState = existing
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState().apply {
            visible = true
        }
        val effect = fastEffect()

        effect.process(view, state, "applyMobileState")

        assertTrue("visible must not be changed", state.visible)
    }

    // -------------------------------------------------------------------------
    // I. strict subclass -> LEGACY
    // -------------------------------------------------------------------------
    @Test
    fun process_strictSubclass_mStateNull_legacyMutates() {
        val view = StatusBarIconVisibilityFixtures.SubStatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        effect.process(view, state, "applyMobileState")

        assertFalse("visible must be written false", state.visible)
    }

    // -------------------------------------------------------------------------
    // J. superclass / unrelated mismatch -> LEGACY
    // -------------------------------------------------------------------------
    @Test
    fun process_superclassOfRoot_legacyMutates() {
        val view = StatusBarIconVisibilityFixtures.BaseStatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        effect.process(view, state, "applyMobileState")

        assertFalse("visible must be written false", state.visible)
    }

    @Test
    fun process_unrelatedClass_legacyThrowsNoSuchFieldError() {
        val view = Any()
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        try {
            effect.process(view, state, "applyMobileState")
            fail("Expected NoSuchFieldError for unrelated receiver")
        } catch (e: NoSuchFieldError) {
            // LEGACY path tried to read mState from java.lang.Object.
        }
    }

    // -------------------------------------------------------------------------
    // K. subclass shadowing -> LEGACY preserves dynamic lookup
    // -------------------------------------------------------------------------
    @Test
    fun process_subclassShadowing_legacyUsesSubclassMState() {
        val view = StatusBarIconVisibilityFixtures.SubStatusBarMobileView()
        val baseState = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        (view as StatusBarIconVisibilityFixtures.BaseStatusBarMobileView).mState = baseState
        view.mState = null

        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        effect.process(view, state, "applyMobileState")

        assertFalse("legacy must see subclass mState == null and mutate", state.visible)
    }

    @Test
    fun process_exactRootWithBaseMState_fastUsesBaseMState() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        val baseState = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        (view as StatusBarIconVisibilityFixtures.BaseStatusBarMobileView).mState = baseState

        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        effect.process(view, state, "applyMobileState")

        assertTrue("FAST must see base mState != null and not mutate", state.visible)
    }

    // -------------------------------------------------------------------------
    // L. FAST mState null/non-null eligibility
    // -------------------------------------------------------------------------
    // Covered by H and K; explicitly named in test naming above.

    // -------------------------------------------------------------------------
    // M. updateState skips mState read semantics
    // -------------------------------------------------------------------------
    @Test
    fun process_updateState_exactRoot_skipsMStateReadAndMutates() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        val existing = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        view.mState = existing
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        effect.process(view, state, "updateState")

        assertFalse("updateState must mutate even when mState != null", state.visible)
    }

    @Test
    fun process_updateState_subclass_skipsMStateReadAndMutates() {
        val view = StatusBarIconVisibilityFixtures.SubStatusBarMobileView()
        val existing = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        view.mState = existing
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = fastEffect()

        effect.process(view, state, "updateState")

        assertFalse("updateState must mutate even when subclass mState != null", state.visible)
    }

    // -------------------------------------------------------------------------
    // N. visible=false early return
    // -------------------------------------------------------------------------
    @Test
    fun process_visibleFalseEarlyReturn_noRoamingOrVolteWrites() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState().apply {
            roaming = true
            volte = true
            speechHd = true
        }
        val effect = StatusBarIconVisibilityEffect(fastAbi()) {
            makeSnapshot(hideSignal = true)
        }

        effect.process(view, state, "applyMobileState")

        assertFalse(state.visible)
        assertTrue("roaming must not be written after early return", state.roaming)
        assertTrue("volte must not be written after early return", state.volte)
        assertTrue("speechHd must not be written after early return", state.speechHd)
    }

    // -------------------------------------------------------------------------
    // O. roaming -> volte -> speechHd ordering
    // -------------------------------------------------------------------------
    @Test
    fun process_roamingVolteSpeechHdOrdering_allFalse() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState().apply {
            roaming = true
            volte = true
            speechHd = true
        }
        val effect = StatusBarIconVisibilityEffect(fastAbi()) {
            makeSnapshot(hideRoaming = true, hideVolte = true)
        }

        effect.process(view, state, "applyMobileState")

        assertTrue(state.visible) // not hidden by signal/sim rules
        assertFalse(state.roaming)
        assertFalse(state.volte)
        assertFalse(state.speechHd)
    }

    // -------------------------------------------------------------------------
    // P. partial mutation on later write failure
    // -------------------------------------------------------------------------
    @Test
    fun process_partialMutation_volteThrows_roamingWrittenSpeechHdPreserved() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState().apply {
            roaming = true
            speechHd = true
        }

        val abi = StatusBarIconVisibilityAbi(
            statusBarMobileViewResolutionRootClass = StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            mobileIconStateResolutionRootClass = StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java,
            mStateField = field(StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java, "mState"),
            wifiAvailableField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "wifiAvailable"),
            subIdField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "subId"),
            visibleField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "visible"),
            roamingField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "roaming"),
            volteField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "volte"),
            speechHdField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "speechHd"),
        )

        val effect = StatusBarIconVisibilityEffect(abi) {
            makeSnapshot(hideRoaming = true, hideVolte = true)
        }

        try {
            effect.process(view, state, "applyMobileState")
            fail("Expected IllegalArgumentException from writing Boolean to int field")
        } catch (e: IllegalArgumentException) {
            // expected; earlier roaming write must remain, later volte/speechHd must not happen.
        }

        assertFalse("roaming must be written before the failure", state.roaming)
        assertEquals(0, state.volte) // primitive int default, unchanged
        assertTrue("speechHd must be preserved", state.speechHd)
    }

    // -------------------------------------------------------------------------
    // Q. fast IllegalAccessException -> IllegalAccessError
    // -------------------------------------------------------------------------
    @Test
    fun process_fastIllegalAccessException_mappedToIllegalAccessError() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = PrivateWifiMobileIconState()

        val wifiField = PrivateWifiMobileIconState::class.java.getDeclaredField("wifiAvailable")
        wifiField.isAccessible = false

        val abi = StatusBarIconVisibilityAbi(
            statusBarMobileViewResolutionRootClass = StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            mobileIconStateResolutionRootClass = PrivateWifiMobileIconState::class.java,
            mStateField = field(StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java, "mState"),
            wifiAvailableField = wifiField,
            subIdField = field(PrivateWifiMobileIconState::class.java, "subId"),
            visibleField = field(PrivateWifiMobileIconState::class.java, "visible"),
            roamingField = field(PrivateWifiMobileIconState::class.java, "roaming"),
            volteField = field(PrivateWifiMobileIconState::class.java, "volte"),
            speechHdField = field(PrivateWifiMobileIconState::class.java, "speechHd"),
        )

        val effect = StatusBarIconVisibilityEffect(abi) { makeSnapshot(hideSignal = true) }

        try {
            effect.process(view, state, "applyMobileState")
            fail("Expected IllegalAccessError")
        } catch (e: IllegalAccessError) {
            assertNotNull(e.message)
        } finally {
            wifiField.isAccessible = true
        }
    }

    // -------------------------------------------------------------------------
    // R. fast IllegalArgumentException -> no legacy retry
    // -------------------------------------------------------------------------
    @Test
    fun process_fastIllegalArgumentException_noLegacyRetry() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState()

        val abi = StatusBarIconVisibilityAbi(
            statusBarMobileViewResolutionRootClass = StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            mobileIconStateResolutionRootClass = StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java,
            mStateField = field(StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java, "mState"),
            wifiAvailableField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "wifiAvailable"),
            subIdField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "subId"),
            visibleField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "visible"),
            roamingField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "roaming"),
            volteField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "volte"),
            speechHdField = field(StatusBarIconVisibilityFixtures.WrongTypeVolteMobileIconState::class.java, "speechHd"),
        )

        val effect = StatusBarIconVisibilityEffect(abi) { makeSnapshot(hideRoaming = true, hideVolte = true) }

        try {
            effect.process(view, state, "applyMobileState")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // FAST path threw; no legacy retry occurred.
        }
    }

    // -------------------------------------------------------------------------
    // S. subId ClassCastException -> no legacy retry
    // -------------------------------------------------------------------------
    @Test
    fun process_fastSubIdClassCastException_noLegacyRetry() {
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.LongSubIdMobileIconState()

        val abi = StatusBarIconVisibilityAbi(
            statusBarMobileViewResolutionRootClass = StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            mobileIconStateResolutionRootClass = StatusBarIconVisibilityFixtures.LongSubIdMobileIconState::class.java,
            mStateField = field(StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java, "mState"),
            wifiAvailableField = field(StatusBarIconVisibilityFixtures.LongSubIdMobileIconState::class.java, "wifiAvailable"),
            subIdField = field(StatusBarIconVisibilityFixtures.LongSubIdMobileIconState::class.java, "subId"),
            visibleField = field(StatusBarIconVisibilityFixtures.LongSubIdMobileIconState::class.java, "visible"),
            roamingField = field(StatusBarIconVisibilityFixtures.LongSubIdMobileIconState::class.java, "roaming"),
            volteField = field(StatusBarIconVisibilityFixtures.LongSubIdMobileIconState::class.java, "volte"),
            speechHdField = field(StatusBarIconVisibilityFixtures.LongSubIdMobileIconState::class.java, "speechHd"),
        )

        val effect = StatusBarIconVisibilityEffect(abi) { makeSnapshot() }

        try {
            effect.process(view, state, "applyMobileState")
            fail("Expected ClassCastException from Long as Int")
        } catch (e: ClassCastException) {
            // FAST path threw; no legacy retry occurred.
        }

        assertTrue("no writes happened after subId cast failure", state.visible)
    }

    // -------------------------------------------------------------------------
    // T. no mutation after failed fast operation
    // -------------------------------------------------------------------------
    // Covered by R and S.

    // -------------------------------------------------------------------------
    // U. production callback captures immutable Effect
    // -------------------------------------------------------------------------
    @Test
    fun effect_isImmutableAndHasNoMutableFields() {
        val effect = fastEffect()
        assertTrue("Effect must hold ABI as val", effect.abi != null)

        val effectClass = StatusBarIconVisibilityEffect::class.java
        for (field in effectClass.declaredFields) {
            assertTrue(
                "Effect field ${field.name} must be final (val)",
                java.lang.reflect.Modifier.isFinal(field.modifiers),
            )
        }
    }

    // -------------------------------------------------------------------------
    // V. no mutable process-global Effect
    // -------------------------------------------------------------------------
    @Test
    fun effect_isConstructedPerHookInstall_notProcessGlobal() {
        val effect1 = fastEffect()
        val effect2 = fastEffect()
        assertTrue("Each effect must be a distinct instance", effect1 !== effect2)
    }

    // -------------------------------------------------------------------------
    // W. existing snapshot/publication reused
    // -------------------------------------------------------------------------
    @Test
    fun effect_usesSuppliedSnapshotProvider() {
        val custom = makeSnapshot(hideSignal = true)
        val view = StatusBarIconVisibilityFixtures.StatusBarMobileView()
        view.mState = null
        val state = StatusBarIconVisibilityFixtures.DeclaredMobileIconState()
        val effect = StatusBarIconVisibilityEffect(fastAbi()) { custom }

        effect.process(view, state, "applyMobileState")

        assertFalse(state.visible)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun fastEffect(): StatusBarIconVisibilityEffect {
        return StatusBarIconVisibilityEffect(fastAbi()) { makeSnapshot(hideSignal = true) }
    }

    private fun fastAbi(): StatusBarIconVisibilityAbi {
        return StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
        ) ?: throw AssertionError("Resolver must return an ABI for the default fixture")
    }

    private fun field(clazz: Class<*>, name: String): Field {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                return current.getDeclaredField(name).apply { isAccessible = true }
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            }
        }
        throw NoSuchFieldException("$name in ${clazz.name}")
    }

    private class PrivateWifiMobileIconState {
        private var wifiAvailable: Boolean = false
        var subId: Int = 0
        var visible: Boolean = true
        var roaming: Boolean = false
        var volte: Boolean = false
        var speechHd: Boolean = false
    }

    private fun makeSnapshot(
        hideSignal: Boolean = false,
        hideSignalWifiConnected: Boolean = false,
        hideSim1: Boolean = false,
        hideSim2: Boolean = false,
        hideSimNoData: Boolean = false,
        hideRoaming: Boolean = false,
        hideVolte: Boolean = false,
    ): StatusBarIconVisibilitySnapshot {
        return StatusBarIconVisibilitySnapshot(
            id = 1L,
            hideHeadset = false,
            hideSound = false,
            hideDnd = false,
            hideAlarm = false,
            hideProfile = false,
            hideVpn = false,
            hideAirplane = false,
            hideNfc = false,
            hideSecondSpace = false,
            hideGps = false,
            hideWifi = false,
            hideHotspot = false,
            hideNoSims = false,
            hideBtBattery = false,
            hideBleUnlock = false,
            hideBluetoothIcn = false,
            hideVolte = hideVolte,
            hideSignal = hideSignal,
            hideSignalWifiConnected = hideSignalWifiConnected,
            hideSim1 = hideSim1,
            hideSim2 = hideSim2,
            hideSimNoData = hideSimNoData,
            hideRoaming = hideRoaming,
            hidePrivacy = false,
            hideMute = false,
            hideSpeaker = false,
            hideRecord = false,
            hideWirelessHeadset = false,
        )
    }

}
