package tv.withaibuild.customiuizer.mods.statusbariconvisibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field

/**
 * Component tests for [StatusBarIconVisibilityResolver].
 *
 * These tests exercise cold-path ABI resolution, including exact roots, inherited
 * fields, primitive/wrapper type enforcement, missing-field fallback, ordinary
 * failure fallback, and fatal error propagation.
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class StatusBarIconVisibilityResolverTest {

    // -------------------------------------------------------------------------
    // A. METHOD-SIGNATURE ROOT DERIVATION (single-Class overload)
    // -------------------------------------------------------------------------
    @Test
    fun resolve_derivesMobileIconStateRootFromMethodParameter() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
        ) ?: throw AssertionError("Resolver must resolve from method parameters")

        assertSame(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            abi.statusBarMobileViewResolutionRootClass,
        )
        assertSame(
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
            abi.mobileIconStateResolutionRootClass,
        )
        assertResolvedFields(abi)
    }

    // -------------------------------------------------------------------------
    // B. mSTATE FALLBACK ROOT DERIVATION (single-Class overload)
    // -------------------------------------------------------------------------
    @Test
    fun resolve_objectMethodParameter_withMStateFallback_usesMStateType() {
        // The hook methods take Object, so the resolver cannot use the parameter type.
        // It falls back to the mState type, which is the concrete state class.
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.ObjectParamWithTypedMStateStatusBarMobileView::class.java,
        ) ?: throw AssertionError("Resolver must fall back to mState type")

        assertSame(
            StatusBarIconVisibilityFixtures.ObjectParamWithTypedMStateStatusBarMobileView::class.java,
            abi.statusBarMobileViewResolutionRootClass,
        )
        assertSame(
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
            abi.mobileIconStateResolutionRootClass,
        )
        assertResolvedFields(abi)
    }

    @Test
    fun resolve_mStateTypeOnly_noMethods_usesMStateType() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.MStateTypedStatusBarMobileView::class.java,
        ) ?: throw AssertionError("Resolver must use mState type when no candidate methods exist")

        assertSame(
            StatusBarIconVisibilityFixtures.MStateTypedStatusBarMobileView::class.java,
            abi.statusBarMobileViewResolutionRootClass,
        )
        assertSame(
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
            abi.mobileIconStateResolutionRootClass,
        )
        assertResolvedFields(abi)
    }

    // -------------------------------------------------------------------------
    // C. AMBIGUOUS ROOT SAFETY (single-Class overload)
    // -------------------------------------------------------------------------
    @Test
    fun resolve_ambiguousParameters_noMStateMatch_returnsNull() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.AmbiguousParamStatusBarMobileView::class.java,
        )
        assertNull(
            "Resolver must not choose an arbitrary method when parameter types differ and mState is not concrete",
            abi,
        )
    }

    @Test
    fun resolve_ambiguousParameters_mStateTieBreak_selectsMStateMatch() {
        // Candidate methods expose different concrete parameter types, but the mState
        // type matches exactly one of them. The resolver may conservatively select it.
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.MStateTieBreakStatusBarMobileView::class.java,
        ) ?: throw AssertionError("Resolver must use mState type as conservative tie-break")

        assertSame(
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
            abi.mobileIconStateResolutionRootClass,
        )
        assertResolvedFields(abi)
    }

    // -------------------------------------------------------------------------
    // D. UNEXPECTED ORDINARY FAILURE (ClassLoader seam)
    // -------------------------------------------------------------------------
    @Test
    fun resolve_ordinaryRuntimeExceptionDuringClassLoading_returnsNull() {
        val throwingLoader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                throw IllegalStateException("simulated ordinary class-loading failure")
            }
        }

        val abi = StatusBarIconVisibilityResolver.resolve(throwingLoader, "any.StatusBarMobileView")
        assertNull(
            "Resolver must treat ordinary Throwable as fallback, not fatal",
            abi,
        )
    }

    // -------------------------------------------------------------------------
    // E. Resolver exact root + declared fields (two-Class overload, field ABI)
    // -------------------------------------------------------------------------
    @Test
    fun resolve_exactRootWithDeclaredFields_returnsAbi() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
        )
        assertNotNull("Resolver must return an ABI for an exact root with declared fields", abi)
        abi ?: return

        assertSame(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            abi.statusBarMobileViewResolutionRootClass,
        )
        assertSame(
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
            abi.mobileIconStateResolutionRootClass,
        )
        assertResolvedFields(abi)
    }

    // -------------------------------------------------------------------------
    // F. Resolver inherited fields (two-Class overload, field ABI)
    // -------------------------------------------------------------------------
    @Test
    fun resolve_inheritedFieldsOnExactRoot_returnsAbiWithInheritedField() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            StatusBarIconVisibilityFixtures.InheritedMobileIconState::class.java,
        )
        assertNotNull("Resolver must return an ABI for a root with inherited fields", abi)
        abi ?: return

        // The field is declared on the base class but the resolution root is the subclass.
        assertEquals(
            StatusBarIconVisibilityFixtures.BaseMobileIconState::class.java.name,
            abi.wifiAvailableField.declaringClass.name,
        )
        assertSame(
            StatusBarIconVisibilityFixtures.InheritedMobileIconState::class.java,
            abi.mobileIconStateResolutionRootClass,
        )
    }

    // -------------------------------------------------------------------------
    // G. wifiAvailable primitive boolean accepted
    // -------------------------------------------------------------------------
    @Test
    fun resolve_wifiAvailablePrimitiveBoolean_accepted() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
        )
        assertNotNull(abi)
        assertTrue(
            "wifiAvailableField must be primitive boolean",
            abi?.wifiAvailableField?.type == Boolean::class.javaPrimitiveType,
        )
    }

    // -------------------------------------------------------------------------
    // H. wifiAvailable Boolean wrapper rejected
    // -------------------------------------------------------------------------
    @Test
    fun resolve_wifiAvailableBooleanWrapper_rejected() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            StatusBarIconVisibilityFixtures.BooleanWifiMobileIconState::class.java,
        )
        assertNull(
            "Resolver must reject a non-primitive Boolean wifiAvailable field",
            abi,
        )
    }

    // -------------------------------------------------------------------------
    // I. Resolver missing each required field -> fallback
    // -------------------------------------------------------------------------
    @Test
    fun resolve_missingWifiAvailable_returnsNull() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            StatusBarIconVisibilityFixtures.MissingWifiMobileIconState::class.java,
        )
        assertNull(abi)
    }

    @Test
    fun resolve_missingSubId_returnsNull() {
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.StatusBarMobileView::class.java,
            StatusBarIconVisibilityFixtures.MissingSubIdMobileIconState::class.java,
        )
        assertNull(abi)
    }

    @Test
    fun resolve_missingMState_returnsNull() {
        // A class with no mState field.
        class NoMStateStatusBarMobileView {
            fun applyMobileState(state: StatusBarIconVisibilityFixtures.DeclaredMobileIconState) {}
            fun updateState(state: StatusBarIconVisibilityFixtures.DeclaredMobileIconState) {}
        }

        val abi = StatusBarIconVisibilityResolver.resolve(
            NoMStateStatusBarMobileView::class.java,
            StatusBarIconVisibilityFixtures.DeclaredMobileIconState::class.java,
        )
        assertNull(abi)
    }

    // -------------------------------------------------------------------------
    // J. Resolver ordinary failure -> fallback
    // -------------------------------------------------------------------------
    @Test
    fun resolve_ordinaryClassNotFound_returnsNull() {
        val classLoader = ClassLoader.getSystemClassLoader()
        val abi = StatusBarIconVisibilityResolver.resolve(classLoader, "non.existent.StatusBarMobileView")
        assertNull(abi)
    }

    // -------------------------------------------------------------------------
    // K. Resolver object parameter + object mState -> no safe root
    // -------------------------------------------------------------------------
    @Test
    fun resolve_objectMethodParameter_objectMState_returnsNull() {
        // Both methods and mState field are Object: no concrete state class can be determined.
        val abi = StatusBarIconVisibilityResolver.resolve(
            StatusBarIconVisibilityFixtures.ObjectParamStatusBarMobileView::class.java,
        )
        assertNull(abi)
    }

    // -------------------------------------------------------------------------
    // L. Resolver fatal -> propagate
    // -------------------------------------------------------------------------
    @Test
    fun resolve_fatalOutOfMemoryError_propagates() {
        val oomLoader = object : ClassLoader() {
            override fun loadClass(name: String?): Class<*> {
                throw OutOfMemoryError("fatal")
            }
        }

        try {
            StatusBarIconVisibilityResolver.resolve(oomLoader, "any.StatusBarMobileView")
            fail("Expected OutOfMemoryError to propagate")
        } catch (e: OutOfMemoryError) {
            assertEquals("fatal", e.message)
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun assertResolvedFields(abi: StatusBarIconVisibilityAbi) {
        assertFieldName("mState", abi.mStateField)
        assertFieldName("wifiAvailable", abi.wifiAvailableField)
        assertFieldName("subId", abi.subIdField)
        assertFieldName("visible", abi.visibleField)
        assertFieldName("roaming", abi.roamingField)
        assertFieldName("volte", abi.volteField)
        assertFieldName("speechHd", abi.speechHdField)
    }

    private fun assertFieldName(expected: String, field: Field) {
        assertEquals(expected, field.name)
    }
}
