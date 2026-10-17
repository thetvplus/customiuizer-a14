package tv.withaibuild.customiuizer.mods.volumedialogautohide

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable
import java.util.concurrent.atomic.AtomicReference

/**
 * Component tests for [VolumeDialogAutohideDelayEffect].
 *
 * These tests exercise FAST vs LEGACY eligibility, callback oracle ordering,
 * safety alias behavior, failure mapping, and the immutable effect contract.
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class VolumeDialogAutohideDelayEffectTest {

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
    }

    // -------------------------------------------------------------------------
    // A. FAST ORACLE
    // -------------------------------------------------------------------------
    @Test
    fun fast_hoveringTrue_returns16000AndSkips() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply { mHovering = true }
        val effect = fastEffect()
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue("callback must be skipped", callback.skipped)
        assertEquals(16000, callback.result)
    }

    @Test
    fun fast_hoveringTrue_doesNotReadSafetyOrExpanded() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = true
            mIsSafetyShowing = null
            // If the effect reached safety or mExpanded reads, it would see a null cast or missing field.
        }
        val effect = fastEffect()
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(16000, callback.result)
    }

    @Test
    fun fast_safetyTrue_expandedPositive_returnsExpanded() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = true
        }
        val effect = fastEffect(expanded = 12345, collapsed = 1000)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(12345, callback.result)
    }

    @Test
    fun fast_safetyTrue_expandedZero_returns5000() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = true
        }
        val effect = fastEffect(expanded = 0, collapsed = 1000)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(5000, callback.result)
    }

    @Test
    fun fast_safetyTrue_doesNotReadMExpanded() {
        // If mExpanded were read, the result would be the collapsed value (12345).
        // Because the safety-true branch returns early, it must use only snapshot.expanded.
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = true
            mExpanded = true
        }
        val effect = fastEffect(expanded = 0, collapsed = 12345)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(5000, callback.result)
    }

    @Test
    fun fast_safetyFalse_expandedTrue_returnsExpanded() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        val effect = fastEffect(expanded = 12345, collapsed = 1000)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(12345, callback.result)
    }

    @Test
    fun fast_safetyFalse_expandedFalse_returnsCollapsed() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = false
        }
        val effect = fastEffect(expanded = 1000, collapsed = 12345)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(12345, callback.result)
    }

    @Test
    fun fast_safetyFalse_optZero_fallsThrough() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = false
        }
        val effect = fastEffect(expanded = 0, collapsed = 0)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertFalse("callback must not be skipped", callback.skipped)
    }

    // -------------------------------------------------------------------------
    // B. SAFETY ALIAS
    // -------------------------------------------------------------------------
    @Test
    fun fast_safetyPrimaryFalse_usesMExpanded() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        val effect = fastEffect(expanded = 777, collapsed = 0)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(777, callback.result)
    }

    @Test
    fun fast_safetyPrimaryNull_usesFallback() {
        val dialog = VolumeDialogAutohideDelayFixtures.PrimarySafetyNullMiuiVolumeDialogImpl().apply {
            // mIsSafetyShowing is null; fallback mSafetyWarning is true.
        }
        val effect = fastEffectForRoot(
            VolumeDialogAutohideDelayFixtures.PrimarySafetyNullMiuiVolumeDialogImpl::class.java,
            expanded = 888,
            collapsed = 0,
        )
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(888, callback.result)
    }

    // -------------------------------------------------------------------------
    // E. SNAPSHOT CAPTURE
    // -------------------------------------------------------------------------
    @Test
    fun fast_processFastReceivesCapturedSnapshotAndDoesNotReadSnapshotRef() {
        // snapshotRef is null, so any access to it inside processFast would fail or select legacy.
        // processFast is called via reflection with an explicit captured snapshot.
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }

        val effect = VolumeDialogAutohideDelayEffect(fastAbi(), AtomicReference(null))
        val callback = makeCallback(dialog)

        val processFast = VolumeDialogAutohideDelayEffect::class.java.getDeclaredMethod(
            "processFast",
            Any::class.java,
            VolumeDialogAutohideDelayAbi::class.java,
            VolumeDialogAutohideDelaySnapshot::class.java,
            HookerClassHelper.BeforeHookCallback::class.java,
        )
        processFast.isAccessible = true
        processFast.invoke(effect, dialog, fastAbi(), VolumeDialogAutohideDelaySnapshot(777, 0), callback)

        assertTrue("processFast must returnAndSkip using the captured snapshot", callback.skipped)
        assertEquals(777, callback.result)
    }

    @Test
    fun fast_snapshotNull_selectsLegacy() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        MainModule.mPrefs.put("system_volumedialogdelay_expanded", 42)

        val effect = VolumeDialogAutohideDelayEffect(fastAbi(), AtomicReference(null))
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(42, callback.result)
    }

    @Test
    fun fast_subclassRuntimeObject_selectsLegacy() {
        val dialog = VolumeDialogAutohideDelayFixtures.SubMiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        MainModule.mPrefs.put("system_volumedialogdelay_expanded", 42)

        val effect = VolumeDialogAutohideDelayEffect(fastAbi(), AtomicReference(VolumeDialogAutohideDelaySnapshot(0, 0)))
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(42, callback.result)
    }

    @Test
    fun fast_safetyPrimaryMissing_usesFallback() {
        val dialog = VolumeDialogAutohideDelayFixtures.PrimaryMissingFallbackPresentMiuiVolumeDialogImpl()
        val effect = fastEffectForRoot(
            VolumeDialogAutohideDelayFixtures.PrimaryMissingFallbackPresentMiuiVolumeDialogImpl::class.java,
            expanded = 999,
            collapsed = 0,
        )
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(999, callback.result)
    }

    // -------------------------------------------------------------------------
    // C. MODE SELECTION
    // -------------------------------------------------------------------------
    @Test
    fun legacy_abiNull_usesLegacyPath() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        val effect = legacyEffect(snapshot = VolumeDialogAutohideDelaySnapshot(42, 0))
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(42, callback.result)
    }

    @Test
    fun legacy_subclassObject_usesLegacyPath() {
        val dialog = VolumeDialogAutohideDelayFixtures.SubMiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        val effect = legacyEffect(snapshot = VolumeDialogAutohideDelaySnapshot(42, 0))
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(42, callback.result)
    }

    @Test
    fun legacy_snapshotNull_usesLegacyPath() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        val effect = VolumeDialogAutohideDelayEffect(
            fastAbi(),
            AtomicReference(null),
        )
        // Seed legacy PrefMap.
        MainModule.mPrefs.put("system_volumedialogdelay_expanded", 42)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(42, callback.result)
    }

    // -------------------------------------------------------------------------
    // D. FAILURE
    // -------------------------------------------------------------------------
    @Test(expected = IllegalAccessError::class)
    fun fast_mHoveringIllegalAccess_exceptionMappedToIllegalAccessError() {
        val dialog = VolumeDialogAutohideDelayFixtures.PrivateFieldMiuiVolumeDialogImpl().apply {
            setHovering(false)
            setSafetyShowing(false)
            setExpanded(true)
        }
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.PrivateFieldMiuiVolumeDialogImpl::class.java,
        )!!

        // Revoke accessibility to force IllegalAccessException.
        abi.mHoveringField.isAccessible = false

        val effect = VolumeDialogAutohideDelayEffect(
            abi,
            AtomicReference(VolumeDialogAutohideDelaySnapshot(42, 0)),
        )
        val callback = makeCallback(dialog)

        try {
            effect.before(callback)
        } finally {
            abi.mHoveringField.isAccessible = true
        }
    }

    @Test
    fun fast_illegalArgumentException_propagatesAndDoesNotSkip() {
        // Use a corrupt ABI where the resolved mHovering Field belongs to a different class.
        // This forces Field.getBoolean to throw IllegalArgumentException for the MiuiVolumeDialogImpl receiver.
        val badFieldTarget = VolumeDialogAutohideDelayFixtures.BadFieldTarget::class.java
        val badHoveringField = badFieldTarget.getDeclaredField("badHovering")
        val badExpandedField = badFieldTarget.getDeclaredField("badExpanded")
        badHoveringField.isAccessible = true
        badExpandedField.isAccessible = true

        val abi = VolumeDialogAutohideDelayAbi(
            VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java,
            badHoveringField,
            badExpandedField,
        )
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl()
        val effect = VolumeDialogAutohideDelayEffect(
            abi,
            AtomicReference(VolumeDialogAutohideDelaySnapshot(0, 0)),
        )
        val callback = makeCallback(dialog)

        try {
            effect.before(callback)
            fail("Expected IllegalArgumentException for mismatched field receiver")
        } catch (e: IllegalArgumentException) {
            assertFalse(callback.skipped)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun fastAbi(): VolumeDialogAutohideDelayAbi {
        return VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java,
        )!!
    }

    private fun fastEffect(expanded: Int = 5000, collapsed: Int = 0): VolumeDialogAutohideDelayEffect {
        return fastEffectForRoot(
            VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java,
            expanded,
            collapsed,
        )
    }

    private fun fastEffectForRoot(
        rootClass: Class<*>,
        expanded: Int = 5000,
        collapsed: Int = 0,
    ): VolumeDialogAutohideDelayEffect {
        return VolumeDialogAutohideDelayEffect(
            VolumeDialogAutohideDelayResolver.resolve(rootClass)!!,
            AtomicReference(VolumeDialogAutohideDelaySnapshot(expanded, collapsed)),
        )
    }

    private fun legacyEffect(snapshot: VolumeDialogAutohideDelaySnapshot): VolumeDialogAutohideDelayEffect {
        MainModule.mPrefs.put("system_volumedialogdelay_expanded", snapshot.expanded)
        MainModule.mPrefs.put("system_volumedialogdelay_collapsed", snapshot.collapsed)
        return VolumeDialogAutohideDelayEffect(null, AtomicReference(null))
    }

    private fun makeCallback(target: Any?, args: Array<Any?> = emptyArray()): HookerClassHelper.BeforeHookCallback {
        val chain = FakeChain(target, args)
        return HookerClassHelper.BeforeHookCallback(chain)
    }

    private class FakeChain(
        private val target: Any?,
        private val args: Array<Any?>,
    ) : XposedInterface.Chain {

        var proceedCount = 0
            private set

        override fun getExecutable(): Executable =
            VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java.getDeclaredMethod("computeTimeoutH")

        override fun getThisObject(): Any? = target
        override fun getArgs(): List<Any?> = args.toList()
        override fun getArg(index: Int): Any? = args.getOrNull(index)

        override fun proceed(): Any? {
            proceedCount++
            return null
        }

        override fun proceed(p0: Array<Any>): Any? {
            proceedCount++
            return null
        }

        override fun proceedWith(p0: Any): Any? = error("not used in test")
        override fun proceedWith(p0: Any, p1: Array<Any>): Any? = error("not used in test")
    }
}
