package tv.withaibuild.customiuizer.mods.volumedialogautohide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.lang.reflect.Field

class VolumeDialogAutohideDelayResolverTest {

    @Test
    fun resolve_inheritedPrimitiveFields_resolvesFromRoot() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java,
        ) ?: throw AssertionError("Resolver must resolve inherited fields")

        assertSame(VolumeDialogAutohideDelayFixtures.MiuiVolumeDialogImpl::class.java, abi.resolutionRootClass)
        assertResolvedPrimitiveBoolean(abi.mHoveringField, "mHovering")
        assertResolvedPrimitiveBoolean(abi.mExpandedField, "mExpanded")
    }

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

    @Test
    fun resolve_stringReturnTypeForComputeTimeoutH_stillResolvesFields() {
        val abi = VolumeDialogAutohideDelayResolver.resolve(
            VolumeDialogAutohideDelayFixtures.StringReturnMiuiVolumeDialogImpl::class.java,
        ) ?: throw AssertionError("Resolver must not filter on return type")

        assertResolvedPrimitiveBoolean(abi.mHoveringField, "mHovering")
        assertResolvedPrimitiveBoolean(abi.mExpandedField, "mExpanded")
    }

    private fun assertResolvedPrimitiveBoolean(field: Field, name: String) {
        assertNotNull("$name field must resolve", field)
        assertEquals("$name field must be primitive boolean", java.lang.Boolean.TYPE, field.type)
    }
}
