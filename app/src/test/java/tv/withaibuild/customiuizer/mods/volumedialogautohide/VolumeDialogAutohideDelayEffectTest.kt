package tv.withaibuild.customiuizer.mods.volumedialogautohide

import io.github.libxposed.api.XposedInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Executable

class VolumeDialogAutohideDelayEffectTest {

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
    fun safetyWarningWithoutExpandedFieldUsesNativeDefault() {
        val dialog = VolumeDialogAutohideDelayFixtures.SafetyNoExpandedMiuiVolumeDialogImpl().apply {
            mIsSafetyShowing = true
        }
        val effect = testEffect(null, collapsed = 1000)
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

    @Test
    fun installedCallbackReadsTheLatestPublishedPreferences() {
        val prefs = PrefMap()
        val effect = VolumeDialogAutohideDelayEffect(fastAbi(), prefs) { _, millis, _ -> millis }
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl()
        val first = makeCallback(dialog)
        effect.before(first)
        assertFalse(first.skipped)

        prefs.replaceSnapshot(mapOf(
            "pref_key_system_volumedialogdelay_expanded" to 6500,
            "pref_key_system_volumedialogdelay_collapsed" to 2100,
        ))
        val collapsed = makeCallback(dialog)
        effect.before(collapsed)
        assertEquals(2100, collapsed.result)

        dialog.mExpanded = true
        val expanded = makeCallback(dialog)
        effect.before(expanded)
        assertEquals(6500, expanded.result)

        prefs.put("system_volumedialogdelay_expanded", "invalid")
        val invalid = makeCallback(dialog)
        effect.before(invalid)
        assertFalse(invalid.skipped)
    }

    @Test
    fun fast_subclassRuntimeObject_selectsLegacy() {
        val dialog = VolumeDialogAutohideDelayFixtures.SubMiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        val effect = testEffect(fastAbi(), expanded = 42)
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

    @Test
    fun legacy_abiNull_usesLegacyPath() {
        val dialog = VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl().apply {
            mHovering = false
            mIsSafetyShowing = false
            mExpanded = true
        }
        val effect = testEffect(null, expanded = 42)
        val callback = makeCallback(dialog)

        effect.before(callback)

        assertTrue(callback.skipped)
        assertEquals(42, callback.result)
    }

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

        val effect = testEffect(abi, expanded = 42)
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
        val effect = testEffect(abi)
        val callback = makeCallback(dialog)

        try {
            effect.before(callback)
            fail("Expected IllegalArgumentException for mismatched field receiver")
        } catch (e: IllegalArgumentException) {
            assertFalse(callback.skipped)
        }
    }

    // Keep the existing ABI/mode tests independent of accessibility policy; dedicated tests
    // exercise recommendation flags, live manager settings and missing-manager fallback.
    private fun testEffect(
        abi: VolumeDialogAutohideDelayAbi?,
        expanded: Int = 0,
        collapsed: Int = 0,
    ) = VolumeDialogAutohideDelayEffect(abi, PrefMap().apply {
        put("system_volumedialogdelay_expanded", expanded)
        put("system_volumedialogdelay_collapsed", collapsed)
    }) { _, millis, _ -> millis }

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
        return testEffect(VolumeDialogAutohideDelayResolver.resolve(rootClass)!!, expanded, collapsed)
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
