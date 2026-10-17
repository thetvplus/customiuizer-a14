package tv.withaibuild.customiuizer.mods.volumedialogautohide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import java.lang.reflect.Field

/**
 * Component tests for [VolumeDialogAutohideDelayResolver].
 *
 * These tests exercise cold-path ABI resolution, including exact roots, inherited
 * fields, primitive/wrapper type enforcement, missing-field fallback, ordinary
 * failure fallback, and fatal error propagation.
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class VolumeDialogAutohideDelayResolverTest {

    // -------------------------------------------------------------------------
    // A. EXACT ROOT RESOLUTION
    // -------------------------------------------------------------------------
    @Test
    fun resolve_exactRoot_resolvesPrimitiveBooleanFields() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java,
        ) ?: throw AssertionError("Resolver must resolve exact root")

        assertSame(VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java, abi.resolutionRootClass)
        assertResolvedPrimitiveBoolean(abi.mHoveringField, "mHovering")
        assertResolvedPrimitiveBoolean(abi.mExpandedField, "mExpanded")
    }

    @Test
    fun resolve_inheritedPrimitiveFields_resolvesFromRoot() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java,
        ) ?: throw AssertionError("Resolver must resolve inherited fields")

        assertSame(VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java, abi.resolutionRootClass)
        assertResolvedPrimitiveBoolean(abi.mHoveringField, "mHovering")
        assertResolvedPrimitiveBoolean(abi.mExpandedField, "mExpanded")
    }

    // -------------------------------------------------------------------------
    // B. PRIMITIVE BOOLEAN ENFORCEMENT
    // -------------------------------------------------------------------------
    @Test
    fun resolve_wrapperHovering_rejects() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.WrapperHoveringMiuiVolumeDialogImpl::class.java,
        )
        assertNull("Resolver must reject wrapper mHovering", abi)
    }

    @Test
    fun resolve_wrapperExpanded_rejects() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.WrapperExpandedMiuiVolumeDialogImpl::class.java,
        )
        assertNull("Resolver must reject wrapper mExpanded", abi)
    }

    @Test
    fun resolve_bothWrapper_rejects() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.WrapperBooleanMiuiVolumeDialogImpl::class.java,
        )
        assertNull("Resolver must reject wrapper Boolean fields", abi)
    }

    // -------------------------------------------------------------------------
    // C. MISSING FIELD / METHOD REJECTION
    // -------------------------------------------------------------------------
    @Test
    fun resolve_missingHovering_rejects() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.MissingHoveringMiuiVolumeDialogImpl::class.java,
        )
        assertNull("Resolver must reject missing mHovering", abi)
    }

    @Test
    fun resolve_missingExpanded_rejects() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.MissingExpandedMiuiVolumeDialogImpl::class.java,
        )
        assertNull("Resolver must reject missing mExpanded", abi)
    }

    @Test
    fun resolve_missingComputeTimeoutH_rejects() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.MissingMethodMiuiVolumeDialogImpl::class.java,
        )
        assertNull("Resolver must reject missing computeTimeoutH", abi)
    }

    // -------------------------------------------------------------------------
    // D. NO RETURN-TYPE FILTERING
    // -------------------------------------------------------------------------
    @Test
    fun resolve_stringReturnTypeForComputeTimeoutH_stillResolvesFields() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.StringReturnMiuiVolumeDialogImpl::class.java,
        ) ?: throw AssertionError("Resolver must not filter on return type")

        assertResolvedPrimitiveBoolean(abi.mHoveringField, "mHovering")
        assertResolvedPrimitiveBoolean(abi.mExpandedField, "mExpanded")
    }

    // -------------------------------------------------------------------------
    // E. ORDINARY RESOLUTION FAILURE
    // -------------------------------------------------------------------------
    @Test
    fun resolve_ordinaryClassLoadingFailure_returnsNull() {
        val throwingLoader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                throw IllegalStateException("simulated ordinary class-loading failure")
            }
        }

        val abi = VolumeDialogAutohideDelayResolver.resolve(throwingLoader)
        assertNull("Resolver must return null on ordinary class-loading failure", abi)
    }

    // -------------------------------------------------------------------------
    // F. FATAL RESOLUTION FAILURE
    // -------------------------------------------------------------------------
    @Test(expected = OutOfMemoryError::class)
    fun resolve_fatalClassLoadingFailure_propagates() {
        val throwingLoader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                throw OutOfMemoryError("simulated fatal class-loading failure")
            }
        }

        VolumeDialogAutohideDelayResolver.resolve(throwingLoader)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun assertResolvedPrimitiveBoolean(field: Field, name: String) {
        assertNotNull("$name field must resolve", field)
        assertEquals("$name field must be primitive boolean", java.lang.Boolean.TYPE, field.type)
    }
}
