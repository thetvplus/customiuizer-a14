package tv.withaibuild.customiuizer.mods.notificationautoexpand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Component tests for [NotificationAutoExpandResolver].
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class NotificationAutoExpandResolverTest {

    // -------------------------------------------------------------------------
    // A. EXACT ROOT RESOLUTION
    // -------------------------------------------------------------------------
    @Test
    fun resolve_exactRoot_resolvesPrimitiveBooleanFieldAndMethods() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
        ) ?: throw AssertionError("Resolver must resolve exact root")

        assertSame(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
            abi.resolutionRootClass,
        )
        assertResolvedPrimitiveBoolean(abi.mOnKeyguardField, "mOnKeyguard")
        assertResolvedMethod(abi.getEntryMethod, "getEntry")
        assertResolvedMethod(abi.setSystemExpandedMethod, "setSystemExpanded")
    }

    @Test
    fun resolve_inheritedMOnKeyguard_resolvesFromRoot() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
        ) ?: throw AssertionError("Resolver must resolve inherited field")

        assertSame(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
            abi.resolutionRootClass,
        )
        assertResolvedPrimitiveBoolean(abi.mOnKeyguardField, "mOnKeyguard")
    }

    // -------------------------------------------------------------------------
    // B. PRIMITIVE BOOLEAN ENFORCEMENT
    // -------------------------------------------------------------------------
    @Test
    fun resolve_wrapperMOnKeyguard_rejects() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.WrapperMOnKeyguardRow::class.java,
        )
        assertNull("Resolver must reject wrapper mOnKeyguard", abi)
    }

    // -------------------------------------------------------------------------
    // C. MISSING MEMBER REJECTION
    // -------------------------------------------------------------------------
    @Test
    fun resolve_missingMOnKeyguard_rejects() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.MissingMOnKeyguardRow::class.java,
        )
        assertNull("Resolver must reject missing mOnKeyguard", abi)
    }

    @Test
    fun resolve_missingGetEntry_rejects() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.MissingGetEntryRow::class.java,
        )
        assertNull("Resolver must reject missing getEntry", abi)
    }

    @Test
    fun resolve_missingSetSystemExpanded_rejects() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.MissingSetSystemExpandedRow::class.java,
        )
        assertNull("Resolver must reject missing setSystemExpanded", abi)
    }

    // -------------------------------------------------------------------------
    // D. ZERO-ARG GET_ENTRY SELECTION
    // -------------------------------------------------------------------------
    @Test
    fun resolve_getEntry_zeroArgSelected() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.OverloadedGetEntryRow::class.java,
        ) ?: throw AssertionError("Resolver must resolve getEntry")

        assertEquals(
            "getEntry must resolve to the zero-argument overload",
            0,
            abi.getEntryMethod.parameterCount,
        )
    }

    @Test
    fun resolve_getEntry_exactRoot_hasZeroArgs() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
        ) ?: throw AssertionError("Resolver must resolve getEntry")

        assertEquals(
            "getEntry must be a zero-argument method",
            0,
            abi.getEntryMethod.parameterCount,
        )
    }

    // -------------------------------------------------------------------------
    // E. BOOLEAN.CLASS BEST-MATCH BEHAVIOR
    // -------------------------------------------------------------------------
    @Test
    fun resolve_setSystemExpandedWithBoxedBoolean_resolves() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.OverloadedSetSystemExpandedRow::class.java,
        ) ?: throw AssertionError("Resolver must resolve setSystemExpanded with Boolean.class")

        assertEquals(
            "setSystemExpanded must resolve to a one-argument method",
            1,
            abi.setSystemExpandedMethod.parameterTypes.size,
        )
        assertEquals(
            "setSystemExpanded must resolve to the exact boxed Boolean overload when present",
            java.lang.Boolean::class.java,
            abi.setSystemExpandedMethod.parameterTypes[0],
        )
    }

    @Test
    fun resolve_setSystemExpandedWithPrimitiveBoolean_resolves() {
        val abi = NotificationAutoExpandResolver.resolve(
            NotificationAutoExpandFixtures.ExpandableNotificationRow::class.java,
        ) ?: throw AssertionError("Resolver must resolve primitive setSystemExpanded")

        assertEquals(
            "setSystemExpanded must resolve to a one-argument method",
            1,
            abi.setSystemExpandedMethod.parameterTypes.size,
        )
        assertEquals(
            "setSystemExpanded must resolve to the primitive boolean overload when only that exists",
            java.lang.Boolean.TYPE,
            abi.setSystemExpandedMethod.parameterTypes[0],
        )
    }

    // -------------------------------------------------------------------------
    // F. ORDINARY RESOLUTION FAILURE
    // -------------------------------------------------------------------------
    @Test
    fun resolve_ordinaryClassLoadingFailure_returnsNull() {
        val throwingLoader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                throw IllegalStateException("simulated ordinary class-loading failure")
            }
        }

        val abi = NotificationAutoExpandResolver.resolve(throwingLoader)
        assertNull("Resolver must return null on ordinary class-loading failure", abi)
    }

    // -------------------------------------------------------------------------
    // G. FATAL RESOLUTION FAILURE
    // -------------------------------------------------------------------------
    @Test(expected = OutOfMemoryError::class)
    fun resolve_fatalClassLoadingFailure_propagates() {
        val throwingLoader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                throw OutOfMemoryError("simulated fatal class-loading failure")
            }
        }

        NotificationAutoExpandResolver.resolve(throwingLoader)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun assertResolvedPrimitiveBoolean(field: Field, name: String) {
        assertNotNull("$name field must resolve", field)
        assertEquals("$name field must be primitive boolean", java.lang.Boolean.TYPE, field.type)
    }

    private fun assertResolvedMethod(method: Method, name: String) {
        assertNotNull("$name method must resolve", method)
        assertEquals("$name method name must match", name, method.name)
    }
}
